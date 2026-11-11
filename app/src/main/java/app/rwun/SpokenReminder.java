package app.rwun;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.*;
import android.speech.tts.*;
import android.widget.*;
import java.io.File;
import java.util.*;

/** Pre-render speech while the user configures it; Android plays it as a notification sound. */
final class SpokenReminder {
 private static final java.util.concurrent.atomic.AtomicBoolean BUSY=new java.util.concurrent.atomic.AtomicBoolean();
 static final String CHANNEL="movement-spoken";
 static String channel(Context c){return c.getSharedPreferences("spoken-reminder",0).getBoolean("enabled",false)&&new File(c.getFilesDir(),"movement-reminder.wav").isFile()?CHANNEL:"movement";}
 static void configure(Activity a){
  var prefs=a.getSharedPreferences("spoken-reminder",0);EditText phrase=new EditText(a);phrase.setText(prefs.getString("phrase","Get up and stretch a little"));phrase.setMaxLines(3);
  new AlertDialog.Builder(a).setTitle("Spoken movement reminder").setMessage("Uses an installed offline voice. Voice quality depends on your TTS engine. Android controls volume, Do Not Disturb and notification sound. Changing an existing spoken phrase requires disabling and re-enabling sound in this channel's Android settings on some devices.")
   .setView(phrase).setNegativeButton("Cancel",null).setNeutralButton("Use standard sound",(d,w)->{prefs.edit().putBoolean("enabled",false).apply();DailyHealthJob.schedule(a);Reminders.schedule(a);})
   .setPositiveButton("Create spoken sound",(d,w)->{String value=phrase.getText().toString().trim();if(value.isEmpty()||value.length()>200){Toast.makeText(a,"Enter 1–200 characters",Toast.LENGTH_LONG).show();return;}render(a,value);}).show();
 }
 private static void render(Activity a,String phrase){
  if(!BUSY.compareAndSet(false,true)){Toast.makeText(a,"A speech sound is already being created",Toast.LENGTH_LONG).show();return;}
  Context c=a.getApplicationContext();Handler main=new Handler(Looper.getMainLooper());TextToSpeech[] engine={null};File pending=new File(c.getFilesDir(),"movement-reminder-pending.wav");
  java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();
  Runnable fail=()->{if(!finished.compareAndSet(false,true))return;BUSY.set(false);if(engine[0]!=null)engine[0].shutdown();pending.delete();Toast.makeText(c,"Speech sound could not be created. Install an offline voice in Android TTS settings. Standard reminders remain available.",Toast.LENGTH_LONG).show();};
  main.postDelayed(fail,30000);
  engine[0]=new TextToSpeech(c,result->main.post(()->{
   if(finished.get()){if(engine[0]!=null)engine[0].shutdown();return;}
   if(result!=TextToSpeech.SUCCESS){fail.run();return;}
   var voices=engine[0].getVoices();Voice voice=voices==null?null:voices.stream().filter(v->!v.isNetworkConnectionRequired()&&v.getLocale().getLanguage().equals(Locale.getDefault().getLanguage())).max(Comparator.comparingInt(Voice::getQuality)).orElse(null);
   if(voice==null||engine[0].setVoice(voice)!=TextToSpeech.SUCCESS){fail.run();return;}
   engine[0].setOnUtteranceProgressListener(new UtteranceProgressListener(){
    public void onStart(String id){}
    public void onError(String id){main.post(fail);}
    public void onDone(String id){main.post(()->{if(finished.get())return;engine[0].shutdown();File target=new File(c.getFilesDir(),"movement-reminder.wav");if(!pending.isFile()||pending.length()<44||!pending.renameTo(target)){fail.run();return;}
     Uri uri=Uri.parse("content://"+c.getPackageName()+".reminder-sound/movement.wav");
     c.grantUriPermission("android",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);c.grantUriPermission("com.android.systemui",uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
     NotificationChannel channel=new NotificationChannel(CHANNEL,"Spoken movement reminders",NotificationManager.IMPORTANCE_DEFAULT);channel.setSound(uri,new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
     c.getSharedPreferences("spoken-reminder",0).edit().putString("phrase",phrase).putBoolean("enabled",true).apply();finished.set(true);BUSY.set(false);DailyHealthJob.schedule(c);Reminders.schedule(c);Toast.makeText(c,"Spoken reminder saved. Send a test reminder to check the voice and channel sound.",Toast.LENGTH_LONG).show();
    });}
   });
   if(engine[0].synthesizeToFile(phrase,new Bundle(),pending,"movement")!=TextToSpeech.SUCCESS)fail.run();
  }));
 }
}
