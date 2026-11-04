package app.rwun;
import org.junit.Test;
import static org.junit.Assert.*;
public class AiFailureMessageTest {
 @Test public void outageKeepsTheProviderStatus(){
  String text=AiCoachClient.failureMessage(new AiCoachClient.ProviderFailure(503,"Gemini is unavailable (HTTP 503). Your plan is unchanged."));
  assertTrue(text.contains("HTTP 503"));assertFalse(text.contains("storage"));
 }
 @Test public void networkFailuresDoNotBecomeFileErrorsOrExposeDetails(){
  String text=AiCoachClient.failureMessage(new java.io.IOException("private transport details"));
  assertTrue(text.contains("internet"));assertFalse(text.contains("private"));assertFalse(text.contains("file"));
  assertTrue(AiCoachClient.failureMessage(new java.net.SocketTimeoutException()).contains("too long"));
 }
}
