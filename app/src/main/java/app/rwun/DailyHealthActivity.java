package app.rwun;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.util.*;

/** Native preferences and explanations. Health answers remain outside exports and AI prompts. */
public final class DailyHealthActivity extends Activity {
    private final UiKit.Palette palette=new UiKit.Palette(true);
    private DailyHealthStore store;private LinearLayout body;private TextView status;
    private String screen="home";private Bundle draft=new Bundle();private int generation;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);store=new DailyHealthStore(this);screen=getIntent().getStringExtra("screen");if(screen==null)screen="home";
        if(saved!=null){screen=saved.getString("screen","home");draft=saved.getBundle("draft");if(draft==null)draft=new Bundle();}show();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("screen",screen);out.putBundle("draft",draft);}
    @Override protected void onResume(){super.onResume();DailyHealthJob.schedule(this);}
    @Override protected void onDestroy(){generation++;super.onDestroy();}
    private int dp(int n){return UiKit.dp(this,n);}
    private void navigate(String next){screen=next;draft=new Bundle();show();}
    private void show(){
        generation++;LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(palette.background);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(UiKit.iconButton(this,palette,R.drawable.ic_arrow_back,"Back",()->{if(screen.equals("home"))finish();else navigate("home");}),new LinearLayout.LayoutParams(dp(48),dp(48)));
        bar.addView(UiKit.label(this,"Movement & recovery",19,palette.ink,true),new LinearLayout.LayoutParams(0,-2,1));root.addView(bar);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(8),dp(20),dp(28));scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);status=text("",14);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        try{switch(screen){case "movement"->movement();case "checkin"->checkin();case "advice"->advice();case "privacy"->privacy();default->home();}}
        catch(RuntimeException error){status.setText(UserErrors.message(error));if(!screen.equals("privacy"))action("Review feature data",()->navigate("privacy"));}
    }
    private TextView text(String label,int size){TextView v=UiKit.label(this,label,size,palette.ink,size>=20);body.addView(v);return v;}
    private void note(String title,String detail){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(12),dp(16),dp(12));card.setBackground(UiKit.shape(this,palette.surface,16));
        card.addView(UiKit.label(this,title,20,palette.ink,true));card.addView(UiKit.label(this,detail,15,palette.muted,false));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(8),0,dp(8));body.addView(card,params);
    }
    private void action(String label,Runnable operation){body.addView(UiKit.action(this,palette,label,()->{try{operation.run();}catch(RuntimeException e){status.setText(UserErrors.message(e));}}));}
    private CheckBox check(String label,boolean on){
        CheckBox v=new CheckBox(this);v.setText(label);v.setTextColor(palette.ink);v.setMinHeight(dp(48));v.setChecked(draft.getBoolean(label,on));
        v.setOnCheckedChangeListener((button,value)->draft.putBoolean(label,value));body.addView(v);return v;
    }
    private EditText input(String label,String value,boolean numeric){
        text(label,14);EditText v=new EditText(this);v.setTextColor(palette.ink);v.setContentDescription(label);
        v.setInputType(numeric?android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL:android.text.InputType.TYPE_CLASS_TEXT);
        v.setText(draft.getString(label,value));v.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){draft.putString(label,s.toString());}});body.addView(v);return v;
    }
    private Spinner choice(String label,List<String> values){
        text(label,14);Spinner v=new Spinner(this);v.setContentDescription(label);v.setMinimumHeight(dp(48));v.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));v.setSelection(draft.getInt(label,0));
        v.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View selected,int pos,long id){draft.putInt(label,pos);}public void onNothingSelected(AdapterView<?> p){}});body.addView(v);return v;
    }
    private void home(){
        text("Make room for recovery",27);note("Private by design","Movement and check-in information stays on this phone. No location history, uploads or model training for this feature.");
        var cfg=store.settings();note("Movement breaks",cfg.movement()?"After about "+cfg.inactivityMinutes()+" minutes with little recorded movement · "+cfg.breakMinutes()+" minute walking goal.":"Off · choose your working days and hours to begin.");
        action("Configure movement breaks",()->navigate("movement"));
        note("Before your next session",cfg.checkin()?"An optional recovery check-in is offered before new sessions. Your plan changes only after review.":"Check-ins are available here. Enable the pre-session prompt in movement preferences or when saving a check-in.");
        action("Pre-run recovery check-in",()->navigate("checkin"));if(store.checkin(Instant.now())!=null)action("Review latest check-in",()->navigate("advice"));
        action("Data, permissions & deletion",()->navigate("privacy"));text("Training holds remain in effect. Wellness guidance is not medical assessment or clearance to exercise.",14);
    }
    private void movement(){
        var cfg=store.settings();text("Movement breaks",26);
        text("Remind me after little recorded movement during working hours. Missing steps do not prove sitting. Delayed or insufficient data suppresses reminders. Enabling this turns off legacy fixed-time movement prompts.",15);
        CheckBox enabled=check("Enable movement reminders",cfg.movement()),prompt=check("Offer a check-in before new sessions",cfg.checkin());
        List<CheckBox> days=new ArrayList<>();for(DayOfWeek day:DayOfWeek.values())days.add(check(day.toString(),cfg.days().contains(day.getValue())));
        EditText start=input("Working hours start · HH:mm",time(cfg.startMinute()),false),end=input("Working hours end · HH:mm (24:00 allowed)",time(cfg.endMinute()),false);
        EditText interval=input("Inactivity interval · minutes (minimum 60)",""+cfg.inactivityMinutes(),true),duration=input("Walking break · minutes (minimum 3)",""+cfg.breakMinutes(),true);
        action("Save movement preferences",()->{
            Set<Integer> chosen=new HashSet<>();for(int i=0;i<days.size();i++)if(days.get(i).isChecked())chosen.add(i+1);
            var next=new DailyHealthRules.Settings(enabled.isChecked(),prompt.isChecked(),chosen,minute(start.getText().toString()),minute(end.getText().toString()),Integer.parseInt(interval.getText().toString().trim()),Integer.parseInt(duration.getText().toString().trim()));
            store.settings(next,Instant.now());if(next.movement())disableLegacyMovement();Reminders.schedule(this);DailyHealthJob.schedule(this);
            if(next.movement()&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},301);
            status.setText(next.movement()?"Saved. Allow steps and background health reads below. Checks begin after a full inactivity interval in working hours.":"Saved. Movement reminders are off.");
        });
        note("Data access",HealthAccess.available(this)?"Steps: "+(HealthAccess.granted(this,DailyHealthSource.STEPS)?"allowed":"not allowed")+" · Background reads: "+(HealthAccess.granted(this,HealthJob.BACKGROUND)?"allowed":"not allowed"):"Health Connect requires Android 14+ and an available service. Manual check-ins still work.");
        action("Allow Health Connect step reads",()->requestHealth(DailyHealthSource.STEPS,302));
        action("Allow background health reads",()->{if(!HealthAccess.backgroundSupported(this))throw new IllegalStateException("Background health reads are unavailable on this Android version. Check system updates; manual check-ins still work.");requestHealth(HealthJob.BACKGROUND,303);});
        text(store.status(),14);text("Fewer than 50 recorded steps over your interval is treated as low movement. Recent short step records are required. The app cannot confirm that you carried your phone. Checks run about every 15 minutes and may be delayed by Android. Up to eight alerts daily; active or pending recordings and training holds suppress them.",14);
        action("Already moved · reset interval",()->{store.acknowledge(Instant.now());cancelReminder();status.setText("Interval reset from your confirmation. No steps were invented.");});
        action("Skip movement reminders today",()->{store.skip(LocalDate.now());cancelReminder();status.setText("Movement reminders skipped for today.");});
        action("Open notification settings",()->startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())));
        action("Planned workout & legacy reminders",()->startActivity(new Intent(this,TrainingActivity.class).putExtra("screen","reminders")));
    }
    private void checkin(){
        text("How are you feeling?",26);text("Optional answers help you choose today's effort. Leave anything unknown. No readiness score is calculated and your plan stays unchanged.",15);
        EditText sleep=input("Hours slept · optional","",true);List<String> scale=List.of("Unknown","1 · Low","2","3","4","5 · High");
        Spinner fatigue=choice("Fatigue",scale),soreness=choice("Muscle soreness",scale),alcohol=choice("Alcohol yesterday evening",List.of("Unknown / skip","No","Yes")),illness=choice("Feeling unwell",List.of("Unknown","No","Yes"));
        CheckBox pain=check("Pain or injury symptoms",false),prompt=check("Offer a check-in before new sessions",store.settings().checkin());
        action("Save and review recovery",()->{
            String hours=sleep.getText().toString().trim();var value=new DailyHealthRules.Checkin(Instant.now(),hours.isEmpty()?null:Double.parseDouble(hours),fatigue.getSelectedItemPosition(),soreness.getSelectedItemPosition(),DailyHealthRules.Answer.values()[alcohol.getSelectedItemPosition()],DailyHealthRules.Answer.values()[illness.getSelectedItemPosition()],pain.isChecked());
            store.saveCheckin(value);var cfg=store.settings();if(prompt.isChecked()!=cfg.checkin())store.settings(new DailyHealthRules.Settings(cfg.movement(),prompt.isChecked(),cfg.days(),cfg.startMinute(),cfg.endMinute(),cfg.inactivityMinutes(),cfg.breakMinutes()),Instant.now());
            DailyHealthJob.schedule(this);navigate("advice");
        });
        action("Continue without saving",this::finish);text("Only your latest saved check-in is retained, for up to seven days. A new check-in replaces it. Answers are excluded from backups, activity exports and AI explanations.",14);recoveryContext();
    }
    private void recoveryContext(){
        text("Optional watch context",21);text("Garmin data is usable only when Garmin Connect publishes it to Health Connect. Sync can be delayed. Direct Garmin account sync and HRV readiness are not implemented.",14);
        action("Allow sleep context",()->{requestHealth(DailyHealthSource.SLEEP,304);store.recoveryReads(true);});
        action("Allow resting heart-rate context",()->{requestHealth(DailyHealthSource.RESTING_HR,305);store.recoveryReads(true);});
        TextView context=text("No recovery measurements loaded. Your answers remain separate from watch data.",14);
        action("Read recovery context on this phone",()->{
            if(!store.recoveryReads())throw new IllegalStateException("Enable sleep or resting heart-rate access first.");
            boolean sleep=HealthAccess.granted(this,DailyHealthSource.SLEEP),heart=HealthAccess.granted(this,DailyHealthSource.RESTING_HR);
            if(!sleep&&!heart)throw new IllegalStateException("No recovery read permission is granted. Your manual check-in is still available.");
            int token=generation;long revision=store.revision();context.setText("Reading local Health Connect history…");
            DailyHealthSource.create(this).recovery(Instant.now(),ZoneId.systemDefault(),sleep,heart).whenComplete((value,error)->runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||generation!=token||revision!=store.revision()||!store.recoveryReads())return;
                if(sleep&&!HealthAccess.granted(this,DailyHealthSource.SLEEP)||heart&&!HealthAccess.granted(this,DailyHealthSource.RESTING_HR)){context.setText("Recovery access changed. No measurements displayed.");return;}
                context.setText(error==null?value:"Recovery history could not be read. Your answers remain available.");
            }));
        });
        action("Stop optional recovery reads",()->{store.recoveryReads(false);context.setText("Optional recovery reads stopped. Manage permission grants in Health Connect settings.");});
    }
    private void advice(){
        var value=store.checkin(Instant.now());if(value==null){text("No saved check-in",24);action("Start a recovery check-in",()->navigate("checkin"));return;}
        var advice=DailyHealthRules.advice(value,new RunStore(this).resting());text(advice.title(),26);text("Saved "+value.at().atZone(ZoneId.systemDefault()).toLocalDateTime().withSecond(0).withNano(0),14);
        if(!value.recent(Instant.now()))note("This check-in is old","These answers are historical. Complete a new check-in for your next session.");
        note("Why this suggestion?",String.join("\n",advice.reasons()));note("Your options",advice.suggestion());text("Your training plan has not changed. Review any schedule change before applying it. Check-ins never clear an existing training hold.",15);
        if(value.recent(Instant.now())&&value.pain()&&!new RunStore(this).resting())action("Review a training hold",()->new AlertDialog.Builder(this).setTitle("Pause training?")
                .setMessage("You reported pain. This holds running and indoor sessions until you explicitly review recovery in Profile.").setNegativeButton("Keep current plan",null)
                .setPositiveButton("Save training hold",(dialog,which)->{new RunStore(this).adapt(new AdaptiveCoach.Advice(0,true,"Pain reported in recovery check-in. Review recovery before resuming training."));Reminders.schedule(this);cancelReminder();show();}).show());
        action("Review my schedule",()->startActivity(new Intent(this,TrainingActivity.class).putExtra("screen","week")));
        if(!advice.reviewHold())action("Choose a comfortable walk",()->startActivity(new Intent(this,IndoorActivity.class).putExtra("goal",store.settings().breakMinutes())));
        action("Keep plan and return",this::finish);action("Start a new check-in",()->navigate("checkin"));action("Delete saved check-in",()->{store.clearCheckin();navigate("home");});
    }
    private void privacy(){
        text("Your data stays here",26);note("Movement and recovery","Settings, reminder state and your latest check-in stay in private storage on this phone. The latest check-in expires after seven days and is removed on the next app/feature access. Raw watch records and recovery comparisons are not saved by this feature.");
        text("This feature sends no network requests, uses no GPS or venue history, and does not send answers or measurements to AI models, analytics, cloud backups or activity exports. No model training uses this feature's data. Existing OpenRun network features and Garmin's upstream processing are separate.",15);
        text("Revoke individual Health Connect permissions at any time to stop future reads. Revocation does not delete your latest check-in. Deleting feature data keeps workout history and Health Connect records.",15);
        action("Open Health Connect controls",()->startActivity(new Intent(this,HealthConnectActivity.class)));
        action("Delete saved check-in",()->{store.clearCheckin();status.setText("Saved check-in deleted.");});
        action("Delete movement & recovery data",()->new AlertDialog.Builder(this).setTitle("Delete this feature's data?").setMessage("Reminders and pre-session prompts turn off. Your check-in and preferences are deleted. Workout history and Health Connect records are kept.")
                .setNegativeButton("Keep data",null).setPositiveButton("Delete",(dialog,which)->{store.clear();DailyHealthJob.schedule(this);cancelReminder();navigate("home");}).show());
    }
    private void requestHealth(String permission,int code){if(!HealthAccess.available(this))throw new IllegalStateException("Health Connect is unavailable. Update Android or use the manual check-in.");requestPermissions(new String[]{permission},code);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);DailyHealthJob.schedule(this);show();status.setText("Permission request finished. Only granted access is used; the manual check-in remains available.");}
    private void cancelReminder(){getSystemService(NotificationManager.class).cancel(DailyHealthJob.NOTIFICATION_ID);}
    private void disableLegacyMovement(){synchronized(RunStore.LOCK){RunStore training=new RunStore(this);var data=training.v103();var reminders=data.optJSONObject("reminders");if(reminders!=null)try{reminders.put("movement",false);training.v103(data);}catch(org.json.JSONException e){throw new IllegalStateException(e);}}}
    private static String time(int minute){return String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60);}
    private static int minute(String text){String value=text.trim();if(value.equals("24:00"))return 1440;try{LocalTime time=LocalTime.parse(value);return time.getHour()*60+time.getMinute();}catch(RuntimeException e){throw new IllegalArgumentException("Use HH:mm, for example 09:00 or 17:30.");}}
}
