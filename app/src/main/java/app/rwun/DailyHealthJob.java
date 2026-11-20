package app.rwun;

import android.app.*;
import android.app.job.*;
import android.content.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Best-effort background checks; Android can defer jobs, particularly in battery-saving modes. */
public final class DailyHealthJob extends JobService {
    static final int JOB_ID=83,NOTIFICATION_ID=1050,COMPLETION_ID=1055;
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    private CompletableFuture<Void> active;private volatile int generation;
    static boolean permitted(Context context){return HealthPreferences.enabled(context,false)&&HealthAccess.available(context)&&HealthAccess.backgroundSupported(context)&&HealthAccess.granted(context,DailyHealthSource.STEPS,HealthJob.BACKGROUND);}
    static void schedule(Context context){schedule(context,false);}
    private static void schedule(Context context,boolean force){
        JobScheduler scheduler=context.getSystemService(JobScheduler.class);if(scheduler==null)return;
        boolean enabled;int minutes;try{var cfg=new DailyHealthStore(context).settings();enabled=cfg.movement();minutes=cfg.checkMinutes();}catch(RuntimeException invalid){enabled=false;minutes=10;}
        if(enabled){
            JobInfo pending=scheduler.getPendingJob(JOB_ID);long delay=minutes*60000L;
            if(force||pending==null||pending.isPeriodic()||pending.getMinLatencyMillis()!=delay)
                if(scheduler.schedule(new JobInfo.Builder(JOB_ID,new ComponentName(context,DailyHealthJob.class)).setMinimumLatency(delay).setPersisted(true).build())!=JobScheduler.RESULT_SUCCESS)
                    android.util.Log.e("DailyHealthJob","Android rejected movement job scheduling");
        }else{scheduler.cancel(JOB_ID);context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);context.getSystemService(NotificationManager.class).cancel(COMPLETION_ID);}
    }
    @Override public boolean onStartJob(JobParameters parameters){
        int token=++generation;
        DailyHealthSource.Source source=null;
        if(permitted(this))try{source=DailyHealthSource.create(this);}catch(RuntimeException unavailable){android.util.Log.w("DailyHealthJob","Health Connect unavailable",unavailable);}
        try{
            active=check(this,source,()->generation==token);
            active.whenComplete((ignored,error)->new android.os.Handler(getMainLooper()).post(()->{if(generation==token){if(error!=null)android.util.Log.w("DailyHealthJob","Movement check failed",error);jobFinished(parameters,false);schedule(this,true);}}));
            return true;
        }catch(RuntimeException error){android.util.Log.w("DailyHealthJob","Movement check failed",error);new android.os.Handler(getMainLooper()).post(()->schedule(this,true));return false;}
    }
    @Override public boolean onStopJob(JobParameters parameters){generation++;if(active!=null)active.cancel(false);return true;}
    static boolean suppressed(Context context){RunStore store=new RunStore(context);return store.resting()||store.indoorPending()||!store.checkpoint().isEmpty();}
    static CompletableFuture<Void> check(Context context,DailyHealthSource.Source source,BooleanSupplier running){return check(context,source,running,()->permitted(context),ZonedDateTime::now);}
    static CompletableFuture<Void> check(Context context,DailyHealthSource.Source source,BooleanSupplier running,BooleanSupplier hasAccess,Supplier<ZonedDateTime> clock){
        if(!BUSY.compareAndSet(false,true))return CompletableFuture.completedFuture(null);
        DailyHealthStore store=new DailyHealthStore(context);CompletableFuture<Void> result;
        try{
            var cfg=store.settings();ZonedDateTime now=clock.get();long revision=store.revision();
            if(!store.eligible(now,suppressed(context))||!Reminders.channelAllowed(context,SpokenReminder.channel(context))){BUSY.set(false);return CompletableFuture.completedFuture(null);}
            Instant start=store.breakStart(now),to=now.toInstant();
            Instant from=start==null?to.minusSeconds(cfg.inactivityMinutes()*60L):start;
            CompletableFuture<DailyHealthRules.Movement> read;
            if(source==null||!hasAccess.getAsBoolean()||!from.isBefore(to))read=CompletableFuture.completedFuture(null);
            else try{read=source.movement(from,to);}catch(RuntimeException error){read=HealthSync.failed(error);}
            result=read.handle((sample,error)->{
                synchronized(DailyHealthStore.LOCK){
                    if(!running.getAsBoolean()||revision!=store.revision()||!Reminders.channelAllowed(context,SpokenReminder.channel(context)))return null;
                    ZonedDateTime checked=clock.get();if(!now.toLocalDate().equals(checked.toLocalDate())||!store.eligible(checked,suppressed(context)))return null;
                    boolean reliable=error==null&&hasAccess.getAsBoolean()&&sample!=null&&sample.fresh(checked.toInstant())
                            &&sample.from()!=null&&!sample.from().isAfter(from)&&!sample.to().isBefore(to.minusSeconds(120));
                    Instant breakAt=store.breakStart(checked);
                    if(breakAt!=null&&reliable&&sample.steps()>=cfg.breakSteps()){
                        store.finishBreak();completed(context,sample.steps(),cfg.breakSteps());
                        store.status("Walking break complete: "+sample.steps()+" / "+cfg.breakSteps()+" steps.");return null;
                    }
                    if(breakAt==null&&reliable&&sample.steps()>=cfg.minimumSteps()){
                        store.status("Lookback step target reached: "+sample.steps()+" / "+cfg.minimumSteps()+" steps.");return null;
                    }
                    if(store.alertDue(checked)){
                        int number=store.nextReminderNumber(checked);if(breakAt==null)store.startBreak(checked);
                        deliver(context,cfg,number,!reliable,breakAt!=null);store.delivered(checked);
                        store.status(reliable?"Movement reminder #"+number+" sent. Walking break is active.":"Movement reminder #"+number+" sent; step data unavailable or delayed. Walking break is active.");
                    }
                }return null;
            });
        }catch(RuntimeException error){BUSY.set(false);return HealthSync.failed(error);}
        result.whenComplete((ignored,error)->BUSY.set(false));return result;
    }
    private static void completed(Context context,long steps,int target){
        Reminders.channels(context);
        NotificationChannel completion=new NotificationChannel("movement-complete","Walking break completion",NotificationManager.IMPORTANCE_LOW);
        completion.setSound(null,null);completion.enableVibration(false);context.getSystemService(NotificationManager.class).createNotificationChannel(completion);
        Notification notification=new Notification.Builder(context,"movement-complete")
                .setSmallIcon(R.drawable.ic_directions_walk).setContentTitle("Walking break complete")
                .setContentText(steps+" / "+target+" steps recorded.").setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(PendingIntent.getActivity(context,1052,new Intent(context,DailyHealthActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)).build();
        context.getSystemService(NotificationManager.class).cancel(NOTIFICATION_ID);
        context.getSystemService(NotificationManager.class).notify(COMPLETION_ID,notification);
    }
    static void deliver(Context context,DailyHealthRules.Settings cfg){deliver(context,cfg,new DailyHealthStore(context).nextReminderNumber(ZonedDateTime.now()),false,false);}
    static void deliver(Context context,DailyHealthRules.Settings cfg,int number){deliver(context,cfg,number,false,false);}
    static void deliver(Context context,DailyHealthRules.Settings cfg,int number,boolean unknown,boolean active){
        Reminders.channels(context);
        String text=unknown?"Step data unavailable or delayed. Take a "+cfg.breakSteps()+"-step walking break.":active?"Your walking break is still active. Aim for "+cfg.breakSteps()+" steps.":"Below your "+cfg.minimumSteps()+"-step target. Take a "+cfg.breakSteps()+"-step walking break.";
        var notification=new Notification.Builder(context,SpokenReminder.channel(context)).setSmallIcon(R.drawable.ic_directions_walk).setContentTitle("Movement reminder #"+number).setNumber(number)
                .setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context,1052,new Intent(context,DailyHealthActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT))
                .addAction(new Notification.Action.Builder(null,"Snooze "+cfg.snoozeMinutes()+" min",DailyHealthActions.pending(context,DailyHealthActions.SNOOZE,1054)).build()).build();
        context.getSystemService(NotificationManager.class).cancel(COMPLETION_ID);
        context.getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification);
    }
}
