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
      assertTrue(text.contains("Create my plan first"));assertFalse(text.contains("Offline Coach"));assertFalse(text.contains("Get Gemma"));assertTrue(text.contains("Routes and heart-rate records are excluded"));
      NativePlanningTest.click(activity,"Generate my AI running plan");assertTrue(ProgressUiTest.all(activity.getWindow().getDecorView()).contains("Enable and save data-sharing consent first"));
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
      NativePlanningTest.shot(controller.get(),"ai-coach-large-font");
    }finally{RuntimeEnvironment.setFontScale(1f);}
  }
  @Test public void blockedNotificationsSuppressCoachNotification()throws Exception{
    AiCoachJob.notifyResult(app,new org.json.JSONObject().put("notifications",false),"Updated");
    assertEquals(0,Shadows.shadowOf(app.getSystemService(NotificationManager.class)).size());
  }
}
