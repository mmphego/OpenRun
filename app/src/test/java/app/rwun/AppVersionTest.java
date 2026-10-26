package app.rwun;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Installed-manifest and native UI checks; not evidence from a physical phone. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, application=Application.class, qualifiers="w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class AppVersionTest {
    @Test public void builtManifestDistinguishesThisDebugBuild() throws Exception {
        var app=RuntimeEnvironment.getApplication();
        var info=app.getPackageManager().getPackageInfo(app.getPackageName(),0);
        assertTrue(info.versionName.endsWith("-build."+info.versionCode+"-debug"));
        assertEquals(info.versionName, AppVersion.name(app));
    }

    @Test public void profileHealthAndDiagnosticsUseInstalledVersionAtLargeFont() throws Exception {
        var app=RuntimeEnvironment.getApplication();
        var info=app.getPackageManager().getPackageInfo(app.getPackageName(),0);
        info.versionName="9.8.7-build.123456";info.versionCode=123456;
        Shadows.shadowOf(app.getPackageManager()).installPackage(info);
        new RunStore(app).save(new PlanEngine.Config(8,3,12,5,java.time.LocalDate.now()));
        TrackingService tracking=Robolectric.buildService(TrackingService.class).create().get();
        Shadows.shadowOf(app).setComponentNameAndServiceForBindService(new ComponentName(app,TrackingService.class),tracking.onBind(new Intent()));
        RuntimeEnvironment.setFontScale(1.6f);
        try {
            try(var controller=Robolectric.buildActivity(MainActivity.class,new Intent().putExtra("destination","Profile")).setup().visible()) {
                var root=controller.get().getWindow().getDecorView();
                assertNotNull(NativePlanningTest.find(root,"OpenRun 9.8.7-build.123456"));
                assertNull(NativePlanningTest.find(root,"OpenRun 1.7.0 · Run around your life"));
                NativePlanningTest.shot(controller.get(),"profile-installed-version-large-font");
            }
            try(var controller=Robolectric.buildActivity(HealthConnectUiTest.AvailableHealthActivity.class).setup().visible()) {
                assertNotNull(NativePlanningTest.find(controller.get().getWindow().getDecorView(),"OpenRun 9.8.7-build.123456"));
                NativePlanningTest.shot(controller.get(),"health-installed-version-large-font");
            }
            assertTrue(HealthReadDiagnostics.describe(app).contains("9.8.7-build.123456 / 123456"));
            assertEquals("9.8.7-build.123456",RecordingDiagnostics.version(app));
        } finally { RuntimeEnvironment.setFontScale(1f); }
    }

    @Test public void missingVersionIsExplicitInsteadOfInventingARelease() throws Exception {
        var app=RuntimeEnvironment.getApplication();
        var info=app.getPackageManager().getPackageInfo(app.getPackageName(),0);
        info.versionName=null;
        Shadows.shadowOf(app.getPackageManager()).installPackage(info);
        assertEquals("OpenRun version unavailable",AppVersion.label(app));
    }
}
