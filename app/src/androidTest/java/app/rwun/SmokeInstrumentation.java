package app.rwun;

import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Run with adb shell am instrument -w app.rwun.test/app.rwun.SmokeInstrumentation. */
public final class SmokeInstrumentation extends Instrumentation {
  /** CI emulator only: exercise real app history, private files/preferences and SQLite across install -r. */
  private void upgradeData(String phase)throws Exception{
    Context context=getTargetContext();RunStore store=new RunStore(context);
    var state=context.getSharedPreferences("upgrade-acceptance",Context.MODE_PRIVATE);
    java.io.File route=new java.io.File(context.getFilesDir(),"upgrade-route.gpx");
    if(phase.equals("seed")){
      if(!state.edit().putString("token","retain-upgrade-data").commit())throw new IllegalStateException("Could not seed preferences");
      try(var stream=new java.io.FileOutputStream(route)){stream.write("synthetic upgrade route".getBytes(StandardCharsets.UTF_8));}
      new DailyHealthStore(context).saveCheckin(new DailyHealthRules.Checkin(java.time.Instant.now(),7d,2,2,DailyHealthRules.Answer.UNKNOWN,DailyHealthRules.Answer.NO,false));
      store.add(new RunStore.Run("upgrade:fixture","2026-01-01",3.25,25,4,2,false,"Upgrade acceptance","upgrade-route.gpx",123,1500));
      store.adapt(new AdaptiveCoach.Advice(0,true,"Upgrade acceptance hold"));
      context.getSharedPreferences("health-connect-exports",Context.MODE_PRIVATE).edit().putLong("upgrade-ledger",12345).commit();
      try(var db=context.openOrCreateDatabase("upgrade-acceptance.db",Context.MODE_PRIVATE,null)){
        db.execSQL("CREATE TABLE sentinel (value TEXT NOT NULL)");db.execSQL("INSERT INTO sentinel VALUES ('retained')");
      }
    }else if(!phase.equals("verify"))throw new IllegalArgumentException("Unknown upgrade phase");
    if(!state.getString("token","").equals("retain-upgrade-data"))throw new IllegalStateException("Preferences lost");
    if(!new String(java.nio.file.Files.readAllBytes(route.toPath()),StandardCharsets.UTF_8).equals("synthetic upgrade route"))throw new IllegalStateException("Private route lost");
    var run=store.runs().stream().filter(r->r.sessionId().equals("upgrade:fixture")).findFirst().orElseThrow();
    if(run.km()!=3.25||run.seconds()!=1500||run.avgHr()!=123||!run.routeFile().equals("upgrade-route.gpx"))throw new IllegalStateException("Activity changed");
    var checkin=new DailyHealthStore(context).checkin(java.time.Instant.now());
    if(checkin==null||checkin.sleepHours()==null||checkin.sleepHours()!=7d)throw new IllegalStateException("Private wellness check-in lost");
    if(!store.resting()||!store.adaptation().equals("Upgrade acceptance hold"))throw new IllegalStateException("Training hold lost");
    if(context.getSharedPreferences("health-connect-exports",Context.MODE_PRIVATE).getLong("upgrade-ledger",0)!=12345)throw new IllegalStateException("Export ledger lost");
    try(var db=context.openOrCreateDatabase("upgrade-acceptance.db",Context.MODE_PRIVATE,null);var cursor=db.rawQuery("SELECT value FROM sentinel",null)){
      if(!cursor.moveToFirst()||!cursor.getString(0).equals("retained"))throw new IllegalStateException("SQLite data lost");
    }
  }

