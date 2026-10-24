package app.rwun;
import android.app.*;
import android.content.*;
import android.widget.*;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class,qualifiers="w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class DailyHealthUiTest {
    Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("rwun",0).edit().clear().commit();context.getSharedPreferences(DailyHealthStore.FILE,0).edit().clear().commit();}
    org.robolectric.android.controller.ActivityController<DailyHealthActivity> open(String screen){return Robolectric.buildActivity(DailyHealthActivity.class,new Intent().putExtra("screen",screen)).setup().visible();}
    @Test public void overviewAndWorkingHoursRender()throws Exception{
        try(var controller=open("home")){NativePlanningTest.shot(controller.get(),"daily-health-home");NativePlanningTest.click(controller.get(),"Configure movement breaks");assertNotNull(NativePlanningTest.edit(controller.get(),"Inactivity interval · minutes (minimum 60)"));NativePlanningTest.shot(controller.get(),"daily-health-working-hours");}
    }
    @Test public void invalidMinimumsAreRejectedAndDraftSurvivesRotation(){
        try(var controller=open("movement")){
            var activity=controller.get();NativePlanningTest.edit(activity,"Inactivity interval · minutes (minimum 60)").setText("59");NativePlanningTest.click(activity,"Save movement preferences");assertFalse(new DailyHealthStore(context).settings().movement());
            NativePlanningTest.edit(activity,"Inactivity interval · minutes (minimum 60)").setText("60");NativePlanningTest.edit(activity,"Walking break · minutes (minimum 3)").setText("3");
            ((CheckBox)NativePlanningTest.find(activity.getWindow().getDecorView(),"Offer a check-in before new sessions")).setChecked(true);
            controller.recreate();activity=controller.get();assertEquals("60",NativePlanningTest.edit(activity,"Inactivity interval · minutes (minimum 60)").getText().toString());
            NativePlanningTest.click(activity,"Save movement preferences");assertEquals(60,new DailyHealthStore(context).settings().inactivityMinutes());assertTrue(new DailyHealthStore(context).settings().checkin());
        }
    }
    @Test public void savingCheckinCannotClearHoldOrEditPlan()throws Exception{
        RunStore training=new RunStore(context);training.adapt(new AdaptiveCoach.Advice(0,true,"Existing hold"));
        try(var controller=open("checkin")){
            NativePlanningTest.shot(controller.get(),"daily-health-checkin");NativePlanningTest.edit(controller.get(),"Hours slept · optional").setText("5");NativePlanningTest.click(controller.get(),"Save and review recovery");
            NativePlanningTest.shot(controller.get(),"daily-health-recovery-review");assertNotNull(NativePlanningTest.find(controller.get().getWindow().getDecorView(),"Review recovery before training"));assertTrue(training.resting());assertEquals("Existing hold",training.adaptation());
        }
    }
    @Test public void unknownAnswersAndLargeFontsAreReadable()throws Exception{
        RuntimeEnvironment.setFontScale(1.6f);
        try(var controller=open("checkin")){NativePlanningTest.shot(controller.get(),"daily-health-checkin-large-font");NativePlanningTest.click(controller.get(),"Save and review recovery");assertNotNull(NativePlanningTest.find(controller.get().getWindow().getDecorView(),"Recovery information is incomplete"));NativePlanningTest.shot(controller.get(),"daily-health-unknown-review-large-font");}
        finally{RuntimeEnvironment.setFontScale(1f);}
    }
    @Test public void optionalPromptDoesNotStartRecorderByOpeningCheckin(){
        DailyHealthStore store=new DailyHealthStore(context);var d=DailyHealthRules.defaults();store.settings(new DailyHealthRules.Settings(false,true,d.days(),d.startMinute(),d.endMinute(),60,3),Instant.now());
        try(var controller=open("home")){
            boolean[] started={false};RecoveryPrompt prompt=new RecoveryPrompt();prompt.beforeStart(controller.get(),()->started[0]=true);AlertDialog dialog=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertFalse(started[0]);dialog.getButton(-1).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertFalse(started[0]);
            Intent intent=Shadows.shadowOf(controller.get()).getNextStartedActivity();assertNotNull(intent);assertEquals("checkin",intent.getStringExtra("screen"));
            store.saveCheckin(new DailyHealthRules.Checkin(Instant.now(),null,0,0,DailyHealthRules.Answer.UNKNOWN,DailyHealthRules.Answer.UNKNOWN,false));prompt.beforeStart(controller.get(),()->started[0]=true);assertTrue(started[0]);
        }
    }
    @Test public void unreadableOptionalStateCanBeReviewedWithoutLosingTrainingData(){
        context.getSharedPreferences(DailyHealthStore.FILE,0).edit().putString("settings","broken").commit();
        try(var controller=open("home")){
            NativePlanningTest.click(controller.get(),"Review feature data");
            assertNotNull(NativePlanningTest.find(controller.get().getWindow().getDecorView(),"Delete movement & recovery data"));
            boolean[] started={false};RecoveryPrompt prompt=new RecoveryPrompt();prompt.beforeStart(controller.get(),()->started[0]=true);
            AlertDialog dialog=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();assertFalse(started[0]);
            dialog.getButton(-3).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(started[0]);
            assertEquals("broken",context.getSharedPreferences(DailyHealthStore.FILE,0).getString("settings",""));
        }
    }
}
