package app.rwun;
import java.time.*;import java.util.*;import org.json.*;
/** Top Events: attribution and factual metadata only; no artwork or description redistribution. */
public final class TopEventsRaceProvider implements RaceProvider {
 public String id(){return "top-events";}
 public String endpoint(int page,String since){return "https://topevents.co.za/wp-json/tribe/events/v1/events?per_page=50&page="+page;}
 public Page parse(String json,LocalDate today)throws Exception{
  JSONObject root=new JSONObject(json);JSONArray events=root.getJSONArray("events");List<RaceData.Race> races=new ArrayList<>();int skipped=0;
  for(int i=0;i<events.length();i++)try{
   JSONObject e=events.getJSONObject(i);if(!e.optString("status","publish").equals("publish"))continue;
   LocalDate date=RaceData.date(e.getString("start_date"));if(date.isBefore(today))continue;
   String categories=e.optJSONArray("categories")==null?"":e.getJSONArray("categories").toString();String title=RaceData.text(e.getString("title"),250);
   if((title+categories).toLowerCase(Locale.ROOT).matches(".*\\b(cycling|triathlon|swim|expo)\\b.*"))continue;
   JSONObject venue=e.optJSONObject("venue");if(venue==null)venue=new JSONObject();String surface=RaceData.terrain(title+" "+categories);
   String explicit=title;List<RaceData.Distance> distances=RaceData.distances(explicit,surface);
   String country=venue.optString("country");if(country.isBlank())country="ZA";
   String zone=e.optString("timezone");try{ZoneId.of(zone);}catch(Exception x){zone="";}
   String time=e.optBoolean("all_day")?"":RaceData.time(e.optString("start_date"));
   races.add(new RaceData.Race(id(),e.get("id").toString(),date.toString(),title,venue.optString("city"),venue.optString("province",venue.optString("state")),country,venue.optString("venue"),time,zone,surface,e.optString("cost"),"",e.optString("url"),e.optString("website"),e.optString("modified_utc",e.optString("modified")),distances));
  }catch(Exception bad){skipped++;}
  int pages=root.optInt("total_pages",1);return new Page(races,root.optInt("page",0)<pages,Math.max(1,pages),skipped);
 }
}
