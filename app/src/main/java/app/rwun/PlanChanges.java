package app.rwun;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.json.*;

/** Immutable audit events and conservative undo rules, independent of UI and disk writes. */
final class PlanChanges {
  static final Set<String> KEYS=Set.of("planId","start","weeks","days","volume","goal","schedule","options","moves","skips","adaptFrom","adaptFactor","adaptReason","rest","profileDistance","profileSeconds","speedWork","protectedTargets");
  static final Set<String> FEATURES=Set.of("overrides","raceReview","race");
  record Undo(String eventId,String fingerprint,String summary) {}

  static JSONObject raw(Map<String,Object> state) {
    try {
      JSONObject result=new JSONObject();
      for(String key:KEYS)if(state.containsKey(key))result.put(key,state.get(key));
      JSONObject data=new JSONObject((String)state.getOrDefault("v103","{}")),features=new JSONObject();
      for(String key:FEATURES)if(data.has(key))features.put(key,data.get(key));
      return result.put("features",features);
    }catch(JSONException e){throw new IllegalStateException("Saved plan cannot be read",e);}
  }
  /** Keep past/completed planned targets when an adjustment would otherwise recalculate them. */
  static void protectHistory(PreferenceEdit edit) {
    try {
      RunStore before=new RunStore(PreferenceEdit.view(edit.before)),after=new RunStore(PreferenceEdit.view(edit.after));
      if(!before.planId().equals(after.planId()))return;
      JSONObject prior=workouts(edit.before),next=workouts(edit.after),targets=new JSONObject((String)edit.after.getOrDefault("protectedTargets","{}"));
      boolean changed=false;
      for(String id:V1State.keys(prior)) {
        JSONObject row=prior.getJSONObject(id);String key=before.planId()+":"+id;
        if((LocalDate.parse(row.getString("date")).isBefore(LocalDate.now())||before.completed(key))&&!canonical(row).equals(canonical(next.opt(id)))) {
          targets.put(key,new JSONObject(row.toString()));changed=true;
        }
      }
      if(changed){validateTargets(targets);edit.putString("protectedTargets",targets.toString());}
    }catch(JSONException e){throw new IllegalStateException("Completed workout targets could not be preserved",e);}
  }
  static void validateTargets(JSONObject targets) throws JSONException {
    if(targets.length()>10000)throw new IllegalArgumentException("Too many preserved workout targets");
    for(String key:V1State.keys(targets)){JSONObject row=targets.getJSONObject(key);LocalDate.parse(row.getString("date"));double km=row.getDouble("km");if(key.length()>200||!Double.isFinite(km)||km<0||km>250||row.getString("title").length()>200||row.getString("instructions").length()>5000)throw new IllegalArgumentException("Invalid preserved workout target");}
  }
  static boolean samePlan(Map<String,Object> a,Map<String,Object> b){return canonical(raw(a)).equals(canonical(raw(b)));}
  static String context(Map<String,Object> state) {
    try {
      JSONObject value=raw(state).put("revision",state.getOrDefault("planRevision","")).put("epoch",state.getOrDefault("planEpoch","")).put("today",LocalDate.now().toString());
      for(String key:List.of("runs","active"))value.put(key,state.getOrDefault(key,""));
      JSONObject data=new JSONObject((String)state.getOrDefault("v103","{}"));
      for(String key:List.of("matches","walks","indoor","activeIndoor"))if(data.has(key))value.put(key,data.get(key));
      value.put("activeCycling",new JSONObject((String)state.getOrDefault("cycling","{}")).optJSONObject("active"));
      return hash(canonical(value));
    }catch(JSONException e){throw new IllegalStateException(e);}
  }
  private static JSONObject workouts(Map<String,Object> state) throws JSONException {
    RunStore store=new RunStore(PreferenceEdit.view(state));JSONObject rows=new JSONObject();
    for(var s:store.plannedSessions())rows.put(s.id(),new JSONObject().put("id",s.id()).put("date",s.date().toString()).put("km",s.km()).put("title",s.title()).put("instructions",s.instructions()).put("skipped",store.skipped(store.planId()+":"+s.id())));
    return rows;
  }
  private static JSONArray differences(Map<String,Object> before,Map<String,Object> after) throws JSONException {
    JSONObject a=workouts(before),b=workouts(after);JSONArray out=new JSONArray();
    Set<String> ids=new TreeSet<>();ids.addAll(V1State.keys(a));ids.addAll(V1State.keys(b));
    for(String id:ids)if(!canonical(a.opt(id)).equals(canonical(b.opt(id))))out.put(new JSONObject().put("id",id).put("before",a.opt(id)).put("after",b.opt(id)));
    return out;
  }
  static String describe(PreferenceEdit edit){try{String summary=summary(differences(edit.before,edit.after),false);return summary.isEmpty()?"No workout dates or distances change.":summary;}catch(JSONException e){throw new IllegalStateException(e);}}
  static void append(PreferenceEdit edit,String kind,String reason,String evidence) {
    try {
      if(reason.isBlank())reason="Reviewed upcoming workout changes.";
      JSONArray events=new JSONArray((String)edit.before.getOrDefault("planChanges","[]"));
      if(events.length()>=10000)throw new IllegalStateException("Plan-change history is full. Export a backup before making further changes.");
      JSONObject event=new JSONObject().put("id",UUID.randomUUID().toString()).put("timestamp",System.currentTimeMillis()).put("kind",kind).put("reason",reason).put("evidence",evidence)
          .put("planId",new RunStore(PreferenceEdit.view(edit.after)).planId()).put("before",raw(edit.before)).put("after",raw(edit.after)).put("changes",differences(edit.before,edit.after)).put("revision",edit.after.getOrDefault("planRevision",""));
      if(kind.equals("Undo"))event.put("undoOf",events.getJSONObject(events.length()-1).getString("id"));
      events.put(event);validateEvent(event);edit.putString("planChanges",events.toString());
    }catch(JSONException e){throw new IllegalStateException("Plan change could not be recorded",e);}
  }
  static Undo previewUndo(Map<String,Object> state) {
    try {
      JSONArray history=new JSONArray((String)state.getOrDefault("planChanges","[]"));
      if(history.length()==0)throw new IllegalStateException("No recorded changes to undo.");
      JSONObject event=history.getJSONObject(history.length()-1);String kind=event.getString("kind");
      if(Set.of("Undo","Recovery hold","Recovery review").contains(kind))throw new IllegalStateException("This event cannot be undone. Recovery needs explicit review; undo events cannot be undone.");
      if(!event.getString("revision").equals(state.getOrDefault("planRevision",""))||!canonical(event.getJSONObject("after")).equals(canonical(raw(state))))throw new IllegalStateException("The plan has changed since this event. Newer work will be kept.");
      RunStore store=new RunStore(PreferenceEdit.view(state));
      if(store.resting())throw new IllegalStateException("Training hold remains active. Review recovery in Profile; undo cannot clear it.");
      if(!store.checkpoint().isEmpty()||store.indoorPending())throw new IllegalStateException("Finish or recover the pending recording before undo.");
      JSONArray changes=event.getJSONArray("changes");
      if(changes.length()==0)throw new IllegalStateException("This event has no workout changes to undo.");
      checkProtected(changes,store);
      PreferenceEdit candidate=new PreferenceEdit(PreferenceEdit.view(state));restoreLatest(candidate);
      // Recompute the inverse from current data. Never trust an imported event's claimed consequences.
      JSONArray inverse=differences(state,candidate.after),expected=new JSONArray();
      for(int i=0;i<changes.length();i++){JSONObject row=changes.getJSONObject(i);expected.put(new JSONObject().put("id",row.getString("id")).put("before",row.opt("after")).put("after",row.opt("before")));}
      if(!canonical(expected).equals(canonical(inverse)))throw new IllegalStateException("Undo consequences no longer match this event. Newer work will be kept.");
      checkProtected(inverse,store);
      Set<LocalDate> occupied=new HashSet<>();RunStore prior=new RunStore(PreferenceEdit.view(candidate.after));
      for(var s:prior.plannedSessions())if(!s.date().isBefore(LocalDate.now())&&!prior.skipped(prior.planId()+":"+s.id())&&!occupied.add(s.date()))throw new IllegalStateException("Undo would schedule two workouts on one day.");
      return new Undo(event.getString("id"),context(state),summary(inverse,false)+"\nSaved activities are kept. Recovery holds require explicit review.");
    }catch(JSONException e){throw new IllegalStateException("Plan-change history cannot be read",e);}
  }
  private static void checkProtected(JSONArray changes,RunStore store) throws JSONException {
    for(int i=0;i<changes.length();i++) {
      JSONObject change=changes.getJSONObject(i),a=change.optJSONObject("before"),b=change.optJSONObject("after");
      if(a==null||b==null||LocalDate.parse(a.getString("date")).isBefore(LocalDate.now())||LocalDate.parse(b.getString("date")).isBefore(LocalDate.now())||store.completed(store.planId()+":"+change.getString("id")))throw new IllegalStateException("An affected workout is completed, past or no longer available. Its history is protected.");
    }
  }
  static void restoreLatest(PreferenceEdit edit) {
    try {
      JSONArray history=new JSONArray((String)edit.before.getOrDefault("planChanges","[]"));JSONObject event=history.getJSONObject(history.length()-1),before=event.getJSONObject("before"),after=event.getJSONObject("after");
      for(String key:KEYS)if(!canonical(before.opt(key)).equals(canonical(after.opt(key)))) {
        if(key.equals("rest")){if(before.optBoolean("rest")!=after.optBoolean("rest"))throw new IllegalStateException("Undo cannot change a recovery hold.");continue;}
        if(!before.has(key))edit.remove(key);else put(edit,key,before.get(key));
      }
      JSONObject data=new JSONObject((String)edit.after.getOrDefault("v103","{}"));
      for(String key:FEATURES)if(!canonical(before.getJSONObject("features").opt(key)).equals(canonical(after.getJSONObject("features").opt(key)))) {
        if(before.getJSONObject("features").has(key))data.put(key,before.getJSONObject("features").get(key));else data.remove(key);
      }
      edit.putString("v103",data.toString());
    }catch(JSONException e){throw new IllegalStateException(e);}
  }
  private static void put(PreferenceEdit edit,String key,Object value) {
    if(RunStore.FLOAT_KEYS.contains(key))edit.putFloat(key,((Number)value).floatValue());
    else if(RunStore.INT_KEYS.contains(key))edit.putInt(key,((Number)value).intValue());
    else if(RunStore.LONG_KEYS.contains(key))edit.putLong(key,((Number)value).longValue());
    else if(RunStore.BOOL_KEYS.contains(key))edit.putBoolean(key,(Boolean)value);
    else edit.putString(key,(String)value);
  }
  static String summary(JSONArray changes,boolean reverse) {
    StringBuilder text=new StringBuilder();
    try {for(int i=0;i<changes.length();i++) {
      JSONObject row=changes.getJSONObject(i),a=row.optJSONObject(reverse?"after":"before"),b=row.optJSONObject(reverse?"before":"after");
      String beforeTitle=a==null?"Workout":a.optString("title","Workout"),afterTitle=b==null?"Workout":b.optString("title","Workout");
      text.append(beforeTitle);
      if(!beforeTitle.equals(afterTitle))text.append(" → ").append(afterTitle);
      text.append('\n').append(label(a)).append(" → ").append(label(b)).append('\n');
      String beforeGuidance=a==null?"":a.optString("instructions"),afterGuidance=b==null?"":b.optString("instructions");
      if(!beforeGuidance.equals(afterGuidance))text.append("Guidance before: ").append(beforeGuidance.isBlank()?"None recorded":beforeGuidance).append('\n').append("Guidance after: ").append(afterGuidance.isBlank()?"None recorded":afterGuidance).append('\n');
    }}catch(JSONException e){throw new IllegalStateException(e);}return text.toString().trim();
  }
  private static String label(JSONObject row){return row==null?"Not scheduled":String.format(Locale.ROOT,"%s · %.1f km%s",row.optString("date"),row.optDouble("km"),row.optBoolean("skipped")?" · skipped":"");}
  static void validate(JSONArray events) throws JSONException {
    if(events.length()>10000)throw new IllegalArgumentException("Too many plan-change events");Set<String> ids=new HashSet<>();
    for(int i=0;i<events.length();i++){JSONObject event=events.getJSONObject(i);validateEvent(event);if(!ids.add(event.getString("id")))throw new IllegalArgumentException("Duplicate plan-change identity");if(event.getString("kind").equals("Undo")&&(i==0||!event.optString("undoOf").equals(events.getJSONObject(i-1).getString("id"))))throw new IllegalArgumentException("Invalid undo reference");}
  }
  private static void validateEvent(JSONObject event) throws JSONException {
    for(String key:List.of("id","kind","reason","evidence","planId","revision"))if(!(event.get(key) instanceof String)||event.getString(key).isBlank()||event.getString(key).length()>12000)throw new IllegalArgumentException("Invalid plan-change "+key);
    if(!(event.get("timestamp") instanceof Number)||event.getLong("timestamp")<0)throw new IllegalArgumentException("Invalid change timestamp");
    for(String side:List.of("before","after")) {
      JSONObject state=event.getJSONObject(side),features=state.getJSONObject("features"),check=new JSONObject(state.toString());check.remove("features");
      for(String k:V1State.keys(state))if(!KEYS.contains(k)&&!k.equals("features"))throw new IllegalArgumentException("Unknown plan snapshot field");
      for(String k:V1State.keys(features))if(!FEATURES.contains(k))throw new IllegalArgumentException("Unknown snapshot feature");
      try{RunStore.validatePreferences(check);V103Data.validate(features);}catch(Exception e){throw new IllegalArgumentException("Invalid plan snapshot",e);}
    }
    JSONArray changes=event.getJSONArray("changes");if(changes.length()>520)throw new IllegalArgumentException("Too many changed workouts");Set<String> ids=new HashSet<>();
    for(int i=0;i<changes.length();i++) {
      JSONObject row=changes.getJSONObject(i);String id=row.getString("id");if(!ids.add(id))throw new IllegalArgumentException("Duplicate changed workout");
      for(String side:List.of("before","after"))if(!row.isNull(side)) {JSONObject s=row.getJSONObject(side);LocalDate.parse(s.getString("date"));double km=s.getDouble("km");if(!id.equals(s.getString("id"))||!Double.isFinite(km)||km<0||km>250||!(s.get("skipped") instanceof Boolean)||s.getString("title").length()>200||s.getString("instructions").length()>5000)throw new IllegalArgumentException("Invalid changed workout");}
    }
  }
  static String canonical(Object value) {
    if(value==null||value==JSONObject.NULL)return "null";
    if(value instanceof JSONObject){JSONObject object=(JSONObject)value;List<String> keys=new ArrayList<>(V1State.keys(object));Collections.sort(keys);StringBuilder s=new StringBuilder("{");for(String k:keys)s.append(JSONObject.quote(k)).append(':').append(canonical(object.opt(k))).append(',');return s.append('}').toString();}
    if(value instanceof JSONArray){JSONArray rows=(JSONArray)value;StringBuilder s=new StringBuilder("[");for(int i=0;i<rows.length();i++)s.append(canonical(rows.opt(i))).append(',');return s.append(']').toString();}
    if(value instanceof Number)return new java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
    if(value instanceof Boolean)return value.toString();
    return JSONObject.quote(value.toString());
  }
  private static String hash(String value){try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
}
