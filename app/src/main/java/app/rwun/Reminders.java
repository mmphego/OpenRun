package app.rwun;
import android.app.*;import android.content.*;import android.os.*;import java.time.*;import java.util.*;import org.json.*;
/** Inexact, opt-in local reminders. No movement inference and no background exercise start. */
public final class Reminders extends android.content.BroadcastReceiver {
 static final String MOVE="app.rwun.MOVE",WORKOUT="app.rwun.WORKOUT",SNOOZE="app.rwun.SNOOZE",SKIP="app.rwun.SKIP",TOGGLE="app.rwun.INDOOR_TOGGLE";
 static void channels(Context c){
  NotificationManager n=c.getSystemService(NotificationManager.class);
  NotificationChannel spoken=n.getNotificationChannel(SpokenReminder.channel(c));
  if(spoken!=null&&spoken.getSound()!=null&&(c.getPackageName()+".reminder-sound").equals(spoken.getSound().getAuthority())){
   c.grantUriPermission("android",spoken.getSound(),Intent.FLAG_GRANT_READ_URI_PERMISSION);
   c.grantUriPermission("com.android.systemui",spoken.getSound(),Intent.FLAG_GRANT_READ_URI_PERMISSION);
  }
  n.createNotificationChannel(new NotificationChannel("movement","Movement breaks",NotificationManager.IMPORTANCE_DEFAULT));
  n.createNotificationChannel(new NotificationChannel("workouts","Planned workouts",NotificationManager.IMPORTANCE_DEFAULT));
  n.createNotificationChannel(new NotificationChannel("indoor","Indoor session timer",NotificationManager.IMPORTANCE_LOW));
 }
 static PendingIntent alarm(Context c,String action,int code){return PendingIntent.getBroadcast(c,code,new Intent(c,Reminders.class).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 static void schedule(Context c){try{channels(c);AlarmManager manager=c.getSystemService(AlarmManager.class);manager.cancel(alarm(c,MOVE,1031));manager.cancel(alarm(c,WORKOUT,1032));JSONObject r=new RunStore(c).v103().optJSONObject("reminders");if(r==null)return;ReminderPlan p=V103Data.plan(r);ZonedDateTime now=ZonedDateTime.now();var next=new DailyHealthStore(c).settings().movement()?null:p.nextMovement(now,r.optString("skip"),r.optLong("snooze"));if(next!=null)manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),alarm(c,MOVE,1031));Set<LocalDate> dates=new HashSet<>();RunStore store=new RunStore(c);for(var s:store.plannedSessions())if(!store.skipped(store.planId()+":"+s.id())&&!store.completed(store.planId()+":"+s.id()))dates.add(s.date());var workout=p.nextWorkout(now,dates);if(workout!=null)manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,workout.toInstant().toEpochMilli(),alarm(c,WORKOUT,1032));}catch(Exception ignored){/* Settings UI reports permission/channel status. */}}
 public void onReceive(Context c,Intent intent){try{channels(c);String action=intent.getAction();RunStore store=new RunStore(c);
   if(TOGGLE.equals(action)){JSONObject a=IndoorState.active(c);if(a!=null&&a.optString("id").equals(intent.getStringExtra("session")))IndoorState.toggle(c);return;}
   if(SNOOZE.equals(action)||SKIP.equals(action)){synchronized(RunStore.LOCK){JSONObject d=store.v103(),r=d.optJSONObject("reminders");if(r!=null){if(SKIP.equals(action))r.put("skip",LocalDate.now().toString());else r.put("snooze",System.currentTimeMillis()+30*60*1000);store.v103(d);}}c.getSystemService(NotificationManager.class).cancel(1031);schedule(c);return;}
   if(MOVE.equals(action)||WORKOUT.equals(action))notifyDue(c,action);
   if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)){IndoorState.snapshot(c);sessionNotification(c);}
   DailyHealthJob.schedule(c);AiCoachJob.schedule(c);
   schedule(c);
  }catch(Exception ignored){schedule(c);}}
 static void notifyDue(Context c,String action)throws Exception{synchronized(RunStore.LOCK){RunStore store=new RunStore(c);JSONObject d=store.v103(),r=d.optJSONObject("reminders");if(r==null||store.resting()||!allowed(c))return;ZonedDateTime now=ZonedDateTime.now();boolean move=MOVE.equals(action);if(!channelAllowed(c,move?SpokenReminder.channel(c):"workouts"))return;ReminderPlan p=V103Data.plan(r);
  if(move){if(new DailyHealthStore(c).settings().movement()||!p.movementWindow(now)||r.optString("skip").equals(now.toLocalDate().toString())||r.optLong("snooze")>System.currentTimeMillis())return;int count=r.optString("lastDay").equals(now.toLocalDate().toString())?r.optInt("count"):0;if(count>=12)return;}
  else{if(!p.workouts()||r.optString("lastWorkout").equals(now.toLocalDate().toString()))return;boolean due=store.plannedSessions().stream().anyMatch(s->s.date().equals(now.toLocalDate())&&!store.completed(store.planId()+":"+s.id())&&!store.skipped(store.planId()+":"+s.id()));int minute=now.getHour()*60+now.getMinute();if(!due||minute<p.workoutMinute()||minute>p.workoutMinute()+120)return;}
  JSONObject pending=d.optJSONObject("activeIndoor");boolean indoors=pending!=null;
  if(pending==null&&!store.checkpoint().isEmpty())pending=new JSONObject(store.checkpoint());
  if(pending!=null){String identity=PendingSession.identity(pending);long nowMs=System.currentTimeMillis();
   if(!identity.equals(r.optString("pendingId"))){r.put("pendingId",identity).put("pendingSeenAt",nowMs);}
   boolean stale=PendingSession.needsReview(pending,nowMs,SystemClock.elapsedRealtime(),IndoorState.boot(c),r.optLong("pendingSeenAt"));
   String ticket=identity+":"+now.toLocalDate();
   if(!stale||ticket.equals(r.optString("lastPendingReminder"))){store.v103(d);return;}
   r.put("lastPendingReminder",ticket);markDelivered(r,move,now.toLocalDate().toString());store.v103(d);
   Notification review=new Notification.Builder(c,move?SpokenReminder.channel(c):"workouts").setSmallIcon(R.drawable.ic_directions_walk).setContentTitle("An unfinished session needs review")
    .setContentText("Resume, save or discard it to get back to your normal movement reminders. Your recording has been kept.")
    .setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open(c,indoors?"indoor":"Run",1037)).build();
   c.getSystemService(NotificationManager.class).notify(move?1031:1032,review);return;
  }
  r.remove("pendingId");r.remove("pendingSeenAt");r.remove("lastPendingReminder");
  markDelivered(r,move,now.toLocalDate().toString());store.v103(d);Notification.Builder b=new Notification.Builder(c,move?SpokenReminder.channel(c):"workouts").setSmallIcon(R.drawable.ic_directions_run).setContentTitle(move?"Make room for a movement break":"Your planned session is waiting").setContentText(move?"Could a short indoor walk or outdoor break fit now?":"Review today’s run and how you feel before starting.").setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open(c,move?"indoor":"Today",1033));
  if(move){b.addAction(new Notification.Action.Builder(null,"Walk indoors",open(c,"indoor",1034)).build()).addAction(new Notification.Action.Builder(null,"Snooze 30 min",alarm(c,SNOOZE,1035)).build()).addAction(new Notification.Action.Builder(null,"Skip today",alarm(c,SKIP,1036)).build());}c.getSystemService(NotificationManager.class).notify(move?1031:1032,b.build());}}
 static void markDelivered(JSONObject r,boolean move,String date)throws Exception{if(move){int count=r.optString("lastDay").equals(date)?r.optInt("count"):0;r.put("lastDay",date).put("count",count+1);}else r.put("lastWorkout",date);}
 static boolean allowed(Context c){return (Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)&&c.getSystemService(NotificationManager.class).areNotificationsEnabled();}
 /** App permission and channel settings are independent Android gates. */
 static boolean channelAllowed(Context c,String channel){
  if(channel.equals("movement"))channel=SpokenReminder.channel(c);
  if(!allowed(c))return false;
  NotificationChannel value=c.getSystemService(NotificationManager.class).getNotificationChannel(channel);
  if(value==null)return true;
  if(value.getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
  if(Build.VERSION.SDK_INT>=28&&value.getGroup()!=null){NotificationChannelGroup group=c.getSystemService(NotificationManager.class).getNotificationChannelGroup(value.getGroup());if(group!=null&&group.isBlocked())return false;}
  return true;
 }
 static String notificationStatus(Context c,String channel){
  if(!allowed(c))return "Notifications blocked for OpenRun. Allow notifications in Android Settings.";
  if(!channelAllowed(c,channel))return (channel.startsWith("movement")?"Movement breaks":"Planned workouts")+" channel blocked. Enable this channel in Android Settings.";
  return (channel.startsWith("movement")?"Movement breaks":"Planned workouts")+" notifications allowed. Sound and pop-up behaviour depend on Android settings and Do Not Disturb.";
 }
 static Intent channelSettings(Context c,String channel){if(channel.equals("movement"))channel=SpokenReminder.channel(c);return new Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,c.getPackageName()).putExtra(android.provider.Settings.EXTRA_CHANNEL_ID,channel);}
 static String testMovement(Context c){
  channels(c);
  if(!channelAllowed(c,SpokenReminder.channel(c)))return notificationStatus(c,SpokenReminder.channel(c));
  NotificationManager manager=c.getSystemService(NotificationManager.class);
  NotificationChannel active=manager.getNotificationChannel(SpokenReminder.channel(c));
  String testId="movement-test-v2-"+Integer.toHexString(java.util.Objects.hash(SpokenReminder.channel(c),active==null?null:active.getSound()));
  NotificationChannel test=new NotificationChannel(testId,"Movement reminder tests",NotificationManager.IMPORTANCE_HIGH);
  if(active!=null)test.setSound(active.getSound(),active.getAudioAttributes());
  manager.createNotificationChannel(test);
  var testPrefs=c.getSharedPreferences("reminder-test",Context.MODE_PRIVATE);String previousTest=testPrefs.getString("channel","");
  if(previousTest.startsWith("movement-test-v2-")&&!previousTest.equals(testId))manager.deleteNotificationChannel(previousTest);
  testPrefs.edit().putString("channel",testId).apply();
  if(!channelAllowed(c,testId))return "Test notification channel is blocked. Open Android notification settings.";
  c.getSystemService(NotificationManager.class).notify(1044,new Notification.Builder(c,testId).setSmallIcon(R.drawable.ic_directions_walk)
   .setContentTitle("OpenRun · test movement reminder").setContentText("Test notification only. This does not check Health Connect steps.")
   .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(open(c,"indoor",1044)).build());
  return "Test notification posted. Check the notification shade; Android may silence sound or pop-ups. Health Connect movement detection was not tested.";
 }
 static void showTest(Activity activity){
  String result;
  try{result=testMovement(activity);}catch(RuntimeException error){result="Test failed: "+UserErrors.message(error);}
  String active=SpokenReminder.channel(activity);
  NotificationChannel channel=activity.getSystemService(NotificationManager.class).getNotificationChannel(active);
  String audio=channel==null?"Channel not created":channel.getImportance()<NotificationManager.IMPORTANCE_DEFAULT||channel.getSound()==null?"This movement channel is silent. Enable Sound in Android notification settings.":"A sound is configured. Notification volume, Do Not Disturb and Android notification cooldown can still silence it.";
  android.media.AudioManager audioManager=activity.getSystemService(android.media.AudioManager.class);
  audio+="\nNotification volume: "+audioManager.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION)+" / "+audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_NOTIFICATION);
  int filter=activity.getSystemService(NotificationManager.class).getCurrentInterruptionFilter();
  audio+="\nDo Not Disturb: "+(filter==NotificationManager.INTERRUPTION_FILTER_ALL?"off":filter==NotificationManager.INTERRUPTION_FILTER_UNKNOWN?"unknown":"active / restricted");
  AlertDialog.Builder dialog=new AlertDialog.Builder(activity).setTitle("Reminder test result").setMessage(AppVersion.label(activity)+"\n\n"+result+"\n\n"+audio+"\n\nPosting a notification does not prove its sound played.")
   .setPositiveButton("Open notification settings",(d,w)->activity.startActivity(channelSettings(activity,"movement"))).setNegativeButton("Close",null);
  if(!active.equals("movement"))dialog.setNeutralButton("Preview spoken audio",(d,w)->SpokenReminder.preview(activity));
  dialog.show();
 }
 static PendingIntent open(Context c,String destination,int id){Intent i=destination.equals("indoor")?new Intent(c,IndoorActivity.class):new Intent(c,MainActivity.class).putExtra("destination",destination);return PendingIntent.getActivity(c,id,i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
 static PendingIntent finishReview(Context c,String id){Intent i=new Intent(c,IndoorActivity.class).putExtra("finishSession",id).setData(android.net.Uri.parse("rwun:indoor-review/"+id)).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);return PendingIntent.getActivity(c,1042,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
 static PendingIntent sessionToggle(Context c,String id){Intent i=new Intent(c,Reminders.class).setAction(TOGGLE).putExtra("session",id).setData(android.net.Uri.parse("rwun:indoor-toggle/"+id));return PendingIntent.getBroadcast(c,1041,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
 static Notification indoorNotification(Context c)throws Exception{channels(c);JSONObject a=IndoorState.active(c);if(a==null)throw new IllegalStateException("No indoor session");IndoorClock clock=V103Data.clock(a);long sec=clock.value(SystemClock.elapsedRealtime(),IndoorState.boot(c));boolean paused=clock.paused()||clock.boot()!=IndoorState.boot(c);Notification.Builder b=new Notification.Builder(c,"indoor").setSmallIcon(R.drawable.ic_directions_run).setContentTitle("OpenRun · Indoor "+a.getString("kind").toLowerCase(Locale.ROOT)+(paused?" paused":" timer")).setContentText(paused?"Paused · tap to resume or finish":(a.optBoolean("phoneSteps")?(!IndoorState.trackingWarning(c,a).isEmpty()?IndoorState.trackingWarning(c,a):a.optLong("phoneStepCount")+" steps · estimated distance · phone in pocket"):"Time recorded · enter machine distance at finish")).setWhen(System.currentTimeMillis()-sec*1000).setShowWhen(true).setUsesChronometer(!paused).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open(c,"indoor",1040)).addAction(new Notification.Action.Builder(null,paused?"Resume":"Pause",sessionToggle(c,a.getString("id"))).build()).addAction(new Notification.Action.Builder(null,"Finish",finishReview(c,a.getString("id"))).build());return b.build();}
 static void sessionNotification(Context c){try{channels(c);NotificationManager n=c.getSystemService(NotificationManager.class);if(IndoorState.active(c)==null)n.cancel(1030);else if(allowed(c))n.notify(1030,indoorNotification(c));}catch(Exception ignored){}}
}
