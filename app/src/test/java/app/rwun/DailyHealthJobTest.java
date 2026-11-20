package app.rwun;

import android.app.*;
import android.content.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Controlled asynchronous source; permissions are tested separately against framework grants. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,application=Application.class)
public class DailyHealthJobTest {
    Application app;DailyHealthStore store;NotificationManager notifications;
    ZonedDateTime now=ZonedDateTime.of(2026,10,19,12,0,0,0,ZoneId.of("Africa/Johannesburg"));
    static class Source implements DailyHealthSource.Source {
        final CompletableFuture<DailyHealthRules.Movement> result=new CompletableFuture<>();int calls;
        public CompletableFuture<DailyHealthRules.Movement> movement(Instant from,Instant to){calls++;return result;}
        public CompletableFuture<String> recovery(Instant at,ZoneId zone,boolean sleep,boolean hr){throw new AssertionError("Movement job must not read recovery records");}
    }
    @Before public void setup(){
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("rwun",0).edit().clear().commit();app.getSharedPreferences(DailyHealthStore.FILE,0).edit().clear().commit();
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);store=new DailyHealthStore(app);
        store.settings(new DailyHealthRules.Settings(true,false,Set.of(1,2,3,4,5,6,7),0,1440,60,3),now.minusHours(3).toInstant());
        notifications=app.getSystemService(NotificationManager.class);assertTrue(Reminders.allowed(app));
    }
    void complete(Source source,Long steps){source.result.complete(new DailyHealthRules.Movement(steps,now.toInstant(),now.minusHours(1).toInstant(),now.toInstant()));}
    CompletableFuture<Void> check(Source source,AtomicBoolean active,AtomicBoolean access){return DailyHealthJob.check(app,source,active::get,access::get,()->now);}
    int count(){return Shadows.shadowOf(notifications).getAllNotifications().size();}
    @Test public void disabledMonitorCannotCancelRaceRefresh(){
        RaceSyncJob.schedule(app);store.clear();DailyHealthJob.schedule(app);
        assertNotNull(app.getSystemService(android.app.job.JobScheduler.class).getPendingJob(RaceSyncJob.ID));
        assertNotEquals(RaceSyncJob.ID,DailyHealthJob.JOB_ID);
    }
    @Test public void lowMovementStartsBreakAndSnoozeStillTracksCompletion(){
        Source source=new Source();var work=check(source,new AtomicBoolean(true),new AtomicBoolean(true));complete(source,0L);work.join();assertEquals(1,count());
        assertEquals(now.toInstant(),store.breakStart(now));store.snooze(now.toInstant());now=now.plusMinutes(5);
        Source walking=new Source();var pending=check(walking,new AtomicBoolean(true),new AtomicBoolean(true));
        walking.result.complete(new DailyHealthRules.Movement(50L,now.toInstant(),now.minusMinutes(5).toInstant(),now.toInstant()));pending.join();
        assertNull(store.breakStart(now));assertEquals("Walking break complete",Shadows.shadowOf(notifications).getNotification(DailyHealthJob.COMPLETION_ID).extras.getString(Notification.EXTRA_TITLE));assertTrue(store.status().contains("complete"));
    }
    @Test public void activeBreakRepeatsOnlyAfterConfiguredInterval(){
        Source first=new Source();var initial=check(first,new AtomicBoolean(true),new AtomicBoolean(true));complete(first,0L);initial.join();
        now=now.plusMinutes(9);Source early=new Source();var pending=check(early,new AtomicBoolean(true),new AtomicBoolean(true));
        early.result.complete(new DailyHealthRules.Movement(10L,now.toInstant(),now.minusMinutes(9).toInstant(),now.toInstant()));pending.join();
        assertEquals(1,Shadows.shadowOf(notifications).getNotification(DailyHealthJob.NOTIFICATION_ID).number);
        now=now.plusMinutes(1);Source due=new Source();var later=check(due,new AtomicBoolean(true),new AtomicBoolean(true));
        due.result.complete(new DailyHealthRules.Movement(20L,now.toInstant(),now.minusMinutes(10).toInstant(),now.toInstant()));later.join();
        assertEquals(2,Shadows.shadowOf(notifications).getNotification(DailyHealthJob.NOTIFICATION_ID).number);
    }
    @Test public void unfinishedBreakDoesNotCarryIntoNextDay(){
        store.startBreak(now);store.delivered(now);store.snooze(now.toInstant());assertNull(store.breakStart(now.plusDays(1)));assertTrue(store.alertDue(now.plusDays(1)));
    }
    @Test public void customizedSettingsSurviveReopeningAndControlSchedule(){
        var configured=new DailyHealthRules.Settings(true,false,Set.of(1,2,3,4,5,6,7),0,1440,90,3,125,80,7,22,17);
        store.settings(configured,now.toInstant());assertEquals(configured,new DailyHealthStore(app).settings());
        DailyHealthJob.schedule(app);assertEquals(7*60000L,app.getSystemService(android.app.job.JobScheduler.class).getPendingJob(DailyHealthJob.JOB_ID).getMinLatencyMillis());
        store.delivered(now);store.snooze(now.toInstant());assertFalse(store.alertDue(now.plusMinutes(21)));assertTrue(store.alertDue(now.plusMinutes(22)));
    }
    @Test public void deletionDuringReadCannotResurrectStateOrNotification(){
        Source source=new Source();var work=check(source,new AtomicBoolean(true),new AtomicBoolean(true));store.clear();complete(source,0L);work.join();assertEquals(0,count());assertFalse(store.settings().movement());assertTrue(store.status().startsWith("No movement check"));
    }
    @Test public void revokedAccessRemindsButStoppedRunDiscardsLateResults(){
        AtomicBoolean access=new AtomicBoolean(true),running=new AtomicBoolean(true);Source source=new Source();var work=check(source,running,access);access.set(false);complete(source,0L);work.join();assertEquals(1,count());
        assertTrue(store.status().contains("unavailable"));store.finishBreak();notifications.cancelAll();
        Source second=new Source();access.set(true);var pending=check(second,running,access);running.set(false);complete(second,0L);pending.join();assertEquals(0,count());
    }
    @Test public void missingStepsRemindWithHonestMessage(){
        Source source=new Source();var work=check(source,new AtomicBoolean(true),new AtomicBoolean(true));complete(source,null);work.join();assertEquals(1,count());assertTrue(store.status().contains("unavailable"));
    }
    @Test public void snoozeNotificationUsesConfiguredDuration(){
        Source first=new Source();var pending=check(first,new AtomicBoolean(true),new AtomicBoolean(true));complete(first,0L);pending.join();
        var notification=Shadows.shadowOf(notifications).getNotification(DailyHealthJob.NOTIFICATION_ID);
        assertEquals("Snooze 15 min",notification.actions[0].title.toString());
        new DailyHealthActions().onReceive(app,new Intent(app,DailyHealthActions.class).setAction(DailyHealthActions.SNOOZE));
        assertEquals(0,count());assertNotNull(store.breakStart(now));
    }
    @Test public void cancellingAReadReleasesTheNextCheck(){
        Source slow=new Source();var pending=check(slow,new AtomicBoolean(true),new AtomicBoolean(true));assertTrue(pending.cancel(false));
        Source next=new Source();var work=check(next,new AtomicBoolean(true),new AtomicBoolean(true));assertEquals(1,next.calls);complete(next,0L);work.join();assertEquals(1,count());
        complete(slow,0L);assertEquals(1,count());
    }
    @Test public void quietHoursAndSkippedDaysSuppressUnavailableDataAlerts(){
        store.settings(new DailyHealthRules.Settings(true,false,Set.of(1),540,1020,60,3),now.toInstant());
        var evening=now.withHour(18);DailyHealthJob.check(app,null,()->true,()->false,()->evening).join();assertEquals(0,count());
        store.skip(now.toLocalDate());DailyHealthJob.check(app,null,()->true,()->false,()->now).join();assertEquals(0,count());
    }
    @Test public void missingPermissionStillNotifiesWithoutReading(){
        DailyHealthJob.check(app,null,()->true,()->false,()->now).join();
        assertEquals(1,count());assertTrue(store.status().contains("unavailable"));assertEquals(now.toInstant(),store.breakStart(now));
    }
    @Test public void editingTargetsDoesNotDiscardAnActiveBreak(){
        store.startBreak(now);var old=store.settings();
        store.settings(new DailyHealthRules.Settings(true,false,old.days(),0,1440,90,3,75,100,5,15,20),now.toInstant());
        assertEquals(now.toInstant(),store.breakStart(now));assertEquals(100,store.settings().breakSteps());
    }
    @Test public void unavailableStepsStillStartAReminder(){
        Source source=new Source();var pending=check(source,new AtomicBoolean(true),new AtomicBoolean(true));
        source.result.completeExceptionally(new IllegalStateException("Health Connect unavailable"));pending.join();
        assertEquals(1,count());
        assertTrue(Shadows.shadowOf(notifications).getNotification(DailyHealthJob.NOTIFICATION_ID).extras.getString(Notification.EXTRA_TEXT).contains("unavailable"));
    }
    @Test public void holdIntroducedDuringReadSuppressesNotification(){
        Source source=new Source();var work=check(source,new AtomicBoolean(true),new AtomicBoolean(true));new RunStore(app).adapt(new AdaptiveCoach.Advice(0,true,"Existing hold"));complete(source,0L);work.join();assertEquals(0,count());
    }
}
