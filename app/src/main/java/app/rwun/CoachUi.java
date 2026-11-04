package app.rwun;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

/** Native presentation for the goal/coach journey. No credentials, network or training decisions. */
final class CoachUi {
  static final UiKit.Palette P=new UiKit.Palette(true);
  static final int MUTED=Color.parseColor("#AFBAC9"),FIELD_OUTLINE=Color.parseColor("#65768B");
  private CoachUi() {}
  static int dp(Context c,int n){return UiKit.dp(c,n);}
  static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
  static TextView label(Context c,String text,int sp,int color,boolean strong){
    TextView v=new TextView(c);v.setText(text);v.setTextSize(sp);v.setTextColor(color);
    v.setTypeface(Typeface.create(strong?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
    v.setIncludeFontPadding(false);v.setLineSpacing(dp(c,3),1);
    if(strong&&sp>=20&&android.os.Build.VERSION.SDK_INT>=28)v.setAccessibilityHeading(true);
    return v;
  }
  static void gap(LinearLayout parent,int n){parent.addView(new View(parent.getContext()),new LinearLayout.LayoutParams(1,dp(parent.getContext(),n)));}
  static TextView copy(LinearLayout parent,String value,int size,boolean strong){
    TextView v=label(parent.getContext(),value,size,strong?P.ink:MUTED,strong);parent.addView(v);return v;
  }
  static LinearLayout card(LinearLayout parent,boolean accent){
    Context c=parent.getContext();LinearLayout v=column(c);int pad=dp(c,20);v.setPadding(pad,pad,pad,pad);
    v.setBackground(UiKit.shape(c,accent?P.tonal:P.surface,24));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(c,16));parent.addView(v,lp);return v;
  }
  static Button button(Context c,String caption,boolean primary,Runnable click){
    Button b=new Button(c);b.setText(caption);b.setAllCaps(false);b.setTextSize(16);
    b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(primary?P.onPrimary:P.ink);
    b.setGravity(Gravity.CENTER);b.setMinHeight(dp(c,52));b.setMinimumHeight(dp(c,52));b.setMinWidth(0);b.setMinimumWidth(0);
    b.setPadding(dp(c,16),dp(c,12),dp(c,16),dp(c,12));b.setStateListAnimator(null);b.setElevation(0);
    GradientDrawable shape=UiKit.shape(c,primary?P.primary:P.background,16);if(!primary)shape.setStroke(dp(c,1),FIELD_OUTLINE);
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3340C9B5),shape,UiKit.shape(c,Color.WHITE,16)));
    b.setOnClickListener(v->{UiKit.feedback(v);click.run();});return b;
  }
  static Button addButton(LinearLayout parent,String caption,boolean primary,Runnable click){
    Button b=button(parent.getContext(),caption,primary,click);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(parent.getContext(),8),0,0);parent.addView(b,lp);return b;
  }
  static View row(LinearLayout parent,int icon,String title,String detail,Runnable click){
    Context c=parent.getContext();LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(c,4),dp(c,12),dp(c,4),dp(c,12));row.setMinimumHeight(dp(c,56));
    ImageView image=new ImageView(c);image.setImageDrawable(UiKit.icon(c,icon,P.primary,22));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(image,new LinearLayout.LayoutParams(dp(c,28),dp(c,28)));
    LinearLayout words=column(c);LinearLayout.LayoutParams content=new LinearLayout.LayoutParams(0,-2,1);content.setMargins(dp(c,12),0,dp(c,8),0);row.addView(words,content);
    words.addView(label(c,title,15,P.ink,true));if(!detail.isEmpty()){gap(words,4);words.addView(label(c,detail,13,MUTED,false));}
    if(click!=null){
      ImageView arrow=new ImageView(c);arrow.setImageDrawable(UiKit.icon(c,R.drawable.ic_chevron_right,MUTED,20));arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow,new LinearLayout.LayoutParams(dp(c,24),dp(c,24)));
      row.setBackground(UiKit.ripple(c,Color.TRANSPARENT,12));row.setClickable(true);row.setFocusable(true);row.setContentDescription(title+(detail.isEmpty()?"":", "+detail));
      words.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);row.setOnClickListener(v->{UiKit.feedback(v);click.run();});
    }
    parent.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;
  }
  static LinearLayout metric(Context c,String value,String unit,String caption){
    LinearLayout v=column(c);v.addView(label(c,value,32,P.ink,true));gap(v,4);v.addView(label(c,unit,13,P.primary,true));gap(v,8);v.addView(label(c,caption,13,MUTED,false));return v;
  }
  static void metrics(LinearLayout parent,String[] values,String[] units,String[] captions){
    Context c=parent.getContext();boolean stack=c.getResources().getConfiguration().fontScale>1.3f;
    LinearLayout row=new LinearLayout(c);row.setOrientation(stack?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);parent.addView(row);
    for(int i=0;i<values.length;i++){
      LinearLayout metric=metric(c,values[i],units[i],captions[i]);LinearLayout.LayoutParams lp=stack?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1);
      if(i>0){if(stack)lp.topMargin=dp(c,20);else lp.leftMargin=dp(c,16);}row.addView(metric,lp);
    }
  }
  static EditText field(LinearLayout parent,String caption,String value,String hint,boolean number){
    Context c=parent.getContext();copy(parent,caption,14,true);gap(parent,8);
    EditText input=new EditText(c);input.setTextColor(P.ink);input.setHintTextColor(MUTED);input.setTextSize(17);input.setSingleLine(true);
    input.setText(value);input.setHint(hint);input.setContentDescription(caption);
    input.setInputType(number?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL:InputType.TYPE_CLASS_TEXT);
    input.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));input.setMinimumHeight(dp(c,56));
    GradientDrawable shape=UiKit.shape(c,P.background,14);shape.setStroke(dp(c,1),FIELD_OUTLINE);input.setBackground(shape);
    parent.addView(input,new LinearLayout.LayoutParams(-1,-2));gap(parent,16);return input;
  }
  static LinearLayout header(Activity a,String title,String subtitle,Runnable back,Runnable settings){
    LinearLayout outer=column(a);LinearLayout bar=new LinearLayout(a);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(a,8),dp(a,4),dp(a,8),dp(a,4));
    bar.addView(UiKit.iconButton(a,P,R.drawable.ic_arrow_back,"Back",back),new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));
    LinearLayout words=column(a);words.addView(label(a,title,19,P.ink,true));if(!subtitle.isEmpty()){gap(words,3);words.addView(label(a,subtitle,12,MUTED,false));}bar.addView(words,new LinearLayout.LayoutParams(0,-2,1));
    if(settings!=null)bar.addView(UiKit.iconButton(a,P,R.drawable.ic_settings,"AI settings",settings),new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));
    outer.addView(bar);return outer;
  }
  static View steps(Activity a,int selected,Runnable back,Runnable close){
    LinearLayout outer=column(a);LinearLayout bar=header(a,"Your AI plan","Step "+(selected+1)+" of 4",back,null);outer.addView(bar);
    LinearLayout toolbar=(LinearLayout)bar.getChildAt(0);
    toolbar.addView(UiKit.iconButton(a,P,android.R.drawable.ic_menu_close_clear_cancel,"Close plan setup",close),new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));
    LinearLayout track=new LinearLayout(a);track.setPadding(dp(a,20),dp(a,4),dp(a,20),dp(a,14));String[] names={"Goal","Baseline","Schedule","Review"};
    for(int i=0;i<4;i++){
      LinearLayout step=column(a);View line=new View(a);line.setBackground(UiKit.shape(a,i<=selected?P.primary:P.outline,3));step.addView(line,new LinearLayout.LayoutParams(-1,dp(a,3)));gap(step,7);
      TextView name=label(a,names[i],11,i==selected?P.primary:MUTED,i==selected);step.addView(name);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(i<3)lp.rightMargin=dp(a,8);track.addView(step,lp);
    }
    outer.addView(track);outer.setContentDescription("AI plan setup, step "+(selected+1)+" of 4, "+names[selected]);return outer;
  }
  static void styleDialog(AlertDialog dialog){
    if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(UiKit.shape(dialog.getContext(),P.surface,24));dialog.getWindow().setGravity(Gravity.BOTTOM);dialog.getWindow().setLayout(-1,-2);}
    for(int which:new int[]{-1,-2,-3}){Button b=dialog.getButton(which);if(b!=null){b.setAllCaps(false);b.setTextColor(which==-1?P.primary:MUTED);b.setMinHeight(dp(dialog.getContext(),48));}}
  }
}
