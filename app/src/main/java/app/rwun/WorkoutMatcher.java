package app.rwun;
import java.time.*;import java.time.temporal.ChronoUnit;import java.util.*;
/** Suggestions only. Matching requires explicit review and never creates a second activity. */
public final class WorkoutMatcher {
 public static List<PlanEngine.Session> candidates(LocalDate date,double km,List<PlanEngine.Session> pending){return pending.stream().filter(s->Math.abs(ChronoUnit.DAYS.between(date,s.date()))<=1&&km>=s.km()*.5&&km<=s.km()*1.5).sorted(Comparator.comparingDouble(s->Math.abs(ChronoUnit.DAYS.between(date,s.date()))+Math.abs(km-s.km())/Math.max(.1,s.km()))).collect(java.util.stream.Collectors.toList());}
 public static boolean possibleDuplicate(LocalDate a,double kmA,long secA,LocalDate b,double kmB,long secB){return a.equals(b)&&Math.abs(kmA-kmB)<=Math.max(.1,Math.max(kmA,kmB)*.05)&&Math.abs(secA-secB)<=Math.max(120,Math.max(secA,secB)*.1);}
}
