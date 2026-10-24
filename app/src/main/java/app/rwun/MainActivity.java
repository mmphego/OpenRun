package app.rwun;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public final class MainActivity extends Activity {
  private boolean refreshFeatures;
  private String filterType="All workouts",filterYear="All years",filterMonth="All months";
  private TextView appTitle;
  private ImageButton appLeft;
  private boolean planSchedule,creatingPlan;
  private int setupStep;
  private String draftVolume="12",draftWeeks="8",draftDays="3",draftTime="";
  private int draftGoal,draftBenchmark;
  private boolean draftSpeed,draftReturn;
  private LocalDate draftStart=LocalDate.now();
  private String subpage="";
  private boolean performance,walkMode,walkProgress,exportingWalk;
  private LocalDate calendarStart=LocalDate.now().with(DayOfWeek.MONDAY);
  private UiKit.Palette palette;
  private int ink, green, paper;
  private RunDashboard dashboard;
  private Button pauseControl;
  private ScrollView pageScroll;
  private RunStore store;
  private PlanEngine.Config config;
  private LinearLayout root, content;
  private int week;
  private String tab = "Today", pendingId = "";
  private double pendingTarget;
  private TrackingService tracker;
  private boolean bound,startingRun,resumeRunFromSettings;
  private String startRequest="";private long startDeadline;private AlertDialog gpsDialog;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private String exportingRoute = "";
  private final ServiceConnection connection =
      new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
          tracker = ((TrackingService.LocalBinder) service).service();
          if (tab.equals("Run")) render();
        }

        public void onServiceDisconnected(ComponentName name) {
          tracker = null;
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    palette = new UiKit.Palette(true);
    ink = palette.ink; green = palette.primary; paper = palette.background;
    getWindow().setStatusBarColor(paper);
    getWindow().setNavigationBarColor(palette.surface);
    getWindow().getDecorView().setSystemUiVisibility(palette.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
    store = new RunStore(this);store.refocus(this);getSystemService(NotificationManager.class).cancel(1101);store.purgeWalkUndo(this);store.purgeUndo(this,false);Reminders.schedule(this);
    if(state==null&&store.config()==null)setupStep=-1;
    if (state != null) {
      planSchedule=state.getBoolean("planSchedule",false);creatingPlan=state.getBoolean("creatingPlan",false);setupStep=state.getInt("setupStep",0);
      draftVolume=state.getString("draftVolume","12");draftWeeks=state.getString("draftWeeks","8");draftDays=state.getString("draftDays","3");draftTime=state.getString("draftTime","");draftGoal=state.getInt("draftGoal");draftBenchmark=state.getInt("draftBenchmark");draftSpeed=state.getBoolean("draftSpeed");draftReturn=state.getBoolean("draftReturn");draftStart=LocalDate.parse(state.getString("draftStart",LocalDate.now().toString()));
      startingRun=state.getBoolean("startingRun");resumeRunFromSettings=state.getBoolean("resumeRunFromSettings");startRequest=state.getString("startRequest","");startDeadline=state.getLong("startDeadline");
      week = state.getInt("week");
      performance=state.getBoolean("performance",false);walkMode=state.getBoolean("walkMode");walkProgress=state.getBoolean("walkProgress");exportingWalk=state.getBoolean("exportingWalk");
      calendarStart=LocalDate.parse(state.getString("calendarStart",calendarStart.toString()));
      tab = state.getString("tab", "Today");
      pendingId = state.getString("pendingId", "");
      pendingTarget = state.getDouble("pendingTarget");
      exportingRoute = state.getString("exportingRoute", "");
    }
    try {
      config = store.config();
      acceptDestination(getIntent());
      render();
    } catch (RuntimeException e) {
      error(e);
    }
  }

  private void acceptDestination(Intent intent){String destination=intent.getStringExtra("destination");if(destination!=null&&List.of("Run","Plan","Today","Profile","Calendar","Progress","Coach").contains(destination)){tab=destination;creatingPlan=false;if(destination.equals("Plan"))planSchedule=true;intent.removeExtra("destination");}}
  @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);acceptDestination(intent);render();}
  private void openV1(String screen){refreshFeatures=true;startActivity(new Intent(this,V1Activity.class).putExtra("screen",screen));}

  @Override
  protected void onStart() {
    super.onStart();
    store.event("app_open");
    bound = bindService(new Intent(this, TrackingService.class), connection, BIND_AUTO_CREATE);
    handler.post(refresh);
  }

  @Override
  protected void onStop() {
    handler.removeCallbacks(refresh);
    if (bound) {
      unbindService(connection);
      bound = false;
    }
    tracker = null;
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    worker.shutdown();
    super.onDestroy();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    super.onSaveInstanceState(out);
    out.putBoolean("planSchedule",planSchedule);out.putBoolean("creatingPlan",creatingPlan);out.putInt("setupStep",setupStep);out.putString("draftVolume",draftVolume);out.putString("draftWeeks",draftWeeks);out.putString("draftDays",draftDays);out.putString("draftTime",draftTime);out.putInt("draftGoal",draftGoal);out.putInt("draftBenchmark",draftBenchmark);out.putBoolean("draftSpeed",draftSpeed);out.putBoolean("draftReturn",draftReturn);out.putString("draftStart",draftStart.toString());
    out.putBoolean("startingRun",startingRun);out.putBoolean("resumeRunFromSettings",resumeRunFromSettings);out.putString("startRequest",startRequest);out.putLong("startDeadline",startDeadline);
    out.putInt("week", week);
    out.putBoolean("performance",performance);out.putBoolean("walkMode",walkMode);out.putBoolean("walkProgress",walkProgress);out.putBoolean("exportingWalk",exportingWalk);out.putString("calendarStart",calendarStart.toString());
    out.putString("tab", tab);
    out.putString("pendingId", pendingId);
    out.putDouble("pendingTarget", pendingTarget);
    out.putString("exportingRoute", exportingRoute);
  }

  private final Runnable refresh =
      new Runnable() {
        public void run() {
          if(startingRun)confirmRunStart();
          if (dashboard != null && tab.equals("Run")) updateDashboard();
          handler.postDelayed(this, 1000);
        }
      };

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density);
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private TextView text(String s, int size, boolean bold) {
    int scaled = size >= 30 ? 24 : size >= 22 ? 20 : size;
    if(bold&&size>=26&&!subpage.isEmpty()&&appTitle!=null)appTitle.setText(s);
    TextView t = UiKit.label(this, s, scaled, bold ? ink : palette.muted, bold);
    return t;
  }

  private GradientDrawable rounded(int color) { return UiKit.shape(this, color, 22); }

  private Button button(String label, Runnable action) {
    if(label.startsWith("Back")){
      header(appTitle==null?"OpenRun":appTitle.getText().toString(),action);
      Button hidden=new Button(this);hidden.setVisibility(View.GONE);return hidden;
    }
    return UiKit.action(this, palette, label, () -> {
      try { action.run(); } catch (RuntimeException e) { error(e); }
    });
  }

  private LinearLayout card() {
    LinearLayout c = column(); c.setPadding(dp(16), dp(12), dp(16), dp(12));
    GradientDrawable background = rounded(palette.surface);
    background.setStroke(dp(1), palette.outline); c.setBackground(background);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, dp(6), 0, dp(6)); c.setLayoutParams(lp); content.addView(c); return c;
  }

  private void clearContent() {
    subpage="Detail";
    content.removeAllViews(); dashboard = null; pauseControl = null;
    if (pageScroll != null) { pageScroll.scrollTo(0,0); pageScroll.post(() -> pageScroll.scrollTo(0,0)); }
  }

  private void render() {
    View focused=getCurrentFocus();
    if(focused!=null){android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.hideSoftInputFromWindow(focused.getWindowToken(),0);}
    subpage="";
    WorkoutWidget.updateAll(this);
    dashboard = null; pauseControl = null;
    root = column(); root.setFocusableInTouchMode(true); root.requestFocus();root.setBackgroundColor(paper);
    root.setOnApplyWindowInsetsListener((v, insets) -> {
      v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
          insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
      return insets.consumeSystemWindowInsets();
    });
    FrameLayout toolbar=new FrameLayout(this);toolbar.setMinimumHeight(dp(56));
    appTitle=UiKit.label(this,tab.equals("Today")?"OpenRun":tab.equals("Plan")?"Your Plan":tab.equals("Run")?(tracker!=null&&tracker.active()&&tracker.walking()?"Walk":"Move"):tab,18,ink,true);appTitle.setGravity(Gravity.CENTER);appTitle.setSingleLine(true);appTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
    FrameLayout.LayoutParams titleLp=new FrameLayout.LayoutParams(-1,dp(56));titleLp.setMargins(dp(62),0,dp(62),0);toolbar.addView(appTitle,titleLp);
    appLeft=UiKit.iconButton(this,palette,R.drawable.ic_settings,"Profile",()->{tab="Profile";creatingPlan=false;render();});
    appLeft.setImageDrawable(getDrawable(R.drawable.ic_rwun));FrameLayout.LayoutParams leftLp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.START|Gravity.CENTER_VERTICAL);leftLp.setMargins(dp(12),0,0,0);toolbar.addView(appLeft,leftLp);
    ImageButton quickRun=UiKit.iconButton(this,palette,R.drawable.ic_directions_run,"Record a run",()->{creatingPlan=false;tab="Run";render();});
    FrameLayout.LayoutParams rightLp=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.END|Gravity.CENTER_VERTICAL);rightLp.setMargins(0,0,dp(12),0);toolbar.addView(quickRun,rightLp);root.addView(toolbar);
    pageScroll=new ScrollView(this);pageScroll.setFillViewport(false);pageScroll.setClipToPadding(false);
    pageScroll.setVerticalScrollBarEnabled(false);content=column();content.setPadding(dp(20),dp(12),dp(20),dp(24));
    pageScroll.addView(content);root.addView(pageScroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    if(config==null&&(tab.equals("Today")||tab.equals("Plan"))){setup();return;}
    View separator=new View(this);separator.setBackgroundColor(palette.outline);root.addView(separator,new LinearLayout.LayoutParams(-1,dp(1)));
    LinearLayout nav=new LinearLayout(this);nav.setBackgroundColor(palette.surface);nav.setPadding(dp(4),0,dp(4),0);
    String[] names={"Today","Plan","Calendar","Progress","Coach"};
    int[] icons={R.drawable.ic_home,R.drawable.ic_assignment,R.drawable.ic_calendar_month,R.drawable.ic_monitoring,R.drawable.ic_volume_up};
    for(int i=0;i<names.length;i++){String name=names[i];nav.addView(UiKit.navigationItem(this,palette,name,icons[i],name.equals(tab),()->{tab=name;creatingPlan=false;if(name.equals("Plan")){planSchedule=false;if(config!=null)week=Math.max(0,Math.min(config.weeks()-1,(int)(java.time.temporal.ChronoUnit.DAYS.between(config.start(),LocalDate.now())/7)));}render();}),new LinearLayout.LayoutParams(0,-2,1));}
    root.addView(nav);
    if(creatingPlan){setup();return;}
    switch(tab){case "Plan":if(planSchedule)plan();else planHub();break;case "Calendar":calendar();break;case "Run":recording();break;case "Progress":progress();break;case "Profile":settings();break;case "Coach":coach();break;default:today();}
  }

  private EditText field(String label, String value) {
    content.addView(text(label, 15, true));
    EditText e = new EditText(this);
    e.setId(View.generateViewId());((TextView)content.getChildAt(content.getChildCount()-1)).setLabelFor(e.getId());e.setContentDescription(label);
    e.setText(value);
    e.setTextColor(ink);
    e.setInputType(8194);
    e.setSingleLine(true);
    content.addView(e);
    return e;
  }

  private Spinner spinner(String... values) {
    Spinner s = new Spinner(this);
    s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
    return s;
  }


  private void header(String title,Runnable back){
    if(appTitle!=null)appTitle.setText(title);
    if(appLeft!=null){appLeft.setImageDrawable(UiKit.icon(this,R.drawable.ic_arrow_back,ink,24));appLeft.setContentDescription("Back");appLeft.setOnClickListener(v->{try{back.run();}catch(RuntimeException e){error(e);}});}
  }

  private void ensurePlanEditable(){
    if((tracker!=null&&tracker.active())||!store.checkpoint().isEmpty()||store.indoorPending())throw new IllegalStateException("Finish, recover or discard the pending session before changing your plan.");
  }

  private void onboard(String volume,String days,boolean returning){draftVolume=volume;draftDays=days;draftReturn=returning;draftSpeed=false;draftTime="";draftGoal=0;draftBenchmark=0;draftWeeks="8";draftStart=LocalDate.now();setupStep=0;store.event("setup_started");render();}

  private void beginPlan(){ensurePlanEditable();openRace("goal");}

  private void planHub(){
    LinearLayout hero=column();hero.setGravity(Gravity.CENTER);hero.setPadding(dp(10),dp(28),dp(10),dp(24));
    ImageView hand=new ImageView(this);hand.setImageDrawable(UiKit.icon(this,R.drawable.ic_waving_hand,palette.primary,40));hero.addView(hand,new LinearLayout.LayoutParams(dp(52),dp(52)));
    TextView welcome=text("Welcome back!",28,true);welcome.setGravity(Gravity.CENTER);LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(-1,-2);wl.setMargins(0,dp(20),0,dp(14));hero.addView(welcome,wl);
    TextView description=text("Let’s make a plan for your next start line.",17,false);description.setGravity(Gravity.CENTER);hero.addView(description);hero.setMinimumHeight(dp(240));content.addView(hero,new LinearLayout.LayoutParams(-1,-2));
    planHubChoice("Start new plan",this::beginPlan);
    planHubChoice("Restart current plan",this::confirmRestart);
    planHubChoice("Continue plan",()->{planSchedule=true;render();});
    if(store.resting())content.addView(text("Your training hold stays active when changing plans.",13,false));
    content.addView(button("Training preferences",()->openFeatures("plan")));
  }
  private void planHubChoice(String label,Runnable action){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(20),dp(16),dp(16),dp(16));row.setMinimumHeight(dp(74));row.setBackground(PlanningUi.shape(this,PlanningUi.CARD,18,true));TextView title=text(label,20,false);title.setTextColor(palette.ink);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));ImageView arrow=new ImageView(this);arrow.setImageDrawable(UiKit.icon(this,R.drawable.ic_chevron_right,palette.muted,25));row.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(28)));row.setClickable(true);row.setFocusable(true);row.setContentDescription(label);row.setOnClickListener(v->{try{action.run();}catch(RuntimeException e){error(e);}});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(20));content.addView(row,lp);}

  private void confirmRestart(){
    try{ensurePlanEditable();}catch(RuntimeException e){error(e);return;}
    new AlertDialog.Builder(this).setTitle("Restart from week one?").setMessage("Your current goal and pace profile stay the same. The schedule starts today. Saved activities and any training hold are kept; moved and skipped sessions are reset.").setNegativeButton("Cancel",null).setPositiveButton("Restart plan",(d,w)->{try{store.restartPlan(LocalDate.now());config=store.config();week=0;planSchedule=true;render();}catch(RuntimeException e){error(e);}}).show();
  }

  private EditText draftField(String label,String value,java.util.function.Consumer<String> changed){
    EditText field=field(label,value);field.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){changed.accept(s.toString());}public void afterTextChanged(android.text.Editable e){}});return field;
  }
  private double[] goals(){return new double[]{5,10,21.1,42.2};}
  private PlanEngine.Config draftConfig(){return new PlanEngine.Config(Integer.parseInt(draftWeeks),Integer.parseInt(draftDays),Double.parseDouble(draftVolume),goals()[draftGoal],draftStart);}
  private PaceProfile draftProfile(){return new PaceProfile(goals()[draftBenchmark],draftTime.trim().isEmpty()?0:Math.round(Double.parseDouble(draftTime)*60),draftSpeed);}

  private void setup(){
    if(setupStep<0){
      content.addView(text("Your next start line.",30,true));content.addView(text("A plan for your running. Room to walk every day.",17,false));PlanningUi.gap(this,content,24);
      content.addView(PlanningUi.choice(this,R.drawable.ic_assignment,"Create my plan","Choose your goal or find a race.",()->openRace("goal")));
      content.addView(PlanningUi.choice(this,R.drawable.ic_directions_walk,"Start a walk","Choose walking pad or outdoors.",this::chooseWalk));
      content.addView(PlanningUi.choice(this,R.drawable.ic_directions_walk,"Walking pad & treadmill","Indoor time, steps and optional distance.",()->openIndoor(null)));
      content.addView(button("Movement reminders",()->openTraining("reminders")));return;
    }
    header("Create your plan",()->{if(setupStep>0){setupStep--;render();}else if(config!=null){creatingPlan=false;render();}else {setupStep=-1;render();}});
    content.addView(text("STEP "+(setupStep+1)+" OF 4",12,false));
    if(setupStep==0){
      content.addView(text("What are you training for?",30,true));content.addView(text("Choose a distance. We’ll build an original plan around your current running.",16,false));
      content.addView(UiKit.choice(this,palette,"Race · choose an event or add your own",()->openRace("search")));
      String[] titles={"5 km","10 km","Half marathon","Marathon"};
      for(int i=0;i<titles.length;i++){final int index=i;LinearLayout choice=UiKit.choice(this,palette,titles[i]+(draftGoal==i?" · chosen":""),()->{draftGoal=index;setupStep=1;render();});choice.setSelected(draftGoal==i);choice.setContentDescription(titles[i]+(draftGoal==i?", selected":""));content.addView(choice);}
      return;
    }else if(setupStep==1){
      content.addView(text("Start with your current fitness",30,true));draftField("Current weekly running distance (km)",draftVolume,v->draftVolume=v);
      content.addView(text("Optional recent race result",18,true));Spinner distances=spinner("5 km","10 km","Half marathon","Marathon");distances.setSelection(draftBenchmark);distances.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int index,long id){draftBenchmark=index;}public void onNothingSelected(AdapterView<?> p){}});content.addView(distances);
      draftField("Race time in minutes · leave blank for effort targets",draftTime,v->draftTime=v);
      CheckBox speed=new CheckBox(this);speed.setText(R.string.enable_speed);speed.setTextColor(ink);speed.setChecked(draftSpeed);speed.setOnCheckedChangeListener((b,on)->draftSpeed=on);content.addView(speed);
    }else if(setupStep==2){
      content.addView(text("Make room for running",30,true));draftField("Runs each week · 2–5",draftDays,v->draftDays=v).setInputType(android.text.InputType.TYPE_CLASS_NUMBER);draftField("Plan length · 4–24 weeks",draftWeeks,v->draftWeeks=v).setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
      content.addView(UiKit.choice(this,palette,"Start date · "+draftStart.format(DateTimeFormatter.ofPattern("d MMM yyyy")),()->new DatePickerDialog(this,(picker,y,m,d)->{draftStart=LocalDate.of(y,m+1,d);render();},draftStart.getYear(),draftStart.getMonthValue()-1,draftStart.getDayOfMonth()).show()));
    }else{
      PlanEngine.Config next=draftConfig();PaceProfile profile=draftProfile();content.addView(text("Your plan, at a glance",30,true));LinearLayout summary=card();summary.addView(text(PlanEngine.distance(next.goalKm())+" goal",23,true));summary.addView(text(next.weeks()+" weeks · "+next.days()+" runs per week",16,false));summary.addView(text(draftReturn?"Return to running · run / walk":"Race preparation",15,false));summary.addView(text("Starts "+next.start().format(DateTimeFormatter.ofPattern("d MMM yyyy")),15,false));
      content.addView(text("WEEK ONE PREVIEW",12,false));for(var session:OnboardingPlan.sessions(next,profile,draftReturn))if(session.date().isBefore(next.start().plusDays(7))){LinearLayout run=card();run.addView(text(session.title(),18,true));run.addView(text(session.date().format(DateTimeFormatter.ofPattern("EEE, d MMM"))+" · "+PlanEngine.distance(session.km()),15,false));}
      content.addView(text("These rule-based plans use conservative progression; they have not been validated as equivalent to professional coaching. Your existing activity history is kept.",13,false));
      content.addView(button("Build my plan",()->{ensurePlanEditable();store.createPlan(next,profile,draftReturn);store.event("plan_created");config=store.config();creatingPlan=false;planSchedule=true;week=0;tab="Plan";render();}));return;
    }
    content.addView(button("Continue",()->{
      if(setupStep==1){double volume=Double.parseDouble(draftVolume);if(!Double.isFinite(volume)||volume<4||volume>120)throw new IllegalArgumentException("Weekly distance must be between 4 and 120 km.");draftProfile();}
      if(setupStep==2){draftConfig();draftProfile();if(draftStart.isBefore(LocalDate.now()))throw new IllegalArgumentException("Choose today or a future start date.");}
      setupStep++;render();
    }));
  }

  private List<PlanEngine.Session> sessions() {
    return store.plannedSessions();
  }

  private String key(PlanEngine.Session s) {
    return store.planId() + ":" + s.id();
  }

  private void openRace(String mode){refreshFeatures=true;startActivityForResult(new Intent(this,RacePlanActivity.class).putExtra("mode",mode),207);}

  private void openTraining(String screen){refreshFeatures=true;startActivity(new Intent(this,TrainingActivity.class).putExtra("screen",screen));}

  private void openIndoor(String planned){refreshFeatures=true;Intent i=new Intent(this,IndoorActivity.class);if(planned!=null)i.putExtra("planned",planned);startActivity(i);}
  private void chooseWalk(){
    if(store.indoorPending()){openIndoor(null);return;}
    if((tracker!=null&&tracker.active())||!store.checkpoint().isEmpty()){tab="Run";render();return;}
    new AlertDialog.Builder(this).setTitle("Where are you walking?")
      .setItems(new String[]{"Walking pad · phone steps","Outdoors · GPS"},(dialog,which)->{
        try{if(which==0)openIndoor(null);
        else{walkMode=true;prepareStart("walk:"+UUID.randomUUID(),0);}}catch(RuntimeException e){error(e);}
      }).setNegativeButton("Cancel",null).show();
  }
  private void today() {
    content.addView(text("Your training, today.",30,true));
    planOverview();
    content.addView(text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),14,false));
    if(store.resting()){LinearLayout hold=card();hold.addView(text("Training paused",20,true));hold.addView(text(store.adaptation(),15,false));}
    if((tracker!=null&&tracker.active())||store.indoorPending()||!store.checkpoint().isEmpty())content.addView(button("Return to your session",()->{if(store.indoorPending())openIndoor(null);else{tab="Run";render();}}));
    var next=sessions().stream().filter(s->!s.date().isBefore(LocalDate.now())&&!store.completed(key(s))&&!store.skipped(key(s))).findFirst().orElse(null);
    LinearLayout hero=card();hero.setBackground(rounded(palette.tonal));hero.addView(text(next==null?"MAKE ROOM TO MOVE":"YOUR NEXT RUN",12,true));hero.addView(text(next==null?"A little movement counts.":next.title(),28,true));
    hero.addView(text(next==null?"Choose a run, a walk, or a few minutes on your walking pad.":PlanEngine.distance(next.km())+" · "+next.date().format(DateTimeFormatter.ofPattern("EEE, d MMM")),16,false));
    if(next!=null)hero.addView(button("View workout",()->detail(next)));
    coachPreview();
    LinearLayout start=new LinearLayout(this);Button run=button("Start run",()->{walkMode=false;prepareStart("free:"+UUID.randomUUID(),0);}),walk=button("Start walk",this::chooseWalk);LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,-2,1);a.setMargins(0,dp(6),dp(5),dp(6));start.addView(run,a);LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(0,-2,1);b.setMargins(dp(5),dp(6),0,dp(6));start.addView(walk,b);content.addView(start);
    LinearLayout indoor=card();row(indoor,R.drawable.ic_directions_walk,"Walking pad & treadmill","Phone steps and estimated distance · no GPS",()->openIndoor(null));
    weeklySummary();WalkData.Week w=WalkData.week(store.v103(),LocalDate.now());LinearLayout walking=card();walking.addView(text("WALKING · LAST 7 DAYS",11,true));walking.addView(text(w.seconds()/60+" minutes · "+w.days()+" active days",21,true));walking.addView(text("Everyday movement, kept separate from running mileage.",14,false));
    LinearLayout tools=card();row(tools,R.drawable.ic_calendar_month,"Movement reminders","Choose times that suit your day",()->openTraining("reminders"));row(tools,R.drawable.ic_info,"How are you feeling?","Optional check-in before your next session",()->openTraining("checkin"));
    if(!store.adaptation().equals("No adjustments applied.")){LinearLayout changes=card();changes.addView(text("Your plan",20,true));changes.addView(text(store.adaptation(),15,false));}
  }

  private PlanEngine.Session nextCoachedSession() {
    return sessions().stream().filter(s->!s.date().isBefore(LocalDate.now())&&!store.completed(key(s))&&!store.skipped(key(s))).findFirst().orElse(null);
  }

  private void planOverview() {
    if(config==null)return;
    int current=Math.max(0,Math.min(config.weeks()-1,(int)(java.time.temporal.ChronoUnit.DAYS.between(config.start(),LocalDate.now())/7)));
    LinearLayout summary=card();summary.addView(text("YOUR RUNNING PLAN",11,true));
    JSONObject race=store.v103().optJSONObject("race");boolean racePlan=race!=null&&race.optString("planId").equals(store.planId());
    summary.addView(text(racePlan?race.optString("name","Your race"):PlanEngine.distance(config.goalKm())+" journey",24,true));
    if(racePlan){summary.addView(text(race.optString("date")+" · "+PlanEngine.distance(config.goalKm())+" · "+race.optString("city"),15,false));long left=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.parse(race.optString("date")));summary.addView(text(left>=0?left+" days to race":"Race date passed",13,false));}
    summary.addView(text("Week "+(current+1)+" of "+config.weeks()+" · "+config.days()+" runs per week",14,false));
    if(!tab.equals("Plan"))row(summary,R.drawable.ic_assignment,"View your plan","Workouts, progression and weekly schedule",()->{tab="Plan";planSchedule=true;week=current;render();});
  }

  private void showCoachBriefing(PlanEngine.Session session) {
    String briefing=CoachGuide.briefing(session.title(),store.resting(),store.profile().seconds()>0);
    new AlertDialog.Builder(this).setTitle("Your workout briefing").setMessage(briefing+"\n\n"+CoachGuide.PROVENANCE).setPositiveButton("Done",null).setNeutralButton("Listen",(d,w)->{if(tracker!=null){Toast.makeText(this,tracker.coachBriefing(briefing),Toast.LENGTH_LONG).show();}else Toast.makeText(this,"Audio service is connecting. Try again shortly.",Toast.LENGTH_LONG).show();}).show();
  }

  private void coachPreview() {
    LinearLayout c=card();c.addView(text("YOUR COACH",11,true));
    PlanEngine.Session next=nextCoachedSession();
    c.addView(text(next==null?"Make your next session count":next.title()+" · coach briefing",20,true));
    c.addView(text(next==null?"Running guidance, walking support and recovery advice in one place.":CoachGuide.briefing(next.title(),store.resting(),store.profile().seconds()>0),15,false));
    row(c,R.drawable.ic_volume_up,"Open Coach","Workout guidance, audio and training review",()->{tab="Coach";render();});
  }

  private void coach() {
    content.addView(text("Your coach",30,true));
    content.addView(text("Prepare. Run or walk. Reflect and improve.",15,false));
    if(store.resting()){LinearLayout hold=card();hold.addView(text("Training is paused",20,true));hold.addView(text(store.adaptation(),15,false));}
    if(config!=null){
      PlanEngine.Session next=nextCoachedSession();
      if(next!=null){LinearLayout briefing=card();briefing.addView(text("NEXT WORKOUT",11,true));briefing.addView(text(next.title(),24,true));briefing.addView(text(PlanEngine.distance(next.km())+" · "+next.date().format(DateTimeFormatter.ofPattern("EEE, d MMM")),15,false));briefing.addView(text(CoachGuide.briefing(next.title(),store.resting(),store.profile().seconds()>0),16,false));row(briefing,R.drawable.ic_volume_up,"Listen to briefing","Spoken guidance using your phone voice",()->showCoachBriefing(next));row(briefing,R.drawable.ic_directions_run,"Open workout","Steps, target effort and recording",()->detail(next));}
      LinearLayout review=card();review.addView(text("Your training review",21,true));review.addView(text(store.adaptation(),15,false));row(review,R.drawable.ic_monitoring,"Review a saved workout","Recorded effort, fatigue and session details",()->openTraining("review"));row(review,R.drawable.ic_info,"How are you feeling?","Optional check-in before training",()->openTraining("checkin"));
    }else{LinearLayout start=card();start.addView(text("Build your running plan",21,true));start.addView(text("Choose a goal to get session briefings and weekly training guidance.",15,false));row(start,R.drawable.ic_assignment,"Create my plan","Your goal, experience and available days",()->{tab="Plan";creatingPlan=false;render();});}
    if(config!=null){content.addView(button("Adapt plan from recent running",()->openRace("review")));weeklyReview();}
    LinearLayout offline=card();row(offline,R.drawable.ic_volume_up,"Offline Coach · Gemma",OfflineModels.ready(this)?"Model installed · generate on this phone":"Private coaching · set up the local model",()->startActivity(new Intent(this,OfflineCoachActivity.class)));
    LinearLayout learn=card();row(learn,R.drawable.ic_info,"Coaching library","Running, walking and recovery guides",()->openFeatures("help"));row(learn,R.drawable.ic_volume_up,"Audio coaching","Voice preferences and a sample cue",()->{tab="Profile";render();});row(learn,R.drawable.ic_directions_walk,"Walking pad & treadmill","Indoor walking and running",()->openIndoor(null));
    content.addView(text(CoachGuide.PROVENANCE,12,false));
  }

  private void weeklySummary() {
    LocalDate start=LocalDate.now().with(java.time.DayOfWeek.MONDAY), end=start.plusDays(7);
    double km=0,planned=0;int count=0;
    for(RunStore.Run run:store.runs()){LocalDate date=LocalDate.parse(run.date());if(!date.isBefore(start)&&date.isBefore(end)){km+=run.km();count++;}}
    for(PlanEngine.Session session:sessions())if(!session.date().isBefore(start)&&session.date().isBefore(end)&&!store.skipped(key(session)))planned+=session.km();
    LinearLayout summary=card();summary.addView(text("THIS WEEK",11,true));
    TextView total=UiKit.label(this,String.format(Locale.US,"%.2f km",km)+"   ·   "+count+(count==1?" run":" runs"),24,ink,true);summary.addView(total);
    if(planned>0){ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgress((int)Math.min(100,km/planned*100));progress.setProgressTintList(ColorStateList.valueOf(green));progress.setProgressBackgroundTintList(ColorStateList.valueOf(palette.tonal));summary.addView(progress,new LinearLayout.LayoutParams(-1,dp(8)));summary.addView(text(String.format(Locale.US,"%.1f km planned this week",planned),12,false));}
  }

  private static String activityDistance(double km) {return km>0&&km<.1?Math.round(km*1000)+" m":String.format(Locale.US,"%.2f km",km);}

  private void plan() {
    planOverview();
    subpage="Schedule";header("Training schedule",()->{planSchedule=false;render();});
    LinearLayout weekBar=new LinearLayout(this);weekBar.setGravity(Gravity.CENTER_VERTICAL);weekBar.setPadding(0,dp(8),0,dp(8));
    ImageButton previous=UiKit.iconButton(this,palette,R.drawable.ic_chevron_left,"Previous week",()->{week=Math.max(0,week-1);render();});
    previous.setEnabled(week>0);previous.setAlpha(week>0?1f:.35f);weekBar.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));
    TextView weekTitle=text("Week "+(week+1)+" of "+config.weeks(),20,true);weekTitle.setGravity(Gravity.CENTER);
    weekBar.addView(weekTitle,new LinearLayout.LayoutParams(0,-2,1));
    ImageButton next=UiKit.iconButton(this,palette,R.drawable.ic_chevron_right,"Next week",()->{week=Math.min(config.weeks()-1,week+1);render();});
    next.setEnabled(week<config.weeks()-1);next.setAlpha(week<config.weeks()-1?1f:.35f);weekBar.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));weekBar.addView(UiKit.iconButton(this,palette,R.drawable.ic_settings,"Manage running plan",()->{planSchedule=false;render();}),new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(weekBar);
    LocalDate start=config.start().plusWeeks(week),end=start.plusDays(7);
    content.addView(text(start.format(DateTimeFormatter.ofPattern("d MMM"))+" – "+end.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM yyyy")),14,false));
    LinearLayout days=new LinearLayout(this);
    for(int i=0;i<7;i++){LocalDate date=start.plusDays(i);boolean today=date.equals(LocalDate.now());LinearLayout day=column();day.setGravity(Gravity.CENTER);day.setPadding(0,dp(8),0,dp(8));day.setBackground(rounded(today?palette.tonal:palette.surface));TextView weekday=text(date.format(DateTimeFormatter.ofPattern("EEEEE")),11,false);weekday.setGravity(Gravity.CENTER);day.addView(weekday);TextView number=text(""+date.getDayOfMonth(),16,true);number.setGravity(Gravity.CENTER);day.addView(number);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(2),dp(4),dp(2),dp(8));days.addView(day,lp);}content.addView(days);
    if (store.resting())
      content.addView(text("Training paused. Distances are reference only.", 16, true));
    for (int d = 0; d < 7; d++) {
      LocalDate date = start.plusDays(d);
      List<PlanEngine.Session> daily = new ArrayList<>();
      for (var session : sessions()) if (session.date().equals(date)) daily.add(session);
      if (daily.isEmpty()) {
        LinearLayout rest = card();
        rest.addView(
            text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM")) + " · Rest", 16, true));
        continue;
      }
      for (var session : daily) {
        LinearLayout c = card();
        c.addView(text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM")), 14, false));
        c.addView(text(session.title(), 22, true));
        c.addView(
            text(
                PlanEngine.distance(session.km())
                    + (store.completed(key(session))
                        ? " · Completed"
                        : store.skipped(key(session)) ? " · Skipped" : ""),
                17,
                false));
        c.addView(button("Workout details", () -> detail(session)));
      }
    }
    content.addView(button("My week changed",()->openTraining("week")));
  }

  private void detail(PlanEngine.Session session) {
    clearContent();
    content.addView(button("Back", this::render));
    content.addView(text(session.title(), 32, true));
    content.addView(
        text(PlanEngine.distance(session.km()) + " main set · " + session.date(), 20, true));
    content.addView(text(session.instructions(), 17, false));
    content.addView(button("Coach briefing",()->showCoachBriefing(session)));
    content.addView(button("Explore coaching guides",()->openFeatures("help")));

    content.addView(button("Add to device calendar",()->{
      Intent intent=new Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI).putExtra(android.provider.CalendarContract.Events.TITLE,"OpenRun · "+session.title()).putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY,true).putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME,session.date().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()).putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME,session.date().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()).putExtra(android.provider.CalendarContract.Events.DESCRIPTION,session.instructions());startActivity(intent);
    }));
    var definition = WorkoutFactory.create(session, store.profile());
    int index = 0;
    for (var step : definition.steps()) {
      LinearLayout c = card();
      c.addView(text((++index) + ". " + step.title(), 18, true));
      c.addView(text(step.target() + " · " + step.pace().label(), 16, false));
    }
    content.addView(
        text(
            "Warm-up, cool-down and timed recoveries add distance beyond the main-set target. Pace"
                + " ranges are estimates; comfortable effort comes first.",
            14,
            false));
    if (store.completed(key(session))) {
      content.addView(text("Workout saved", 20, true));
      return;
    }
    if (store.skipped(key(session))) {
      content.addView(text("Skipped · no catch-up distance added", 17, true));
      content.addView(
          button(
              "Unskip workout",
              () -> {
                store.skip(key(session), false);
                detail(session);
              }));
      return;
    }
    content.addView(button("Reschedule workout", () -> reschedule(session)));
    content.addView(
        button(
            "Skip workout",
            () -> {
              store.skip(key(session), true);
              render();
            }));
    if (session.date().isAfter(LocalDate.now())) {
      content.addView(
          text(
              "Recording opens on the scheduled day. You can reschedule to today if needed.",
              16,
              false));
      return;
    }
    content.addView(button("Use treadmill for this workout",()->openIndoor(key(session))));
    content.addView(button("Start GPS workout", () -> prepareStart(key(session), session.km())));
    EditText km = field("Or enter actual distance (km)", String.valueOf(session.km())),
        minutes = field("Duration (whole minutes)", "");
    content.addView(
        button(
            "Log manual run",
            () ->
                feedback(
                    key(session),
                    LocalDate.now().toString(),
                    Double.parseDouble(km.getText().toString()),
                    Long.parseLong(minutes.getText().toString()) * 60,
                    "Manual",
                    "",
                    0,
                    session.km(),
                    null)));
  }

  private void reschedule(PlanEngine.Session session) {
    if (tracker != null && tracker.active())
      throw new IllegalStateException("Finish your active run first.");
    if (config.start().plusWeeks(config.weeks()).minusDays(1).isBefore(LocalDate.now()))
      throw new IllegalStateException(
          "This plan has ended. Create a new plan to schedule future runs.");
    LocalDate date = session.date();
    DatePickerDialog dialog =
        new DatePickerDialog(
            this,
            (picker, year, month, day) -> {
              try {
                store.move(key(session), LocalDate.of(year, month + 1, day));
                render();
              } catch (Exception error) {
                error(error);
              }
            },
            date.getYear(),
            date.getMonthValue() - 1,
            date.getDayOfMonth());
    dialog
        .getDatePicker()
        .setMinDate(
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
    dialog
        .getDatePicker()
        .setMaxDate(
            config
                .start()
                .plusWeeks(config.weeks())
                .minusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli());
    dialog.show();
  }

  private boolean gpsEnabled(){try{return getSystemService(android.location.LocationManager.class).isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);}catch(RuntimeException unavailable){return false;}}
  private void enableLocation(){
    if(gpsDialog!=null&&gpsDialog.isShowing())return;
    gpsDialog=new AlertDialog.Builder(this).setTitle("Turn on location to start")
      .setMessage("Your session has not started. GPS recording needs device Location switched on. Enable it in Android Settings, then return to OpenRun; we’ll continue starting this run.")
      .setNegativeButton("Not now",null).setPositiveButton("Open location settings",(d,w)->{
        try{resumeRunFromSettings=true;startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));}
        catch(ActivityNotFoundException|SecurityException blocked){resumeRunFromSettings=false;new AlertDialog.Builder(this).setTitle("Enable Location manually").setMessage("Open Android Settings → Location and turn it on, then return and tap Start free run again. No run is recording yet.").setPositiveButton("OK",null).show();}
      }).create();gpsDialog.setOnDismissListener(d->gpsDialog=null);gpsDialog.show();
  }
  private boolean canStartRun(){
    if(store.indoorPending()){openIndoor(null);return false;}
    var next=RunStartGate.next(tracker!=null&&tracker.active(),store.resting(),!store.checkpoint().isEmpty(),checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED,gpsEnabled());
    switch(next){
      case ACTIVE_RUN:tab="Run";render();return false;
      case TRAINING_HOLD:throw new IllegalStateException("Training is paused after your feedback. Review Profile before resuming.");
      case PENDING_RECORDING:tab="Run";render();throw new IllegalStateException("Recover or discard the interrupted recording on this screen before starting another run.");
      case LOCATION_PERMISSION:requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},100);return false;
      case LOCATION_SETTINGS:enableLocation();return false;
      default:return true;
    }
  }
  private void prepareStart(String id,double target){
    if(startingRun){tab="Run";render();return;}
    if(store.skipped(id))throw new IllegalStateException("Unskip this workout before starting it.");
    walkMode=id.startsWith("walk:");pendingId=id;pendingTarget=target;
    if(!canStartRun())return;
    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},101);return;}
    startRecording();
  }
  private void startRecording(){
    if(startingRun||!canStartRun())return;
    if(store.skipped(pendingId))throw new IllegalStateException("Unskip this workout before starting it.");
    startRequest=UUID.randomUUID().toString();startDeadline=SystemClock.elapsedRealtime()+10000;startingRun=true;
    try{startForegroundService(new Intent(this,TrackingService.class).setAction("START").putExtra("id",pendingId).putExtra("target",pendingTarget).putExtra("request",startRequest));}
    catch(RuntimeException failure){startingRun=false;throw failure;}
    tab="Run";render();handler.postDelayed(()->{if(bound&&!isFinishing())confirmRunStart();},300);
  }
  private void confirmRunStart(){
    if(!startingRun||!bound||isFinishing())return;
    var outcome=RunStartGate.outcome(tracker!=null&&tracker.active(),startRequest,tracker==null?"":tracker.startRequest(),tracker==null?"":tracker.startFailure(),SystemClock.elapsedRealtime(),startDeadline);
    if(outcome==RunStartGate.Outcome.WAITING)return;
    startingRun=false;
    if(tab.equals("Run"))render();
    if(outcome==RunStartGate.Outcome.RECORDING){Toast.makeText(this,(walkMode?"Outdoor walk":"Run")+" recording started. GPS measures outdoor movement; use Indoors for a walking pad.",Toast.LENGTH_LONG).show();return;}
    String message=outcome==RunStartGate.Outcome.FAILED?tracker.startFailure():"The recorder has not confirmed a start. Check the Run screen and notification before trying again. No recording has been confirmed.";
    new AlertDialog.Builder(this).setTitle(outcome==RunStartGate.Outcome.FAILED?"Run did not start":"Check recording status").setMessage(message).setPositiveButton("OK",null).show();
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
    super.onRequestPermissionsResult(request, permissions, results);
    if (request == 100) {
      if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
          == PackageManager.PERMISSION_GRANTED) {try{prepareStart(pendingId, pendingTarget);}catch(RuntimeException e){error(e);}}
      else new AlertDialog.Builder(this).setTitle("Precise location is needed")
        .setMessage("Your session has not started. In App permissions → Location, allow access while using OpenRun and enable precise location. Return here to continue starting your run.")
        .setNegativeButton("Not now",null).setPositiveButton("Open app permissions",(d,w)->{try{resumeRunFromSettings=true;startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(ActivityNotFoundException|SecurityException e){resumeRunFromSettings=false;error(new IllegalStateException("Open Android Settings → Apps → OpenRun → Permissions → Location manually, then try again."));}}).show();
    }
    if (request == 101) {if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"Notification controls will be hidden until notifications are enabled in app settings.",Toast.LENGTH_LONG).show();try{startRecording();}catch(RuntimeException e){error(e);}}
    if (request == 102 && bleGranted()) scanSensors();
  }

  private void updateDashboard() {
    if(dashboard!=null)dashboard.update(tracker,store.audio());
    if(pauseControl!=null&&tracker!=null){
      String label=tracker.paused()?"Resume run":"Pause run";pauseControl.setText(label);
      pauseControl.setCompoundDrawablesRelative(UiKit.icon(this,tracker.paused()?R.drawable.ic_play_arrow:R.drawable.ic_pause,palette.primary,22),null,null,null);
    }
  }

  private void recording() {
    if(store.indoorPending()){
      content.addView(text("Indoor session in progress",24,true));
      content.addView(text("Your walking-pad or treadmill session uses phone steps and time. Return to see its recording status.",15,false));
      content.addView(button("Return to indoor session",()->openIndoor(null)));return;
    }
    if(tracker!=null&&tracker.active())walkMode=tracker.walking();if((tracker==null||!tracker.active())&&store.checkpoint().isEmpty())content.addView(UiKit.tabs(this,palette,new String[]{"Run","Walk"},walkMode?1:0,i->{walkMode=i==1;render();}));
    content.addView(text(walkMode?"Your walk":"Your run",28,true));
    dashboard = new RunDashboard(this,palette);dashboard.setWalking(walkMode);content.addView(dashboard);updateDashboard();
    if(startingRun){content.addView(text("Starting GPS recording…",22,true));content.addView(text("Waiting for the recorder to confirm. Your recording has not been confirmed yet.",15,false));content.addView(new ProgressBar(this));}
    else if (tracker != null && tracker.active()) {
      pauseControl=button(tracker.paused()?"Resume session":"Pause session",()->{tracker.togglePause();updateDashboard();});
      content.addView(pauseControl);
      content.addView(
          button(
              "Finish and save",
              () -> {
                TrackingService recording = tracker;
                recording.freeze();
                RunStore.Run r = recording.pending(5, 2, false);
                feedback(
                    r.sessionId(),
                    r.date(),
                    r.km(),
                    r.seconds(),
                    r.source(),
                    r.routeFile(),
                    r.avgHr(),
                    recording.target(),
                    recording::saved,
                    r);
              }));
      content.addView(button("GPS recording details",()->new AlertDialog.Builder(this).setTitle("GPS recording details").setMessage(tracker.gpsDetails()).setPositiveButton("Done",null).show()));
      content.addView(button("Connect wearable heart rate", this::wearable));
      content.addView(
          button(
              "Discard session",
              () ->
                  new AlertDialog.Builder(this)
                      .setTitle("Discard this session?")
                      .setMessage("The recording and its GPS route will be deleted.")
                      .setNegativeButton("Keep", null)
                      .setPositiveButton(
                          "Discard",
                          (d, w) -> {
                            tracker.discard();
                            render();
                          })
                      .show()));
    } else if (!store.checkpoint().isEmpty()) {
      content.addView(
          text(
              "Interrupted recording found. Unrecorded time is excluded. Recover the saved distance"
                  + " and route, or discard it.",
              16,
              false));
      content.addView(button("Recover recorded portion", this::recover));
      content.addView(
          button(
              "Discard interrupted run",
              () ->
                  new AlertDialog.Builder(this)
                      .setTitle("Discard interrupted recording?")
                      .setNegativeButton("Keep", null)
                      .setPositiveButton(
                          "Discard",
                          (d, w) -> {
                            try {
                              JSONObject o = new JSONObject(store.checkpoint());
                              String route = o.optString("route");
                              if (route.matches("route-[a-zA-Z0-9-]+\\.jsonl")) deleteFile(route);
                              store.clearCheckpoint();
                              render();
                            } catch (Exception e) {
                              error(e);
                            }
                          })
                      .show()));
    } else {
      content.addView(
          text(
              "Record outdoors with precise location enabled. Keep the run notification active while your screen is off. Interrupted recordings can be recovered.",
              16,
              false));
      content.addView(button(store.indoorPending()?"Return to indoor session":"Walking pad · phone steps",()->openIndoor(null)));
      content.addView(button("Recording readiness",()->openV1("tools")));
      content.addView(button(walkMode?"Start outdoor walk":"Start free run", () -> prepareStart((walkMode?"walk:":"free:") + UUID.randomUUID(), 0)));
    }
  }

  private void recover() {
    try {
      JSONObject o = new JSONObject(store.checkpoint());
      String id = o.getString("id");
      if (store.completed(id)) {
        store.clearCheckpoint();
        render();
        return;
      }
      feedback(
          id,
          o.getString("date"),
          o.getDouble("km"),
          o.getLong("seconds"),
          "Recovered GPS",
          o.getString("route"),
          o.optInt("hr"),
          0,
          () -> store.clearCheckpoint());
    } catch (Exception e) {
      error(e);
    }
  }

  private void feedback(
      String id,
      String date,
      double km,
      long seconds,
      String source,
      String route,
      int hr,
      double target,
      Runnable after) {
    feedback(id, date, km, seconds, source, route, hr, target, after, null);
  }

  private void feedback(
      String id,
      String date,
      double km,
      long seconds,
      String source,
      String route,
      int hr,
      double target,
      Runnable after,
      RunStore.Run metrics) {
    if (!Double.isFinite(km) || km < 0 || (km==0&&!id.startsWith("walk:")) || seconds <= 0 || seconds > 180000 || km > 250)
      throw new IllegalArgumentException(
          "A run needs a positive distance and duration. Wait for GPS movement or discard the"
              + " recording.");
    LinearLayout form = column();
    form.setPadding(dp(20), dp(12), dp(20), 0);
    final String savedRoute=id.startsWith("walk:")&&!route.isEmpty()&&!getFileStreamPath(route).isFile()?"":route;
    final EditText knownWalkDistance;
    if(id.startsWith("walk:")) {
      form.addView(text("Known distance in km · optional",15,true));
      knownWalkDistance=new EditText(this);knownWalkDistance.setInputType(8194);knownWalkDistance.setTextSize(16);
      knownWalkDistance.setTextColor(palette.ink);knownWalkDistance.setHint("GPS recorded "+String.format(Locale.ROOT,"%.2f",km)+" km");
      knownWalkDistance.setContentDescription("Known walking distance in kilometres");form.addView(knownWalkDistance);
      form.addView(text("Enter a distance you know from the machine or another measurement. It is saved as manual distance; no missing GPS route is created.",13,false));
    } else knownWalkDistance=null;
    form.addView(text("Effort: 1 easy – 10 maximal", 15, true));
    Spinner rpe = spinner("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    rpe.setSelection(4);
    form.addView(rpe);
    form.addView(text("Fatigue: 1 fresh – 5 exhausted", 15, true));
    Spinner fatigue = spinner("1", "2", "3", "4", "5");
    fatigue.setSelection(1);
    form.addView(fatigue);
    CheckBox pain = new CheckBox(this);
    pain.setText(R.string.pain_feedback);
    form.addView(pain);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("How did it feel?")
            .setView(form)
            .setNegativeButton(
                "Cancel",
                (cancel, which) -> {
                  if (source.startsWith("Garmin ") && !route.isEmpty()) deleteFile(route);
                })
            .setPositiveButton("Save", null)
            .create();
    dialog.setOnCancelListener(
        cancel -> {
          if (source.startsWith("Garmin ") && !route.isEmpty()) deleteFile(route);
        });
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        String enteredDistance=knownWalkDistance==null?"":knownWalkDistance.getText().toString().trim();
                        double savedDistance=enteredDistance.isEmpty()?km:Double.parseDouble(enteredDistance);
                        if(!Double.isFinite(savedDistance)||savedDistance<0||savedDistance>250)throw new IllegalArgumentException("Enter a walking distance from 0 to 250 km");
                        int effort = rpe.getSelectedItemPosition() + 1,
                            tired = fatigue.getSelectedItemPosition() + 1;
                        RunStore.Run savedRecord=new RunStore.Run(
                                id,
                                date,
                                savedDistance,
                                (int) Math.max(1, Math.round(seconds / 60.0)),
                                effort,
                                tired,
                                pain.isChecked(),
                                enteredDistance.isEmpty()?source:"Manual walk distance",
                                savedRoute,
                                hr,
                                seconds,
                                metrics == null ? "Unstructured" : metrics.workoutType(),
                                metrics == null ? 0 : metrics.qualityKm(),
                                metrics == null ? 0 : metrics.qualitySeconds(),
                                metrics == null ? 0 : metrics.targetPace(),
                                metrics == null ? 0 : metrics.benchmark());
                        if(id.startsWith("walk:"))store.addWalk(savedRecord);else store.add(savedRecord);
                        if(!id.startsWith("walk:"))store.event("run_saved");
                        if (after != null) after.run();
                        dialog.dismiss();
                        tab = "Progress";
                        render();
                        if(id.startsWith("walk:")){walkProgress=true;performance=false;render();Reminders.schedule(this);new AlertDialog.Builder(this).setTitle("Walk saved").setMessage((savedDistance>0?"Your walking time and distance are saved separately from running. ":"Your walking time is saved. No distance was measured; use Correct walking distance if you know it. ")+(pain.isChecked()?"Your training hold is active.":"Your running plan is unchanged.")).setPositiveButton("Done",null).show();return;}
                        String debrief=CoachGuide.debrief(km,seconds,effort,tired,pain.isChecked(),true,store.resting());
                        if (!date.equals(LocalDate.now().toString())) {new AlertDialog.Builder(this).setTitle("Run reflection").setMessage(debrief).setPositiveButton("Done",null).show();return;}
                        if(store.resting()&&!pain.isChecked()) {new AlertDialog.Builder(this).setTitle("Training hold remains").setMessage(debrief).setPositiveButton("Done",null).show();return;}
                        AdaptiveCoach.Advice advice =
                            new AdaptiveCoach()
                                .advise(
                                    new AdaptiveCoach.Feedback(
                                        effort,
                                        tired,
                                        pain.isChecked(),
                                        target > 0 ? Math.min(3, km / target) : 1));
                        if (advice.rest()) {
                          store.adapt(advice);
                          new AlertDialog.Builder(this)
                              .setTitle("Training paused")
                              .setMessage(debrief+"\n\n"+advice.reason())
                              .setPositiveButton("OK", (a, b) -> render())
                              .show();
                        } else
                          new AlertDialog.Builder(this)
                              .setTitle("Review training adjustment")
                              .setMessage(
                                  debrief+"\n\n"+advice.reason()
                                      + "\n\n"
                                      + "Applies to tomorrow through the following six days. Past"
                                      + " workouts remain unchanged.")
                              .setNegativeButton("Keep current plan", null)
                              .setPositiveButton(
                                  "Apply",
                                  (a, b) -> {
                                    store.adapt(advice);
                                    render();
                                  })
                              .show();
                      } catch (Exception e) {
                        error(e);
                      }
                    }));
    dialog.show();
  }

  private void correctWalkDistance(JSONObject walk){
    LinearLayout form=column();form.setPadding(dp(20),dp(10),dp(20),0);
    form.addView(text("Enter a distance you know from the machine or another measurement. Recorded time and actual route points stay unchanged. The distance will be labelled manual.",14,false));
    EditText value=new EditText(this);value.setInputType(8194);value.setTextSize(16);value.setTextColor(palette.ink);
    value.setText(String.valueOf(walk.optDouble("km",0)));value.setContentDescription("Correct walking distance in kilometres");form.addView(value);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Walking distance · km").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save distance",null).create();
    dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{store.correctWalkDistance(walk.optString("id"),Double.parseDouble(value.getText().toString().trim()));dialog.dismiss();render();}catch(Exception e){error(e);}}));dialog.show();
  }

  private void walkingProgress(){
    store.purgeWalkUndo(this);WalkData.Week w=WalkData.week(store.v103(),LocalDate.now());LinearLayout summary=card();summary.addView(text("LAST 7 DAYS",12,true));summary.addView(text(w.seconds()/60+" minutes",30,true));summary.addView(text(w.sessions()+" walks · "+w.days()+" active days",16,false));summary.addView(text("Outdoor GPS distance, indoor entered distance and step estimates retain their own source labels.",13,false));
    if(store.v103().has("walkUndo"))content.addView(button("Undo last walk deletion · 15 minutes",()->{store.undoWalk();render();}));
    content.addView(button("Start walk",this::chooseWalk));content.addView(button("Walking pad",()->openIndoor(null)));
    var all=WalkData.all(store.v103());if(all.isEmpty()){content.addView(text("Your first walk starts here.",22,true));return;}
    for(int i=all.size()-1;i>=0;i--){JSONObject r=all.get(i);LinearLayout c=card();String id=r.optString("id"),source=r.optString("source",r.optString("distanceSource","Indoor timer"));c.addView(text(r.optString("date")+" · "+source,15,true));c.addView(text(duration(r.optLong("seconds"))+(r.isNull("km")||!r.optBoolean("distanceKnown",true)?" · time only":String.format(Locale.US," · %.2f km",r.optDouble("km"))),22,true));if(r.optLong("phoneSteps")>0)c.addView(text(r.optLong("phoneSteps")+" detected steps · "+(r.optBoolean("partialSteps")?"may be partial":"phone-step estimate"),14,false));if(id.startsWith("walk:")){RunStore.Run walk=new RunStore.Run(id,r.optString("date"),r.optDouble("km"),Math.max(1,(int)(r.optLong("seconds")/60)),r.optInt("rpe"),r.optInt("fatigue",2),r.optBoolean("pain"),source,r.optString("route"),r.optInt("hr"),r.optLong("seconds"));if(!walk.routeFile().isEmpty()){c.addView(button("View walking route",()->showRoute(walk)));c.addView(button("Export walking GPX",()->{exportingWalk=true;export(walk.routeFile());}));}c.addView(button("Correct walking distance",()->correctWalkDistance(r)));c.addView(button("Delete walk",()->new AlertDialog.Builder(this).setTitle("Delete saved walk?").setMessage("The latest deletion can be undone for 15 minutes.").setNegativeButton("Keep",null).setPositiveButton("Delete",(d,n)->{try{store.deleteWalk(id);render();}catch(Exception e){error(e);}}).show()));}else c.addView(button("Indoor session details",()->openTraining("indoorHistory")));}
  }

  private void progress() {
    if(store.pendingImports().length()>0)content.addView(button("Review imports · "+store.pendingImports().length()+" possible duplicates",()->startActivity(new Intent(this,ImportReviewActivity.class))));

    store.purgeUndo(this,false);
    
    if(store.v103().has("undo"))content.addView(button("Undo most recent deletion · 15 minutes",()->{store.undoDelete();render();}));
    content.addView(UiKit.tabs(this,palette,new String[]{"Runs","Walks","Performance"},walkProgress?1:performance?2:0,index->{walkProgress=index==1;performance=index==2;render();}));
    if(walkProgress){walkingProgress();return;}
    if(performance){performance();return;}
    List<RunStore.Run> runs = store.runs();
    if(runs.isEmpty()){emptyProgress();return;}
    TreeSet<String> years=new TreeSet<>(Comparator.reverseOrder());TreeSet<String> types=new TreeSet<>();for(var run:runs){years.add(run.date().substring(0,4));types.add(run.workoutType());}
    List<String> typeOptions=new ArrayList<>(List.of("All workouts"));typeOptions.addAll(types);List<String> yearOptions=new ArrayList<>(List.of("All years"));yearOptions.addAll(years);List<String> monthOptions=new ArrayList<>(List.of("All months"));for(int m=1;m<=12;m++)monthOptions.add(String.format(Locale.US,"%02d",m));
    LinearLayout filters=new LinearLayout(this);filterControl(filters,typeOptions,filterType,v->{filterType=v;render();});filterControl(filters,yearOptions,filterYear,v->{filterYear=v;render();});filterControl(filters,monthOptions,filterMonth,v->{filterMonth=v;render();});content.addView(filters);
    runs=runs.stream().filter(r->filterType.equals("All workouts")||r.workoutType().equals(filterType)).filter(r->filterYear.equals("All years")||r.date().startsWith(filterYear)).filter(r->filterMonth.equals("All months")||r.date().substring(5,7).equals(filterMonth)).collect(java.util.stream.Collectors.toList());
    if(runs.isEmpty()){content.addView(text("No activities match these filters.",17,true));content.addView(button("Clear filters",()->{filterType="All workouts";filterYear="All years";filterMonth="All months";render();}));return;}
    LinearLayout totals = card();
    totals.addView(
        text(activityDistance(runs.stream().mapToDouble(RunStore.Run::km).sum()), 34, true));
    totals.addView(
        text(
            runs.size()
                + (runs.size() == 1 ? " run · " : " runs · ")
                + duration(runs.stream().mapToLong(RunStore.Run::seconds).sum()),
            17,
            false));
    content.addView(button("Import Garmin GPX / TCX / FIT", this::importActivity));
    for (int i = runs.size() - 1; i >= 0; i--) {
      RunStore.Run r = runs.get(i);
      LinearLayout c = card();
      c.addView(
          text(
              r.date() + " · " + (r.source().startsWith("Garmin ") ? "Garmin" : r.source()),
              16,
              true));
      c.addView(text(activityDistance(r.km()) + " · " + duration(r.seconds()), 22, true));
      String pace=PaceProfile.format(r.seconds()/r.km())+" min/km";
      c.addView(text(pace+((r.source().startsWith("Health Connect")||r.source().equals("Strava"))?" · elapsed time · feedback not recorded":" · effort "+r.rpe()+"/10")+(r.avgHr()>0?" · "+r.avgHr()+" bpm":""),15,false));
      c.addView(button("Review, correct & match workout",()->{refreshFeatures=true;startActivity(new Intent(this,TrainingActivity.class).putExtra("screen","review").putExtra("id",r.sessionId()));}));
      c.addView(button("Share activity card",()->{refreshFeatures=true;startActivity(new Intent(this,V1Activity.class).putExtra("screen","share").putExtra("id",r.sessionId()));}));
      if (!r.routeFile().isEmpty()) {
        c.addView(button("View route", () -> showRoute(r)));
        c.addView(button("Export GPX", () -> {exportingWalk=false;export(r.routeFile());}));
        if(Build.VERSION.SDK_INT>=34&&(r.source().equals("GPS")||r.source().startsWith("Indoor · ")))c.addView(button("Write to Health Connect",()->exportHealth(r)));
        c.addView(button("Send to Strava",()->uploadStrava(r)));
      }
      c.addView(
          button(
              "Delete activity",
              () ->
                  new AlertDialog.Builder(this)
                      .setTitle("Delete saved run?")
                      .setMessage(
                          "You can undo the most recent deletion for 15 minutes. Applied plan adjustments remain until reviewed.")
                      .setNegativeButton("Cancel", null)
                      .setPositiveButton(
                          "Delete",
                          (d, w) -> {
                            store.purgeUndo(this,true);store.softDelete(r.sessionId());Reminders.schedule(this);
                            render();
                          })
                      .show()));
    }
  }

  private void showRoute(RunStore.Run run) {
    try {
      var points = TrackingService.readRoute(this, run.routeFile());
      clearContent();
      content.addView(button("Back", this::render));
      content.addView(text("Your route · offline sketch", 24, true));
      content.addView(
          text(
              "GPS points without street-map tiles. Breaks indicate pauses or signal gaps.",
              16,
              false));
      content.addView(button("Open online street map",()->startActivity(new Intent(this,StreetMapActivity.class).putExtra("route",run.routeFile()))));
      content.addView(new RouteView(this, points), new LinearLayout.LayoutParams(-1, dp(320)));
    } catch (Exception e) {
      error(e);
    }
  }

  private void settings() {
    content.addView(text("Your profile", 30, true));
    content.addView(text("OpenRun 1.6.0 · Run around your life", 16, false));

    section("MY STUFF");
    LinearLayout devices=card();row(devices,R.drawable.ic_watch,"Connected apps & devices","Health Connect, Garmin and heart rate",this::connections);


    
    section("TRAINING PREFERENCES");
    LinearLayout preferences=card();row(preferences,R.drawable.ic_calendar_month,"Programme & training days","Weekdays, long run and plan type",()->openFeatures("plan"));
    row(preferences,R.drawable.ic_monitoring,"Pace profile & targets","Recent race result and workout targets",this::paceProfile);
    Switch audio=new Switch(this);audio.setText(R.string.spoken_cues);audio.setTextColor(ink);audio.setMinHeight(dp(56));audio.setChecked(store.audio());audio.setOnCheckedChangeListener((b,on)->store.audio(on));preferences.addView(audio);
    row(preferences,R.drawable.ic_volume_up,"Test audio coach","Hear a sample cue",()->{if(tracker!=null)tracker.testVoice();});
    Switch alerts=new Switch(this);alerts.setText(R.string.pace_alerts);alerts.setTextColor(ink);alerts.setMinHeight(dp(56));alerts.setChecked(store.paceAlerts());alerts.setOnCheckedChangeListener((b,on)->store.paceAlerts(on));preferences.addView(alerts);
    section("LEARN & SUPPORT");LinearLayout learning=card();row(learning,R.drawable.ic_info,"Coaching library","Running, walking and recovery",()->openFeatures("help"));
    LinearLayout beta=card();row(beta,R.drawable.ic_settings,"Android & privacy","Widget, haptics, diagnostics and local metrics",()->openV1("tools"));row(beta,R.drawable.ic_info,"Beta feedback","Preview and share an issue report",()->openV1("beta"));
    section("YOUR DATA");
    LinearLayout data=card();row(data,R.drawable.ic_refresh,"Account & encrypted cloud backup","Optional self-hosted service",()->openFeatures("cloud"));row(data,R.drawable.ic_file_upload,"Export full backup","Plans, history and recorded routes",this::backup);
    row(data,R.drawable.ic_file_download,"Restore full backup","Choose an existing OpenRun backup",this::restoreBackup);
    row(data,R.drawable.ic_info,"Health data & privacy","Access, local storage and deletion",()->startActivity(new Intent(this,HealthPrivacyActivity.class)));
    content.addView(text("Adaptive training", 22, true));
    content.addView(text(store.adaptation(), 16, false));
    if (store.resting())
      content.addView(
          button(
              "Review and resume training",
              () ->
                  new AlertDialog.Builder(this)
                      .setTitle("Resume training?")
                      .setMessage(
                          "Resume only when appropriate for your symptoms. The next seven days will"
                              + " use 80% of base distance. OpenRun cannot assess injury readiness.")
                      .setNegativeButton("Stay paused", null)
                      .setPositiveButton(
                          "Resume",
                          (d, w) -> {
                            store.resumeTraining();
                            render();
                          })
                      .show()));
    content.addView(button("Manage training plan",()->{tab="Plan";planSchedule=false;render();}));
    content.addView(
        text(
            "Privacy: routes and feedback stay in private app storage; Android system cloud backup is"
                + " disabled. Exported files contain precise locations. Uninstall removes local"
                + " history. Optional encrypted cloud backup and Strava require explicit setup of your own service. No Runna services are used.",
            14,
            false));
  }


  private void section(String name){TextView title=text(name,12,false);title.setPadding(dp(12),dp(24),0,dp(6));content.addView(title);}

  private void row(LinearLayout parent,int icon,String title,String subtitle,Runnable action){
    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(10),dp(2),dp(10));row.setMinimumHeight(dp(64));
    ImageView leading=new ImageView(this);leading.setImageDrawable(UiKit.icon(this,icon,ink,26));row.addView(leading,new LinearLayout.LayoutParams(dp(36),dp(32)));
    LinearLayout labels=column();labels.setPadding(dp(12),0,dp(8),0);labels.addView(text(title,17,true));if(!subtitle.isEmpty())labels.addView(text(subtitle,12,false));row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
    ImageView chevron=new ImageView(this);chevron.setImageDrawable(UiKit.icon(this,R.drawable.ic_chevron_right,palette.muted,22));row.addView(chevron,new LinearLayout.LayoutParams(dp(24),dp(24)));
    row.setBackground(UiKit.ripple(this,palette.surface,12));row.setFocusable(true);row.setClickable(true);row.setOnClickListener(v->{try{action.run();}catch(RuntimeException e){error(e);}});parent.addView(row);
    View line=new View(this);line.setBackgroundColor(palette.outline);parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
  }

  private void connections(){
    clearContent();subpage="Connections";content.addView(button("Back to profile",this::render));content.addView(text("Connected apps & devices",28,true));
    content.addView(text("Bring completed activities into your history and connect a compatible heart-rate sensor for live runs.",16,false));
    section("APPLICATIONS");LinearLayout apps=card();row(apps,R.drawable.ic_directions_run,"Strava","Official OAuth via your configured service",()->openFeatures("strava"));row(apps,R.drawable.ic_favorite,"Health Connect",HealthSync.status(this),this::healthConnect);
    section("WATCHES");LinearLayout watches=card();row(watches,R.drawable.ic_watch,"Garmin Connect","Account sync · approval required",this::garmin);
    row(watches,R.drawable.ic_favorite,"Live heart-rate sensor",tracker==null?"Bluetooth sensor pairing":tracker.hrStatus(),this::wearable);
    section("ACTIVITY FILES");LinearLayout files=card();row(files,R.drawable.ic_file_download,"Import GPX / TCX / FIT","Garmin Connect exports and compatible files",this::importActivity);
  }

  private void healthConnect(){subpage="Connections";startActivity(new Intent(this,HealthConnectActivity.class));}

  private void garmin(){
    clearContent();subpage="Garmin";content.addView(button("Back to connections",this::connections));content.addView(text("Garmin Connect",28,true));
    LinearLayout status=card();status.addView(text("Not connected",18,true));status.addView(text("Automatic workout and activity sync is not available yet. You can import completed activities below.",15,false));
    section("AVAILABLE NOW");LinearLayout available=card();row(available,R.drawable.ic_file_download,"Import completed activity","Choose an exported GPX, TCX or FIT file",this::importActivity);row(available,R.drawable.ic_favorite,"Connect a heart-rate sensor","Bluetooth broadcasting is separate from account sync",this::wearable);
    content.addView(text("Export GPX, TCX or FIT from Garmin Connect on the web, then choose the file here. Only single-session running FIT files are supported; unknown developer data is ignored.",14,false));
    content.addView(button("About automatic Garmin sync",()->new AlertDialog.Builder(this).setTitle("Garmin sync availability").setMessage("Automatic sync needs official Garmin Activity and Training API approval, partner credentials and a configured service. Those are not available for OpenRun yet. Opening the Garmin website does not connect your account.").setPositiveButton("OK",null).show()));
    content.addView(button("Open Garmin Connect website",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://connect.garmin.com/")))));
  }

  private void calendar(){

    JSONObject race=store.v103().optJSONObject("race");if(race!=null&&race.optString("planId").equals(store.planId()))content.addView(text("Race day · "+race.optString("name","Your race")+" · "+race.optString("date"),19,true));
    content.addView(text("Calendar",28,true));LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.addView(UiKit.iconButton(this,palette,R.drawable.ic_chevron_left,"Previous calendar week",()->{calendarStart=calendarStart.minusWeeks(1);render();}),new LinearLayout.LayoutParams(dp(48),dp(48)));
    TextView range=text(calendarStart.format(DateTimeFormatter.ofPattern("d MMM"))+" – "+calendarStart.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM")),18,true);range.setGravity(Gravity.CENTER);bar.addView(range,new LinearLayout.LayoutParams(0,-2,1));
    bar.addView(UiKit.iconButton(this,palette,R.drawable.ic_chevron_right,"Next calendar week",()->{calendarStart=calendarStart.plusWeeks(1);render();}),new LinearLayout.LayoutParams(dp(48),dp(48)));content.addView(bar);

    content.addView(button("Export training calendar",this::exportCalendar));
    content.addView(button("This week",()->{calendarStart=LocalDate.now().with(DayOfWeek.MONDAY);render();}));
    for(int i=0;i<7;i++){
      LocalDate date=calendarStart.plusDays(i);LinearLayout day=card();day.addView(text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))+(date.equals(LocalDate.now())?" · TODAY":""),15,true));int count=0;
      for(var session:sessions())if(session.date().equals(date)&&!store.skipped(key(session))){count++;boolean done=store.completed(key(session));row(day,R.drawable.ic_directions_run,session.title(),(done?"Completed · ":"Planned · ")+PlanEngine.distance(session.km()),()->detail(session));}
      for(var run:store.runs())if(run.date().equals(date.toString())){count++;day.addView(text("Saved run · "+activityDistance(run.km())+" · "+duration(run.seconds()),14,false));}
      for(var other:store.otherActivities())if(other.date().equals(date)){count++;day.addView(text(other.type()+" · "+other.minutes()+" min · "+(other.completed()?"completed":"planned"),14,false));}
      if(count==0)day.addView(text("Rest & recovery",15,false));
    }
  }


  private void emptyProgress(){
    LinearLayout empty=card();ImageView symbol=new ImageView(this);symbol.setImageDrawable(UiKit.icon(this,R.drawable.ic_monitoring,green,40));empty.addView(symbol,new LinearLayout.LayoutParams(dp(44),dp(52)));
    empty.addView(text("Your progress starts with one run",23,true));empty.addView(text("Record a run or import an activity to see your distance, training time and weekly trends.",15,false));
    empty.addView(button("Start free run",()->prepareStart("free:"+UUID.randomUUID(),0)));empty.addView(button("Import activity",this::importActivity));
  }
  private void metric(LinearLayout parent,String value,String title){
    LinearLayout cell=column();cell.setPadding(0,dp(8),dp(8),dp(8));TextView amount=text(value,20,true);amount.setSingleLine(true);amount.setAutoSizeTextTypeUniformWithConfiguration(11,UiKit.textSizeSp(20),1,android.util.TypedValue.COMPLEX_UNIT_SP);cell.addView(amount);cell.addView(text(title,11,false));parent.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
  }
  private void performance(){
    List<RunStore.Run> runs=store.runs();if(runs.isEmpty()){emptyProgress();return;}
    LocalDate monday=LocalDate.now().with(DayOfWeek.MONDAY);double longest=0,total=0;long time=0;int count=0;
    for(var run:runs){longest=Math.max(longest,run.km());LocalDate date=LocalDate.parse(run.date());if(!date.isBefore(monday)&&date.isBefore(monday.plusDays(7))){total+=run.km();time+=run.seconds();count++;}}
    LinearLayout summary=card();summary.addView(text("This week",20,true));LinearLayout metrics=new LinearLayout(this);metric(metrics,String.format(Locale.US,"%.2f",total),"KILOMETRES");metric(metrics,duration(time),"TIME");metric(metrics,""+count,"ACTIVITIES");summary.addView(metrics);
    LinearLayout trends=card();trends.addView(text("Weekly distance",20,true));trends.addView(text("Last 6 weeks",13,false));double[] kms=new double[6];double max=1;
    for(int i=0;i<6;i++){LocalDate start=monday.minusWeeks(5-i);for(var run:runs){LocalDate date=LocalDate.parse(run.date());if(!date.isBefore(start)&&date.isBefore(start.plusWeeks(1)))kms[i]+=run.km();}max=Math.max(max,kms[i]);}
    for(int i=0;i<6;i++){trends.addView(text(monday.minusWeeks(5-i).format(DateTimeFormatter.ofPattern("d MMM"))+" · "+activityDistance(kms[i]),13,false));ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)(kms[i]/max*1000));bar.setProgressTintList(ColorStateList.valueOf(green));bar.setProgressBackgroundTintList(ColorStateList.valueOf(palette.outline));trends.addView(bar,new LinearLayout.LayoutParams(-1,dp(6)));}
    LinearLayout best=card();best.addView(text("Longest saved run",16,false));best.addView(text(activityDistance(longest),23,true));recordAnalytics();
  }


  private void openFeatures(String screen){refreshFeatures=true;startActivity(new Intent(this,FeaturesActivity.class).putExtra("screen",screen));}
  private void filterControl(LinearLayout row,List<String> choices,String selected,java.util.function.Consumer<String> select){Spinner spinner=new Spinner(this);spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices));spinner.setSelection(Math.max(0,choices.indexOf(selected)));spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> parent,View view,int index,long id){String value=choices.get(index);if(!value.equals(selected))select.accept(value);}public void onNothingSelected(AdapterView<?> p){}});row.addView(spinner,new LinearLayout.LayoutParams(0,dp(52),1));}
  private void exportCalendar(){startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/calendar").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"OpenRun-training.ics"),204);}
  private void exportHealth(RunStore.Run run){startActivity(new Intent(this,HealthConnectActivity.class).putExtra("exportId",run.sessionId()));}
  private void uploadStrava(RunStore.Run run){
    CloudClient client=new CloudClient(this);if(!client.vault.read().has("token")){openFeatures("strava");return;}
    new AlertDialog.Builder(this).setTitle("Upload this route to Strava?").setMessage("This sends the activity’s precise GPS route through your configured OpenRun server to Strava. Strava applies your account privacy settings.").setNegativeButton("Cancel",null).setPositiveButton("Upload",(d,w)->worker.submit(()->{try{byte[] gpx=ActivityFiles.gpx(TrackingService.readRoute(this,run.routeFile())).getBytes(StandardCharsets.UTF_8);JSONObject result=client.request("POST","/v1/strava/upload",gpx,"application/gpx+xml",-1).json();if(!result.isNull("error")&&!result.optString("error").isEmpty())throw new IllegalStateException(result.optString("error"));JSONObject settings=client.vault.read();settings.put("lastUpload",result.getLong("upload_id"));client.vault.write(settings);runOnUiThread(()->Toast.makeText(this,"Upload queued. Check its result in Strava connection settings.",Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->error(e));}})).show();
  }
  private void weeklyReview(){
    var entries=store.runs().stream().map(r->new TrainingReview.Entry(LocalDate.parse(r.date()),r.km(),r.rpe(),r.fatigue(),!r.source().startsWith("Health Connect")&&!r.source().equals("Strava"))).collect(java.util.stream.Collectors.toList());var review=TrainingReview.review(entries,LocalDate.now());LinearLayout card=card();card.addView(text("Weekly training review",20,true));card.addView(text(review.reason(),14,false));
    if(review.reduce()&&!store.resting())card.addView(button("Review workload reduction",()->new AlertDialog.Builder(this).setTitle("Reduce upcoming workload?").setMessage(review.reason()+"\n\nApplies to tomorrow through the following six days. No missed workouts are added.").setNegativeButton("Keep plan",null).setPositiveButton("Apply 20% reduction",(d,w)->{if(store.resting()){error(new IllegalStateException("Training is paused; review the hold first."));return;}store.adapt(new AdaptiveCoach.Advice(.8,false,review.reason()));render();}).show()));
  }

  private void recordAnalytics(){
    List<RunStore.Run> runs=store.runs();LinearLayout trends=card();trends.addView(text("Keep improving",21,true));trends.addView(text("Review consistency, effort and recovery over time. Walking minutes stay separate from running mileage.",15,false));trends.addView(button("Review indoor movement",()->openTraining("indoorHistory")));
    TextView records=text("Calculating GPS best efforts…",15,false);LinearLayout best=card();best.addView(text("GPS best efforts",21,true));best.addView(records);best.addView(text("Derived from continuous timestamped routes, not average-pace extrapolation. Signal gaps, pauses and implausible jumps break a segment. These are GPS estimates, not certified race results.",13,false));
    worker.submit(()->{try{Map<Double,Double> values=new TreeMap<>();for(var run:runs)if(!run.routeFile().isEmpty()){var points=TrackingService.readRoute(this,run.routeFile());var fixes=points.stream().map(p->new BestEfforts.Fix(p.lat(),p.lon(),p.time(),p.segment())).collect(java.util.stream.Collectors.toList());BestEfforts.calculate(fixes).forEach((distance,time)->values.merge(distance,time,Math::min));}StringBuilder result=new StringBuilder();for(var item:values.entrySet())result.append(String.format(Locale.US,"%.3g km",item.getKey()/1000)).append(" · ").append(duration(Math.round(item.getValue()))).append("\n");String label=result.length()==0?"No continuous route is long enough for a distance record yet.":result.toString().trim();runOnUiThread(()->{if(!isDestroyed())records.setText(label);});}catch(Exception e){runOnUiThread(()->records.setText("A saved route could not be read. Your other history is unchanged."));}});
  }

  private void openHealthSettings(){
    boolean opened=SettingsNavigator.openHealth(action->{try{startActivity(new Intent(action));return true;}catch(ActivityNotFoundException missing){return false;}});
    if(!opened)new AlertDialog.Builder(this).setTitle("Open Health Connect manually").setMessage("Open Android Settings and search for Health Connect, then choose App permissions → OpenRun.").setPositiveButton("OK",null).show();
  }

  @Override public void onBackPressed(){
    if(creatingPlan||config==null){if(setupStep>0){setupStep--;render();return;}if(config!=null){creatingPlan=false;render();return;}if(setupStep==0){setupStep=-1;render();return;}super.onBackPressed();return;}
    if(tab.equals("Calendar")){tab="Plan";render();return;}
    if(subpage.equals("Schedule")){planSchedule=false;render();return;}
    if(!subpage.isEmpty()){if(subpage.equals("Sensor")&&tab.equals("Run"))render();else if(subpage.equals("Health")||subpage.equals("Garmin")||subpage.equals("Sensor"))connections();else render();return;}
    if(!tab.equals("Today")){tab="Today";render();return;}super.onBackPressed();
  }

  @Override protected void onResume(){super.onResume();if(store!=null&&resumeRunFromSettings){resumeRunFromSettings=false;tab="Run";if(gpsEnabled()&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){try{prepareStart(pendingId,pendingTarget);}catch(RuntimeException e){error(e);}}else{render();Toast.makeText(this,"Run has not started. Enable device Location and precise location permission, then try again.",Toast.LENGTH_LONG).show();}return;}if(store!=null)Reminders.schedule(this);if(store!=null&&refreshFeatures){refreshFeatures=false;config=store.config();render();}else if(store!=null&&subpage.equals("Health"))healthConnect();}

  private void wearable() {
    dashboard = null;
    clearContent();
    content.addView(button("Back", ()->{if(tab.equals("Run"))render();else connections();}));
    subpage="Sensor";
    content.addView(text("Live heart-rate sensor", 28, true));
    content.addView(
        text(
            "Instinct 2 and later compatible models can broadcast Bluetooth HR. The original"
                + " Instinct uses ANT+; this app does not include ANT+. Enable Broadcast Heart Rate"
                + " on your watch, then start an outdoor run or walk before scanning.",
            16,
            false));
    content.addView(text(tracker == null ? "Recorder connecting…" : tracker.hrStatus(), 16, true));
    content.addView(
        button(
            "Scan Bluetooth HR sensors",
            () -> {
              if (!bleGranted()) {
                if (Build.VERSION.SDK_INT >= 31)
                  requestPermissions(
                      new String[] {
                        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
                      },
                      102);
                else
                  requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 102);
                return;
              }
              scanSensors();
            }));
    if (tracker != null)
      for (var device : tracker.sensors()) {
        String name;
        try {
          name = device.getName();
        } catch (SecurityException e) {
          name = null;
        }
        String label = (name == null ? "Heart-rate sensor" : name) + " · " + device.getAddress();
        content.addView(
            button(
                label,
                () -> {
                  tracker.connectSensor(device);
                  Toast.makeText(
                          this, "Connecting. Return to Run for live heart rate.", Toast.LENGTH_LONG)
                      .show();
                }));
      }
    content.addView(button("Refresh sensor list", this::wearable));
    content.addView(
        button(
            "Disconnect sensor",
            () -> {
              if (tracker != null) tracker.disconnectSensor();
              wearable();
            }));
    content.addView(
        text(
            "Bluetooth pairing supplies live heart rate only. It does not link a Garmin account"
                + " or transfer workouts.",
            15,
            false));
  }

  private boolean bleGranted() {
    return Build.VERSION.SDK_INT >= 31
        ? checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED
        : checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED;
  }

  private void scanSensors() {
    if (tracker == null) throw new IllegalStateException("Recorder is connecting. Try again.");
    tracker.scanSensors();
    wearable();
    handler.postDelayed(
        () -> {
          if (!isFinishing())
            Toast.makeText(this, "Scan complete. Tap Refresh sensor list.", Toast.LENGTH_LONG)
                .show();
        },
        12500);
  }

  private void importActivity() {
    Intent i =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i, 200);
  }

  private void export(String route) {
    exportingRoute = route;
    Intent i =
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("application/gpx+xml")
            .putExtra(Intent.EXTRA_TITLE, "OpenRun-run.gpx")
            .addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i, 201);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    if(request==207){if(result==RESULT_OK){creatingPlan=false;setupStep=0;tab="Plan";planSchedule=true;config=store.config();week=0;render();}return;}
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null) return;
    Uri uri = data.getData();
    if(request==204){worker.submit(()->{try{String ics=CalendarExport.build(store.planId(),sessions().stream().filter(session->!store.skipped(key(session))).collect(java.util.stream.Collectors.toList()));try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();out.write(ics.getBytes(StandardCharsets.UTF_8));}runOnUiThread(()->Toast.makeText(this,"Calendar file exported",Toast.LENGTH_LONG).show());}catch(Exception e){runOnUiThread(()->error(e));}});return;}
    if (request == 202 || request == 203) {
      backupResult(request, uri);
      return;
    }
    if (request == 200) {
      Toast.makeText(this, "Reading activity…", Toast.LENGTH_SHORT).show();
      worker.submit(
          () -> {
            try {
              byte[] bytes;
              try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Cannot open file");
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] block = new byte[8192];
                int count;
                while ((count = in.read(block)) != -1) {
                  if (buffer.size() + count > 10 * 1024 * 1024)
                    throw new IOException("Activity exceeds 10 MB.");
                  buffer.write(block, 0, count);
                }
                bytes = buffer.toByteArray();
              }
              ActivityFiles.Imported imported = ActivityFiles.parse(bytes);
              runOnUiThread(
                  () -> {
                    if (!isFinishing()) chooseImport(imported);
                  });
            } catch (Exception e) {
              runOnUiThread(() -> error(e));
            }
          });
    }
    if (request == 201) {
      String route = exportingRoute;
      worker.submit(
          () -> {
            try {
              String xml = ActivityFiles.gpx(TrackingService.readRoute(this, route));if(exportingWalk)xml=xml.replace("OpenRun run</name>","OpenRun walk</name><type>Walking</type>");
              try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Cannot create GPX");
                out.write(xml.getBytes(StandardCharsets.UTF_8));
              }
              runOnUiThread(() -> Toast.makeText(this, "GPX exported", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
              runOnUiThread(() -> error(e));
            }
          });
    }
  }

  private void chooseImport(ActivityFiles.Imported imported) {
    long started=imported.points().isEmpty()?0:imported.points().get(0).time();long ended=imported.points().isEmpty()?0:imported.points().get(imported.points().size()-1).time();
    var candidate=new RunStore.Run("garmin:"+imported.id(),imported.date(),imported.km(),(int)Math.max(1,imported.seconds()/60),5,2,false,"Garmin file","",0,imported.seconds());
    JSONObject match=store.possibleImport(candidate,false,RunStore.origin("Garmin file",imported.id(),started,Math.max(started,ended)));
    if(match!=null){new AlertDialog.Builder(this).setTitle("This may already be saved").setMessage("A saved activity on "+match.optString("date")+" has similar timing or totals. Importing it again would increase your totals.").setNegativeButton("Keep existing",null).setPositiveButton("Import as separate",(d,w)->chooseSeparateFileImport(imported)).show();return;}
    chooseSeparateFileImport(imported);
  }
  private void chooseSeparateFileImport(ActivityFiles.Imported imported) {
    String duplicate = "garmin:" + imported.id();
    if (store.completed(duplicate)) {
      error(new IllegalArgumentException("This exact activity file is already imported."));
      return;
    }
    List<PlanEngine.Session> candidates = new ArrayList<>();
    List<String> names = new ArrayList<>();
    names.add("Save as an unscheduled Garmin run");
    for (var s : sessions())
      if (s.date().toString().equals(imported.date()) && !store.completed(key(s))) {
        candidates.add(s);
        names.add("Match " + s.title() + " · " + PlanEngine.distance(s.km()));
      }
    new AlertDialog.Builder(this)
        .setTitle("Import " + PlanEngine.distance(imported.km()) + " · " + imported.date())
        .setItems(
            names.toArray(new String[0]),
            (d, index) -> {
              try {
                String id = index == 0 ? duplicate : key(candidates.get(index - 1));
                double target = index == 0 ? 0 : candidates.get(index - 1).km();
                // Preserve the import hash separately in source to detect a repeat even when
                // matched to a plan session.
                if (store.runs().stream()
                    .anyMatch(r -> r.source().equals("Garmin " + imported.id())))
                  throw new IllegalArgumentException("This activity has already been imported.");
                String route =
                    imported.points().isEmpty() ? "" : "route-" + UUID.randomUUID() + ".jsonl";
                if (!route.isEmpty())
                  try (FileOutputStream out = openFileOutput(route, MODE_PRIVATE)) {
                    for (var point : imported.points())
                      out.write(
                          (new JSONObject()
                                      .put("lat", point.lat())
                                      .put("lon", point.lon())
                                      .put("time", point.time())
                                      .put("hr", point.hr())
                                      .put("segment", point.segment())
                                  + "\n")
                              .getBytes(StandardCharsets.UTF_8));
                  }
                feedback(
                    id,
                    imported.date(),
                    imported.km(),
                    imported.seconds(),
                    "Garmin " + imported.id(),
                    route,
                    imported.avgHr(),
                    target,
                    null);
              } catch (Exception e) {
                error(e);
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void paceProfile() {
    if (tracker != null && tracker.active())
      throw new IllegalStateException("Finish recording before changing pace targets.");
    clearContent();
    content.addView(button("Back", this::render));
    content.addView(text("Your pace, your baseline.", 28, true));
    PaceProfile current = store.profile();
    Spinner distance = spinner("5 km", "10 km", "Half marathon", "Marathon");
    double[] choices = {5, 10, 21.1, 42.2};
    for (int i = 0; i < choices.length; i++)
      if (Math.abs(current.distanceKm() - choices[i]) < .02) distance.setSelection(i);
    content.addView(distance);
    EditText minutes =
        field(
            "Recent race time in minutes (blank = effort only)",
            current.seconds() > 0 ? String.valueOf(current.seconds() / 60.0) : "");
    CheckBox speed = new CheckBox(this);
    speed.setText(R.string.enable_speed);
    speed.setChecked(current.speedWork());
    content.addView(speed);
    content.addView(
        text(
            "Use a recent result, not an aspirational goal. These pace estimates are a starting"
                + " point. Speed sessions require opt-in, at least 3 runs/week, 15 km/week and a"
                + " 6-week plan. Increase fitness before adding intensity.",
            15,
            false));
    for (var zone :
        new PaceProfile.Zone[] {
          PaceProfile.Zone.EASY,
          PaceProfile.Zone.STEADY,
          PaceProfile.Zone.TEMPO,
          PaceProfile.Zone.INTERVAL
        }) content.addView(text(zone + " · " + current.range(zone).label(), 18, true));
    content.addView(
        button(
            "Save pace profile",
            () -> {
              String value = minutes.getText().toString().trim();
              PaceProfile next =
                  new PaceProfile(
                      choices[distance.getSelectedItemPosition()],
                      value.isEmpty() ? 0 : Math.round(Double.parseDouble(value) * 60),
                      speed.isChecked());
              store.profile(next);
              paceProfile();
              Toast.makeText(this, "Pace profile saved", Toast.LENGTH_SHORT).show();
            }));
  }

  private void showPaceInsight() {
    if (tracker != null && tracker.active()) return;
    var suggestion = store.paceSuggestion();
    if (suggestion == null) return;
    LinearLayout c = card();
    c.addView(text("Pace review", 22, true));
    c.addView(text(suggestion.reason(), 16, false));
    c.addView(
        text("Suggested 5 km benchmark: " + duration(suggestion.benchmarkSeconds()), 17, true));
    c.addView(
        button(
            "Review benchmark update",
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("Update estimated 5 km benchmark?")
                    .setMessage(
                        suggestion.reason()
                            + "\nYour current baseline is "
                            + duration(store.profile().equivalent5k())
                            + ". The proposed baseline is "
                            + duration(suggestion.benchmarkSeconds())
                            + ". Upcoming pace targets will be recalculated.")
                    .setNegativeButton("Keep current", null)
                    .setPositiveButton(
                        "Apply",
                        (dialog, which) -> {
                          store.profile(
                              new PaceProfile(
                                  5, suggestion.benchmarkSeconds(), store.profile().speedWork()));
                          render();
                        })
                    .show()));
  }

  private void ensureIdle() {
    if (tracker != null && tracker.active() || !store.checkpoint().isEmpty() || store.indoorPending())
      throw new IllegalStateException(
          "Finish, recover or discard the recording before backing up or restoring.");
  }

  private void backup() {
    ensureIdle();
    startActivityForResult(
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_TITLE, "OpenRun-backup-" + LocalDate.now() + ".zip")
            .addCategory(Intent.CATEGORY_OPENABLE),
        202);
  }

  private void restoreBackup() {
    ensureIdle();
    startActivityForResult(
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .addCategory(Intent.CATEGORY_OPENABLE),
        203);
  }

  private void backupResult(int request, Uri uri) {
    ensureIdle();
    if (request == 203) {
      new AlertDialog.Builder(this)
          .setTitle("Restore selected OpenRun backup?")
          .setMessage(
              "The selected backup will replace your current plan, pace profile, history and"
                  + " routes. Export a current backup first if you need to keep both. Invalid"
                  + " backups are rejected before existing preferences are replaced.")
          .setNegativeButton("Cancel", null)
          .setPositiveButton("Restore", (dialog, which) -> transferBackup(request, uri))
          .show();
    } else transferBackup(request, uri);
  }

  private void transferBackup(int request, Uri uri) {
    ensureIdle();
    ProgressDialog progress =
        ProgressDialog.show(
            this,
            "OpenRun",
            request == 202 ? "Exporting backup…" : "Validating and restoring…",
            true,
            false);
    worker.submit(
        () -> {
          try {
            if (request == 202) {
              try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Cannot create backup");
                BackupManager.exportTo(this, store, out);
              }
            } else {
              try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Cannot open backup");
                BackupManager.restoreFrom(this, store, input);
              }
            }
            runOnUiThread(
                () -> {
                  if (!isFinishing() && !isDestroyed()) {
                    progress.dismiss();
                    config = store.config();
                    week = 0;
                    render();
                    Toast.makeText(
                            this,
                            request == 202 ? "Backup exported" : "Backup restored",
                            Toast.LENGTH_LONG)
                        .show();
                  }
                });
          } catch (Exception error) {
            runOnUiThread(
                () -> {
                  if (!isFinishing() && !isDestroyed()) {
                    progress.dismiss();
                    error(error);
                  }
                });
          }
        });
  }

  private static String duration(long seconds) {
    return String.format(
        Locale.US, "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
  }

  private void error(Exception e) {
    android.util.Log.w("OpenRun","Action failed",e);
    if (!isFinishing() && !isDestroyed())
      new AlertDialog.Builder(this)
          .setTitle("Could not continue")
          .setMessage(UserErrors.message(e))
          .setPositiveButton("OK", null)
          .show();
  }
}
