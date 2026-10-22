package app.rwun;
import android.app.job.*;import android.content.*;import android.content.pm.PackageManager;import android.os.Build;
public final class HealthJob extends JobService {
  public static final String BACKGROUND="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND";
  public static void schedule(Context context,boolean enabled){JobScheduler scheduler=(JobScheduler)context.getSystemService(JOB_SCHEDULER_SERVICE);if(enabled)scheduler.schedule(new JobInfo.Builder(81,new ComponentName(context,HealthJob.class)).setPeriodic(6*60*60*1000L).setPersisted(true).build());else scheduler.cancel(81);}
  public boolean onStartJob(JobParameters p){if(Build.VERSION.SDK_INT<34||!HealthSync.granted(this)||checkSelfPermission(BACKGROUND)!=PackageManager.PERMISSION_GRANTED)return false;HealthSync.sync(this,new RunStore(this)).whenComplete((result,error)->{if(error!=null)RecordingDiagnostics.error(this,"health_background_import_failed");jobFinished(p,false);});return true;}
  public boolean onStopJob(JobParameters p){return false;}
}
