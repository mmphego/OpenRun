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
  static String prompt(RaceTraining.Draft baseline, RunStore store, JSONObject settings) throws JSONException {
    JSONArray sessions=new JSONArray();
    for(PlanEngine.Session s:baseline.sessions())sessions.put(new JSONObject()
        .put("id",s.id()).put("date",s.date().toString()).put("title",s.title())
        .put("km",s.km()).put("instructions",s.instructions()));
    JSONArray runs=AiCoachConversation.recentRuns(store);
    JSONObject context=new JSONObject().put("goalKm",baseline.config().goalKm())
        .put("goalPaceSecondsPerKm",settings.optInt("goalPace",0))
        .put("observedPaceSecondsPerKm",AiCoachConversation.observedPace(store)).put("trainingHold",baseline.review().hold()).put("longRunDay",baseline.preferences().longDay().toString())
        .put("weeks",baseline.config().weeks()).put("weeklyBaselineKm",baseline.config().weeklyKm())
        .put("review",baseline.review().reason()).put("recentRuns",runs).put("baselineSessions",sessions);
    return "Design a complete personalised running programme balancing performance and recovery. "
        +"Return JSON {explanation:string,sessions:[{id:string,km:number,paceSecondsPerKm:integer,instructions:string}]}. "
        +"Include EVERY baseline session exactly once. Preserve all IDs, dates, workout types, recovery "
        +"days and session order. For easy sessions, distance may be 70-100% of baseline (no increases), rounded to one decimal; "
        +"for long/tempo/interval/run-walk/quality sessions preserve exact distance and instructions. Keep easy-run instructions "
        +"conservative, practical and under 400 characters each. Explain the distance and pace gap and how the weeks progress towards the goal. For easy sessions optionally supply paceSecondsPerKm, no faster than observedPaceSecondsPerKm and between 180 and 1800. Use 0 for effort-only, mandatory when there is no observed pace or a training hold. Do not use a Coach pace marker in instructions. Keep demanding sessions at pace 0. Never diagnose injury or claim "
        +"race-readiness. Missing feedback is unknown readiness; goal pace is an aspiration, not a training prescription. "
        +"Use only supplied evidence. Do not include routes or personal identifiers.\n"+context;
  }
  static List<PlanEngine.Session> validate(RaceTraining.Draft baseline,String raw) throws JSONException {
    return validate(baseline,raw,0);
  }
  static List<PlanEngine.Session> validate(RaceTraining.Draft baseline,String raw,double observedPace) throws JSONException {
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
      if(!(row.get("km") instanceof Number))throw new IllegalArgumentException("AI workout distance must be numeric.");
      double km=row.getDouble("km");
      if(WorkoutFactory.coachPace(instructions)>0)throw new IllegalArgumentException("AI instructions contain an unvalidated pace marker.");
      boolean demanding=ScheduleProposer.demanding(old.title())||old.title().equals("Run / walk");
      Object paceValue=row.opt("paceSecondsPerKm");double pace=paceValue==null?0:row.optDouble("paceSecondsPerKm",Double.NaN);
      if(paceValue!=null&&!(paceValue instanceof Number)||!Double.isFinite(pace)||pace!=Math.rint(pace)||pace!=0&&(demanding||baseline.review().hold()||observedPace<=0||pace<Math.max(180,observedPace)||pace>1800))throw new IllegalArgumentException("AI training pace is unsupported by recent running evidence.");
      if(pace>0)instructions="Coach pace: "+PaceProfile.format(pace)+" min/km.\nKeep a conversational effort; slow down or walk if needed. "+instructions;
      double min=demanding?old.km():old.km()*.70,max=old.km();
      if(!Double.isFinite(km)||km<min-.000001||km>max+.000001)
        throw new IllegalArgumentException("AI plan exceeds safe workout distance bounds.");
      int week=Integer.parseInt(id.split("-")[0]);
      weekly.merge(week,km,Double::sum);limits.merge(week,old.km(),Double::sum);
      double acceptedKm=demanding?old.km():Math.max(Math.ceil(min*10-.000001)/10,Math.min(max,Math.round(km*10)/10.));
      result.add(new PlanEngine.Session(id,old.date(),old.title(),acceptedKm,demanding?old.instructions():instructions));
    }
    for(int week:weekly.keySet())if(weekly.get(week)>limits.get(week)+.01)
      throw new IllegalArgumentException("AI plan exceeds weekly volume limits.");
    result.sort(Comparator.comparing(PlanEngine.Session::date).thenComparing(PlanEngine.Session::id));
    return List.copyOf(result);
  }
}
