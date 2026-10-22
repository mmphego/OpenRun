package app.rwun;
import android.app.*;import android.os.*;import android.view.*;import android.widget.*;import java.util.*;import org.json.*;
/** Incoming possible duplicates stay out of progress and training until explicitly reviewed. */
public final class ImportReviewActivity extends Activity {
 private final UiKit.Palette p=new UiKit.Palette(true);private RunStore store;private LinearLayout body;private TextView status;
 public void onCreate(Bundle state){super.onCreate(state);store=new RunStore(this);show();}
 private void show(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(p.background);root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});
  LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.addView(UiKit.iconButton(this,p,R.drawable.ic_arrow_back,"Back",this::finish));bar.addView(UiKit.label(this,"Review imports",19,p.ink,true));root.addView(bar);
  ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(UiKit.dp(this,20),UiKit.dp(this,12),UiKit.dp(this,20),UiKit.dp(this,24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);status=label("",14);
  label("Possible duplicate activities",24);label("These imports are held outside your history and training totals. Similar totals can also belong to separate sessions; choose what to keep.",15);
  JSONArray rows=store.pendingImports();if(rows.length()==0){label("No imports need review.",18);return;}
  for(int i=0;i<rows.length();i++)try{JSONObject row=rows.getJSONObject(i);String id=row.getString("id");
   LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(0,UiKit.dp(this,20),0,UiKit.dp(this,20));body.addView(card);
   card.addView(UiKit.label(this,row.getString("date")+" · "+(row.getBoolean("walk")?"Walk":"Run"),18,p.ink,true));
   card.addView(UiKit.label(this,"Incoming: "+row.getString("source")+"\n"+startLabel(row.getJSONObject("origin").optLong("started"),row.getString("date"))+"\n"+String.format(Locale.ROOT,"%.2f km · %dm %ds",row.getDouble("km"),row.getLong("seconds")/60,row.getLong("seconds")%60),15,p.ink,false));
   card.addView(UiKit.label(this,"Saved match: "+row.optString("matchSource")+"\n"+startLabel(row.optLong("matchStarted"),row.optString("matchDate",row.optString("date")))+"\n"+String.format(Locale.ROOT,"%.2f km · %dm %ds",row.optDouble("matchKm"),row.optLong("matchSeconds")/60,row.optLong("matchSeconds")%60),15,p.muted,false));
   boolean exists=store.runs().stream().anyMatch(r->r.sessionId().equals(row.optString("matchId")))||WalkData.all(store.v103()).stream().anyMatch(r->r.optString("id").equals(row.optString("matchId")));
   if(!exists)card.addView(UiKit.label(this,"The previously matching activity is no longer saved.",14,p.danger,false));
   card.addView(UiKit.action(this,p,exists?"Keep existing activity":"Dismiss this import",()->resolve(id,false)));
   card.addView(UiKit.action(this,p,"Import as a separate activity",()->new AlertDialog.Builder(this).setTitle("Count this as another activity?").setMessage("Only choose this if it is a separate session. It will be included in your activity totals.").setNegativeButton("Cancel",null).setPositiveButton("Import separately",(d,w)->resolve(id,true)).show()));
  }catch(Exception e){status.setText(UserErrors.message(e));}
 }
 private String startLabel(long timestamp,String date){return timestamp>0?"Start: "+java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm")):date+" · start time unavailable";}
 private TextView label(String s,int size){TextView v=UiKit.label(this,s,size,p.ink,size>=20);body.addView(v);return v;}
 private void resolve(String id,boolean separately){try{store.resolveImport(id,separately);show();}catch(Exception e){status.setText(UserErrors.message(e));}}
}
