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
public final class HealthConnectActivity extends Activity {
    private static final int READ=201, WRITE=202, HEART_RATE=203, BACKGROUND=204;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final UiKit.Palette palette=new UiKit.Palette(true);
    private LinearLayout body;
    private String message="", pendingId="", pendingFingerprint="";
    private int generation;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if(saved!=null){message=saved.getString("message","");pendingId=saved.getString("pendingId","");pendingFingerprint=saved.getString("pendingFingerprint","");}
        show();
        if(saved==null&&getIntent().hasExtra("exportId"))preview(getIntent().getStringExtra("exportId"));
    }
    @Override protected void onResume(){super.onResume();if(body!=null)show();}
    @Override protected void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putString("message",message);saved.putString("pendingId",pendingId);saved.putString("pendingFingerprint",pendingFingerprint);}
    @Override protected void onDestroy(){generation++;worker.shutdownNow();super.onDestroy();}
    private boolean closed(){return isDestroyed()||isFinishing();}
    private void text(String label,int size){body.addView(UiKit.label(this,label,size,palette.ink,size>=18));}
    private Button action(String label,Runnable click){Button b=UiKit.action(this,palette,label,click);body.addView(b);return b;}
    private void show(){
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=UiKit.dp(this,20);body.setPadding(pad,pad,pad,pad);body.setBackgroundColor(palette.background);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});setContentView(scroll);
        action("Back",this::finish);text("Health Connect",24);text(HealthAccess.status(this),14);
        text("Read activities",20);text("Import runs and walks from the last 30 days. Imports stay on this phone; possible duplicates require review.",14);
        if(HealthAccess.available(this)){
            if(!HealthSync.granted(this))action("Allow reading activities",()->new AlertDialog.Builder(this).setTitle("Read recent activities?")
                    .setMessage("Read exercise sessions and distance. Optional heart-rate access is separate. Importing does not write or delete Health Connect data.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->requestPermissions(HealthSync.PERMISSIONS,READ)).show());
            action(HealthSync.busy()?"Import in progress…":"Read recent activities",this::read).setEnabled(HealthSync.granted(this)&&!HealthSync.busy());
            action(HealthAccess.granted(this,HealthSync.HEART_RATE)?"Heart-rate import allowed":"Allow heart-rate import (optional)",()->requestPermissions(new String[]{HealthSync.HEART_RATE},HEART_RATE));
            if(HealthAccess.backgroundSupported(this)){
                text("Background reads: "+(getSystemService(android.app.job.JobScheduler.class).getPendingJob(81)!=null?"scheduled":"off"),13);
                action("Enable background reads",()->{
                    if(!HealthSync.granted(this)){message="Allow exercise and distance reads first.";show();return;}
                    new AlertDialog.Builder(this).setTitle("Allow background import?").setMessage("Android may import recent runs and walks about every six hours. Timing depends on the system. Writing stays manual.")
                            .setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->requestPermissions(new String[]{HealthJob.BACKGROUND},BACKGROUND)).show();
                });
            }else text("Background reads are unavailable on this Health Connect version. Foreground import remains available.",13);
            action("Disable background reads",()->{HealthJob.schedule(this,false);message="Background reads disabled.";show();});
        }
        text("Write activities",20);text("Choose an original outdoor or indoor run/walk, then review the session and distance before writing. Imported activities are excluded.",14);
        if(HealthAccess.available(this)){
            if(!HealthExport.granted(this))action("Allow writing activities",()->{
                pendingId="";pendingFingerprint="";
                new AlertDialog.Builder(this).setTitle("Allow activity exports?").setMessage("Exercise and distance write access does not enable reading. Granting permission sends nothing; you still choose and review each recording.")
                        .setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->requestPermissions(HealthExport.PERMISSIONS,WRITE)).show();
            });
            action(HealthExport.busy()?"Write in progress…":"Choose activity to write",this::choose).setEnabled(!HealthExport.busy());
        }
        text("Writing uses stable record IDs, including when a distance is corrected. No GPS route, steps, heart-rate samples or feedback are exported. Time-only recordings create no distance record.",13);
        if(!message.isEmpty())text(message,14);
        action("Manage Health Connect access",()->SettingsNavigator.openHealth(a->{try{startActivity(new Intent(a));return true;}catch(android.content.ActivityNotFoundException|SecurityException missing){return false;}}));
        action("Health data & privacy",()->startActivity(new Intent(this,HealthPrivacyActivity.class)));
        if(new RunStore(this).pendingImports().length()>0)action("Review possible duplicates",()->startActivity(new Intent(this,ImportReviewActivity.class)));
    }
    private void read(){
        message="Reading recent activities…";show();
        HealthSync.sync(getApplicationContext(),new RunStore(this)).whenComplete((value,error)->runOnUiThread(()->{
            if(closed())return;message=error==null?value:"Read stopped. "+UserErrors.message(error)+" Already saved activities are kept.";show();
        }));show();
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
                    if(!HealthExport.granted(this))requestPermissions(HealthExport.PERMISSIONS,WRITE);else write();
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
        else if(request==READ)message=HealthSync.granted(this)?"Read access allowed. Tap Read recent activities to import.":"Exercise and distance read permissions are both required.";
        else if(request==WRITE)message=HealthExport.granted(this)?"Write access allowed. Choose an activity to review; nothing has been sent.":"Exercise and distance write permissions are both required.";
        show();
    }
}
