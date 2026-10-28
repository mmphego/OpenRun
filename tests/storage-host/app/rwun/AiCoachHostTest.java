package app.rwun;

import android.content.Context;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

public final class AiCoachHostTest {
  static int assertions;static RunStore store;static Context context;
  interface Action {void run()throws Exception;}
  static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
  static void reject(Action action)throws Exception{String before=store.backupPreferences().toString();try{action.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException|IllegalStateException|java.io.IOException|JSONException expected){}check(before.equals(store.backupPreferences().toString()),"rejection preserves all data");}
  static void setup()throws Exception{context=new Context(Files.createTempDirectory("ai-coach").toFile());store=new RunStore(context);store.save(new PlanEngine.Config(8,3,12,10,LocalDate.now().plusDays(1)));}
  static JSONObject settings()throws Exception{return new JSONObject().put("goalPace",330);}
  static void history(int count,boolean fatigue){for(int i=0;i<count;i++)store.add(new RunStore.Run("history:"+i,LocalDate.now().minusDays(i).toString(),5,35,5,fatigue?4:2,false,"Manual","private-route.gpx",155,2100));}
  static String response(AiCoachPolicy.Request request,double km,double pace)throws Exception{return new JSONObject().put("explanation","Build distance gradually and keep the next run comfortable. The goal is an estimate.").put("sessions",new JSONArray().put(new JSONObject().put("id",request.sessions().get(0).id()).put("km",km).put("paceSecondsPerKm",pace).put("reason","Use an easy effort and review recovery."))).toString();}
  public static void main(String[] args)throws Exception{
    setup();var missing=AiCoachPolicy.snapshot(store,settings(),false);check(missing.observedPace()==0&&!missing.increase(),"unknown baseline stays unknown");
    reject(()->AiCoachPolicy.validate(missing,response(missing,missing.sessions().get(0).km(),400)));
    var effort=AiCoachPolicy.validate(missing,response(missing,missing.sessions().get(0).km(),0));check(!effort.sessions().get(0).instructions().startsWith("Coach pace"),"missing history uses effort only");
    history(3,false);var request=AiCoachPolicy.snapshot(store,settings(),false);
    check(request.increase()&&request.observedPace()==420,"three recent reviewed days support bounded increase");
    check(!request.payload().toString().contains("private-route")&&!request.payload().toString().contains("155"),"no routes or heart rate sent");
    var auto=AiCoachPolicy.snapshot(store,settings(),true);check(auto.sessions().stream().allMatch(s->s.date().isBefore(LocalDate.now().plusDays(8))),"automatic changes bounded to seven days");
    var first=request.sessions().get(0);double cap=request.caps().get(first.id());
    reject(()->AiCoachPolicy.validate(request,response(request,cap+.01,450)));
    reject(()->AiCoachPolicy.validate(request,response(request,first.km(),419)));
    reject(()->AiCoachPolicy.validate(request,response(request,first.km(),Double.NaN)));
    JSONObject unknown=new JSONObject(response(request,first.km(),450));unknown.getJSONArray("sessions").getJSONObject(0).put("id","foreign-plan");reject(()->AiCoachPolicy.validate(request,unknown.toString()));
    JSONObject duplicate=new JSONObject(response(request,first.km(),450));duplicate.getJSONArray("sessions").put(duplicate.getJSONArray("sessions").getJSONObject(0));reject(()->AiCoachPolicy.validate(request,duplicate.toString()));
    var proposal=AiCoachPolicy.validate(request,response(request,cap,450));String activities=store.runs().toString();int commits=context.preferenceCommits();
    store.applyAiPlan(request.fingerprint(),proposal.sessions(),proposal.explanation(),"OpenAI",request.runToken());
    check(context.preferenceCommits()==commits+1&&store.aiHistoryReviewed(),"update and processed run token commit atomically");
    check(new RunStore(context).aiHistoryReviewed()&&store.runs().toString().equals(activities),"update survives repository recreation and preserves history");
    check(store.planChanges().getJSONObject(0).getString("kind").equals("AI Coach update"),"AI changes have provenance and history");
    var updated=store.plannedSessions().get(0);check(WorkoutFactory.coachPace(updated.instructions())==450,"saved pace is readable");
    check(WorkoutFactory.create(updated,store.profile()).steps().get(1).pace().fastSeconds()==450,"coach pace reaches workout guidance");
    reject(()->store.applyAiPlan(request.fingerprint(),proposal.sessions(),"duplicate","OpenAI",request.runToken()));
    var next=AiCoachPolicy.snapshot(store,settings(),false);check(next.caps().get(first.id())<=store.aiBaselineKm(first.id())*1.04+.001,"repeated reviews cannot compound workload increases");
    reject(()->AiCoachPolicy.validate(next,response(next,next.sessions().get(0).km(),420)));
    store.undoPlanChange(store.previewUndo());check(store.plannedSessions().get(0).instructions().equals(first.instructions()),"undo restores original workout and pace");
    check(store.runs().toString().equals(activities),"undo keeps completed runs");
    var stale=AiCoachPolicy.snapshot(store,settings(),false);store.add(new RunStore.Run("new-run",LocalDate.now().toString(),3,20));reject(()->store.applyAiPlan(stale.fingerprint(),proposal.sessions(),"stale","Gemini",stale.runToken()));
    store.adapt(new AdaptiveCoach.Advice(0,true,"Pain reported"));reject(()->AiCoachPolicy.snapshot(store,settings(),true));
    setup();history(1,true);var tired=AiCoachPolicy.snapshot(store,settings(),true);check(!tired.increase()&&tired.caps().get(tired.sessions().get(0).id())<=tired.sessions().get(0).km()*.8+.001,"high fatigue caps volume at reduction");
    var old=tired.sessions().get(0);var reduced=AiCoachPolicy.validate(tired,response(tired,Math.max(.1,Math.floor(old.km()*.8*100)/100),450));
    store.add(new RunStore.Run(store.planId()+":"+old.id(),old.date().toString(),old.km(),20));reject(()->store.applyAiPlan(store.proposalFingerprint(),reduced.sessions(),"protected","Gemini",store.aiHistoryToken()));
    String text="{\"explanation\":\"Keep easy\",\"sessions\":[]}";
    JSONObject openai=new JSONObject().put("status","completed").put("output",new JSONArray().put(new JSONObject().put("type","reasoning")).put(new JSONObject().put("type","message").put("content",new JSONArray().put(new JSONObject().put("type","output_text").put("text",text)))));
    check(AiCoachClient.extract("OpenAI",openai).equals(text),"Responses API output parsed after reasoning item");
    JSONObject gemini=new JSONObject().put("candidates",new JSONArray().put(new JSONObject().put("finishReason","STOP").put("content",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("thought",true).put("text","internal")).put(new JSONObject().put("text",text))))));
    check(AiCoachClient.extract("Gemini",gemini).equals(text),"Gemini text parsed and thoughts excluded");
    openai.put("status","incomplete");reject(()->AiCoachClient.extract("OpenAI",openai));
    gemini.getJSONArray("candidates").getJSONObject(0).put("finishReason","MAX_TOKENS");reject(()->AiCoachClient.extract("Gemini",gemini));
    check(WorkoutFactory.coachPace("Coach pace: 9:99 min/km.\n") == 0,"malformed pace ignored");
    System.out.println("AI Coach: "+assertions+" assertions passed");
  }
}
