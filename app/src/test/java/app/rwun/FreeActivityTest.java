package app.rwun;

import org.junit.Test;
import static org.junit.Assert.*;

public class FreeActivityTest {
    @Test public void freeRunStartsOpenEndedAndDoesNotFinishAutomatically(){
        var definition=WorkoutFactory.free();
        assertEquals(1,definition.steps().size());
        var engine=new WorkoutEngine(definition);
        assertEquals("Free run",engine.step().title());
        assertEquals("Open distance",engine.remaining(0,0));
        engine.update(0,0,Double.NaN);
        engine.update(7200,10000,Double.NaN);
        assertFalse(engine.complete());
        assertEquals("Open distance",engine.remaining(7200,10000));
    }
    @Test public void freeRunStillRecordsPaceSplits(){
        var engine=new WorkoutEngine(WorkoutFactory.free());
        engine.update(0,0,Double.NaN);engine.update(300,1000,Double.NaN);engine.update(610,2000,Double.NaN);
        assertEquals(2,engine.paceSegments());assertEquals(300,engine.firstMainPace(),0);assertEquals(310,engine.lastMainPace(),0);
    }
}
