package app.rwun;

import java.util.*;
import org.json.*;

/**
 * Validates a complete AI-personalised schedule against an independently generated
 * deterministic plan. The model may personalise effort and easy-run volume, but
 * cannot invent dates, remove recovery days, change session identity or prescribe
 * unsupported quality workouts.
 */
final class AiFullPlanPolicy {
  private AiFullPlanPolicy() {}
  static String prompt(RaceTraining.Draft baseline, List<RunStore.Run> history) throws JSONException {
    JSONArray sessions=new JSONArray(),runs=new JSONArray();
    for(PlanEngine.Session s:baseline.sessions())sessions.put(new JSONObject()
        .put("id",s.id()).put("date",s.date().toString()).put("title",s.title())
        .put("km",s.km()).put("instructions",s.instructions()));
    for(RunStore.Run r:history.stream().sorted(Comparator.comparing(RunStore.Run::date).reversed()).limit(30).toList())
      runs.put(new JSONObject().put("date",r.date()).put("km",r.km()).put("seconds",r.seconds())
          .put("effort",r.rpe()>0?r.rpe():JSONObject.NULL)
          .put("fatigue",r.fatigue()>0?r.fatigue():JSONObject.NULL).put("pain",r.pain()));
    JSONObject context=new JSONObject().put("goalKm",baseline.config().goalKm())
        .put("weeks",baseline.config().weeks()).put("weeklyBaselineKm",baseline.config().weeklyKm())
        .put("review",baseline.review().reason()).put("recentRuns",runs).put("baselineSessions",sessions);
    return "Design a complete personalised running programme balancing performance and recovery. "
        +"Return JSON {explanation:string,sessions:[{id:string,km:number,instructions:string}]}. "
        +"Include EVERY baseline session exactly once. Preserve all IDs, dates, workout types, recovery "
        +"days and session order. For easy sessions, distance may be 70-100% of baseline (no increases); "
        +"for tempo/interval/run-walk/quality sessions preserve exact distance. Keep instructions "
        +"conservative, practical and under 400 characters each. Never diagnose injury or claim "
        +"race-readiness. Use only supplied evidence. Do not include routes or personal identifiers.\n"+context;
  }
  static List<PlanEngine.Session> validate(RaceTraining.Draft baseline,String raw) throws JSONException {
    if(raw==null||raw.length()>180000)throw new IllegalArgumentException("Invalid AI plan response.");
    JSONObject response=new JSONObject(raw);
    String explanation=response.getString("explanation").trim();
    if(explanation.isEmpty()||explanation.length()>4000)throw new IllegalArgumentException("AI plan explanation is missing.");
    JSONArray rows=response.getJSONArray("sessions");
    List<PlanEngine.Session> original=baseline.sessions();
    if(rows.length()!=original.size())throw new IllegalArgumentException("AI must return every scheduled workout.");
    Map<String,PlanEngine.Session> byId=new HashMap<>();
    for(PlanEngine.Session s:original)byId.put(s.id(),s);
    Set<String> seen=new HashSet<>();
    Map<Integer,Double> weekly=new HashMap<>(),limits=new HashMap<>();
    List<PlanEngine.Session> result=new ArrayList<>();
    for(int i=0;i<rows.length();i++){
      JSONObject row=rows.getJSONObject(i);
      String id=row.getString("id"),instructions=row.getString("instructions").trim();
      PlanEngine.Session old=byId.get(id);
      if(old==null||!seen.add(id)||instructions.isEmpty()||instructions.length()>400)
        throw new IllegalArgumentException("AI plan contains an invalid or duplicate workout.");
      double km=row.getDouble("km");
      boolean demanding=ScheduleProposer.demanding(old.title())||old.title().equals("Run / walk");
      double min=demanding?old.km():old.km()*.70,max=old.km();
      if(!Double.isFinite(km)||km<min-.011||km>max+.011)
        throw new IllegalArgumentException("AI plan exceeds safe workout distance bounds.");
      int week=Integer.parseInt(id.split("-")[0]);
      weekly.merge(week,km,Double::sum);limits.merge(week,old.km(),Double::sum);
      result.add(new PlanEngine.Session(id,old.date(),old.title(),Math.round(km*100)/100.,instructions));
    }
    for(int week:weekly.keySet())if(weekly.get(week)>limits.get(week)+.01)
      throw new IllegalArgumentException("AI plan exceeds weekly volume limits.");
    result.sort(Comparator.comparing(PlanEngine.Session::date).thenComparing(PlanEngine.Session::id));
    return List.copyOf(result);
  }
}