  private int assertions;
  private String upgradePhase="";

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    if(arguments!=null)upgradePhase=arguments.getString("upgradePhase","");
    start();
  }

  @Override
  public void onStart() {
    if(!upgradePhase.isEmpty()){
      Bundle upgradeResult=new Bundle();
      try{upgradeData(upgradePhase);upgradeResult.putString("upgradeCheck","passed");finish(-1,upgradeResult);}
      catch(Exception error){upgradeResult.putString("upgradeCheck","failed: "+error);finish(1,upgradeResult);}
      return;
    }
    Bundle result = new Bundle();
    try {
      check(java.util.Arrays.asList(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions).contains(android.Manifest.permission.ACCESS_NETWORK_STATE),"network-constrained jobs declare network state permission");
      check(new IndoorClock(10,1000,1,false,false).snapshot(2000,2).paused(),"indoor reboot recovery pauses");
      check(Math.abs(StepEstimate.length(1,1400,"Walk")-1/1.4)<.00001,"step calibration");
      check(IndoorStepService.available(getTargetContext()) == (getTargetContext().getSystemService(android.hardware.SensorManager.class).getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)!=null),"step sensor availability lookup");
      getTargetContext().getPackageManager().getActivityInfo(new ComponentName(getTargetContext(),IndoorActivity.class),0);
      getTargetContext().getPackageManager().getActivityInfo(new ComponentName(getTargetContext(),TrainingActivity.class),0);
      getTargetContext().getPackageManager().getActivityInfo(new ComponentName(getTargetContext(),RacePlanActivity.class),0);
      getTargetContext().getPackageManager().getActivityInfo(new ComponentName(getTargetContext(),AiCoachActivity.class),0);
      var coachJob=getTargetContext().getPackageManager().getServiceInfo(new ComponentName(getTargetContext(),AiCoachJob.class),0);
      check("android.permission.BIND_JOB_SERVICE".equals(coachJob.permission),"AI review job requires the system binding permission");
      check(true,"indoor, race, AI coach and training screens registered");
      for(String removed:new String[]{"app.rwun.OfflineCoachActivity","app.rwun.GemmaService"}){
        boolean absent=false;try{if(removed.endsWith("Activity"))getTargetContext().getPackageManager().getActivityInfo(new ComponentName(getTargetContext(),removed),0);else getTargetContext().getPackageManager().getServiceInfo(new ComponentName(getTargetContext(),removed),0);}catch(android.content.pm.PackageManager.NameNotFoundException expected){absent=true;}check(absent,"local model component removed: "+removed);
      }
      boolean retired=false;try{getTargetContext().getPackageManager().getActivityInfo(new ComponentName(getTargetContext(),"app.rwun.CyclingActivity"),0);}catch(android.content.pm.PackageManager.NameNotFoundException expected){retired=true;}check(retired,"cycling screen removed");
      String gpx =
          "<gpx xmlns='http://www.topografix.com/GPX/1/1'><trk><trkseg><trkpt lat='0'"
              + " lon='0'><time>2026-10-16T10:00:00Z</time></trkpt><trkpt lat='0'"
              + " lon='0.0001'><time>2026-10-16T10:00:10Z</time></trkpt><trkpt lat='0'"
              + " lon='0.0002'><time>2026-10-16T10:00:20Z</time></trkpt></trkseg></trk></gpx>";
      var imported = ActivityFiles.parse(gpx.getBytes(StandardCharsets.UTF_8));
      check(imported.points().size() == 3, "GPX points");
      check(imported.seconds() == 20, "GPX timestamps");
      check(imported.km() > .022 && imported.km() < .023, "GPX distance");
      var roundtrip =
          ActivityFiles.parse(
              ActivityFiles.gpx(imported.points()).getBytes(StandardCharsets.UTF_8));
      check(Math.abs(roundtrip.km() - imported.km()) < .00001, "GPX round trip");
      String tcx =
          "<TrainingCenterDatabase><Activities><Activity Sport='Running'><Lap"
              + " StartTime='2026-10-16T10:00:00Z'><TotalTimeSeconds>600</TotalTimeSeconds><DistanceMeters>1500</DistanceMeters><Track><Trackpoint><Time>2026-10-16T10:00:00Z</Time><Position><LatitudeDegrees>0</LatitudeDegrees><LongitudeDegrees>0</LongitudeDegrees></Position><HeartRateBpm><Value>140</Value></HeartRateBpm></Trackpoint></Track></Lap></Activity></Activities></TrainingCenterDatabase>";
      var t = ActivityFiles.parse(tcx.getBytes(StandardCharsets.UTF_8));
      check(t.km() == 1.5 && t.seconds() == 600 && t.avgHr() == 140, "TCX lap totals and HR");
      var segmented =
          List.of(
              new ActivityFiles.Sample(0, 0, 100000, 140, 0),
              new ActivityFiles.Sample(0, .0001, 110000, 141, 0),
              new ActivityFiles.Sample(1, 1, 120000, 142, 1),
              new ActivityFiles.Sample(1, 1.0001, 130000, 143, 1));
      var split =
          ActivityFiles.parse(ActivityFiles.gpx(segmented).getBytes(StandardCharsets.UTF_8));
      check(split.km() < .03, "pause segments preserved");
      check(split.avgHr() == 141, "heart rate export/import");
      expectInvalid("<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><gpx>&x;</gpx>");
      expectInvalid("<gpx><trk><trkseg><trkpt lat='0' lon='0'/></trkseg></trk></gpx>");
      expectInvalid("<html/>");
      Context isolated =
          new ContextWrapper(getTargetContext()) {
            @Override
            public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
              return super.getSharedPreferences(name + "_instrumentation", mode);
            }
          };
      isolated.getSharedPreferences("rwun", 0).edit().clear().commit();
      RunStore store = new RunStore(isolated);
      store.save(new PlanEngine.Config(8, 3, 12, 5, LocalDate.now()));
      check(StrengthPlan.routines().size() == 6, "original strength routine library");
      store.completeStrength("foundation");
      check(new RunStore(isolated).strengthSessions().length() == 1, "strength persists");
      boolean invalidStrength = false;
      try { store.completeStrength("unknown"); } catch (IllegalArgumentException expected) { invalidStrength = true; }
      check(invalidStrength && store.strengthSessions().length() == 1, "unknown routine rejected");
      RunStore.validatePreferences(store.backupPreferences());
      check(store.backupPreferences().has("strength"), "strength included in portable backup");
      var v1=new org.json.JSONObject().put("metrics",true);
      V1State.event(v1,"app_open",LocalDate.now());store.v1(v1);
      check(!new org.json.JSONObject(store.backupPreferences().getString("v1")).has("events"),"v1 usage metrics excluded from backup");
      var previewConfig=new PlanEngine.Config(8,3,12,5,LocalDate.now());var previewProfile=new PaceProfile(5,0,false);
      var preview=OnboardingPlan.sessions(previewConfig,previewProfile,true);store.createPlan(previewConfig,previewProfile,true);
      check(preview.equals(store.plannedSessions()),"onboarding preview is the stored run-walk schedule");
      store.save(new PlanEngine.Config(8,3,12,5,LocalDate.now()));
      String old = store.planId();
      check(store.config().days() == 3, "configuration persisted");
      store.add(new RunStore.Run("test:1", LocalDate.now().toString(), 1, 10));
      check(
          new RunStore(isolated).runs().size() == 1,
          "history persists across repository instances");
      boolean duplicate = false;
      try {
        store.add(new RunStore.Run("test:1", LocalDate.now().toString(), 1, 10));
      } catch (IllegalArgumentException expected) {
        duplicate = true;
      }
      check(duplicate, "duplicate completion blocked");
      store.adapt(new AdaptiveCoach.Advice(.8, false, "Test"));
      check(store.factor(LocalDate.now()) == 1, "past/current day not changed");
      check(Math.abs(store.factor(LocalDate.now().plusDays(1)) - .8) < .001, "future adjusted");
      check(store.factor(LocalDate.now().plusDays(8)) == 1, "adjustment window ends");
      store.adapt(new AdaptiveCoach.Advice(0, true, "Pain"));
      check(store.resting(), "pain pause persists");
      store.resumeTraining();
      check(!store.resting(), "explicit resume");
      store.save(new PlanEngine.Config(8, 3, 12, 5, LocalDate.now()));
      check(
          !old.equals(store.planId()) && store.runs().size() == 1,
          "replacement plan identity with history preserved");
      store.delete("test:1");
      check(store.runs().isEmpty(), "delete persists");
      store.profile(new PaceProfile(21.1, 7200, true));
      check(
          Math.abs(new RunStore(isolated).profile().distanceKm() - 21.1) < .001,
          "half-marathon profile precision");
      store.save(new PlanEngine.Config(8, 3, 24, 42.2, LocalDate.now()));
      check(
          Math.abs(new RunStore(isolated).config().goalKm() - 42.2) < .001,
          "marathon config survives float storage");
      store.profile(new PaceProfile(5, 1800, true));
      var first = store.plannedSessions().get(0);
      String sessionKey = store.planId() + ":" + first.id();
      store.skip(sessionKey, true);
      check(new RunStore(isolated).skipped(sessionKey), "skip persists");
      store.skip(sessionKey, false);
      store.move(sessionKey, LocalDate.now().plusDays(1));
      check(
          store.plannedSessions().stream()
              .anyMatch(
                  row ->
                      row.id().equals(first.id())
                          && row.date().equals(LocalDate.now().plusDays(1))),
          "reschedule persists");
      boolean collision = false;
      try {
        store.move(sessionKey, LocalDate.now().plusDays(2));
      } catch (IllegalArgumentException expected) {
        collision = true;
      }
      check(collision, "schedule collision rejected");
      String route = "route-test-" + java.util.UUID.randomUUID() + ".jsonl";
      try (var out = isolated.openFileOutput(route, 0)) {
        out.write(
            "{\"lat\":0,\"lon\":0,\"time\":100000,\"hr\":140,\"segment\":0}\n"
                .getBytes(StandardCharsets.UTF_8));
      }
      store.add(
          new RunStore.Run(
              "backup:1",
              LocalDate.now().toString(),
              1,
              10,
              5,
              2,
              false,
              "GPS",
              route,
              140,
              600,
              "Intervals",
              .5,
              180,
              320,
              1800));
      var buffer = new java.io.ByteArrayOutputStream();
      BackupManager.exportTo(isolated, store, buffer);
      byte[] backup = buffer.toByteArray();
      store.delete("backup:1");
      store.profile(PaceProfile.unset());
      BackupManager.restoreFrom(isolated, store, new java.io.ByteArrayInputStream(backup));
      check(
          store.runs().size() == 1 && store.profile().equivalent5k() == 1800,
          "full backup restores history and profile");
      check(store.runs().get(0).qualitySeconds() == 180, "quality metrics survive backup");
      String restored = store.runs().get(0).routeFile();
      check(
          !restored.equals(route) && isolated.getFileStreamPath(restored).isFile(),
          "restore stages a new route safely");
      check(
          TrackingService.readRoute(isolated, restored).size() == 1,
          "restored route content readable");
      org.json.JSONObject bad = store.backupPreferences();
      bad.put("days", 100);
      boolean invalidBackup = false;
      try {
        store.restorePreferences(bad);
      } catch (Exception expected) {
        invalidBackup = true;
      }
      check(
          invalidBackup && store.config().days() == 3 && store.runs().size() == 1,
          "invalid preferences leave existing data intact");
      var malicious = new java.io.ByteArrayOutputStream();
      try (var zip = new java.util.zip.ZipOutputStream(malicious)) {
        zip.putNextEntry(new java.util.zip.ZipEntry("../../outside"));
        zip.write(new byte[] {1});
        zip.closeEntry();
      }
      boolean traversal = false;
      try {
        BackupManager.restoreFrom(
            isolated, store, new java.io.ByteArrayInputStream(malicious.toByteArray()));
      } catch (Exception expected) {
        traversal = true;
      }
      check(
          traversal && store.runs().size() == 1,
          "unsafe backup paths rejected without overwriting history");
      org.json.JSONObject validPreferences = store.backupPreferences();
      org.json.JSONObject manifest =
          new org.json.JSONObject()
              .put("app", "OpenRun")
              .put("schema", 1)
              .put("preferences", validPreferences);
      var corrupt = new java.io.ByteArrayOutputStream();
      try (var zip = new java.util.zip.ZipOutputStream(corrupt)) {
        zip.putNextEntry(new java.util.zip.ZipEntry("manifest.json"));
        zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.putNextEntry(new java.util.zip.ZipEntry(restored));
        zip.write("{\"lat\":999,\"lon\":0,\"time\":100000}\n".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
      boolean corruptRoute = false;
      try {
        BackupManager.restoreFrom(
            isolated, store, new java.io.ByteArrayInputStream(corrupt.toByteArray()));
      } catch (Exception expected) {
        corruptRoute = true;
      }
      check(
          corruptRoute
              && store.runs().size() == 1
              && store.runs().get(0).routeFile().equals(restored),
          "corrupt route rejected before replacing preferences");
      isolated.deleteFile(route);
      isolated.deleteFile(restored);
      isolated.getSharedPreferences("rwun", 0).edit().clear().commit();
      java.util.concurrent.atomic.AtomicReference<Throwable> layoutFailure=new java.util.concurrent.atomic.AtomicReference<>();
      runOnMainSync(()->{
        try{
          for(float scale:new float[]{1f,1.3f,2f}){
            android.content.res.Configuration cfg=new android.content.res.Configuration(getTargetContext().getResources().getConfiguration());cfg.fontScale=scale;
            Context scaled=new android.view.ContextThemeWrapper(getTargetContext().createConfigurationContext(cfg),app.rwun.R.style.AppTheme);
            var tabs=UiKit.tabs(scaled,new UiKit.Palette(true),new String[]{"Workouts","Performance"},1,index->{});
            int width=UiKit.dp(scaled,300);tabs.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(UiKit.dp(scaled,200),android.view.View.MeasureSpec.AT_MOST));tabs.layout(0,0,width,tabs.getMeasuredHeight());
            var selected=(android.widget.LinearLayout)tabs.getChildAt(1);var title=(android.widget.TextView)selected.getChildAt(0);
            check(title.getText().toString().equals("Performance"),"tab excludes visible selection suffix at scale "+scale);
            check(title.getLineCount()==1,"Performance tab stays on one line at scale "+scale);
            check(selected.isSelected()&&selected.getContentDescription().toString().contains("selected"),"selection remains accessible at scale "+scale);
          }
        }catch(Throwable failure){layoutFailure.set(failure);}
      });
      runOnMainSync(()->{try{
        android.graphics.Bitmap card=V1Activity.card(new RunStore.Run("card",LocalDate.now().toString(),5,30),false);
        check(card.getWidth()==1080&&card.getHeight()==1350,"activity card dimensions");card.recycle();
        android.widget.RemoteViews widget=new android.widget.RemoteViews(getTargetContext().getPackageName(),app.rwun.R.layout.workout_widget);
        var parent=new android.widget.FrameLayout(getTargetContext());var inflated=widget.apply(getTargetContext(),parent);
        check(inflated.findViewById(app.rwun.R.id.widget_run)!=null,"widget recording action inflates");
      }catch(Throwable failure){layoutFailure.set(failure);}});
      if(layoutFailure.get()!=null)throw new AssertionError("Tab layout regression",layoutFailure.get());
      result.putString(
          "stream", "PASS: " + assertions + " Android storage and GPX/TCX assertions.\n");
      finish(-1, result);
    } catch (Throwable error) {
      result.putString("stream", "FAIL: " + error + "\n");
      finish(0, result);
    }
  }

  private void check(boolean condition, String description) {
    assertions++;
    if (!condition) throw new AssertionError(description);
  }

  private void expectInvalid(String xml) throws Exception {
    try {
      ActivityFiles.parse(xml.getBytes(StandardCharsets.UTF_8));
    } catch (Exception expected) {
      assertions++;
      return;
    }
    throw new AssertionError("invalid XML accepted");
  }
}
