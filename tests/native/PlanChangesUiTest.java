package app.rwun;

import android.app.*;
import android.content.*;
import java.io.*;
import java.time.LocalDate;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w393dp-h852dp-mdpi", manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PlanChangesUiTest {
  Application app;RunStore store;
  @Before public void setup(){
    app=RuntimeEnvironment.getApplication();app.getSharedPreferences("rwun",0).edit().clear().commit();store=new RunStore(app);
    store.save(new PlanEngine.Config(8,3,12,5,LocalDate.now().plusDays(1)));
    TrackingService service=Robolectric.buildService(TrackingService.class).create().get();
    Shadows.shadowOf(app).setComponentNameAndServiceForBindService(new ComponentName(app,TrackingService.class),service.onBind(new Intent()));
  }
  void move(){var first=store.plannedSessions().get(0);store.move(store.proposalFingerprint(),store.planId()+":"+first.id(),store.config().start().plusDays(1));}
  void manage(MainActivity activity, String choice) {
    NativePlanningTest.click(activity, "Manage plan");
    AlertDialog menu = (AlertDialog) ShadowDialog.getLatestDialog();
    android.view.View action = NativePlanningTest.find(menu.getWindow().getDecorView(), choice);
    assertNotNull(choice, action);
    assertTrue(action.performClick());
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  }
  private void shotDialog(AlertDialog dialog, String name) throws Exception {
    var view = dialog.getWindow().getDecorView();
    view.measure(android.view.View.MeasureSpec.makeMeasureSpec(393, android.view.View.MeasureSpec.EXACTLY),
        android.view.View.MeasureSpec.makeMeasureSpec(852, android.view.View.MeasureSpec.AT_MOST));
    view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    var bitmap = android.graphics.Bitmap.createBitmap(view.getWidth(), view.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
    view.draw(new android.graphics.Canvas(bitmap));
    try (var out = new FileOutputStream(new File(System.getProperty("rwun.evidence"), name + ".png"))) {
      assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
    } finally { bitmap.recycle(); }
  }

  @Test public void scheduleEditorWithLargeTextKeepsReviewReachable() throws Exception {
    RuntimeEnvironment.setFontScale(1.6f);
    try (var controller = Robolectric.buildActivity(FeaturesActivity.class,
        new Intent().putExtra("screen", "plan")).setup().visible()) {
      var activity = controller.get();
      NativePlanningTest.shot(activity, "training-schedule-large-font");
      android.view.View review = NativePlanningTest.find(activity.getWindow().getDecorView(), "Review schedule");
      assertTrue(review.requestRectangleOnScreen(new android.graphics.Rect(0, 0, review.getWidth(),
          review.getHeight()), true));
      NativePlanningTest.shot(activity, "training-schedule-large-font-scrolled");
      var visible = new android.graphics.Rect();
      assertTrue(review.getGlobalVisibleRect(visible));
      assertEquals(review.getHeight(), visible.height());
    } finally { RuntimeEnvironment.setFontScale(1f); }
  }

  @Test public void managePlanPauseResumeAndDeletePreservesActivity()throws Exception{
    store.add(new RunStore.Run("free:kept",LocalDate.now().toString(),2,15));
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
      NativePlanningTest.click(controller.get(),"Plan");
      NativePlanningTest.shot(controller.get(),"manage-plan");
      manage(controller.get(), "Pause plan");
      AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();dialog.getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertTrue(store.planPaused());
      manage(controller.get(), "Resume plan");dialog=(AlertDialog)ShadowDialog.getLatestDialog();dialog.getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertFalse(store.planPaused());
      manage(controller.get(), "Delete plan");dialog=(AlertDialog)ShadowDialog.getLatestDialog();dialog.getButton(-2).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertNotNull(store.config());
      manage(controller.get(), "Delete plan");dialog=(AlertDialog)ShadowDialog.getLatestDialog();dialog.getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertNull(store.config());assertEquals(1,store.runs().size());
    }
  }
  @Test public void changingTrainingDaysKeepsPlanAndSurvivesBackup() throws Exception {
    LocalDate start = LocalDate.now().minusDays(1);
    store.save(new PlanEngine.Config(8, 3, 12, 5, start));
    String plan = store.planId();
    var currentWeek = store.plannedSessions().subList(0, 3);
    store.add(new RunStore.Run(plan + ":" + currentWeek.get(0).id(), start.toString(), 2, 15));
    store.setPlanPaused(store.proposalFingerprint(), true);
    store.options(store.options().put("weekdays", new org.json.JSONArray("[1,2,3,4,5,6,7]"))
        .put("longDay", 7));
    assertEquals(plan, store.planId());
    assertEquals(7, store.config().days());
    assertEquals(currentWeek, store.plannedSessions().subList(0, 3));
    assertEquals(7, store.plannedSessions().stream().filter(s -> s.id().startsWith("1-")).count());
    assertTrue(store.planPaused());
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    BackupManager.exportTo(app, store, bytes);
    var schedule = store.plannedSessions();
    app.getSharedPreferences("rwun", 0).edit().clear().commit();
    BackupManager.restoreFrom(app, store, new ByteArrayInputStream(bytes.toByteArray()));
    assertEquals(schedule, store.plannedSessions());
    assertEquals(1, store.runs().size());
  }

  @Test public void editScheduleAddsFourthDayAndRequiresConfirmation() throws Exception {
    String plan = store.planId();
    Intent editor;
    try (var controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity activity = controller.get();
      NativePlanningTest.click(activity, "Plan");
      manage(activity, "Edit plan");
      editor = Shadows.shadowOf(activity).getNextStartedActivity();
      assertEquals(FeaturesActivity.class.getName(), editor.getComponent().getClassName());
    }
    try (var controller = Robolectric.buildActivity(FeaturesActivity.class, editor).setup().visible()) {
      var activity = controller.get();
      NativePlanningTest.shot(activity, "training-schedule");
      var available = java.util.Arrays.stream(java.time.DayOfWeek.values())
          .filter(day -> !store.trainingPreferences().weekdays().contains(day)).findFirst().orElseThrow();
      NativePlanningTest.click(activity, available.getDisplayName(java.time.format.TextStyle.SHORT,
          java.util.Locale.getDefault()));
      controller.recreate();
      activity = controller.get();
      assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(), "4 days per week"));
      NativePlanningTest.click(activity, "Review schedule");
      var review = (AlertDialog) ShadowDialog.getLatestDialog();
      assertTrue(review.isShowing());
      shotDialog(review, "training-schedule-review");
      assertEquals(3, store.config().days());
      review.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertEquals(3, store.config().days());
      NativePlanningTest.click(activity, "Review schedule");
      review = (AlertDialog) ShadowDialog.getLatestDialog();
      review.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertTrue(activity.isFinishing());
      assertEquals(4, store.config().days());
      assertEquals(plan, store.planId());
      store.undoPlanChange(store.previewUndo());
      assertEquals(3, store.config().days());
    }
  }

  @Test @Config(sdk=28) @GraphicsMode(GraphicsMode.Mode.LEGACY)
  public void savingScheduleOffersCalendarForFirstUpdatedWeek() throws Exception {
    store.save(new PlanEngine.Config(8,3,12,5,LocalDate.now()));
    try (var main = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
      MainActivity activity = main.get();
      NativePlanningTest.click(activity, "Plan");
      manage(activity, "Edit plan");
      var launch = Shadows.shadowOf(activity).getNextStartedActivityForResult();
      assertNotNull("The plan editor should return the first updated week", launch);
      try (var editor = Robolectric.buildActivity(FeaturesActivity.class, launch.intent).setup().visible()) {
        var available = java.util.Arrays.stream(java.time.DayOfWeek.values())
            .filter(day -> !store.trainingPreferences().weekdays().contains(day)).findFirst().orElseThrow();
        NativePlanningTest.click(editor.get(), available.getDisplayName(java.time.format.TextStyle.SHORT,
            java.util.Locale.getDefault()));
        NativePlanningTest.click(editor.get(), "Review schedule");
        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        var result = Shadows.shadowOf(editor.get());
        activity.onActivityResult(launch.requestCode, result.getResultCode(), result.getResultIntent());
      }
      var confirmation = (AlertDialog) ShadowDialog.getLatestDialog();
      assertEquals("View in Calendar", confirmation.getButton(AlertDialog.BUTTON_POSITIVE).getText());
      confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
      Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      var start = store.config().start().plusWeeks(1);
      var format = java.time.format.DateTimeFormatter.ofPattern("d MMM");
      var root = activity.getWindow().getDecorView();
      assertNotNull(NativePlanningTest.find(root, start.format(format) + " – " + start.plusDays(6).format(format)));
      assertNotNull(AiPlanningUiTest.findDescription(root, "Previous calendar week"));
      var dateFormat = java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM");
      var workouts = store.plannedSessions().stream().filter(workout -> workout.id().startsWith("1-")).toList();
      assertEquals(4, workouts.size());
      for(var workout : workouts){
        android.view.View day = NativePlanningTest.find(root, workout.date().format(dateFormat));
        assertNotNull(day);
        assertNotNull(NativePlanningTest.find((android.view.View) day.getParent(), workout.title()));
      }
    }
  }

  @Test public void oneTrainingDayPreservesMovedWorkoutsAndRejectsStaleReview() {
    var original = store.plannedSessions().get(0);
    move();
    var moved = store.plannedSessions().stream().filter(s -> s.id().equals(original.id())).findFirst().orElseThrow();
    var settings = new PlanPreferences("Maintenance", java.util.Set.of(java.time.DayOfWeek.SUNDAY),
        java.time.DayOfWeek.SUNDAY);
    var review = store.reviewTrainingSchedule(store.proposalFingerprint(), settings, 12);
    assertEquals(1, review.workouts().size());
    assertEquals(java.time.DayOfWeek.SUNDAY, review.workouts().get(0).date().getDayOfWeek());
    assertTrue(review.workouts().get(0).km() > 0 && review.workouts().get(0).km() < 12);
    store.setPlanPaused(store.proposalFingerprint(), true);
    assertThrows(IllegalStateException.class, () -> store.saveTrainingSchedule(review));
    assertEquals(3, store.config().days());
    store.saveTrainingSchedule(store.reviewTrainingSchedule(store.proposalFingerprint(), settings, 12));
    assertEquals(1, store.config().days());
    assertTrue(store.plannedSessions().contains(moved));
    assertTrue(store.planPaused());
    assertEquals("Training schedule", store.planChanges().optJSONObject(store.planChanges().length() - 1).optString("kind"));
  }

  @Test public void editStartsWithCurrentSettingsAndCancelKeepsPlan()throws Exception{
    String before=store.planId();try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
      NativePlanningTest.click(controller.get(),"Plan");manage(controller.get(), "Change goal or plan length");
      assertTrue(ProgressUiTest.all(controller.get().getWindow().getDecorView()).contains("5 km"));
      controller.get().onBackPressed();assertEquals(before,store.planId());
    }
  }
  @Test public void editReviewSavesNewGoalAndKeepsHistory()throws Exception{
    store.add(new RunStore.Run("free:kept",LocalDate.now().toString(),2,15));String before=store.planId();
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
      MainActivity activity=controller.get();NativePlanningTest.click(activity,"Plan");manage(activity, "Change goal or plan length");
      NativePlanningTest.click(activity,"10 km");NativePlanningTest.click(activity,"Continue");NativePlanningTest.click(activity,"Continue");
      assertTrue(ProgressUiTest.all(activity.getWindow().getDecorView()).contains("Saving replaces the current schedule"));
      NativePlanningTest.click(activity,"Build my plan");assertEquals(10,store.config().goalKm(),0.01);assertNotEquals(before,store.planId());assertEquals(1,store.runs().size());
    }
  }
  @Test public void emptyHistoryIsHonestAndSurvivesRecreation()throws Exception{
    try(var controller=Robolectric.buildActivity(PlanChangesActivity.class).setup().visible()){
      assertTrue(ProgressUiTest.all(controller.get().getWindow().getDecorView()).contains("Earlier changes were not recorded."));
      NativePlanningTest.shot(controller.get(),"plan-changes-empty");controller.recreate();assertEquals(0,new RunStore(app).planChanges().length());
    }
  }
  @Test public void managementUnifiesPlanActionsAndHistory()throws Exception{
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
      MainActivity activity=controller.get();NativePlanningTest.click(activity,"Plan");
      assertNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Plan changes"));
      NativePlanningTest.click(activity,"Manage plan");AlertDialog menu=(AlertDialog)ShadowDialog.getLatestDialog();
      String menuText=ProgressUiTest.all(menu.getWindow().getDecorView());
      for(String action:new String[]{"Edit plan","Pause plan","Delete plan","Plan changes","Restart current plan","Start new plan","Change goal or plan length"})assertTrue(action,menuText.contains(action));
      shotDialog(menu, "unified-plan-menu");
      assertTrue(NativePlanningTest.find(menu.getWindow().getDecorView(), "Plan changes").performClick());Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertEquals(PlanChangesActivity.class.getName(),Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
    }
  }
  @Test public void coachProvidesManagementAndRetainsHistoryWithoutPlan()throws Exception{
    try(var controller=Robolectric.buildActivity(MainActivity.class,new Intent().putExtra("destination","Coach")).setup().visible()){
      MainActivity activity=controller.get();manage(activity, "Plan changes");Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertEquals(PlanChangesActivity.class.getName(),Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
    }
    store.deletePlan(store.proposalFingerprint());
    try(var controller=Robolectric.buildActivity(MainActivity.class,new Intent().putExtra("destination","Coach")).setup().visible()){
      NativePlanningTest.click(controller.get(),"Plan changes");
      assertEquals(PlanChangesActivity.class.getName(),Shadows.shadowOf(controller.get()).getNextStartedActivity().getComponent().getClassName());
    }
  }
  @Test public void historyReviewCancelAndConfirmUndo()throws Exception{
    move();String history=store.planChanges().toString();
    try(var controller=Robolectric.buildActivity(PlanChangesActivity.class).setup().visible()){
      String text=ProgressUiTest.all(controller.get().getWindow().getDecorView());assertTrue(text.contains("Rescheduled workout"));assertTrue(text.contains("Evidence:"));assertTrue(text.contains("km →"));
      NativePlanningTest.shot(controller.get(),"plan-changes-history");
      NativePlanningTest.click(controller.get(),"Review undo of latest change");AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());dialog.getButton(-2).performClick();assertEquals(history,store.planChanges().toString());
      NativePlanningTest.click(controller.get(),"Review undo of latest change");dialog=(AlertDialog)ShadowDialog.getLatestDialog();dialog.getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertEquals("Confirm must append undo after the dialog callback runs",2,new RunStore(app).planChanges().length());
      assertTrue(ProgressUiTest.all(controller.get().getWindow().getDecorView()).contains("Undo"));
      controller.recreate();assertTrue(ProgressUiTest.all(controller.get().getWindow().getDecorView()).contains("Undo"));
    }
  }
  @Test public void dialogCannotClearHoldAddedAfterPreview()throws Exception{
    move();try(var controller=Robolectric.buildActivity(PlanChangesActivity.class).setup().visible()){
      NativePlanningTest.click(controller.get(),"Review undo of latest change");AlertDialog review=(AlertDialog)ShadowDialog.getLatestDialog();
      store.adapt(new AdaptiveCoach.Advice(0,true,"Reported pain"));review.getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(store.resting());assertEquals(2,store.planChanges().length());
      assertTrue(((AlertDialog)ShadowDialog.getLatestDialog()).isShowing());
    }
  }
  @Test public void backupRoundTripAndSingleCommitObservations()throws Exception{
    final int[] notifications={0};android.content.SharedPreferences prefs=app.getSharedPreferences("rwun",0);
    android.content.SharedPreferences.OnSharedPreferenceChangeListener listener=(p,key)->{if(key.equals("moves")){notifications[0]++;assertEquals(1,new RunStore(app).planChanges().length());}};
    prefs.registerOnSharedPreferenceChangeListener(listener);move();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(1,notifications[0]);prefs.unregisterOnSharedPreferenceChangeListener(listener);
    String events=store.planChanges().toString();ByteArrayOutputStream bytes=new ByteArrayOutputStream();BackupManager.exportTo(app,store,bytes);prefs.edit().clear().commit();BackupManager.restoreFrom(app,new RunStore(app),new ByteArrayInputStream(bytes.toByteArray()));assertEquals(events,new RunStore(app).planChanges().toString());
    store.undoPlanChange(store.previewUndo());assertEquals(2,store.planChanges().length());
  }
  @Test public void reviewRevealsEasierTypeAndHarderUndo()throws Exception{
    store.save(new PlanEngine.Config(8,3,24,5,LocalDate.now().plusDays(1)));
    store.profile(new PaceProfile(5,1800,true));
    var hard=store.plannedSessions().stream().filter(w->w.title().equals("Intervals")).findFirst().orElseThrow();
    var changes=java.util.List.of(new ScheduleProposer.Change(hard.id(),hard.date(),hard.date(),"Easy recovery run",hard.km()*.8,false,"A lighter week was requested."));
    String fingerprint=store.proposalFingerprint(),preview=store.previewProposal(fingerprint,changes,"");
    assertTrue(preview.contains("Intervals → Easy recovery run"));assertTrue(preview.contains("Guidance after:"));
    store.applyProposal(fingerprint,changes);
    try(var controller=Robolectric.buildActivity(PlanChangesActivity.class).setup().visible()){
      String history=ProgressUiTest.all(controller.get().getWindow().getDecorView());
      assertTrue(history.contains("Intervals → Easy recovery run"));assertTrue(history.contains("Guidance after:"));
      NativePlanningTest.shot(controller.get(),"plan-changes-effort");
      NativePlanningTest.click(controller.get(),"Review undo of latest change");
      AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();String inverse=ProgressUiTest.all(dialog.getWindow().getDecorView());
      assertTrue(inverse.contains("Easy recovery run → Intervals"));assertTrue(inverse.contains("Guidance after: "+hard.instructions()));dialog.dismiss();
    }
  }
  @Test public void largeFontHistoryIsScrollableAndDoesNotLoseChanges()throws Exception{
    move();RuntimeEnvironment.setFontScale(1.6f);try(var controller=Robolectric.buildActivity(PlanChangesActivity.class).setup().visible()){
      NativePlanningTest.shot(controller.get(),"plan-changes-large-font");assertNotNull(NativePlanningTest.find(controller.get().getWindow().getDecorView(),"Review undo of latest change"));assertEquals(1,store.planChanges().length());
    }finally{RuntimeEnvironment.setFontScale(1f);}
  }
}
