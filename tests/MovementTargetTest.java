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
  var now=ZonedDateTime.parse("2026-11-09T12:00:00Z");long enabled=now.minusHours(3).toInstant().toEpochMilli();var cfg=target(300);
  expect(DailyHealthRules.MovementState.REMIND,DailyHealthRules.movement(cfg,now,sample(now,100),enabled,0,0,0,"",0,false));
  expect(DailyHealthRules.MovementState.MOVING,DailyHealthRules.movement(cfg,now,sample(now,300),enabled,0,0,0,"",0,false));
  long delivered=now.toInstant().toEpochMilli();var early=now.plusMinutes(59);
  expect(DailyHealthRules.MovementState.WAITING,DailyHealthRules.movement(cfg,early,sample(early,0),enabled,0,delivered,0,"",1,false));
  var next=now.plusHours(1);
  expect(DailyHealthRules.MovementState.REMIND,DailyHealthRules.movement(cfg,next,sample(next,0),enabled,0,delivered,0,"",1,false));
  expect(DailyHealthRules.MovementState.UNKNOWN,DailyHealthRules.movement(cfg,next,null,enabled,0,delivered,0,"",1,false));
  expect(DailyHealthRules.MovementState.WAITING,DailyHealthRules.movement(cfg,next,sample(next,0),enabled,0,delivered,0,"",8,false));
  expect(50,new DailyHealthRules.Settings(true,false,Set.of(1),480,1080,60,3).minimumSteps());
  for(int invalid:new int[]{0,-1,10001}){try{target(invalid);throw new AssertionError("Invalid target accepted: "+invalid);}catch(IllegalArgumentException expected){}}
  System.out.println("Movement goal and repeat interval boundaries passed");
 }
}
