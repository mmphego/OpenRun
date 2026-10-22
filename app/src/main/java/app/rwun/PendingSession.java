package app.rwun;
import org.json.*;
/** Reminder policy only. It never discards, completes or starts a session. */
final class PendingSession {
 static boolean needsReview(JSONObject a,long nowWall,long nowElapsed,int boot,long firstSeen){
  if(a==null)return false;
  if(a.has("boot")&&a.optInt("boot")!=boot)return true;
  long paused=a.optLong("pausedAtWall"),heartbeat=a.optLong("heartbeatWall"),started=a.optLong("startedAtWall");
  if(a.optBoolean("paused")&&paused>0)return nowWall>=paused&&nowWall-paused>=30*60*1000L;
  long seconds=a.optLong("seconds");
  if(a.has("resumed")&&!a.optBoolean("paused")&&a.optInt("boot")==boot)seconds+=Math.max(0,nowElapsed-a.optLong("resumed"))/1000;
  if(!a.optBoolean("paused")&&a.has("goal")){
   long limit=a.optInt("goal")*60L+30*60;
   long movement=Math.max(a.optLong("stepResume"),a.optLong("stepMovementNanos",a.optLong("stepEvent")));
   long quiet=movement>0?Math.max(0,nowElapsed-movement/1000000)/1000:seconds;
   if(seconds>=limit&&(!a.optBoolean("phoneSteps")||quiet>=30*60))return true;
  }
  if(a.has("lastMovementSeconds")&&seconds>=60*60&&seconds-a.optLong("lastMovementSeconds")>=30*60)return true;
  if(heartbeat>0)return nowWall>=heartbeat&&nowWall-heartbeat>=15*60*1000L;
  long anchor=started>0?started:firstSeen;
  return anchor>0&&nowWall>=anchor&&nowWall-anchor>=30*60*1000L;
 }
 static String identity(JSONObject a){return a==null?"":a.optString("id");}
}
