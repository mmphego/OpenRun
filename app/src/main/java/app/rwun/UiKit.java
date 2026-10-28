package app.rwun;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.Gravity;
import android.widget.*;

/** Shared native Android presentation; no training or storage logic. */
final class UiKit {
  static final class Palette {
    final boolean dark;
    final int ink, muted, primary, background, surface, outline, tonal, onPrimary, danger, dangerSurface;
    Palette(boolean dark) {
      this.dark = dark;
      ink = Color.parseColor(dark ? "#E1E7EF" : "#19232F");
      muted = Color.parseColor(dark ? "#939FB0" : "#5B687A");
      primary = Color.parseColor(dark ? "#40C9B5" : "#00796B");
      background = Color.parseColor(dark ? "#171B22" : "#F4F6FA");
      surface = Color.parseColor(dark ? "#242B35" : "#FFFFFF");
      outline = Color.parseColor(dark ? "#394350" : "#DEE3EB");
      tonal = Color.parseColor(dark ? "#203F41" : "#E0F3F0");
      onPrimary = Color.parseColor(dark ? "#102A28" : "#FFFFFF");
      danger = Color.parseColor(dark ? "#FFB4AB" : "#A4332B");
      dangerSurface = Color.parseColor(dark ? "#4D2724" : "#FFF0ED");
    }
  }
  static void feedback(android.view.View v){try{if(new RunStore(v.getContext()).v1().optBoolean("haptics",true))v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);}catch(RuntimeException ignored){}}
  static int dp(Context c, int value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
  static GradientDrawable shape(Context c, int color, int radius) {
    GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
  }
  static Drawable ripple(Context c, int color, int radius) {
    return new RippleDrawable(ColorStateList.valueOf(0x22006B52), shape(c,color,radius),shape(c,Color.WHITE,radius));
  }
  static Drawable icon(Context c, int resource, int color, int size) {
    Drawable d=c.getDrawable(resource).mutate();d.setTint(color);d.setBounds(0,0,dp(c,size),dp(c,size));return d;
  }
  /** Compact app type scale in sp; Android accessibility font scaling still applies. */
  static int textSizeSp(int requested) {
    if (requested >= 28) return Math.round(requested * .8f);
    if (requested >= 24) return 20;
    if (requested >= 21) return 18;
    if (requested >= 17) return 16;
    if (requested >= 15) return 14;
    return requested;
  }
  static TextView label(Context c, String text, int size, int color, boolean medium) {
    TextView t=new TextView(c);t.setText(text);t.setTextSize(textSizeSp(size));t.setTextColor(color);
    t.setTypeface(Typeface.create(medium?"sans-serif-medium":"sans-serif",Typeface.NORMAL));t.setIncludeFontPadding(false);
    if(medium&&size>=20&&android.os.Build.VERSION.SDK_INT>=28)t.setAccessibilityHeading(true);
    t.setPadding(0,dp(c,5),0,dp(c,5));t.setLineSpacing(dp(c,2),1);return t;
  }
  static int actionIcon(String label) {
    String s=label.toLowerCase(java.util.Locale.ROOT);
    if(s.startsWith("back"))return R.drawable.ic_arrow_back;
    if(s.contains("previous"))return R.drawable.ic_chevron_left;
    if(s.contains("next week"))return R.drawable.ic_chevron_right;
    if(s.contains("discard")||s.contains("delete"))return R.drawable.ic_delete;
    if(s.contains("walk"))return R.drawable.ic_directions_walk;
    if(s.contains("voice")||s.contains("spoken"))return R.drawable.ic_volume_up;
    if(s.contains("export"))return R.drawable.ic_file_upload;
    if(s.contains("import")||s.contains("restore"))return R.drawable.ic_file_download;
    if(s.contains("pause"))return R.drawable.ic_pause;
    if(s.contains("save")||s.contains("complete"))return R.drawable.ic_check;
    if(s.contains("garmin")||s.contains("wearable"))return R.drawable.ic_watch;
    if(s.contains("start")||s.contains("resume"))return R.drawable.ic_play_arrow;
    if(s.contains("schedule")||s.contains("plan"))return R.drawable.ic_calendar_month;
    if(s.contains("pace"))return R.drawable.ic_monitoring;
    return R.drawable.ic_arrow_forward;
  }
  static Button action(Context c,Palette p,String text,Runnable action) {
    boolean destructive=text.toLowerCase(java.util.Locale.ROOT).matches(".*(discard|delete).*"),
      primary=text.equals("View workout")||text.startsWith("Start ")||text.equals("Finish and save")||text.equals("Finish and review")||text.equals("Build my plan")||text.equals("Save completed session")||text.equals("Continue")||text.startsWith("Create race")||text.startsWith("Create paused race")||text.equals("Preview race plan")||text.equals("Add race")||text.equals("Create my plan")||text.equals("Preview my plan")||text.equals("Create paused plan")||text.equals("Generate offline insight");
    int bg=destructive?p.dangerSurface:primary?p.primary:p.surface;
    int fg=destructive?p.danger:primary?p.onPrimary:p.ink;
    Button b=new Button(c);b.setText(text);b.setTextSize(textSizeSp(15));b.setAllCaps(false);b.setTextColor(fg);
    b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
    b.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));
    GradientDrawable border=shape(c,bg,16);if(!primary)border.setStroke(dp(c,1),p.outline);b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3348D8C1),border,shape(c,Color.WHITE,16)));b.setStateListAnimator(null);b.setElevation(0);b.setCompoundDrawablePadding(dp(c,12));
    b.setCompoundDrawablesRelative(icon(c,actionIcon(text),fg,22),null,null,null);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(c,5),0,dp(c,5));b.setLayoutParams(lp);
    b.setMinWidth(0);b.setMinimumWidth(0);b.setOnClickListener(v->{feedback(v);action.run();});return b;
  }
  static LinearLayout tabs(Context c,Palette p,String[] labels,int selected,java.util.function.IntConsumer onSelect){
    LinearLayout tabs=new LinearLayout(c);
    for(int i=0;i<labels.length;i++){
      final int index=i;boolean active=i==selected;LinearLayout tab=new LinearLayout(c);tab.setOrientation(LinearLayout.VERTICAL);tab.setGravity(Gravity.CENTER);tab.setMinimumWidth(0);tab.setMinimumHeight(dp(c,52));
      tab.setBackground(ripple(c,p.background,0));tab.setClickable(true);tab.setFocusable(true);tab.setSelected(active);tab.setContentDescription(labels[i]+(active?", selected":""));
      TextView title=label(c,labels[i],14,active?p.ink:p.muted,true);title.setSingleLine(true);title.setGravity(Gravity.CENTER);title.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);title.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
      tab.addView(title,new LinearLayout.LayoutParams(-1,dp(c,48)));android.view.View underline=new android.view.View(c);underline.setBackgroundColor(active?p.primary:p.outline);tab.addView(underline,new LinearLayout.LayoutParams(-1,dp(c,2)));tab.setOnClickListener(v->{feedback(v);onSelect.accept(index);});tabs.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
    }return tabs;
  }

  static LinearLayout choice(Context c,Palette p,String title,Runnable click){
    LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(c,18),dp(c,12),dp(c,14),dp(c,12));row.setMinimumHeight(dp(c,64));
    GradientDrawable border=shape(c,p.surface,14);border.setStroke(dp(c,1),p.outline);row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3348D8C1),border,shape(c,Color.WHITE,14)));
    TextView text=label(c,title,17,p.ink,false);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));ImageView arrow=new ImageView(c);arrow.setImageDrawable(icon(c,R.drawable.ic_chevron_right,p.muted,24));row.addView(arrow,new LinearLayout.LayoutParams(dp(c,24),dp(c,24)));
    row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->{feedback(v);click.run();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(c,8),0,dp(c,8));row.setLayoutParams(lp);return row;
  }

  static ImageButton iconButton(Context c,Palette p,int resource,String description,Runnable click) {
    ImageButton b=new ImageButton(c);b.setImageDrawable(icon(c,resource,p.primary,24));b.setContentDescription(description);
    b.setBackground(ripple(c,p.background,24));b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));
    b.setOnClickListener(v->{feedback(v);click.run();});return b;
  }
  static LinearLayout navigationItem(Context c,Palette p,String label,int icon,boolean active,Runnable click) {
    LinearLayout item=new LinearLayout(c);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(0,dp(c,6),0,dp(c,5));
    item.setMinimumHeight(dp(c,64));item.setClickable(true);item.setFocusable(true);item.setSelected(active);item.setContentDescription(label+(active?", selected":""));
    item.setBackground(ripple(c,p.surface,12));
    ImageView image=new ImageView(c);image.setImageDrawable(icon(c,icon,active?p.primary:p.muted,24));
    image.setPadding(dp(c,18),dp(c,4),dp(c,18),dp(c,4));image.setBackground(shape(c,active?p.tonal:p.surface,18));
    image.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(image,new LinearLayout.LayoutParams(dp(c,56),dp(c,32)));
    TextView title=label(c,label,11,active?p.primary:p.muted,active);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setGravity(Gravity.CENTER);title.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);item.addView(title);
    item.setOnClickListener(v->{feedback(v);click.run();});return item;
  }
}
