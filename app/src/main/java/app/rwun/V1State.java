package app.rwun;

import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** Bounded local beta data; no identifiers, routes, health samples or network telemetry. */
final class V1State {
  static final Set<String> EVENTS=Set.of("app_open","setup_started","plan_created","recording_started","run_saved","guide_opened","card_saved");
  static Set<String> keys(JSONObject o){Set<String> out=new HashSet<>();var it=o.keys();while(it.hasNext())out.add(it.next());return out;}
  static JSONObject empty(){return new JSONObject();}
  static void validate(JSONObject data)throws Exception {
    for(var k:keys(data))if(!Set.of("activities","metrics","events","haptics","intro").contains(k))throw new IllegalArgumentException("Unknown v1 setting");
    for(String key:List.of("metrics","haptics","intro"))if(data.has(key)&&!(data.get(key) instanceof Boolean))throw new IllegalArgumentException("Invalid v1 toggle");
    JSONArray rows=data.optJSONArray("activities");if(data.has("activities")&&rows==null)throw new IllegalArgumentException("Invalid activities");
    if(rows!=null){if(rows.length()>1000)throw new IllegalArgumentException("Maximum 1000 additional activities; delete older entries first");Set<String> ids=new HashSet<>();Map<LocalDate,Integer> perDay=new HashMap<>();for(int i=0;i<rows.length();i++){var row=rows.getJSONObject(i);var entry=read(row);if(perDay.merge(entry.date(),1,Integer::sum)>10)throw new IllegalArgumentException("Maximum 10 additional activities per day");if(!ids.add(row.getString("id")))throw new IllegalArgumentException("Duplicate activity");}}
    JSONObject events=data.optJSONObject("events");if(data.has("events")&&events==null)throw new IllegalArgumentException("Invalid metrics");
    if(events!=null){if(events.length()>90)throw new IllegalArgumentException("Invalid metric history");for(var day:keys(events)){LocalDate.parse(day);JSONObject counts=events.getJSONObject(day);for(var event:keys(counts)){Object count=counts.get(event);if(!EVENTS.contains(event)||!(count instanceof Number)||counts.getDouble(event)!=Math.rint(counts.getDouble(event))||counts.getDouble(event)<0||counts.getDouble(event)>10000)throw new IllegalArgumentException("Invalid event counter");}}}
  }
  static WeekBalance.Other read(JSONObject row)throws Exception {for(String key:keys(row))if(!Set.of("id","date","type","minutes","effort","completed").contains(key))throw new IllegalArgumentException("Unknown activity field");for(String k:List.of("minutes","effort"))if(!(row.get(k) instanceof Number)||row.getDouble(k)!=Math.rint(row.getDouble(k)))throw new IllegalArgumentException("Whole numbers required");if(row.getDouble("minutes")<5||row.getDouble("minutes")>720||row.getDouble("effort")<1||row.getDouble("effort")>10)throw new IllegalArgumentException("Activity values outside bounds");return new WeekBalance.Other(row.getString("id"),LocalDate.parse(row.getString("date")),row.getString("type"),row.getInt("minutes"),row.getInt("effort"),row.getBoolean("completed"));}
  static List<WeekBalance.Other> activities(JSONObject data){try{List<WeekBalance.Other> out=new ArrayList<>();JSONArray rows=data.optJSONArray("activities");if(rows!=null)for(int i=0;i<rows.length();i++)out.add(read(rows.getJSONObject(i)));out.sort(Comparator.comparing(WeekBalance.Other::date));return out;}catch(Exception e){throw new IllegalStateException("Additional activity data cannot be read",e);}}
  static void event(JSONObject data,String name,LocalDate today)throws Exception{
    if(!data.optBoolean("metrics",false))return;if(!EVENTS.contains(name))throw new IllegalArgumentException("Unknown event");
    JSONObject days=data.optJSONObject("events");if(days==null)days=new JSONObject();for(String date:new ArrayList<>(keys(days)))if(LocalDate.parse(date).isBefore(today.minusDays(89))||LocalDate.parse(date).isAfter(today))days.remove(date);
    JSONObject counts=days.optJSONObject(today.toString());if(counts==null)counts=new JSONObject();counts.put(name,Math.min(10000,counts.optInt(name)+1));days.put(today.toString(),counts);data.put("events",days);
  }
  static String report(JSONObject data,LocalDate today){JSONObject days=data.optJSONObject("events");if(days==null)days=new JSONObject();int active7=0,active28=0;Map<String,Integer> totals=new TreeMap<>();for(var day:keys(days)){LocalDate date=LocalDate.parse(day);if(date.isAfter(today)||date.isBefore(today.minusDays(27)))continue;JSONObject counts=days.optJSONObject(day);if(counts==null)continue;if(counts.optInt("app_open")>0){active28++;if(!date.isBefore(today.minusDays(6)))active7++;}for(String e:EVENTS)totals.merge(e,counts.optInt(e),Integer::sum);}
    StringBuilder result=new StringBuilder("OpenRun 1.0 · local usage summary\nActive days: "+active7+" / 7; "+active28+" / 28\nLast 28 days event totals:\n");totals.forEach((k,v)->result.append(k).append(": ").append(v).append('\n'));return result+"\nCounts on this installation only. Not cohort retention or unique users. No names, locations, route or health records included. No automatic upload.";
  }
}
