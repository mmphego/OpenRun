package app.rwun;

import android.app.Application;
import android.health.connect.datatypes.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Real platform record mapping + controlled asynchronous sources/writers, never a device-service claim. */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class HealthConnectTest {
    Application app;RunStore store;
    @Before public void setup(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences("rwun",0).edit().clear().commit();app.getSharedPreferences("health-connect-exports",0).edit().clear().commit();store=new RunStore(app);}
    @After public void settle()throws Exception{for(int i=0;i<100&&(HealthSync.busy()||HealthExport.busy());i++)Thread.sleep(5);}
    static ExerciseSessionRecord session(int n,int type,String provider,long ago){
        Instant start=Instant.now().minusSeconds(ago);
        var meta=new Metadata.Builder().setId(String.format(Locale.ROOT,"00000000-0000-0000-0000-%012d",n)).setDataOrigin(new DataOrigin.Builder().setPackageName(provider).build()).build();
        return new ExerciseSessionRecord.Builder(meta,start,start.plusSeconds(600),type).setStartZoneOffset(ZoneOffset.ofHours(2)).build();
    }
    static final int RUN=ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING,WALK=ExerciseSessionType.EXERCISE_SESSION_TYPE_WALKING;
    static class Source implements HealthSync.Source {
        List<ExerciseSessionRecord> rows;int pages,distances,heart;Double km=2d;boolean hrFails;
        Source(ExerciseSessionRecord... rows){this.rows=List.of(rows);}
        public CompletableFuture<HealthSync.Page> page(Instant start,Instant end,long token){pages++;return CompletableFuture.completedFuture(new HealthSync.Page(rows,-1));}
        public CompletableFuture<Double> distance(ExerciseSessionRecord row){distances++;return CompletableFuture.completedFuture(km);}
        public CompletableFuture<Integer> heartRate(ExerciseSessionRecord row){heart++;return hrFails?HealthSync.failed(new SecurityException("optional permission revoked")):CompletableFuture.completedFuture(140);}
    }
    String read(Source source,boolean hr)throws Exception{String result=HealthSync.sync(store,app.getPackageName(),hr,source).get(5,TimeUnit.SECONDS);settle();return result;}
    static void fails(CompletableFuture<?> result)throws Exception{try{result.get(5,TimeUnit.SECONDS);fail("Failure expected");}catch(ExecutionException expected){assertNotNull(expected.getCause());}}
    @Test public void readAndWritePermissionsAreIndependent(){
        assertFalse(HealthSync.granted(app));assertFalse(HealthExport.granted(app));
        Shadows.shadowOf(app).grantPermissions(HealthSync.PERMISSIONS);assertTrue(HealthSync.granted(app));assertFalse(HealthExport.granted(app));
        Shadows.shadowOf(app).grantPermissions(HealthExport.PERMISSIONS);Shadows.shadowOf(app).denyPermissions(HealthSync.PERMISSIONS);
        assertTrue(HealthExport.granted(app));assertFalse(HealthSync.granted(app));
    }
    @Test public void missingReadPermissionDoesNotCallService()throws Exception{fails(HealthSync.sync(app,store));assertTrue(store.runs().isEmpty());}
    @Test public void runsAndWalksImportSeparatelyAndKeepOptionalHeartRate()throws Exception{
        Source source=new Source(session(1,RUN,"test.publisher",7200),session(2,WALK,"test.publisher",3600));
        assertTrue(read(source,true).contains("Imported 2"));assertEquals(1,store.runs().size());assertEquals(1,store.walks().length());assertEquals(140,store.runs().get(0).avgHr());assertEquals(2,source.heart);
    }
    @Test public void missingRunDistanceIsSkippedAndWalkRetainsTimeOnly()throws Exception{
        Source source=new Source(session(1,RUN,"test.publisher",7200),session(2,WALK,"test.publisher",3600));source.km=null;
        read(source,false);assertTrue(store.runs().isEmpty());assertFalse(store.walks().getJSONObject(0).getBoolean("distanceKnown"));assertEquals(0,source.heart);
    }
    @Test public void ownExportsAndOtherSportsNeverLoopBack()throws Exception{
        Source source=new Source(session(1,RUN,app.getPackageName(),7200),session(2,ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING,"test.publisher",3600));
        read(source,false);assertEquals(0,source.distances);assertTrue(store.runs().isEmpty());
    }
    @Test public void repeatedRecordIdsDoNotDoubleCount()throws Exception{
        Source source=new Source(session(1,RUN,"test.publisher",7200));read(source,false);read(source,false);assertEquals(1,store.runs().size());assertEquals(1,source.distances);
    }
    @Test public void optionalHeartRateFailureDoesNotLoseActivity()throws Exception{
        Source source=new Source(session(1,RUN,"test.publisher",7200));source.hrFails=true;read(source,true);assertEquals(1,store.runs().size());assertEquals(0,store.runs().get(0).avgHr());
    }
    @Test public void crossSourceDuplicatesWaitForReview()throws Exception{
        ExerciseSessionRecord one=session(1,RUN,"test.publisher",7200);
        read(new Source(one),false);
        var two=new ExerciseSessionRecord.Builder(new Metadata.Builder().setId("00000000-0000-0000-0000-000000000002").setDataOrigin(new DataOrigin.Builder().setPackageName("other.publisher").build()).build(),one.getStartTime(),one.getEndTime(),RUN).setStartZoneOffset(one.getStartZoneOffset()).build();
        read(new Source(two),false);assertEquals(1,store.runs().size());assertEquals(1,store.pendingImports().length());
    }
    @Test public void partialReadFailureKeepsSavedPage()throws Exception{
        Source source=new Source(session(1,RUN,"test.publisher",7200)){
            public CompletableFuture<HealthSync.Page> page(Instant start,Instant end,long token){pages++;return token==-1?CompletableFuture.completedFuture(new HealthSync.Page(rows,7)):HealthSync.failed(new IllegalStateException("source offline"));}
        };
        fails(HealthSync.sync(store,app.getPackageName(),false,source));assertEquals(1,store.runs().size());settle();
    }
    @Test public void repeatedPageTokenStopsSafely()throws Exception{
        Source source=new Source(){public CompletableFuture<HealthSync.Page> page(Instant start,Instant end,long token){pages++;return CompletableFuture.completedFuture(new HealthSync.Page(List.of(),7));}};
        fails(HealthSync.sync(store,app.getPackageName(),false,source));assertEquals(2,source.pages);settle();
    }
    @Test public void concurrentReadsAreBlockedAndCancelledCallbacksCannotSave()throws Exception{
        CompletableFuture<HealthSync.Page> pending=new CompletableFuture<>();Source source=new Source(session(1,RUN,"test.publisher",7200)){public CompletableFuture<HealthSync.Page> page(Instant s,Instant e,long token){return pending;}};
        var first=HealthSync.sync(store,app.getPackageName(),false,source);fails(HealthSync.sync(store,app.getPackageName(),false,new Source()));first.cancel(false);pending.complete(new HealthSync.Page(source.rows,-1));Thread.sleep(50);assertTrue(store.runs().isEmpty());assertFalse(HealthSync.busy());
    }
    HealthWritePlan plan(boolean walking,boolean indoor,Double km){Instant start=Instant.now().minusSeconds(1200);return new HealthWritePlan("original-1","OpenRun activity",walking,indoor,start,start.plusSeconds(900),600,km,"Machine entered");}
    @Test public void recordMappingPreservesTypesStableIdsAndNoRoutes(){
        for(boolean walk:new boolean[]{true,false})for(boolean indoor:new boolean[]{true,false}){
            var records=HealthExport.Api34.records(plan(walk,indoor,2d),123);
            var exercise=(ExerciseSessionRecord)records.get(0);var distance=(DistanceRecord)records.get(1);
            assertEquals(walk?WALK:indoor?ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING_TREADMILL:RUN,exercise.getExerciseType());
            assertEquals("rwun-session:original-1",exercise.getMetadata().getClientRecordId());assertEquals("rwun-distance:original-1",distance.getMetadata().getClientRecordId());
            assertEquals(123,exercise.getMetadata().getClientRecordVersion());assertEquals(2000,distance.getDistance().getInMeters(),0);assertFalse(exercise.hasRoute());
        }
    }
    @Test public void timeOnlySessionNeverWritesZeroDistance(){assertEquals(1,HealthExport.Api34.records(plan(true,true,null),1).size());}
    @Test public void changedDistanceAdvancesVersionAndRepeatsRetainVersion(){
        var one=plan(true,true,1d);long first=HealthExport.version(app,one);assertEquals(first,HealthExport.version(app,one));
        var changed=new HealthWritePlan(one.id(),one.title(),true,true,one.start(),one.end(),one.activeSeconds(),2d,one.distanceSource());assertTrue(HealthExport.version(app,changed)>first);
    }
    @Test public void originalIndoorRecordingsSupportTimeOnlyAndCorrectedDistance()throws Exception{
        saveIndoor(null);var one=HealthWritePlan.find(app,store,"indoor:owned");assertNull(one.km());assertTrue(one.walking());assertTrue(one.indoor());
        saveIndoor(2d);var two=HealthWritePlan.find(app,store,"indoor:owned");assertEquals(one.id(),two.id());assertNotEquals(one.fingerprint(),two.fingerprint());assertEquals(2,two.km(),0);
    }
    @Test public void legacyAndImportedRecordsCannotBeExported()throws Exception{
        store.add(new RunStore.Run("health:imported",LocalDate.now().toString(),5,30,5,2,false,"Health Connect · other","",0,1800));
        try{HealthWritePlan.find(app,store,"health:imported");fail();}catch(IllegalArgumentException expected){}
        saveIndoor(2d);JSONObject data=store.v103();data.getJSONArray("indoor").getJSONObject(0).remove("startedAtWall");store.v103(data);
        assertTrue(HealthWritePlan.available(app,store).isEmpty());
    }
    @Test public void originalOutdoorWalkMapsToWalkingWithRealTimestamps()throws Exception{
        long start=Instant.now().minusSeconds(1200).toEpochMilli();String route="route-health-export.jsonl";
        String raw="{\"lat\":0,\"lon\":0,\"time\":"+start+",\"segment\":0}\n{\"lat\":0,\"lon\":0.01,\"time\":"+(start+600000)+",\"segment\":0}\n";
        try(var out=app.openFileOutput(route,0)){out.write(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        store.addWalk(new RunStore.Run("walk:owned",LocalDate.now().toString(),1,10,0,2,false,"GPS",route,0,600));
        var p=HealthWritePlan.find(app,store,"walk:owned");assertTrue(p.walking());assertFalse(p.indoor());assertEquals(start,p.start().toEpochMilli());assertEquals(WALK,((ExerciseSessionRecord)HealthExport.Api34.records(p,1).get(0)).getExerciseType());
    }
    @Test public void writeOnlyAccessExportsAndStaleReviewIsRejected()throws Exception{
        Shadows.shadowOf(app).grantPermissions(HealthExport.PERMISSIONS);assertTrue(HealthAccess.available(app));saveIndoor(1d);
        HealthWritePlan one=HealthWritePlan.find(app,store,"indoor:owned");AtomicInteger calls=new AtomicInteger();List<Long> versions=new CopyOnWriteArrayList<>();
        HealthExport.Writer writer=(p,v)->{calls.incrementAndGet();versions.add(v);return CompletableFuture.completedFuture(null);};
        HealthExport.write(app,one.id(),one.fingerprint(),writer).get(5,TimeUnit.SECONDS);settle();assertFalse(HealthSync.granted(app));
        saveIndoor(2d);fails(HealthExport.write(app,one.id(),one.fingerprint(),writer));settle();assertEquals(1,calls.get());
        var two=HealthWritePlan.find(app,store,one.id());HealthExport.write(app,two.id(),two.fingerprint(),writer).get(5,TimeUnit.SECONDS);settle();assertTrue(versions.get(1)>versions.get(0));
    }
    @Test public void deniedWriteDoesNotInvokeWriterOrChangeLocalHistory()throws Exception{
        saveIndoor(1d);String before=store.backupPreferences().toString();AtomicInteger calls=new AtomicInteger();var p=HealthWritePlan.find(app,store,"indoor:owned");
        fails(HealthExport.write(app,p.id(),p.fingerprint(),(v,n)->{calls.incrementAndGet();return CompletableFuture.completedFuture(null);}));assertEquals(0,calls.get());assertEquals(before,store.backupPreferences().toString());
    }
    void saveIndoor(Double km)throws Exception{
        long start=Instant.now().minusSeconds(1200).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toEpochMilli();
        JSONObject row=new JSONObject().put("id","owned").put("date",LocalDate.now().toString()).put("kind","Walk").put("seconds",600).put("km",km==null?JSONObject.NULL:km).put("effort",3).put("fatigue",2).put("pain",false).put("distanceSource",km==null?"Time only":"Machine entered").put("startedAtWall",start).put("endedAtWall",start+900000);
        store.v103(store.v103().put("indoor",new JSONArray().put(row)));
    }
}
