package app.rwun;

import android.content.Context;
import android.content.pm.PackageManager;
import java.time.ZonedDateTime;

/** On-demand local diagnostics. No health values, record IDs or routes are logged. */
final class HealthReadDiagnostics {
    static String describe(Context context) {
        StringBuilder result = new StringBuilder("Device time: ").append(ZonedDateTime.now())
            .append("\nAndroid API: ").append(android.os.Build.VERSION.SDK_INT)
            .append("\nReading in OpenRun: ").append(HealthPreferences.enabled(context,false)?"enabled":"paused");
        try {
            var info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            result.append("\nOpenRun: ").append(info.versionName).append(" / ").append(info.versionCode);
        } catch(PackageManager.NameNotFoundException unavailable) {
            result.append("\nOpenRun version unavailable");
        }
        for(int group=0;group<HealthPreferences.READ_PERMISSIONS.length;group++) {
            result.append("\n").append(HealthPreferences.LABELS[group]).append(HealthPreferences.selected(context,group)?": selected":": not selected");
            for(String permission:HealthPreferences.READ_PERMISSIONS[group]) {
                boolean granted=context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;
                result.append("\n  ").append(permission.substring(permission.lastIndexOf('.')+1))
                    .append(granted?": granted":": denied");
            }
        }
        return result.append("\nExercise access is separate from steps. An empty exercise result is not a successful workout import.").toString();
    }
}
