package app.rwun;
import android.content.*;import android.os.*;import android.app.*;import org.json.*;
/** Local, bounded support state. No routes, sample values, account data or automatic uploads. */
final class RecordingDiagnostics {
 static void error(Context c,String code){try{c.getSharedPreferences("rwun-recording-health",0).edit().putString("error",code).putLong("at",System.currentTimeMillis()).commit();}catch(RuntimeException ignored){}}
 static String version(Context c){return AppVersion.name(c);}
 static String age(long then,long now){return then>0&&then<=now?(now-then)/1000+" s ago":"not available";}
 static String report(Context c){
  StringBuilder b=new StringBuilder("OpenRun "+version(c)+"\nAndroid API "+Build.VERSION.SDK_INT+" · "+Build.MANUFACTURER+" "+Build.MODEL);
  b.append("\nPhone step sensor: ").append(IndoorStepService.available(c)?"available":"unavailable");
  b.append("\nPhysical activity permission: ").append(IndoorStepService.permitted(c)?"granted":"not granted");
  b.append("\nIndoor service: ").append(IndoorStepService.health());
  try{RunStore store=new RunStore(c);JSONObject a=IndoorState.active(c);
   if(a!=null){b.append("\nMode: indoor ").append(a.optString("kind")).append(a.optBoolean("phoneSteps")?" · phone steps":" · time only").append(a.optBoolean("paused")?" · paused":" · recording");
    b.append("\nLast sensor sample: ").append(age(a.optLong("stepSampleNanos",a.optLong("stepEvent"))/1000000,SystemClock.elapsedRealtime()));
    b.append("\nIndoor heartbeat: ").append(age(a.optLong("heartbeatWall"),System.currentTimeMillis()));
    b.append("\nIndoor checkpoint: ").append(age(a.optLong("stepPersistWall"),System.currentTimeMillis()));
    b.append("\nStep estimate partial: ").append(a.optBoolean("partialSteps"));b.append("\nAudio coaching: ").append(store.audio()?a.optString("audioStatus","preparing or not running"):"off");
   }else b.append("\nIndoor session: none");
   b.append("\nGPS checkpoint: ").append(!store.checkpoint().isEmpty()?"available":"none");
   b.append("\n").append(TrackingService.health());
  }catch(RuntimeException e){b.append("\nSaved recording state: could not read");}
  boolean gps=false;try{gps=c.getSystemService(android.location.LocationManager.class).isProviderEnabled("gps");}catch(RuntimeException ignored){}
  b.append("\nDevice GPS: ").append(gps?"enabled":"disabled");
  b.append("\nPrecise location: ").append(c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED?"granted":"not granted");
  b.append("\nNotifications: ").append(Reminders.allowed(c)?"enabled":"disabled");
  var p=c.getSharedPreferences("rwun-recording-health",0);String code=p.getString("error","");
  if(!code.isEmpty())b.append("\nLast recording error: ").append(code).append(" · ").append(age(p.getLong("at",0),System.currentTimeMillis()));
  return b.toString();
 }
}
