package app.rwun;
import android.content.Context;
import android.os.SystemClock;
import org.json.*;
/** Timer progression is independent of the Activity; distance steps remain explicitly confirmed. */
final class IndoorCoaching {
 static final long MAX_AUDIO_SECONDS=12*60*60;
 static WorkoutFactory.Workout definition(RunStore store,JSONObject a){
  for(var s:store.plannedSessions())if((store.planId()+":"+s.id()).equals(a.optString("planned")))return WorkoutFactory.create(s,store.profile());
  return null;
 }
 static boolean progress(JSONObject a,WorkoutFactory.Workout def,long seconds)throws Exception{
  if(a.optBoolean("paused")||def==null)return false;
  int step=a.optInt("step");long start=a.optLong("stepStart");int before=step;
  while(step<def.steps().size()){
   var current=def.steps().get(step);
   if(current.seconds()<=0||seconds-start<current.seconds())break;
   start+=current.seconds();step++;
  }
  if(step!=before)a.put("step",step).put("stepStart",start);
  return step!=before;
 }
 static void catchUp(Context c)throws Exception{
  synchronized(RunStore.LOCK){RunStore s=new RunStore(c);JSONObject d=s.v103(),a=d.optJSONObject("activeIndoor");if(a==null)return;
   if(progress(a,definition(s,a),V103Data.clock(a).value(SystemClock.elapsedRealtime(),IndoorState.boot(c))))s.v103(d);
  }
 }
 static String cueKey(JSONObject a,WorkoutFactory.Workout def,long seconds){
  if(def!=null)return "step:"+a.optInt("step");
  return seconds>=a.optInt("goal")*60L?"goal":"start";
 }
 static String cue(JSONObject a,WorkoutFactory.Workout def,long seconds){
  if(def!=null){int n=a.optInt("step");return n>=def.steps().size()?"Workout steps complete. Finish and review when ready.":def.steps().get(n).title()+". "+def.steps().get(n).target();}
  return seconds>=a.optInt("goal")*60L?"Your indoor time goal is reached. Finish and review when ready, or continue if you choose.":"Indoor "+a.optString("kind").toLowerCase(java.util.Locale.ROOT)+" started. Begin gently. Keep your phone in your pocket for steps.";
 }
 static boolean needsAudio(RunStore store,JSONObject a,long seconds){
  if(!store.audio()||a==null||a.optBoolean("paused")||seconds>=MAX_AUDIO_SECONDS)return false;
  var def=definition(store,a);
  return def==null?seconds<a.optInt("goal")*60L+30:a.optInt("step")<def.steps().size()||!a.optString("coachCueKey").equals("step:"+a.optInt("step"));
 }
 static boolean needsAwake(JSONObject a,WorkoutFactory.Workout def,long seconds){
  if(def==null)return seconds<a.optInt("goal")*60L;
  int n=a.optInt("step");return n<def.steps().size()&&def.steps().get(n).seconds()>0;
 }
}
