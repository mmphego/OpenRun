package app.rwun;
import android.app.job.*;import android.content.*;import android.os.Build;
/** Opt-in periodic jobs. Android controls execution timing; no exact cadence is promised. */
public final class SyncJob extends JobService {
  private java.util.concurrent.ExecutorService executor;
  public static void schedule(Context context,boolean enabled){JobScheduler scheduler=(JobScheduler)context.getSystemService(JOB_SCHEDULER_SERVICE);if(enabled)scheduler.schedule(new JobInfo.Builder(80,new ComponentName(context,SyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setPeriodic(6*60*60*1000L).setPersisted(true).build());else scheduler.cancel(80);}
  public boolean onStartJob(JobParameters parameters){executor=java.util.concurrent.Executors.newSingleThreadExecutor();executor.execute(()->{boolean retry=false;try{var vault=new SyncVault(this);var settings=vault.read();if(settings.optBoolean("autoBackup",false))new CloudClient(this).upload(this);}catch(Exception e){retry=false;try{var vault=new SyncVault(this);vault.update(data->{if(data.has("token"))data.put("lastError",UserErrors.message(e));});}catch(Exception ignored){}}finally{jobFinished(parameters,retry);executor.shutdown();}});return true;}
  public boolean onStopJob(JobParameters p){if(executor!=null)executor.shutdownNow();return false;}
}
