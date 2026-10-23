package app.rwun;
import java.io.*;import java.net.*;import java.util.*;import java.util.concurrent.*;
/** HTTPS page transport: bounded redirects/retries/bytes/time; credentials are never embedded in feed requests. */
public class RaceHttp {
 public record Response(int status,String body,String etag,String modified){}
 private final Set<HttpURLConnection> active=ConcurrentHashMap.newKeySet();private volatile boolean cancelled;
 public void cancel(){cancelled=true;for(var c:active)c.disconnect();}
 public Response get(String endpoint,RaceDatabase.HttpPage cached)throws Exception {
  for(int attempt=0;attempt<2;attempt++){
   if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException("Race sync cancelled");
   HttpURLConnection c=null;
   try {
    c=open(endpoint,cached);int status=c.getResponseCode();
    if(status==304)return new Response(status,"",header(c,"ETag"),header(c,"Last-Modified"));
    if(status==429||status>=500){if(attempt==0){long delay=500;try{delay=Math.max(delay,Long.parseLong(c.getHeaderField("Retry-After"))*1000);}catch(Exception ignored){}if(delay>30000)throw new IOException("Provider asked to retry later (HTTP "+status+")");c.disconnect();Thread.sleep(delay);continue;}throw new IOException("Provider temporarily unavailable (HTTP "+status+")");}
    if(status!=200)throw new IOException("Provider returned HTTP "+status);
    try(InputStream raw=c.getInputStream();InputStream in="gzip".equalsIgnoreCase(c.getContentEncoding())?new java.util.zip.GZIPInputStream(raw):raw){return new Response(status,read(in,3*1024*1024),header(c,"ETag"),header(c,"Last-Modified"));}
   }catch(SocketTimeoutException|ConnectException e){if(attempt==1)throw e;Thread.sleep(500);}finally{if(c!=null){active.remove(c);c.disconnect();}}
  }throw new IOException("Race request failed");
 }
 private HttpURLConnection open(String endpoint,RaceDatabase.HttpPage cached)throws Exception{
  URI uri=URI.create(endpoint);validate(uri);
  for(int redirects=0;redirects<=2;redirects++){
   if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedException("Race sync cancelled");
   HttpURLConnection c=(HttpURLConnection)uri.toURL().openConnection();active.add(c);boolean returned=false;
   try {c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","OpenRun/1.5.0 race-discovery");
    if(cached!=null){if(!cached.etag.isEmpty())c.setRequestProperty("If-None-Match",cached.etag);if(!cached.modified.isEmpty())c.setRequestProperty("If-Modified-Since",cached.modified);}
    int status=c.getResponseCode();if(Set.of(301,302,303,307,308).contains(status)){
     String location=c.getHeaderField("Location");if(location==null||redirects==2)throw new IOException("Provider redirect limit reached");
     uri=redirect(uri,location);continue;
    }
    returned=true;return c;
   }finally{if(!returned){active.remove(c);c.disconnect();}}
  }throw new IOException("Provider redirect limit reached");
 }
 static URI redirect(URI from,String location)throws IOException{URI to;try{to=from.resolve(location);validate(to);}catch(Exception e){throw new IOException("Invalid provider redirect");}
  boolean same=Objects.equals(from.getHost(),to.getHost())&&from.getPort()==to.getPort();
  // Observed on 22 Oct 2026: Bield's documented public feed redirects to this public function.
  boolean bield=from.getHost().equals("app.bield.run")&&to.getHost().equals("zlphtleymloulgqkvkev.supabase.co")&&to.getPath().equals("/functions/v1/races-public-feed");
  if(!same&&!bield)throw new IOException("Provider redirected to an unverified host; cached races retained");return to;
 }
 private static void validate(URI uri)throws IOException{if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IOException("Race feed requires HTTPS");}
 private static String header(HttpURLConnection c,String key){String s=c.getHeaderField(key);return s==null||s.length()>1000?"":s;}
 static String read(InputStream in,int limit)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();if(out.size()+n>limit)throw new IOException("Race feed exceeded its size limit");out.write(b,0,n);}return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}
}
