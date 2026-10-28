package app.rwun;

import android.content.Context;
import org.json.*;

/** Device-local, encrypted settings; keys/consent never enter portable training backups. */
final class AiCoachSettings {
  private final SyncVault vault;
  AiCoachSettings(Context c){vault=new SyncVault(c,"coach_vault","rwun.coach");}
  JSONObject read(){return vault.read();}
  boolean enabled(){JSONObject s=read();return s.optBoolean("consent")&&!s.optString("key").isBlank();}
  void save(String provider,String model,String key,int goalPace,boolean automatic,boolean notifications)throws Exception {
    if(!provider.equals("OpenAI")&&!provider.equals("Gemini")||!model.matches("[A-Za-z0-9._-]{1,100}")||key.isBlank()||key.length()>512||key.contains("\n")||key.contains("\r")||goalPace!=0&&(goalPace<180||goalPace>1800))throw new IllegalArgumentException("Enter a provider, model ID, API key and a goal pace between 3:00 and 30:00 min/km (or leave pace blank).");
    JSONObject old=read();vault.write(new JSONObject().put("provider",provider).put("model",model).put("key",key).put("goalPace",goalPace).put("automatic",automatic).put("notifications",notifications).put("consent",true).put("revision",java.util.UUID.randomUUID().toString()).put("lastResult",old.optString("lastResult")));
  }
  void result(String text)throws Exception{vault.update(s->s.put("lastResult",text));}
  void clear(){vault.clear();}
}
