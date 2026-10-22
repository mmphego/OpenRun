package app.rwun;
import java.time.*;import java.util.*;
/** Approximate, user-selected local reminder slots; not an inactivity detector. */
public record ReminderPlan(boolean movement,boolean workouts,Set<Integer> days,int start,int end,int interval,int workoutMinute) {
 public ReminderPlan {if(days==null||days.isEmpty()||days.stream().anyMatch(d->d<1||d>7)||start<0||end>1440||end<=start||interval<30||interval>180||workoutMinute<0||workoutMinute>1439)throw new IllegalArgumentException("Choose days and a daytime window, interval 30–180 minutes");days=Set.copyOf(days);}
 public ZonedDateTime nextMovement(ZonedDateTime now,String skipDay,long snooze){if(!movement)return null;if(snooze>now.toInstant().toEpochMilli()){ZonedDateTime deferred=Instant.ofEpochMilli(snooze).atZone(now.getZone());if(movementWindow(deferred)&&!deferred.toLocalDate().toString().equals(skipDay))return deferred;}for(int offset=0;offset<9;offset++){LocalDate day=now.toLocalDate().plusDays(offset);if(!days.contains(day.getDayOfWeek().getValue())||day.toString().equals(skipDay))continue;int count=0;for(int minute=start;minute<end&&count<12;minute+=interval,count++){ZonedDateTime slot=day.atTime(minute/60,minute%60).atZone(now.getZone());if(slot.isAfter(now)&&slot.toInstant().toEpochMilli()>=snooze)return slot;}}return null;}
 public boolean movementWindow(ZonedDateTime now){int minute=now.getHour()*60+now.getMinute();return movement&&days.contains(now.getDayOfWeek().getValue())&&minute>=start&&minute<end;}
 public ZonedDateTime nextWorkout(ZonedDateTime now,Set<LocalDate> planned){if(!workouts)return null;return planned.stream().map(d->d.atTime(workoutMinute/60,workoutMinute%60).atZone(now.getZone())).filter(d->d.isAfter(now)).min(Comparator.naturalOrder()).orElse(null);}
}
