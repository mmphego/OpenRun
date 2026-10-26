package app.rwun;

import android.content.Context;
import android.content.pm.PackageManager;

/** Read the installed APK identity; UI labels must not maintain their own version. */
final class AppVersion {
    private AppVersion() {}

    static String name(Context context) {
        try {
            String name = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            return name == null || name.isBlank() ? "version unavailable" : name;
        } catch (PackageManager.NameNotFoundException unavailable) {
            return "version unavailable";
        }
    }

    static String label(Context context) { return "OpenRun " + name(context); }
}
