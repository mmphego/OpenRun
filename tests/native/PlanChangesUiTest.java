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
  @Test public void emptyHistoryIsHonestAndSurvivesRecreation()throws Exception{
    try(var controller=Robolectric.buildActivity(PlanChangesActivity.class).setup().visible()){
      assertTrue(ProgressUiTest.all(controller.get().getWindow().getDecorView()).contains("Earlier changes were not recorded."));
      NativePlanningTest.shot(controller.get(),"plan-changes-empty");controller.recreate();assertEquals(0,new RunStore(app).planChanges().length());
    }
  }
  @Test public void planAndCoachOpenHistory()throws Exception{
    for(String destination:new String[]{"Plan","Coach"})try(var controller=Robolectric.buildActivity(MainActivity.class,new Intent().putExtra("destination",destination)).setup().visible()){
      NativePlanningTest.click(controller.get(),"Plan changes");assertEquals(PlanChangesActivity.class.getName(),Shadows.shadowOf(controller.get()).getNextStartedActivity().getComponent().getClassName());
    }
  }
  @Test public void historyReviewCancelAndConfirmUndo()throws Exception{
    move();String history=store.planChanges().toString();
    try(var controller=Robolectric.buildActivity(PlanChangesActivity.class).setup().visible()){
      String text=ProgressUiTest.all(controller.get().getWindow().getDecorView());assertTrue(text.contains("Rescheduled workout"));assertTrue(text.contains("Evidence:"));assertTrue(text.contains("km →"));
      NativePlanningTest.shot(controller.get(),"plan-changes-history");
      NativePlanningTest.click(controller.get(),"Review undo of latest change");AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());dialog.getButton(-2).performClick();assertEquals(history,store.planChanges().toString());
      NativePlanningTest.click(controller.get(),"Review undo of latest change");dialog=(AlertDialog)ShadowDialog.getLatestDialog();dialog.getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
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
  @Test public void largeFontHistoryIsScrollableAndDoesNotLoseChanges()throws Exception{
    move();RuntimeEnvironment.setFontScale(1.6f);try(var controller=Robolectric.buildActivity(PlanChangesActivity.class).setup().visible()){
      NativePlanningTest.shot(controller.get(),"plan-changes-large-font");assertNotNull(NativePlanningTest.find(controller.get().getWindow().getDecorView(),"Review undo of latest change"));assertEquals(1,store.planChanges().length());
    }finally{RuntimeEnvironment.setFontScale(1f);}
  }
}
