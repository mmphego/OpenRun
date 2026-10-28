package app.rwun;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Direct provider REST calls. No intermediary server, redirects, body logging or URL credentials. */
final class AiCoachClient {
  static String generate(JSONObject settings,String prompt)throws Exception {
    String provider=settings.getString("provider"),model=settings.getString("model"),key=settings.getString("key");
    if(!model.matches("[A-Za-z0-9._-]{1,100}")||key.isBlank()||key.length()>512||key.contains("\n")||key.contains("\r"))throw new IllegalArgumentException("Check the model ID and API key in AI Coach settings.");
    boolean openai=provider.equals("OpenAI");if(!openai&&!provider.equals("Gemini"))throw new IllegalArgumentException("Choose OpenAI or Gemini.");
    URL url=new URL(openai?"https://api.openai.com/v1/responses":"https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent");
    JSONObject body=openai?new JSONObject().put("model",model).put("store",false).put("input",prompt).put("max_output_tokens",12000).put("text",new JSONObject().put("format",new JSONObject().put("type","json_object"))):new JSONObject().put("contents",new JSONArray().put(new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",prompt))))).put("generationConfig",new JSONObject().put("responseMimeType","application/json").put("maxOutputTokens",12000));
    HttpURLConnection connection=(HttpURLConnection)url.openConnection();
    try{
      connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(90000);connection.setRequestMethod("POST");connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty(openai?"Authorization":"x-goog-api-key",openai?"Bearer "+key:key);
      byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);
      try(OutputStream stream=connection.getOutputStream()){stream.write(bytes);}
      int code=connection.getResponseCode();
      if(code==401||code==403)throw new IOException("The provider rejected the key or model access. Check AI Coach settings.");
      if(code==429)throw new IOException("The provider's usage or rate limit was reached. Check billing and try later.");
      if(code!=200)throw new IOException("The AI provider could not complete the request (HTTP "+code+"). Your plan is unchanged.");
      JSONObject response;try(InputStream stream=connection.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>262144)throw new IOException("The AI response was too large.");out.write(buffer,0,n);}response=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));}
      return extract(provider,response);
    }finally{connection.disconnect();}
  }
  static String extract(String provider,JSONObject response)throws JSONException,IOException {
    StringBuilder text=new StringBuilder();
    if(provider.equals("OpenAI")){
      if(!response.optString("status").equals("completed"))throw new IOException("The provider returned an incomplete answer. Try again.");
      JSONArray output=response.getJSONArray("output");for(int i=0;i<output.length();i++){JSONObject item=output.getJSONObject(i);if(!item.optString("type").equals("message"))continue;JSONArray parts=item.getJSONArray("content");for(int j=0;j<parts.length();j++){JSONObject part=parts.getJSONObject(j);if(part.optString("type").equals("output_text"))text.append(part.getString("text"));}}
    }else{
      JSONObject candidate=response.getJSONArray("candidates").getJSONObject(0);if(!candidate.optString("finishReason").equals("STOP"))throw new IOException("The provider stopped before a complete answer. Try again.");
      JSONArray parts=candidate.getJSONObject("content").getJSONArray("parts");for(int i=0;i<parts.length();i++)if(!parts.getJSONObject(i).optBoolean("thought"))text.append(parts.getJSONObject(i).optString("text"));
    }
    if(text.length()==0)throw new IOException("The provider returned no coaching answer.");return text.toString();
  }
}
