package app.rwun;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public final class StorageHostTest {
  private static int assertions;

  public static void main(String[] args) throws Exception {
    Context isolated =
        new Context(java.nio.file.Files.createTempDirectory("rwun-storage-tests").toFile());
    RunStore store = new RunStore(isolated);
    store.save(new PlanEngine.Config(8, 3, 12, 5, LocalDate.now()));
    store.completeStrength("foundation");
    check(new RunStore(isolated).strengthSessions().length() == 1, "strength persists");
    boolean invalidStrength = false;
    try { store.completeStrength("unknown"); } catch (IllegalArgumentException expected) { invalidStrength = true; }
    check(invalidStrength && store.strengthSessions().length() == 1, "unknown routine rejected");
    RunStore.validatePreferences(store.backupPreferences());
    org.json.JSONObject badStrength = store.backupPreferences();
    badStrength.put("strength", "[{\"id\":\"x\",\"date\":\"2026-10-17\",\"routine\":\"unknown\"}]");
    boolean invalidStrengthBackup = false;
    try { RunStore.validatePreferences(badStrength); } catch (IllegalArgumentException expected) { invalidStrengthBackup = true; }
    check(invalidStrengthBackup, "invalid strength backup rejected");
    String old = store.planId();
    check(store.config().days() == 3, "configuration persisted");
    store.add(new RunStore.Run("test:1", LocalDate.now().toString(), 1, 10));
    check(
        new RunStore(isolated).runs().size() == 1, "history persists across repository instances");
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
    store.adapt(new AdaptiveCoach.Advice(.8,false,"Low fatigue"));
    check(store.resting() && store.adaptation().equals("Pain"), "ordinary advice cannot clear or overwrite a pain hold");
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
                    row.id().equals(first.id()) && row.date().equals(LocalDate.now().plusDays(1))),
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
    check(store.strengthSessions().length() == 1, "strength survives backup roundtrip");
    check(store.runs().get(0).qualitySeconds() == 180, "quality metrics survive backup");
    String restored = store.runs().get(0).routeFile();
    check(
        !restored.equals(route) && isolated.getFileStreamPath(restored).isFile(),
        "restore stages a new route safely");
    check(isolated.getFileStreamPath(restored).length() > 0, "restored route content readable");
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
            .put("app", "Rwun")
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
    // Health imports retain their provider identity through backup and never complete plan IDs.
    String healthId="health:provider-record-123";
    store.add(new RunStore.Run(healthId,LocalDate.now().toString(),5,30,5,2,false,"Health Connect · publisher.example","",0,1800));
    check(store.completed(healthId)&&!store.completed(sessionKey),"Health import identity is distinct from a planned workout");
    var healthBackup=new java.io.ByteArrayOutputStream();BackupManager.exportTo(isolated,store,healthBackup);
    store.delete(healthId);BackupManager.restoreFrom(isolated,store,new java.io.ByteArrayInputStream(healthBackup.toByteArray()));
    check(store.completed(healthId),"Health record ID survives backup for repeat-import deduplication");
    check(store.runs().stream().anyMatch(r->r.sessionId().equals(healthId)&&r.source().equals("Health Connect · publisher.example")&&r.seconds()==1800),"Health provenance and elapsed duration survive backup");
    boolean healthDuplicate=false;try{store.add(new RunStore.Run(healthId,LocalDate.now().toString(),5,30));}catch(IllegalArgumentException expected){healthDuplicate=true;}
    check(healthDuplicate,"Repeated Health record rejected after restore");
    store.adapt(new AdaptiveCoach.Advice(0,true,"Pain hold"));
    String previousPlan=store.planId();int previousRuns=store.runs().size();double benchmark=store.profile().equivalent5k();
    store.skip(sessionKey,true);
    store.restartPlan(LocalDate.now().plusDays(2));
    check(!store.planId().equals(previousPlan),"restart creates independent completion identity");
    check(store.runs().size()==previousRuns&&store.completed(healthId),"restart retains historical activities");
    check(store.resting(),"restart preserves safety hold");
    check(store.profile().equivalent5k()==benchmark,"restart keeps pace profile");
    check(store.config().start().equals(LocalDate.now().plusDays(2)),"restart changes schedule start");
    check(!store.skipped(sessionKey)&&!store.backupPreferences().has("moves")&&!store.backupPreferences().has("skips"),"restart clears moved and skipped schedule");
    store.checkpoint("pending");String guardedPlan=store.planId();boolean blockedRestart=false;
    try{store.restartPlan(LocalDate.now());}catch(IllegalStateException expected){blockedRestart=true;}
    check(blockedRestart&&store.planId().equals(guardedPlan),"pending recording prevents plan restart");
    store.checkpoint("");store.save(new PlanEngine.Config(8,3,12,5,LocalDate.now()));
    check(store.resting(),"new plan also preserves safety hold");
    store.save(new PlanEngine.Config(8,3,24,21.1,LocalDate.now().plusDays(1)));
    store.profile(new PaceProfile(5,1800,true));
    var prefs=new org.json.JSONObject().put("mode","Maintenance").put("weekdays",new org.json.JSONArray("[1,3,7]")).put("longDay",7).put("strengthMinutes",20).put("strengthLevel",1).put("equipment","bodyweight");store.options(prefs);
    check(store.plannedSessions().stream().allMatch(s->java.util.Set.of(1,3,7).contains(s.date().getDayOfWeek().getValue())),"future schedule honours preferred weekdays");
    String fixed=store.backupPreferences().getString("schedule");prefs.put("strengthMinutes",10);store.options(prefs);check(fixed.equals(store.backupPreferences().getString("schedule")),"strength settings do not regenerate dates");
    var snapshot=new java.io.ByteArrayOutputStream();BackupManager.exportTo(isolated,store,snapshot);BackupManager.restoreFrom(isolated,store,new java.io.ByteArrayInputStream(snapshot.toByteArray()));check(store.options().getString("mode").equals("Maintenance")&&store.backupPreferences().getString("schedule").equals(fixed),"programme preferences and frozen schedule survive backup");
    var corruptSchedule=store.backupPreferences();var scheduleRows=new org.json.JSONArray(corruptSchedule.getString("schedule"));scheduleRows.getJSONObject(0).put("km",-1);corruptSchedule.put("schedule",scheduleRows.toString());boolean invalidSchedule=false;try{store.restorePreferences(corruptSchedule);}catch(Exception expected){invalidSchedule=true;}check(invalidSchedule&&store.backupPreferences().getString("schedule").equals(fixed),"bad schedule cannot replace valid history");
    store.restartPlan(LocalDate.now().plusWeeks(1));check(store.plannedSessions().stream().allMatch(s->!s.date().isBefore(LocalDate.now().plusWeeks(1))),"restart shifts frozen schedule to new dates");
    prefs.put("mode","Race preparation");store.options(prefs);check(store.plannedSessions().stream().anyMatch(s->s.title().equals("Intervals")||s.title().equals("Tempo run")),"quality workouts available when enabled");store.profile(PaceProfile.unset());check(store.plannedSessions().stream().noneMatch(s->s.title().equals("Intervals")||s.title().equals("Tempo run")),"disabling speed work overrides a frozen quality schedule");
    store.save(new PlanEngine.Config(8,4,24,10,LocalDate.now()));check(!store.backupPreferences().has("schedule")&&!store.options().has("weekdays")&&store.options().optInt("strengthMinutes")==10,"new plan resets schedule choices and keeps strength preferences");
    store.save(new PlanEngine.Config(8,3,24,10,LocalDate.now().minusDays(2)));java.util.Map<String,java.time.LocalDate> currentWeek=new java.util.HashMap<>();for(var session:store.plannedSessions())if(session.id().startsWith("0-"))currentWeek.put(session.id(),session.date());store.options(new org.json.JSONObject().put("mode","Base building").put("weekdays",new org.json.JSONArray("[1,3,7]")).put("longDay",7));check(store.plannedSessions().stream().filter(row->row.id().startsWith("0-")).allMatch(row->row.date().equals(currentWeek.get(row.id()))),"applying preferences preserves this week’s dates");
    int beforeConcurrent=store.runs().size();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);java.util.List<java.util.concurrent.Future<?>> writes=new java.util.ArrayList<>();for(int owner=0;owner<2;owner++){final int writer=owner;writes.add(pool.submit(()->{RunStore otherStore=new RunStore(isolated);for(int n=0;n<10;n++)otherStore.add(new RunStore.Run("concurrent:"+writer+":"+n,LocalDate.now().toString(),1,1));}));}for(var write:writes)write.get(10,java.util.concurrent.TimeUnit.SECONDS);pool.shutdown();check(store.runs().size()==beforeConcurrent+20,"UI/background repository instances do not lose concurrent activity writes");
    isolated.deleteFile(route);
    isolated.deleteFile(restored);
    isolated.getSharedPreferences("rwun", 0).edit().clear().commit();
    System.out.println("Storage and backup host tests passed: " + assertions + " assertions.");
  }

  private static void check(boolean condition, String description) {
    assertions++;
    if (!condition) throw new AssertionError(description);
  }
}
