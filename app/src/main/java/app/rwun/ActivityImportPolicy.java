package app.rwun;
import org.json.*;
/** Similarity is a reason to ask the user, never evidence for silently deleting an activity. */
final class ActivityImportPolicy {
 static boolean possible(JSONObject incoming,JSONObject old,JSONObject a,JSONObject b){
  double km=incoming.optDouble("km"),other=old.optDouble("km");
  long x=incoming.optLong("seconds"),y=old.optLong("seconds");
  if(a!=null&&b!=null&&a.optLong("started")>0&&b.optLong("started")>0&&a.optLong("ended")>a.optLong("started")&&b.optLong("ended")>b.optLong("started")){
   long startA=a.optLong("started"),endA=a.optLong("ended"),startB=b.optLong("started"),endB=b.optLong("ended");
   long overlap=Math.max(0,Math.min(endA,endB)-Math.max(startA,startB));
   return endA>startA&&endB>startB&&Math.abs(startA-startB)<=180000&&overlap>=.8*Math.min(endA-startA,endB-startB)&&(unknownDistance(incoming)||unknownDistance(old)||Math.abs(km-other)<=Math.max(.25,Math.max(km,other)*.08));
  }
  return incoming.optString("date").equals(old.optString("date"))&&Math.abs(km-other)<=Math.max(.1,Math.max(km,other)*.03)&&Math.abs(x-y)<=Math.max(60,Math.min(x,y)*.03);
 }
 static boolean unknownDistance(JSONObject row){return row.optDouble("km",-1)==0&&row.has("distanceKnown")&&!row.optBoolean("distanceKnown",true);}
 static void validateOrigin(JSONObject o)throws Exception{
  if(o.optString("provider").length()>200||o.optString("externalId").length()>250)throw new IllegalArgumentException("Invalid import origin");
  for(String k:new String[]{"started","ended"})V103Data.whole(o,k,0,1000000000000000L);
  if(o.getLong("ended")<o.getLong("started"))throw new IllegalArgumentException("Invalid import interval");
 }
}
