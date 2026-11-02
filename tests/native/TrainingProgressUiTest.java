package app.rwun;

import android.app.*;
import android.content.*;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,manifest=Config.NONE)
public class TrainingProgressUiTest {
  @Before public void clear(){
    RuntimeEnvironment.getApplication().getSharedPreferences("rwun",0).edit().clear().commit();
  }
  @Test public void noPlanHasHonestEmptySnapshot(){
    TrainingProgress.Snapshot s=TrainingProgress.from(new RunStore(RuntimeEnvironment.getApplication()),LocalDate.now());
    assertEquals("No active plan",s.phase());
    assertEquals(0,s.completedWorkouts());
  }
  @Test public void plannedProgressNeverCountsUnrelatedRuns(){
    RunStore store=new RunStore(RuntimeEnvironment.getApplication());
    store.save(new PlanEngine.Config(8,3,12,10,LocalDate.now()));
    TrainingProgress.Snapshot s=TrainingProgress.from(store,LocalDate.now());
    assertTrue(s.plannedKm()>=0);
    assertEquals(0,s.completedWorkouts());
    assertTrue(s.recommendation().contains("km"));
    assertTrue(s.latestChange()!=null);
  }
  @Test public void progressScreenRenders(){
    RunStore store=new RunStore(RuntimeEnvironment.getApplication());
    store.save(new PlanEngine.Config(8,3,12,10,LocalDate.now()));
    try(var controller=Robolectric.buildActivity(TrainingActivity.class,new Intent().putExtra("screen","progress")).setup().visible()){
      String rendered=ProgressUiTest.all(controller.get().getWindow().getDecorView());
      assertTrue(rendered.contains("Progress toward your goal"));
      assertTrue(rendered.contains("PLANNED VS ACTUAL"));
      assertTrue(rendered.contains("RECOVERY CONTEXT"));
      assertTrue(rendered.contains("LATEST APPLIED CHANGE"));
    }
  }
}
