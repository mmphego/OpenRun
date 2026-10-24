package app.rwun;

import android.content.Context;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

/** Compact, accessible workout metrics bound to the real recorder state. */
final class RunDashboard extends LinearLayout {
  private boolean walking;
  void setWalking(boolean value){walking=value;}
  private final UiKit.Palette colors;
  private final TextView state,distance,time,pace,heart,phase,target,gps,voice,cue;
  RunDashboard(Context c,UiKit.Palette p) {
    super(c);colors=p;setOrientation(VERTICAL);setPadding(0,UiKit.dp(c,6),0,UiKit.dp(c,8));
    state=UiKit.label(c,"READY",12,p.primary,true);addView(state);
    LinearLayout hero=panel();hero.setBackground(UiKit.shape(c,p.tonal,24));
    hero.addView(UiKit.label(c,"DISTANCE  ·  GPS KM",11,p.muted,true));
    distance=UiKit.label(c,"0.00",48,p.ink,true);distance.setContentDescription("Distance, 0 kilometres");distance.setMaxLines(1);distance.setAutoSizeTextTypeUniformWithConfiguration(20,UiKit.textSizeSp(48),2,android.util.TypedValue.COMPLEX_UNIT_SP);hero.addView(distance);addView(hero);
    LinearLayout metrics=new LinearLayout(c);metrics.setGravity(Gravity.CENTER_VERTICAL);
    time=metric(metrics,"DURATION",R.drawable.ic_directions_run);pace=metric(metrics,"PACE /KM",R.drawable.ic_monitoring);addView(metrics);
    LinearLayout workout=panel();phase=UiKit.label(c,"Ready to run",19,p.ink,true);target=UiKit.label(c,"",14,p.muted,false);
    workout.addView(phase);workout.addView(target);heart=UiKit.label(c,"Heart rate not connected",13,p.muted,false);
    heart.setCompoundDrawablesRelative(UiKit.icon(c,R.drawable.ic_favorite,p.primary,18),null,null,null);heart.setCompoundDrawablePadding(UiKit.dp(c,8));workout.addView(heart);
    gps=UiKit.label(c,"Waiting for GPS",12,p.muted,false);workout.addView(gps);addView(workout);
    cue=UiKit.label(c,"",15,p.ink,false);cue.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);addView(cue);
    voice=UiKit.label(c,"",12,p.muted,false);addView(voice);
  }
  private LinearLayout panel(){LinearLayout c=new LinearLayout(getContext());c.setOrientation(VERTICAL);c.setPadding(UiKit.dp(getContext(),18),UiKit.dp(getContext(),12),UiKit.dp(getContext(),18),UiKit.dp(getContext(),12));c.setBackground(UiKit.shape(getContext(),colors.surface,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,UiKit.dp(getContext(),5),0,UiKit.dp(getContext(),5));c.setLayoutParams(lp);return c;}
  private TextView metric(LinearLayout row,String label,int icon){LinearLayout c=panel();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(0,UiKit.dp(getContext(),5),UiKit.dp(getContext(),6),UiKit.dp(getContext(),5));row.addView(c,lp);c.addView(UiKit.label(getContext(),label,10,colors.muted,true));TextView value=UiKit.label(getContext(),"—",23,colors.ink,true);value.setMaxLines(1);value.setAutoSizeTextTypeUniformWithConfiguration(12,UiKit.textSizeSp(23),1,android.util.TypedValue.COMPLEX_UNIT_SP);c.addView(value);return value;}
  void update(TrackingService tracker,boolean audio){
    boolean active=tracker!=null&&tracker.active();
    boolean failed=tracker!=null&&!tracker.startFailure().isEmpty()&&!active;
    boolean locationOn=false;try{locationOn=getContext().getSystemService(android.location.LocationManager.class).isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);}catch(RuntimeException ignored){}
    boolean precise=getContext().checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==android.content.pm.PackageManager.PERMISSION_GRANTED;
    state.setText(active?(tracker.paused()?"PAUSED":tracker.distanceNeedsAttention()?"CHECK DISTANCE":"RECORDING") : !locationOn?"LOCATION IS OFF":!precise?"PRECISE LOCATION NEEDED":failed?"RUN DID NOT START":(walking?"READY TO WALK":"READY TO RUN"));
    state.setTextColor(active&&tracker.distanceNeedsAttention()?colors.danger:colors.primary);
    distance.setText(String.format(Locale.US,"%.2f",tracker==null?0:tracker.km()));
    distance.setContentDescription("Distance, "+distance.getText()+" kilometres");
    long sec=tracker==null?0:tracker.seconds();time.setText(String.format(Locale.US,"%02d:%02d:%02d",sec/3600,sec/60%60,sec%60));
    pace.setText(tracker==null?"—":PaceProfile.format(tracker.currentPace()));
    phase.setText(active?tracker.phase():!locationOn?"Turn on location to run":!precise?"Location permission needed":failed?"Start blocked":walking?"Ready to walk":"Ready to run");
    String remaining=active?tracker.remaining():!locationOn?"Tap Start free run to open Android Location settings. No run is recording.":!precise?"Tap Start free run to grant precise location. No run is recording.":"Start outdoors with precise location enabled.";
    String range=active?tracker.paceTarget():"";target.setText(remaining+(range.isEmpty()?"":"\nTarget "+range));
    heart.setText(tracker!=null&&tracker.bpm()>0?tracker.bpm()+" bpm":"Heart rate not connected");
    gps.setText(tracker==null?"Outdoor GPS · walking pad sessions use phone steps or machine distance.":tracker.status());
    gps.setTextColor(active&&tracker.distanceNeedsAttention()?colors.danger:colors.muted);
    String nextCue=active?tracker.lastCue():"";if(!android.text.TextUtils.equals(cue.getText(),nextCue))cue.setText(nextCue);cue.setVisibility(active?VISIBLE:GONE);
    voice.setText(tracker==null?"Audio unavailable · check voice settings":tracker.voiceStatus());
  }
}
