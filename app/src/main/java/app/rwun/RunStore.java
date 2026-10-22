package app.rwun;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.util.*;
import org.json.*;

final class RunStore {
  static final Object LOCK=new Object();
  record Run(
      String sessionId,
      String date,
      double km,
      int minutes,
      int rpe,
      int fatigue,
      boolean pain,
      String source,
      String routeFile,
      int avgHr,
      long seconds,
      String workoutType,
      double qualityKm,
      long qualitySeconds,
      double targetPace,
      long benchmark) {
    Run(
        String sessionId,
        String date,
        double km,
        int minutes,
        int rpe,
        int fatigue,
        boolean pain,
        String source,
        String routeFile,
        int avgHr,
        long seconds) {
      this(
          sessionId,
          date,
          km,
          minutes,
          rpe,
          fatigue,
          pain,
          source,
          routeFile,
          avgHr,
          seconds,
          "Unstructured",
          0,
          0,
          0,
          0);
    }

    Run(String id, String date, double km, int minutes) {
      this(id, date, km, minutes, 5, 2, false, "Manual", "", 0, minutes * 60L);
    }
  }

  private final SharedPreferences preferences;

  RunStore(Context context) {
    preferences = context.getSharedPreferences("rwun", Context.MODE_PRIVATE);
  }

  PlanEngine.Config config() {
    if (!preferences.contains("start")) return null;
    try {
      return new PlanEngine.Config(
          preferences.getInt("weeks", 8),
          preferences.getInt("days", 3),
          preferences.getFloat("volume", 12),
          Math.round(preferences.getFloat("goal", 5) * 10.0) / 10.0,
          LocalDate.parse(preferences.getString("start", "")));
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Saved plan cannot be read. Data has not been overwritten.", e);
    }
  }

  JSONObject v1(){ synchronized(LOCK){try{return new JSONObject(preferences.getString("v1","{}"));}catch(Exception e){throw new IllegalStateException("V1 preferences cannot be read",e);}}}
  void v1(JSONObject data){ synchronized(LOCK){try{V1State.validate(data);commit(preferences.edit().putString("v1",data.toString()));}catch(Exception e){throw new IllegalArgumentException("Invalid v1 preferences",e);}}}
  void event(String name){ synchronized(LOCK){try{JSONObject data=v1();if(!data.optBoolean("metrics",false))return;V1State.event(data,name,LocalDate.now());v1(data);}catch(Exception ignored){/* Optional metrics never interrupt training. */}}}

