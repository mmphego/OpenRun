package app.rwun;
import android.app.*;import android.content.*;
import org.junit.*;import org.junit.runner.RunWith;
import org.robolectric.*;import org.robolectric.annotation.*;
import static org.junit.Assert.*;
/** Native Android host UI evidence; does not represent an actual Health Connect service. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class,qualifiers="w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class HealthConnectUiTest {
 @Before public void clearControls(){RuntimeEnvironment.getApplication().getSharedPreferences("health-connect-controls",Context.MODE_PRIVATE).edit().clear().commit();}
 public static class AvailableHealthActivity extends HealthConnectActivity { @Override protected boolean healthAvailable(){return true;} }
 @Test public void readAndWriteControlsAreVisibleWithoutConnectingAnAccount()throws Exception{
  try(var c=Robolectric.buildActivity(AvailableHealthActivity.class).setup().visible()){
   assertNotNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Read from Health Connect"));assertNotNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Write activities"));assertNotNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Choose activity to write"));NativePlanningTest.shot(c.get(),"23-health-connect-read-write");
  }
 }
 @Test public void grantingWriteAccessDoesNotAutomaticallyExport()throws Exception{
  try(var c=Robolectric.buildActivity(AvailableHealthActivity.class).setup().visible()){
   ((android.widget.Switch)NativePlanningTest.find(c.get().getWindow().getDecorView(),"Write to Health Connect")).setChecked(true);AlertDialog dialog=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(dialog);dialog.getButton(-1).performClick();assertFalse(HealthExport.busy());
  }
 }
 @Test public void readChooserOffersMetricsAndCancelDoesNotSave(){
  try(var c=Robolectric.buildActivity(AvailableHealthActivity.class).setup().visible()){
   NativePlanningTest.click(c.get(),"Choose data to read");
   AlertDialog dialog=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
   assertEquals(6,dialog.getListView().getCount());
   assertEquals("Active calories",dialog.getListView().getItemAtPosition(3));
   dialog.getButton(-2).performClick();assertFalse(HealthPreferences.selected(c.get(),3));
  }
 }
 @Test public void pausesAreIndependentPersistAndRespectRevokedPermissions(){
  var app=RuntimeEnvironment.getApplication();
  Shadows.shadowOf(app).grantPermissions(HealthSync.PERMISSIONS);
  Shadows.shadowOf(app).grantPermissions(HealthExport.PERMISSIONS);
  assertTrue(HealthSync.granted(app));assertTrue(HealthExport.granted(app));
  HealthPreferences.enabled(app,false,false);assertFalse(HealthSync.granted(app));assertTrue(HealthExport.granted(app));
  HealthPreferences.enabled(app,true,false);assertFalse(HealthExport.granted(app));
  assertFalse(HealthPreferences.enabled(app,false));
  HealthPreferences.enabled(app,false,true);assertTrue(HealthSync.granted(app));
  Shadows.shadowOf(app).denyPermissions(HealthSync.PERMISSIONS);assertFalse(HealthSync.granted(app));
 }
 public static class SlowReadActivity extends AvailableHealthActivity {
  final java.util.concurrent.CompletableFuture<String> pending=new java.util.concurrent.CompletableFuture<>();
  final java.util.concurrent.CompletableFuture<String> summary=new java.util.concurrent.CompletableFuture<>();
  int calls;
  @Override protected java.util.concurrent.CompletableFuture<String> importActivities(){calls++;return pending;}
  @Override protected java.util.concurrent.CompletableFuture<String> readSummary(){return summary;}
 }
 @Test public void doubleReadKeepsOriginalHandleAndPauseCancelsIt(){
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(HealthSync.PERMISSIONS);
  try(var c=Robolectric.buildActivity(SlowReadActivity.class).setup().visible()){
   NativePlanningTest.click(c.get(),"Read selected data now");
   NativePlanningTest.find(c.get().getWindow().getDecorView(),"Reading selected data…").performClick();
   assertEquals(1,c.get().calls);
   ((android.widget.Switch)NativePlanningTest.find(c.get().getWindow().getDecorView(),"Read from Health Connect")).setChecked(false);
   assertTrue(c.get().pending.isCancelled());
  }
 }
 @Test public void readShowsImmediateFeedbackAndActivityResultBeforeSummary(){
  Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(HealthSync.PERMISSIONS);
  try(var c=Robolectric.buildActivity(SlowReadActivity.class).setup().visible()){
   NativePlanningTest.click(c.get(),"Read selected data now");
   assertNotNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Reading selected data…"));
   c.get().pending.complete("Imported 1 Garmin run");
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
   assertTrue(((android.widget.TextView)NativePlanningTest.find(c.get().getWindow().getDecorView(),"Imported 1 Garmin run\n\nReading daily summary…")).getText().toString().contains("Imported 1 Garmin run"));
   c.get().summary.complete("Steps: unavailable.");
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
   assertTrue(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Read selected data now").isEnabled());
  }
 }
 @Test public void explicitDailyHealthOptInSelectsMetricWithoutClearingReadPause(){
  var app=RuntimeEnvironment.getApplication();
  HealthPreferences.select(app,new boolean[]{true,false,false,false,false,false});
  HealthPreferences.enabled(app,false,false);
  HealthPreferences.selectPermission(app,DailyHealthSource.STEPS);
  assertTrue(HealthPreferences.selected(app,2));assertFalse(HealthPreferences.enabled(app,false));
 }
 @Test public void requestingOnlyCaloriesDoesNotRequestOtherPermissions(){
  var app=RuntimeEnvironment.getApplication();
  HealthPreferences.select(app,new boolean[]{false,false,false,true,false,false});
  assertArrayEquals(new String[]{HealthPreferences.CALORIES},HealthPreferences.requested(app));
  Shadows.shadowOf(app).grantPermissions(HealthSync.PERMISSIONS);
  assertFalse(HealthSync.granted(app));
 }
 @Test @Config(sdk=33) public void unsupportedAndroidExplainsAvailabilityWithoutWriteButton()throws Exception{
  try(var c=Robolectric.buildActivity(HealthConnectActivity.class).setup().visible()){
   assertNotNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Requires Android 14 or newer in this version"));assertNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Choose activity to write"));
  }
 }
}
