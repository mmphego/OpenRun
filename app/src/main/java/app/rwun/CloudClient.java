package app.rwun;
import android.content.Context;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import org.json.*;
final class CloudClient {
  record Reply(byte[] data,int revision) {JSONObject json()throws JSONException{return new JSONObject(new String(data,StandardCharsets.UTF_8));}}
  final SyncVault vault;
  CloudClient(Context context){vault=new SyncVault(context);}
  static String endpoint(String text){try{URL url=new URL(text);if(!url.getProtocol().equals("https")||url.getHost().isEmpty()||url.getUserInfo()!=null||url.getQuery()!=null||url.getRef()!=null)throw new Exception();return text.replaceAll("/+$","");}catch(Exception e){throw new IllegalArgumentException("Enter an HTTPS server URL without credentials, query or fragment");}}
  Reply request(String method,String path,byte[] body,String type,int revision)throws Exception{return request(vault.read(),method,path,body,type,revision);}
  static Reply request(JSONObject settings,String method,String path,byte[] body,String type,int revision)throws Exception{
    URL url=new URL(endpoint(settings.getString("url"))+path);HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(20000);connection.setReadTimeout(60000);connection.setRequestMethod(method);connection.setRequestProperty("Authorization","Bearer "+settings.optString("token"));connection.setRequestProperty("Accept","application/json");
    try{if(body!=null){connection.setDoOutput(true);connection.setFixedLengthStreamingMode(body.length);connection.setRequestProperty("Content-Type",type);if(revision>=0)connection.setRequestProperty("If-Match",""+revision);try(OutputStream out=connection.getOutputStream()){out.write(body);}}
      int status=connection.getResponseCode();InputStream stream=status<400?connection.getInputStream():connection.getErrorStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(stream!=null)try(stream){byte[] block=new byte[8192];int count;while((count=stream.read(block))!=-1){if(bytes.size()+count>50*1024*1024)throw new IOException("Response too large");bytes.write(block,0,count);}}
      if(status<200||status>=300){String message="Server request failed ("+status+").";try{message=new JSONObject(bytes.toString("UTF-8")).optString("error",message);}catch(JSONException ignored){}throw new IllegalStateException(message);}
      int tag=0;try{tag=Integer.parseInt(connection.getHeaderField("ETag"));}catch(Exception ignored){}return new Reply(bytes.toByteArray(),tag);
    }catch(IOException error){throw new IllegalStateException("Could not reach the sync service. Check the URL, HTTPS certificate and connection.",error);}finally{connection.disconnect();}
  }
  void upload(Context context)throws Exception{
    JSONObject settings=vault.read();if(new RunStore(context).checkpoint().length()>0)throw new IllegalStateException("Finish or recover your run before creating a backup");ByteArrayOutputStream zip=new ByteArrayOutputStream();BackupManager.exportTo(context,new RunStore(context),zip);byte[] encrypted=BackupCipher.encrypt(zip.toByteArray(),settings.getString("passphrase").toCharArray());JSONObject result=request("PUT","/v1/backup",encrypted,"application/octet-stream",settings.optInt("revision",0)).json();
    vault.update(latest->{if(latest.optString("token").equals(settings.optString("token"))&&latest.optString("url").equals(settings.optString("url"))){latest.put("revision",result.getInt("revision")).put("lastSync",java.time.Instant.now().toString()).remove("lastError");}});
  }
}
