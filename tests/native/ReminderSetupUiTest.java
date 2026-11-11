package app.rwun;

import android.app.*;
import android.content.*;
import android.os.Looper;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.time.Instant;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Synthetic native UI/notification checks; no live Health Connect service or OEM delivery claim. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class,qualifiers="w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class ReminderSetupUiTest {
    Application app;
    @Before public void setup(){
        app=RuntimeEnvironment.getApplication();
        for(String file:new String[]{"rwun",DailyHealthStore.FILE,"health-connect-controls"})app.getSharedPreferences(file,0).edit().clear().commit();
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        Shadows.shadowOf(app.getSystemService(NotificationManager.class)).setNotificationsEnabled(true);
        Reminders.channels(app);
    }
    private void detected(boolean enabled){var cfg=DailyHealthRules.defaults();new DailyHealthStore(app).settings(new DailyHealthRules.Settings(enabled,false,cfg.days(),cfg.startMinute(),cfg.endMinute(),60,3),Instant.now());}
    @Test public void blockedChannelDoesNotPostTestAndOpensExactChannel(){
        NotificationManager manager=app.getSystemService(NotificationManager.class);
        manager.deleteNotificationChannel("movement");manager.createNotificationChannel(new NotificationChannel("movement","Movement breaks",NotificationManager.IMPORTANCE_NONE));
        assertFalse(Reminders.channelAllowed(app,"movement"));assertTrue(Reminders.testMovement(app).contains("channel blocked"));assertEquals(0,Shadows.shadowOf(manager).size());
        Intent intent=Reminders.channelSettings(app,"movement");assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS,intent.getAction());assertEquals("movement",intent.getStringExtra(Settings.EXTRA_CHANNEL_ID));assertEquals(app.getPackageName(),intent.getStringExtra(Settings.EXTRA_APP_PACKAGE));
    }
    @Test public void testNotificationDoesNotClaimHealthConnectWasTested(){
        String result=Reminders.testMovement(app);assertTrue(result.contains("Health Connect movement detection was not tested"));assertEquals(1,Shadows.shadowOf(app.getSystemService(NotificationManager.class)).size());
        assertEquals("No movement check yet. Health Connect data may be missing or delayed.",new DailyHealthStore(app).status());
    }
    @Test public void activeDetectionCannotSaveContradictoryFixedTimePrompts()throws Exception{
        detected(true);
        try(var controller=Robolectric.buildActivity(TrainingActivity.class,new Intent().putExtra("screen","reminders")).setup().visible()){
            var activity=controller.get();Switch fixed=NativePlanningTest.find(activity.getWindow().getDecorView(),"Fixed-time movement prompts");assertFalse(fixed.isEnabled());assertFalse(fixed.isChecked());
            assertFalse(NativePlanningTest.edit(activity,"Movement window starts · hour 0–23").isShown());
            NativePlanningTest.click(activity,"Save reminder preferences");assertFalse(new RunStore(app).v103().getJSONObject("reminders").getBoolean("movement"));assertTrue(new DailyHealthStore(app).settings().movement());
            NativePlanningTest.shot(activity,"reminder-setup-health-connect-mode");
        }
    }
    @Test public void returningFromMovementSettingsRefreshesModeAndKeepsWorkoutDraft(){
        try(var controller=Robolectric.buildActivity(TrainingActivity.class,new Intent().putExtra("screen","reminders")).setup().visible()){
            NativePlanningTest.edit(controller.get(),"Workout reminder · hour 0–23").setText("7");controller.pause();detected(true);controller.resume();
            assertFalse(((Switch)NativePlanningTest.find(controller.get().getWindow().getDecorView(),"Fixed-time movement prompts")).isEnabled());
            assertEquals("7",NativePlanningTest.edit(controller.get(),"Workout reminder · hour 0–23").getText().toString());
        }
    }
    @Test public void permissionResultKeepsMovementFormAndReportsDenial()throws Exception{
        try(var controller=Robolectric.buildActivity(DailyHealthActivity.class,new Intent().putExtra("screen","movement")).setup().visible()){
            var activity=controller.get();EditText interval=NativePlanningTest.edit(activity,"Inactivity interval · minutes (minimum 60)");interval.setText("90");
            activity.onRequestPermissionsResult(302,new String[]{DailyHealthSource.STEPS},new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
            assertSame(interval,NativePlanningTest.edit(activity,"Inactivity interval · minutes (minimum 60)"));assertEquals("90",interval.getText().toString());
            assertTrue(ProgressUiTest.all(activity.getWindow().getDecorView()).contains("was not granted"));NativePlanningTest.shot(activity,"reminder-setup-missing-access");
        }
    }
    @Test public void grantedPermissionsAreNotRequestedAgainAndResumePreservesDraft(){
        Shadows.shadowOf(app).grantPermissions(DailyHealthSource.STEPS,HealthJob.BACKGROUND);
        try(var controller=Robolectric.buildActivity(DailyHealthActivity.class,new Intent().putExtra("screen","movement")).setup().visible()){
            var activity=controller.get();Button steps=NativePlanningTest.find(activity.getWindow().getDecorView(),"Health Connect steps allowed");assertNotNull(steps);assertFalse(steps.isEnabled());
            NativePlanningTest.edit(activity,"Working hours start · HH:mm").setText("08:30");controller.pause();HealthPreferences.enabled(app,false,false);controller.resume();
            assertNotNull(NativePlanningTest.find(activity.getWindow().getDecorView(),"Health Connect reading paused"));assertEquals("08:30",NativePlanningTest.edit(activity,"Working hours start · HH:mm").getText().toString());
        }
    }
    @Test public void largeFontSetupRendersWithoutClearingSettings()throws Exception{
        RuntimeEnvironment.setFontScale(1.6f);
        try(var controller=Robolectric.buildActivity(DailyHealthActivity.class,new Intent().putExtra("screen","movement")).setup().visible()){
            NativePlanningTest.shot(controller.get(),"reminder-setup-large-font");assertFalse(new DailyHealthStore(app).settings().movement());
        }finally{RuntimeEnvironment.setFontScale(1f);}
    }
}
