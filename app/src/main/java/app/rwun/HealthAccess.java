package app.rwun;
import android.content.Context;import android.content.pm.PackageManager;import android.os.Build;
/** Separate read/write consent; old Android versions never load platform health classes. */
final class HealthAccess {
 @androidx.annotation.ChecksSdkIntAtLeast(api=34)
 static boolean available(Context c){return Build.VERSION.SDK_INT>=34&&Api34.available(c);}
 static boolean granted(Context c,String... permissions){if(Build.VERSION.SDK_INT<34)return false;for(String p:permissions)if(c.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)return false;return true;}
 static boolean backgroundSupported(Context c){if(Build.VERSION.SDK_INT<34||!available(c)||(Build.VERSION.SDK_INT<35&&android.os.ext.SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)<13))return false;try{c.getPackageManager().getPermissionInfo(HealthJob.BACKGROUND,0);return true;}catch(PackageManager.NameNotFoundException unavailable){return false;}}
 static String status(Context c){if(Build.VERSION.SDK_INT<34)return "Requires Android 14 or newer in this version";if(!available(c))return "Health Connect unavailable · check Android system updates";return "Read: "+(HealthSync.granted(c)?"allowed":"not allowed")+" · Write: "+(HealthExport.granted(c)?"allowed":"not allowed");}
 @android.annotation.TargetApi(34) private static final class Api34 {static boolean available(Context c){try{return c.getSystemService(android.health.connect.HealthConnectManager.class)!=null;}catch(RuntimeException unavailable){return false;}}}
}
