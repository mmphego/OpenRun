package app.rwun;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.json.*;

/** Read-only progress snapshot. Never mutates the schedule or attributes unrelated runs to a plan. */
final class TrainingProgress {
  record Snapshot(double plannedKm,double actualKm,int plannedWorkouts,int completedWorkouts,
                  String phase,String recommendation,String recovery,String latestChange) {}
  private TrainingProgress(){}
  static Snapshot from(RunStore store,LocalDate today){synchronized(RunStore.LOCK){return snapshot(store,today);}}
  private static Snapshot snapshot(RunStore store,LocalDate today){
    PlanEngine.Config config=store.config();
    if(config==null)return new Snapshot(0,0,0,0,"No active plan","Create a plan to see recommendations.",
        "No recovery assessment available.","No plan changes recorded.");
    String plan=store.planId()+":";
    List<PlanEngine.Session> sessions=store.plannedSessions();
    Map<String,RunStore.Run> completed=new HashMap<>();
    List<RunStore.Run> runs=store.runs();
    for(RunStore.Run run:runs)if(run.sessionId().startsWith(plan))completed.putIfAbsent(run.sessionId(),run);
    JSONObject matches=store.v103().optJSONObject("matches");
    if(matches!=null)for(String activity:V1State.keys(matches)){
      String session=matches.optString(activity);
      if(!session.startsWith(plan)||completed.containsKey(session))continue;
      for(RunStore.Run run:runs)if(run.sessionId().equals(activity)){completed.put(session,run);break;}
    }
    double planned=0,actual=0;int due=0,done=0;
    for(PlanEngine.Session session:sessions){
      String id=plan+session.id();
      RunStore.Run run=completed.get(id);
      if(!session.date().isAfter(today)||run!=null){
        planned+=session.km();due++;
        if(run!=null){actual+=run.km();done++;}
      }
    }
    long week=ChronoUnit.WEEKS.between(config.start(),today);
    String phase=today.isBefore(config.start())?"Upcoming":week>=config.weeks()?"Plan finished":
        week>=config.weeks()-2?"Taper / finish":
        (week+1)%4==0?"Recovery week":"Build · week "+(week+1)+" of "+config.weeks();
    boolean recentPain=runs.stream().anyMatch(r->r.pain()&&!LocalDate.parse(r.date()).isBefore(today.minusDays(6))&&!LocalDate.parse(r.date()).isAfter(today));
    String recovery=store.resting()?"Training hold active. Review recovery before running.":
        recentPain?
        "Recent pain reported. Review recovery before your next run.":
        "No active training hold. Adjust effort for fatigue; this is not a medical readiness score.";
    PlanEngine.Session next=sessions.stream().filter(s->!s.date().isBefore(today))
        .filter(s->!store.completed(plan+s.id())&&!store.skipped(plan+s.id()))
        .min(Comparator.comparing(PlanEngine.Session::date)).orElse(null);
    String recommendation=store.resting()||recentPain?"Rest and review your training hold.":
        next==null?"No remaining scheduled runs. Review your plan.":
        next.date()+" · "+next.title()+" · "+PlanEngine.distance(next.km())+". "+next.instructions();
    String change="No recorded plan changes yet.";
    JSONArray history=store.planChanges();
    for(int i=history.length()-1;i>=0;i--){
      JSONObject event=history.optJSONObject(i);
      if(event!=null&&store.planId().equals(event.optString("planId"))){
        change=event.optString("kind","Plan update")+": "+event.optString("reason","Reason not recorded.");
        JSONArray changes=event.optJSONArray("changes");
        if(changes!=null){String detail=changes.length()>3?changes.length()+" workout targets recorded. View all plan changes for the full details.":PlanChanges.summary(changes,false);if(!detail.isBlank())change+=" "+detail;}
        break;
      }
    }
    return new Snapshot(Math.round(planned*10)/10d,Math.round(actual*10)/10d,due,done,phase,recommendation,recovery,change);
  }
}
