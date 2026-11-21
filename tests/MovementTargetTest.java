package app.rwun;
import java.time.*;import java.util.*;
/** Deterministic boundary tests for configurable movement goals and repeat timing. */
public final class MovementTargetTest {
 static DailyHealthRules.Settings target(int steps)throws Exception {
  return new DailyHealthRules.Settings(true,false,Set.of(1),480,1080,60,3,steps);
 }
 static void expect(Object expected,Object actual){if(!expected.equals(actual))throw new AssertionError("Expected "+expected+", got "+actual);}
 static DailyHealthRules.Movement sample(ZonedDateTime now,long steps){return new DailyHealthRules.Movement(steps,now.toInstant(),now.minusHours(1).toInstant(),now.toInstant());}
 public static void main(String[] args)throws Exception{
  var now=ZonedDateTime.parse("2026-11-09T12:00:00Z");var cfg=target(300);
  expect(300,cfg.minimumSteps());expect(50,cfg.breakSteps());expect(10,cfg.repeatMinutes());
  for(int invalid:new int[]{0,-1,10001}){try{target(invalid);throw new AssertionError("Invalid target accepted: "+invalid);}catch(IllegalArgumentException expected){}}
  var context=new android.content.Context(java.nio.file.Files.createTempDirectory("movement-store-test").toFile());
  var store=new DailyHealthStore(context);store.settings(cfg,now.minusHours(3).toInstant());store.startBreak(now);store.delivered(now);store.snooze(now.toInstant());
  expect(false,store.alertDue(now.plusMinutes(14)));expect(true,store.alertDue(now.plusMinutes(15)));
  expect(now.toInstant(),store.breakStart(now.plusMinutes(1)));
  if(store.breakStart(now.plusDays(1))!=null)throw new AssertionError("Break carried over to the next day");
  System.out.println("Movement goal and repeat interval boundaries passed");
 }
}
