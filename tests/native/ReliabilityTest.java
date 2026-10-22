package app.rwun;
import android.Manifest;import android.app.*;import android.content.*;import android.hardware.*;import android.os.*;import android.speech.tts.*;import android.widget.*;
import java.time.*;import java.util.*;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;
import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import org.robolectric.android.controller.*;import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="w393dp-h852dp-mdpi",manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class ReliabilityTest {
 Application app;RunStore store;Sensor sensor;ServiceController<IndoorStepService> indoor;ServiceController<TrackingService> voice;
 @Before public void setup(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences("rwun",0).edit().clear().putBoolean("audio",false).commit();store=new RunStore(app);
  Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION,Manifest.permission.POST_NOTIFICATIONS);
  android.provider.Settings.Global.putInt(app.getContentResolver(),android.provider.Settings.Global.BOOT_COUNT,1);
  sensor=ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER);Shadows.shadowOf(app.getSystemService(SensorManager.class)).addSensor(sensor);
  voice=Robolectric.buildService(TrackingService.class).create();Shadows.shadowOf(app).setComponentNameAndServiceForBindService(new ComponentName(app,TrackingService.class),voice.get().onBind(new Intent()));
 }
 @After public void cleanup(){if(indoor!=null)indoor.destroy();IndoorState.clear(app);voice.destroy();}
 void begin()throws Exception{IndoorState.start(app,"Walk",10,null,true);connect();}
 void connect(){indoor=Robolectric.buildService(IndoorStepService.class).create();indoor.get().onStartCommand(new Intent(app,IndoorStepService.class),0,1);}
 void emit(long raw,int seconds){ShadowSystemClock.advanceBy(Duration.ofSeconds(seconds));SensorEvent e=ReflectionHelpers.callConstructor(SensorEvent.class,ReflectionHelpers.ClassParameter.from(int.class,1));e.sensor=sensor;e.values[0]=raw;e.timestamp=SystemClock.elapsedRealtimeNanos();indoor.get().onSensorChanged(e);IndoorStepService.flush();}
 void reminderPrefs()throws Exception{JSONObject r=V103Data.reminderDefaults().put("movement",true).put("days",new JSONArray(List.of(1,2,3,4,5,6,7))).put("start",0).put("end",1440);store.v103(store.v103().put("reminders",r));}
 Notification movement(){return Shadows.shadowOf(app.getSystemService(NotificationManager.class)).getNotification(1031);}
 RunStore.Run run(String id,String date,double km,long seconds){return new RunStore.Run(id,date,km,(int)Math.max(1,seconds/60),5,2,false,id.startsWith("health")?"Health Connect · sample":"Strava","",0,seconds);}
 JSONObject origin(String provider,long start,long seconds){return RunStore.origin(provider,provider+"-id",start,start+seconds*1000);}

 @Test public void countingThatStallsShowsWarningAfterProgress()throws Exception{
  begin();emit(10000,1);emit(10100,60);ShadowSystemClock.advanceBy(Duration.ofSeconds(61));
  assertTrue(IndoorState.trackingWarning(app,IndoorState.active(app)).contains("NO RECENT STEPS"));
  try(var activity=Robolectric.buildActivity(IndoorActivity.class).setup().visible()){
   assertTrue(IndoorRecordingTest.text(activity.get().getWindow().getDecorView()).contains("NO RECENT STEPS"));NativePlanningTest.shot(activity.get(),"18-stalled-after-progress");
  }
  assertTrue(Reminders.indoorNotification(app).extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("NO RECENT STEPS"));emit(10200,60);assertEquals("",IndoorState.trackingWarning(app,IndoorState.active(app)));
 }
 @Test public void resumingGivesTheSensorAFreshGracePeriod()throws Exception{begin();emit(10000,1);emit(10100,60);ShadowSystemClock.advanceBy(Duration.ofSeconds(70));assertFalse(IndoorState.trackingWarning(app,IndoorState.active(app)).isEmpty());IndoorState.toggle(app);IndoorState.toggle(app);assertEquals("",IndoorState.trackingWarning(app,IndoorState.active(app)));}
 @Test public void finishNotificationPausesAndOpensSaveReview()throws Exception{
  begin();emit(10000,1);emit(10100,60);Notification n=Reminders.indoorNotification(app);Intent finish=Shadows.shadowOf(n.actions[1].actionIntent).getSavedIntent();
  try(var activity=Robolectric.buildActivity(IndoorActivity.class,finish).setup().visible()){
   assertTrue(IndoorState.active(app).getBoolean("paused"));assertTrue(IndoorState.active(app).getBoolean("reviewPending"));
   AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();assertEquals("Save session",dialog.getButton(-1).getText().toString());long seconds=IndoorState.seconds(app);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60));assertEquals(seconds,IndoorState.seconds(app));
   NativePlanningTest.shot(activity.get(),"19-notification-finish-review");
  }
 }
 @Test public void staleFinishIntentCannotPauseANewSession()throws Exception{
  begin();String previous=IndoorState.active(app).getString("id");indoor.destroy();indoor=null;IndoorState.clear(app);IndoorState.start(app,"Walk",10,null,true);
  try(var a=Robolectric.buildActivity(IndoorActivity.class,new Intent(app,IndoorActivity.class).putExtra("finishSession",previous)).setup().visible()){assertFalse(IndoorState.active(app).getBoolean("paused"));}
 }
 @Test public void finishNotificationWorksWhenScreenAlreadyOpen()throws Exception{begin();emit(10000,2);try(var a=Robolectric.buildActivity(IndoorActivity.class).setup().visible()){
  Intent finish=Shadows.shadowOf(Reminders.indoorNotification(app).actions[1].actionIntent).getSavedIntent();a.newIntent(finish);assertTrue(IndoorState.active(app).getBoolean("paused"));assertTrue(((AlertDialog)ShadowDialog.getLatestDialog()).isShowing());
 }}
 @Test public void oldPauseNotificationCannotToggleNewSession()throws Exception{begin();String id=IndoorState.active(app).getString("id");new Reminders().onReceive(app,new Intent().setAction(Reminders.TOGGLE).putExtra("session","old-id"));assertFalse(IndoorState.active(app).getBoolean("paused"));new Reminders().onReceive(app,new Intent().setAction(Reminders.TOGGLE).putExtra("session",id));assertTrue(IndoorState.active(app).getBoolean("paused"));}
 @Test public void stalePausedSessionGeneratesReviewInsteadOfSilence()throws Exception{
  begin();emit(10000,2);IndoorState.toggle(app);reminderPrefs();JSONObject d=store.v103();d.getJSONObject("activeIndoor").put("pausedAtWall",System.currentTimeMillis()-31*60*1000L);store.v103(d);
  Reminders.notifyDue(app,Reminders.MOVE);assertNotNull(movement());assertEquals("An unfinished session needs review",movement().extras.getCharSequence(Notification.EXTRA_TITLE).toString());
  assertNotNull(IndoorState.active(app));int count=store.v103().getJSONObject("reminders").getInt("count");Reminders.notifyDue(app,Reminders.MOVE);assertEquals(count,store.v103().getJSONObject("reminders").getInt("count"));
 }
 @Test public void activeSessionDoesNotConsumeReminderQuota()throws Exception{begin();reminderPrefs();Reminders.notifyDue(app,Reminders.MOVE);assertNull(movement());assertEquals(0,store.v103().getJSONObject("reminders").optInt("count"));}
 @Test public void forgottenActiveTimerDoesNotSuppressRemindersForever()throws Exception{
  begin();reminderPrefs();ShadowSystemClock.advanceBy(Duration.ofHours(3));JSONObject d=store.v103(),a=d.getJSONObject("activeIndoor");a.put("heartbeatWall",System.currentTimeMillis());store.v103(d);
  Reminders.notifyDue(app,Reminders.MOVE);assertNotNull(movement());assertEquals("An unfinished session needs review",movement().extras.getCharSequence(Notification.EXTRA_TITLE).toString());assertNotNull(IndoorState.active(app));
 }
 @Test public void ongoingStepProgressStillSuppressesMovementReminder()throws Exception{
  begin();emit(10000,1);emit(10100,60);reminderPrefs();JSONObject d=store.v103(),a=d.getJSONObject("activeIndoor");a.put("seconds",3*60*60).put("resumed",SystemClock.elapsedRealtime()).put("heartbeatWall",System.currentTimeMillis());store.v103(d);
  Reminders.notifyDue(app,Reminders.MOVE);assertNull(movement());
 }
 @Test public void legacyPendingSessionEventuallyPromptsReview()throws Exception{reminderPrefs();store.checkpoint(new JSONObject().put("id","free:legacy").put("date",LocalDate.now().toString()).toString());Reminders.notifyDue(app,Reminders.MOVE);assertNull(movement());JSONObject d=store.v103();d.getJSONObject("reminders").put("pendingSeenAt",System.currentTimeMillis()-31*60*1000L);store.v103(d);Reminders.notifyDue(app,Reminders.MOVE);assertNotNull(movement());assertTrue(store.checkpoint().contains("legacy"));}
 @Test public void timeWindowAndOptOutStillApplyToStaleSession()throws Exception{begin();IndoorState.toggle(app);JSONObject d=store.v103();d.getJSONObject("activeIndoor").put("pausedAtWall",System.currentTimeMillis()-31*60*1000L);d.put("reminders",V103Data.reminderDefaults().put("movement",false));store.v103(d);Reminders.notifyDue(app,Reminders.MOVE);assertNull(movement());}

 @Test public void crossProviderDuplicateIsHeldOutsideTotals()throws Exception{
  long start=1700000000000L;String date="2023-11-14";assertEquals(RunStore.ImportResult.ADDED,store.importActivity(run("strava:1",date,5,1800),false,origin("Strava",start,1800)));
  assertEquals(RunStore.ImportResult.REVIEW,store.importActivity(run("health:1",date,5.05,1810),false,origin("sample",start+10000,1810)));assertEquals(1,store.runs().size());assertEquals(1,store.pendingImports().length());
  assertEquals(RunStore.ImportResult.REVIEW,store.importActivity(run("health:1",date,5.05,1810),false,origin("sample",start+10000,1810)));assertEquals(1,store.pendingImports().length());
 }
 @Test public void keepingExistingPreventsRepeatedImportPrompts()throws Exception{crossProviderDuplicateIsHeldOutsideTotals();store.resolveImport("health:1",false);assertEquals(1,store.runs().size());assertEquals(0,store.pendingImports().length());assertEquals(RunStore.ImportResult.DUPLICATE,store.importActivity(run("health:1","2023-11-14",5.05,1810),false,origin("sample",1700000010000L,1810)));}
 @Test public void userCanExplicitlyKeepSeparateActivities()throws Exception{crossProviderDuplicateIsHeldOutsideTotals();store.resolveImport("health:1",true);assertEquals(2,store.runs().size());assertEquals(0,store.pendingImports().length());assertEquals(RunStore.ImportResult.DUPLICATE,store.importActivity(run("health:1","2023-11-14",5.05,1810),false,origin("sample",1700000010000L,1810)));}
 @Test public void similarTotalsAtDifferentTimesAreNotHeld()throws Exception{long time=1700000000000L;store.importActivity(run("strava:1","2023-11-14",5,1800),false,origin("Strava",time,1800));assertEquals(RunStore.ImportResult.ADDED,store.importActivity(run("health:2","2023-11-14",5,1800),false,origin("sample",time+4*3600000L,1800)));assertEquals(2,store.runs().size());}
 @Test public void timezoneDateDifferenceDoesNotHideSameTimedActivity()throws Exception{long time=1700000000000L;store.importActivity(run("strava:1","2023-11-14",5,1800),false,origin("Strava",time,1800));assertEquals(RunStore.ImportResult.REVIEW,store.importActivity(run("health:2","2023-11-15",5,1800),false,origin("sample",time,1800)));assertEquals(1,store.runs().size());}
 @Test public void clockChangeDoesNotPreventSavingNativeHistory()throws Exception{store.checkpoint(new JSONObject().put("id","free:clock").put("startedAtWall",2000).put("endedAtWall",1000).toString());store.add(run("free:clock","2023-11-14",5,1800));assertEquals(1,store.runs().size());assertEquals(0,store.v103().getJSONObject("importOrigins").getJSONObject("free:clock").getLong("started"));}
 @Test public void progressMakesHeldImportsDiscoverable()throws Exception{crossProviderDuplicateIsHeldOutsideTotals();try(var a=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra("destination","Progress")).setup().visible()){NativePlanningTest.click(a.get(),"Review imports · 1 possible duplicates");assertEquals(ImportReviewActivity.class.getName(),Shadows.shadowOf(a.get()).getNextStartedActivity().getComponent().getClassName());}}
 @Test public void legacyTotalsNeedReviewRatherThanAutomaticMerging()throws Exception{store.add(run("old:1","2023-11-14",5,1800));assertEquals(RunStore.ImportResult.REVIEW,store.importActivity(run("health:1","2023-11-14",5,1800),false,origin("sample",1700000000000L,1800)));assertEquals(1,store.runs().size());}
 @Test public void walksRemainSeparateAndMissingDistanceStaysTimeOnly()throws Exception{store.add(run("old:1","2023-11-14",5,1800));RunStore.Run walk=new RunStore.Run("walk:health:1","2023-11-14",0,30,0,2,false,"Health Connect · sample","",0,1800);assertEquals(RunStore.ImportResult.ADDED,store.importActivity(walk,true,origin("sample",1700000000000L,1800)));assertEquals(1,store.runs().size());assertEquals(1,store.walks().length());assertFalse(store.walks().getJSONObject(0).getBoolean("distanceKnown"));}
 @Test public void pendingReviewsSurviveValidatedBackupAndRestore()throws Exception{crossProviderDuplicateIsHeldOutsideTotals();JSONObject backup=store.backupPreferences();RunStore.validatePreferences(backup);store.restorePreferences(backup);assertEquals(1,store.pendingImports().length());assertEquals(1,store.runs().size());}
 @Test public void reviewScreenDoesNotCountActivityUntilConfirmed()throws Exception{crossProviderDuplicateIsHeldOutsideTotals();try(var a=Robolectric.buildActivity(ImportReviewActivity.class).setup().visible()){assertNotNull(NativePlanningTest.find(a.get().getWindow().getDecorView(),"Keep existing activity"));NativePlanningTest.shot(a.get(),"20-import-duplicate-review");NativePlanningTest.click(a.get(),"Keep existing activity");assertNotNull(NativePlanningTest.find(a.get().getWindow().getDecorView(),"No imports need review."));assertEquals(1,store.runs().size());}}
 @Test public void invalidImportCannotChangeExistingHistory()throws Exception{store.add(run("old:1","2023-11-14",5,1800));String before=store.backupPreferences().toString();try{store.importActivity(run("health:1","2023-11-14",5,1800),false,RunStore.origin("sample","bad",100,10));fail();}catch(IllegalArgumentException expected){}assertEquals(before,store.backupPreferences().toString());}

 @Test public void timedCoachingContinuesWhenActivityIsStopped()throws Exception{
  store.audio(true);ShadowTextToSpeech.addLanguageAvailability(Locale.UK);TextToSpeech tts=ReflectionHelpers.getField(voice.get(),"speech");Shadows.shadowOf(tts).getOnInitListener().onInit(TextToSpeech.SUCCESS);
  store.save(new PlanEngine.Config(8,3,12,5,LocalDate.now()));var first=store.plannedSessions().get(0);IndoorState.start(app,"Run",10,store.planId()+":"+first.id(),false);connect();
  try(var a=Robolectric.buildActivity(IndoorActivity.class).setup().visible()){
   Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));a.pause().stop();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(300));
   assertEquals(1,IndoorState.active(app).getInt("step"));assertEquals(300,IndoorState.active(app).getLong("stepStart"));assertEquals("step:1",IndoorState.active(app).getString("coachCueKey"));assertNotNull(Shadows.shadowOf(tts).getLastSpokenText());
   assertTrue("distance steps require explicit confirmation",IndoorState.active(app).getInt("step")==1);
  }
 }
 @Test public void lateTimerCatchupDoesNotSkipDistanceSteps()throws Exception{
  var def=new WorkoutFactory.Workout(List.of(new WorkoutFactory.Step("Warmup",PaceProfile.Zone.EASY,0,20,PaceProfile.Range.effort()),new WorkoutFactory.Step("Recovery",PaceProfile.Zone.EASY,0,40,PaceProfile.Range.effort()),new WorkoutFactory.Step("Distance",PaceProfile.Zone.EASY,500,0,PaceProfile.Range.effort())));
  JSONObject state=new JSONObject().put("step",0).put("stepStart",0).put("paused",false);assertTrue(IndoorCoaching.progress(state,def,500));assertEquals(2,state.getInt("step"));assertEquals(60,state.getLong("stepStart"));assertFalse(IndoorCoaching.progress(state,def,1000));
 }
 @Test public void longestSupportedTimerCanStillSpeakItsGoalCue()throws Exception{store.audio(true);JSONObject a=new JSONObject().put("goal",180).put("paused",false).put("kind","Walk");assertTrue(IndoorCoaching.needsAudio(store,a,180*60));assertTrue(IndoorCoaching.cue(a,null,180*60).contains("goal is reached"));}
 @Test public void finalCoachingSentenceIsNotCancelledByTheNextTick()throws Exception{
  store.audio(true);ShadowTextToSpeech.addLanguageAvailability(Locale.UK);TextToSpeech tts=ReflectionHelpers.getField(voice.get(),"speech");Shadows.shadowOf(tts).getOnInitListener().onInit(TextToSpeech.SUCCESS);
  // Model a sentence still being spoken. The default shadow completes speech immediately.
  tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){public void onStart(String id){}public void onDone(String id){}public void onError(String id){}});
  store.save(new PlanEngine.Config(8,3,12,5,LocalDate.now()));var first=store.plannedSessions().get(0);IndoorState.start(app,"Run",10,store.planId()+":"+first.id(),false);
  JSONObject d=store.v103(),a=d.getJSONObject("activeIndoor");a.put("step",WorkoutFactory.create(first,store.profile()).steps().size());store.v103(d);connect();
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));assertTrue(Shadows.shadowOf(tts).getLastSpokenText().contains("Workout steps complete"));
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));assertTrue("the final sentence must be allowed to finish",voice.get().indoorSpeechActive());
 }
 @Test public void stoppingServiceReleasesCoachingWakeLock()throws Exception{store.audio(true);IndoorState.start(app,"Walk",10,null,false);connect();Shadows.shadowOf(Looper.getMainLooper()).idle();PowerManager.WakeLock wake=ReflectionHelpers.getField(indoor.get(),"coachingWake");assertTrue(wake.isHeld());indoor.destroy();indoor=null;assertFalse(wake.isHeld());}
 @Test public void diagnosticsIncludeRecordingHealthWithoutRouteOrSampleValues()throws Exception{begin();emit(10000,1);emit(10100,60);RecordingDiagnostics.error(app,"test_checkpoint_failure");String result=RecordingDiagnostics.report(app);assertTrue(result.contains("Physical activity permission: granted"));assertTrue(result.contains("Last sensor sample:"));assertTrue(result.contains("Indoor heartbeat:"));assertTrue(result.contains("test_checkpoint_failure"));assertFalse(result.contains("10100"));assertFalse(result.contains("1.0 beta"));assertFalse(result.contains("latitude"));}
}
