package app.rwun;
import android.app.*;import android.content.*;
import org.junit.*;import org.junit.runner.RunWith;
import org.robolectric.*;import org.robolectric.annotation.*;
import static org.junit.Assert.*;
/** Native Android host UI evidence; does not represent an actual Health Connect service. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class,qualifiers="w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class HealthConnectUiTest {
 @Test public void readAndWriteControlsAreVisibleWithoutConnectingAnAccount()throws Exception{
  try(var c=Robolectric.buildActivity(HealthConnectActivity.class).setup().visible()){
   assertNotNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Read activities"));assertNotNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Write activities"));assertNotNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Choose activity to write"));NativePlanningTest.shot(c.get(),"23-health-connect-read-write");
  }
 }
 @Test public void grantingWriteAccessDoesNotAutomaticallyExport()throws Exception{
  try(var c=Robolectric.buildActivity(HealthConnectActivity.class).setup().visible()){
   NativePlanningTest.click(c.get(),"Allow writing activities");AlertDialog dialog=(AlertDialog)org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(dialog);dialog.getButton(-1).performClick();assertFalse(HealthExport.busy());
  }
 }
 @Test @Config(sdk=33) public void unsupportedAndroidExplainsAvailabilityWithoutWriteButton()throws Exception{
  try(var c=Robolectric.buildActivity(HealthConnectActivity.class).setup().visible()){
   assertNotNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Requires Android 14 or newer in this version"));assertNull(NativePlanningTest.find(c.get().getWindow().getDecorView(),"Choose activity to write"));
  }
 }
}
