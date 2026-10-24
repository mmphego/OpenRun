package app.rwun;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=33,application=android.app.Application.class)
public class HealthConnectLegacyTest {
 @Test public void oldAndroidDoesNotLoadUnavailableHealthClasses()throws Exception{var c=RuntimeEnvironment.getApplication();assertFalse(HealthAccess.available(c));assertFalse(HealthExport.granted(c));assertTrue(HealthAccess.status(c).contains("Android 14"));try{HealthSync.sync(c,new RunStore(c)).get();fail();}catch(java.util.concurrent.ExecutionException expected){assertTrue(expected.getCause() instanceof SecurityException);}}
}
