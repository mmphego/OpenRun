package app.rwun;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** Android conveniences and mixed-activity planning, separated from recording and training rules. */
public final class V1Activity extends Activity {
  private final UiKit.Palette palette=new UiKit.Palette(true);
  private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();private boolean busy;
  private RunStore store;private LinearLayout body;private TextView status;private String screen;private LocalDate week=LocalDate.now().with(DayOfWeek.MONDAY),draftDate=LocalDate.now();
  private EditText minutes,effort,feedback;private Spinner type,issue;private CheckBox completed,includeDate;private String editing="";private Bundle saved;
  public void onCreate(Bundle state){super.onCreate(state);store=new RunStore(this);screen=getIntent().getStringExtra("screen");if(screen==null||screen.equals("week"))screen="tools";saved=state;if(state!=null){week=LocalDate.parse(state.getString("week",week.toString()));draftDate=LocalDate.parse(state.getString("draftDate",draftDate.toString()));editing=state.getString("editing","");}show();}
  protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putString("week",week.toString());out.putString("draftDate",draftDate.toString());out.putString("editing",editing);if(minutes!=null){out.putString("minutes",minutes.getText().toString());out.putString("effort",effort.getText().toString());out.putInt("type",type.getSelectedItemPosition());out.putBoolean("completed",completed.isChecked());}if(feedback!=null){out.putString("feedback",feedback.getText().toString());out.putInt("issue",issue.getSelectedItemPosition());}if(includeDate!=null)out.putBoolean("includeDate",includeDate.isChecked());}
  private void show(){
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(palette.background);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
    LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.addView(UiKit.iconButton(this,palette,R.drawable.ic_arrow_back,"Back",this::finish),new LinearLayout.LayoutParams(dp(48),dp(48)));bar.addView(UiKit.label(this,switch(screen){case "share"->"Share your effort";case "beta"->"Beta feedback";case "tools"->"Android & privacy";default->"Android & privacy";},18,palette.ink,true));root.addView(bar);
    ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(12),dp(20),dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);status=UiKit.label(this,"",14,palette.primary,false);body.addView(status);try{switch(screen){case "share"->share();case "beta"->beta();case "tools"->tools();default->tools();}}catch(Exception e){status.setText(UserErrors.message(e));}
  }
  private int dp(int n){return UiKit.dp(this,n);}private void title(String s){TextView v=UiKit.label(this,s,22,palette.ink,true);if(Build.VERSION.SDK_INT>=28)v.setAccessibilityHeading(true);body.addView(v);}private void text(String s){body.addView(UiKit.label(this,s,15,palette.muted,false));}
  private void action(String s,Runnable r){body.addView(UiKit.action(this,palette,s,()->{try{if(busy)throw new IllegalStateException("Wait for the current export to finish");r.run();}catch(Exception e){status.setText(UserErrors.message(e));}}));}
  private EditText input(String label,String value,boolean number){text(label);EditText e=new EditText(this);e.setContentDescription(label);e.setTextColor(palette.ink);e.setText(value);e.setInputType(number?android.text.InputType.TYPE_CLASS_NUMBER:android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);e.setMinHeight(dp(48));body.addView(e);return e;}
  private Spinner choices(String label,List<String> values,int position){text(label);Spinner s=new Spinner(this);s.setMinimumHeight(dp(48));s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));s.setSelection(Math.max(0,Math.min(position,values.size()-1)));body.addView(s);return s;}
  private CheckBox check(String label,boolean on){CheckBox c=new CheckBox(this);c.setText(label);c.setTextColor(palette.ink);c.setMinHeight(dp(48));c.setChecked(on);body.addView(c);return c;}

  private void tools(){
    title("Useful on your phone");action("Add workout widget",()->{AppWidgetManager manager=getSystemService(AppWidgetManager.class);if(!manager.isRequestPinAppWidgetSupported())throw new IllegalStateException("This launcher does not support pinning. Add OpenRun from its widget picker.");manager.requestPinAppWidget(new ComponentName(this,WorkoutWidget.class),null,null);});
    CheckBox haptic=check("Haptic feedback on app controls",store.v1().optBoolean("haptics",true));haptic.setOnCheckedChangeListener((b,on)->setting("haptics",on));
    title("Local beta measurements");text("Optional daily event counts for this installation only. Off by default. No advertising IDs, names, location, health samples or automatic uploads. Metrics are excluded from training backups. Turning this off erases the counts.");
    CheckBox metrics=check("Collect local usage counts",store.v1().optBoolean("metrics",false));metrics.setOnCheckedChangeListener((b,on)->{synchronized(RunStore.LOCK){JSONObject data=store.v1();try{data.put("metrics",on);if(!on)data.remove("events");store.v1(data);if(on)store.event("app_open");}catch(Exception e){status.setText(UserErrors.message(e));}}});
    action("View usage summary",()->new AlertDialog.Builder(this).setTitle("Local usage only").setMessage(V1State.report(store.v1(),LocalDate.now())).setPositiveButton("Done",null).setNeutralButton("Share summary",(d,w)->sendText(V1State.report(store.v1(),LocalDate.now()))).show());
    title("Recording readiness");text(diagnostics());action("Open app permission & battery settings",()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));text("Battery optimisation settings are device-specific. OpenRun does not request unrestricted battery access. Interrupted recording checkpoints can be recovered on the Run screen.");
    action("Beta feedback",()->{screen="beta";show();});
  }
  private void setting(String key,boolean value){synchronized(RunStore.LOCK){try{JSONObject d=store.v1();d.put(key,value);store.v1(d);}catch(Exception e){status.setText(UserErrors.message(e));}}}
  private String diagnostics(){return RecordingDiagnostics.report(this);}

  private void beta(){title("Make the next run better");text("Describe what happened and what you expected. Preview the report before choosing where to send it. No report is transmitted automatically.");issue=choices("Area",List.of("Recording / GPS","Plan / coaching","Connection / sync","Interface / accessibility","Other"),saved==null?0:saved.getInt("issue"));feedback=input("Steps, expected result and actual result",saved==null?"":saved.getString("feedback",""),false);feedback.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3000)});action("Preview feedback report",()->{String message=feedback.getText().toString().trim();if(message.length()<10)throw new IllegalArgumentException("Describe the issue in at least 10 characters");String report="OpenRun "+RecordingDiagnostics.version(this)+" feedback\nArea: "+issue.getSelectedItem()+"\n\n"+message+"\n\nDevice checks (no routes or health samples):\n"+diagnostics();new AlertDialog.Builder(this).setTitle("Review before sharing").setMessage(report).setNegativeButton("Edit",null).setPositiveButton("Choose destination",(d,w)->sendText(report)).show();});}
  private void sendText(String value){try{startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,value),"Share with your chosen app"));}catch(Exception e){status.setText("No sharing app is available.");}}
  private RunStore.Run sharedRun(){for(var run:store.runs())if(run.sessionId().equals(getIntent().getStringExtra("id")))return run;throw new IllegalStateException("This activity is no longer available");}
  private void share(){RunStore.Run run=sharedRun();title("Your effort, your story");text("This card excludes the route, location, heart rate and account information. The date is optional. Save a PNG, then choose where to share it.");includeDate=check("Include activity date",saved!=null&&saved.getBoolean("includeDate"));ImageView preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setContentDescription("OpenRun activity card preview: "+PlanEngine.distance(run.km())+", recorded time "+run.seconds()+" seconds. No route included.");body.addView(preview,new LinearLayout.LayoutParams(-1,-2));Runnable draw=()->{Bitmap b=card(run,includeDate.isChecked());preview.setImageBitmap(b);};includeDate.setOnCheckedChangeListener((b,on)->draw.run());draw.run();action("Save activity card",()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("image/png").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"OpenRun-activity.png"),8102));}
  static Bitmap card(RunStore.Run run,boolean date){Bitmap image=Bitmap.createBitmap(1080,1350,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(image);c.drawColor(Color.rgb(23,27,35));Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.rgb(72,216,193));c.drawRoundRect(64,64,1016,1286,54,54,p);p.setColor(Color.rgb(23,27,35));c.drawRoundRect(68,68,1012,1282,50,50,p);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setColor(Color.rgb(72,216,193));p.setTextSize(50);c.drawText("rwun / YOUR OWN PACE",116,174,p);p.setColor(Color.rgb(228,233,241));p.setTextSize(82);c.drawText("Made room",116,360,p);c.drawText("for a run.",116,460,p);p.setTextSize(134);c.drawText(String.format(Locale.ROOT,"%.2f",run.km()),116,715,p);p.setTextSize(42);c.drawText("KILOMETRES",120,780,p);p.setTextSize(74);long s=run.seconds();c.drawText(String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,(s/60)%60,s%60),116,960,p);p.setTextSize(32);p.setColor(Color.rgb(165,175,191));c.drawText("RECORDED TIME",120,1014,p);if(date)c.drawText(run.date(),120,1135,p);p.setTextSize(28);c.drawText("EVERY SESSION COUNTS.",120,1210,p);return image;}
  private Intent imageIntent(Uri uri){Intent intent=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("OpenRun activity card",uri));return intent;}
  @Override protected void onDestroy(){worker.shutdown();super.onDestroy();}
  @Override public void finish(){if(busy){status.setText("Wait for the image export to finish.");return;}super.finish();}
  protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);if(request!=8102||result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri uri=data.getData();RunStore.Run run;try{run=sharedRun();}catch(Exception e){status.setText(UserErrors.message(e));return;}
    boolean date=includeDate.isChecked();busy=true;status.setText("Saving image…");worker.submit(()->{
      try{Bitmap b=card(run,date);try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null||!b.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Unable to save card");}finally{b.recycle();}store.event("card_saved");
        runOnUiThread(()->{busy=false;if(isDestroyed()||isFinishing())return;status.setText("Card saved. Review before sharing.");new AlertDialog.Builder(this).setTitle("Card saved").setMessage("The image contains distance, recorded time and the date only if selected. Your chosen destination controls who can see it.").setNegativeButton("Done",null).setPositiveButton("Share image",(d,w)->{try{startActivity(Intent.createChooser(imageIntent(uri),"Share activity card"));}catch(Exception e){status.setText("Saved; no image-sharing app is available.");}}).show();});
      }catch(Exception e){runOnUiThread(()->{busy=false;if(!isDestroyed()&&!isFinishing())status.setText(UserErrors.message(e));});}
    });
  }
}
