package app.rwun;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class RunStoreCheckpointTest {
  private RunStore store;

  @Before
  public void setUp() {
    Application app = RuntimeEnvironment.getApplication();
    app.getSharedPreferences("rwun", 0).edit().clear().commit();
    store = new RunStore(app);
  }

  @Test
  public void discardingMalformedCheckpointUnblocksNewRecording() {
    store.checkpoint("not-json");

    assertEquals("", store.discardCheckpoint());
    assertTrue(store.checkpoint().isEmpty());
  }

  @Test
  public void refocusDoesNotBlockStartupWhenCheckpointIsMalformed() {
    store.checkpoint("not-json");

    store.refocus(RuntimeEnvironment.getApplication());

    assertEquals("not-json", store.checkpoint());
  }

  @Test
  public void discardingValidCheckpointReturnsOnlySafeRouteName() {
    store.checkpoint("{\"route\":\"route-safe.jsonl\"}");

    assertEquals("route-safe.jsonl", store.discardCheckpoint());
    assertTrue(store.checkpoint().isEmpty());
  }
}
