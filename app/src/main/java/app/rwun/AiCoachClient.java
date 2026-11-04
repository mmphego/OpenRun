package app.rwun;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Direct provider REST calls. No intermediary server, redirects, body logging or URL credentials. */
final class AiCoachClient {
  static final class ProviderFailure extends IOException {
    final int status;
    final long retryAfterMillis;
    ProviderFailure(int status,String message){this(status,message,0);}
    ProviderFailure(int status,String message,long retryAfterMillis){super(message);this.status=status;this.retryAfterMillis=retryAfterMillis;}
  }
  static final class InvalidResponse extends IOException {InvalidResponse(String message){super(message);}}
  /** User-safe messages for network/provider failures; do not expose transport exception details. */
  static String failureMessage(Throwable failure){
    if(failure instanceof ProviderFailure)return failure.getMessage();
    if(failure instanceof InvalidResponse)return failure.getMessage();
    if(failure instanceof SocketTimeoutException)return "The AI provider took too long to respond. Try again later. Your saved plan is unchanged.";
    if(failure instanceof IOException)return "The AI provider could not be reached. Check your internet connection and try again. Your saved plan is unchanged.";
    return "The AI response could not be accepted. Review your settings and try again. Your saved plan is unchanged.";
  }
  static boolean retryable(Throwable failure){
    if(failure instanceof InvalidResponse)return false;
    return failure instanceof ProviderFailure ? ((ProviderFailure)failure).status==429||((ProviderFailure)failure).status>=500 : failure instanceof IOException;
  }
  interface ConnectionFactory {HttpURLConnection open(URL url)throws IOException;}
  static final int MAX_ATTEMPTS=3;
  interface RetryListener {
    default void beforeAttempt()throws Exception {}
    void retrying(int nextAttempt,int totalAttempts,long delayMillis,int status);
  }
  interface Request {String run()throws Exception;}
  interface Sleeper {void sleep(long millis)throws InterruptedException;}

