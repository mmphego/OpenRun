package app.rwun;
import java.time.DayOfWeek;import java.util.*;
/** The preview and persisted first plan use the same generator. */
public final class OnboardingPlan {
  public static PlanPreferences returning(PlanEngine.Config c,PaceProfile profile){List<PlanEngine.Session> base=new PlanEngine().generate(c,profile);Set<DayOfWeek> days=new HashSet<>();DayOfWeek last=null;for(var session:base)if(session.date().isBefore(c.start().plusDays(7))){days.add(session.date().getDayOfWeek());last=session.date().getDayOfWeek();}return new PlanPreferences("Return to running",days,last);}
  public static List<PlanEngine.Session> sessions(PlanEngine.Config c,PaceProfile profile,boolean returning){return returning?returning(c,profile).generate(c,profile):new PlanEngine().generate(c,profile);}
}
