package app.rwun;
import java.time.*;import java.net.URLEncoder;import java.nio.charset.StandardCharsets;import java.util.*;import org.json.*;
/** Requires operator approval for catalogue caching and a server proxy holding caller credentials. */
public final class RunSignupRaceProvider implements RaceProvider {
 private final String proxy;private final boolean approved;
 public RunSignupRaceProvider(){this("",false);}public RunSignupRaceProvider(String proxy,boolean approved){this.proxy=proxy;this.approved=approved;}
 public String id(){return "runsignup";}public boolean incremental(){return true;}
 public String blockedReason(){return !approved?"Automatic catalogue caching needs RunSignup approval.":!proxy.startsWith("https://")?"Configure the approved RunSignup proxy; caller secrets must stay off the phone.":"";}
 public String endpoint(int page,String since){String base=proxy.isBlank()?"https://api.runsignup.com/rest/races":proxy;return base+(base.contains("?")?"&":"?")+"format=json&country=ZA&events=T&results_per_page=100&page="+page+(since.isBlank()?"":"&modified_since="+android.net.Uri.encode(since));}
 public Page parse(String json,LocalDate today)throws Exception{
  JSONArray rows=new JSONObject(json).getJSONArray("races");List<RaceData.Race> out=new ArrayList<>();int skipped=0;
  for(int i=0;i<rows.length();i++)try{JSONObject r=rows.getJSONObject(i).getJSONObject("race");JSONObject address=r.optJSONObject("address");if(address==null)address=new JSONObject();if(!RaceData.countryCode(address.optString("country_code")).equals("ZA"))continue;
   if(r.optString("is_private_race").equals("T")||r.optString("is_draft_race").equals("T"))continue;
   LocalDate next=RaceData.date(r.getString("next_date"));JSONArray events=r.optJSONArray("events");Map<String,List<RaceData.Distance>> byDate=new TreeMap<>();Map<String,String> times=new HashMap<>();
   if(events!=null)for(int j=0;j<events.length();j++){JSONObject e=events.getJSONObject(j);String name=RaceData.text(e.optString("name"),300);if(e.optString("volunteer").equals("T")||!e.optString("event_type","running_race").equals("running_race")||name.toLowerCase(Locale.ROOT).matches(".*(fundraiser only|virtual|donation).*"))continue;
    LocalDate date=e.optString("start_time").isBlank()?next:RaceData.date(e.getString("start_time"));if(date.isBefore(today))continue;String surface=RaceData.terrain(name+" "+e.optString("details"));List<RaceData.Distance> distances=RaceData.distances(e.optString("distance"),surface);if(distances.isEmpty())distances=RaceData.distances(name,surface);List<RaceData.Distance> variants=byDate.computeIfAbsent(date.toString(),x->new ArrayList<>());for(var distance:distances)variants.add(new RaceData.Distance(distance.metres(),distance.label(),distance.surface(),e.optString("event_id")));times.put(date.toString(),RaceData.time(e.optString("start_time")));}
   if(byDate.isEmpty()){if(events!=null&&events.length()>0)continue;if(!next.isBefore(today))byDate.put(next.toString(),List.of());}
   for(var entry:byDate.entrySet())out.add(new RaceData.Race(id(),r.get("race_id").toString()+(byDate.size()>1?":"+entry.getKey():""),entry.getKey(),r.getString("name"),address.optString("city"),address.optString("state"),"ZA",address.optString("street"),times.getOrDefault(entry.getKey(),""),r.optString("timezone"),RaceData.terrain(r.optString("name")),"","",r.optString("url"),r.optString("external_race_url",r.optString("url")),r.optString("last_modified"),entry.getValue()));
  }catch(Exception bad){skipped++;}
  return new Page(out,rows.length()==100,0,skipped);
 }
}
