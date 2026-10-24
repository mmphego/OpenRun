package app.rwun;

import android.app.*;
import android.content.*;
import android.os.Looper;
import android.view.*;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Actual native activity interactions and renders; no network or synthetic street maps. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w393dp-h852dp-mdpi", manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ProgressUiTest {
  Application app;
  RunStore store;
  @Before public void setup() throws Exception {
    app=RuntimeEnvironment.getApplication();
    app.getSharedPreferences("rwun",0).edit().clear().commit();
    store=new RunStore(app);
    store.save(new PlanEngine.Config(8,3,12,5,LocalDate.now()));
    TrackingService service=Robolectric.buildService(TrackingService.class).create().get();
    Shadows.shadowOf(app).setComponentNameAndServiceForBindService(new ComponentName(app,TrackingService.class),service.onBind(new Intent()));
    // Deliberately out of order: the feed must sort by activity date rather than import order.
    store.add(run("health:recent","2026-10-24",5.01,2201,"Health Connect · com.garmin.android.apps.connectmobile",""));
    store.add(run("indoor:older","2026-09-21",1.59,3688,"Indoor · Machine entered",""));
  }
  RunStore.Run run(String id,String date,double km,long seconds,String source,String route) {
    return new RunStore.Run(id,date,km,(int)(seconds/60),5,2,false,source,route,152,seconds);
  }
  org.robolectric.android.controller.ActivityController<MainActivity> open() {
    return Robolectric.buildActivity(MainActivity.class,new Intent().putExtra("destination","Progress")).setup().visible();
  }
  static String all(View view) {
    StringBuilder result=new StringBuilder();
    if(view instanceof TextView)result.append(((TextView)view).getText()).append('\n');
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)result.append(all(((ViewGroup)view).getChildAt(i)));
    return result.toString();
  }
  @Test public void historyIsChronologicalAndKeepsActionsInsideDetails() throws Exception {
    try(var controller=open()) {
      MainActivity activity=controller.get();String text=all(activity.getWindow().getDecorView());
      assertTrue(text.indexOf("October 2026")<text.indexOf("September 2026"));
      assertTrue(text.contains("5.01 km"));assertTrue(text.contains("36:41"));assertTrue(text.contains("7:19 /km"));
      assertTrue(text.contains("Garmin · Health Connect"));assertFalse(text.contains("com.garmin"));
      assertNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Delete activity"));
      assertNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Review, correct & match workout"));
      NativePlanningTest.shot(activity,"progress-history");
      NativePlanningTest.click(activity,"Run");
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Route unavailable"));
      assertTrue(all(activity.getWindow().getDecorView()).contains("152 bpm"));
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Review, correct & match workout"));
      NativePlanningTest.shot(activity,"progress-health-detail");
      controller.recreate();activity=controller.get();
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Route unavailable"));
      activity.onBackPressed();
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"October 2026"));
    }
  }
  @Test public void monthFilterSurvivesDetailAndRotation() throws Exception {
    try(var controller=open()) {
      MainActivity activity=controller.get();NativePlanningTest.click(activity,"Month");
      AlertDialog choices=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
      choices.getListView().performItemClick(null,10,10); // All months, then January through October.
      assertNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"September 2026"));
      NativePlanningTest.click(activity,"Run");controller.recreate();activity=controller.get();activity.onBackPressed();
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"October 2026"));
      assertNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"September 2026"));
    }
  }
  @Test public void routePreviewUsesSavedPointsAndStreetMapIsAnExplicitAction() throws Exception {
    try(var out=app.openFileOutput("route-progress-test.jsonl",0)) {
      out.write(("{\"lat\":-33.9,\"lon\":18.6,\"time\":1000,\"segment\":0}\n"+
          "{\"lat\":-33.91,\"lon\":18.61,\"time\":2000,\"segment\":0}\n").getBytes(StandardCharsets.UTF_8));
    }
    store.add(run("gps:new","2026-10-25",5,1800,"GPS","route-progress-test.jsonl"));
    try(var controller=open()) {
      MainActivity activity=controller.get();NativePlanningTest.click(activity,"Outdoor run");
      for(int i=0;i<100&&NativePlanningTest.find(activity.getWindow().getDecorView(),"Load street map")==null;i++) {
        Thread.sleep(10);Shadows.shadowOf(Looper.getMainLooper()).idle();
      }
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"GPS route"));
      NativePlanningTest.shot(activity,"progress-route-detail");
      NativePlanningTest.click(activity,"Load street map");
      AlertDialog consent=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
      assertNotNull(consent);consent.getButton(-2).performClick();
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Offline route preview · start and finish marked"));
      assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
  }
  @Test public void largeFontKeepsMetricsInTwoColumns() throws Exception {
    RuntimeEnvironment.setFontScale(1.6f);
    try(var controller=open()) {
      NativePlanningTest.click(controller.get(),"Run");
      NativePlanningTest.shot(controller.get(),"progress-detail-large-font");
      assertNotNull(NativePlanningTest.find(controller.get().getWindow().getDecorView(),"5.01 km"));
      assertNotNull(NativePlanningTest.find(controller.get().getWindow().getDecorView(),"36:41"));
    } finally {RuntimeEnvironment.setFontScale(1f);}
  }
}
