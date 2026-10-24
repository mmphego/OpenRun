package app.rwun;
import android.app.job.*;import android.content.*;import java.util.concurrent.CompletableFuture;
/** Consent-gated reads only. Never writes activities from a scheduled job. */
public final class HealthJob extends JobService {
 public static final String BACKGROUND="android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND";
 private CompletableFuture<String> active;private volatile boolean stopped;
 public static void schedule(Context c,boolean enabled){JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler==null)return;if(enabled&&HealthAccess.backgroundSupported(c)&&HealthSync.granted(c)&&HealthAccess.granted(c,BACKGROUND))scheduler.schedule(new JobInfo.Builder(81,new ComponentName(c,HealthJob.class)).setPeriodic(6*60*60*1000L).setPersisted(true).build());else scheduler.cancel(81);}
 public boolean onStartJob(JobParameters p){stopped=false;if(!HealthAccess.available(this)||!HealthSync.granted(this)||!HealthAccess.granted(this,BACKGROUND)){schedule(this,false);return false;}active=HealthSync.sync(this,new RunStore(this));active.whenComplete((result,error)->{if(error!=null&&!stopped)RecordingDiagnostics.error(this,"health_background_import_failed");if(!stopped)jobFinished(p,false);});return true;}
 public boolean onStopJob(JobParameters p){stopped=true;if(active!=null)active.cancel(false);return false;}
}
