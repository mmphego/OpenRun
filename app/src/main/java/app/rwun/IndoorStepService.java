package app.rwun;
import android.app.*;import android.content.*;import android.content.pm.ServiceInfo;import android.hardware.*;import android.os.*;import org.json.*;

/** One user-started foreground session owns sensor health and optional background audio coaching. */
public final class IndoorStepService extends Service implements SensorEventListener {
 private static volatile IndoorStepService instance;
 private StepEstimate.Count live;private long epoch,lastPersist,lastAudioTry;private String session="",lastWarning="";
 private HandlerThread sensorThread;private Handler sensorHandler;
 private final Handler main=new Handler(Looper.getMainLooper());
 private SensorManager sensors;private Sensor counter;private boolean registered,bound,destroying;
 private TrackingService voice;private PowerManager.WakeLock coachingWake;
 private final ServiceConnection connection=new ServiceConnection(){
  public void onServiceConnected(ComponentName n,IBinder b){if(b instanceof TrackingService.LocalBinder)voice=((TrackingService.LocalBinder)b).service();}
  public void onServiceDisconnected(ComponentName n){voice=null;}
 };
 static boolean available(Context c){SensorManager m=c.getSystemService(SensorManager.class);return m!=null&&m.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!=null;}
 static boolean permitted(Context c){return Build.VERSION.SDK_INT<29||c.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
 static String health(){IndoorStepService s=instance;return s==null?"inactive":s.registered?"running · step listener registered":"running · audio coaching";}
 static boolean running(){return instance!=null&&!instance.destroying;}
 static void sync(Context c){
  try{JSONObject a=IndoorState.active(c);RunStore store=new RunStore(c);
   boolean active=a!=null&&!a.optBoolean("paused")&&a.optInt("boot")==IndoorState.boot(c);
   boolean steps=active&&a.optBoolean("phoneSteps")&&available(c)&&permitted(c);
   boolean audio=active&&IndoorCoaching.needsAudio(store,a,IndoorState.seconds(c));
   if(steps||audio)c.startForegroundService(new Intent(c,IndoorStepService.class));
   else c.stopService(new Intent(c,IndoorStepService.class));
   if(active&&a.optBoolean("phoneSteps")&&(!available(c)||!permitted(c)))partial(c,"Step permission or sensor unavailable · use machine distance");
  }catch(RuntimeException e){RecordingDiagnostics.error(c,"indoor_service_start_failed");partial(c,"Step tracking could not start · reopen indoor screen or use machine distance");}
 }
 static void partial(Context c,String status){
  try{synchronized(RunStore.LOCK){RunStore s=new RunStore(c);JSONObject d=s.v103(),a=d.optJSONObject("activeIndoor");if(a!=null){a.put("partialSteps",true).put("stepStatus",status);s.v103(d);}}}catch(Exception e){RecordingDiagnostics.error(c,"indoor_state_save_failed");}
 }
 static void flush(){IndoorStepService s=instance;if(s!=null)s.persist();}
 private void persist(){
  try{synchronized(RunStore.LOCK){
   if(live==null)return;RunStore s=new RunStore(this);JSONObject d=s.v103(),a=d.optJSONObject("activeIndoor");
   if(a==null||!a.optString("id").equals(session)||a.optLong("stepResume")!=epoch)return;
   a.put("stepStatus",permitted(this)?(live.steps()>0?"Steps detected":"Waiting for steps"):"Physical activity permission lost · steps are no longer being recorded")
    .put("phoneStepCount",live.steps()).put("stepRaw",live.raw()).put("stepEvent",live.eventNanos())
    .put("partialSteps",a.optBoolean("partialSteps")||live.partial()).put("stepPersistWall",System.currentTimeMillis());
   s.v103(d);lastPersist=SystemClock.elapsedRealtime();
  }Reminders.sessionNotification(this);
  }catch(Exception e){RecordingDiagnostics.error(this,"indoor_steps_save_failed");stopSelf();}
 }
 private void heartbeat(){
  try{synchronized(RunStore.LOCK){RunStore s=new RunStore(this);JSONObject d=s.v103(),a=d.optJSONObject("activeIndoor");
   if(a==null||!a.optString("id").equals(session))return;
   a.put("heartbeatWall",System.currentTimeMillis()).put("heartbeatElapsed",SystemClock.elapsedRealtime());s.v103(d);
  }}catch(Exception e){RecordingDiagnostics.error(this,"indoor_heartbeat_save_failed");stopSelf();}
 }
 private final Runnable healthCheck=new Runnable(){public void run(){
  if(destroying)return;
  heartbeat();Reminders.sessionNotification(IndoorStepService.this);
  if(registered&&!permitted(IndoorStepService.this)){partial(IndoorStepService.this,"Physical activity permission lost · steps are no longer being recorded");sensors.unregisterListener(IndoorStepService.this);registered=false;}
  sensorHandler.postDelayed(this,15000);
 }};
 private final Runnable coachTick=new Runnable(){public void run(){
  if(destroying)return;
  try{
   JSONObject a=IndoorState.active(IndoorStepService.this);
   if(a==null||a.optBoolean("paused")||a.optInt("boot")!=IndoorState.boot(IndoorStepService.this)){stopSelf();return;}
   IndoorCoaching.catchUp(IndoorStepService.this);a=IndoorState.active(IndoorStepService.this);
   RunStore store=new RunStore(IndoorStepService.this);long sec=IndoorState.seconds(IndoorStepService.this);
   var def=IndoorCoaching.definition(store,a);boolean audio=IndoorCoaching.needsAudio(store,a,sec);
   boolean awake=audio&&IndoorCoaching.needsAwake(a,def,sec);
   if(awake&&coachingWake!=null&&!coachingWake.isHeld())coachingWake.acquire(Math.max(1000,(IndoorCoaching.MAX_AUDIO_SECONDS-sec)*1000));
   if(!awake&&coachingWake!=null&&coachingWake.isHeld())coachingWake.release();
   String warning=IndoorState.trackingWarning(IndoorStepService.this,a);
   String key=IndoorCoaching.cueKey(a,def,sec),message=IndoorCoaching.cue(a,def,sec);
   boolean freshWarning=!warning.isEmpty()&&!warning.equals(lastWarning);
   if(voice!=null&&store.audio()&&SystemClock.elapsedRealtime()-lastAudioTry>=5000&&(freshWarning||audio&&!key.equals(a.optString("coachCueKey")))){
    lastAudioTry=SystemClock.elapsedRealtime();
    String result=voice.coachBriefing(freshWarning?warning:message);
    synchronized(RunStore.LOCK){JSONObject d=store.v103(),current=d.optJSONObject("activeIndoor");if(current!=null&&current.optString("id").equals(session)){current.put("audioStatus",result.startsWith("Playing")?"Audio coaching active":result);store.v103(d);}}
    if(result.startsWith("Playing")){if(freshWarning)lastWarning=warning;else{synchronized(RunStore.LOCK){JSONObject d=store.v103(),current=d.optJSONObject("activeIndoor");if(current!=null&&current.optString("id").equals(session)){current.put("coachCueKey",key);store.v103(d);}}}}
   }
   if(warning.isEmpty())lastWarning="";
   boolean draining=voice!=null&&store.audio()&&voice.indoorSpeechActive()&&SystemClock.elapsedRealtime()-lastAudioTry<30000;
   if(!store.audio()&&voice!=null)voice.stopIndoorSpeech();
   if(!registered&&!audio&&!draining){stopSelf();return;}
  }catch(Exception e){RecordingDiagnostics.error(IndoorStepService.this,"indoor_coaching_failed");}
  main.postDelayed(this,1000);
 }};
 public IBinder onBind(Intent i){return null;}
 public void onCreate(){
  super.onCreate();instance=this;sensorThread=new HandlerThread("OpenRunIndoorSession");sensorThread.start();sensorHandler=new Handler(sensorThread.getLooper());
  sensors=getSystemService(SensorManager.class);counter=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
  coachingWake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"rwun:indoor-coaching");
 }
 public int onStartCommand(Intent i,int flags,int id){
  try{
   JSONObject a=IndoorState.active(this);RunStore store=new RunStore(this);
   if(a==null||a.optBoolean("paused")||a.optInt("boot")!=IndoorState.boot(this)){stopSelf();return START_NOT_STICKY;}
   boolean steps=a.optBoolean("phoneSteps")&&permitted(this)&&counter!=null;
   boolean audio=IndoorCoaching.needsAudio(store,a,IndoorState.seconds(this));
   if(!steps&&!audio){stopSelf();return START_NOT_STICKY;}
   Reminders.channels(this);Notification n=Reminders.indoorNotification(this);
   int types=(steps?ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH:0)|(audio?ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK:0);
   if(Build.VERSION.SDK_INT>=34)startForeground(1030,n,types);else startForeground(1030,n);
   boolean changed=!session.equals(a.optString("id"));
   if(changed){session=a.getString("id");live=null;epoch=0;lastWarning="";}
   if(steps&&!registered){
    synchronized(RunStore.LOCK){JSONObject d=store.v103(),v=d.getJSONObject("activeIndoor");
     if(v.optBoolean("sensorStarted"))v.put("partialSteps",true);
     v.put("stepStatus","Waiting for steps").put("sensorStarted",true).put("stepRaw",-1).put("stepEvent",0).put("stepResume",SystemClock.elapsedRealtimeNanos());store.v103(d);live=null;
    }
    registered=sensors.registerListener(this,counter,SensorManager.SENSOR_DELAY_NORMAL,0,sensorHandler);
    if(!registered){partial(this,"Step sensor registration failed · use machine distance");RecordingDiagnostics.error(this,"step_sensor_registration_failed");}
   }
   if(store.audio()&&!bound)bound=bindService(new Intent(this,TrackingService.class),connection,BIND_AUTO_CREATE);
   if(changed){sensorHandler.removeCallbacks(healthCheck);sensorHandler.post(healthCheck);}
   main.removeCallbacks(coachTick);main.post(coachTick);heartbeat();
   return START_NOT_STICKY;
  }catch(Exception e){RecordingDiagnostics.error(this,"indoor_foreground_start_failed");partial(this,"Indoor service unavailable · reopen the screen or use machine distance");stopSelf();return START_NOT_STICKY;}
 }
 public void onSensorChanged(SensorEvent e){
  if(e.sensor==null||e.sensor.getType()!=Sensor.TYPE_STEP_COUNTER||e.values.length==0||!Float.isFinite(e.values[0])||e.timestamp>SystemClock.elapsedRealtimeNanos()+1000000000L)return;
  try{synchronized(RunStore.LOCK){RunStore s=new RunStore(this);JSONObject d=s.v103(),a=d.optJSONObject("activeIndoor");
   if(a==null||!a.optString("id").equals(session)||a.optBoolean("paused")||a.optInt("boot")!=IndoorState.boot(this))return;
   long resume=a.optLong("stepResume");
   if(live==null||epoch!=resume){epoch=resume;live=new StepEstimate.Count(a.optLong("phoneStepCount"),-1,0,a.optBoolean("partialSteps"));}
   boolean baseline=live.raw()<0;long previous=live.steps(),event=live.eventNanos();live=StepEstimate.accept(live,(long)e.values[0],e.timestamp,resume);
   if(live.eventNanos()>event)a.put("stepSampleNanos",live.eventNanos()).put("stepDeliveredElapsed",SystemClock.elapsedRealtime());
   if(live.steps()>previous)a.put("stepMovementNanos",live.eventNanos());
   if(live.eventNanos()>event)s.v103(d);
   if(baseline||SystemClock.elapsedRealtime()-lastPersist>=5000)persist();
  }}catch(Exception ex){RecordingDiagnostics.error(this,"step_event_save_failed");stopSelf();}
 }
 public void onAccuracyChanged(Sensor s,int accuracy){}
 public void onDestroy(){
  destroying=true;persist();if(instance==this)instance=null;registered=false;
  main.removeCallbacksAndMessages(null);if(sensors!=null)sensors.unregisterListener(this);
  if(sensorHandler!=null)sensorHandler.removeCallbacksAndMessages(null);if(sensorThread!=null)sensorThread.quitSafely();
  if(coachingWake!=null&&coachingWake.isHeld())coachingWake.release();
  if(voice!=null)voice.stopIndoorSpeech();if(bound){unbindService(connection);bound=false;}voice=null;
  stopForeground(STOP_FOREGROUND_DETACH);super.onDestroy();Reminders.sessionNotification(this);
 }
}
