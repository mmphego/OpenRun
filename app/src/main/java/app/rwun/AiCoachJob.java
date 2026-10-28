package app.rwun;

import android.app.*;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;
import org.json.*;

/** One persisted, network-constrained post-run job; Android controls execution time. */
public final class AiCoachJob extends JobService {
  private ExecutorService executor;private volatile boolean stopped;
  private static final java.util.concurrent.atomic.AtomicBoolean ACTIVE=new java.util.concurrent.atomic.AtomicBoolean();
  static void schedule(Context context){
    try{JSONObject settings=new AiCoachSettings(context).read();RunStore store=new RunStore(context);
      if(!settings.optBoolean("consent")||!settings.optBoolean("automatic")||store.config()==null||store.runs().isEmpty()||store.aiHistoryReviewed())return;
      JobScheduler scheduler=context.getSystemService(JobScheduler.class);if(scheduler!=null&&scheduler.getPendingJob(84)==null)scheduler.schedule(new JobInfo.Builder(84,new ComponentName(context,AiCoachJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }catch(RuntimeException ignored){/* The settings screen exposes unreadable credentials; recording stays available. */}
  }
  static void cancel(Context c){JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(scheduler!=null)scheduler.cancel(84);}
  public boolean onStartJob(JobParameters params){
    if(!ACTIVE.compareAndSet(false,true))return false;stopped=false;executor=Executors.newSingleThreadExecutor();executor.execute(()->{
      boolean retry=false;String requestedToken="";
      try{AiCoachSettings vault=new AiCoachSettings(this);JSONObject settings=vault.read();RunStore store=new RunStore(this);
        if(!settings.optBoolean("consent")||!settings.optBoolean("automatic")||store.aiHistoryReviewed())return;
        AiCoachPolicy.Request request=AiCoachPolicy.snapshot(store,settings,true);requestedToken=request.runToken();
        AiCoachPolicy.Proposal proposal=AiCoachPolicy.validate(request,AiCoachClient.generate(settings,AiCoachPolicy.prompt(request,null)));
        synchronized(RunStore.LOCK){JSONObject current=vault.read();if(stopped||!settings.optString("revision").equals(current.optString("revision")))return;
          store.applyAiPlan(request.fingerprint(),proposal.sessions(),proposal.explanation(),settings.getString("provider"),request.runToken());
          vault.result("Post-run review saved.\n"+proposal.explanation());
        }
        Reminders.schedule(this);notifyResult(this,settings,proposal.sessions().isEmpty()?"Your running plan was reviewed":"Your running plan has been updated");
      }catch(java.net.SocketTimeoutException|java.net.UnknownHostException|java.net.ConnectException e){retry=true;status("AI Coach is waiting for a connection. Your current plan is kept.");}
      catch(IllegalStateException e){RunStore current=new RunStore(this);retry=!current.resting()&&(!current.checkpoint().isEmpty()||current.indoorPending()||!requestedToken.isEmpty()&&!requestedToken.equals(current.aiHistoryToken()));status("AI Coach needs review. Open AI Coach to continue. Your saved activities are kept.");}
      catch(Exception e){status("AI Coach could not update the plan. Open AI Coach and try again.");}
      finally{ACTIVE.set(false);if(!stopped)jobFinished(params,retry);executor.shutdown();}
    });return true;
  }
  private void status(String text){try{AiCoachSettings settings=new AiCoachSettings(this);settings.result(text);if(!stopped)notifyResult(this,settings.read(),"AI Coach needs attention");}catch(Exception ignored){}}
  public boolean onStopJob(JobParameters params){stopped=true;if(executor!=null)executor.shutdownNow();return true;}
  static void notifyResult(Context c,JSONObject settings,String title){
    if(!settings.optBoolean("notifications")||!Reminders.allowed(c))return;
    NotificationManager manager=c.getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("ai_coach","AI Coach plan reviews",NotificationManager.IMPORTANCE_DEFAULT));
    PendingIntent open=PendingIntent.getActivity(c,1084,new Intent(c,AiCoachActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    manager.notify(1084,new Notification.Builder(c,"ai_coach").setSmallIcon(R.drawable.ic_directions_run).setContentTitle(title).setContentText("Open AI Coach to see the review and your next steps.").setContentIntent(open).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build());
  }
}
