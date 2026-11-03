package app.rwun;

import android.app.*;
import android.content.*;
import android.widget.*;
import java.time.LocalDate;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w393dp-h852dp-mdpi",manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class AiCoachUiTest {
  Application app;
  @Before public void setup(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences("rwun",0).edit().clear().commit();app.getSharedPreferences("coach_vault",0).edit().clear().commit();}
  @Test public void emptyStateRequiresConsentAndShowsProviderChoices()throws Exception{
    try(var controller=Robolectric.buildActivity(AiCoachActivity.class).setup().visible()){
      AiCoachActivity activity=controller.get();String text=ProgressUiTest.all(activity.getWindow().getDecorView());
      assertTrue(text.contains("No existing plan is required"));assertFalse(text.contains("Offline Coach"));assertFalse(text.contains("Get Gemma"));assertTrue(text.contains("Routes and heart-rate records are excluded"));
      NativePlanningTest.click(activity,"Create my AI running plan");
      Intent setup=Shadows.shadowOf(activity).getNextStartedActivity();
      assertEquals(RacePlanActivity.class.getName(),setup.getComponent().getClassName());assertEquals("ai",setup.getStringExtra("planningMethod"));
      assertNull(new RunStore(app).config());
      NativePlanningTest.click(activity,"Save AI Coach settings");assertTrue(ProgressUiTest.all(activity.getWindow().getDecorView()).contains("Enable the data-sharing consent"));
      assertEquals("",app.getSharedPreferences("coach_vault",0).getString("data",""));
      NativePlanningTest.shot(activity,"ai-coach-empty");controller.recreate();assertTrue(ProgressUiTest.all(controller.get().getWindow().getDecorView()).contains("AI Coach"));
    }
  }
  @Test public void existingPlanAndLargeFontsRender()throws Exception{
    new RunStore(app).save(new PlanEngine.Config(8,3,12,10,LocalDate.now().plusDays(1)));
    RuntimeEnvironment.setFontScale(1.6f);
    try(var controller=Robolectric.buildActivity(AiCoachActivity.class).setup().visible()){
      assertTrue(ProgressUiTest.all(controller.get().getWindow().getDecorView()).contains("Goal distance: 10.0 km"));
      assertNotNull(NativePlanningTest.find(controller.get().getWindow().getDecorView(),"Personalise my existing plan with AI"));
      NativePlanningTest.shot(controller.get(),"ai-coach-large-font");
    }finally{RuntimeEnvironment.setFontScale(1f);}
  }
  @Test public void askWithoutPlanReportsConnectionRequirementInsteadOfPlanRequirement(){
    try(var c=Robolectric.buildActivity(AiCoachActivity.class).setup().visible()){
      EditText question=org.robolectric.util.ReflectionHelpers.getField(c.get(),"question");question.setText("Break down my recent runs");
      NativePlanningTest.click(c.get(),"Ask coach");
      TextView status=org.robolectric.util.ReflectionHelpers.getField(c.get(),"status");
      assertTrue(status.getText().toString().contains("API key and data-sharing consent"));assertFalse(status.getText().toString().contains("Create a running plan"));
    }
  }
  @Test public void blockedNotificationsSuppressCoachNotification()throws Exception{
    AiCoachJob.notifyResult(app,new org.json.JSONObject().put("notifications",false),"Updated");
    assertEquals(0,Shadows.shadowOf(app.getSystemService(NotificationManager.class)).size());
  }
  @Test public void retryProgressIsReadableAndAnnouncedAtLargeFont()throws Exception{
    RuntimeEnvironment.setFontScale(1.6f);
    try(var controller=Robolectric.buildActivity(AiCoachActivity.class).setup().visible()){
      AiCoachActivity activity=controller.get();
      AiCoachClient.RetryListener progress=org.robolectric.util.ReflectionHelpers.callInstanceMethod(activity,"retryProgress",org.robolectric.util.ReflectionHelpers.ClassParameter.from(org.json.JSONObject.class,new org.json.JSONObject()));
      progress.retrying(2,3,1500,503);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      TextView status=org.robolectric.util.ReflectionHelpers.getField(activity,"status");
      assertTrue(status.getText().toString().contains("HTTP 503"));assertTrue(status.getText().toString().contains("attempt 2 of 3"));
      assertEquals(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE,status.getAccessibilityLiveRegion());
      NativePlanningTest.shot(activity,"ai-retry-large-font-layout");
      ((ScrollView)status.getParent().getParent()).scrollTo(0,Math.max(0,status.getTop()-24));
      NativePlanningTest.shot(activity,"ai-retry-status-large-font");
    }finally{RuntimeEnvironment.setFontScale(1f);}
  }
  @Test public void revokedSettingsStopTheNextRetryBeforeSending(){
    try(var controller=Robolectric.buildActivity(AiCoachActivity.class).setup().visible()){
      AiCoachClient.RetryListener progress=org.robolectric.util.ReflectionHelpers.callInstanceMethod(controller.get(),"retryProgress",org.robolectric.util.ReflectionHelpers.ClassParameter.from(org.json.JSONObject.class,new org.json.JSONObject()));
      try{progress.beforeAttempt();fail("Revoked consent must block the next attempt");}catch(Exception expected){assertTrue(expected.getMessage().contains("AI settings changed"));}
    }
  }

  @Test public void returningFromPlanCreationRefreshesTheCoachActions(){
    try(var controller=Robolectric.buildActivity(AiCoachActivity.class).setup().visible()){
      assertNotNull(NativePlanningTest.find(controller.get().getWindow().getDecorView(),"Create my AI running plan"));
      controller.pause();new RunStore(app).save(new PlanEngine.Config(8,3,12,10,LocalDate.now()));controller.resume();
      assertNotNull(NativePlanningTest.find(controller.get().getWindow().getDecorView(),"Personalise my existing plan with AI"));
      assertNull(NativePlanningTest.find(controller.get().getWindow().getDecorView(),"Create my AI running plan"));
    }
  }

}
