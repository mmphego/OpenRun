package app.rwun;

import android.app.*;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.json.*;

/** Applied events only. Undo is a separate reviewed, revalidated transaction. */
public final class PlanChangesActivity extends Activity {
  private final UiKit.Palette palette=new UiKit.Palette(true);
  private RunStore store;
  private LinearLayout body;
  public void onCreate(Bundle state){super.onCreate(state);store=new RunStore(this);}
  protected void onResume(){super.onResume();show();}
  private void show(){
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(palette.background);
    root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
    LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.addView(UiKit.iconButton(this,palette,R.drawable.ic_arrow_back,"Back",this::finish));bar.addView(UiKit.label(this,"Plan changes",21,palette.ink,true));root.addView(bar);
    ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);int pad=UiKit.dp(this,20);body.setPadding(pad,pad,pad,pad);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    label(body,"Understand every adjustment",26,true);
    label(body,"Applied changes appear here. Previews and rejected suggestions are never recorded. Running targets and walking activities stay separate.",15,false);
    try {
      JSONArray events=store.planChanges();
      if(events.length()==0){label(body,"No recorded plan changes yet.",21,true);label(body,"Earlier changes were not recorded. Your saved plan and completed activities are kept; history starts with your next applied change.",16,false);return;}
      try {store.previewUndo();body.addView(UiKit.action(this,palette,"Review undo of latest change",this::reviewUndo));}
      catch(IllegalStateException unavailable){label(body,unavailable.getMessage(),15,false);}
      for(int i=events.length()-1;i>=0;i--) {
        JSONObject event=events.getJSONObject(i);LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(pad,pad,pad,pad);card.setBackground(PlanningUi.shape(this,PlanningUi.CARD,18,true));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,UiKit.dp(this,16),0,0);body.addView(card,lp);
        label(card,event.getString("kind"),21,true);
        label(card,Instant.ofEpochMilli(event.getLong("timestamp")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")),14,false);
        label(card,event.getString("reason"),17,true);label(card,"Evidence: "+event.getString("evidence"),14,false);
        if(event.has("undoOf"))label(card,"Reverses event "+event.getString("undoOf"),13,false);
        String summary=PlanChanges.summary(event.getJSONArray("changes"),false);label(card,summary.isEmpty()?"No workout dates or distances changed.":summary,16,false);
        if(!event.getString("planId").equals(store.planId()))label(card,"From an earlier plan · kept for your records",13,false);
      }
    }catch(Exception e){error(e);}
  }
  private void reviewUndo(){
    try {
      PlanChanges.Undo preview=store.previewUndo();
      new AlertDialog.Builder(this).setTitle("Review undo consequences").setMessage(preview.summary()).setNegativeButton("Keep change",null).setPositiveButton("Apply undo",(d,w)->{
        try{store.undoPlanChange(preview);Reminders.schedule(this);WorkoutWidget.updateAll(this);show();Toast.makeText(this,"Undo saved as a new plan change",Toast.LENGTH_LONG).show();}catch(Exception e){error(e);}
      }).show();
    }catch(Exception e){error(e);}
  }
  private void label(LinearLayout parent,String value,int size,boolean bold){parent.addView(UiKit.label(this,value,size,palette.ink,bold));}
  private void error(Exception e){new AlertDialog.Builder(this).setTitle("Plan change unavailable").setMessage(UserErrors.message(e)).setPositiveButton("Done",(d,w)->show()).show();}
}
