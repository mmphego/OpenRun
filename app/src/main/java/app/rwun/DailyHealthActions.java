package app.rwun;
import android.app.*;
import android.content.*;
import java.time.*;

/** Private notification actions contain no recovery answers. */
public final class DailyHealthActions extends BroadcastReceiver {
    static final String MOVED="app.rwun.HEALTH_MOVED",SNOOZE="app.rwun.HEALTH_SNOOZE";
    static PendingIntent pending(Context context,String action,int id){return PendingIntent.getBroadcast(context,id,new Intent(context,DailyHealthActions.class).setAction(action),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    @Override public void onReceive(Context context,Intent intent){
        DailyHealthStore store=new DailyHealthStore(context);
        if(MOVED.equals(intent.getAction()))store.acknowledge(Instant.now());else if(SNOOZE.equals(intent.getAction()))store.snooze(Instant.now());else return;
        context.getSystemService(NotificationManager.class).cancel(DailyHealthJob.NOTIFICATION_ID);
    }
}
