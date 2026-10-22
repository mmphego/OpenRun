package app.rwun;
import android.app.*;import android.appwidget.*;import android.content.*;import android.widget.RemoteViews;import java.time.LocalDate;
public final class WorkoutWidget extends AppWidgetProvider {
  public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)safeUpdate(c,m,id);}
  public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,android.os.Bundle options){safeUpdate(c,m,id);}
  private static void safeUpdate(Context c,AppWidgetManager m,int id){try{update(c,m,id);}catch(RuntimeException e){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.workout_widget);v.setTextViewText(R.id.widget_title,"Open OpenRun to review your data");v.setTextViewText(R.id.widget_detail,"Your stored data has not been overwritten.");v.setOnClickPendingIntent(R.id.widget_plan,open(c,"Profile",10));v.setOnClickPendingIntent(R.id.widget_run,open(c,"Run",11));m.updateAppWidget(id,v);}}
  public static void updateAll(Context c){try{AppWidgetManager m=AppWidgetManager.getInstance(c);for(int id:m.getAppWidgetIds(new ComponentName(c,WorkoutWidget.class)))update(c,m,id);}catch(RuntimeException ignored){/* A launcher failure must not interrupt training. */}}
  private static void update(Context c,AppWidgetManager m,int id){
    RunStore store=new RunStore(c);RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.workout_widget);String title="Make room for a run",detail="Create your plan in OpenRun";
    if(store.resting()){title="Training paused";detail="Review your recovery in Profile";}else if(store.config()!=null){title="No upcoming workout";detail="Open your plan or record a free run";for(var s:store.plannedSessions())if(!s.date().isBefore(LocalDate.now())&&!store.completed(store.planId()+":"+s.id())&&!store.skipped(store.planId()+":"+s.id())){title=s.title();detail=PlanEngine.distance(s.km())+" · "+s.date();break;}}
    views.setTextViewText(R.id.widget_title,title);views.setTextViewText(R.id.widget_detail,detail);views.setOnClickPendingIntent(R.id.widget_plan,open(c,"Plan",10));views.setOnClickPendingIntent(R.id.widget_run,open(c,"Run",11));m.updateAppWidget(id,views);
  }
  private static PendingIntent open(Context c,String page,int code){return PendingIntent.getActivity(c,code,new Intent(c,MainActivity.class).setAction("app.rwun.OPEN_"+page).putExtra("destination",page).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
}
