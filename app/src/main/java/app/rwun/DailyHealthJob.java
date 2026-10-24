package app.rwun;

import android.app.*;
import android.app.job.*;
import android.content.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Approximate, opt-in checks. No network or continuously running location/sensor service. */
public final class DailyHealthJob extends JobService {
    static final int JOB_ID=83,NOTIFICATION_ID=1050;
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private CompletableFuture<Void> active;private volatile int generation;
    static boolean permitted(Context context){return HealthAccess.available(context)&&HealthAccess.backgroundSupported(context)&&HealthAccess.granted(context,DailyHealthSource.STEPS,HealthJob.BACKGROUND);}
    static void schedule(Context context){
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);if(scheduler==null)return;
        boolean enabled;try{enabled=new DailyHealthStore(context).settings().movement();}catch(RuntimeException invalid){enabled=false;}
        if(enabled&&permitted(context)&&Reminders.allowed(context)){
            if(scheduler.getPendingJob(JOB_ID)==null)scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(context,DailyHealthJob.class)).setPeriodic(15*60*1000L).setPersisted(true).build());
        }else{scheduler.cancel(JOB_ID);context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);}
    }
    @Override public boolean onStartJob(JobParameters parameters){
        int token=++generation;if(!permitted(this)){schedule(this);return false;}
        try{active=check(this,DailyHealthSource.create(this),()->generation==token);active.whenComplete((ignored,error)->{if(generation==token)jobFinished(parameters,false);});return true;}
        catch(RuntimeException unavailable){return false;}
    }
    @Override public boolean onStopJob(JobParameters parameters){generation++;if(active!=null)active.cancel(false);return false;}
    static boolean suppressed(Context context){RunStore store=new RunStore(context);return store.resting()||store.indoorPending()||!store.checkpoint().isEmpty();}
    static CompletableFuture<Void> check(Context context,DailyHealthSource.Source source,BooleanSupplier running){
        return check(context,source,running,()->permitted(context),ZonedDateTime::now);
    }
    /** Injectable access/clock keeps asynchronous cancellation and permission races reproducible. */
    static CompletableFuture<Void> check(Context context,DailyHealthSource.Source source,BooleanSupplier running,
                                         BooleanSupplier hasAccess,Supplier<ZonedDateTime> clock){
        if(!BUSY.compareAndSet(false,true))return CompletableFuture.completedFuture(null);
        DailyHealthStore store=new DailyHealthStore(context);CompletableFuture<Void> result;
        try{
            var cfg=store.settings();ZonedDateTime now=clock.get();long revision=store.revision();
            if(store.evaluate(now,null,suppressed(context))!=DailyHealthRules.MovementState.UNKNOWN||!hasAccess.getAsBoolean()||!Reminders.allowed(context)){
                BUSY.set(false);return CompletableFuture.completedFuture(null);
            }
            Instant to=now.toInstant(),from=to.minusSeconds(cfg.inactivityMinutes()*60L);
            result=source.movement(from,to).orTimeout(40,TimeUnit.SECONDS).handle((sample,error)->{
                synchronized(DailyHealthStore.LOCK){
                    if(!running.getAsBoolean()||revision!=store.revision()||!hasAccess.getAsBoolean()||!Reminders.allowed(context))return null;
                    ZonedDateTime checked=clock.get();var state=store.evaluate(checked,error==null?sample:null,suppressed(context));
                    if(state==DailyHealthRules.MovementState.REMIND){
                        deliver(context,store.settings());store.delivered(checked);
                        store.status("Movement reminder sent at "+checked.toLocalTime().withSecond(0).withNano(0)+". Low recorded steps; sitting is not established.");
                    }else if(state==DailyHealthRules.MovementState.UNKNOWN)store.status("Insufficient recent step data. No inactivity reminder sent. Sync your watch or check Health Connect access.");
                    else if(state==DailyHealthRules.MovementState.MOVING)store.status("Recent movement recorded. No reminder needed.");
                }return null;
            });
        }catch(RuntimeException error){BUSY.set(false);return HealthSync.failed(error);}
        return result.whenComplete((ignored,error)->BUSY.set(false));
    }
    static void deliver(Context context,DailyHealthRules.Settings cfg){
        Reminders.channels(context);
        PendingIntent walk=PendingIntent.getActivity(context,1051,new Intent(context,IndoorActivity.class).putExtra("goal",cfg.breakMinutes()),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        var notification=new Notification.Builder(context,"movement").setSmallIcon(R.drawable.ic_directions_walk).setContentTitle("Time for a movement break?")
                .setContentText("Little movement recorded over about "+cfg.inactivityMinutes()+" minutes. Try a "+cfg.breakMinutes()+" minute walk.")
                .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context,1052,new Intent(context,DailyHealthActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(new Notification.Action.Builder(null,"Start walk",walk).build())
                .addAction(new Notification.Action.Builder(null,"Already moved",DailyHealthActions.pending(context,DailyHealthActions.MOVED,1053)).build())
                .addAction(new Notification.Action.Builder(null,"Snooze 30 min",DailyHealthActions.pending(context,DailyHealthActions.SNOOZE,1054)).build()).build();
        context.getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification);
    }
}
