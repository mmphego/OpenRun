package app.rwun;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit read/write entry point. Platform permission grants alone never initiate a write. */
public class HealthConnectActivity extends Activity {
    private static final int READ=201, WRITE=202, HEART_RATE=203, BACKGROUND=204;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final UiKit.Palette palette=new UiKit.Palette(true);
    private LinearLayout body;
    private String message="", pendingId="", pendingFingerprint="";
    private int generation,readGeneration;
    private boolean reading;
    private java.util.concurrent.CompletableFuture<String> activeRead;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if(saved!=null){message=saved.getString("message","");pendingId=saved.getString("pendingId","");pendingFingerprint=saved.getString("pendingFingerprint","");}
        show();
        if(saved==null&&getIntent().hasExtra("exportId"))preview(getIntent().getStringExtra("exportId"));
    }
    @Override protected void onResume(){super.onResume();if(body!=null)show();}
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putString("message",message);saved.putString("pendingId",pendingId);saved.putString("pendingFingerprint",pendingFingerprint);}
    @Override protected void onDestroy(){generation++;readGeneration++;worker.shutdownNow();super.onDestroy();}
    protected boolean healthAvailable(){return HealthAccess.available(this);}
    private boolean closed(){return isDestroyed()||isFinishing();}
    private void text(String label,int size){body.addView(UiKit.label(this,label,size,palette.ink,size>=18));}
    private Button action(String label,Runnable click){Button b=UiKit.action(this,palette,label,click);body.addView(b);return b;}
    private void show(){
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=UiKit.dp(this,20);body.setPadding(pad,pad,pad,pad);body.setBackgroundColor(palette.background);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});setContentView(scroll);
        action("Back",this::finish);text("Health Connect",24);text(HealthAccess.status(this,healthAvailable()),14);
        if(!message.isEmpty()){text("Health Connect result",18);text(message,14);}
        text("Read activities",20);
        text("Choose what OpenRun can read. Runs and walks import locally with duplicate review; other selected readings show a daily summary.",14);
        if(healthAvailable()){
            toggle("Read from Health Connect",HealthPreferences.anyReadGranted(this),enabled->{
                if(enabled)chooseReadData();else{
                    HealthPreferences.enabled(this,false,false);HealthJob.schedule(this,false);
                    if(activeRead!=null)activeRead.cancel(false);reading=false;
                    readGeneration++;message="Reading paused in OpenRun. Saved activities are kept. System grants remain until changed in Health Connect.";show();
                }
            });
            text(HealthPreferences.summary(this),13);
            action("Choose data to read",this::chooseReadData);
            action(HealthSync.busy()?"Import in progress…":"Read selected data now",this::read).setEnabled(HealthPreferences.anyReadGranted(this)&&!HealthSync.busy()&&!reading);
            if(HealthAccess.backgroundSupported(this)){
                boolean scheduled=getSystemService(android.app.job.JobScheduler.class).getPendingJob(81)!=null;
                toggle("Background activity imports",scheduled,enabled->{
                    if(!enabled){HealthJob.schedule(this,false);show();return;}
                    if(!HealthSync.granted(this)){message="Select and allow runs, walks & distance before enabling background imports.";show();return;}
                    new AlertDialog.Builder(this).setTitle("Allow background import?").setMessage("Import recent runs and walks about every six hours. Other health summaries stay foreground-only. Android controls timing.")
                        .setNegativeButton("Cancel",(d,w)->show()).setPositiveButton("Continue",(d,w)->requestPermissions(new String[]{HealthJob.BACKGROUND},BACKGROUND)).setOnCancelListener(d->show()).show();
                });
            }else text("Background imports are unavailable on this Health Connect version.",13);
        }
        text("Write activities",20);
        text("Only original OpenRun runs and walks can be written. Each activity is reviewed before export; enabling access sends nothing.",14);
        if(healthAvailable()){
            toggle("Write to Health Connect",HealthExport.granted(this),enabled->{
                pendingId="";pendingFingerprint="";
                if(!enabled){HealthPreferences.enabled(this,true,false);message="Future writes paused. A write already sent may finish. Existing Health Connect records are kept.";show();return;}
                new AlertDialog.Builder(this).setTitle("Choose data to write")

                    .setMultiChoiceItems(new String[]{"Runs, walks & recorded distance"},new boolean[]{true},(d,w,checked)->((AlertDialog)d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked))
                    .setNegativeButton("Cancel",(d,w)->show()).setPositiveButton("Allow selected data",(d,w)->{
                        HealthPreferences.enabled(this,true,true);requestPermissions(HealthExport.PERMISSIONS,WRITE);
                    }).setOnCancelListener(d->show()).show();
            });
            action(HealthExport.busy()?"Write in progress…":"Choose activity to write",this::choose).setEnabled(HealthExport.granted(this)&&!HealthExport.busy());
        }
        text("Supported writes: activity sessions and recorded distance only. Time-only activities create no distance record. OpenRun does not export calories or health samples it has not recorded.",13);
        text("Turning a toggle off pauses that direction in OpenRun. To revoke Android permissions, open Health Connect access below. Routes, steps, calories and heart-rate samples are never exported.",13);

        action("Manage Health Connect access",()->SettingsNavigator.openHealth(a->{try{startActivity(new Intent(a));return true;}catch(android.content.ActivityNotFoundException|SecurityException missing){return false;}}));
        action("Health data & privacy",()->startActivity(new Intent(this,HealthPrivacyActivity.class)));
        if(new RunStore(this).pendingImports().length()>0)action("Review possible duplicates",()->startActivity(new Intent(this,ImportReviewActivity.class)));
    }
    private void toggle(String label,boolean checked,java.util.function.Consumer<Boolean> change){
        Switch control=new Switch(this);control.setText(label);control.setTextSize(17);control.setTextColor(palette.ink);
        control.setMinHeight(UiKit.dp(this,56));control.setPadding(0,UiKit.dp(this,8),0,UiKit.dp(this,8));
        control.setChecked(checked);control.setOnCheckedChangeListener((button,value)->change.accept(value));body.addView(control);
    }
    private void chooseReadData(){
        boolean[] selected=new boolean[HealthPreferences.LABELS.length];
        for(int i=0;i<selected.length;i++)selected[i]=HealthPreferences.selected(this,i);
        new AlertDialog.Builder(this).setTitle("Choose data to read")
            .setMultiChoiceItems(HealthPreferences.LABELS,selected,(d,index,checked)->selected[index]=checked)
            .setNegativeButton("Cancel",(d,w)->show()).setPositiveButton("Allow selected data",(d,w)->{
                boolean any=false;for(boolean choice:selected)any|=choice;
                if(activeRead!=null)activeRead.cancel(false);reading=false;readGeneration++;
                HealthPreferences.select(this,selected);HealthPreferences.enabled(this,false,any);
                if(!selected[0]||!any)HealthJob.schedule(this,false);
                if(!any){message="No read data selected. Reading paused.";show();return;}
                requestPermissions(HealthPreferences.requested(this),READ);
            }).setOnCancelListener(d->show()).show();
    }
    protected java.util.concurrent.CompletableFuture<String> readSummary(){return HealthSummary.read(getApplicationContext());}
    protected java.util.concurrent.CompletableFuture<String> importActivities(){return HealthSync.sync(getApplicationContext(),new RunStore(this));}
    private void read(){
        if(reading||HealthSync.busy())return;
        reading=true;
        int ticket=++readGeneration;message="Reading selected data…";show();
        java.util.concurrent.CompletableFuture<String> activities=HealthSync.granted(this)?importActivities():
            java.util.concurrent.CompletableFuture.completedFuture("");
        activeRead=activities;
        var summary=readSummary();
        activities.handle((value,error)->error==null?value:"Activity import stopped. Saved activities are kept. "+UserErrors.message(error))
            .thenCombine(summary.exceptionally(error->"Selected health summary unavailable. "+UserErrors.message(error)),(a,b)->a+(a.isEmpty()||b.isEmpty()?"":"\n\n")+b)
            .whenComplete((value,error)->runOnUiThread(()->{
                if(closed()||ticket!=readGeneration)return;
                reading=false;
                message=error==null?(value.isEmpty()?"No selected data is currently allowed.":value):UserErrors.message(error);show();
            }));
    }
    private void choose(){
        int ticket=++generation;message="Loading original recordings…";show();
        worker.execute(()->{
            try{List<HealthWritePlan> choices=HealthWritePlan.available(this,new RunStore(this));runOnUiThread(()->{
                if(closed()||ticket!=generation)return;
                if(choices.isEmpty()){message="No exportable original recordings. Record a run/walk first. Older entries without real timestamps remain local.";show();return;}
                String[] labels=choices.stream().map(p->p.start().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"))+" · "+p.title()+" · "+(p.km()==null?"time only":PlanEngine.distance(p.km()))).toArray(String[]::new);
                message="Choose an activity, then review what will be written.";show();
                new AlertDialog.Builder(this).setTitle("Choose original recording").setItems(labels,(d,w)->review(choices.get(w))).setNegativeButton("Cancel",null).show();
            });}catch(Exception e){report(e,ticket);}
        });
    }
    private void preview(String id){
        int ticket=++generation;message="Preparing activity review…";show();
        worker.execute(()->{try{HealthWritePlan plan=HealthWritePlan.find(this,new RunStore(this),id);runOnUiThread(()->{if(!closed()&&ticket==generation)review(plan);});}catch(Exception e){report(e,ticket);}});
    }
    private void review(HealthWritePlan plan){
        new AlertDialog.Builder(this).setTitle("Write this activity?").setMessage(plan.summary()).setNegativeButton("Cancel",null)
                .setPositiveButton("Write activity",(d,w)->{
                    pendingId=plan.id();pendingFingerprint=plan.fingerprint();
                    if(!HealthPreferences.enabled(this,true)){pendingId="";pendingFingerprint="";message="Enable Write to Health Connect before reviewing an export.";show();}
                    else if(!HealthExport.granted(this))requestPermissions(HealthExport.PERMISSIONS,WRITE);else write();
                }).show();
    }
    private void write(){
        String id=pendingId,fingerprint=pendingFingerprint;pendingId="";pendingFingerprint="";
        message="Writing reviewed activity…";show();
        HealthExport.write(getApplicationContext(),id,fingerprint).whenComplete((value,error)->runOnUiThread(()->{
            if(closed())return;message=error==null?value:"Write not confirmed. "+UserErrors.message(error)+" Local history is unchanged.";show();
        }));show();
    }
    private void report(Exception error,int ticket){runOnUiThread(()->{if(!closed()&&ticket==generation){message=UserErrors.message(error);show();}});}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==WRITE&&!pendingId.isEmpty()){
            if(HealthExport.granted(this)){write();return;}
            pendingId="";pendingFingerprint="";message="Writing was not allowed. Nothing was sent.";
        }else if(request==BACKGROUND){boolean enabled=HealthSync.granted(this)&&HealthAccess.granted(this,HealthJob.BACKGROUND);HealthJob.schedule(this,enabled);message=enabled?"Background reads enabled; Android controls timing.":"Background access was not granted. Manual import remains available.";}
        else if(request==READ)message=HealthPreferences.anyReadGranted(this)?"Read access updated. Only selected, allowed data is read when requested.":"No selected read data was allowed. You can retry or manage access in Health Connect.";
        else if(request==WRITE)message=HealthExport.granted(this)?"Write access allowed. Choose an activity to review; nothing has been sent.":"Exercise and distance write permissions are both required.";
        show();
    }
}
