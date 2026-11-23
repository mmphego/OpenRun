package app.rwun;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;
import android.os.SystemClock;
import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowSystemClock;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, qualifiers="w320dp-h640dp-mdpi", manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class RecordingJourneyUiTest {
  @Test public void missingDistanceLeavesRecordingRunningAndRetrySavesOnce() {
    var app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("rwun",0).edit().putBoolean("audio",false).commit();
    var shadow = Shadows.shadowOf(app);
    shadow.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION);
    var location = app.getSystemService(LocationManager.class);
    Shadows.shadowOf(location).setProviderEnabled(LocationManager.GPS_PROVIDER,true);

    var serviceController = Robolectric.buildService(TrackingService.class).create();
    var recorder = serviceController.get();
    shadow.setComponentNameAndServiceForBindService(new ComponentName(app,TrackingService.class),
        recorder.onBind(new Intent()));
    RuntimeEnvironment.setFontScale(2f);
    try (var home = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity activity = home.get();
      NativePlanningTest.click(activity,"Start free run");
      Intent command;
      do {
        command = shadow.getNextStartedService();
        assertNotNull("Starting a free run should dispatch a recorder command",command);
      } while (!"START".equals(command.getAction()));
      recorder.onStartCommand(command,0,1);
      assertTrue(recorder.startFailure(),recorder.active());
      long started = SystemClock.elapsedRealtime();
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
      NativePlanningTest.click(activity,"Finish and save");
      var error = (AlertDialog) ShadowDialog.getLatestDialog();
      assertTrue(error.isShowing());
      assertFalse("Unavailable distance must not pause an active recording",recorder.paused());
      error.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

      NativePlanningTest.click(activity,"Pause run");
      assertTrue(recorder.paused());
      NativePlanningTest.click(activity,"Resume run");
      assertFalse(recorder.paused());
      ShadowSystemClock.advanceBy(Duration.ofSeconds(12));
      recorder.onLocationChanged(List.of(point(started+2000,18.4),point(started+12000,18.4002)));
      assertTrue("Synthetic fixes should give the run distance",recorder.km()>0);
      NativePlanningTest.click(activity,"Finish and save");
      var review = (AlertDialog) ShadowDialog.getLatestDialog();
      review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      var saved = (AlertDialog) ShadowDialog.getLatestDialog();
      assertNotNull(NativePlanningTest.find(saved.getWindow().getDecorView(),"Run saved"));
      saved.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
      Shadows.shadowOf(Looper.getMainLooper()).idle();

      RunStore store = new RunStore(app);
      assertEquals(1,store.runs().size());
      assertTrue(store.checkpoint().isEmpty());
      assertFalse(recorder.active());
      var progress = activity.getWindow().getDecorView();
      assertNotNull(NativePlanningTest.find(progress,"Progress"));
      String savedRunTitle = ProgressViews.title(store.runs().get(0));
      String feed = ProgressUiTest.all(progress);
      assertEquals("Progress should show a single activity tile in " + feed,1,
          feed.lines().filter(savedRunTitle::equals).count());
    } finally {
      RuntimeEnvironment.setFontScale(1f);
      serviceController.destroy();
    }
  }

  private static Location point(long elapsedMillis, double longitude) {
    var point = new Location(LocationManager.GPS_PROVIDER);
    point.setLatitude(-33.9);
    point.setLongitude(longitude);
    point.setAccuracy(10);
    point.setElapsedRealtimeNanos(elapsedMillis*1_000_000);
    point.setTime(System.currentTimeMillis());
    return point;
  }
}
