package app.rwun;
import android.content.Context;import android.content.pm.PackageManager;import android.health.connect.*;import android.health.connect.datatypes.*;import android.health.connect.datatypes.units.Length;import android.os.OutcomeReceiver;import java.time.*;import java.util.*;import java.util.concurrent.*;
final class HealthExport {
  static final String[] PERMISSIONS={"android.permission.health.WRITE_EXERCISE","android.permission.health.WRITE_DISTANCE"};
  static boolean granted(Context c){for(String p:PERMISSIONS)if(c.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)return false;return true;}
  static CompletableFuture<String> write(Context c,RunStore.Run run){
    CompletableFuture<String> future=new CompletableFuture<>();
    if(android.os.Build.VERSION.SDK_INT<34){future.completeExceptionally(new IllegalStateException("Health Connect requires Android 14 or newer"));return future;}
    try{
      if(!granted(c))throw new SecurityException("Grant exercise and distance write access first");
      if(!run.source().equals("GPS")||run.routeFile().isEmpty())throw new IllegalArgumentException("Only OpenRun GPS recordings with real timestamps can be exported");
      var points=TrackingService.readRoute(c,run.routeFile());if(points.size()<2)throw new IllegalArgumentException("This recording does not have enough timestamped GPS points");
      Instant start=Instant.ofEpochMilli(points.get(0).time()),end=Instant.ofEpochMilli(points.get(points.size()-1).time());if(!end.isAfter(start))throw new IllegalArgumentException("Invalid recording timestamps");
      var sessionMeta=new Metadata.Builder().setClientRecordId("rwun-session:"+run.sessionId()).setClientRecordVersion(1).setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED).build();
      var distanceMeta=new Metadata.Builder().setClientRecordId("rwun-distance:"+run.sessionId()).setClientRecordVersion(1).setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED).build();
      var session=new ExerciseSessionRecord.Builder(sessionMeta,start,end,ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING).setTitle("OpenRun · "+run.workoutType()).build();
      var distance=new DistanceRecord.Builder(distanceMeta,start,end,Length.fromMeters(run.km()*1000)).build();
      var manager=c.getSystemService(HealthConnectManager.class);if(manager==null)throw new IllegalStateException("Health Connect is unavailable");
      manager.insertRecords(List.of(session,distance),c.getMainExecutor(),new OutcomeReceiver<InsertRecordsResponse,HealthConnectException>(){public void onResult(InsertRecordsResponse r){future.complete("Run exported to Health Connect. Repeating uses the same record IDs.");}public void onError(HealthConnectException e){future.completeExceptionally(e);}});
    }catch(Exception e){future.completeExceptionally(e);}return future;
  }

}
