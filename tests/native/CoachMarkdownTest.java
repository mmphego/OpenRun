package app.rwun;
import android.text.Spanned;
import android.text.style.StyleSpan;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,manifest=Config.NONE)
public class CoachMarkdownTest {
 @Test public void formatsTheReportedRunBreakdown(){
  Spanned value=(Spanned)CoachMarkdown.render("## Recent runs\n\n**Run 1**\n- **Distance:** 5 km");
  assertEquals("Recent runs\n\nRun 1\n• Distance: 5 km",value.toString());
  assertEquals(3,value.getSpans(0,value.length(),StyleSpan.class).length);
 }
 @Test public void doesNotInterpretHtmlOrCreateExecutableLinks(){
  String raw="<img src=\"https://example.com/tracker\">\n[link](javascript:alert(1))";
  Spanned value=(Spanned)CoachMarkdown.render(raw);
  assertEquals(raw,value.toString());
  assertEquals(0,value.getSpans(0,value.length(),android.text.style.URLSpan.class).length);
 }
}
