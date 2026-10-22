package app.rwun;
import java.time.*;import java.util.*;import org.json.*;
public final class BieldRaceProvider implements RaceProvider {
 public String id(){return "bield";}
 public String endpoint(int page,String since){return "https://app.bield.run/api/races/public.json?page="+page;}
 public Page parse(String json,LocalDate today)throws Exception{
  JSONObject root=new JSONObject(json);JSONArray rows=root.getJSONArray("races");JSONObject meta=root.getJSONObject("meta");List<RaceData.Race> out=new ArrayList<>();int skipped=0;
  for(int i=0;i<rows.length();i++)try{JSONObject r=rows.getJSONObject(i);LocalDate date=RaceData.date(r.getString("date"));if(date.isBefore(today)||!r.optString("status","published").equals("published"))continue;
   String surface=RaceData.terrain(r.optString("terrain"));List<RaceData.Distance> options=new ArrayList<>();double km=r.optDouble("distance_km",0);if(Double.isFinite(km)&&km>=.1&&km<=1000)options.add(new RaceData.Distance((int)Math.round(km*1000),String.format(Locale.ROOT,"%s km",km),surface));
   out.add(new RaceData.Race(id(),r.getString("id"),date.toString(),r.getString("name"),r.optString("city"),r.optString("province"),r.optString("country"),r.optString("location"),"","",surface,"","",r.optString("url"),r.optString("registration_url",r.optString("website_url")),r.optString("updated_at"),options));
  }catch(Exception bad){skipped++;}
  return new Page(out,meta.getInt("page")<meta.getInt("pages"),meta.getInt("pages"),skipped);
 }
}
