package app.rwun;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.hardware.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Android service + Views driven by controlled sensor events. Not physical S23 evidence. */
@RunWith(RobolectricTestRunner.class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=35,qualifiers="w393dp-h852dp-mdpi",manifest=Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public class IndoorRecordingTest {
 Application app;Sensor sensor;ServiceController<IndoorStepService> controller;IndoorStepService service;ServiceController<TrackingService> voiceController;
 @Before public void setup(){
  app=RuntimeEnvironment.getApplication();app.getSharedPreferences("rwun",0).edit().clear().putBoolean("audio",false).commit();
  Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION,Manifest.permission.POST_NOTIFICATIONS);
  sensor=ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER);
  Shadows.shadowOf(app.getSystemService(SensorManager.class)).addSensor(sensor);
  android.provider.Settings.Global.putInt(app.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,1);
  voiceController=Robolectric.buildService(TrackingService.class).create();
  Shadows.shadowOf(app).setComponentNameAndServiceForBindService(new ComponentName(app,TrackingService.class),voiceController.get().onBind(new Intent()));
 }
 @After public void stop(){if(controller!=null)controller.destroy();IndoorState.clear(app);voiceController.destroy();}
 void start()throws Exception{IndoorState.start(app,"Walk",10,null,true);connect();}
 void connect(){controller=Robolectric.buildService(IndoorStepService.class).create();service=controller.get();assertTrue("step sensor must exist in service context",IndoorStepService.available(service));assertTrue("test activity recognition grant",IndoorStepService.permitted(service));service.onStartCommand(new Intent(app,IndoorStepService.class),0,1);assertTrue("sensor registration: "+IndoorState.active(app),ReflectionHelpers.<Boolean>getField(service,"registered"));}
 void emit(long raw,long afterSeconds){ShadowSystemClock.advanceBy(Duration.ofSeconds(afterSeconds));event(raw,SystemClock.elapsedRealtimeNanos());IndoorStepService.flush();}
 void event(long raw,long nanos){SensorEvent e=ReflectionHelpers.callConstructor(SensorEvent.class,ReflectionHelpers.ClassParameter.from(int.class,1));e.sensor=sensor;e.values[0]=raw;e.timestamp=nanos;service.onSensorChanged(e);}
 long count(){return IndoorState.active(app).optLong("phoneStepCount");}
 void twoKm()throws Exception{start();emit(10000,1);for(int i=1;i<=14;i++)emit(10000+i*200,120);emit(12858,35);assertEquals(2858,count());}

 @Test public void walkingPadStepsAreOnByDefault()throws Exception{
  try(var activity=Robolectric.buildActivity(IndoorActivity.class).setup().visible()){
   CheckBox steps=NativePlanningTest.find(activity.get().getWindow().getDecorView(),"Count phone steps · carry phone in pocket");assertTrue(steps.isChecked());
   assertNotNull(NativePlanningTest.find(activity.get().getWindow().getDecorView(),"Walking pad & treadmill"));NativePlanningTest.shot(activity.get(),"15-walking-pad-ready");
  }
 }
 @Test public void startWalkRequiresIndoorOrOutdoorChoice()throws Exception{
  new RunStore(app).save(new PlanEngine.Config(8,3,12,5,java.time.LocalDate.now()));
  try(var activity=Robolectric.buildActivity(MainActivity.class).setup().visible()){
   NativePlanningTest.click(activity.get(),"Start walk");AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();assertEquals(2,dialog.getListView().getCount());
   dialog.getListView().performItemClick(null,0,0);Intent opened=Shadows.shadowOf(activity.get()).getNextStartedActivity();assertNotNull(opened);assertEquals(IndoorActivity.class.getName(),opened.getComponent().getClassName());
   assertNull("No location permission request for the walking pad",Shadows.shadowOf(activity.get()).getLastRequestedPermission());
  }
 }
 @Test public void startButtonCreatesStepBasedWalkingPadSession()throws Exception{
  try(var activity=Robolectric.buildActivity(IndoorActivity.class).setup().visible()){
   NativePlanningTest.click(activity.get(),"Start indoor session");Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
   JSONObject active=IndoorState.active(app);assertNotNull(active);assertTrue(active.getBoolean("phoneSteps"));assertEquals("Walk",active.getString("kind"));assertEquals(.70,active.getDouble("length"),0);assertNotNull(NativePlanningTest.find(activity.get().getWindow().getDecorView(),"Your walking-pad walk"));
  }
 }
 @Test public void pendingIndoorSessionDoesNotShowGpsDashboard()throws Exception{
  start();try(var activity=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra("destination","Run")).setup().visible()){
   assertNotNull(NativePlanningTest.find(activity.get().getWindow().getDecorView(),"Indoor session in progress"));assertNotNull(NativePlanningTest.find(activity.get().getWindow().getDecorView(),"Return to indoor session"));assertNull(NativePlanningTest.find(activity.get().getWindow().getDecorView(),"GPS KM"));
  }
 }
 @Test public void deniedStepPermissionDoesNotSilentlyStartTimer()throws Exception{
  Shadows.shadowOf(app).denyPermissions(Manifest.permission.ACTIVITY_RECOGNITION);
  try(var activity=Robolectric.buildActivity(IndoorActivity.class).setup().visible()){
   IndoorActivity a=activity.get();NativePlanningTest.click(a,"Start indoor session");
   assertArrayEquals(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},Shadows.shadowOf(a).getLastRequestedPermission().requestedPermissions);
   a.onRequestPermissionsResult(104,new String[]{Manifest.permission.ACTIVITY_RECOGNITION},new int[]{-1});Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
   assertNull(IndoorState.active(app));assertNotNull(NativePlanningTest.find(a.getWindow().getDecorView(),"Step counting needs Physical activity permission. Tap Start to retry, or turn off Count phone steps to explicitly record time only."));
  }
 }
 @Test public void twoKilometresFromPocketStepsWithoutGps()throws Exception{
  Shadows.shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION);
  twoKm();JSONObject active=IndoorState.active(app);assertEquals(2.0006,StepEstimate.km(count(),active.getDouble("length")),.00001);assertFalse(active.getBoolean("stepLengthCalibrated"));assertEquals("Steps detected",active.getString("stepStatus"));
  try(var activity=Robolectric.buildActivity(IndoorActivity.class).setup().visible()){assertNotNull(NativePlanningTest.find(activity.get().getWindow().getDecorView(),"~2.00"));NativePlanningTest.shot(activity.get(),"16-walking-pad-two-km");}
 }
 @Test public void activeSessionKeepsMetricsAndActionsClearAtLargeFont()throws Exception{
  start();emit(10000,1);emit(10300,120);RuntimeEnvironment.setFontScale(1.6f);
  try(var activity=Robolectric.buildActivity(IndoorActivity.class).setup().visible()){
   View root=activity.get().getWindow().getDecorView();
   assertNotNull(NativePlanningTest.find(root,"Estimated distance"));assertNotNull(NativePlanningTest.find(root,"Duration"));
   assertNotNull(NativePlanningTest.find(root,"Pause timer"));assertNotNull(NativePlanningTest.find(root,"Finish and review"));
   assertNotNull(NativePlanningTest.find(root,"Recording diagnostics"));assertNotNull(NativePlanningTest.find(root,"Discard indoor session"));
   NativePlanningTest.shot(activity.get(),"walking-pad-active-clean-large-font");
  }finally{RuntimeEnvironment.setFontScale(1f);}
 }
 @Test public void indoorHistoryUsesSummaryAndSessionCardsAtLargeFont()throws Exception{
  JSONObject state=new RunStore(app).v103();state.put("indoor",new JSONArray().put(new JSONObject()
    .put("id","walk-history").put("date",java.time.LocalDate.now().toString()).put("kind","Walk").put("seconds",2714)
    .put("km",3.6).put("distanceSource","Machine entered").put("effort",4).put("fatigue",2).put("pain",false)
    .put("phoneSteps",3659).put("partialSteps",false)));
  new RunStore(app).v103(state);RuntimeEnvironment.setFontScale(1.6f);
  try(var activity=Robolectric.buildActivity(TrainingActivity.class,new Intent(app,TrainingActivity.class).putExtra("screen","indoorHistory")).setup().visible()){
   View root=activity.get().getWindow().getDecorView();assertNotNull(NativePlanningTest.find(root,"Indoor movement"));
   assertNotNull(NativePlanningTest.find(root,"45 min walking"));assertNotNull(NativePlanningTest.find(root,"45m 14s · 3.60 km"));
   assertNotNull(NativePlanningTest.find(root,"3,659 detected phone steps"));assertNotNull(NativePlanningTest.find(root,"Delete this indoor entry"));
   NativePlanningTest.shot(activity.get(),"indoor-history-clean-large-font");
  }finally{RuntimeEnvironment.setFontScale(1f);}
 }
 @Test public void reminderScheduleUsesGroupedControlsAtLargeFont()throws Exception{
  RuntimeEnvironment.setFontScale(1.6f);
  try(var activity=Robolectric.buildActivity(TrainingActivity.class,new Intent(app,TrainingActivity.class).putExtra("screen","reminders")).setup().visible()){
   View root=activity.get().getWindow().getDecorView();assertNotNull(NativePlanningTest.find(root,"Reminder schedule"));
   assertNotNull(NativePlanningTest.find(root,"REMIND ME ABOUT"));assertNotNull(NativePlanningTest.find(root,"ACTIVE DAYS"));assertNotNull(NativePlanningTest.find(root,"TIMES"));
   assertNotNull(NativePlanningTest.find(root,"MON"));assertNotNull(NativePlanningTest.find(root,"Save reminder preferences"));
   NativePlanningTest.shot(activity.get(),"reminder-schedule-clean-large-font");
  }finally{RuntimeEnvironment.setFontScale(1f);}
 }
 @Test public void existingCalibrationTakesPriority()throws Exception{
  new RunStore(app).v103(new JSONObject().put("stepLengths",new JSONObject().put("Walk",.8)));start();emit(4000,1);emit(4100,60);JSONObject active=IndoorState.active(app);assertEquals(.08,StepEstimate.km(count(),active.getDouble("length")),.00001);assertTrue(active.getBoolean("stepLengthCalibrated"));
 }
 @Test public void missingStepsShowVisibleAndNotificationWarning()throws Exception{
  start();ShadowSystemClock.advanceBy(Duration.ofSeconds(31));
  try(var activity=Robolectric.buildActivity(IndoorActivity.class).setup().visible()){
   assertTrue(text(activity.get().getWindow().getDecorView()).contains("NO STEPS YET"));assertNotNull(NativePlanningTest.find(activity.get().getWindow().getDecorView(),"—"));NativePlanningTest.shot(activity.get(),"17-walking-pad-no-steps");
  }
  assertTrue(Reminders.indoorNotification(app).extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("NO STEPS YET"));
 }
 @Test public void duplicatesOldAndFutureEventsCannotAddSteps()throws Exception{
  start();long before=SystemClock.elapsedRealtimeNanos()-1000000;event(50000,before);assertEquals(0,count());emit(10000,1);emit(10100,60);long now=SystemClock.elapsedRealtimeNanos();event(10100,now);event(20000,now-1000000);event(30000,now+2000000000L);IndoorStepService.flush();assertEquals(100,count());
 }
 @Test public void pauseResumeExcludesPausedSteps()throws Exception{
  start();emit(10000,1);emit(10100,60);IndoorState.toggle(app);emit(10600,300);assertEquals(100,count());IndoorState.toggle(app);emit(10600,1);emit(10700,60);assertEquals(200,count());
 }
 @Test public void restartPreservesCheckpointAndMarksPartial()throws Exception{
  start();emit(10000,1);emit(10100,60);controller.destroy();controller=null;connect();emit(10300,60);emit(10400,60);assertEquals(200,count());assertTrue(IndoorState.active(app).getBoolean("partialSteps"));
 }
 @Test public void finalReviewSavesEstimatedWalkWithoutRunningMileage()throws Exception{
  twoKm();try(var activity=Robolectric.buildActivity(IndoorActivity.class).setup().visible()){
   NativePlanningTest.click(activity.get(),"Finish and review");AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();dialog.getButton(-1).performClick();
   JSONObject saved=new RunStore(app).v103().getJSONArray("indoor").getJSONObject(0);assertEquals(2.0006,saved.getDouble("km"),.00001);assertEquals("Phone steps estimate",saved.getString("distanceSource"));assertEquals(2858,saved.getLong("phoneSteps"));assertFalse(saved.getBoolean("stepLengthCalibrated"));assertTrue(new RunStore(app).runs().isEmpty());assertNull(IndoorState.active(app));
  }
 }
 @Test public void machineDistanceOverridesEstimateAndCalibratesFutureWalks()throws Exception{
  twoKm();try(var activity=Robolectric.buildActivity(IndoorActivity.class).setup().visible()){
   NativePlanningTest.click(activity.get(),"Finish and review");AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();NativePlanningTest.editView(dialog.getWindow().getDecorView(),"Machine distance in km · optional").setText("2.2");
   CheckBox calibrate=NativePlanningTest.find(dialog.getWindow().getDecorView(),"Calibrate future sessions from this machine distance and detected steps");assertTrue(calibrate.isEnabled());calibrate.setChecked(true);dialog.getButton(-1).performClick();
   JSONObject state=new RunStore(app).v103(),row=state.getJSONArray("indoor").getJSONObject(0);assertEquals(2.2,row.getDouble("km"),0);assertEquals("Machine entered",row.getString("distanceSource"));assertEquals(2200d/2858,state.getJSONObject("stepLengths").getDouble("Walk"),.000001);
  }
 }
 @Test public void revokedPermissionMarksStepsPartial()throws Exception{
  start();emit(10000,1);emit(10100,60);Shadows.shadowOf(app).denyPermissions(Manifest.permission.ACTIVITY_RECOGNITION);IndoorStepService.sync(app);JSONObject a=IndoorState.active(app);assertEquals(100,a.getLong("phoneStepCount"));assertTrue(a.getBoolean("partialSteps"));assertTrue(a.getString("stepStatus").contains("permission"));
 }
 static String text(View v){String out=v instanceof TextView?((TextView)v).getText().toString():"";if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out+="\n"+text(((ViewGroup)v).getChildAt(i));return out;}
}
