package app.rwun;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ParserHostTest {
  private static int assertions;

  public static void main(String[] args) throws Exception {
    String gpx =
        "<gpx xmlns='http://www.topografix.com/GPX/1/1'><trk><trkseg><trkpt lat='0'"
            + " lon='0'><time>2026-10-16T10:00:00Z</time></trkpt><trkpt lat='0'"
            + " lon='0.0001'><time>2026-10-16T10:00:10Z</time></trkpt><trkpt lat='0'"
            + " lon='0.0002'><time>2026-10-16T10:00:20Z</time></trkpt></trkseg></trk></gpx>";
    var imported = ActivityFiles.parse(gpx.getBytes(StandardCharsets.UTF_8));
    check(imported.points().size() == 3, "GPX points");
    check(imported.seconds() == 20, "GPX timestamps");
    check(imported.km() > .022 && imported.km() < .023, "GPX distance");
    var roundtrip =
        ActivityFiles.parse(ActivityFiles.gpx(imported.points()).getBytes(StandardCharsets.UTF_8));
    check(Math.abs(roundtrip.km() - imported.km()) < .00001, "GPX round trip");
    String tcx =
        "<TrainingCenterDatabase><Activities><Activity Sport='Running'><Lap"
            + " StartTime='2026-10-16T10:00:00Z'><TotalTimeSeconds>600</TotalTimeSeconds><DistanceMeters>1500</DistanceMeters><Track><Trackpoint><Time>2026-10-16T10:00:00Z</Time><Position><LatitudeDegrees>0</LatitudeDegrees><LongitudeDegrees>0</LongitudeDegrees></Position><HeartRateBpm><Value>140</Value></HeartRateBpm></Trackpoint></Track></Lap></Activity></Activities></TrainingCenterDatabase>";
    var t = ActivityFiles.parse(tcx.getBytes(StandardCharsets.UTF_8));
    check(t.km() == 1.5 && t.seconds() == 600 && t.avgHr() == 140, "TCX lap totals and HR");
    var segmented =
        List.of(
            new ActivityFiles.Sample(0, 0, 100000, 140, 0),
            new ActivityFiles.Sample(0, .0001, 110000, 141, 0),
            new ActivityFiles.Sample(1, 1, 120000, 142, 1),
            new ActivityFiles.Sample(1, 1.0001, 130000, 143, 1));
    var split = ActivityFiles.parse(ActivityFiles.gpx(segmented).getBytes(StandardCharsets.UTF_8));
    check(split.km() < .03, "pause segments preserved");
    check(split.avgHr() == 141, "heart rate export/import");
    expectInvalid("<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><gpx>&x;</gpx>");
    expectInvalid("<gpx><trk><trkseg><trkpt lat='0' lon='0'/></trkseg></trk></gpx>");
    expectInvalid("<html/>");
    String cycle=tcx.replace("Sport='Running'","Sport='Biking'");check(ActivityFiles.parse(cycle.getBytes(StandardCharsets.UTF_8),true).km()==1.5,"cycling TCX totals");boolean wrong=false;try{ActivityFiles.parse(cycle.getBytes(StandardCharsets.UTF_8));}catch(IllegalArgumentException e){wrong=true;}check(wrong,"bike TCX rejected as running");String highSpeed="<gpx><trk><trkseg><trkpt lat='0' lon='0'><ele>100</ele><time>2026-10-16T10:00:00Z</time></trkpt><trkpt lat='0' lon='0.001'><ele>110</ele><time>2026-10-16T10:00:05Z</time></trkpt></trkseg></trk></gpx>";var bike=ActivityFiles.parse(highSpeed.getBytes(StandardCharsets.UTF_8),true);check(bike.km()>.11&&bike.points().get(1).altitude()==110,"bike GPX speed and elevation");check(ActivityFiles.parse(ActivityFiles.gpx(bike.points(),true).getBytes(StandardCharsets.UTF_8),true).points().get(0).altitude()==100,"elevation GPX roundtrip");
    System.out.println("GPX/TCX host tests passed: " + assertions + " assertions.");
  }

  private static void check(boolean condition, String description) {
    assertions++;
    if (!condition) throw new AssertionError(description);
  }

  private static void expectInvalid(String xml) throws Exception {
    try {
      ActivityFiles.parse(xml.getBytes(StandardCharsets.UTF_8));
    } catch (Exception expected) {
      assertions++;
      return;
    }
    throw new AssertionError("invalid XML accepted");
  }
}
