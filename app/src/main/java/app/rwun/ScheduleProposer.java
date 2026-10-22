package app.rwun;
import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
/** Proposes only remaining sessions. Unplaced sessions are skipped, never caught up or invented. */
public final class ScheduleProposer {
 public record Change(String id,LocalDate before,LocalDate after,String title,double km,boolean skip,String reason){}
 public static List<Change> propose(List<PlanEngine.Session> pending,Set<LocalDate> available,List<WeekBalance.Other> others,boolean lighter){
  List<Change> out=new ArrayList<>();Set<LocalDate> used=new HashSet<>();List<PlanEngine.Session> ordered=new ArrayList<>(pending);ordered.sort(Comparator.comparing(PlanEngine.Session::date));
  for(var s:ordered){boolean demanding=demanding(s.title())&&!lighter;
   var candidates=available.stream().filter(d->!used.contains(d)).filter(d->others.stream().noneMatch(o->o.hard()&&(o.date().equals(d)||(demanding&&Math.abs(ChronoUnit.DAYS.between(d,o.date()))==1)))).filter(d->!demanding||out.stream().noneMatch(c->!c.skip()&&demanding(c.title())&&Math.abs(ChronoUnit.DAYS.between(d,c.after()))<2)).sorted(Comparator.<LocalDate>comparingLong(d->Math.abs(ChronoUnit.DAYS.between(s.date(),d))).thenComparing(Comparator.naturalOrder())).collect(java.util.stream.Collectors.toList());
   if(candidates.isEmpty()){out.add(new Change(s.id(),s.date(),s.date(),s.title(),s.km(),true,"No suitable available day. Skip without catch-up."));continue;}
   LocalDate date=candidates.get(0);used.add(date);out.add(new Change(s.id(),s.date(),date,lighter?"Easy recovery run":s.title(),lighter?Math.max(.1,Math.floor(s.km()*.8*10)/10):s.km(),false,lighter?"Lower distance and easy effort; no added mileage.":"Reschedule remaining session only."));
  }return List.copyOf(out);
 }
 public static boolean demanding(String title){String t=title.toLowerCase(Locale.ROOT);return t.contains("long")||t.contains("tempo")||t.contains("interval");}
}
