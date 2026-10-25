package app.rwun;

import android.content.Context;
import android.os.OutcomeReceiver;
import android.health.connect.*;
import android.health.connect.datatypes.*;
import android.health.connect.datatypes.units.Length;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded, read-only import. Each publisher's distance stays associated with its own sessions. */
final class HealthSync {
 static final String HEART_RATE="android.permission.health.READ_HEART_RATE";
 static final String[] PERMISSIONS={"android.permission.health.READ_EXERCISE","android.permission.health.READ_DISTANCE"};
 private static final AtomicBoolean BUSY=new AtomicBoolean();
 static boolean granted(Context c){return HealthPreferences.selected(c,0)&&HealthAccess.granted(c,PERMISSIONS);}
 static String status(Context c){return HealthAccess.status(c);}
 static boolean busy(){return BUSY.get();}
 static <T> CompletableFuture<T> failed(Throwable e){CompletableFuture<T> f=new CompletableFuture<>();f.completeExceptionally(e);return f;}
 static CompletableFuture<String> sync(Context c,RunStore store){
  if(android.os.Build.VERSION.SDK_INT<34||!HealthAccess.available(c)||!granted(c))return failed(new SecurityException("Grant exercise and distance read access first."));
  try{return sync(store,c.getPackageName(),HealthPreferences.selected(c,1)&&HealthAccess.granted(c,HEART_RATE),new Api34.Source(c),()->granted(c));}
  catch(Exception e){return failed(e);}
 }
 record Page(List<ExerciseSessionRecord> records,long next){}
 interface Source {
  CompletableFuture<Page> page(Instant start,Instant end,long token);
  CompletableFuture<Double> distance(ExerciseSessionRecord session);
  CompletableFuture<Integer> heartRate(ExerciseSessionRecord session);
 }
 /** Injectable source keeps platform I/O separate from import policy and local persistence. */
 @android.annotation.TargetApi(34)
 static CompletableFuture<String> sync(RunStore store,String ownPackage,boolean heartRate,Source source){
  return sync(store,ownPackage,heartRate,source,()->true);
 }
 static CompletableFuture<String> sync(RunStore store,String ownPackage,boolean heartRate,Source source,java.util.function.BooleanSupplier allowed){
  if(!BUSY.compareAndSet(false,true))return failed(new IllegalStateException("An import is already in progress."));
  CompletableFuture<String> result=new CompletableFuture<>();ExecutorService executor=Executors.newSingleThreadExecutor();
  result.whenComplete((value,error)->{BUSY.set(false);executor.shutdown();});HealthTransfer.bound(result);
  new Importer(store,ownPackage,heartRate,source,result,executor,allowed).page(-1);return result;
 }
 @android.annotation.TargetApi(34)
 private static final class Importer {
  final java.util.function.BooleanSupplier allowed;
  final RunStore store;final String own;final boolean hr;final Source source;final CompletableFuture<String> result;final Executor executor;
  final Instant end=Instant.now(),start=end.minus(Duration.ofDays(30));final Set<Long> tokens=new HashSet<>();
  int pages,added,duplicates,skipped,review,seen,timeOnly;
  Importer(RunStore s,String p,boolean h,Source src,CompletableFuture<String> r,Executor e,java.util.function.BooleanSupplier allowed){store=s;own=p;hr=h;source=src;result=r;executor=e;this.allowed=allowed;}
  boolean active(){if(result.isDone())return false;if(!allowed.getAsBoolean()){result.completeExceptionally(new SecurityException("Reading paused or activity access changed. Saved activities are kept."));return false;}return true;}
  void page(long token){
   if(!active())return;
   if(++pages>100||!tokens.add(token)){result.completeExceptionally(new IllegalStateException("Health Connect pagination stopped safely. Saved activities are kept; retry later."));return;}
   try{source.page(start,end,token).whenCompleteAsync((page,error)->{
    if(!active())return;if(error!=null){result.completeExceptionally(error);return;}
    if(page==null||page.records()==null||page.records().size()>100){result.completeExceptionally(new IllegalStateException("Health Connect returned an invalid page."));return;}
    session(page.records(),0,page.next());
   },executor);}catch(Exception e){result.completeExceptionally(e);}
  }
  void session(List<ExerciseSessionRecord> records,int index,long next){
   if(!active())return;
   if(index==records.size()){if(next!=-1){page(next);return;}result.complete("Imported "+added+" runs/walks · "+duplicates+" already saved · "+skipped+" skipped · "+review+" held for duplicate review. "+timeOnly+" imported without distance. Checked "+seen+" sessions from the last 30 days. "+(seen==0?"No exercise sessions were returned. Check that your source app writes exercise sessions to Health Connect; steps alone do not create activities.":"Open Progress for saved runs or Walking for saved walks; possible duplicates need review."));return;}
   seen++;ExerciseSessionRecord s=records.get(index);int type=s.getExerciseType();boolean walk=type==ExerciseSessionType.EXERCISE_SESSION_TYPE_WALKING;
   String provider=s.getMetadata().getDataOrigin().getPackageName(),external=s.getMetadata().getId();
   if((!walk&&type!=ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING&&type!=ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING_TREADMILL)||provider.equals(own)){skipped++;session(records,index+1,next);return;}
   String id=(walk?"walk:health:":"health:")+external;
   long seconds=Duration.between(s.getStartTime(),s.getEndTime()).getSeconds();
   if(external.isEmpty()||id.length()>180||provider.isEmpty()||seconds<1||seconds>180000||s.getEndTime().isAfter(end)||s.getStartTime().isBefore(start)){skipped++;session(records,index+1,next);return;}
   if(store.completed(id)){duplicates++;session(records,index+1,next);return;}
   try{source.distance(s).whenCompleteAsync((km,error)->{
    if(!active())return;if(error!=null){result.completeExceptionally(error);return;}
    double value=km==null?0:km;
    if(!Double.isFinite(value)||value<0||value>250){skipped++;session(records,index+1,next);return;}
    if(hr){try{source.heartRate(s).whenCompleteAsync((bpm,optionalError)->save(s,id,value,seconds,optionalError==null&&bpm!=null?bpm:0,()->session(records,index+1,next)),executor);}catch(Exception optionalError){save(s,id,value,seconds,0,()->session(records,index+1,next));}}
    else save(s,id,value,seconds,0,()->session(records,index+1,next));
   },executor);}catch(Exception e){result.completeExceptionally(e);}
  }
  void save(ExerciseSessionRecord s,String id,double km,long seconds,int bpm,Runnable after){
   if(!active())return;
   try{boolean walk=s.getExerciseType()==ExerciseSessionType.EXERCISE_SESSION_TYPE_WALKING;String provider=s.getMetadata().getDataOrigin().getPackageName();
    String date=s.getStartTime().atZone(s.getStartZoneOffset()==null?ZoneId.systemDefault():s.getStartZoneOffset()).toLocalDate().toString();
    var run=new RunStore.Run(id,date,km,(int)Math.max(1,seconds/60),walk?0:5,2,false,"Health Connect · "+provider,"",bpm>0&&bpm<250?bpm:0,seconds);
    var outcome=store.importActivity(run,walk,RunStore.origin(provider,s.getMetadata().getId(),s.getStartTime().toEpochMilli(),s.getEndTime().toEpochMilli()));
    if(outcome==RunStore.ImportResult.ADDED){added++;if(km==0)timeOnly++;}else if(outcome==RunStore.ImportResult.REVIEW)review++;else duplicates++;
    after.run();
   }catch(Exception e){result.completeExceptionally(e);}
  }
 }
 @android.annotation.TargetApi(34)
 static final class Api34 {
  static TimeInstantRangeFilter range(Instant start,Instant end){return new TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build();}
  static AggregateRecordsRequest<Length> distanceRequest(ExerciseSessionRecord s){return new AggregateRecordsRequest.Builder<Length>(range(s.getStartTime(),s.getEndTime())).addAggregationType(DistanceRecord.DISTANCE_TOTAL).addDataOriginsFilter(s.getMetadata().getDataOrigin()).build();}
  static final class Source implements HealthSync.Source {
   final HealthConnectManager manager;final Executor callback;
   Source(Context c){manager=c.getSystemService(HealthConnectManager.class);callback=c.getMainExecutor();if(manager==null)throw new IllegalStateException("Health Connect is unavailable.");}
   public CompletableFuture<Page> page(Instant start,Instant end,long token){CompletableFuture<Page> f=new CompletableFuture<>();var b=new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class).setTimeRangeFilter(range(start,end)).setPageSize(100).setAscending(true);if(token!=-1)b.setPageToken(token);try{manager.readRecords(b.build(),callback,new OutcomeReceiver<ReadRecordsResponse<ExerciseSessionRecord>,HealthConnectException>(){public void onResult(ReadRecordsResponse<ExerciseSessionRecord> r){f.complete(new Page(r.getRecords(),r.getNextPageToken()));}public void onError(HealthConnectException e){f.completeExceptionally(e);}});}catch(Exception e){f.completeExceptionally(e);}return f;}
   public CompletableFuture<Double> distance(ExerciseSessionRecord s){CompletableFuture<Double> f=new CompletableFuture<>();try{manager.aggregate(distanceRequest(s),callback,new OutcomeReceiver<AggregateRecordsResponse<Length>,HealthConnectException>(){public void onResult(AggregateRecordsResponse<Length> r){Length length=r.get(DistanceRecord.DISTANCE_TOTAL);f.complete(length==null?null:length.getInMeters()/1000);}public void onError(HealthConnectException e){f.completeExceptionally(e);}});}catch(Exception e){f.completeExceptionally(e);}return f;}
   public CompletableFuture<Integer> heartRate(ExerciseSessionRecord s){CompletableFuture<Integer> f=new CompletableFuture<>();var request=new AggregateRecordsRequest.Builder<Long>(range(s.getStartTime(),s.getEndTime())).addAggregationType(HeartRateRecord.BPM_AVG).addDataOriginsFilter(s.getMetadata().getDataOrigin()).build();try{manager.aggregate(request,callback,new OutcomeReceiver<AggregateRecordsResponse<Long>,HealthConnectException>(){public void onResult(AggregateRecordsResponse<Long> r){Long bpm=r.get(HeartRateRecord.BPM_AVG);f.complete(bpm==null?0:bpm.intValue());}public void onError(HealthConnectException e){f.completeExceptionally(e);}});}catch(Exception e){f.completeExceptionally(e);}return f;}
  }
 }
}
