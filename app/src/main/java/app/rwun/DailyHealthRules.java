package app.rwun;

import java.time.*;
import java.util.*;

/** Pure, explainable wellness heuristics. Never diagnoses or edits training. */
final class DailyHealthRules {
    record Settings(boolean movement, boolean checkin, Set<Integer> days, int startMinute,
                    int endMinute, int inactivityMinutes, int breakMinutes, int minimumSteps,
                    int breakSteps, int checkMinutes, int repeatMinutes, int snoozeMinutes) {
        Settings(boolean movement,boolean checkin,Set<Integer> days,int startMinute,int endMinute,int inactivityMinutes,int breakMinutes,int minimumSteps){this(movement,checkin,days,startMinute,endMinute,inactivityMinutes,breakMinutes,minimumSteps,50,10,10,15);}
        Settings(boolean movement,boolean checkin,Set<Integer> days,int startMinute,int endMinute,int inactivityMinutes,int breakMinutes){this(movement,checkin,days,startMinute,endMinute,inactivityMinutes,breakMinutes,50);}
        Settings {
            if(days==null || days.isEmpty() || days.stream().anyMatch(d->d<1||d>7)
                    || startMinute<0 || endMinute>1440 || startMinute>=endMinute
                    || minimumSteps<1 || minimumSteps>10000 || breakSteps<1 || breakSteps>10000
                    || checkMinutes<1 || checkMinutes>1440 || repeatMinutes<1 || repeatMinutes>1440 || snoozeMinutes<1 || snoozeMinutes>1440
                    || inactivityMinutes<60 || inactivityMinutes>720 || breakMinutes<3 || breakMinutes>60)
                throw new IllegalArgumentException("Choose working days and hours, a 60–720 minute lookback, 1–10,000 step targets, and intervals from 1 to 1,440 minutes.");
            days=Set.copyOf(days);
        }
        boolean inWindow(ZonedDateTime now) {
            int minute=now.getHour()*60+now.getMinute();
            return days.contains(now.getDayOfWeek().getValue())&&minute>=startMinute&&minute<endMinute;
        }
        Instant windowStart(ZonedDateTime now) {
            return now.toLocalDate().atTime(startMinute/60,startMinute%60).atZone(now.getZone()).toInstant();
        }
    }
    static Settings defaults(){return new Settings(false,false,Set.of(1,2,3,4,5),540,1020,60,3);}
    /** Null totals are unknown, never zero. Fresh short records are required independently. */
    record Movement(Long steps, Instant observedThrough, Instant from, Instant to) {
        boolean fresh(Instant now) {
            return steps!=null&&steps>=0&&observedThrough!=null&&from!=null&&to!=null
                    &&!to.isAfter(now)&&!from.isAfter(to)&&!observedThrough.isAfter(now)
                    &&!observedThrough.isBefore(now.minus(Duration.ofMinutes(20)));
        }
    }
    enum Answer { UNKNOWN, NO, YES }
    record Checkin(Instant at,Double sleepHours,int fatigue,int soreness,Answer alcohol,Answer illness,boolean pain) {
        Checkin {
            if(at==null||sleepHours!=null&&(!Double.isFinite(sleepHours)||sleepHours<0||sleepHours>16)
                    ||fatigue<0||fatigue>5||soreness<0||soreness>5||alcohol==null||illness==null)
                throw new IllegalArgumentException("Enter sleep between 0 and 16 hours, or leave it unknown.");
        }
        boolean recent(Instant now){return !at.isAfter(now)&&at.isAfter(now.minus(Duration.ofHours(6)));}
    }
    record Advice(String title,List<String> reasons,String suggestion,boolean reviewHold){}
    static Advice advice(Checkin value,boolean held) {
        List<String> reasons=new ArrayList<>();
        if(held)reasons.add("Your existing training hold remains active.");
        if(value.pain())reasons.add("You reported pain or injury symptoms.");
        if(value.illness()==Answer.YES)reasons.add("You reported feeling unwell.");
        if(value.alcohol()==Answer.YES)reasons.add("You reported alcohol last evening; it may affect sleep and recovery.");
        if(value.sleepHours()!=null&&value.sleepHours()<6)reasons.add("You reported less than six hours of sleep.");
        if(value.fatigue()>=4)reasons.add("You reported high fatigue.");
        if(value.soreness()>=4)reasons.add("You reported high muscle soreness.");
        if(held||value.pain()||value.illness()==Answer.YES)return new Advice("Review recovery before training",List.copyOf(reasons),
                "Consider resting. The app cannot assess illness or injury. Review persistent or concerning symptoms with a qualified professional.",true);
        if(!reasons.isEmpty())return new Advice("Consider an easier day",List.copyOf(reasons),
                "Consider a comfortable walk, a shorter easy session or rest. Review your schedule before making a change.",false);
        boolean unknown=value.sleepHours()==null||value.fatigue()==0||value.soreness()==0||value.alcohol()==Answer.UNKNOWN||value.illness()==Answer.UNKNOWN;
        return new Advice(unknown?"Recovery information is incomplete":"No recovery concern reported",
                List.of(unknown?"Some answers were left unknown.":"Your answers did not trigger a recovery suggestion."),
                "This is not clearance to train. Start comfortably and adjust to how you feel. Your plan stays unchanged.",false);
    }
}
