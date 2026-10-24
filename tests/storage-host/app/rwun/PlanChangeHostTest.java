package app.rwun;

import android.content.Context;
import java.io.*;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import org.json.*;

/** Storage/domain checks. Native persistence/UI is tested separately. */
public final class PlanChangeHostTest {
  static int assertions;
  static Context context;
  static RunStore store;
  static void setup() throws Exception {context=new Context(Files.createTempDirectory("openrun-plan-changes").toFile());store=new RunStore(context);store.save(new PlanEngine.Config(8,3,12,5,LocalDate.now().plusDays(1)));}
  static void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError(message);}
  interface Action {void run() throws Exception;}
  static void rejected(Action action,String message) throws Exception {String before=store.backupPreferences().toString();try{action.run();throw new AssertionError(message);}catch(IllegalArgumentException|IllegalStateException expected){}check(before.equals(store.backupPreferences().toString()),message+" leaves all stored data unchanged");}
  static PlanEngine.Session first(){return store.plannedSessions().get(0);}
  static String key(PlanEngine.Session s){return store.planId()+":"+s.id();}
  static LocalDate freeDate(){return store.config().start().plusDays(1);}
  static void move() {var s=first();String fp=store.proposalFingerprint();store.previewMove(fp,key(s),freeDate());store.move(fp,key(s),freeDate());}
  static JSONObject latest()throws Exception{return store.planChanges().getJSONObject(store.planChanges().length()-1);}
  static RunStore.Run run(String id,String date){return new RunStore.Run(id,date,2,15);}
  public static void main(String[] args)throws Exception {
    setup();check(store.planChanges().length()==0,"new and existing plans start honestly empty");
    String untouched=store.backupPreferences().toString(),fp=store.proposalFingerprint();
    check(store.previewMove(fp,key(first()),freeDate()).contains("→"),"preview compares dates and distances");
    store.previewAdaptation(fp,new AdaptiveCoach.Advice(.8,false,"High fatigue"));
    check(untouched.equals(store.backupPreferences().toString()),"previews/rejection perform zero writes");
    var before=first();int commits=context.preferenceCommits();move();
    check(context.preferenceCommits()==commits+1,"mutation and event use exactly one preferences commit");
    JSONObject e=latest(),change=e.getJSONArray("changes").getJSONObject(0);
    check(e.getString("kind").equals("Rescheduled workout")&&e.getLong("timestamp")>0&&!e.getString("evidence").isEmpty(),"timestamp, reason and evidence saved");
    check(change.getJSONObject("before").getString("date").equals(before.date().toString()),"before date is actual persisted schedule");
    check(change.getJSONObject("after").getString("date").equals(freeDate().toString()),"after date is actual saved schedule");
    check(change.getJSONObject("before").getDouble("km")==before.km(),"before distance accurate");
    check(new RunStore(context).planChanges().toString().equals(store.planChanges().toString()),"history survives new repository instance");
    String staleFingerprint=fp;
    rejected(()->store.move(staleFingerprint,key(before),freeDate().plusDays(2)),"stale move preview rejected");
    rejected(()->store.adapt(staleFingerprint,new AdaptiveCoach.Advice(.8,false,"stale")),"stale adaptation preview rejected");
    PlanChanges.Undo preview=store.previewUndo();store.add(run("free:new",LocalDate.now().toString()));
    PlanChanges.Undo staleUndo=preview;rejected(()->store.undoPlanChange(staleUndo),"stale undo review rejects newer evidence");
    store.v103(store.v103().put("reminders",V103Data.reminderDefaults().put("movement",true)));
    String runs=store.runs().toString();store.undoPlanChange(store.previewUndo());
    check(store.runs().toString().equals(runs)&&store.v103().getJSONObject("reminders").getBoolean("movement"),"undo preserves newer activity and reminder settings");
    check(first().date().equals(before.date())&&latest().getString("kind").equals("Undo"),"undo restores schedule and appends event");
    check(latest().getString("undoOf").equals(e.getString("id"))&&store.planChanges().length()==2,"undo links original event without erasing it");
    rejected(()->store.previewUndo(),"undo cannot be undone or skip back over newer events");

    setup();fp=store.proposalFingerprint();var s=first();List<ScheduleProposer.Change> proposal=List.of(new ScheduleProposer.Change(s.id(),s.date(),s.date(),"Easy recovery run",s.km()*.8,false,"User requested a lighter week"));
    String plain=store.previewProposal(fp,proposal,"");check(store.planChanges().length()==0&&plain.contains("km"),"weekly preview separate from history");
    store.applyProposal(fp,proposal);check(latest().getString("kind").equals("Weekly adjustment"),"weekly change recorded");
    var reduced=store.plannedSessions().stream().filter(x->x.id().equals(s.id())).findFirst().orElseThrow();
    check(latest().getJSONArray("changes").getJSONObject(0).getJSONObject("after").getDouble("km")==reduced.km(),"after distance reflects rounding/overrides, not requested value");
    store.undoPlanChange(store.previewUndo());check(Math.abs(first().km()-s.km())<.00001,"undo removes new override exactly");

    setup();store.adapt(store.proposalFingerprint(),new AdaptiveCoach.Advice(.8,false,"High reported effort"));
    check(latest().getString("kind").equals("Training adaptation")&&latest().getJSONArray("changes").length()>0,"adaptation includes affected workouts");
    store.undoPlanChange(store.previewUndo());check(store.factor(first().date())==1,"undo restores missing adaptation keys");

    setup();move();var target=store.plannedSessions().stream().filter(x->x.id().equals("0-0")).findFirst().orElseThrow();store.add(run(key(target),target.date().toString()));
    rejected(()->store.previewUndo(),"completed affected workout blocks undo");check(store.runs().size()==1,"completion kept");
    setup();move();target=store.plannedSessions().stream().filter(x->x.id().equals("0-0")).findFirst().orElseThrow();store.add(run("import:1",target.date().toString()));store.matchWorkout("import:1",key(target));rejected(()->store.previewUndo(),"matched completion also blocks undo");

    setup();move();preview=store.previewUndo();store.adapt(new AdaptiveCoach.Advice(0,true,"Pain reported"));
    check(store.resting()&&latest().getString("kind").equals("Recovery hold"),"hold is recorded and remains explicit");
    PlanChanges.Undo heldPreview=preview;rejected(()->store.undoPlanChange(heldPreview),"undo never clears hold");
    store.resumeTraining();check(!store.resting()&&latest().getString("kind").equals("Recovery review"),"only explicit recovery review clears hold");rejected(()->store.previewUndo(),"undo cannot reverse recovery review");

    setup();move();String oldPlan=store.planId();store.restartPlan(LocalDate.now().plusDays(2));rejected(()->store.previewUndo(),"replacement plan blocks older undo");check(!store.planId().equals(oldPlan)&&store.planChanges().length()==1,"new plan preserves existing audit trail");
    setup();move();store.checkpoint("{\"id\":\"free:pending\"}");rejected(()->store.previewUndo(),"pending recording blocks undo");store.clearCheckpoint();
    PlanChanges.Undo undoBeforeBackup=store.previewUndo();ByteArrayOutputStream zip=new ByteArrayOutputStream();BackupManager.exportTo(context,store,zip);
    String history=store.planChanges().toString();BackupManager.restoreFrom(context,store,new ByteArrayInputStream(zip.toByteArray()));
    check(store.planChanges().toString().equals(history),"portable backup retains events");rejected(()->store.undoPlanChange(undoBeforeBackup),"restore invalidates open undo reviews");store.undoPlanChange(store.previewUndo());check(store.planChanges().length()==2,"fresh undo works after compatible restore");
    JSONObject legacy=store.backupPreferences();legacy.remove("planChanges");legacy.remove("planRevision");legacy.remove("planEpoch");store.restorePreferences(legacy);check(store.planChanges().length()==0,"legacy backup yields empty history");
    setup();move();JSONObject bad=store.backupPreferences();JSONArray rows=new JSONArray(bad.getString("planChanges"));rows.getJSONObject(0).getJSONArray("changes").getJSONObject(0).getJSONObject("after").put("km",-5);bad.put("planChanges",rows.toString());rejected(()->store.restorePreferences(bad),"invalid backup history rejected before mutation");

    setup();var completed=first();store.add(run(key(completed),completed.date().toString()));var saved=first();
    store.adapt(store.proposalFingerprint(),new AdaptiveCoach.Advice(.8,false,"High fatigue after a saved run"));
    check(first().equals(saved),"adaptation preserves completed planned target");
    JSONArray actual=latest().getJSONArray("changes");for(int i=0;i<actual.length();i++)check(!actual.getJSONObject(i).getString("id").equals(saved.id()),"completed target is excluded from affected workouts");
    store.undoPlanChange(store.previewUndo());check(first().equals(saved)&&store.runs().size()==1,"undo preserves completed target and activity");

    setup();move();JSONObject forged=store.backupPreferences();JSONArray events=new JSONArray(forged.getString("planChanges"));
    events.getJSONObject(0).getJSONArray("changes").getJSONObject(0).getJSONObject("before").put("km",123);
    forged.put("planChanges",events.toString());store.restorePreferences(forged);
    rejected(()->store.previewUndo(),"inconsistent imported undo consequences rejected");
    System.out.println("Plan-change history: "+assertions+" assertions passed");
  }
}
