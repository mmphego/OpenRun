package app.rwun;
import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class DailyHealthRulesTest {
    final ZonedDateTime now=ZonedDateTime.of(2026,10,26,12,0,0,0,ZoneId.of("Africa/Johannesburg"));
    final DailyHealthRules.Settings cfg=new DailyHealthRules.Settings(true,true,Set.of(1,2,3,4,5),540,1020,120,3);
    DailyHealthRules.Movement sample(Long steps,int age){return new DailyHealthRules.Movement(steps,now.toInstant().minusSeconds(age*60L),now.minusHours(2).toInstant(),now.toInstant());}
    DailyHealthRules.MovementState evaluate(DailyHealthRules.Movement sample){return DailyHealthRules.movement(cfg,now,sample,now.minusDays(1).toInstant().toEpochMilli(),0,0,0,"",0,false);}
    @Test public void minimumSettingsAreEnforced(){
        assertThrows(IllegalArgumentException.class,()->new DailyHealthRules.Settings(true,true,Set.of(1),540,1020,59,3));
        assertThrows(IllegalArgumentException.class,()->new DailyHealthRules.Settings(true,true,Set.of(1),540,1020,60,2));
        assertThrows(IllegalArgumentException.class,()->new DailyHealthRules.Settings(true,true,Set.of(),540,1020,60,3));
        assertThrows(IllegalArgumentException.class,()->new DailyHealthRules.Settings(true,true,Set.of(1),1020,540,60,3));
        new DailyHealthRules.Settings(true,true,Set.of(1),0,1440,60,3);
    }
    @Test public void missingStaleFutureAndPartialWindowsNeverBecomeZeroSteps(){
        assertEquals(DailyHealthRules.MovementState.UNKNOWN,evaluate(null));
        assertEquals(DailyHealthRules.MovementState.UNKNOWN,evaluate(sample(null,0)));
        assertEquals(DailyHealthRules.MovementState.UNKNOWN,evaluate(sample(0L,21)));
        assertEquals(DailyHealthRules.MovementState.UNKNOWN,evaluate(sample(0L,-1)));
        assertEquals(DailyHealthRules.MovementState.UNKNOWN,evaluate(new DailyHealthRules.Movement(0L,now.toInstant(),now.minusHours(1).toInstant(),now.toInstant())));
        assertEquals(DailyHealthRules.MovementState.UNKNOWN,evaluate(new DailyHealthRules.Movement(0L,now.toInstant(),now.minusHours(2).toInstant(),now.plusMinutes(1).toInstant())));
    }
    @Test public void knownLowMovementDiffersFromRecentMovement(){
        assertEquals(DailyHealthRules.MovementState.REMIND,evaluate(sample(0L,1)));assertEquals(DailyHealthRules.MovementState.REMIND,evaluate(sample(49L,1)));
        assertEquals(DailyHealthRules.MovementState.MOVING,evaluate(sample(50L,1)));assertEquals(DailyHealthRules.MovementState.MOVING,evaluate(sample(400L,1)));
    }
    @Test public void quietHoursAcknowledgementsAndCooldownsAreRespected(){
        long old=now.minusDays(1).toInstant().toEpochMilli();var sample=sample(0L,1);
        assertEquals(DailyHealthRules.MovementState.WAITING,DailyHealthRules.movement(cfg,now,sample,now.minusMinutes(59).toInstant().toEpochMilli(),0,0,0,"",0,false));
        assertEquals(DailyHealthRules.MovementState.WAITING,DailyHealthRules.movement(cfg,now,sample,old,now.minusMinutes(30).toInstant().toEpochMilli(),0,0,"",0,false));
        assertEquals(DailyHealthRules.MovementState.WAITING,DailyHealthRules.movement(cfg,now,sample,old,0,now.minusMinutes(30).toInstant().toEpochMilli(),0,"",0,false));
        assertEquals(DailyHealthRules.MovementState.WAITING,DailyHealthRules.movement(cfg,now,sample,old,0,0,now.plusMinutes(30).toInstant().toEpochMilli(),"",0,false));
        assertEquals(DailyHealthRules.MovementState.WAITING,DailyHealthRules.movement(cfg,now,sample,old,0,0,0,"",8,false));
        assertEquals(DailyHealthRules.MovementState.WAITING,DailyHealthRules.movement(cfg,now,sample,old,0,0,0,"",0,true));
        assertEquals(DailyHealthRules.MovementState.QUIET,DailyHealthRules.movement(cfg,now,sample,old,0,0,0,now.toLocalDate().toString(),0,false));
        assertEquals(DailyHealthRules.MovementState.QUIET,DailyHealthRules.movement(cfg,now.withHour(18),sample,old,0,0,0,"",0,false));
        assertEquals(DailyHealthRules.MovementState.QUIET,DailyHealthRules.movement(cfg,now.minusDays(1),sample,old,0,0,0,"",0,false));
        assertEquals(DailyHealthRules.MovementState.WAITING,DailyHealthRules.movement(cfg,now.withHour(10),sample,old,0,0,0,"",0,false));
    }
    @Test public void suggestionsExplainAnswersWithoutClearingHolds(){
        var unknown=new DailyHealthRules.Checkin(now.toInstant(),null,0,0,DailyHealthRules.Answer.UNKNOWN,DailyHealthRules.Answer.UNKNOWN,false);
        assertEquals("Recovery information is incomplete",DailyHealthRules.advice(unknown,false).title());
        var alcohol=new DailyHealthRules.Checkin(now.toInstant(),5.0,4,1,DailyHealthRules.Answer.YES,DailyHealthRules.Answer.NO,false);
        var advice=DailyHealthRules.advice(alcohol,false);assertEquals("Consider an easier day",advice.title());assertEquals(3,advice.reasons().size());assertFalse(advice.reviewHold());
        assertTrue(DailyHealthRules.advice(alcohol,true).reviewHold());assertTrue(DailyHealthRules.advice(unknown,true).reasons().get(0).contains("hold remains"));
        var pain=new DailyHealthRules.Checkin(now.toInstant(),8.0,1,1,DailyHealthRules.Answer.NO,DailyHealthRules.Answer.NO,true);assertTrue(DailyHealthRules.advice(pain,false).reviewHold());
        assertFalse(unknown.recent(now.plusHours(7).toInstant()));assertFalse(unknown.recent(now.minusMinutes(1).toInstant()));
    }
}
