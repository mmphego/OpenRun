package app.rwun;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
import org.json.*;

/** Bring-your-own-key setup, evidence-based coaching, and reviewable AI plan changes. */
public final class AiCoachActivity extends Activity {
  private final UiKit.Palette palette=new UiKit.Palette(true);
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private LinearLayout body;private TextView status,answer;private Spinner provider;
  private EditText model,key,goal,question;private CheckBox consent,automatic,notifications;
  private AiCoachSettings vault;private RunStore store;private JSONObject saved;
  private String pendingSettingsRevision="";
  private boolean busy;private AiCoachPolicy.Request pending;private AiCoachPolicy.Proposal proposal;
  public void onCreate(Bundle state){super.onCreate(state);vault=new AiCoachSettings(this);store=new RunStore(this);show();}
  protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
  private TextView text(String value,int size,boolean bold){TextView label=UiKit.label(this,value,size,bold?palette.ink:palette.muted,bold);body.addView(label);return label;}
  private EditText field(String caption,String value){text(caption,14,true);EditText input=new EditText(this);input.setTextColor(palette.ink);input.setHintTextColor(palette.muted);input.setText(value);input.setContentDescription(caption);input.setSingleLine(true);body.addView(input,new LinearLayout.LayoutParams(-1,-2));return input;}
  private CheckBox check(String caption,boolean value){CheckBox box=new CheckBox(this);box.setText(caption);box.setTextColor(palette.ink);box.setChecked(value);body.addView(box);return box;}
  private interface Action {void run()throws Exception;}
  private void action(String caption,Action action){body.addView(UiKit.action(this,palette,caption,()->{try{if(busy)throw new IllegalStateException("Wait for the current coach request to finish.");action.run();}catch(Exception e){status.setText(e.getMessage());}}));}
  private void show(){
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(palette.background);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
    LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(UiKit.iconButton(this,palette,R.drawable.ic_arrow_back,"Back",this::finish),new LinearLayout.LayoutParams(UiKit.dp(this,56),UiKit.dp(this,56)));header.addView(UiKit.label(this,"AI Coach",21,palette.ink,true));root.addView(header);
    ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=UiKit.dp(this,20);body.setPadding(pad,pad,pad,pad);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    text("Your runs. Your goal. Your coach.",28,true);
    text("Use your own OpenAI or Google Gemini API key to personalise your running plan and ask your coach questions. API usage is billed by the provider; a ChatGPT subscription does not supply an API key.",16,false);
    try{saved=vault.read();}catch(RuntimeException e){saved=new JSONObject();text(e.getMessage(),16,true);}
    text("Provider",14,true);provider=new Spinner(this);provider.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"OpenAI","Gemini"}));provider.setSelection(saved.optString("provider").equals("Gemini")?1:0);body.addView(provider);
    model=field("Model ID",saved.optString("model","gpt-5-mini"));
    provider.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){private boolean initial=true;public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){if(initial){initial=false;return;}model.setText(position==0?"gpt-5-mini":"gemini-3.8-flash");key.setText("");key.setHint("Enter the selected provider's API key");}});
    key=field("API key (stored encrypted on this device)","");key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setSaveEnabled(false);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);key.setHint(saved.has("key")?"Saved key · leave blank to keep":"Enter API key");
    goal=field("Goal pace · min:sec per km (optional)",saved.optInt("goalPace")>0?PaceProfile.format(saved.optInt("goalPace")):"");
    text(store.config()==null?"Choose your goal distance and training days in Create my plan first.":"Goal distance: "+PlanEngine.distance(store.config().goalKm())+" · "+store.runningEvidence(),15,false);
    action("Create or change my running goal",()->startActivity(new Intent(this,RacePlanActivity.class)));
    consent=check("I agree to send recent run dates, distance, duration, pace, effort, fatigue, pain flags, goal and upcoming workouts to my selected AI provider. Routes and heart-rate records are excluded. Questions I type are also sent.",saved.optBoolean("consent"));
    automatic=check("After each saved or imported run, automatically review and update upcoming easy runs within the app's adjustment limits.",saved.optBoolean("automatic"));
    notifications=check("Notify me when a post-run review completes or needs attention.",saved.optBoolean("notifications"));
    text("Automatic reviews require internet and run when Android permits. Offline or failed requests keep the saved plan. AI cannot clear recovery holds. You can inspect plan changes and undo an eligible update.",14,false);
    status=text(saved.optString("lastResult","Set up your provider to begin."),15,true);
    action("Save AI Coach settings",this::save);
    action("Remove key and turn off AI Coach",()->{synchronized(RunStore.LOCK){vault.clear();AiCoachJob.cancel(this);}pending=null;proposal=null;show();});
    action("Generate my AI running plan",()->generate(null));
    question=field("Ask your running coach","");question.setSingleLine(false);question.setMaxLines(6);question.setSaveEnabled(false);
    action("Ask coach",()->{String value=question.getText().toString().trim();if(value.isEmpty()||value.length()>1000)throw new IllegalArgumentException("Enter a coaching question of up to 1,000 characters.");generate(value);});
    answer=text("",17,false);
    action("Review and apply this AI plan",this::review);
    action("View plan changes and undo",()->startActivity(new Intent(this,PlanChangesActivity.class)));
    action("Offline Coach · local model",()->startActivity(new Intent(this,OfflineCoachActivity.class)));
    text("AI guidance may be inaccurate. Targets are estimates: effort, recovery and pain take priority. Model access depends on your provider account; you can edit the model ID above.",13,false);
  }
  private void save()throws Exception {
    if(!consent.isChecked())throw new IllegalArgumentException("Enable the data-sharing consent to use AI Coach, or remove the key to turn it off.");
    String selected=provider.getSelectedItem().toString(),secret=key.getText().toString().trim();
    if(secret.isEmpty()&&selected.equals(saved.optString("provider")))secret=saved.optString("key");
    String pace=goal.getText().toString().trim();int seconds=0;
    if(!pace.isEmpty()){if(!pace.matches("[0-9]{1,2}:[0-5][0-9]"))throw new IllegalArgumentException("Enter goal pace as min:sec per km, for example 6:30.");String[] parts=pace.split(":");seconds=Integer.parseInt(parts[0])*60+Integer.parseInt(parts[1]);}
    synchronized(RunStore.LOCK){vault.save(selected,model.getText().toString().trim(),secret,seconds,automatic.isChecked(),notifications.isChecked());AiCoachJob.cancel(this);}
    saved=vault.read();key.setText("");key.setHint("Saved key · leave blank to keep");pending=null;proposal=null;answer.setText("");status.setText("Settings saved. Generate a plan or ask your coach. "+(notifications.isChecked()&&!Reminders.allowed(this)?"Notifications are currently blocked in Android settings.":""));
    if(notifications.isChecked()&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},84);
    AiCoachJob.schedule(this);
  }
  private void generate(String question){
    if(!consent.isChecked())throw new IllegalStateException("Enable and save data-sharing consent first.");
    if(!vault.enabled())throw new IllegalStateException("Save your API key and data-sharing consent first.");
    busy=true;pending=null;proposal=null;status.setText("Coach is reviewing your recent runs…");answer.setText("");
    worker.execute(()->{try{
      JSONObject settings=vault.read();AiCoachPolicy.Request request=AiCoachPolicy.snapshot(store,settings,false);
      AiCoachPolicy.Proposal result=AiCoachPolicy.validate(request,AiCoachClient.generate(settings,AiCoachPolicy.prompt(request,question)));
      synchronized(RunStore.LOCK){if(!settings.optString("revision").equals(vault.read().optString("revision")))throw new IllegalStateException("AI settings changed. Generate again.");vault.result(result.explanation());}
      runOnUiThread(()->{if(isDestroyed())return;busy=false;if(question==null){pending=request;proposal=result;pendingSettingsRevision=settings.optString("revision");}answer.setText(result.explanation()+"\n\n"+summary(result));status.setText(question==null?"Plan proposal ready. Review it before applying.":"Coach answer ready. Your plan is unchanged.");});
    }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){busy=false;status.setText(e instanceof java.io.IOException?e.getMessage():"Coach could not complete a valid proposal. "+e.getMessage());}});}});
  }
  private String summary(AiCoachPolicy.Proposal result){StringBuilder out=new StringBuilder();for(var s:result.sessions())out.append(s.date()).append(" · ").append(PlanEngine.distance(s.km())).append("\n").append(s.instructions()).append("\n\n");return out.length()==0?"No workout changes proposed.":out.toString();}
  private void review(){
    if(pending==null||proposal==null)throw new IllegalStateException("Generate a fresh plan proposal first.");
    new AlertDialog.Builder(this).setTitle("Apply AI running plan?").setMessage(proposal.explanation()+"\n\n"+summary(proposal)).setNegativeButton("Keep current plan",null).setPositiveButton("Apply",(dialog,which)->{try{
      synchronized(RunStore.LOCK){if(!vault.enabled()||!pendingSettingsRevision.equals(vault.read().optString("revision")))throw new IllegalStateException("AI settings changed. Generate a fresh proposal.");store.applyAiPlan(pending.fingerprint(),proposal.sessions(),proposal.explanation(),saved.getString("provider"),pending.runToken());}
      Reminders.schedule(this);pending=null;proposal=null;status.setText("AI plan saved. View changes and undo to inspect the update.");
    }catch(Exception e){status.setText(e.getMessage());}}).show();
  }
}
