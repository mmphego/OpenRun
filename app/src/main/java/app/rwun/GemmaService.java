package app.rwun;
import android.app.*;import android.content.*;import android.os.*;import java.util.*;import java.util.concurrent.*;
import com.google.ai.edge.litertlm.*;
/** JNI runs in a separate app process. Unbinding terminates it, releasing memory and cancellation. */
public final class GemmaService extends Service {
 static final int GENERATE=1,RUNTIME=2,RESULT=3,FAILURE=4;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();private volatile boolean busy;
 private final Messenger messenger=new Messenger(new Handler(Looper.getMainLooper(),message->{if(message.replyTo==null)return true;Messenger reply=message.replyTo;Bundle request=message.getData();int ticket=request.getInt("ticket");if(busy){send(reply,ticket,FAILURE,"The offline coach is already working.");return true;}busy=true;int what=message.what;worker.submit(()->{try{
  if(what==RUNTIME){System.loadLibrary("litertlm_jni");send(reply,ticket,RESULT,"LiteRT-LM native runtime loaded · "+Build.SUPPORTED_ABIS[0]+". A verified model is still required for generation.");return;}
  RunStore store=new RunStore(this);if(!store.checkpoint().isEmpty()||store.indoorPending())throw new IllegalStateException("Finish or recover your active session before using the offline coach.");
  if(!OfflineModels.ready(this))throw new IllegalStateException("Import the verified Gemma model first.");
  ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();getSystemService(ActivityManager.class).getMemoryInfo(info);if(info.lowMemory||info.availMem<1500L*1024*1024)throw new IllegalStateException("Not enough free memory. Close other apps and try again.");
  String prompt=GemmaModel.prompt(request.getString("summary",""));
  java.io.File cache=new java.io.File(getCacheDir(),"gemma");cache.mkdirs();
  EngineConfig config=new EngineConfig(OfflineModels.model(this).getPath(),new Backend.CPU(2,null),null,null,1024,null,cache.getPath());
  try(Engine engine=new Engine(config)){engine.initialize();ConversationConfig conversationConfig=new ConversationConfig(null,List.of(),List.of(),new SamplerConfig(10,.9,.2,0));try(Conversation conversation=engine.createConversation(conversationConfig)){
   com.google.ai.edge.litertlm.Message answer=conversation.sendMessage(prompt,null,null,null,null,192,null,null);String result=answer.getContents().toString().trim();if(result.isEmpty()||result.length()>4000)throw new IllegalStateException("The local model did not return a usable explanation.");send(reply,ticket,RESULT,result);
  }}
 }catch(Throwable error){send(reply,ticket,FAILURE,error instanceof UnsatisfiedLinkError?"The local runtime is unavailable for this device architecture.":error.getMessage()==null?"The offline coach stopped. Your plan is unchanged.":error.getMessage());}finally{busy=false;}});return true;}));
 private void send(Messenger to,int ticket,int what,String text){try{android.os.Message result=android.os.Message.obtain(null,what);Bundle b=new Bundle();b.putString("text",text);b.putInt("ticket",ticket);result.setData(b);to.send(result);}catch(RemoteException ignored){}}
 public IBinder onBind(Intent intent){return messenger.getBinder();}
 public boolean onUnbind(Intent intent){android.os.Process.killProcess(android.os.Process.myPid());return false;}
 public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
