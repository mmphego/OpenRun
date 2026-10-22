package app.rwun;
import android.app.*;import android.os.*;
/** Process foreground sessions, debounced across Activity navigation and rotation. No database/network work on the UI thread. */
public final class RwunApplication extends Application implements Application.ActivityLifecycleCallbacks {
 private final Handler main=new Handler(Looper.getMainLooper());private int started;private boolean foreground;
 private final Runnable background=()->foreground=false;
 public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(this);}
 public void onActivityStarted(Activity activity){started++;main.removeCallbacks(background);if(!foreground){foreground=true;RaceSyncJob.schedule(this);}}
 public void onActivityStopped(Activity activity){started=Math.max(0,started-1);if(started==0)main.postDelayed(background,1000);}
 public void onActivityCreated(Activity a,Bundle b){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
}
