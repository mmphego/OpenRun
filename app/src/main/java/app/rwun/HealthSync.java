package app.rwun;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.OutcomeReceiver;
import android.health.connect.*;
import android.health.connect.datatypes.*;
import android.health.connect.datatypes.units.Length;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Official Android 14+ framework API. Read-only, foreground import; never fabricates distance. */
final class HealthSync {
  static final String HEART_RATE="android.permission.health.READ_HEART_RATE";
  static final String[] PERMISSIONS={"android.permission.health.READ_EXERCISE","android.permission.health.READ_DISTANCE"};
  private static final AtomicBoolean BUSY=new AtomicBoolean();
  private static <T> CompletableFuture<T> failed(Throwable error){CompletableFuture<T> future=new CompletableFuture<>();future.completeExceptionally(error);return future;}
  static boolean granted(Context c) {
    if(Build.VERSION.SDK_INT<34)return false;
    for(String p:PERMISSIONS)if(c.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)return false;
    return true;
  }
  static String status(Context c) {
    if(Build.VERSION.SDK_INT<34)return "Requires Android 14 or newer in this version";
    if(granted(c))return "Read access granted";
    int count=0;for(String p:PERMISSIONS)if(c.checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED)count++;
    return count==0?"Not connected":"Partial access · exercise and distance required";
  }
  static boolean busy(){return BUSY.get();}
  static CompletableFuture<String> sync(Context context, RunStore store) {
    if(Build.VERSION.SDK_INT<34||!granted(context))return failed(new SecurityException("Grant exercise and distance access first."));
    if(!BUSY.compareAndSet(false,true))return failed(new IllegalStateException("An import is already in progress."));
    CompletableFuture<String> result=new CompletableFuture<>();
    ExecutorService executor=Executors.newSingleThreadExecutor();
    try {
      Api34.start(context,store,result,executor);
    }catch(Exception e){result.completeExceptionally(e);}
    result.whenComplete((value,error)->{BUSY.set(false);executor.shutdown();});
    return result;
  }
  @android.annotation.TargetApi(34)
  private static final class Api34 {
    static void start(Context context,RunStore store,CompletableFuture<String> result,Executor executor){
      HealthConnectManager manager=context.getSystemService(HealthConnectManager.class);
      if(manager==null)throw new IllegalStateException("Health Connect is unavailable. Check system updates and Health Connect settings.");
      new Importer(manager,executor,store,context.getPackageName(),result,context.checkSelfPermission(HEART_RATE)==PackageManager.PERMISSION_GRANTED).page(-1);
    }
  }
  @android.annotation.TargetApi(34)
  private static final class Importer {
    final HealthConnectManager manager;final Executor executor;final RunStore store;final String ownPackage;
    final CompletableFuture<String> result;final boolean heartRate;final Instant end=Instant.now(),start=end.minus(Duration.ofDays(30));
    int added,duplicates,skipped,pages,review;
    Importer(HealthConnectManager m,Executor e,RunStore s,String own,CompletableFuture<String> r,boolean hr){manager=m;executor=e;store=s;ownPackage=own;result=r;heartRate=hr;}
    void page(long token) {
      if(++pages>100){result.completeExceptionally(new IllegalStateException("Import limit reached. Some activities may already be saved; retry safely."));return;}
      var builder=new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class).setTimeRangeFilter(range(start,end)).setPageSize(100).setAscending(true);
      if(token!=-1)builder.setPageToken(token);
      try{manager.readRecords(builder.build(),executor,new OutcomeReceiver<ReadRecordsResponse<ExerciseSessionRecord>,HealthConnectException>(){
        public void onResult(ReadRecordsResponse<ExerciseSessionRecord> response){session(response.getRecords(),0,response.getNextPageToken());}
        public void onError(HealthConnectException e){result.completeExceptionally(e);}
      });}catch(Exception e){result.completeExceptionally(e);}
    }
    void session(List<ExerciseSessionRecord> list,int index,long next) {
      if(result.isDone())return;
      if(index==list.size()){
        if(next!=-1){page(next);return;}
        result.complete("Imported "+added+" running/walking activities · "+duplicates+" already saved · "+skipped+" skipped · "+review+" held for duplicate review in Progress.");return;
      }
      ExerciseSessionRecord s=list.get(index);
      int type=s.getExerciseType();
      boolean walk=isWalking(type);
      if((!walk&&type!=ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING&&type!=ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING_TREADMILL)||s.getMetadata().getDataOrigin().getPackageName().equals(ownPackage)){session(list,index+1,next);return;}
      String id=(walk?"walk:health:":"health:")+s.getMetadata().getId();
      if(store.completed(id)){duplicates++;session(list,index+1,next);return;}
      long seconds=Duration.between(s.getStartTime(),s.getEndTime()).getSeconds();
      if(seconds<1||seconds>180000||s.getEndTime().isAfter(end)){skipped++;session(list,index+1,next);return;}
      // Only distance from the same publisher and time window; do not mix multiple apps' totals.
      var request=new AggregateRecordsRequest.Builder<Length>(range(s.getStartTime(),s.getEndTime())).addAggregationType(DistanceRecord.DISTANCE_TOTAL).addDataOriginsFilter(s.getMetadata().getDataOrigin()).build();
      try{manager.aggregate(request,executor,new OutcomeReceiver<AggregateRecordsResponse<Length>,HealthConnectException>(){
        public void onResult(AggregateRecordsResponse<Length> response){
          try{
            Length distance=response.get(DistanceRecord.DISTANCE_TOTAL);
            double km=distance==null?0:distance.getInMeters()/1000;
            if(!Double.isFinite(km)||km<0||(!walk&&km==0)||km>250){skipped++;}
            else if(store.completed(id)){duplicates++;}
            else {
              Runnable after=()->session(list,index+1,next);
              if(heartRate){var hrRequest=new AggregateRecordsRequest.Builder<Long>(range(s.getStartTime(),s.getEndTime())).addAggregationType(HeartRateRecord.BPM_AVG).addDataOriginsFilter(s.getMetadata().getDataOrigin()).build();try{manager.aggregate(hrRequest,executor,new OutcomeReceiver<AggregateRecordsResponse<Long>,HealthConnectException>(){public void onResult(AggregateRecordsResponse<Long> hr){Long bpm=hr.get(HeartRateRecord.BPM_AVG);save(s,id,km,seconds,bpm==null?0:bpm.intValue(),after);}public void onError(HealthConnectException e){save(s,id,km,seconds,0,after);}});}catch(Exception optionalDenied){save(s,id,km,seconds,0,after);}return;}
              save(s,id,km,seconds,0,after);return;
            }
            session(list,index+1,next);
          }catch(Exception e){result.completeExceptionally(e);}
        }
        public void onError(HealthConnectException e){result.completeExceptionally(e);}
      });}catch(Exception e){result.completeExceptionally(e);}
    }
    void save(ExerciseSessionRecord session,String id,double km,long seconds,int bpm,Runnable after){
      if(result.isDone())return;
      try{
        boolean walk=isWalking(session.getExerciseType());String provider=session.getMetadata().getDataOrigin().getPackageName();
        String date=session.getStartTime().atZone(session.getStartZoneOffset()==null?ZoneId.systemDefault():session.getStartZoneOffset()).toLocalDate().toString();
        RunStore.Run run=new RunStore.Run(id,date,km,(int)Math.max(1,seconds/60),walk?0:5,2,false,"Health Connect · "+provider,"",bpm>0&&bpm<250?bpm:0,seconds);
        var outcome=store.importActivity(run,walk,RunStore.origin(provider,session.getMetadata().getId(),session.getStartTime().toEpochMilli(),session.getEndTime().toEpochMilli()));
        if(outcome==RunStore.ImportResult.ADDED)added++;else if(outcome==RunStore.ImportResult.REVIEW)review++;else duplicates++;
        after.run();
      }catch(Exception e){result.completeExceptionally(e);}
    }
    static boolean isWalking(int type){return type==ExerciseSessionType.EXERCISE_SESSION_TYPE_WALKING;}
    static TimeInstantRangeFilter range(Instant start,Instant end){return new TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build();}
  }
}
