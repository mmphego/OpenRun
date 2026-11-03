package app.rwun;

import android.app.*;
import android.content.*;
import android.widget.*;
import java.time.LocalDate;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Actual native flow with synthetic provider/settings boundaries; no credentials or live API. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w393dp-h852dp-mdpi",manifest=Config.NONE,
    shadows={AiCoachJourneyUiTest.Settings.class,AiCoachJourneyUiTest.Provider.class})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class AiCoachJourneyUiTest {
  RunStore store;
  @Implements(value=AiCoachSettings.class,isInAndroidSdk=false)
  public static class Settings {
    @Implementation protected void __constructor__(Context context){}
    @Implementation protected JSONObject read(){try{return new JSONObject().put("provider","Gemini").put("model","synthetic-model").put("key","synthetic-test-key").put("consent",true).put("revision","synthetic-revision");}catch(JSONException e){throw new AssertionError(e);}}
    @Implementation protected boolean enabled(){return true;}
    @Implementation protected void result(String message){}
  }
  @Implements(value=AiCoachClient.class,isInAndroidSdk=false)
  public static class Provider {
    static volatile String prompt="";
    @Implementation protected static String generate(JSONObject settings,String value,AiCoachClient.RetryListener listener)throws Exception{
      listener.beforeAttempt();prompt=value;
      if(value.contains("Return JSON {answer:string}"))return new JSONObject().put("answer","Your recent 5 km took 35 minutes: 7:00 min/km. Build consistency before chasing your goal.").toString();
      JSONObject context=(JSONObject)new JSONTokener(value.substring(value.indexOf('\n')+1)).nextValue();
      if(context.has("upcoming")){
        JSONObject next=context.getJSONArray("upcoming").getJSONObject(0);
        return new JSONObject().put("explanation","Hold volume steady and keep this run conversational; review recovery after the run.")
            .put("sessions",new JSONArray().put(new JSONObject().put("id",next.getString("id")).put("km",next.getDouble("km"))
                .put("paceSecondsPerKm",450).put("reason","Recent running supports an easy effort at the current distance."))).toString();
      }
      JSONArray baseline=context.getJSONArray("baselineSessions"),rows=new JSONArray();
      for(int i=0;i<baseline.length();i++){
        JSONObject old=baseline.getJSONObject(i);boolean demanding=ScheduleProposer.demanding(old.getString("title"))||old.getString("title").equals("Run / walk");
        rows.put(new JSONObject().put("id",old.getString("id")).put("km",old.getDouble("km"))
            .put("instructions",demanding?old.getString("instructions"):"A conversational run tailored to your recent running.")
            .put("paceSecondsPerKm",demanding?0:450));
      }
      return new JSONObject().put("explanation","Work towards 10 km at 6:30; begin from your observed 7:00 pace with recovery between runs.").put("sessions",rows).toString();
    }
  }
  @Before public void setup(){
    var app=RuntimeEnvironment.getApplication();app.getSharedPreferences("rwun",0).edit().clear().commit();app.getSharedPreferences("coach_vault",0).edit().clear().commit();
    store=new RunStore(app);store.add(new RunStore.Run("history",LocalDate.now().minusDays(1).toString(),5,35));Provider.prompt="";
  }
  static void awaitIdle(Activity activity)throws Exception{
    for(int i=0;i<250;i++){
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      boolean busy=ReflectionHelpers.getField(activity,activity instanceof RacePlanActivity?"aiBusy":"busy");
      if(!busy)return;Thread.sleep(20);
    }
    fail("Synthetic provider request did not complete");
  }
  static void generatePlan(RacePlanActivity activity)throws Exception{
    ((EditText)AiPlanningUiTest.findDescription(activity.getWindow().getDecorView(),"Target distance (km)")).setText("10");
    ((EditText)AiPlanningUiTest.findDescription(activity.getWindow().getDecorView(),"Target pace (min:sec per km)")).setText("6:30");
    assertTrue(ProgressUiTest.all(activity.getWindow().getDecorView()).contains("1:05:00"));
    NativePlanningTest.click(activity,"Continue with my AI goal");NativePlanningTest.click(activity,"Continue");
    NativePlanningTest.click(activity,"AI personalised plan · OpenAI / Gemini");NativePlanningTest.click(activity,"Generate AI plan");awaitIdle(activity);
  }
  static void assertAcceptVisible(Activity activity){
    android.view.View button=NativePlanningTest.find(activity.getWindow().getDecorView(),"Create my plan");
    int[] location=new int[2];button.getLocationOnScreen(location);
    assertTrue("Plan acceptance stays within the screen",location[1]>=0&&location[1]+button.getHeight()<=activity.getWindow().getDecorView().getHeight());
  }
  @Test public void largeFontPreviewKeepsTheAcceptActionVisible()throws Exception{
    RuntimeEnvironment.setFontScale(1.6f);
    try(var c=Robolectric.buildActivity(RacePlanActivity.class,new Intent().putExtra("planningMethod","ai")).setup().visible()){
      generatePlan(c.get());NativePlanningTest.shot(c.get(),"ai-goal-preview-large-font");assertAcceptVisible(c.get());
      assertTrue(ProgressUiTest.all(c.get().getWindow().getDecorView()).contains("6:30"));assertNull(store.config());
    }finally{RuntimeEnvironment.setFontScale(1f);}
  }
  @Test public void recoveryHomeExplainsTheHoldWithoutOfferingAPlanUpdate()throws Exception{
    store.save(new PlanEngine.Config(8,3,12,10,LocalDate.now()));store.adapt(new AdaptiveCoach.Advice(0,true,"Pain reported"));
    String before=store.backupPreferences().toString();
    try(var c=Robolectric.buildActivity(AiCoachActivity.class).setup().visible()){
      assertTrue(ProgressUiTest.all(c.get().getWindow().getDecorView()).contains("Recovery comes first"));
      assertNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Review my plan with AI"));
      assertNotNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Ask coach"));assertEquals(before,store.backupPreferences().toString());
      NativePlanningTest.shot(c.get(),"ai-coach-recovery-home");
    }
  }
  @Test public void dashboardPlanReviewRequiresAcceptanceAndThenHidesTheAction()throws Exception{
    store.save(new PlanEngine.Config(8,3,12,10,LocalDate.now().plusDays(1)));
    String runs=store.runs().toString();
    try(var c=Robolectric.buildActivity(AiCoachActivity.class).setup().visible()){
      NativePlanningTest.click(c.get(),"Review my plan with AI");awaitIdle(c.get());
      android.view.View review=NativePlanningTest.find(c.get().getWindow().getDecorView(),"Review proposed changes");
      assertEquals(android.view.View.VISIBLE,review.getVisibility());assertFalse(store.aiHistoryReviewed());
      NativePlanningTest.click(c.get(),"Review proposed changes");
      ((AlertDialog)ShadowDialog.getLatestDialog()).getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertTrue(store.aiHistoryReviewed());assertEquals(runs,store.runs().toString());assertEquals(android.view.View.GONE,review.getVisibility());
      assertTrue(store.plannedSessions().stream().anyMatch(s->WorkoutFactory.coachPace(s.instructions())==450));
      NativePlanningTest.shot(c.get(),"ai-coach-review-accepted");
    }
  }
  @Test public void askAnswersRecentRunsWithoutAPlanAndDuringRecoveryHold()throws Exception{
    for(boolean hold:new boolean[]{false,true}){
      if(hold)store.adapt(new AdaptiveCoach.Advice(0,true,"Pain reported"));
      String before=store.backupPreferences().toString();
      try(var c=Robolectric.buildActivity(AiCoachActivity.class).setup().visible()){
        EditText question=ReflectionHelpers.getField(c.get(),"question");question.setText("Break down my recent runs");
        NativePlanningTest.click(c.get(),"Ask coach");awaitIdle(c.get());
        TextView answer=ReflectionHelpers.getField(c.get(),"answer");assertTrue(answer.getText().toString().contains("7:00 min/km"));
        assertTrue(Provider.prompt.contains("\"hasPlan\":false"));assertTrue(Provider.prompt.contains("\"trainingHold\":"+hold));
        assertEquals(before,store.backupPreferences().toString());assertNull(store.config());
        NativePlanningTest.shot(c.get(),hold?"ai-answer-during-hold":"ai-answer-without-plan");
      }
    }
  }
  @Test public void goalsGenerateAcceptAndReopenTheActualAiSchedule()throws Exception{
    try(var c=Robolectric.buildActivity(RacePlanActivity.class,new Intent().putExtra("planningMethod","ai")).setup().visible()){
      generatePlan(c.get());
      assertTrue(Provider.prompt.contains("\"goalPaceSecondsPerKm\":390"));assertTrue(Provider.prompt.contains("\"goalKm\":10"));
      RaceTraining.Draft accepted=ReflectionHelpers.getField(c.get(),"preview");
      assertTrue(accepted.sessions().stream().anyMatch(s->WorkoutFactory.coachPace(s.instructions())==450));
      assertNull(store.config());c.recreate();
      String previewText=ProgressUiTest.all(c.get().getWindow().getDecorView());
      assertTrue(previewText.contains("10.0")&&previewText.contains("6:30")&&previewText.contains("Target distance")&&previewText.contains("Target pace"));
      NativePlanningTest.shot(c.get(),"ai-goal-generated-preview");
      assertAcceptVisible(c.get());
      NativePlanningTest.click(c.get(),"Create my plan");((AlertDialog)ShadowDialog.getLatestDialog()).getButton(-1).performClick();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      RunStore reopened=new RunStore(RuntimeEnvironment.getApplication());
      assertEquals(accepted.sessions(),reopened.plannedSessions());assertEquals(390,reopened.options().getInt("goalPace"));assertEquals(1,reopened.runs().size());
      assertEquals("AI plan created",reopened.planChanges().getJSONObject(0).getString("kind"));
      try(var home=Robolectric.buildActivity(AiCoachActivity.class).setup().visible()){
        assertTrue(ProgressUiTest.all(home.get().getWindow().getDecorView()).contains("6:30"));
        assertNotNull(NativePlanningTest.find(home.get().getWindow().getDecorView(),"Review my plan with AI"));
        assertEquals(android.view.View.GONE,NativePlanningTest.find(home.get().getWindow().getDecorView(),"Review proposed changes").getVisibility());
        NativePlanningTest.shot(home.get(),"ai-coach-plan-home");
      }

    }
  }
}
