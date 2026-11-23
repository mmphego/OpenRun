package app.rwun;

import android.app.AlertDialog;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, qualifiers="w320dp-h640dp-mdpi", manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class PlanCreationJourneyTest {
  @Test public void selectedWeekdaysSurviveBackAndRecreationAndAppearInCalendar() {
    Application app = RuntimeEnvironment.getApplication();
    RunStore store = new RunStore(app);
    var service = Robolectric.buildService(TrackingService.class).create().get();
    Shadows.shadowOf(app).setComponentNameAndServiceForBindService(
        new ComponentName(app, TrackingService.class), service.onBind(new Intent()));
    try (var home = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      NativePlanningTest.click(home.get(), "Create my plan");
      var launch = Shadows.shadowOf(home.get()).getNextStartedActivityForResult();
      try (var setup = Robolectric.buildActivity(RacePlanActivity.class, launch.intent).setup().visible()) {
        NativePlanningTest.click(setup.get(), "Run a specific distance");
        NativePlanningTest.click(setup.get(), "10 km");
        NativePlanningTest.click(setup.get(), "Continue");
        NativePlanningTest.click(setup.get(), DayOfWeek.MONDAY.getDisplayName(TextStyle.SHORT, Locale.getDefault()));
        assertSelectedCount(setup.get(), 4);
        setup.recreate();
        assertSelectedCount(setup.get(), 4);
        NativePlanningTest.click(setup.get(), "Preview my plan");
        setup.get().onBackPressed();
        assertSelectedCount(setup.get(), 4);
        NativePlanningTest.click(setup.get(), "Preview my plan");
        assertNull(store.config());
        NativePlanningTest.click(setup.get(), "Create my plan");
        var confirmation = (AlertDialog) ShadowDialog.getLatestDialog();
        confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        var result = Shadows.shadowOf(setup.get());
        home.get().onActivityResult(launch.requestCode, result.getResultCode(), result.getResultIntent());
      }
      assertEquals(4, store.config().days());
      assertTrue(store.trainingPreferences().weekdays().contains(DayOfWeek.MONDAY));
      NativePlanningTest.click(home.get(), "Calendar");
      View calendar = home.get().getWindow().getDecorView();
      assertTrue(store.plannedSessions().stream()
          .anyMatch(workout -> NativePlanningTest.find(calendar, workout.title()) != null));
    }
  }

  private void assertSelectedCount(RacePlanActivity activity, int count) {
    var root = activity.getWindow().getDecorView();
    assertNotNull(NativePlanningTest.find(root, count + " days per week"));
    View monday = AiPlanningUiTest.findDescription(root,
        DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL, Locale.getDefault()));
    assertTrue(monday.createAccessibilityNodeInfo().isSelected());
  }
}
