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
  void manage(MainActivity activity,int choice){NativePlanningTest.click(activity,"Manage plan");AlertDialog menu=(AlertDialog)ShadowDialog.getLatestDialog();menu.getListView().performItemClick(menu.getListView().getChildAt(choice),choice,choice);}
  @Test public void managePlanPauseResumeAndDeletePreservesActivity()throws Exception{
    store.add(new RunStore.Run("free:kept",LocalDate.now().toString(),2,15));
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
      NativePlanningTest.click(controller.get(),"Plan");
      NativePlanningTest.shot(controller.get(),"manage-plan");
      manage(controller.get(),1);
      AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();dialog.getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertTrue(store.planPaused());
      manage(controller.get(),1);dialog=(AlertDialog)ShadowDialog.getLatestDialog();dialog.getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertFalse(store.planPaused());
      manage(controller.get(),2);dialog=(AlertDialog)ShadowDialog.getLatestDialog();dialog.getButton(-2).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertNotNull(store.config());
      manage(controller.get(),2);dialog=(AlertDialog)ShadowDialog.getLatestDialog();dialog.getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertNull(store.config());assertEquals(1,store.runs().size());
    }
  }
  @Test public void editStartsWithCurrentSettingsAndCancelKeepsPlan()throws Exception{
    String before=store.planId();try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
      NativePlanningTest.click(controller.get(),"Plan");manage(controller.get(),0);
      assertTrue(ProgressUiTest.all(controller.get().getWindow().getDecorView()).contains("5 km"));
      controller.get().onBackPressed();assertEquals(before,store.planId());
    }
  }
  @Test public void editReviewSavesNewGoalAndKeepsHistory()throws Exception{
    store.add(new RunStore.Run("free:kept",LocalDate.now().toString(),2,15));String before=store.planId();
    try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
      MainActivity activity=controller.get();NativePlanningTest.click(activity,"Plan");manage(activity,0);
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
      for(String action:new String[]{"Edit plan","Pause plan","Delete plan","Plan changes","Restart current plan","Start new plan","Training preferences"})assertTrue(action,menuText.contains(action));
      android.view.View view=menu.getWindow().getDecorView();view.measure(android.view.View.MeasureSpec.makeMeasureSpec(393,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(852,android.view.View.MeasureSpec.EXACTLY));view.layout(0,0,393,852);
      android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(393,852,android.graphics.Bitmap.Config.ARGB_8888);view.draw(new android.graphics.Canvas(bitmap));
      try(FileOutputStream out=new FileOutputStream(new File(System.getProperty("rwun.evidence"),"unified-plan-menu.png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
      menu.getListView().performItemClick(menu.getListView().getChildAt(3),3,3);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      assertEquals(PlanChangesActivity.class.getName(),Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
    }
  }
  @Test public void coachProvidesManagementAndRetainsHistoryWithoutPlan()throws Exception{
    try(var controller=Robolectric.buildActivity(MainActivity.class,new Intent().putExtra("destination","Coach")).setup().visible()){
      MainActivity activity=controller.get();manage(activity,3);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
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
