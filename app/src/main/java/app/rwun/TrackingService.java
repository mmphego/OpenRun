package app.rwun;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.location.*;
import android.os.*;
import android.speech.tts.TextToSpeech;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

/** User-started foreground location recording with pause, recovery checkpoint and local route. */
public final class TrackingService extends Service implements LocationListener {
  private static volatile TrackingService current;
  static String health(){TrackingService s=current;if(s==null||!s.active)return "GPS service: inactive";return "GPS service: "+(s.paused?"paused":"recording")+" · last fix "+RecordingDiagnostics.age(s.lastFix,SystemClock.elapsedRealtime())+" · checkpoint "+RecordingDiagnostics.age(s.lastCheckpoint,SystemClock.elapsedRealtime());}
  public final class LocalBinder extends Binder {
    TrackingService service() {
      return TrackingService.this;
    }
  }

  private final LocalBinder binder = new LocalBinder();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private android.media.AudioManager audioManager;
  private android.media.AudioFocusRequest focusRequest;
  private final Set<String> spoken=new HashSet<>();
  private long lastCheckpoint,lastNotification;
  private String startRequest="",startFailure="";
  String startRequest(){return startRequest;}
  String startFailure(){return startFailure;}
  private RunStore store;
  private LocationManager locations;
  private PowerManager.WakeLock wake;
  private GpsAccumulator gps = new GpsAccumulator(0);
  private WorkoutEngine workout;
  private final RollingPace rollingPace = new RollingPace();
  private String workoutType = "Free run";
  private double expectedPace;
  private long benchmark,startedAtWall,pausedAtWall;
  private TextToSpeech speech;
  private boolean walking;
  private boolean voiceReady, active, paused, connectedMode;
  private long elapsedBefore, resumedAt, lastFix, lastHr, lastProcessedFix, movementAtSeconds;
  private int receivedFixes, ignoredFixes, delayedFixes;
  private boolean distanceWarningSpoken;
  private int bpm, hrSum, hrCount, segment;
  private double target;
  private String id = "",
      date = "",
      route = "",
      status = "Ready",
      hrStatus = "Not connected",
      cue = "";
  private HeartRateClient heart;

