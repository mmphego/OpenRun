package app.rwun;

import android.util.Xml;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.xmlpull.v1.XmlPullParser;

/** Interoperable activity files. Import is explicit and does not log in to Garmin. */
final class ActivityFiles {
  record Sample(double lat,double lon,long time,int hr,int segment,double altitude,double verticalAccuracy) {Sample(double lat,double lon,long time,int hr,int segment){this(lat,lon,time,hr,segment,Double.NaN,Double.NaN);}}

  record Imported(
      String id, String date, double km, long seconds, int avgHr, List<Sample> points) {}

  static Imported parse(byte[] bytes)throws Exception{return parse(bytes,false);}
  static Imported parse(byte[] bytes,boolean cycling) throws Exception {
    if (bytes.length > 10 * 1024 * 1024)
      throw new IllegalArgumentException("File exceeds the 10 MB import limit.");
    if(bytes.length>=12&&bytes[8]=='.'&&bytes[9]=='F'&&bytes[10]=='I'&&bytes[11]=='T')return FitReader.parse(bytes,cycling);
    XmlPullParser p = Xml.newPullParser();
    p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
    if (new String(bytes, StandardCharsets.UTF_8).toUpperCase(Locale.ROOT).contains("<!DOCTYPE"))
      throw new IllegalArgumentException("DTD files are unsupported.");
    p.setInput(new ByteArrayInputStream(bytes), null);
    List<Sample> points = new ArrayList<>();
    List<String> stack = new ArrayList<>();
    double altitude=Double.NaN;double lat = Double.NaN, lon = Double.NaN, lapMetres = 0, lapSeconds = 0;
    long time = 0, first = 0, last = 0;
    int hr = 0, hrCount = 0, hrTotal = 0;
    String root = "";
    int segment = 0, activities = 0;
    for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.next()) {
      if (event == XmlPullParser.DOCDECL)
        throw new IllegalArgumentException("DTD files are unsupported.");
      if (event == XmlPullParser.START_TAG) {
        String n = p.getName();
        stack.add(n);
        if (root.isEmpty()) root = n;
        if(n.equals("Activity")){String sport=p.getAttributeValue(null,"Sport");if(sport!=null&&!sport.equalsIgnoreCase(cycling?"Biking":"Running"))throw new IllegalArgumentException("File sport does not match the selected activity type");}
        if (n.equals("Activity") && ++activities > 1)
          throw new IllegalArgumentException("Import one Garmin activity at a time.");
        if (n.equals("trkseg") || n.equals("Track")) segment++;
        if (n.equals("trkpt") || n.equals("Trackpoint")) {
          altitude=Double.NaN;lat = Double.NaN;
          lon = Double.NaN;
          time = 0;
          hr = 0;
          if (n.equals("trkpt")) {
            lat = Double.parseDouble(p.getAttributeValue(null, "lat"));
            lon = Double.parseDouble(p.getAttributeValue(null, "lon"));
          }
        }
      } else if (event == XmlPullParser.TEXT) {
        if (stack.isEmpty()) continue;
        String n = stack.get(stack.size() - 1), v = p.getText().trim();
        if (v.isEmpty()) continue;
        String parent = stack.size() > 1 ? stack.get(stack.size() - 2) : "";
        if(n.equals("ele")||n.equals("AltitudeMeters"))altitude=Double.parseDouble(v);
        if (n.equals("LatitudeDegrees")) lat = Double.parseDouble(v);
        if (n.equals("LongitudeDegrees")) lon = Double.parseDouble(v);
        if ((n.equals("Time") || n.equals("time"))
            && (stack.contains("Trackpoint") || stack.contains("trkpt")))
          time = Instant.parse(v).toEpochMilli();
        if (n.equals("hr") || n.equals("Value") && parent.equals("HeartRateBpm"))
          hr = Integer.parseInt(v);
        if (n.equals("DistanceMeters") && parent.equals("Lap")) lapMetres += Double.parseDouble(v);
        if (n.equals("TotalTimeSeconds") && parent.equals("Lap"))
          lapSeconds += Double.parseDouble(v);
      } else if (event == XmlPullParser.END_TAG) {
        String n = p.getName();
        if (n.equals("trkpt") || n.equals("Trackpoint")) {
          if (time > 0) {
            if (first == 0) first = time;
            last = Math.max(last, time);
          }
          if (hr > 0 && hr < 250) {
            hrCount++;
            hrTotal += hr;
          }
          if (Double.isFinite(lat)
              && Double.isFinite(lon)
              && Math.abs(lat) <= 90
              && Math.abs(lon) <= 180
              && time > 0) points.add(new Sample(lat,lon,time,hr,segment,altitude,Double.NaN));
          if (points.size() > 150000) throw new IllegalArgumentException("Too many route points.");
        }
        if (!stack.isEmpty()) stack.remove(stack.size() - 1);
      }
    }
    if (!root.equals("gpx") && !root.equals("TrainingCenterDatabase"))
      throw new IllegalArgumentException(
          "Choose a GPX, TCX or supported running FIT activity with timestamps.");
    double metres = lapMetres;
    if (metres == 0) {
      for (int i = 1; i < points.size(); i++) {
        Sample a = points.get(i - 1), b = points.get(i);
        long dt = b.time() - a.time();
        if (a.segment() == b.segment() && dt > 0 && dt <= 30000) {
          double d = GpsAccumulator.distance(a.lat(), a.lon(), b.lat(), b.lon());
          if (d / (dt / 1000.0) < (cycling?35:12)) metres += d;
        }
      }
    }
    long seconds = lapSeconds > 0 ? Math.round(lapSeconds) : Math.max(0, (last - first) / 1000);
    if (first == 0 || !Double.isFinite(metres)||metres<=0||metres>(cycling?1000000:250000)||seconds<=0||seconds>180000)
      throw new IllegalArgumentException(
          "Activity needs distance and timestamped duration. Export GPX or TCX from Garmin"
              + " Connect.");
    return new Imported(
        hex(MessageDigest.getInstance("SHA-256").digest(bytes)),
        Instant.ofEpochMilli(first).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
        metres / 1000,
        seconds,
        hrCount == 0 ? 0 : hrTotal / hrCount,
        List.copyOf(points));
  }

  private static String hex(byte[] bytes) {
    StringBuilder out = new StringBuilder();
    for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b & 255));
    return out.toString();
  }

  static String gpx(List<Sample> points){return gpx(points,false);}
  static String gpx(List<Sample> points,boolean cycling) {
    StringBuilder s =
        new StringBuilder(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"OpenRun\""
                + " xmlns=\"http://www.topografix.com/GPX/1/1\""
                + " xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\"><trk><name>OpenRun"
                + (cycling?" ride</name><type>Cycling</type><trkseg>":" run</name><trkseg>"));
    int segment = points.isEmpty() ? 0 : points.get(0).segment();
    for (Sample p : points) {
      if (p.segment() != segment) {
        s.append("</trkseg><trkseg>");
        segment = p.segment();
      }
      s.append("<trkpt lat=\"")
          .append(p.lat())
          .append("\" lon=\"")
          .append(p.lon())
          .append("\"><time>")
          .append(Instant.ofEpochMilli(p.time()))
          .append("</time>");
      if(Double.isFinite(p.altitude()))s.append("<ele>").append(p.altitude()).append("</ele>");
      if (p.hr() > 0)
        s.append("<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>")
            .append(p.hr())
            .append("</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>");
      s.append("</trkpt>");
    }
    return s.append("</trkseg></trk></gpx>").toString();
  }
}
