package app.rwun;

import android.content.Context;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import org.json.*;

/** Synthetic storage/domain acceptance. No provider credentials or network calls. */
public final class AiPlanningHostTest {
  static int assertions;static RunStore store;static Context context;
  interface Action{void run()throws Exception;}
  static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
  static void rejected(Action action)throws Exception{
    String before=store.backupPreferences().toString();
    try{action.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException|IllegalStateException|JSONException expected){}
    check(before.equals(store.backupPreferences().toString()),"Rejected response leaves all saved data intact");
  }
  // These fixtures deliberately complete future planned sessions; never depend on today being a rest day.
  static RaceTraining.Draft baseline(){return RaceTraining.goalDraft(LocalDate.now().plusDays(1),10,8,Set.of(DayOfWeek.TUESDAY,DayOfWeek.THURSDAY,DayOfWeek.SUNDAY),DayOfWeek.SUNDAY,12,PaceProfile.unset(),List.of(),store.resting(),"distance");}
  static JSONObject response(RaceTraining.Draft baseline)throws Exception{
    JSONArray rows=new JSONArray();
    for(var s:baseline.sessions())rows.put(new JSONObject().put("id",s.id()).put("km",ScheduleProposer.demanding(s.title())||s.title().equals("Run / walk")?s.km():s.km()*.83).put("instructions","Keep a conversational effort; walk breaks are welcome."));
    return new JSONObject().put("explanation","Reduced easy runs to fit current training evidence; goal pace is uncertain.").put("sessions",rows);
  }
  static JSONObject options(RaceTraining.Draft b)throws Exception{return new JSONObject().put("mode",b.preferences().mode()).put("weekdays",new JSONArray().put(2).put(4).put(7)).put("longDay",7);}
  public static void main(String[] args)throws Exception{
    context=new Context(Files.createTempDirectory("ai-full-plan").toFile());store=new RunStore(context);
    var b=baseline();JSONObject valid=response(b);var personalised=AiFullPlanPolicy.validate(b,valid.toString());
    check(personalised.size()==b.sessions().size(),"Every session retained");
    check(!personalised.equals(b.sessions()),"Personalisation actually changes the schedule");
    for(int i=0;i<personalised.size();i++){var a=personalised.get(i);var old=b.sessions().get(i);check(a.id().equals(old.id())&&a.date().equals(old.date())&&a.title().equals(old.title()),"Dates, IDs and types retained");if(ScheduleProposer.demanding(old.title()))check(a.instructions().equals(old.instructions())&&a.km()==old.km(),"Quality prescription retained");}
    rejected(()->AiFullPlanPolicy.validate(b,"not json"));
    rejected(()->AiFullPlanPolicy.validate(b,"{}"));
    JSONObject missing=response(b);missing.getJSONArray("sessions").remove(0);rejected(()->AiFullPlanPolicy.validate(b,missing.toString()));
    JSONObject duplicate=response(b);duplicate.getJSONArray("sessions").put(1,duplicate.getJSONArray("sessions").get(0));rejected(()->AiFullPlanPolicy.validate(b,duplicate.toString()));
    for(Object bad:List.of(-1,0,1000,"3.2")){JSONObject out=response(b);out.getJSONArray("sessions").getJSONObject(0).put("km",bad);rejected(()->AiFullPlanPolicy.validate(b,out.toString()));}
    JSONObject tooMuch=response(b);tooMuch.getJSONArray("sessions").getJSONObject(0).put("km",b.sessions().get(0).km()+.001);rejected(()->AiFullPlanPolicy.validate(b,tooMuch.toString()));
    JSONObject unknown=response(b);unknown.getJSONArray("sessions").getJSONObject(0).put("id","foreign");rejected(()->AiFullPlanPolicy.validate(b,unknown.toString()));
    JSONObject injectedPace=response(b);injectedPace.getJSONArray("sessions").getJSONObject(0).put("instructions","Coach pace: 3:00 min/km.\nRun at this pace.");rejected(()->AiFullPlanPolicy.validate(b,injectedPace.toString()));
    JSONObject noWhy=response(b);noWhy.put("explanation","");rejected(()->AiFullPlanPolicy.validate(b,noWhy.toString()));
    // Store precisely the validated proposal, using the planner's supported distance precision, instead of regenerating rules.
    store.createStructuredPlan(store.proposalFingerprint(),b.config(),PaceProfile.unset(),null,options(b),personalised,"AI plan created",valid.getString("explanation"),"Provider: synthetic; recent running evidence reviewed.");
    check(store.plannedSessions().equals(personalised),"Saved AI schedule equals the complete accepted preview");
    store=new RunStore(context);check(store.plannedSessions().equals(personalised),"Reopening displays the AI schedule");
    check(store.planChanges().getJSONObject(0).getString("reason").equals(valid.getString("explanation")),"Creation explanation persists in audit history");
    rejected(()->store.previewUndo());
    JSONObject backup=store.backupPreferences();store.restorePreferences(backup);check(store.plannedSessions().equals(personalised),"Backup/upgrade-compatible preferences retain AI schedule");
    check(TrainingProgress.from(store,b.config().start().minusDays(1)).phase().equals("Upcoming"),"Short pre-plan intervals are upcoming, not build week one");
    check(TrainingProgress.from(store,b.config().start().plusWeeks(3)).phase().equals("Recovery week"),"Fourth week is recovery");
    check(TrainingProgress.from(store,b.config().start().plusWeeks(6)).phase().equals("Taper / finish"),"Final fortnight is taper");
    check(TrainingProgress.from(store,b.config().start().plusWeeks(8)).phase().equals("Plan finished"),"Finished plan is explicit");
    var skipped=store.plannedSessions().get(1);store.skip(store.planId()+":"+skipped.id(),true);
    var adherence=TrainingProgress.from(store,skipped.date());
    check(adherence.plannedWorkouts()==2&&adherence.completedWorkouts()==0,"Skipped due sessions remain in the progress denominator");
    String plan=store.planId();var first=store.plannedSessions().get(0);String linked=plan+":"+first.id();
    RunStore.Run imported=new RunStore.Run("health:stable",first.date().toString(),3,20,5,2,false,"Health Connect · Synthetic","",0,1200);
    JSONObject origin=RunStore.origin("Synthetic","stable",1000,1201000);
    store.importActivity(imported,false,origin);store.matchWorkout(imported.sessionId(),linked);
    String token=store.aiHistoryToken();store.importActivity(imported,false,origin);
    check(store.runs().size()==1&&token.equals(store.aiHistoryToken()),"Identical reimport is idempotent, including AI history token");
    var progress=TrainingProgress.from(store,first.date());check(progress.completedWorkouts()==1&&progress.actualKm()==3,"Matched imported activity counts exactly once");
    check(!progress.recommendation().contains(first.date()+" · "),"Matched workout is excluded from next-run recommendation");
    // Completing a local counterpart cannot double-count a matched session in progress.
    rejected(()->store.add(new RunStore.Run(linked,first.date().toString(),3,20)));
    check(TrainingProgress.from(store,first.date()).completedWorkouts()==1&&TrainingProgress.from(store,first.date()).actualKm()==3,"Local plus explicit match count one workout");
    String history=store.runs().toString();var protectedFirst=store.plannedSessions().get(0);
    store.adapt(new AdaptiveCoach.Advice(.8,false,"Fatigue reported"));
    check(store.plannedSessions().get(0).equals(protectedFirst),"Completed target is protected during adaptation");
    store.adapt(new AdaptiveCoach.Advice(0,true,"Pain reported"));
    rejected(()->AiCoachPolicy.snapshot(store,new JSONObject(),true));
    check(TrainingProgress.from(store,LocalDate.now()).recommendation().startsWith("Rest"),"Recovery hold blocks next-run prescription");
    var nextBaseline=baseline();store.createGoalPlan(store.proposalFingerprint(),nextBaseline.config(),PaceProfile.unset(),options(nextBaseline),nextBaseline.sessions());
    check(store.resting()&&store.runs().toString().equals(history),"Plan replacement retains runs and recovery hold");
    check(TrainingProgress.from(store,first.date()).completedWorkouts()==0,"Old plan completions do not leak into a new plan");
    String prompt=AiFullPlanPolicy.prompt(nextBaseline,store,new JSONObject().put("goalPace",330));
    JSONObject payload=new JSONObject(prompt.substring(prompt.indexOf('{',prompt.indexOf('\n'))));
    check(payload.getInt("goalPaceSecondsPerKm")==330&&payload.getBoolean("trainingHold"),"Goal pace and recovery hold supplied explicitly");
    // Imported future sessions are deliberately absent from recent history.
    check(!prompt.contains("route-")&&!prompt.contains("stable")&&!prompt.contains("Synthetic"),"No activity IDs, routes or provider identity in prompt");
    check(AiCoachClient.retryable(new AiCoachClient.ProviderFailure(429,"quota")),"Rate limit can retry");
    check(AiCoachClient.retryable(new AiCoachClient.ProviderFailure(503,"temporary")),"Provider outage can retry");
    check(!AiCoachClient.retryable(new AiCoachClient.ProviderFailure(401,"key")),"Rejected credentials do not retry");
    check(!AiCoachClient.retryable(new JSONException("invalid")),"Malformed plans do not retry");
    RunStore.Run recent=new RunStore.Run("health:recent",LocalDate.now().toString(),4,30,5,2,false,"Health Connect · Synthetic","",0,1800);
    store.importActivity(recent,false,RunStore.origin("Synthetic","recent",2000000,3800000));
    String withImport=AiFullPlanPolicy.prompt(nextBaseline,store,new JSONObject());
    JSONObject recentPayload=new JSONObject(withImport.substring(withImport.indexOf('{',withImport.indexOf('\n'))));
    JSONObject row=recentPayload.getJSONArray("recentRuns").getJSONObject(0);
    check(row.isNull("effort")&&row.isNull("fatigue")&&row.isNull("pain"),"Unreported imported feedback stays unknown in full-plan prompt");
    check(recentPayload.getJSONArray("recentRuns").length()==1,"Future completed imports are excluded from recent training evidence");
    RunStore legacy=new RunStore(new Context(Files.createTempDirectory("legacy-ai-override").toFile()));
    legacy.save(new PlanEngine.Config(8,3,12,10,LocalDate.now()));var original=legacy.plannedSessions().get(0);
    JSONObject legacyData=legacy.v103();legacyData.put("overrides",new JSONObject().put(legacy.planId()+":"+original.id(),new JSONObject().put("date",original.date().toString()).put("km",3.141).put("factor",1).put("title",original.title()).put("instructions","Legacy reviewed guidance")));legacy.v103(legacyData);
    check(legacy.plannedSessions().get(0).km()==3.1,"Legacy overrides keep their prior rounding on upgrade");
    legacy.restorePreferences(legacy.backupPreferences());check(legacy.plannedSessions().get(0).km()==3.1,"Legacy precision survives backup restore without a destructive migration");
    conversationAndGoals();
    transport();
    System.out.println("AI full planning: "+assertions+" assertions passed");
  }
  static void conversationAndGoals() throws Exception {
    context=new Context(Files.createTempDirectory("ai-goals").toFile());store=new RunStore(context);
    JSONObject defaults=new JSONObject().put("goalPace",500);
    String empty=AiCoachConversation.prompt(store,defaults,"Break down my recent runs");
    check(empty.contains("\"hasPlan\":false")&&empty.contains("\"recentRuns\":[]"),"Coach questions work without a plan or runs");
    store.add(new RunStore.Run("unlinked-run",LocalDate.now().toString(),5,35,5,2,false,"Manual","route-private.jsonl",155,2100));
    String before=store.backupPreferences().toString();String question=AiCoachConversation.prompt(store,defaults,"Break down my recent runs");
    check(question.contains("420")&&!question.contains("route-private")&&!question.contains("155")&&!question.contains("unlinked-run"),"Conversation receives pace and summaries, excludes identifiers/routes/HR");
    check(AiCoachConversation.validate("{\"answer\":\"Your recent 5 km took 35 minutes. Build consistency first.\"}").contains("35 minutes"),"Conversational answer does not require workout proposals");
    check(store.backupPreferences().toString().equals(before),"Asking questions never changes the plan or activity history");
    rejected(()->AiCoachConversation.validate("{\"answer\":\" \"}"));
    rejected(()->AiCoachConversation.validate("{\"answer\":42}"));
    rejected(()->AiCoachConversation.validate("{\"explanation\":\"change\",\"sessions\":[]}"));
    rejected(()->AiCoachConversation.validate(new JSONObject().put("answer","a".repeat(8001)).toString()));
    var b=baseline();JSONObject options=options(b).put("goalPace",330);JSONObject out=response(b);
    int easy=0;while(ScheduleProposer.demanding(b.sessions().get(easy).title())||b.sessions().get(easy).title().equals("Run / walk"))easy++;
    out.getJSONArray("sessions").getJSONObject(easy).put("paceSecondsPerKm",450);
    var accepted=AiFullPlanPolicy.validate(b,out.toString(),420);
    check(WorkoutFactory.coachPace(accepted.get(easy).instructions())==450,"Full AI plan accepts a numeric pace bounded by actual running");
    rejected(()->AiFullPlanPolicy.validate(b,out.toString(),0));
    JSONObject fast=new JSONObject(out.toString());fast.getJSONArray("sessions").getJSONObject(easy).put("paceSecondsPerKm",419);
    rejected(()->AiFullPlanPolicy.validate(b,fast.toString(),420));
    fast.getJSONArray("sessions").getJSONObject(easy).put("paceSecondsPerKm","450");rejected(()->AiFullPlanPolicy.validate(b,fast.toString(),420));
    store.createStructuredPlan(store.proposalFingerprint(),b.config(),PaceProfile.unset(),null,options,accepted,"AI plan created","Working towards 10 km at 5:30; start with conversational running.","Synthetic evidence");
    store=new RunStore(context);check(store.goalPace(defaults)==330&&store.config().goalKm()==10,"Plan owns distance and pace goals independently of provider defaults");
    store.restorePreferences(store.backupPreferences());check(store.goalPace(defaults)==330&&store.plannedSessions().equals(accepted),"Pace goal and accepted AI paces survive restart/backup restore");
    check(AiCoachPolicy.snapshot(store,defaults,true).payload().getInt("goalPaceSecondsPerKm")==330,"Automatic reviews use the saved plan goal");
    store.add(new RunStore.Run("new-evidence",LocalDate.now().toString(),5,33,5,2,false,"Manual","",0,1950));
    var request=AiCoachPolicy.snapshot(store,defaults,true);
    var paced=request.sessions().stream().filter(s->WorkoutFactory.coachPace(s.instructions())==450).findFirst().orElseThrow();
    String revision=new JSONObject().put("explanation","Recent pace improved; a small easy-pace step towards the saved goal, with unchanged volume.")
        .put("sessions",new JSONArray().put(new JSONObject().put("id",paced.id()).put("km",paced.km()).put("paceSecondsPerKm",441).put("reason","New run supports a small pace step; conversational effort still takes priority."))).toString();
    var proposal=AiCoachPolicy.validate(request,revision);
    store.applyAiPlan(request.fingerprint(),proposal.sessions(),proposal.explanation(),"Synthetic",request.runToken());
    check(store.aiHistoryReviewed()&&WorkoutFactory.coachPace(store.plannedSessions().stream().filter(s->s.id().equals(paced.id())).findFirst().orElseThrow().instructions())==441,"New running evidence supports a bounded goal-driven post-run pace update");
    rejected(()->store.applyAiPlan(request.fingerprint(),proposal.sessions(),proposal.explanation(),"Synthetic",request.runToken()));
    check(AiCoachConversation.prompt(store,defaults,"How am I progressing?").contains("\"goalPaceSecondsPerKm\":330"),"Conversation uses saved goal and progress");
    JSONObject bad=store.options().put("goalPace",179);rejected(()->store.options(bad));
    store.adapt(new AdaptiveCoach.Advice(0,true,"Pain reported"));
    check(AiCoachConversation.prompt(store,defaults,"What next?").contains("\"trainingHold\":true"),"Coach can answer during a hold with explicit recovery context");
    rejected(()->AiCoachPolicy.snapshot(store,defaults,true));
    check(store.runs().size()==2,"Goal persistence, restore, adaptation and hold preserve completed runs");
    store.save(new PlanEngine.Config(8,3,12,5,LocalDate.now()));check(!store.options().has("goalPace"),"An unrelated replacement does not inherit the old plan goal");
  }
  static final class FakeConnection extends java.net.HttpURLConnection {
    final java.io.ByteArrayOutputStream sent=new java.io.ByteArrayOutputStream();final int code;final String body;boolean disconnected;
    FakeConnection(java.net.URL url,int code,String body){super(url);this.code=code;this.body=body;}
    public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}public void connect(){}
    public java.io.OutputStream getOutputStream(){return sent;}
    public int getResponseCode(){return code;}
    public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  }
  static void transport()throws Exception{
    for(String provider:List.of("OpenAI","Gemini")){
      String content="{\"connection\":\"ok\"}";
      JSONObject envelope=provider.equals("OpenAI")?new JSONObject().put("status","completed").put("output",new JSONArray().put(new JSONObject().put("type","message").put("content",new JSONArray().put(new JSONObject().put("type","output_text").put("text",content))))):new JSONObject().put("candidates",new JSONArray().put(new JSONObject().put("finishReason","STOP").put("content",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",content))))));
      JSONObject settings=new JSONObject().put("provider",provider).put("model","test-model").put("key","synthetic-test-key");
      FakeConnection[] captured=new FakeConnection[1];
      String answer=AiCoachClient.generate(settings,"Return JSON connection status",url->captured[0]=new FakeConnection(url,200,envelope.toString()));
      FakeConnection connection=captured[0];
      check(answer.equals(content)&&connection.disconnected,"Both provider response adapters work and close transport");
      check(connection.getURL().getProtocol().equals("https")&&connection.getURL().getQuery()==null&&!connection.getInstanceFollowRedirects(),"TLS endpoint has no URL key and does not redirect");
      check(!connection.sent.toString("UTF-8").contains("synthetic-test-key"),"Key stays in request header, outside prompt/body");
      check(connection.getRequestProperty(provider.equals("OpenAI")?"Authorization":"x-goog-api-key").contains("synthetic-test-key"),"Expected authentication header used");
      try{AiCoachClient.generate(settings,"JSON",url->new FakeConnection(url,401,"synthetic-test-key"));throw new AssertionError("Expected credential rejection");}catch(AiCoachClient.ProviderFailure e){check(!e.getMessage().contains("synthetic-test-key")&&!AiCoachClient.retryable(e),"Provider error bodies cannot disclose keys and auth failures do not retry");}
    }
  }

}
