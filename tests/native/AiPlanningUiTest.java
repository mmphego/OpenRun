package app.rwun;

import android.app.*;
import android.content.*;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w393dp-h852dp-mdpi",manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AiPlanningUiTest {
  RunStore store;
  @Before public void setup(){var app=RuntimeEnvironment.getApplication();app.getSharedPreferences("rwun",0).edit().clear().commit();app.getSharedPreferences("coach_vault",0).edit().clear().commit();store=new RunStore(app);}
  static void idle(){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
  static void availability(RacePlanActivity a){NativePlanningTest.click(a,"Run a specific distance");NativePlanningTest.click(a,"10 km");NativePlanningTest.click(a,"Continue");}
  @Test public void completePreviewAcceptAndReopenPreservesHistory()throws Exception{
    store.add(new RunStore.Run("old-plan:run",LocalDate.now().minusDays(2).toString(),5,35));
    try(var c=Robolectric.buildActivity(RacePlanActivity.class).setup().visible()){
      availability(c.get());NativePlanningTest.click(c.get(),"Preview my plan");
      RaceTraining.Draft draft=ReflectionHelpers.getField(c.get(),"preview");
      NativePlanningTest.click(c.get(),"Review all workouts");AlertDialog all=(AlertDialog)ShadowDialog.getLatestDialog();
      assertTrue(ProgressUiTest.all(all.getWindow().getDecorView()).contains(draft.sessions().get(draft.sessions().size()-1).date().toString()));
      all.getButton(-1).performClick();idle();c.recreate();
      NativePlanningTest.click(c.get(),"Create my plan");((AlertDialog)ShadowDialog.getLatestDialog()).getButton(-1).performClick();idle();
      assertEquals(draft.sessions(),new RunStore(RuntimeEnvironment.getApplication()).plannedSessions());
      assertEquals(1,store.runs().size());assertEquals("Plan created",store.planChanges().getJSONObject(0).getString("kind"));
    }
  }
  @Test public void missingConnectionKeepsOnboardingAndOffersOfflinePlan()throws Exception{
    RuntimeEnvironment.setFontScale(1.6f);
    try(var c=Robolectric.buildActivity(RacePlanActivity.class).setup().visible()){
      availability(c.get());NativePlanningTest.click(c.get(),"AI personalised plan · OpenAI / Gemini");
      AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(ProgressUiTest.all(dialog.getWindow().getDecorView()).contains("Your running goal and training days will remain here"));
      dialog.getButton(-1).performClick();idle();assertEquals("connections",Shadows.shadowOf(c.get()).getNextStartedActivity().getStringExtra("screen"));
      c.recreate();NativePlanningTest.click(c.get(),"Preview my plan");
      assertNotNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Review all workouts"));
      NativePlanningTest.shot(c.get(),"ai-planning-fallback-large-font");
      assertNull(store.config());
    }finally{RuntimeEnvironment.setFontScale(1f);}
  }
  @Test public void stalePreviewCannotSaveAfterNewRunningEvidence()throws Exception{
    try(var c=Robolectric.buildActivity(RacePlanActivity.class).setup().visible()){
      availability(c.get());NativePlanningTest.click(c.get(),"Preview my plan");
      store.add(new RunStore.Run("new-evidence",LocalDate.now().toString(),3,20));
      // Re-rendering must not refresh the fingerprint of an old proposal.
      ReflectionHelpers.callInstanceMethod(c.get(),"show");NativePlanningTest.click(c.get(),"Create my plan");
      ((AlertDialog)ShadowDialog.getLatestDialog()).getButton(-1).performClick();idle();
      assertTrue(ProgressUiTest.all(ShadowDialog.getLatestDialog().getWindow().getDecorView()).contains("history or plan changed"));
      assertNull(store.config());assertEquals(1,store.runs().size());
    }
  }
  @Test public void validatedAiPreviewSurvivesRotationAndShowsFullSchedule()throws Exception{
    try(var c=Robolectric.buildActivity(RacePlanActivity.class).setup().visible()){
      availability(c.get());NativePlanningTest.click(c.get(),"Preview my plan");
      RaceTraining.Draft baseline=ReflectionHelpers.getField(c.get(),"preview");
      org.json.JSONArray rows=new org.json.JSONArray();
      for(var workout:baseline.sessions())rows.put(new org.json.JSONObject().put("id",workout.id()).put("km",ScheduleProposer.demanding(workout.title())?workout.km():workout.km()*.8).put("instructions","Keep this run conversational."));
      String raw=new org.json.JSONObject().put("explanation","Reduced volume from the baseline; recovery takes priority.").put("sessions",rows).toString();
      var accepted=AiFullPlanPolicy.validate(baseline,raw);
      ReflectionHelpers.setField(c.get(),"aiPlanning",true);ReflectionHelpers.setField(c.get(),"aiReady",true);
      ReflectionHelpers.setField(c.get(),"aiRaw",raw);ReflectionHelpers.setField(c.get(),"aiExplanation","Reduced volume from the baseline; recovery takes priority.");
      ReflectionHelpers.setField(c.get(),"preview",new RaceTraining.Draft(baseline.config(),baseline.preferences(),accepted,baseline.review(),baseline.warning()));
      c.recreate();
      RaceTraining.Draft restored=ReflectionHelpers.getField(c.get(),"preview");assertEquals(accepted,restored.sessions());
      assertTrue(ProgressUiTest.all(c.get().getWindow().getDecorView()).contains("Reduced volume from the baseline"));
      NativePlanningTest.click(c.get(),"Review all workouts");
      assertTrue(ProgressUiTest.all(ShadowDialog.getLatestDialog().getWindow().getDecorView()).contains(accepted.get(accepted.size()-1).date().toString()));
      ((AlertDialog)ShadowDialog.getLatestDialog()).getButton(-1).performClick();idle();
      NativePlanningTest.shot(c.get(),"ai-plan-restored-preview");assertNull(store.config());
    }
  }
  @Test public void automaticFailurePolicyRespectsChoiceAndRecovery(){
    var timeout=new java.net.SocketTimeoutException();
    assertTrue(AiCoachJob.retryAfterFailure("retry",timeout,false,false));
    assertFalse(AiCoachJob.retryAfterFailure("keep",timeout,false,false));
    assertFalse(AiCoachJob.retryAfterFailure("pause",timeout,false,false));
    assertFalse(AiCoachJob.retryAfterFailure("retry",timeout,false,true));
    assertFalse(AiCoachJob.retryAfterFailure("retry",new AiCoachClient.InvalidResponse("incomplete"),false,false));
    assertFalse(AiCoachJob.retryAfterFailure("retry",new AiCoachClient.ProviderFailure(401,"access"),false,false));
    assertTrue(AiCoachJob.retryAfterFailure("retry",new AiCoachClient.ProviderFailure(503,"outage"),false,false));
  }
  @Test public void connectionsScreenIsUsableWithoutAPlan()throws Exception{
    try(var c=Robolectric.buildActivity(AiCoachActivity.class,new Intent().putExtra("screen","connections")).setup().visible()){
      String text=ProgressUiTest.all(c.get().getWindow().getDecorView());assertTrue(text.contains("AI Connections"));
      assertTrue(text.contains("Test AI connection"));assertFalse(text.contains("Generate my AI running plan"));
      NativePlanningTest.shot(c.get(),"ai-connections-without-plan");
      NativePlanningTest.click(c.get(),"Save AI Coach settings");assertTrue(ProgressUiTest.all(c.get().getWindow().getDecorView()).contains("Enable the data-sharing consent"));
    }
  }
}
