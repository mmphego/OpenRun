package app.rwun;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** A goal-first coaching home, with provider configuration in its own destination. */
public final class AiCoachActivity extends Activity {
  private final UiKit.Palette palette=CoachUi.P;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final List<View> requestActions=new ArrayList<>();
  private LinearLayout body;private TextView status,answer,responseLabel;private ProgressBar progress;private Button reviewAction;
  private EditText model,key,goal,question;private CheckBox consent;private CompoundButton automatic,notifications;
  private String selectedProvider="OpenAI",fallbackPolicy="keep",pendingSettingsRevision="",shownPlanId="",shownSettingsRevision="";
  private AiCoachSettings vault;private RunStore store;private JSONObject saved;
  private boolean busy,connectionsOnly;private AiCoachPolicy.Request pending;private AiCoachPolicy.Proposal proposal;
  private interface Action {void run()throws Exception;}
  public void onCreate(Bundle state){super.onCreate(state);vault=new AiCoachSettings(this);store=new RunStore(this);connectionsOnly="connections".equals(getIntent().getStringExtra("screen"));show();}
  protected void onResume(){
    super.onResume();if(store==null||busy)return;
    String revision;try{revision=vault.read().optString("revision");}catch(RuntimeException unavailable){revision="unavailable";}
    if(!shownPlanId.equals(store.config()==null?"":store.planId())||!shownSettingsRevision.equals(revision)){pending=null;proposal=null;show();}
  }
  protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
  private void run(Action operation){try{if(busy)throw new IllegalStateException("Wait for the current coach request to finish.");operation.run();}catch(Exception e){status.setText(e.getMessage());reveal(status);}}
  private Button action(LinearLayout parent,String caption,boolean primary,Action operation){
    Button b=CoachUi.addButton(parent,caption,primary,()->run(operation));requestActions.add(b);return b;
  }
  private void openSettings(){startActivity(new Intent(this,AiCoachActivity.class).putExtra("screen","connections"));}
  private void newAiPlan(){startActivity(new Intent(this,RacePlanActivity.class).putExtra("planningMethod","ai"));}
  private void statusArea(LinearLayout parent){
    LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);
    progress=new ProgressBar(this);progress.setIndeterminateTintList(ColorStateList.valueOf(palette.primary));progress.setContentDescription("Waiting for AI response");progress.setVisibility(View.GONE);
    LinearLayout.LayoutParams spinner=new LinearLayout.LayoutParams(CoachUi.dp(this,24),CoachUi.dp(this,24));spinner.setMargins(0,0,CoachUi.dp(this,12),0);line.addView(progress,spinner);
    status=UiKit.status(this,palette);status.setTextSize(14);line.addView(status,new LinearLayout.LayoutParams(0,-2,1));parent.addView(line);
    status.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable s){refreshActions();}});
  }
  private void refreshActions(){
    if(progress!=null)progress.setVisibility(busy?View.VISIBLE:View.GONE);
    for(View action:requestActions)action.setEnabled(!busy);
    if(reviewAction!=null)reviewAction.setVisibility(!busy&&pending!=null&&proposal!=null?View.VISIBLE:View.GONE);
  }
  private void show(){
    String draftQuestion=question==null?"":question.getText().toString();
    requestActions.clear();reviewAction=null;question=null;answer=null;progress=null;status=null;
    shownPlanId=store.config()==null?"":store.planId();String protectedError="";
    try{saved=vault.read();}catch(RuntimeException e){saved=new JSONObject();protectedError=e.getMessage();}
    shownSettingsRevision=protectedError.isEmpty()?saved.optString("revision"):"unavailable";
    LinearLayout root=CoachUi.column(this);root.setBackgroundColor(palette.background);
    root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
    getWindow().setStatusBarColor(palette.background);getWindow().setNavigationBarColor(palette.background);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    root.addView(CoachUi.header(this,connectionsOnly?"AI Connections":"AI Coach",connectionsOnly?"Your key. Your choice.":saved.optBoolean("consent")&&!saved.optString("key").isBlank()?saved.optString("provider")+" configured":"",this::finish,connectionsOnly?null:this::openSettings));
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
    body=CoachUi.column(this);body.setPadding(CoachUi.dp(this,20),CoachUi.dp(this,16),CoachUi.dp(this,20),CoachUi.dp(this,28));
    scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    if(connectionsOnly)settings(root);else home(draftQuestion);
    if(!protectedError.isEmpty())status.setText(protectedError);
    refreshActions();
  }
  private void home(String draftQuestion){
    boolean configured=saved.optBoolean("consent")&&!saved.optString("key").isBlank();
    if(!configured){CoachUi.row(body,R.drawable.ic_settings,"Connect your AI coach","Add your API key once. Keep control of your data.",this::openSettings);CoachUi.gap(body,12);}
    if(store.resting()){
      LinearLayout recovery=CoachUi.card(body,false);CoachUi.copy(recovery,"Recovery comes first",21,true);CoachUi.gap(recovery,8);
      CoachUi.copy(recovery,"Your training hold is active. Questions are available; AI cannot clear the hold or change your workouts.",14,false);
      action(recovery,"Open recovery check-in",false,()->startActivity(new Intent(this,DailyHealthActivity.class).putExtra("screen","checkin")));
    }
    LinearLayout plan=CoachUi.card(body,true);
    if(store.config()==null){
      CoachUi.copy(plan,"A goal worth running for.",27,true);CoachUi.gap(plan,10);
      CoachUi.copy(plan,"Choose your distance and pace. Build from your recent runs, around your week.",15,false);CoachUi.gap(plan,14);
      action(plan,"Create my AI running plan",true,this::newAiPlan);
      CoachUi.copy(plan,"No existing plan is required.",12,false);
    }else{
      CoachUi.copy(plan,"YOUR RUNNING GOAL",11,true);CoachUi.gap(plan,16);
      int pace=store.goalPace(saved);
      CoachUi.metrics(plan,new String[]{PlanEngine.distance(store.config().goalKm()).replace(" km",""),pace>0?PaceProfile.format(pace):"Easy"},
          new String[]{"km",pace>0?"min / km":"effort first"},new String[]{"Target distance","Target pace"});
      CoachUi.gap(plan,20);
      TrainingProgress.Snapshot snapshot=TrainingProgress.from(store,LocalDate.now());
      CoachUi.copy(plan,snapshot.phase()+" · "+store.config().days()+" runs / week",13,false);
      CoachUi.gap(plan,12);
      ProgressBar adherence=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);adherence.setMax(Math.max(1,snapshot.plannedWorkouts()));adherence.setProgress(snapshot.completedWorkouts());adherence.setProgressTintList(ColorStateList.valueOf(palette.primary));
      adherence.setContentDescription(snapshot.completedWorkouts()+" of "+snapshot.plannedWorkouts()+" due workouts completed");plan.addView(adherence,new LinearLayout.LayoutParams(-1,CoachUi.dp(this,5)));CoachUi.gap(plan,8);
      CoachUi.copy(plan,snapshot.plannedWorkouts()==0?"No workouts due yet":snapshot.completedWorkouts()+" of "+snapshot.plannedWorkouts()+" due workouts completed",13,false);CoachUi.gap(plan,16);
      if(store.resting())CoachUi.copy(plan,"AI plan reviews are paused during your recovery hold.",13,false);
      else if(configured)action(plan,"Review my plan with AI",true,this::generate);
      else action(plan,"Set up AI reviews",true,this::openSettings);
      CoachUi.row(plan,R.drawable.ic_monitoring,"Progress & next run","Planned versus actual",()->startActivity(new Intent(this,TrainingActivity.class).putExtra("screen","progress")));
    }
    LinearLayout ask=CoachUi.card(body,false);CoachUi.copy(ask,"Ask your coach",22,true);CoachUi.gap(ask,8);
    CoachUi.copy(ask,"Understand your runs. Decide what comes next.",14,false);CoachUi.gap(ask,16);
    question=CoachUi.field(ask,"Ask your running coach",draftQuestion,"What would you like to work on?",false);question.setSingleLine(false);question.setMinLines(2);question.setMaxLines(6);question.setSaveEnabled(false);requestActions.add(question);
    LinearLayout suggestions=new LinearLayout(this);suggestions.setOrientation(getResources().getConfiguration().fontScale>1.3?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
    for(String suggestion:new String[]{"Break down my recent runs","How can I improve?"}){
      Button b=CoachUi.button(this,suggestion,false,()->{question.setText(suggestion);question.requestFocus();reveal(question);});b.setTextSize(12);requestActions.add(b);
      LinearLayout.LayoutParams lp=suggestions.getOrientation()==LinearLayout.HORIZONTAL?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,CoachUi.dp(this,6),CoachUi.dp(this,6));suggestions.addView(b,lp);
    }
    ask.addView(suggestions);
    action(ask,"Ask coach",true,()->{String value=question.getText().toString().trim();if(value.isEmpty()||value.length()>1000)throw new IllegalArgumentException("Enter a coaching question of up to 1,000 characters.");ask(value);});
    CoachUi.gap(ask,12);statusArea(ask);CoachUi.gap(ask,8);
    responseLabel=CoachUi.copy(ask,"",11,true);responseLabel.setTextColor(palette.primary);responseLabel.setVisibility(View.GONE);
    answer=CoachUi.copy(ask,"",16,false);answer.setTextColor(palette.ink);answer.setTextIsSelectable(true);answer.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);answer.setVisibility(View.GONE);
    answer.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){answer.setVisibility(s.length()==0?View.GONE:View.VISIBLE);responseLabel.setVisibility(s.length()==0?View.GONE:View.VISIBLE);}public void afterTextChanged(Editable e){}});
    reviewAction=action(ask,"Review proposed changes",true,this::review);reviewAction.setVisibility(View.GONE);
    CoachUi.copy(ask,"Questions use recent run summaries. Your plan only changes after review, unless automatic reviews are enabled.",12,false);
    if(store.config()!=null){
      CoachUi.row(body,R.drawable.ic_calendar_month,"Create a new AI goal plan","Keep saved activities when replacing a plan",this::newAiPlan);
      CoachUi.row(body,R.drawable.ic_refresh,"View plan changes and undo","See what changed and why",()->startActivity(new Intent(this,PlanChangesActivity.class)));
    }else CoachUi.row(body,R.drawable.ic_calendar_month,"Build a standard plan","Available offline, without an API key",()->startActivity(new Intent(this,RacePlanActivity.class)));
    if(!saved.optString("lastResult").isBlank()){
      LinearLayout latest=CoachUi.card(body,false);CoachUi.copy(latest,"LATEST COACH REVIEW",11,true);CoachUi.gap(latest,8);
      TextView excerpt=CoachUi.copy(latest,saved.optString("lastResult"),14,false);excerpt.setMaxLines(3);excerpt.setEllipsize(TextUtils.TruncateAt.END);
      CoachUi.row(latest,R.drawable.ic_info,"Read the review","",()->showInfo("Latest coach review",saved.optString("lastResult")));
    }
    CoachUi.row(body,R.drawable.ic_info,"About AI coaching","Privacy, provider costs and training limits",()->showInfo("About AI coaching","Your selected provider receives recent running dates, distance, duration, pace, effort, fatigue, pain flags, goal and upcoming workouts after consent. Routes and heart-rate records are excluded. Typed questions are also sent. API usage is billed by your provider. AI can be inaccurate. Recorded ability, effort and recovery take priority; goal achievement is not guaranteed. Automatic reviews are opt-in and Android controls their timing. Holds cannot be cleared by AI."));
  }
  private void showInfo(String title,String message){
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Done",null).create();dialog.show();CoachUi.styleDialog(dialog);
  }
  private CheckBox consent(LinearLayout parent){
    CheckBox box=new CheckBox(this);box.setText("I agree to send recent run dates, distance, duration, pace, effort, fatigue, pain flags, goal and upcoming workouts to my selected AI provider. Routes and heart-rate records are excluded. Questions I type are also sent.");
    box.setTextColor(palette.ink);box.setTextSize(14);box.setButtonTintList(ColorStateList.valueOf(palette.primary));box.setChecked(saved.optBoolean("consent"));box.setMinimumHeight(CoachUi.dp(this,48));parent.addView(box);return box;
  }
  private CompoundButton toggle(LinearLayout parent,String title,String detail,boolean checked){
    Switch control=new Switch(this);control.setText(title);control.setTextColor(palette.ink);control.setTextSize(16);control.setChecked(checked);control.setMinimumHeight(CoachUi.dp(this,56));control.setPadding(0,CoachUi.dp(this,8),0,CoachUi.dp(this,8));
    control.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{palette.primary,palette.muted}));
    parent.addView(control,new LinearLayout.LayoutParams(-1,-2));CoachUi.copy(parent,detail,13,false);CoachUi.gap(parent,12);return control;
  }
  private void settings(LinearLayout root){
    selectedProvider=saved.optString("provider","OpenAI");fallbackPolicy=saved.optString("fallback","keep");
    LinearLayout connection=CoachUi.card(body,false);CoachUi.copy(connection,"Connect your coach",25,true);CoachUi.gap(connection,8);
    CoachUi.copy(connection,"Your API key is encrypted on this device. Provider usage is billed separately.",14,false);CoachUi.gap(connection,20);
    CoachUi.copy(connection,"Provider",14,true);CoachUi.gap(connection,10);LinearLayout choices=new LinearLayout(this);connection.addView(choices);
    List<Button> providers=new ArrayList<>();
    for(String name:new String[]{"OpenAI","Gemini"}){
      Button b=CoachUi.button(this,name,name.equals(selectedProvider),()->{});providers.add(b);requestActions.add(b);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(0,0,CoachUi.dp(this,8),0);choices.addView(b,lp);
      b.setSelected(name.equals(selectedProvider));b.setOnClickListener(v->{if(name.equals(selectedProvider))return;selectedProvider=name;model.setText(name.equals("OpenAI")?"gpt-5-mini":"gemini-3.8-flash");key.setText("");key.setHint("Enter the selected provider's API key");for(Button option:providers){boolean active=option.getText().toString().equals(name);option.setSelected(active);option.setTextColor(active?palette.onPrimary:palette.ink);option.setBackground(UiKit.ripple(this,active?palette.primary:palette.background,16));}});
    }
    CoachUi.gap(connection,20);
    model=CoachUi.field(connection,"Model ID",saved.optString("model","gpt-5-mini"),"Provider model ID",false);
    key=CoachUi.field(connection,"API key","",saved.has("key")?"Saved key · leave blank to keep":"Enter API key",false);
    key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setSaveEnabled(false);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
    CoachUi.copy(connection,"Changing provider requires that provider's key. Save edits before testing.",12,false);
    LinearLayout privacy=CoachUi.card(body,false);CoachUi.copy(privacy,"Data sharing",20,true);CoachUi.gap(privacy,12);consent=consent(privacy);
    LinearLayout reviews=CoachUi.card(body,false);CoachUi.copy(reviews,"After your runs",20,true);CoachUi.gap(reviews,8);
    automatic=toggle(reviews,"Automatic plan reviews","Review upcoming easy runs after a saved or imported run. Android controls when the review runs.",saved.optBoolean("automatic"));
    notifications=toggle(reviews,"Review notifications","Get a private notification when a review finishes or needs attention.",saved.optBoolean("notifications"));
    CoachUi.row(reviews,R.drawable.ic_favorite,"Recovery stays protected","AI cannot clear a hold. Failed requests keep your saved plan.",null);
    LinearLayout advanced=CoachUi.card(body,false);CoachUi.copy(advanced,"When AI is unavailable",20,true);CoachUi.gap(advanced,10);
    RadioGroup failures=new RadioGroup(this);String[] policies={"keep","retry","pause"},captions={"Keep current plan","Retry automatically","Pause AI reviews"};
    for(int i=0;i<policies.length;i++){
      final String policy=policies[i];RadioButton choice=new RadioButton(this);choice.setText(captions[i]);choice.setTextColor(palette.ink);choice.setTextSize(14);choice.setMinimumHeight(CoachUi.dp(this,48));choice.setButtonTintList(ColorStateList.valueOf(palette.primary));choice.setId(View.generateViewId());requestActions.add(choice);failures.addView(choice);choice.setChecked(policy.equals(fallbackPolicy));choice.setOnCheckedChangeListener((v,on)->{if(on)fallbackPolicy=policy;});
    }
    advanced.addView(failures);CoachUi.gap(advanced,16);
    goal=CoachUi.field(advanced,"Default goal pace (optional)",saved.optInt("goalPace")>0?PaceProfile.format(saved.optInt("goalPace")):"","For example 6:30 min/km",false);
    CoachUi.copy(advanced,"A new AI plan asks for its own distance and pace. Changing this default does not rewrite a saved goal.",12,false);
    for(View field:new View[]{model,key,goal,consent,automatic,notifications})requestActions.add(field);
    CoachUi.row(body,R.drawable.ic_delete,"Remove key and turn off AI Coach","Saved activities and plans stay on this device",()->{
      AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Turn off AI Coach?").setMessage("Remove the saved key and cancel automatic reviews? Your saved activities, goals and plan remain.")
          .setNegativeButton("Keep AI Coach",null).setPositiveButton("Remove key",(d,w)->run(()->{synchronized(RunStore.LOCK){vault.clear();AiCoachJob.cancel(this);}pending=null;proposal=null;show();})).create();dialog.show();CoachUi.styleDialog(dialog);
    });
    LinearLayout footer=CoachUi.column(this);footer.setPadding(CoachUi.dp(this,20),CoachUi.dp(this,8),CoachUi.dp(this,20),CoachUi.dp(this,12));footer.setBackgroundColor(palette.surface);
    statusArea(footer);action(footer,"Save AI Coach settings",true,this::save);action(footer,"Test AI connection",false,this::testConnection);
    root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
  }
  private void save()throws Exception {
    if(!consent.isChecked())throw new IllegalArgumentException("Enable the data-sharing consent to use AI Coach, or remove the key to turn it off.");
    String selected=selectedProvider,secret=key.getText().toString().trim();
    if(secret.isEmpty()&&selected.equals(saved.optString("provider")))secret=saved.optString("key");
    String pace=goal.getText().toString().trim();int seconds=0;
    if(!pace.isEmpty()){if(!pace.matches("[0-9]{1,2}:[0-5][0-9]"))throw new IllegalArgumentException("Enter goal pace as min:sec per km, for example 6:30.");String[] parts=pace.split(":");seconds=Integer.parseInt(parts[0])*60+Integer.parseInt(parts[1]);}
    synchronized(RunStore.LOCK){vault.save(selected,model.getText().toString().trim(),secret,seconds,automatic.isChecked(),notifications.isChecked(),fallbackPolicy);AiCoachJob.cancel(this);}
    saved=vault.read();key.setText("");key.setHint("Saved key · leave blank to keep");pending=null;proposal=null;if(answer!=null)answer.setText("");status.setText("Settings saved. "+(notifications.isChecked()&&!Reminders.allowed(this)?"Notifications are currently blocked in Android settings.":""));
    if(notifications.isChecked()&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},84);
    AiCoachJob.schedule(this);
  }
  private AiCoachClient.RetryListener retryProgress(JSONObject expected){
    return new AiCoachClient.RetryListener(){
      public void beforeAttempt(){
        JSONObject current=vault.read();
        if(!current.optBoolean("consent")||!expected.optString("revision").equals(current.optString("revision")))
          throw new IllegalStateException("AI settings changed. Save settings and try again.");
      }
      public void retrying(int next,int total,long delay,int code){
        runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())status.setText(AiCoachClient.retryMessage(next,total,delay,code));});
      }
    };
  }
  private void testConnection(){
    if(!vault.enabled())throw new IllegalStateException("Save your provider, API key and consent before testing.");
    busy=true;status.setText("Testing AI connection…");reveal(status);
    worker.execute(()->{
      String result;
      try{
        JSONObject settings=vault.read();
        String raw=AiCoachClient.generate(settings,"Return only this JSON object: {\"connection\":\"ok\"}.",retryProgress(settings));
        if(!new JSONObject(raw).optString("connection").equals("ok"))throw new IllegalStateException("Provider replied, but did not return the expected test response.");
        if(!settings.optString("revision").equals(vault.read().optString("revision")))throw new IllegalStateException("AI settings changed. Test the saved connection again.");
        result="Connection successful: "+settings.getString("provider")+" / "+settings.getString("model");
      }catch(Exception e){result="Connection test failed: "+e.getMessage();}
      final String message=result;
      runOnUiThread(()->{if(!isDestroyed()){busy=false;status.setText(message);reveal(status);}});
    });
  }
  private void reveal(TextView view){view.post(()->view.requestRectangleOnScreen(new android.graphics.Rect(0,0,view.getWidth(),view.getHeight()),false));}
  private void ask(String value){
    if(!vault.enabled())throw new IllegalStateException("Save your API key and data-sharing consent first.");
    responseLabel.setText("COACH ANSWER");pending=null;proposal=null;busy=true;status.setText("Coach is reading your recent runs…");answer.setText("");reveal(status);
    worker.execute(()->{try{
      JSONObject settings=vault.read();String fingerprint=store.proposalFingerprint();
      String response=AiCoachConversation.validate(AiCoachClient.generate(settings,AiCoachConversation.prompt(store,settings,value),retryProgress(settings)));
      synchronized(RunStore.LOCK){if(!settings.optString("revision").equals(vault.read().optString("revision")))throw new IllegalStateException("AI settings changed. Ask again.");if(!fingerprint.equals(store.proposalFingerprint()))throw new IllegalStateException("Your running history changed while the coach was answering. Ask again for current advice.");}
      runOnUiThread(()->{if(isDestroyed())return;busy=false;answer.setText(CoachMarkdown.render(response));status.setText("Coach answer ready. Your plan is unchanged.");reveal(answer);});
    }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){busy=false;status.setText("Coach could not answer. "+e.getMessage());reveal(status);}});}});
  }
  private void generate(){
    if(store.config()==null){
      startActivity(new Intent(this,RacePlanActivity.class).putExtra("planningMethod","ai"));return;
    }
    if(!vault.enabled())throw new IllegalStateException("Save your API key and data-sharing consent first.");
    responseLabel.setText("PLAN REVIEW");busy=true;pending=null;proposal=null;status.setText("Coach is reviewing your recent runs…");answer.setText("");reveal(status);
    worker.execute(()->{try{
      JSONObject settings=vault.read();AiCoachPolicy.Request request=AiCoachPolicy.snapshot(store,settings,false);
      AiCoachPolicy.Proposal result=AiCoachPolicy.validate(request,AiCoachClient.generate(settings,AiCoachPolicy.prompt(request,null),retryProgress(settings)));
      synchronized(RunStore.LOCK){if(!settings.optString("revision").equals(vault.read().optString("revision")))throw new IllegalStateException("AI settings changed. Generate again.");vault.result(result.explanation());}
      runOnUiThread(()->{if(isDestroyed())return;busy=false;pending=request;proposal=result;pendingSettingsRevision=settings.optString("revision");answer.setText(CoachMarkdown.render(result.explanation()+"\n\n"+summary(result)));status.setText("Plan proposal ready. Review it before applying.");reveal(answer);});
    }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){busy=false;status.setText(e instanceof java.io.IOException?e.getMessage():"Coach could not complete a valid proposal. "+e.getMessage());reveal(status);}});}});
  }
  private String summary(AiCoachPolicy.Proposal result){StringBuilder out=new StringBuilder();for(var s:result.sessions())out.append(s.date()).append(" · ").append(PlanEngine.distance(s.km())).append("\n").append(s.instructions()).append("\n\n");return out.length()==0?"No workout changes proposed.":out.toString();}
  private void review(){
    if(pending==null||proposal==null)throw new IllegalStateException("Generate a fresh plan proposal first.");
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Apply AI running plan?").setMessage(proposal.explanation()+"\n\n"+summary(proposal)).setNegativeButton("Keep current plan",null).setPositiveButton("Apply",(confirmed,which)->{try{
      synchronized(RunStore.LOCK){if(!vault.enabled()||!pendingSettingsRevision.equals(vault.read().optString("revision")))throw new IllegalStateException("AI settings changed. Generate a fresh proposal.");store.applyAiPlan(pending.fingerprint(),proposal.sessions(),proposal.explanation(),saved.getString("provider"),pending.runToken());}
      Reminders.schedule(this);pending=null;proposal=null;status.setText("AI plan saved. View changes and undo to inspect the update.");
    }catch(Exception e){status.setText(e.getMessage());}}).create();dialog.show();CoachUi.styleDialog(dialog);
  }
}
