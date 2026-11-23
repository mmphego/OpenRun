package app.rwun;

import static org.junit.Assert.*;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class RunComparisonTest {
  private RunStore.Run run(String id, String date, double km, long seconds, String type, String source) {
    return new RunStore.Run(id,date,km,30,5,2,false,source,"",0,seconds,type,0,0,0,0);
  }
  private RunStore.Run run(String id, String date, double km, long seconds) {
    return run(id,date,km,seconds,"Easy","GPS");
  }
  @Test public void findsMostRecentSimilarRunDespiteOtherWorkouts() {
    var old=run("a","2026-01-01",5,1800);
    var interval=run("b","2026-01-02",5,1500,"Intervals","GPS");
    var longRun=run("c","2026-01-03",10,4000);
    var now=run("d","2026-01-04",5,1710);
    var result=RunComparison.compare(List.of(now,longRun,old,interval),r->0).get("d");
    assertEquals(RunComparison.Band.FASTER,result.band());
    assertEquals(-5.0,result.percent(),0.00001);
    assertEquals("2026-01-01",result.previousDate());
  }
  @Test public void classifiesPaceAndThresholds() {
    var base=run("a","2026-01-01",5,2000);
    assertEquals(RunComparison.Band.SAME,RunComparison.compare(List.of(base,run("b","2026-01-02",5,2005)),r->0).get("b").band());
    assertEquals(RunComparison.Band.SLOWER,RunComparison.compare(List.of(base,run("b","2026-01-02",5,2100)),r->0).get("b").band());
    assertEquals(RunComparison.Band.MUCH_SLOWER,RunComparison.compare(List.of(base,run("b","2026-01-02",5,2300)),r->0).get("b").band());
  }
  @Test public void unknownMetricsAndVenueDoNotInventComparisons() {
    var first=run("a","2026-01-01",5,1800);
    var zero=run("b","2026-01-02",0,1000);
    var unknown=run("c","2026-01-03",5,1800,"Easy","Health Connect · source");
    var indoor=run("d","2026-01-04",5,1800,"Easy","Indoor · Machine entered");
    assertTrue(RunComparison.compare(List.of(first,zero,unknown,indoor),r->0).isEmpty());
  }
  @Test public void sameDayNeedsKnownDistinctTimesAndAmbiguityBlocksOlderMatch() {
    var old=run("a","2026-01-01",5,1800);
    var earlier=run("b","2026-01-02",5,1700);
    var later=run("c","2026-01-02",5,1600);
    var values=List.of(old,earlier,later);
    assertFalse(RunComparison.compare(values,r->0).containsKey("c"));
    var times=Map.of("b",100L,"c",200L);
    var result=RunComparison.compare(values,r->times.getOrDefault(r.sessionId(),0L)).get("c");
    assertEquals("2026-01-02",result.previousDate());
    assertEquals(RunComparison.Band.FASTER,result.band());
  }
  @Test public void newerSimilarRunSupersedesAmbiguousOlderDay() {
    var old1=run("a","2026-01-01",5,1800);
    var old2=run("b","2026-01-01",5,1700);
    var previous=run("c","2026-01-02",5,1600);
    var current=run("d","2026-01-03",5,1500);
    assertEquals("2026-01-02",RunComparison.compare(List.of(old1,old2,previous,current),r->0).get("d").previousDate());
  }

  @Test public void unknownSameDayOrderCannotBeClearedByAnotherKnownRun() {
    var old=run("a","2026-01-01",5,1800);
    var unknown=run("b","2026-01-02",5,1700);
    var known=run("c","2026-01-02",5,1600);
    var current=run("d","2026-01-02",5,1500);
    var times=Map.of("c",100L,"d",200L);
    assertFalse(RunComparison.compare(List.of(old,unknown,known,current),r->times.getOrDefault(r.sessionId(),0L)).containsKey("d"));
  }

  @Test public void distanceBoundaryAndIndoorHistoryStaySeparate() {
    var outdoor=run("a","2026-01-01",5,1800);
    var boundary=run("b","2026-01-02",6,2100);
    var beyond=run("c","2026-01-03",7.21,2400);
    var indoorOld=run("d","2026-01-01",5,2000,"Easy","Indoor · Machine entered");
    var indoorNew=run("e","2026-01-04",5,1800,"Easy","Indoor · Machine entered");
    var results=RunComparison.compare(List.of(outdoor,boundary,beyond,indoorOld,indoorNew),r->0);
    assertEquals("2026-01-01",results.get("b").previousDate());
    assertFalse(results.containsKey("c"));
    assertEquals("2026-01-01",results.get("e").previousDate());
  }

}
