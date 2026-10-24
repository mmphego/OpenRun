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
    @Test public void knownLowMovementNotifiesOnceAndActionPreservesConfiguredGoal(){
        Source source=new Source();var work=check(source,new AtomicBoolean(true),new AtomicBoolean(true));complete(source,0L);work.join();assertEquals(1,count());
        Notification notification=Shadows.shadowOf(notifications).getNotification(DailyHealthJob.NOTIFICATION_ID);assertEquals("Time for a movement break?",notification.extras.getString(Notification.EXTRA_TITLE));
        assertEquals(3,Shadows.shadowOf(notification.actions[0].actionIntent).getSavedIntent().getIntExtra("goal",0));
        Source repeated=new Source();check(repeated,new AtomicBoolean(true),new AtomicBoolean(true)).join();assertEquals(0,repeated.calls);assertEquals(1,count());
    }
    @Test public void deletionDuringReadCannotResurrectStateOrNotification(){
        Source source=new Source();var work=check(source,new AtomicBoolean(true),new AtomicBoolean(true));store.clear();complete(source,0L);work.join();assertEquals(0,count());assertFalse(store.settings().movement());assertTrue(store.status().startsWith("No movement check"));
    }
    @Test public void revokedAccessAndStoppedRunDiscardLateResults(){
        AtomicBoolean access=new AtomicBoolean(true),running=new AtomicBoolean(true);Source source=new Source();var work=check(source,running,access);access.set(false);complete(source,0L);work.join();assertEquals(0,count());
        Source second=new Source();access.set(true);var pending=check(second,running,access);running.set(false);complete(second,0L);pending.join();assertEquals(0,count());
    }
    @Test public void missingDataAndFailedReadsNeverNotify(){
        Source source=new Source();var work=check(source,new AtomicBoolean(true),new AtomicBoolean(true));complete(source,null);work.join();assertEquals(0,count());assertTrue(store.status().contains("Insufficient"));
        Source failed=new Source();var pending=check(failed,new AtomicBoolean(true),new AtomicBoolean(true));failed.result.completeExceptionally(new SecurityException());pending.join();assertEquals(0,count());
    }
    @Test public void holdIntroducedDuringReadSuppressesNotification(){
        Source source=new Source();var work=check(source,new AtomicBoolean(true),new AtomicBoolean(true));new RunStore(app).adapt(new AdaptiveCoach.Advice(0,true,"Existing hold"));complete(source,0L);work.join();assertEquals(0,count());
    }
}
