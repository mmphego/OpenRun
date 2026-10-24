package app.rwun;
import android.app.*;
import android.content.*;
import android.health.connect.datatypes.*;
import java.time.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Synthetic framework host tests, not evidence of a real Health Connect or Garmin service. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,application=Application.class)
public class DailyHealthTest {
    Context context;DailyHealthStore store;Instant now=Instant.parse("2026-10-19T10:00:00Z");
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("rwun",0).edit().clear().commit();context.getSharedPreferences(DailyHealthStore.FILE,0).edit().clear().commit();store=new DailyHealthStore(context);}
    DailyHealthRules.Settings cfg(boolean on){return new DailyHealthRules.Settings(on,true,Set.of(1,2,3,4,5,6,7),0,1440,60,3);}
    @Test public void stateSurvivesRecreationWithoutEnteringTrainingBackup()throws Exception{
        store.settings(cfg(true),now);store.saveCheckin(new DailyHealthRules.Checkin(now,5.0,4,2,DailyHealthRules.Answer.YES,DailyHealthRules.Answer.NO,false));
        var reopened=new DailyHealthStore(context);assertEquals(cfg(true),reopened.settings());assertEquals(DailyHealthRules.Answer.YES,reopened.checkin(now).alcohol());
        String backup=new RunStore(context).backupPreferences().toString();assertFalse(backup.contains("alcohol"));assertFalse(backup.contains("checkin"));assertFalse(backup.contains(DailyHealthStore.FILE));
        assertFalse(new RunStore(context).resting());assertNull(new RunStore(context).config());reopened.clear();assertNull(store.checkin(now));assertFalse(store.settings().movement());
    }
    @Test public void latestCheckinReplacesPreviousAndExpires(){
        store.saveCheckin(new DailyHealthRules.Checkin(now,4.0,5,0,DailyHealthRules.Answer.YES,DailyHealthRules.Answer.UNKNOWN,false));
        store.saveCheckin(new DailyHealthRules.Checkin(now.plusSeconds(10),null,1,1,DailyHealthRules.Answer.NO,DailyHealthRules.Answer.NO,false));
        assertEquals(DailyHealthRules.Answer.NO,store.checkin(now.plusSeconds(10)).alcohol());assertNull(store.checkin(now.plus(Duration.ofDays(8))));assertFalse(context.getSharedPreferences(DailyHealthStore.FILE,0).contains("checkin"));
    }
    @Test public void snoozeRechecksAfterThirtyMinutes(){
        var at=now.atZone(ZoneId.of("Africa/Johannesburg"));store.settings(cfg(true),now.minus(Duration.ofHours(3)));store.delivered(at);store.snooze(now);
        Instant later=now.plusSeconds(31*60);var sample=new DailyHealthRules.Movement(0L,later,later.minusSeconds(3600),later);
        assertEquals(DailyHealthRules.MovementState.WAITING,store.evaluate(at.plusMinutes(29),null,false));assertEquals(DailyHealthRules.MovementState.REMIND,new DailyHealthStore(context).evaluate(at.plusMinutes(31),sample,false));
        store.acknowledge(later);assertEquals(DailyHealthRules.MovementState.WAITING,store.evaluate(at.plusMinutes(32),sample,false));
    }
    @Test public void checkinToggleDoesNotRestartMonitoringWindow(){
        store.settings(cfg(true),now.minusSeconds(7200));store.settings(new DailyHealthRules.Settings(true,false,cfg(true).days(),0,1440,60,3),now);
        assertEquals(DailyHealthRules.MovementState.REMIND,store.evaluate(now.atZone(ZoneOffset.UTC),new DailyHealthRules.Movement(0L,now,now.minusSeconds(3600),now),false));
    }
    Metadata metadata(String name){return new Metadata.Builder().setDataOrigin(new DataOrigin.Builder().setPackageName(name).build()).build();}
    @Test public void dailySummaryDoesNotEstablishRecentFreshness(){
        var daily=new StepsRecord.Builder(metadata("watch"),now.minusSeconds(8*3600),now,0).build();assertNull(DailyHealthSource.Api34.freshThrough(List.of(daily),now));
        var recent=new StepsRecord.Builder(metadata("phone"),now.minusSeconds(120),now.minusSeconds(60),0).build();assertEquals(now.minusSeconds(60),DailyHealthSource.Api34.freshThrough(List.of(daily,recent),now));
    }
    @Test public void restingBaselineRequiresSevenPriorDaysFromSameSource(){
        List<RestingHeartRateRecord> rows=new ArrayList<>();rows.add(new RestingHeartRateRecord.Builder(metadata("watch"),now,65).build());
        for(int i=1;i<=6;i++)rows.add(new RestingHeartRateRecord.Builder(metadata("watch"),now.minus(Duration.ofDays(i)),60).build());
        rows.add(new RestingHeartRateRecord.Builder(metadata("other"),now.minus(Duration.ofDays(7)),40).build());assertTrue(DailyHealthSource.Api34.restingContext(rows,now,ZoneOffset.UTC).contains("needs at least seven"));
        rows.add(new RestingHeartRateRecord.Builder(metadata("watch"),now.minus(Duration.ofDays(7)),60).build());assertTrue(DailyHealthSource.Api34.restingContext(rows,now,ZoneOffset.UTC).contains("median 60 bpm (7 days)"));
    }
    @Test public void sleepWindowIsNotMisrepresentedAsSleepDuration(){
        var row=new SleepSessionRecord.Builder(metadata("watch"),now.minusSeconds(8*3600),now).build();String result=DailyHealthSource.Api34.sleepContext(List.of(row),now);
        assertTrue(result.contains("8h 00m"));assertTrue(result.contains("not measured time asleep"));assertTrue(DailyHealthSource.Api34.sleepContext(List.of(row),now.plus(Duration.ofDays(2))).contains("no recent"));
    }
    @Test public void deniedAccessCancelsJob(){
        store.settings(cfg(true),now);DailyHealthJob.schedule(context);assertNull(context.getSystemService(android.app.job.JobScheduler.class).getPendingJob(DailyHealthJob.JOB_ID));
    }
    @Test @Config(sdk=28) public void manualAnswersWorkOnOlderAndroid(){
        store.saveCheckin(new DailyHealthRules.Checkin(now,null,1,1,DailyHealthRules.Answer.UNKNOWN,DailyHealthRules.Answer.NO,false));assertNotNull(store.checkin(now));assertFalse(DailyHealthJob.permitted(context));assertThrows(IllegalStateException.class,()->DailyHealthSource.create(context));
    }
}
