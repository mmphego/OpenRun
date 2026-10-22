package app.rwun;
import android.content.*;import android.net.Uri;import android.os.*;import java.io.*;import java.net.*;
/** Private model installation, verified before atomic replacement; never included in backups. */
final class OfflineModels {
 static File model(Context c){return new File(c.getFilesDir(),"offline-coach/gemma3-1b.litertlm");}
 static boolean ready(Context c){return model(c).isFile()&&model(c).length()==GemmaModel.BYTES;}
 static void install(Context c,InputStream stream,GemmaModel.Progress progress)throws Exception{
  File dest=model(c),folder=dest.getParentFile();if(!folder.exists()&&!folder.mkdirs())throw new IOException("Could not create private model folder");
  if(new StatFs(folder.getPath()).getAvailableBytes()<GemmaModel.BYTES+64*1024*1024L)throw new IOException("Free at least 700 MB before importing the model.");
  File part=new File(folder,"model-"+java.util.UUID.randomUUID()+".part");try(InputStream input=stream;FileOutputStream output=new FileOutputStream(part)){GemmaModel.copyVerified(input,output,progress);output.getFD().sync();}catch(Exception e){part.delete();throw e;}
  try{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Import cancelled");java.nio.file.Files.move(part.toPath(),dest.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){part.delete();throw e;}
 }
 static void download(Context c,GemmaModel.Progress progress)throws Exception{
  HttpURLConnection connection=(HttpURLConnection)new URL(GemmaModel.DOWNLOAD).openConnection();connection.setConnectTimeout(20000);connection.setReadTimeout(30000);
  try{int code=connection.getResponseCode();if(code==401||code==403)throw new IOException("The publisher requires Gemma licence acceptance and a Hugging Face download. Open the model page, download the listed file, then choose Import model. No account token is stored by OpenRun.");if(code!=200)throw new IOException("Model download unavailable ("+code+"). Import the publisher's file instead.");install(c,connection.getInputStream(),progress);}finally{connection.disconnect();}
 }
 static void remove(Context c)throws IOException{File model=model(c);if(model.exists()&&!model.delete())throw new IOException("Could not remove the local model");File cache=new File(c.getCacheDir(),"gemma");if(cache.isDirectory()){File[] rows=cache.listFiles();if(rows!=null)for(File f:rows)if(f.isFile())f.delete();}}
}
