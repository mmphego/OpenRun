package app.rwun;

import android.content.ComponentName;
import android.content.Intent;
import java.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, qualifiers="w320dp-h640dp-mdpi", manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class CoachProgressSettingsUiTest {
  private RunStore store;

  @Before public void setup() {
    var app = RuntimeEnvironment.getApplication();
    store = new RunStore(app);
    var service = Robolectric.buildService(TrackingService.class).create().get();
    Shadows.shadowOf(app).setComponentNameAndServiceForBindService(
        new ComponentName(app, TrackingService.class), service.onBind(new Intent()));
  }

  @Test public void coachPutsScheduledWorkoutAheadOfGeneralLessons() {
    store.save(new PlanEngine.Config(8,3,12,5,LocalDate.now()));
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      var activity=controller.get();
      NativePlanningTest.click(activity,"Coach");
      var root=activity.getWindow().getDecorView();
      String text=ProgressUiTest.all(root);
      assertTrue(text.indexOf("NEXT WORKOUT")<text.indexOf("Your next-run lesson"));
      NativePlanningTest.click(activity,"Open workout");
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Reschedule workout"));
    }
  }

  @Test public void settingsWithNoPlanOpensCreation() {
    var intent=new Intent().putExtra("destination","Profile");
    try(var controller=Robolectric.buildActivity(MainActivity.class,intent).setup().visible()) {
      var activity=controller.get();
      NativePlanningTest.click(activity,"Create a training plan");
      var launch=Shadows.shadowOf(activity).getNextStartedActivityForResult();
      assertEquals(RacePlanActivity.class.getName(),launch.intent.getComponent().getClassName());
    }
  }

  @Test public void historyKeepsPaceExplanationBesideTheSelectedRun() {
    store.add(new RunStore.Run("gps:prior","2026-10-01",5,33,4,2,false,"GPS","",0,2000));
    store.add(new RunStore.Run("gps:recent","2026-11-01",5,30,4,2,false,"GPS","",0,1800));
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      var activity=controller.get();
      NativePlanningTest.click(activity,"Progress");
      var root=activity.getWindow().getDecorView();
      assertFalse(ProgressUiTest.all(root).contains("Pace varies with terrain"));
      NativePlanningTest.click(activity,"10% faster pace than previous similar run");
      root=activity.getWindow().getDecorView();
      assertNotNull(NativePlanningTest.find(root,"10% faster pace than previous similar run"));
      assertTrue(ProgressUiTest.all(root).contains("Previous similar run · 1 Oct 2026"));
    }
  }
}