  /** Foreground calls retry explicit transient HTTP responses, never ambiguous transport failures. */
  static String generate(JSONObject settings,String prompt)throws Exception {return generate(settings,prompt,(RetryListener)null);}
  static String generate(JSONObject settings,String prompt,RetryListener listener)throws Exception {
    return withRetry(()->generateOnce(settings,prompt),listener,Thread::sleep,
        ()->java.util.concurrent.ThreadLocalRandom.current().nextLong(501));
  }
  /** Background jobs already use the user's JobScheduler keep/retry/pause policy. */
  static String generateOnce(JSONObject settings,String prompt)throws Exception {
    return generate(settings,prompt,url->(HttpURLConnection)url.openConnection());
  }
  static String withRetry(Request request,RetryListener listener,Sleeper sleeper,java.util.function.LongSupplier jitter)throws Exception {
    for(int attempt=1;;attempt++){
      checkCancelled();
      if(listener!=null)listener.beforeAttempt();
      checkCancelled();
      try{return request.run();}
      catch(ProviderFailure failure){
        boolean transientStatus=java.util.Set.of(408,429,500,502,503,504).contains(failure.status);
        if(!transientStatus)throw failure;
        if(attempt>=MAX_ATTEMPTS)throw new ProviderFailure(failure.status,
            failure.getMessage()+" Tried "+MAX_ATTEMPTS+" times; please try again later.",failure.retryAfterMillis);
        // Never retry sooner than a provider's Retry-After or silently wait for minutes.
        if(failure.retryAfterMillis<0)throw new ProviderFailure(failure.status,
            failure.getMessage()+" The provider requested a longer wait; please try again later.",-1);
        long delay=Math.max(failure.retryAfterMillis,(1L<<(attempt-1))*1000+Math.max(0,Math.min(500,jitter.getAsLong())));
        if(listener!=null)listener.retrying(attempt+1,MAX_ATTEMPTS,delay,failure.status);
        checkCancelled();
        try{sleeper.sleep(delay);}catch(InterruptedException cancelled){Thread.currentThread().interrupt();throw new InterruptedIOException("AI request cancelled.");}
      }
    }
  }
  private static void checkCancelled()throws InterruptedIOException {
    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("AI request cancelled.");
  }
  /** -1 means defer to a later user/job attempt rather than ignore a long server wait. */
  static long retryAfterMillis(String header,long now){
    if(header==null||header.length()>128)return 0;
    String value=header.trim();
    if(value.matches("[0-9]+")){
      if(value.length()>2)return -1;
      long millis=Long.parseLong(value)*1000;
      return millis>30000?-1:millis;
    }
    try{
      long millis=java.time.ZonedDateTime.parse(value,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now;
      return millis>30000?-1:Math.max(0,millis);
    }catch(java.time.DateTimeException|ArithmeticException ignored){return 0;}
  }
  static String retryMessage(int nextAttempt,int totalAttempts,long delayMillis,int status){
    return "AI service returned HTTP "+status+". Retrying in "+((delayMillis+999)/1000)
        +" s (attempt "+nextAttempt+" of "+totalAttempts+")…";
  }
  static String generate(JSONObject settings,String prompt,ConnectionFactory factory)throws Exception {
    checkCancelled();
    String provider=settings.getString("provider"),model=settings.getString("model"),key=settings.getString("key");
    if(!model.matches("[A-Za-z0-9._-]{1,100}")||key.isBlank()||key.length()>512||key.contains("\n")||key.contains("\r"))throw new IllegalArgumentException("Check the model ID and API key in AI Coach settings.");
    boolean openai=provider.equals("OpenAI");if(!openai&&!provider.equals("Gemini"))throw new IllegalArgumentException("Choose OpenAI or Gemini.");
    URL url=new URL(openai?"https://api.openai.com/v1/responses":"https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent");
    JSONObject body=openai?new JSONObject().put("model",model).put("store",false).put("input",prompt).put("max_output_tokens",12000).put("text",new JSONObject().put("format",new JSONObject().put("type","json_object"))):new JSONObject().put("contents",new JSONArray().put(new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",prompt))))).put("generationConfig",new JSONObject().put("responseMimeType","application/json").put("maxOutputTokens",12000));
    checkCancelled();
    HttpURLConnection connection=factory.open(url);
    try{
      connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(90000);connection.setRequestMethod("POST");connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty(openai?"Authorization":"x-goog-api-key",openai?"Bearer "+key:key);
      byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);
      checkCancelled();
      try(OutputStream stream=connection.getOutputStream()){checkCancelled();stream.write(bytes);}
      int code=connection.getResponseCode();
      if(code==401||code==403)throw new ProviderFailure(code,"The provider rejected the key or model access. Check AI Coach settings.");
      long retryAfter=code==200?0:retryAfterMillis(connection.getHeaderField("Retry-After"),System.currentTimeMillis());
      if(code==429)throw new ProviderFailure(code,"The provider's usage or rate limit was reached. Check billing and try later.",retryAfter);
      if(code==503)throw new ProviderFailure(code,provider+" / "+model+" is temporarily unavailable (HTTP 503). Your plan is unchanged.",retryAfter);
      if(code!=200)throw new ProviderFailure(code,"The AI provider could not complete the request (HTTP "+code+"). Your plan is unchanged.",retryAfter);
      JSONObject response;try(InputStream stream=connection.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>262144)throw new InvalidResponse("The AI response was too large.");out.write(buffer,0,n);}response=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));}
      return extract(provider,response);
    }finally{connection.disconnect();}
  }
  static String extract(String provider,JSONObject response)throws JSONException,IOException {
    StringBuilder text=new StringBuilder();
    if(provider.equals("OpenAI")){
      if(!response.optString("status").equals("completed"))throw new InvalidResponse("The provider returned an incomplete answer. Try again.");
      JSONArray output=response.getJSONArray("output");for(int i=0;i<output.length();i++){JSONObject item=output.getJSONObject(i);if(!item.optString("type").equals("message"))continue;JSONArray parts=item.getJSONArray("content");for(int j=0;j<parts.length();j++){JSONObject part=parts.getJSONObject(j);if(part.optString("type").equals("output_text"))text.append(part.getString("text"));}}
    }else{
      JSONObject candidate=response.getJSONArray("candidates").getJSONObject(0);if(!candidate.optString("finishReason").equals("STOP"))throw new InvalidResponse("The provider stopped before a complete answer. Try again.");
      JSONArray parts=candidate.getJSONObject("content").getJSONArray("parts");for(int i=0;i<parts.length();i++)if(!parts.getJSONObject(i).optBoolean("thought"))text.append(parts.getJSONObject(i).optString("text"));
    }
    if(text.length()==0)throw new InvalidResponse("The provider returned no coaching answer.");return text.toString();
  }
}
