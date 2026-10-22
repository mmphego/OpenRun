package app.rwun;
import java.time.*;import java.net.URI;import java.util.*;import org.json.*;
/** Race variants have their own distance and date, rather than a guessed weekend date. */
final class RaceCatalogue {
 static void validate(JSONArray races)throws Exception{
  if(races.length()>5000)throw new IllegalArgumentException("Too many race entries");Set<String> ids=new HashSet<>();
  for(int i=0;i<races.length();i++){JSONObject r=races.getJSONObject(i);if(!r.getString("id").matches("[A-Za-z0-9:_-]{1,120}")||!ids.add(r.getString("id")))throw new IllegalArgumentException("Invalid race identity");for(String k:List.of("name","city","country","surface"))if(r.getString(k).trim().isEmpty()||r.getString(k).length()>180)throw new IllegalArgumentException("Invalid race "+k);LocalDate.parse(r.getString("date"));LocalDate.parse(r.getString("verified"));double km=r.getDouble("km");if(!Double.isFinite(km)||km<1||km>250)throw new IllegalArgumentException("Invalid race distance");String url=r.getString("source");URI u=URI.create(url);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||url.length()>1000)throw new IllegalArgumentException("Invalid event source");}
 }
 static List<JSONObject> search(JSONArray races,String query,String country,double distance,LocalDate from,LocalDate to)throws Exception{
  validate(races);if(to.isBefore(from))throw new IllegalArgumentException("End date must follow start date");String q=query.trim().toLowerCase(Locale.ROOT);List<JSONObject> out=new ArrayList<>();
  for(int i=0;i<races.length();i++){var r=races.getJSONObject(i);LocalDate date=LocalDate.parse(r.getString("date"));String text=(r.getString("name")+" "+r.getString("city")).toLowerCase(Locale.ROOT);if(!date.isBefore(from)&&!date.isAfter(to)&&text.contains(q)&&(country.equals("All countries")||r.getString("country").equalsIgnoreCase(country))&&(distance==0||Math.abs(r.getDouble("km")-distance)<.11))out.add(r);}
  out.sort(Comparator.comparing(r->r.optString("date")));return List.copyOf(out);
 }
}
