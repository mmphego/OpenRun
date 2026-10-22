package app.rwun;
import java.io.*;
public final class GemmaModelTest {
 static int n;static void ok(boolean value,String s){n++;if(!value)throw new AssertionError(s);}interface Work{void run()throws Exception;}static void bad(Work task,String s){boolean rejected=false;try{task.run();}catch(Exception e){rejected=true;}ok(rejected,s);}
 public static void main(String[] args)throws Exception{
  ok(GemmaModel.SHA256.matches("[a-f0-9]{64}"),"fixed model checksum");ok(GemmaModel.DOWNLOAD.contains(GemmaModel.REVISION)&&!GemmaModel.DOWNLOAD.contains("/main/"),"immutable model revision");
  bad(()->GemmaModel.copyVerified(new ByteArrayInputStream(new byte[0]),OutputStream.nullOutputStream(),b->{}),"empty model refused");
  bad(()->GemmaModel.copyVerified(new ByteArrayInputStream("not a model".getBytes()),OutputStream.nullOutputStream(),b->{}),"wrong format and truncated model refused");
  bad(()->GemmaModel.prompt(null),"null summary refused");bad(()->GemmaModel.prompt("x".repeat(5001)),"oversize summary refused");
  String prompt=GemmaModel.prompt("No runs reported. Hold active.");ok(prompt.contains("Do not invent")&&prompt.contains("clear a training hold"),"rules kept in local prompt");ok(prompt.contains("at most 90 words"),"short response request");
  Thread.currentThread().interrupt();bad(()->GemmaModel.copyVerified(new ByteArrayInputStream(new byte[20]),OutputStream.nullOutputStream(),b->{}),"cancelled import refused");Thread.interrupted();
  System.out.println(n+" offline-model integrity/prompt assertions passed");
 }
}