  @Override
  public void onCreate() {
    super.onCreate();current=this;
    store = new RunStore(this);
    locations = getSystemService(LocationManager.class);
    audioManager=getSystemService(android.media.AudioManager.class);
    focusRequest=new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
      .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
      .setAcceptsDelayedFocusGain(false).setOnAudioFocusChangeListener(change->{if(change<0){stopSpeech();}},handler).build();
    getSystemService(NotificationManager.class)
        .createNotificationChannel(
            new NotificationChannel("recording", "Active run", NotificationManager.IMPORTANCE_LOW));
    speech =
        new TextToSpeech(
            this,
            result -> {
              if (result == TextToSpeech.SUCCESS) {
                int language = speech.setLanguage(Locale.UK);
                if (language < TextToSpeech.LANG_AVAILABLE)
                  language = speech.setLanguage(Locale.getDefault());
                if (language < TextToSpeech.LANG_AVAILABLE)
                  language = speech.setLanguage(Locale.US);
                voiceReady = language >= TextToSpeech.LANG_AVAILABLE;
              }
            });
    speech.setOnUtteranceProgressListener(
        new android.speech.tts.UtteranceProgressListener() {
          public void onStart(String id) {}

          public void onDone(String id) {handler.post(()->audioDone(id));}

          public void onError(String id) {
            handler.post(() -> {voiceReady=false;audioDone(id);});
          }

          public void onError(String id, int error) {
            handler.post(() -> {voiceReady=false;audioDone(id);});
          }
        });
    speech.setAudioAttributes(
        new android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build());
    heart =
        new HeartRateClient(
            this,
            new HeartRateClient.Listener() {
              public void status(String s) {
                hrStatus = s;
              }

              public void heartRate(int value) {
                bpm = value;
                lastHr = SystemClock.elapsedRealtime();
                if (value > 0 && !paused && active) {
                  hrSum += value;
                  hrCount++;
                }
              }
            });
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) return START_NOT_STICKY;
    String action = intent.getAction();
    if ("START".equals(action) && !active) {
      if(intent.getBooleanExtra("cycling",false)){stopSelf();return START_NOT_STICKY;}walking=intent.getStringExtra("id")!=null&&intent.getStringExtra("id").startsWith("walk:");
      startRequest=intent.getStringExtra("request");if(startRequest==null)startRequest="";startFailure="";
      connectedMode = false;
      try {
        foreground();
        begin(intent);
      } catch (RuntimeException e) {
        startFailure=UserErrors.message(e);status=startFailure;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
      }
    } else if ("PAUSE".equals(action) && active) {
      try{togglePause();}catch(RuntimeException e){status="Could not save pause state; recorded checkpoint remains available.";pauseForError();}
    } else if ("DISCARD".equals(action)) {
      discard();
    }
    return START_NOT_STICKY;
  }

  private void foreground() {
    Intent open = new Intent(this,MainActivity.class).putExtra("destination","Run").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent pi =
        PendingIntent.getActivity(
            this, 1, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent pause =
        PendingIntent.getService(
            this,
            2,
            new Intent(this, TrackingService.class).setAction("PAUSE"),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification n = notification(pi,pause);
    if (Build.VERSION.SDK_INT >= 29)
      startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION | (connectedMode ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE : 0));
    else startForeground(7,n);
  }

  private Notification notification(PendingIntent pi,PendingIntent pause){
    return new Notification.Builder(this, "recording")
            .setSmallIcon(walking?R.drawable.ic_directions_walk:R.drawable.ic_directions_run)
            .setContentTitle("OpenRun · "+(walking?"walk":"run")+(active&&paused?" paused":" recording"))
            .setContentText((distanceNeedsAttention()?"Check distance · ":"")+String.format(Locale.ROOT,"%.2f km · %d:%02d · Tap to finish",km(),seconds()/60,seconds()%60))
            .setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(pi)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_pause), paused?"Resume":"Pause", pause).build())
            .addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this,R.drawable.ic_directions_run),"View / finish",pi).build()).build();
  }

  private void refreshNotification(){if(active){PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class).putExtra("destination","Run").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);PendingIntent pause=PendingIntent.getService(this,2,new Intent(this,TrackingService.class).setAction("PAUSE"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);getSystemService(NotificationManager.class).notify(7,notification(open,pause));}}


  private void begin(Intent i) {
    if(store.resting())throw new IllegalStateException("Review your training hold before recording");
    if(store.indoorPending())throw new IllegalStateException("Finish the indoor session first");
    if(!store.checkpoint().isEmpty())throw new IllegalStateException("Recover or discard the pending run first");
    if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        != android.content.pm.PackageManager.PERMISSION_GRANTED)
      throw new SecurityException("Precise location permission is required.");
    if (!locations.isProviderEnabled(LocationManager.GPS_PROVIDER))
      throw new IllegalStateException("Enable device GPS before starting.");
    id = i.getStringExtra("id");
    if (id == null) id = "free:" + UUID.randomUUID();
    target = i.getDoubleExtra("target", 0);
    connectedMode = false;
    hrSum = 0;
    hrCount = 0;
    bpm = 0;
    lastFix = 0;
    lastProcessedFix = 0; movementAtSeconds = 0;
    receivedFixes = ignoredFixes = delayedFixes = 0; distanceWarningSpoken = false;
    lastHr = 0;
    date = LocalDate.now().toString();startedAtWall=System.currentTimeMillis();
    route = "route-" + UUID.randomUUID() + ".jsonl";
    gps = new GpsAccumulator(0,walking?5:12);
    elapsedBefore = 0;
    resumedAt = SystemClock.elapsedRealtime();
    segment = 0;
    PlanEngine.Session session = null;
    for (var candidate : store.plannedSessions())
      if ((store.planId() + ":" + candidate.id()).equals(id)) {
        session = candidate;
        break;
      }
    WorkoutFactory.Workout definition =
        walking?new WorkoutFactory.Workout(java.util.List.of(new WorkoutFactory.Step("Walk at your own pace",PaceProfile.Zone.EASY,0,0,PaceProfile.Range.effort()))):session == null ? WorkoutFactory.free() : WorkoutFactory.create(session, store.profile());
    workoutType = walking?"Walk":session == null ? "Free run" : session.title();
    if (session != null) target = session.km();
    workout = new WorkoutEngine(definition);
    rollingPace.reset();
    benchmark = store.profile().equivalent5k();
    double weighted = 0, distance = 0;
    for (var step : definition.steps())
      if (step.quality()) {
        weighted += step.metres() * step.pace().middle();
        distance += step.metres();
      }
    expectedPace = distance > 0 ? weighted / distance : 0;
    active = true;
    paused = false;
    status = "Waiting for accurate GPS…";
    wake =
        getSystemService(PowerManager.class)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rwun:recording");
    wake.acquire(12 * 60 * 60 * 1000L);
    try {
      if (Build.VERSION.SDK_INT >= 31) {
        android.location.LocationRequest request = new android.location.LocationRequest.Builder(2000)
            .setMinUpdateIntervalMillis(1000).setMinUpdateDistanceMeters(0)
            .setMaxUpdateDelayMillis(0).setQuality(android.location.LocationRequest.QUALITY_HIGH_ACCURACY).build();
        locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, request, getMainExecutor(), this);
      } else locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, this, Looper.getMainLooper());
      checkpoint();
      store.event("recording_started");
      lastCheckpoint=SystemClock.elapsedRealtime();lastNotification=0;refreshNotification();
      handler.post(tick);
    } catch (RuntimeException e) {
      active = false;
      locations.removeUpdates(this);
      if (wake.isHeld()) wake.release();
      throw e;
    }
  }

  private final Runnable tick =
      new Runnable() {
        public void run() {
          if (!active) return;
          if (!paused) {
            for (String s :
                workout.update(
                    seconds(),
                    gps.metres(),
                    !walking&&store.paceAlerts() ? rollingPace.value(seconds()) : Double.NaN)) say(s);
            if (!distanceWarningSpoken && distanceNeedsAttention()) {
              distanceWarningSpoken = true;
              say("No distance has been recorded. If you are moving, check GPS. For a walking pad or treadmill, use an indoor session.");
              refreshNotification();
            }
          }
          if (bpm > 0 && SystemClock.elapsedRealtime() - lastHr > 15000) {
            bpm = 0;
            hrStatus = "Heart-rate data stale";
          }
          if (SystemClock.elapsedRealtime()-lastCheckpoint>=5000)
            try {
              checkpoint();lastCheckpoint=SystemClock.elapsedRealtime();
            } catch (RuntimeException e) {
              RecordingDiagnostics.error(TrackingService.this,"gps_checkpoint_failed");status = "Save error: " + e.getMessage();
              pauseForError();
            }
          if(SystemClock.elapsedRealtime()-lastNotification>=10000){refreshNotification();lastNotification=SystemClock.elapsedRealtime();}
          handler.postDelayed(this, 1000);
        }
      };

  private void pauseForError() {
    if (active && !paused) {
      elapsedBefore = seconds();
      paused = true;pausedAtWall=System.currentTimeMillis();
      gps.breakSegment();
    }
  }

  void togglePause() {
    if (!active) return;
    if (paused) {
      if(store.resting())throw new IllegalStateException("Review your training hold before resuming");
      paused = false;pausedAtWall=0;
      resumedAt = SystemClock.elapsedRealtime();
      lastFix = 0; status = "Waiting for GPS after resume…";
      movementAtSeconds = elapsedBefore; distanceWarningSpoken = false;
      gps.breakSegment();
      rollingPace.reset();
      segment++;
      say(walking?"Walk resumed.":"Resuming run.");
    } else {
      elapsedBefore = seconds();
      paused = true;pausedAtWall=System.currentTimeMillis();
      gps.breakSegment();
      rollingPace.reset();
      stopSpeech();
      say(walking?"Walk paused.":"Run paused.");
    }
    checkpoint();refreshNotification();
  }

  private void say(String s) {
    cue = s;
    if (store.audio() && voiceReady) {
      try{if(audioManager.requestAudioFocus(focusRequest)!=android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)return;}catch(RuntimeException unavailable){return;}
      String id=UUID.randomUUID().toString();spoken.add(id);
      int result = speech.speak(s, TextToSpeech.QUEUE_ADD, null,id);
      if (result == TextToSpeech.ERROR) {voiceReady=false;audioDone(id);}
    }
  }

  private void stopSpeech(){if(speech!=null)speech.stop();spoken.clear();if(audioManager!=null)audioManager.abandonAudioFocusRequest(focusRequest);}

  private void audioDone(String id){spoken.remove(id);if(spoken.isEmpty()&&audioManager!=null)audioManager.abandonAudioFocusRequest(focusRequest);}

  void stopIndoorSpeech(){if(!active)stopSpeech();}
  boolean indoorSpeechActive(){return !active&&!spoken.isEmpty();}

  String coachBriefing(String briefing) {
    if(active)return "Finish the current run before playing a briefing; workout cues take priority.";
    if(!store.audio())return "Enable spoken workout cues in Profile first.";
    if(!voiceReady||speech==null)return "Voice engine is not ready. Check your installed TTS voice.";
    stopSpeech();say(briefing);return !spoken.isEmpty()?"Playing workout briefing":"Audio focus is unavailable. The briefing remains on screen.";
  }

  void testVoice() {
    say("OpenRun audio coaching is ready. Keep an easy, conversational effort.");
  }

  @Override
  public void onLocationChanged(Location location) {
    if (!active || paused) return;
    if (location == null) return;
    receivedFixes++;
    long now = SystemClock.elapsedRealtime();
    long fix = location.getElapsedRealtimeNanos() / 1000000;
    // A delayed callback can contain valid movement recorded inside this segment.
    // Never count cached pre-start/pre-resume, duplicate, out-of-order or future samples.
    if (fix <= 0 || fix < resumedAt || fix <= lastProcessedFix || fix > now + 1000) {
      ignoredFixes++;
      return;
    }
    lastProcessedFix = fix;
    boolean delayed = now - fix > 10000;
    if (delayed) delayedFixes++;
    GpsAccumulator.Point p =
        new GpsAccumulator.Point(
            location.getLatitude(),
            location.getLongitude(),
            location.hasAccuracy() ? location.getAccuracy() : 999,
            fix,
            location.getTime());
    if (location.hasAccuracy() && Float.isFinite(location.getAccuracy()) && location.getAccuracy() >= 0 && location.getAccuracy() <= 30) lastFix = Math.max(lastFix, fix);
    int gaps = gps.gaps();
    double before = gps.metres();
    if (!gps.add(p)) {
      status =
          !location.hasAccuracy() || location.getAccuracy() > 30
              ? "Waiting for accurate GPS"
              : "GPS steady or implausible jump filtered";
      return;
    }
    if (gps.gaps() > gaps) {
      segment++;
      rollingPace.reset();
    }
    if (gps.metres() > before) { movementAtSeconds = seconds(); distanceWarningSpoken = false; }
    // Pace uses sample time, not the later callback delivery time.
    rollingPace.add(elapsedBefore + Math.max(0, fix - resumedAt) / 1000, gps.metres());
    lastFix = Math.max(lastFix, fix);
    status =
        location.isFromMockProvider()
            ? "Simulated GPS location"
            : (delayed ? "Delayed GPS update · ±" : "GPS ±") + Math.round(location.getAccuracy()) + " m";
    try {
      JSONObject o =
          new JSONObject()
              .put("lat", p.lat())
              .put("lon", p.lon())
              .put("time", p.epochMs())
              .put("hr", delayed ? 0 : bpm)
              .put("segment", segment);
      if(location.hasAltitude())o.put("alt",location.getAltitude());if(location.hasVerticalAccuracy())o.put("vAcc",location.getVerticalAccuracyMeters());
      try (FileOutputStream out = openFileOutput(route, MODE_APPEND)) {
        out.write((o + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
    } catch (Exception e) {
      RecordingDiagnostics.error(this,"gps_route_save_failed");status = "Route save failed: " + e.getMessage();
      pauseForError();
    }
  }

  @Override
  public void onLocationChanged(List<Location> batch) {
    if (batch == null || !active || paused) return;
    List<Location> ordered = new ArrayList<>();
    for (Location location : batch) if (location != null) ordered.add(location);
    ordered.sort(Comparator.comparingLong(Location::getElapsedRealtimeNanos));
    for (Location location : ordered) onLocationChanged(location);
    if (active) refreshNotification();
  }

  @Override
  public void onProviderDisabled(String provider) {
    status = "GPS disabled · pause or re-enable location";
    gps.breakSegment();
    rollingPace.reset();
    segment++;
  }

  @Override
  public void onProviderEnabled(String provider) {
    status = "Waiting for GPS…";
  }

  @Override
  public void onStatusChanged(String provider, int status, Bundle extras) {}

  long seconds() {
    return elapsedBefore
        + (active && !paused ? (SystemClock.elapsedRealtime() - resumedAt) / 1000 : 0);
  }

  double km() {
    return gps.metres() / 1000;
  }

  boolean walking(){return walking;}
  double target() {
    return target;
  }

  boolean active() {
    return active;
  }

  boolean paused() {
    return paused;
  }

  boolean distanceNeedsAttention() {
    if (!active || paused || seconds() < 60) return false;
    return gps.metres() < 3 || lastFix == 0 || SystemClock.elapsedRealtime() - lastFix > 30000
        || seconds() - movementAtSeconds >= 120;
  }

  String status() {
    if (!active || paused) return status;
    if (lastFix > 0 && SystemClock.elapsedRealtime() - lastFix > 30000)
      return "GPS updates delayed or lost · distance may be partial";
    if (seconds() >= 60 && gps.metres() < 3)
      return "No GPS distance recorded · walking pad? Use an indoor session.";
    if (gps.metres() >= 3 && seconds() - movementAtSeconds >= 120)
      return "No GPS movement for 2 minutes · check distance before continuing.";
    if (lastFix == 0 && seconds() >= 30) return "No accurate GPS fix yet · only time is recording.";
    return status;
  }

  String gpsDetails() {
    long age = lastFix == 0 ? -1 : Math.max(0, (SystemClock.elapsedRealtime() - lastFix) / 1000);
    return "Outdoor GPS recording. GPS measures changes in position; it cannot measure walking-pad distance."
        + "\n\nDistance: " + String.format(Locale.ROOT, "%.3f km", km())
        + "\nLocation callbacks: " + receivedFixes + "\nAccepted route points: " + gps.accepted()
        + "\nRejected accuracy/speed points: " + gps.rejected() + "\nIgnored timestamps: " + ignoredFixes
        + "\nDelayed samples: " + delayedFixes + "\nRoute gaps: " + gps.gaps()
        + "\nLast accurate sample: " + (age < 0 ? "none" : age + " seconds ago")
        + "\n\n" + status();
  }

  String hrStatus() {
    return hrStatus;
  }

  int bpm() {
    return bpm;
  }

  String phase() {
    return workout == null ? "Ready" : workout.phase();
  }

  String remaining() {
    return workout == null ? "" : workout.remaining(seconds(), gps.metres());
  }

  String paceTarget() {
    return workout == null || workout.complete() ? "" : workout.step().pace().label();
  }

  double currentPace() {
    return paused ? Double.NaN : rollingPace.value(seconds());
  }

  String lastCue() {
    return cue;
  }

  boolean voiceReady() {
    if (voiceReady && speech.getLanguage() == null) voiceReady = false;
    return voiceReady;
  }

  List<android.bluetooth.BluetoothDevice> sensors() {
    return heart.devices();
  }

  void scanSensors() {
    if (!active)
      throw new IllegalStateException("Start an outdoor run or walk before connecting live heart rate.");
    connectedMode = true;
    foreground();
    heart.scan();
  }

  void connectSensor(android.bluetooth.BluetoothDevice device) {
    heart.connect(device);
  }

  void disconnectSensor() {
    heart.close();
    connectedMode = false;
    if (active) foreground();
    bpm = 0;
    hrStatus = "Disconnected";
  }

  void freeze() {
    if (active && !paused) togglePause();
  }

  RunStore.Run pending(int rpe, int fatigue, boolean pain) {
    return new RunStore.Run(
        id,
        date,
        km(),
        (int) Math.max(1, Math.round(seconds() / 60.0)),
        rpe,
        fatigue,
        pain,
        "GPS",
        route,
        hrCount == 0 ? 0 : hrSum / hrCount,
        seconds(),
        workoutType,
        workout != null && workout.qualityComplete() && gps.gaps() == 0 && delayedFixes == 0
            ? workout.qualityMetres() / 1000
            : 0,
        workout != null && workout.qualityComplete() && gps.gaps() == 0 && delayedFixes == 0
            ? workout.qualitySeconds()
            : 0,
        expectedPace,
        benchmark);
  }

  private void checkpoint() {
    try {
      store.checkpoint(
          new JSONObject()
              .put("id", id).put("walking",walking)
              .put("date", date).put("startedAtWall",startedAtWall).put("endedAtWall",paused&&pausedAtWall>0?pausedAtWall:System.currentTimeMillis()).put("heartbeatWall",System.currentTimeMillis()).put("boot",IndoorState.boot(this)).put("paused",paused).put("pausedAtWall",pausedAtWall)
              .put("km", km()).put("gpsReceived",receivedFixes).put("gpsAccepted",gps.accepted())
              .put("gpsDelayed",delayedFixes).put("gpsGaps",gps.gaps())
              .put("seconds", seconds()).put("lastMovementSeconds",movementAtSeconds)
              .put("route", route)
              .put("hr", hrCount == 0 ? 0 : hrSum / hrCount)
              .toString());
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  void saved() {
    shutdown();
    status = "Run saved";
    store.clearCheckpoint();
  }

  void discard() {
    String path = route;
    shutdown();
    store.clearCheckpoint();
    if (!path.isEmpty()) deleteFile(path);
  }

  private void shutdown() {
    active = false;
    handler.removeCallbacksAndMessages(null);
    locations.removeUpdates(this);
    heart.close();
    if (wake != null && wake.isHeld()) wake.release();
    stopSpeech();stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }

  @Override public void onTaskRemoved(Intent rootIntent){if(active)try{checkpoint();}catch(RuntimeException ignored){}super.onTaskRemoved(rootIntent);}

  @Override
  public void onDestroy() {
    if(current==this)current=null;
    if(active)try{checkpoint();}catch(RuntimeException ignored){}
    spoken.clear();if(audioManager!=null)audioManager.abandonAudioFocusRequest(focusRequest);
    handler.removeCallbacksAndMessages(null);
    locations.removeUpdates(this);
    heart.close();
    if (wake != null && wake.isHeld()) wake.release();
    if (speech != null) {
      speech.stop();
      speech.shutdown();
    }
    super.onDestroy();
  }

  static List<ActivityFiles.Sample> readRoute(Context c, String name) throws Exception {
    if (name.isEmpty() || !name.matches("route-[a-zA-Z0-9-]+\\.jsonl")) return List.of();
    List<ActivityFiles.Sample> points = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                c.openFileInput(name), java.nio.charset.StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        JSONObject o = new JSONObject(line);
        points.add(
            new ActivityFiles.Sample(
                o.getDouble("lat"),
                o.getDouble("lon"),
                o.getLong("time"),
                o.optInt("hr"),
                o.optInt("segment"),o.optDouble("alt",Double.NaN),o.optDouble("vAcc",Double.NaN)));
      }
    }
    return points;
  }
}