  JSONObject cycling(){synchronized(LOCK){return object("cycling");}}
  void cycling(JSONObject d,boolean clear,boolean pain){synchronized(LOCK){try{CyclingData.validate(d);var e=preferences.edit().putString("cycling",d.toString());if(clear)e.remove("active");if(pain)e.putBoolean("rest",true).putString("adaptReason","Pain reported during cycling. Review recovery before resuming training.");commit(e);}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}}}
  List<WeekBalance.Other> otherActivities(){return List.of();}
  void refocus(Context context){synchronized(LOCK){try{if(preferences.getInt("focusVersion",0)>=1)return;JSONObject cycle=cycling();JSONArray archived=CyclingData.rows(cycle,"archivedRecordings");JSONObject indoor=cycle.optJSONObject("active");if(indoor!=null){indoor.put("archiveType","Indoor ride");archived.put(indoor);cycle.remove("active");}boolean clear=false;if(!checkpoint().isEmpty()){JSONObject record=new JSONObject(checkpoint());if(record.optBoolean("cycling")){record.put("archiveType","GPS ride");String route=record.optString("route");if(!route.isEmpty()&&!context.getFileStreamPath(route).isFile())record.put("missingRoute",route).put("route","");archived.put(record);clear=true;}}if(archived.length()>0)cycle.put("archivedRecordings",archived);CyclingData.validate(cycle);var e=preferences.edit().putString("cycling",cycle.toString()).putInt("focusVersion",1);if(clear)e.remove("active");commit(e);}catch(Exception e){throw new IllegalStateException("Previous recording could not be archived safely",e);}}}
  JSONArray walks(){return WalkData.outdoor(v103());}
  void addWalk(Run run){synchronized(LOCK){try{JSONArray rows=walks();for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(run.sessionId()))throw new IllegalArgumentException("This walk is already saved");rows.put(WalkData.from(run));WalkData.validate(rows);JSONObject d=v103().put("walks",rows);captureGpsOrigin(d,run.sessionId());V103Data.validate(d);var e=preferences.edit().putString("v103",d.toString());if(run.pain())e.putBoolean("rest",true).putString("adaptReason","Pain reported during walking. Review recovery before resuming training.");commit(e);}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}}}
  void correctWalkDistance(String id,double km){synchronized(LOCK){
    if(!Double.isFinite(km)||km<0||km>250)throw new IllegalArgumentException("Enter a walking distance from 0 to 250 km");
    try{JSONArray rows=walks();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.getString("id").equals(id)){
      if(!row.has("recordedSource"))row.put("recordedSource",row.optString("source"));
      row.put("km",km).put("distanceKnown",km>0).put("source","Manual walk distance");
      WalkData.validate(rows);v103(v103().put("walks",rows));return;
    }}throw new IllegalArgumentException("This saved walk was not found");}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}
  }}
  void deleteWalk(String id){synchronized(LOCK){try{JSONObject d=v103();JSONArray keep=new JSONArray(),rows=walks();JSONObject removed=null;for(int i=0;i<rows.length();i++){var r=rows.getJSONObject(i);if(r.getString("id").equals(id))removed=r;else keep.put(r);}if(removed==null)return;if(d.has("walkUndo"))throw new IllegalStateException("Undo the previous walk deletion or wait for its cleanup before deleting another");d.put("walks",keep).put("walkUndo",new JSONObject().put("walk",removed).put("expires",System.currentTimeMillis()+900000));v103(d);}catch(Exception e){throw new IllegalArgumentException(e);}}}
  void undoWalk(){synchronized(LOCK){try{JSONObject d=v103(),u=d.optJSONObject("walkUndo");if(u==null||u.getLong("expires")<System.currentTimeMillis())throw new IllegalArgumentException("The 15-minute undo window ended");JSONArray rows=walks();rows.put(u.getJSONObject("walk"));WalkData.validate(rows);d.put("walks",rows);d.remove("walkUndo");v103(d);}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}}}
  void purgeWalkUndo(Context c){synchronized(LOCK){try{JSONObject d=v103(),u=d.optJSONObject("walkUndo");if(u==null||u.getLong("expires")>=System.currentTimeMillis())return;String route=u.getJSONObject("walk").optString("route");boolean used=false;for(int i=0;i<walks().length();i++)if(walks().getJSONObject(i).optString("route").equals(route))used=true;if(!used&&runs().stream().noneMatch(r->r.routeFile().equals(route))&&route.matches("route-[a-zA-Z0-9-]+\\.jsonl"))c.deleteFile(route);d.remove("walkUndo");v103(d);}catch(Exception ignored){}}}
  JSONObject v103(){synchronized(LOCK){return object("v103");}}
  void v103(JSONObject data){synchronized(LOCK){try{V103Data.validate(data);commit(preferences.edit().putString("v103",data.toString()));}catch(Exception e){throw new IllegalArgumentException("Invalid feature settings: "+e.getMessage(),e);}}}
  boolean indoorPending(){return v103().has("activeIndoor")||cycling().has("active");}
  String proposalFingerprint(){return planId()+plannedSessions()+runs().toString()+preferences.getString("skips","")+resting();}
  void applyProposal(String fingerprint,List<ScheduleProposer.Change> changes){applyReviewedProposal(fingerprint,changes,"");}
  void applyReviewedProposal(String fingerprint,List<ScheduleProposer.Change> changes,String reviewTicket){synchronized(LOCK){
    if(!reviewTicket.isEmpty()&&v103().optString("raceReview").equals(reviewTicket))throw new IllegalStateException("This running evidence has already been reviewed and applied.");
    if(!fingerprint.equals(proposalFingerprint()))throw new IllegalStateException("Your plan changed. Generate a new preview.");
    if(resting()||!checkpoint().isEmpty()||indoorPending())throw new IllegalStateException("Review the training hold or pending recording first");
    try{JSONObject data=v103(),overrides=data.optJSONObject("overrides");if(overrides==null)overrides=new JSONObject();JSONObject skips=object("skips"),moves=object("moves");Set<LocalDate> occupied=new HashSet<>();Set<String> changed=new HashSet<>();for(var c:changes)if(!changed.add(c.id()))throw new IllegalArgumentException("Duplicate proposal session");
      for(var s:plannedSessions())if(!changed.contains(s.id())&&!skipped(planId()+":"+s.id()))occupied.add(s.date());
      Map<String,PlanEngine.Session> current=new HashMap<>();for(var s:plannedSessions())current.put(s.id(),s);
      for(var c:changes){var old=current.get(c.id());String key=planId()+":"+c.id();if(old==null||completed(key)||old.date().isBefore(LocalDate.now())||skipped(key)||c.km()>old.km()+.001||c.km()<=0)throw new IllegalArgumentException("Proposal changes protected history or increases mileage");if(c.skip()){skips.put(key,true);continue;}if(c.after().isBefore(LocalDate.now())||c.after().isBefore(config().start())||!c.after().isBefore(config().start().plusWeeks(config().weeks()))||!occupied.add(c.after()))throw new IllegalArgumentException("Invalid or occupied proposal date");moves.put(key,c.after().toString());overrides.put(key,new JSONObject().put("date",c.after().toString()).put("km",c.km()).put("factor",factor(c.after())).put("title",c.title()));}
      data.put("overrides",overrides);if(!reviewTicket.isEmpty())data.put("raceReview",reviewTicket);V103Data.validate(data);commit(preferences.edit().putString("v103",data.toString()).putString("moves",moves.toString()).putString("skips",skips.toString()));
    }catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}
  }}
  void matchWorkout(String activity,String session){synchronized(LOCK){if(runs().stream().noneMatch(r->r.sessionId().equals(activity)))throw new IllegalArgumentException("Activity is no longer available");if(completed(session))throw new IllegalArgumentException("Workout is already completed");boolean exists=plannedSessions().stream().anyMatch(s->(planId()+":"+s.id()).equals(session));if(!exists||skipped(session))throw new IllegalArgumentException("Choose an unskipped planned workout");try{JSONObject d=v103(),m=d.optJSONObject("matches");if(m==null)m=new JSONObject();if(m.has(activity))throw new IllegalArgumentException("Unmatch the existing workout first");m.put(activity,session);d.put("matches",m);v103(d);}catch(JSONException e){throw new IllegalArgumentException(e);}}}
  void unmatch(String activity){synchronized(LOCK){JSONObject d=v103(),m=d.optJSONObject("matches");if(m!=null){m.remove(activity);v103(d);}}}
  void correctTotals(String id,double km,long seconds){synchronized(LOCK){try{if(!Double.isFinite(km)||km<=0||km>250||seconds<1||seconds>180000)throw new IllegalArgumentException("Check distance and duration");JSONArray records=new JSONArray(preferences.getString("runs","[]"));boolean found=false;for(int i=0;i<records.length();i++){JSONObject row=records.getJSONObject(i);if(row.getString("id").equals(id)){if(id.startsWith("indoor:"))row.put("source","Indoor · corrected totals");row.put("km",km).put("seconds",seconds).put("minutes",Math.max(1,seconds/60)).put("qualityKm",0).put("qualitySeconds",0).put("targetPace",0).put("benchmark",0);found=true;}}if(!found)throw new IllegalArgumentException("Activity no longer exists");JSONObject d=v103();JSONArray indoor=d.optJSONArray("indoor");if(indoor!=null)for(int i=0;i<indoor.length();i++){JSONObject row=indoor.getJSONObject(i);if(id.equals("indoor:"+row.getString("id")))row.put("km",km).put("seconds",seconds).put("distanceSource","Corrected totals");}V103Data.validate(d);commit(preferences.edit().putString("runs",records.toString()).putString("v103",d.toString()));}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}}}
  void softDelete(String id){synchronized(LOCK){try{JSONArray rows=new JSONArray(preferences.getString("runs","[]")),keep=new JSONArray();JSONObject removed=null;for(int i=0;i<rows.length();i++){var row=rows.getJSONObject(i);if(row.getString("id").equals(id))removed=row;else keep.put(row);}if(removed==null)return;JSONObject d=v103(),undo=new JSONObject().put("run",removed).put("expires",System.currentTimeMillis()+15*60*1000);JSONArray indoor=d.optJSONArray("indoor");if(indoor!=null){JSONArray retained=new JSONArray();for(int i=0;i<indoor.length();i++){JSONObject row=indoor.getJSONObject(i);if(id.equals("indoor:"+row.getString("id")))undo.put("indoor",row);else retained.put(row);}d.put("indoor",retained);}JSONObject matches=d.optJSONObject("matches");if(matches!=null&&matches.has(id)){undo.put("match",matches.getString(id));matches.remove(id);}d.put("undo",undo);V103Data.validate(d);commit(preferences.edit().putString("runs",keep.toString()).putString("v103",d.toString()));}catch(Exception e){throw new IllegalArgumentException(e);}}}
  void undoDelete(){synchronized(LOCK){try{JSONObject d=v103(),u=d.optJSONObject("undo");if(u==null||u.getLong("expires")<System.currentTimeMillis())throw new IllegalStateException("The 15-minute undo window has ended");var r=u.getJSONObject("run");JSONArray rows=new JSONArray(preferences.getString("runs","[]"));if(completed(r.getString("id")))throw new IllegalStateException("Activity already restored");rows.put(r);JSONObject check=backupPreferences();check.put("runs",rows.toString());validatePreferences(check);if(u.has("match")){JSONObject matches=d.optJSONObject("matches");if(matches==null)matches=new JSONObject();for(String key:V1State.keys(matches))if(matches.optString(key).equals(u.getString("match")))throw new IllegalStateException("Unmatch the other activity from this workout before restoring this one");matches.put(r.getString("id"),u.getString("match"));d.put("matches",matches);}if(u.has("indoor")){JSONArray indoor=d.optJSONArray("indoor");if(indoor==null)indoor=new JSONArray();indoor.put(u.getJSONObject("indoor"));d.put("indoor",indoor);}d.remove("undo");V103Data.validate(d);commit(preferences.edit().putString("runs",rows.toString()).putString("v103",d.toString()));}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}}}

  void finishIndoor(JSONObject row){finishIndoor(row,0);}
  void finishIndoor(JSONObject row,double calibration){synchronized(LOCK){try{JSONObject d=v103();JSONObject active=d.optJSONObject("activeIndoor");if(active==null||!active.getString("id").equals(row.getString("id"))||!active.getBoolean("paused"))throw new IllegalStateException("This session changed. Reopen its review before saving.");if(!active.getString("kind").equals(row.getString("kind"))||!active.getString("date").equals(row.getString("date")))throw new IllegalArgumentException("Session identity changed");if(calibration!=0){if(!Double.isFinite(calibration)||calibration<.2||calibration>(row.getString("kind").equals("Walk")?1.5:2)||row.optLong("phoneSteps")<100||!row.optString("distanceSource").equals("Machine entered"))throw new IllegalArgumentException("Invalid calibration evidence");JSONObject lengths=d.optJSONObject("stepLengths");if(lengths==null)lengths=new JSONObject();lengths.put(row.getString("kind"),calibration);d.put("stepLengths",lengths);}JSONArray history=d.optJSONArray("indoor");if(history==null)history=new JSONArray();history.put(row);d.put("indoor",history);if(row.optLong("startedAtWall")>0){JSONObject origins=d.optJSONObject("importOrigins");if(origins==null)origins=new JSONObject();String localId=row.getString("kind").equals("Run")?"indoor:"+row.getString("id"):row.getString("id");long begun=row.getLong("startedAtWall"),ended=row.getLong("endedAtWall");origins.put(localId,origin("OpenRun indoor",localId,ended>=begun?begun:0,ended>=begun?ended:0));d.put("importOrigins",origins);}d.remove("activeIndoor");V103Data.validate(d);JSONArray records=new JSONArray(preferences.getString("runs","[]"));if(row.getString("kind").equals("Run")&&!row.isNull("km")){String id="indoor:"+row.getString("id");if(completed(id))throw new IllegalArgumentException("Indoor session already saved");records.put(new JSONObject().put("id",id).put("date",row.getString("date")).put("km",row.getDouble("km")).put("minutes",Math.max(1,row.getLong("seconds")/60)).put("seconds",row.getLong("seconds")).put("rpe",row.getInt("effort")).put("fatigue",row.getInt("fatigue")).put("pain",row.getBoolean("pain")).put("source","Indoor · "+row.optString("distanceSource","Machine entered")).put("route","").put("hr",0).put("workoutType","Indoor run"));}var edit=preferences.edit().putString("v103",d.toString()).putString("runs",records.toString());if(row.getBoolean("pain"))edit.putBoolean("rest",true).putString("adaptReason","Pain reported during an indoor session. Review recovery before further training.");commit(edit);}catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}}}
  void deleteIndoorTimeOnly(String id){synchronized(LOCK){if(runs().stream().anyMatch(r->r.sessionId().equals("indoor:"+id)))throw new IllegalStateException("Delete the saved running activity in Progress so it can be undone");try{JSONObject d=v103();JSONArray rows=d.optJSONArray("indoor"),keep=new JSONArray();if(rows!=null)for(int i=0;i<rows.length();i++)if(!rows.getJSONObject(i).getString("id").equals(id))keep.put(rows.getJSONObject(i));d.put("indoor",keep);v103(d);}catch(Exception e){throw new IllegalArgumentException(e);}}}
  void purgeUndo(Context context,boolean replace){synchronized(LOCK){try{JSONObject d=v103(),u=d.optJSONObject("undo");if(u==null||(!replace&&u.getLong("expires")>=System.currentTimeMillis()))return;String route=u.getJSONObject("run").optString("route");if(route.matches("route-[a-zA-Z0-9-]+\\.jsonl")&&runs().stream().noneMatch(r->r.routeFile().equals(route)))context.deleteFile(route);d.remove("undo");v103(d);}catch(Exception ignored){}}}

  String planId() {
    return preferences.getString("planId", preferences.getString("start", "legacy"));
  }

  void save(PlanEngine.Config c) { synchronized(LOCK) {
    JSONObject retained=options();retained.remove("mode");retained.remove("weekdays");retained.remove("longDay");
    commit(
        preferences
            .edit()
            .putString("planId", UUID.randomUUID().toString())
            .putInt("weeks", c.weeks())
            .putInt("days", c.days())
            .putFloat("volume", (float) c.weeklyKm())
            .putFloat("goal", (float) c.goalKm())
            .putString("start", c.start().toString())
            .remove("moves")
            .remove("schedule")
            .putString("options",retained.toString())
            .remove("skips")
            .remove("adaptFrom")
            .remove("adaptFactor")
            .putString("adaptReason", resting() ? adaptation() : "No adjustments applied.")
            .putBoolean("rest", preferences.getBoolean("rest", false)));
   }}

  /** Atomic creation keeps preview, first week, history and recovery state consistent. */
  void createPlan(PlanEngine.Config c,PaceProfile p,boolean returning){synchronized(LOCK){
    if(!checkpoint().isEmpty())throw new IllegalStateException("Finish or recover the pending run first");
    JSONObject options=options();options.remove("mode");options.remove("weekdays");options.remove("longDay");
    try{if(returning){var preference=OnboardingPlan.returning(c,p);JSONArray days=new JSONArray();for(var day:preference.weekdays())days.put(day.getValue());options.put("mode",preference.mode()).put("weekdays",days).put("longDay",preference.longDay().getValue());}validateOptions(options);
    var schedule=OnboardingPlan.sessions(c,p,returning);
    commit(preferences.edit().putString("planId",UUID.randomUUID().toString()).putInt("weeks",c.weeks()).putInt("days",c.days()).putFloat("volume",(float)c.weeklyKm()).putFloat("goal",(float)c.goalKm()).putString("start",c.start().toString()).putFloat("profileDistance",(float)p.distanceKm()).putLong("profileSeconds",p.seconds()).putBoolean("speedWork",p.speedWork()).putString("options",options.toString()).putString("schedule",serializeSchedule(schedule).toString()).remove("moves").remove("skips").remove("adaptFrom").remove("adaptFactor").putString("adaptReason",resting()?adaptation():"No adjustments applied."));
    }catch(JSONException e){throw new IllegalArgumentException(e);}
  }}

  /** Commit race identity, generated schedule and settings together; retain history and holds. */
  void createRacePlan(String expectedFingerprint,PlanEngine.Config c,PaceProfile p,JSONObject race,JSONObject options,List<PlanEngine.Session> sessions){if(race==null)throw new IllegalArgumentException("Choose a race");createStructuredPlan(expectedFingerprint,c,p,race,options,sessions);}
  void createGoalPlan(String expectedFingerprint,PlanEngine.Config c,PaceProfile p,JSONObject options,List<PlanEngine.Session> sessions){createStructuredPlan(expectedFingerprint,c,p,null,options,sessions);}
  private void createStructuredPlan(String expectedFingerprint,PlanEngine.Config c,PaceProfile p,JSONObject race,JSONObject options,List<PlanEngine.Session> sessions){synchronized(LOCK){
    if(!expectedFingerprint.equals(proposalFingerprint()))throw new IllegalStateException("Your plan or running history changed. Preview again.");
    if(!checkpoint().isEmpty()||indoorPending())throw new IllegalStateException("Finish or recover your pending session first");
    try{
      String id=UUID.randomUUID().toString();JSONObject target=race==null?null:new JSONObject(race.toString()).put("planId",id),data=v103();if(target==null)data.remove("race");else data.put("race",target);V103Data.validate(data);validateOptions(options);
      if(options.getJSONArray("weekdays").length()!=c.days()||(target!=null&&(Math.abs(target.getDouble("km")-c.goalKm())>.001||!LocalDate.parse(target.getString("date")).equals(c.start().plusWeeks(c.weeks())))))throw new IllegalArgumentException("Race and training dates disagree");
      JSONObject candidate=new JSONObject().put("weeks",c.weeks()).put("days",c.days()).put("volume",c.weeklyKm()).put("goal",c.goalKm()).put("start",c.start().toString()).put("schedule",serializeSchedule(sessions).toString());validatePreferences(candidate);
      commit(preferences.edit().putString("planId",id).putInt("weeks",c.weeks()).putInt("days",c.days()).putFloat("volume",(float)c.weeklyKm()).putFloat("goal",(float)c.goalKm()).putString("start",c.start().toString()).putFloat("profileDistance",(float)p.distanceKm()).putLong("profileSeconds",p.seconds()).putBoolean("speedWork",p.speedWork()).putString("options",options.toString()).putString("schedule",serializeSchedule(sessions).toString()).putString("v103",data.toString()).remove("moves").remove("skips").remove("adaptFrom").remove("adaptFactor").putString("adaptReason",resting()?adaptation():"Plan created from reviewed running history."));
    }catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}
  }}

  /** Restart creates new completion IDs; historical runs, pace profile and safety hold remain. */
  void restartPlan(LocalDate start){ synchronized(LOCK) {
    if(start==null)throw new IllegalArgumentException("Choose a start date.");
    if(config()==null)throw new IllegalStateException("Create a plan first.");
    if(!checkpoint().isEmpty())throw new IllegalStateException("Finish, recover or discard the pending run first.");
    var current=config();var next=new PlanEngine.Config(current.weeks(),current.days(),current.weeklyKm(),current.goalKm(),start);
    var editor=preferences.edit().putString("planId",UUID.randomUUID().toString()).putString("start",start.toString()).remove("moves").remove("skips").remove("schedule");
    if(options().has("weekdays"))editor.putString("schedule",serializeSchedule(preferred(next,options())).toString());
    commit(editor);
   }}

  List<Run> runs() { synchronized(LOCK) {
    List<Run> result = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(preferences.getString("runs", "[]"));
      for (int i = 0; i < a.length(); i++) {
        JSONObject o = a.getJSONObject(i);
        result.add(
            new Run(
                o.getString("id"),
                o.getString("date"),
                o.getDouble("km"),
                o.getInt("minutes"),
                o.optInt("rpe", 5),
                o.optInt("fatigue", 2),
                o.optBoolean("pain"),
                o.optString("source", "Manual"),
                o.optString("route", ""),
                o.optInt("hr"),
                o.optLong("seconds", o.getInt("minutes") * 60L),
                o.optString("workoutType", "Unstructured"),
                o.optDouble("qualityKm", 0),
                o.optLong("qualitySeconds", 0),
                o.optDouble("targetPace", 0),
                o.optLong("benchmark", 0)));
      }
    } catch (JSONException e) {
      throw new IllegalStateException(
          "Run history cannot be read. Data has not been overwritten.", e);
    }
    result.sort(Comparator.comparing(Run::date));
    return result;
   }}

  boolean completed(String id) {
    if(id.startsWith("walk:")){JSONArray rows=walks();for(int i=0;i<rows.length();i++)if(rows.optJSONObject(i).optString("id").equals(id))return true;}
    if(runs().stream().anyMatch(r->r.sessionId().equals(id)))return true;
    JSONObject matches=v103().optJSONObject("matches");if(matches!=null)for(String key:V1State.keys(matches))if(matches.optString(key).equals(id)&&runs().stream().anyMatch(r->r.sessionId().equals(key)))return true;return false;
  }

  enum ImportResult { ADDED, DUPLICATE, REVIEW }
  static JSONObject importRow(Run r){try{return new JSONObject().put("id",r.sessionId()).put("date",r.date()).put("km",r.km()).put("minutes",r.minutes()).put("seconds",r.seconds()).put("rpe",r.rpe()).put("fatigue",r.fatigue()).put("pain",r.pain()).put("source",r.source()).put("route",r.routeFile()).put("hr",r.avgHr()).put("distanceKnown",r.km()>0);}catch(Exception e){throw new IllegalArgumentException(e);}}
  private static Run importedRun(JSONObject row)throws Exception{return new Run(row.getString("id"),row.getString("date"),row.getDouble("km"),row.getInt("minutes"),row.getInt("rpe"),row.getInt("fatigue"),row.getBoolean("pain"),row.getString("source"),row.optString("route"),row.optInt("hr"),row.getLong("seconds"));}
  static JSONObject origin(String provider,String external,long started,long ended){try{return new JSONObject().put("provider",provider).put("externalId",external).put("started",started).put("ended",ended);}catch(Exception e){throw new IllegalArgumentException(e);}}
  JSONArray pendingImports(){JSONArray a=v103().optJSONArray("possibleImports");return a==null?new JSONArray():a;}
  JSONObject possibleImport(Run candidate,boolean walk,JSONObject origin){synchronized(LOCK){
    JSONObject incoming=importRow(candidate),origins=v103().optJSONObject("importOrigins");
    List<JSONObject> rows=new ArrayList<>();if(walk)rows.addAll(WalkData.all(v103()));else for(var run:runs())rows.add(importRow(run));
    for(JSONObject row:rows){if(row.optString("id").equals(candidate.sessionId()))continue;JSONObject previous=origins==null?null:origins.optJSONObject(row.optString("id"));if(ActivityImportPolicy.possible(incoming,row,origin,previous))return row;}
    return null;
  }}
  ImportResult importActivity(Run run,boolean walk,JSONObject origin){synchronized(LOCK){try{
    ActivityImportPolicy.validateOrigin(origin);
    JSONObject d=v103(),ignored=d.optJSONObject("ignoredImports");
    if(completed(run.sessionId())||ignored!=null&&ignored.optBoolean(run.sessionId()))return ImportResult.DUPLICATE;
    JSONArray queue=pendingImports();for(int i=0;i<queue.length();i++)if(queue.getJSONObject(i).getString("id").equals(run.sessionId()))return ImportResult.REVIEW;
    JSONObject row=importRow(run),match=possibleImport(run,walk,origin);
    if(match!=null){if(queue.length()>=500)throw new IllegalStateException("Review pending imports before importing more activities");
      row.put("walk",walk).put("origin",origin).put("matchId",match.optString("id")).put("matchSource",match.optString("source",match.optString("distanceSource","Saved activity"))).put("matchKm",match.optDouble("km")).put("matchSeconds",match.optLong("seconds"));
      JSONObject origins=d.optJSONObject("importOrigins"),matched=origins==null?null:origins.optJSONObject(match.optString("id"));row.put("matchDate",match.optString("date")).put("matchStarted",matched==null?0:matched.optLong("started"));queue.put(row);d.put("possibleImports",queue);v103(d);return ImportResult.REVIEW;
    }
    commitImport(d,row,walk,origin);return ImportResult.ADDED;
  }catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}}}
  void resolveImport(String id,boolean importSeparately){synchronized(LOCK){try{
    JSONObject d=v103(),selected=null;JSONArray rows=pendingImports(),keep=new JSONArray();
    for(int i=0;i<rows.length();i++){var row=rows.getJSONObject(i);if(row.getString("id").equals(id))selected=row;else keep.put(row);}
    if(selected==null)throw new IllegalStateException("This import review is no longer available");
    d.put("possibleImports",keep);
    if(importSeparately){if(completed(id))throw new IllegalStateException("This activity was already saved");commitImport(d,selected,selected.getBoolean("walk"),selected.getJSONObject("origin"));}
    else{JSONObject ignored=d.optJSONObject("ignoredImports");if(ignored==null)ignored=new JSONObject();ignored.put(id,true);d.put("ignoredImports",ignored);v103(d);}
  }catch(Exception e){throw new IllegalArgumentException(e.getMessage(),e);}}}
  private void commitImport(JSONObject d,JSONObject row,boolean walk,JSONObject origin)throws Exception{
    JSONObject origins=d.optJSONObject("importOrigins");if(origins==null)origins=new JSONObject();origins.put(row.getString("id"),origin);d.put("importOrigins",origins);
    var edit=preferences.edit();
    if(walk){JSONArray rows=WalkData.outdoor(d);rows.put(row);d.put("walks",rows);}
    else{JSONArray rows=new JSONArray(preferences.getString("runs","[]"));rows.put(row);validatePreferences(new JSONObject().put("runs",rows.toString()));edit.putString("runs",rows.toString());}
    V103Data.validate(d);edit.putString("v103",d.toString());commit(edit);
  }
  private void captureGpsOrigin(JSONObject d,String id)throws Exception{
    if(checkpoint().isEmpty())return;JSONObject cp=new JSONObject(checkpoint());if(!id.equals(cp.optString("id"))||cp.optLong("startedAtWall")<=0)return;
    JSONObject origins=d.optJSONObject("importOrigins");if(origins==null)origins=new JSONObject();long started=cp.getLong("startedAtWall"),ended=cp.optLong("endedAtWall",started);origins.put(id,origin("OpenRun GPS",id,ended>=started?started:0,ended>=started?ended:0));d.put("importOrigins",origins);
  }

  void add(Run r) { synchronized(LOCK) {
    if (!Double.isFinite(r.km())
        || r.km() <= 0
        || r.km() > 250
        || r.seconds() < 1
        || r.seconds() > 180000
        || r.rpe() < 1
        || r.rpe() > 10
        || r.fatigue() < 1
        || r.fatigue() > 5)
      throw new IllegalArgumentException("Check distance, duration and feedback.");
    LocalDate.parse(r.date());
    List<Run> list = runs();
    if (completed(r.sessionId()))
      throw new IllegalArgumentException("This activity has already been saved.");
    list.add(r);
    writeRuns(list);
   }}

  void delete(String id) { synchronized(LOCK) {
    List<Run> list = runs();
    list.removeIf(r -> r.sessionId().equals(id));
    writeRuns(list);
   }}

  private void writeRuns(List<Run> runs) {
    JSONArray a = new JSONArray();
    try {
      for (Run r : runs)
        a.put(
            new JSONObject()
                .put("id", r.sessionId())
                .put("date", r.date())
                .put("km", r.km())
                .put("minutes", r.minutes())
                .put("rpe", r.rpe())
                .put("fatigue", r.fatigue())
                .put("pain", r.pain())
                .put("source", r.source())
                .put("route", r.routeFile())
                .put("hr", r.avgHr())
                .put("seconds", r.seconds())
                .put("workoutType", r.workoutType())
                .put("qualityKm", r.qualityKm())
                .put("qualitySeconds", r.qualitySeconds())
                .put("targetPace", r.targetPace())
                .put("benchmark", r.benchmark()));
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    try{JSONObject d=v103();for(Run r:runs)captureGpsOrigin(d,r.sessionId());V103Data.validate(d);commit(preferences.edit().putString("runs",a.toString()).putString("v103",d.toString()));}catch(Exception e){throw new IllegalStateException("Activity could not be saved",e);}
  }

  void adapt(AdaptiveCoach.Advice advice) { synchronized(LOCK) {
    if (resting() && !advice.rest()) return; // Only explicit recovery review may clear a hold.
    commit(
        preferences
            .edit()
            .putBoolean("rest", advice.rest())
            .putFloat("adaptFactor", (float) advice.factor())
            .putString("adaptFrom", LocalDate.now().plusDays(1).toString())
            .putString("adaptReason", advice.reason()));
   }}

  boolean resting() {
    return preferences.getBoolean("rest", false);
  }

  String adaptation() {
    return preferences.getString("adaptReason", "No adjustments applied.");
  }

  void resumeTraining() { synchronized(LOCK) {
    commit(
        preferences
            .edit()
            .putBoolean("rest", false)
            .putFloat("adaptFactor", .8f)
            .putString("adaptFrom", LocalDate.now().plusDays(1).toString())
            .putString(
                "adaptReason",
                "Training resumed at 80% of base distance for the next seven days."));
   }}

  double factor(LocalDate date) {
    String from = preferences.getString("adaptFrom", "");
    if (from.isEmpty()) return 1;
    LocalDate start = LocalDate.parse(from);
    return !date.isBefore(start) && date.isBefore(start.plusDays(7))
        ? preferences.getFloat("adaptFactor", 1)
        : 1;
  }

  boolean audio() {
    return preferences.getBoolean("audio", true);
  }

  void audio(boolean on) {
    commit(preferences.edit().putBoolean("audio", on));
  }

  void checkpoint(String json) { synchronized(LOCK) {
    commit(preferences.edit().putString("active", json));
   }}

  String checkpoint() { synchronized(LOCK) {
    return preferences.getString("active", "");
   }}

  void clearCheckpoint() {
    commit(preferences.edit().remove("active"));
  }

  PaceProfile profile() {
    return new PaceProfile(
        Math.round(preferences.getFloat("profileDistance", 5) * 10.0) / 10.0,
        preferences.getLong("profileSeconds", 0),
        preferences.getBoolean("speedWork", false));
  }

  void profile(PaceProfile p) {
    commit(
        preferences
            .edit()
            .putFloat("profileDistance", (float) p.distanceKm())
            .putLong("profileSeconds", p.seconds())
            .putBoolean("speedWork", p.speedWork()));
  }

  boolean paceAlerts() {
    return preferences.getBoolean("paceAlerts", true);
  }

  void paceAlerts(boolean value) {
    commit(preferences.edit().putBoolean("paceAlerts", value));
  }

  boolean skipped(String id) {
    return object("skips").optBoolean(id, false);
  }

  void skip(String id, boolean skip) { synchronized(LOCK) {
    JSONObject values = object("skips");
    try {
      values.put(id, skip);
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    commit(preferences.edit().putString("skips", values.toString()));
   }}

  private JSONObject object(String key) {
    try {
      return new JSONObject(preferences.getString(key, "{}"));
    } catch (JSONException e) {
      throw new IllegalStateException("Saved schedule cannot be read.", e);
    }
  }

  JSONObject options(){ synchronized(LOCK) {return object("options"); }}
  void options(JSONObject value){ synchronized(LOCK) {
    validateOptions(value);var editor=preferences.edit().putString("options",value.toString());JSONObject previous=options();
    boolean changed=!value.optString("mode").equals(previous.optString("mode"))||!value.optString("weekdays").equals(previous.optString("weekdays"))||value.optInt("longDay")!=previous.optInt("longDay");
    if(changed&&value.has("weekdays")&&config()!=null){
      var c=config();var proposed=preferred(c,value);Map<String,PlanEngine.Session> prior=new HashMap<>();for(var session:generated(c))prior.put(session.id(),session);List<PlanEngine.Session> finalSchedule=new ArrayList<>();Set<LocalDate> occupied=new HashSet<>();JSONObject moves=object("moves");
      for(var session:proposed){int week=Integer.parseInt(session.id().split("-")[0]);var kept=!c.start().plusWeeks(week).isAfter(LocalDate.now())?prior.get(session.id()):session;finalSchedule.add(kept);String key=planId()+":"+session.id();LocalDate moved=LocalDate.parse(moves.optString(key,kept.date().toString()));if(!moved.isBefore(LocalDate.now())&&!skipped(key)&&!completed(key)&&!occupied.add(moved))throw new IllegalArgumentException("Training days conflict with a moved workout on "+moved+". Reschedule that workout first.");}
      editor.putString("schedule",serializeSchedule(finalSchedule).toString());
    }
    commit(editor);
   }}
  private static JSONArray serializeSchedule(List<PlanEngine.Session> sessions){JSONArray out=new JSONArray();try{for(var s:sessions)out.put(new JSONObject().put("id",s.id()).put("date",s.date()).put("title",s.title()).put("km",s.km()).put("instructions",s.instructions()));}catch(JSONException e){throw new IllegalArgumentException(e);}return out;}

  private static void validateOptions(JSONObject value){
    if(value.length()>12)throw new IllegalArgumentException("Too many preference fields");
    for(Iterator<String> keys=value.keys();keys.hasNext();){String key=keys.next();if(!Set.of("mode","weekdays","longDay","strengthLevel","strengthMinutes","equipment","units").contains(key))throw new IllegalArgumentException("Unknown preference");}
    String mode=value.optString("mode","Race preparation");if(!PlanPreferences.MODES.contains(mode))throw new IllegalArgumentException("Unknown programme");
    if(value.has("weekdays")){var days=value.optJSONArray("weekdays");if(days==null||days.length()<2||days.length()>5)throw new IllegalArgumentException("Choose 2–5 weekdays");Set<Integer> unique=new HashSet<>();for(int i=0;i<days.length();i++){int day=days.optInt(i,0);if(day<1||day>7||!unique.add(day))throw new IllegalArgumentException("Invalid weekday");}if(!unique.contains(value.optInt("longDay",0)))throw new IllegalArgumentException("Choose a long-run day from your training days");}
    if(value.optInt("strengthLevel",1)<1||value.optInt("strengthLevel",1)>3||!Set.of(10,20,30).contains(value.optInt("strengthMinutes",20))||!Set.of("bodyweight","dumbbells").contains(value.optString("equipment","bodyweight"))||!Set.of("km","mi").contains(value.optString("units","km")))throw new IllegalArgumentException("Invalid training preferences");
  }
  private List<PlanEngine.Session> preferred(PlanEngine.Config c,JSONObject o){
    if(!o.has("weekdays"))return new PlanEngine().generate(c,profile());var days=o.optJSONArray("weekdays");Set<java.time.DayOfWeek> selected=new HashSet<>();for(int i=0;i<days.length();i++)selected.add(java.time.DayOfWeek.of(days.optInt(i)));
    return new PlanPreferences(o.optString("mode","Race preparation"),selected,java.time.DayOfWeek.of(o.optInt("longDay"))).generate(c,profile());
  }
  private List<PlanEngine.Session> generated(PlanEngine.Config c){
    String saved=preferences.getString("schedule","");if(saved.isEmpty())return new PlanEngine().generate(c,profile());
    try{JSONArray rows=new JSONArray(saved);List<PlanEngine.Session> out=new ArrayList<>();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);out.add(new PlanEngine.Session(row.getString("id"),LocalDate.parse(row.getString("date")),row.getString("title"),row.getDouble("km"),row.getString("instructions")));}return out;}catch(Exception e){throw new IllegalStateException("Saved training schedule could not be read",e);}
  }

  List<PlanEngine.Session> plannedSessions() {
    PlanEngine.Config c = config();
    if (c == null) return List.of();
    List<PlanEngine.Session> out = new ArrayList<>();
    JSONObject moves = object("moves");
    for (var s : generated(c)) {
      if((!profile().speedWork()||profile().seconds()==0)&&(s.title().equals("Intervals")||s.title().equals("Tempo run")))s=new PlanEngine.Session(s.id(),s.date(),"Easy run",s.km(),"Keep this run conversational; speed work is disabled in your pace profile.");
      LocalDate date =
          LocalDate.parse(moves.optString(planId() + ":" + s.id(), s.date().toString()));
      for(Run saved:runs())if(saved.sessionId().equals(planId()+":"+s.id()))date=LocalDate.parse(saved.date());
      double factor = factor(date);
      out.add(
          new PlanEngine.Session(
              s.id(),
              date,
              factor < 1 ? "Easy recovery run" : s.title(),
              Math.round(s.km() * factor * 10) / 10.0,
              factor < 1
                  ? "Keep this adjusted run easy. Walk breaks are welcome."
                  : s.instructions()));
    }
    JSONObject overrides=v103().optJSONObject("overrides");if(overrides!=null)for(int i=0;i<out.size();i++){var session=out.get(i);JSONObject o=overrides.optJSONObject(planId()+":"+session.id());if(o!=null){double ratio=completed(planId()+":"+session.id())?1:Math.min(1,factor(session.date())/o.optDouble("factor",1));out.set(i,new PlanEngine.Session(session.id(),session.date(),ratio<1?"Easy recovery run":o.optString("title",session.title()),Math.max(.1,Math.floor(o.optDouble("km",session.km())*ratio*10+.00001)/10),"Reviewed weekly change. Keep effort controlled; no catch-up mileage."));}}
    JSONObject race=v103().optJSONObject("race");
    if(race!=null&&race.optString("planId").equals(planId())){
      LocalDate target=LocalDate.parse(race.optString("date"));
      for(int i=0;i<out.size();i++){var session=out.get(i);long remaining=java.time.temporal.ChronoUnit.DAYS.between(session.date(),target);if(remaining>0&&remaining<=7&&!completed(planId()+":"+session.id())){double cap=remaining<=2?1.5:c.goalKm()<=10?2:4;if(session.km()>cap||ScheduleProposer.demanding(session.title()))out.set(i,new PlanEngine.Session(session.id(),session.date(),remaining<=2?"Optional easy shakeout":"Easy race-week run",Math.min(session.km(),cap),"Keep this race-week session conversational. Rest is welcome; no catch-up or hard effort."));}}
    }
    out.sort(Comparator.comparing(PlanEngine.Session::date));
    return out;
  }

  void move(String id, LocalDate date) { synchronized(LOCK) {
    if (date.isBefore(LocalDate.now())
        || date.isBefore(config().start())
        || !date.isBefore(config().start().plusWeeks(config().weeks())))
      throw new IllegalArgumentException("Choose a future date within your plan.");
    if (completed(id))
      throw new IllegalArgumentException("Completed workouts cannot be rescheduled.");
    for (var s : plannedSessions())
      if (!(planId() + ":" + s.id()).equals(id)
          && !skipped(planId() + ":" + s.id())
          && s.date().equals(date))
        throw new IllegalArgumentException("Another workout is already scheduled that day.");
    JSONObject moves = object("moves");
    try {
      moves.put(id, date.toString());
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
    commit(preferences.edit().putString("moves", moves.toString()));
   }}

  PaceInsights.Suggestion paceSuggestion() {
    if (resting() || factor(LocalDate.now()) < 1) return null;
    List<PaceInsights.Observation> observations = new ArrayList<>();
    List<Run> records = runs();
    LocalDate cutoff = LocalDate.now().minusDays(21);
    for (int i = records.size() - 1; i >= 0; i--) {
      var r = records.get(i);
      if (r.qualityKm() > .1
          && r.qualitySeconds() > 0
          && !LocalDate.parse(r.date()).isBefore(cutoff)
          && !LocalDate.parse(r.date()).isAfter(LocalDate.now()))
        observations.add(
            new PaceInsights.Observation(
                r.qualitySeconds() / r.qualityKm(),
                r.targetPace(),
                r.rpe(),
                r.fatigue(),
                r.pain(),
                r.benchmark()));
    }
    return new PaceInsights().suggest(profile().equivalent5k(), observations);
  }

  JSONArray strengthSessions() { synchronized(LOCK) {
    try { return new JSONArray(preferences.getString("strength", "[]")); }
    catch (JSONException e) { throw new IllegalStateException("Cannot read strength history", e); }
  }}

  void completeStrength(String routine) { synchronized(LOCK) {
    StrengthPlan.find(routine);
    try {
      JSONArray sessions = strengthSessions();
      if (sessions.length() >= 10000) throw new IllegalStateException("Export and archive strength history first");
      sessions.put(new JSONObject().put("id", UUID.randomUUID().toString())
          .put("date", LocalDate.now().toString()).put("routine", routine));
      commit(preferences.edit().putString("strength", sessions.toString()));
    } catch (JSONException e) { throw new IllegalArgumentException(e); }
   }}

  JSONObject backupPreferences() { synchronized(LOCK) {
    JSONObject out = new JSONObject(preferences.getAll());
    out.remove("active");
    if(out.has("cycling"))try{JSONObject d=new JSONObject(out.getString("cycling"));CyclingData.sanitise(d);out.put("cycling",d.toString());}catch(Exception e){throw new IllegalStateException(e);}
    if(out.has("v103"))try{JSONObject d=new JSONObject(out.getString("v103"));V103Data.sanitiseBackup(d);out.put("v103",d.toString());}catch(Exception e){throw new IllegalStateException(e);}
    if(out.has("v1"))try{JSONObject local=new JSONObject(out.getString("v1"));local.remove("events");local.put("metrics",false);out.put("v1",local.toString());}catch(Exception e){throw new IllegalStateException("V1 preferences cannot be backed up",e);}
    return out;
   }}

  static final Set<String> FLOAT_KEYS = Set.of("volume", "goal", "adaptFactor", "profileDistance");
  static final Set<String> INT_KEYS = Set.of("focusVersion", "weeks", "days");
  static final Set<String> LONG_KEYS = Set.of("profileSeconds");
  static final Set<String> BOOL_KEYS = Set.of("rest", "audio", "speedWork", "paceAlerts");
  static final Set<String> STRING_KEYS =
      Set.of("start", "planId", "runs", "adaptFrom", "adaptReason", "moves", "skips", "strength", "options", "schedule", "v1", "v103", "cycling");

  static void validatePreferences(JSONObject data) throws Exception {
    if(data.has("cycling"))CyclingData.validate(new JSONObject(data.getString("cycling")));
    if(data.has("focusVersion")&&(data.getInt("focusVersion")<0||data.getInt("focusVersion")>1))throw new IllegalArgumentException("Unsupported focus migration version");
    if(data.has("v103"))V103Data.validate(new JSONObject(data.getString("v103")));
    if(data.has("v1"))V1State.validate(new JSONObject(data.getString("v1")));
    try{if(data.has("options"))validateOptions(new JSONObject(data.getString("options")));}catch(JSONException e){throw new IllegalArgumentException("Invalid training preferences",e);}
    for (Iterator<String> keys = data.keys(); keys.hasNext(); ) {
      String k = keys.next();
      Object value = data.get(k);
      if (FLOAT_KEYS.contains(k) || INT_KEYS.contains(k) || LONG_KEYS.contains(k)) {
        if (!(value instanceof Number)
            || !Double.isFinite(((Number) value).doubleValue())
            || ((INT_KEYS.contains(k) || LONG_KEYS.contains(k))
                && ((Number) value).doubleValue() != Math.rint(((Number) value).doubleValue())))
          throw new IllegalArgumentException("Invalid numeric setting: " + k);
      } else if (BOOL_KEYS.contains(k)) {
        if (!(value instanceof Boolean)) throw new IllegalArgumentException("Invalid toggle: " + k);
      } else if (STRING_KEYS.contains(k)) {
        if (!(value instanceof String))
          throw new IllegalArgumentException("Invalid text setting: " + k);
      } else throw new IllegalArgumentException("Unknown backup setting: " + k);
    }
    if (data.has("start"))
      new PlanEngine.Config(
          data.getInt("weeks"),
          data.getInt("days"),
          data.getDouble("volume"),
          Math.round(data.getDouble("goal") * 10) / 10.0,
          LocalDate.parse(data.getString("start")));
    new PaceProfile(
        Math.round(data.optDouble("profileDistance", 5) * 10) / 10.0,
        data.optLong("profileSeconds", 0),
        data.optBoolean("speedWork", false));
    if (data.has("adaptFrom")) LocalDate.parse(data.getString("adaptFrom"));
    if (data.optDouble("adaptFactor", 1) < 0 || data.optDouble("adaptFactor", 1) > 1)
      throw new IllegalArgumentException("Invalid workload adjustment");
    JSONObject moves = new JSONObject(data.optString("moves", "{}"));
    for (Iterator<String> keys = moves.keys(); keys.hasNext(); )
      LocalDate.parse(moves.getString(keys.next()));
    new JSONObject(data.optString("skips", "{}"));
    JSONArray strength = new JSONArray(data.optString("strength", "[]"));
    if (strength.length() > 10000) throw new IllegalArgumentException("Too many strength sessions");
    Set<String> strengthIds = new HashSet<>();
    for (int i = 0; i < strength.length(); i++) {
      JSONObject row = strength.getJSONObject(i);
      if (row.getString("id").isEmpty() || !strengthIds.add(row.getString("id")))
        throw new IllegalArgumentException("Invalid strength session identity");
      LocalDate.parse(row.getString("date"));
      String routine = row.getString("routine");
      StrengthPlan.find(routine);
    }
    if(data.has("schedule")){
      JSONArray schedule=new JSONArray(data.getString("schedule"));int weeks=data.getInt("weeks"),days=data.getInt("days");LocalDate start=LocalDate.parse(data.getString("start"));Set<String> seen=new HashSet<>();if(schedule.length()!=weeks*days)throw new IllegalArgumentException("Incomplete training schedule");
      for(int i=0;i<schedule.length();i++){JSONObject row=schedule.getJSONObject(i);String id=row.getString("id");String[] pair=id.split("-");if(pair.length!=2||Integer.parseInt(pair[0])<0||Integer.parseInt(pair[0])>=weeks||Integer.parseInt(pair[1])<0||Integer.parseInt(pair[1])>=days||!seen.add(id))throw new IllegalArgumentException("Invalid schedule identity");LocalDate date=LocalDate.parse(row.getString("date"));double km=row.getDouble("km");if(date.isBefore(start)||!date.isBefore(start.plusWeeks(weeks))||!Double.isFinite(km)||km<=0||km>250||row.getString("title").length()>100||row.getString("instructions").length()>5000)throw new IllegalArgumentException("Invalid saved workout");}
    }
    JSONArray rows = new JSONArray(data.optString("runs", "[]"));
    Set<String> ids = new HashSet<>();
    if (rows.length() > 10000) throw new IllegalArgumentException("Too many runs in backup");
    for (int i = 0; i < rows.length(); i++) {
      JSONObject r = rows.getJSONObject(i);
      LocalDate.parse(r.getString("date"));
      String id = r.getString("id");
      if (id.isEmpty()
          || !ids.add(id)
          || !Double.isFinite(r.getDouble("km"))
          || r.getDouble("km") <= 0
          || r.getDouble("km") > 250
          || r.getInt("minutes") < 1
          || r.getInt("minutes") > 3000
          || r.optLong("seconds", r.getInt("minutes") * 60L) < 1
          || r.optLong("seconds", 1) > 180000
          || r.optInt("rpe", 5) < 1
          || r.optInt("rpe", 5) > 10
          || r.optInt("fatigue", 2) < 1
          || r.optInt("fatigue", 2) > 5)
        throw new IllegalArgumentException("Invalid run in backup");
      if (!Double.isFinite(r.optDouble("qualityKm", 0))
          || r.optDouble("qualityKm", 0) < 0
          || r.optDouble("qualityKm", 0) > 250
          || r.optLong("qualitySeconds", 0) < 0
          || r.optLong("qualitySeconds", 0) > 180000
          || !Double.isFinite(r.optDouble("targetPace", 0))
          || r.optDouble("targetPace", 0) < 0
          || r.optDouble("targetPace", 0) > 3600)
        throw new IllegalArgumentException("Invalid pace metrics");
      String route = r.optString("route", "");
      if (!route.isEmpty() && !route.matches("route-[a-zA-Z0-9-]+\\.jsonl"))
        throw new IllegalArgumentException("Invalid route filename");
    }
  }

  void restorePreferences(JSONObject data) throws Exception { synchronized(LOCK) {
    validatePreferences(data);
    JSONObject old = new JSONObject(preferences.getAll());old.remove("active");
    try {
      JSONObject clean=new JSONObject(data.toString());
      if(clean.has("v1")){JSONObject local=new JSONObject(clean.getString("v1"));local.remove("events");local.put("metrics",false);clean.put("v1",local.toString());}
      if(clean.has("cycling")){JSONObject d=new JSONObject(clean.getString("cycling"));CyclingData.sanitise(d);clean.put("cycling",d.toString());}
      if(clean.has("v103")){JSONObject d=new JSONObject(clean.getString("v103"));V103Data.sanitiseBackup(d);clean.put("v103",d.toString());}
      writePreferences(clean);
    } catch (RuntimeException failure) {
      writePreferences(old);
      throw failure;
    }
   }}

  private void writePreferences(JSONObject data) {
    SharedPreferences.Editor e = preferences.edit().clear();
    try {
      for (Iterator<String> keys = data.keys(); keys.hasNext(); ) {
        String k = keys.next();
        if (FLOAT_KEYS.contains(k)) e.putFloat(k, (float) data.getDouble(k));
        else if (INT_KEYS.contains(k)) e.putInt(k, data.getInt(k));
        else if (LONG_KEYS.contains(k)) e.putLong(k, data.getLong(k));
        else if (BOOL_KEYS.contains(k)) e.putBoolean(k, data.getBoolean(k));
        else e.putString(k, data.getString(k));
      }
    } catch (JSONException error) {
      throw new IllegalArgumentException(error);
    }
    commit(e);
  }

  private void commit(SharedPreferences.Editor e) {
    if (!e.commit()) throw new IllegalStateException("Could not save. Please try again.");
  }
}
