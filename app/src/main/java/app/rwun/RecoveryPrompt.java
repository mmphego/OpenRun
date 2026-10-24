package app.rwun;
import android.app.*;
import android.content.Intent;
import java.time.Instant;

/** Optional nudge; skipping does not bypass existing recorder gates or change the plan. */
final class RecoveryPrompt {
    private AlertDialog dialog;
    void beforeStart(Activity activity,Runnable start){
        if(dialog!=null&&dialog.isShowing())return;
        boolean needed;
        try{
            DailyHealthStore store=new DailyHealthStore(activity);var checkin=store.checkin(Instant.now());
            needed=store.settings().checkin()&&(checkin==null||!checkin.recent(Instant.now()));
        }catch(RuntimeException unreadable){
            dialog=new AlertDialog.Builder(activity).setTitle("Recovery preferences need review")
                    .setMessage("Saved recovery information could not be read. It has been kept. You can review or delete this feature's data, or continue without a check-in. Existing training holds still apply.")
                    .setNegativeButton("Not now",null).setNeutralButton("Continue without check-in",(d,w)->start.run())
                    .setPositiveButton("Review feature data",(d,w)->activity.startActivity(new Intent(activity,DailyHealthActivity.class).putExtra("screen","privacy"))).create();
            dialog.setOnDismissListener(d->dialog=null);dialog.show();return;
        }
        if(!needed){start.run();return;}
        dialog=new AlertDialog.Builder(activity).setTitle("Check in before you move?")
                .setMessage("Review sleep, fatigue, soreness and last evening's alcohol before choosing your effort. Answers stay on this phone. Your workout has not started.")
                .setNegativeButton("Not now",null).setNeutralButton("Start without check-in",(d,w)->start.run())
                .setPositiveButton("Recovery check-in",(d,w)->activity.startActivity(new Intent(activity,DailyHealthActivity.class).putExtra("screen","checkin"))).create();
        dialog.setOnDismissListener(d->dialog=null);dialog.show();
    }
    void close(){if(dialog!=null)dialog.dismiss();}
}
