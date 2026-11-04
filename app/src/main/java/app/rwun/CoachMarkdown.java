package app.rwun;

import android.graphics.Typeface;
import android.text.*;
import android.text.style.*;
import java.util.regex.*;

/** Offline provider-text formatting. HTML and remote resources are never interpreted. */
final class CoachMarkdown {
  private CoachMarkdown() {}
  static CharSequence render(String raw){
    SpannableStringBuilder out=new SpannableStringBuilder();
    for(String source:raw.split("\n",-1)){
      if(out.length()>0)out.append('\n');
      String line=source;int start=out.length();
      Matcher heading=Pattern.compile("^#{1,6}\\s+(.+)$").matcher(line);
      boolean title=heading.matches();if(title)line=heading.group(1);
      line=line.replaceFirst("^\\s*[-*+]\\s+","• ");
      Matcher bold=Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__").matcher(line);int previous=0;
      while(bold.find()){
        out.append(line,previous,bold.start());int from=out.length();out.append(bold.group(1)!=null?bold.group(1):bold.group(2));
        out.setSpan(new StyleSpan(Typeface.BOLD),from,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);previous=bold.end();
      }
      out.append(line,previous,line.length());
      if(title&&out.length()>start){out.setSpan(new StyleSpan(Typeface.BOLD),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);out.setSpan(new RelativeSizeSpan(1.2f),start,out.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
    }
    return out;
  }
}
