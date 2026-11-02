package app.rwun;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.*;

/** Deterministic retries with synthetic HTTP responses and a fake clock/sleeper. No API calls. */
public final class AiRetryHostTest {
  static int assertions;
  static void check(boolean value,String reason){assertions++;if(!value)throw new AssertionError(reason);}
  interface Action{void run()throws Exception;}
  static <T extends Exception>T failure(Class<T> type,Action action)throws Exception{
    try{action.run();throw new AssertionError("Expected "+type.getSimpleName());}
    catch(Exception e){if(!type.isInstance(e))throw e;assertions++;return type.cast(e);}
  }
  static JSONObject settings(String provider)throws Exception{return new JSONObject().put("provider",provider).put("model","synthetic-model").put("key","synthetic-secret");}
  static String envelope(String provider)throws Exception{
    String content="{\"connection\":\"ok\"}";
    if(provider.equals("OpenAI"))return new JSONObject().put("status","completed").put("output",new JSONArray().put(new JSONObject().put("type","message").put("content",new JSONArray().put(new JSONObject().put("type","output_text").put("text",content))))).toString();
    return new JSONObject().put("candidates",new JSONArray().put(new JSONObject().put("finishReason","STOP").put("content",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",content)))))).toString();
  }
  static final class Connection extends HttpURLConnection {
    final int code;final String body,after;boolean closed;
    final ByteArrayOutputStream sent=new ByteArrayOutputStream();
    Connection(URL url,int code,String body,String after){super(url);this.code=code;this.body=body;this.after=after;}
    public void connect(){}public void disconnect(){closed=true;}public boolean usingProxy(){return false;}
    public int getResponseCode(){return code;}
    public String getHeaderField(String name){return "Retry-After".equalsIgnoreCase(name)?after:null;}
    public OutputStream getOutputStream(){return sent;}
    public InputStream getInputStream(){return new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  }
  static final class Sequence implements AiCoachClient.ConnectionFactory {
    final int[] codes;final String provider,after;final List<Connection> connections=new ArrayList<>();
    Sequence(String provider,String after,int... codes){this.provider=provider;this.after=after;this.codes=codes;}
    public HttpURLConnection open(URL url)throws IOException{
      int code=codes[connections.size()];
      try{Connection c=new Connection(url,code,code==200?envelope(provider):"synthetic-secret must never appear in a displayed error",after);connections.add(c);return c;}catch(Exception e){throw new IOException(e);}
    }
  }
  static String request(Sequence s,List<Long> waits,AiCoachClient.RetryListener listener)throws Exception{
    return AiCoachClient.withRetry(()->AiCoachClient.generate(settings(s.provider),"Return connection JSON",s),listener,waits::add,()->0);
  }
  public static void main(String[] args)throws Exception{
    for(String provider:List.of("Gemini","OpenAI")){
      Sequence s=new Sequence(provider,null,503,503,200);List<Long> waits=new ArrayList<>();List<String> progress=new ArrayList<>();int[] guards={0};
      String result=request(s,waits,new AiCoachClient.RetryListener(){public void beforeAttempt(){guards[0]++;}public void retrying(int next,int total,long delay,int code){progress.add(AiCoachClient.retryMessage(next,total,delay,code));}});
      check(new JSONObject(result).getString("connection").equals("ok"),"Both providers recover from transient 503 responses");
      check(waits.equals(List.of(1000L,2000L)),"Exponential waits are 1 and 2 seconds before jitter");
      check(s.connections.size()==3&&guards[0]==3,"Every attempt is guarded, with at most three transmissions");
      check(s.connections.stream().map(c->c.getURL().toString()).distinct().count()==1,"Retries never silently switch provider or model");
      check(s.connections.stream().allMatch(c->c.closed),"Every failed and successful connection is disconnected");
      check(progress.size()==2&&progress.get(0).contains("HTTP 503")&&progress.get(1).contains("attempt 3 of 3"),"Progress includes HTTP status, delay and bounded attempt count");
      check(progress.stream().noneMatch(x->x.contains("synthetic-secret")),"Retry progress cannot disclose credentials");
    }
    Sequence exhausted=new Sequence("Gemini",null,503,503,503);List<Long> waits=new ArrayList<>();
    RunStore store=new RunStore(new Context(Files.createTempDirectory("retry-storage").toFile()));store.save(new PlanEngine.Config(8,3,12,10,LocalDate.now()));String before=store.backupPreferences().toString();
    var outage=failure(AiCoachClient.ProviderFailure.class,()->request(exhausted,waits,null));
    check(exhausted.connections.size()==3&&waits.size()==2,"Persistent outage stops after three attempts");
    check(outage.status==503&&outage.getMessage().contains("Tried 3 times")&&outage.getMessage().contains("temporarily unavailable")&&!outage.getMessage().contains("synthetic-secret"),"Final outage is actionable and never displays provider error bodies");
    check(before.equals(store.backupPreferences().toString()),"Retry exhaustion leaves saved plans intact");
    for(int code:new int[]{400,401,403,404,501}){
      Sequence s=new Sequence("Gemini",null,code);List<Long> none=new ArrayList<>();failure(AiCoachClient.ProviderFailure.class,()->request(s,none,null));
      check(s.connections.size()==1&&none.isEmpty(),"Permanent HTTP "+code+" fails immediately");
    }
    for(int code:new int[]{408,429,500,502,504}){
      Sequence s=new Sequence("Gemini",null,code,200);List<Long> delay=new ArrayList<>();request(s,delay,null);check(s.connections.size()==2&&delay.equals(List.of(1000L)),"Transient HTTP "+code+" retries");
    }
    Sequence throttled=new Sequence("Gemini","5",429,200);List<Long> throttleWait=new ArrayList<>();request(throttled,throttleWait,null);check(throttleWait.equals(List.of(5000L)),"Retry-After seconds are honoured");
    Sequence longWait=new Sequence("Gemini","120",503);List<Long> noWait=new ArrayList<>();failure(AiCoachClient.ProviderFailure.class,()->request(longWait,noWait,null));check(longWait.connections.size()==1&&noWait.isEmpty(),"Long server waits defer rather than retry prematurely or block the worker for minutes");
    long now=Instant.parse("2026-11-02T12:00:00Z").toEpochMilli();String future=DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(now+7000).atZone(ZoneOffset.UTC));
    check(AiCoachClient.retryAfterMillis(future,now)==7000,"HTTP date form of Retry-After is supported");
    check(AiCoachClient.retryAfterMillis(future,now+8000)==0,"Past Retry-After dates do not delay");
    check(AiCoachClient.retryAfterMillis("999999999999999999999999",now)==-1,"Overflowing numeric server waits are handled safely");
    check(AiCoachClient.retryAfterMillis("invalid",now)==0&&AiCoachClient.retryAfterMillis(null,now)==0,"Missing or malformed headers use normal backoff");
    Sequence jittered=new Sequence("Gemini",null,503,503,200);List<Long> jitterWait=new ArrayList<>();AiCoachClient.withRetry(()->AiCoachClient.generate(settings("Gemini"),"JSON",jittered),null,jitterWait::add,()->500);
    check(jitterWait.equals(List.of(1500L,2500L)),"Jitter stays within the bounded delay window");
    int[] calls={0};failure(SocketTimeoutException.class,()->AiCoachClient.withRetry(()->{calls[0]++;throw new SocketTimeoutException();},null,m->{throw new AssertionError("No wait expected");},()->0));
    check(calls[0]==1,"Ambiguous network timeouts are not automatically resent");
    failure(JSONException.class,()->AiCoachClient.withRetry(()->{throw new JSONException("malformed");},null,m->{throw new AssertionError("No retry expected");},()->0));
    failure(AiCoachClient.InvalidResponse.class,()->AiCoachClient.withRetry(()->{throw new AiCoachClient.InvalidResponse("incomplete");},null,m->{throw new AssertionError("No retry expected");},()->0));
    Sequence cancelled=new Sequence("Gemini",null,503,200);
    try{failure(InterruptedIOException.class,()->AiCoachClient.withRetry(()->AiCoachClient.generate(settings("Gemini"),"JSON",cancelled),null,m->{throw new InterruptedException();},()->0));check(Thread.currentThread().isInterrupted(),"Interrupted backoff preserves the cancellation signal");check(cancelled.connections.size()==1,"Cancellation prevents another HTTP transmission");}finally{Thread.interrupted();}
    int[] checks={0};Sequence revoked=new Sequence("Gemini",null,503,200);
    failure(IllegalStateException.class,()->request(revoked,new ArrayList<>(),new AiCoachClient.RetryListener(){public void beforeAttempt(){if(++checks[0]>1)throw new IllegalStateException("Settings revoked");}public void retrying(int n,int t,long d,int c){}}));
    check(revoked.connections.size()==1,"Revoked settings are checked again after waiting, before sending data");
    Thread.currentThread().interrupt();try{failure(InterruptedIOException.class,()->AiCoachClient.withRetry(()->{throw new AssertionError("Cancelled request must not start");},null,m->{},()->0));}finally{Thread.interrupted();}
    System.out.println("AI retry/backoff: "+assertions+" assertions passed");
  }
}
