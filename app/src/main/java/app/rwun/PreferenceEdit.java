package app.rwun;

import android.content.SharedPreferences;
import java.util.*;

/** Stages a preference transaction so the existing planner can evaluate it before any write. */
final class PreferenceEdit {
  final Map<String,Object> before;
  final Map<String,Object> after;
  private final SharedPreferences target;
  PreferenceEdit(SharedPreferences target) {
    this.target=target;
    before=new HashMap<>(target.getAll());
    after=new HashMap<>(before);
  }
  PreferenceEdit putString(String k,String v){if(v==null)return remove(k);after.put(k,v);return this;}
  PreferenceEdit putInt(String k,int v){after.put(k,v);return this;}
  PreferenceEdit putLong(String k,long v){after.put(k,v);return this;}
  PreferenceEdit putFloat(String k,float v){after.put(k,v);return this;}
  PreferenceEdit putBoolean(String k,boolean v){after.put(k,v);return this;}
  PreferenceEdit remove(String k){after.remove(k);return this;}
  PreferenceEdit clear(){after.clear();return this;}
  boolean commit(){
    SharedPreferences.Editor edit=target.edit();
    for(String key:before.keySet())if(!after.containsKey(key))edit.remove(key);
    for(var entry:after.entrySet()) {
      String k=entry.getKey();Object v=entry.getValue();
      if(Objects.equals(before.get(k),v))continue;
      if(v instanceof String)edit.putString(k,(String)v);
      else if(v instanceof Integer)edit.putInt(k,(Integer)v);
      else if(v instanceof Long)edit.putLong(k,(Long)v);
      else if(v instanceof Float)edit.putFloat(k,(Float)v);
      else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
      else throw new IllegalArgumentException("Unsupported stored preference: "+k);
    }
    return edit.commit();
  }
  /** Read-only candidate; planning cannot accidentally persist a preview. */
  static SharedPreferences view(Map<String,Object> values){
    Map<String,Object> state=Map.copyOf(values);
    return new SharedPreferences(){
      public Map<String,?> getAll(){return state;}
      public String getString(String k,String d){return (String)state.getOrDefault(k,d);}
      public int getInt(String k,int d){return (Integer)state.getOrDefault(k,d);}
      public long getLong(String k,long d){return (Long)state.getOrDefault(k,d);}
      public float getFloat(String k,float d){return (Float)state.getOrDefault(k,d);}
      public boolean getBoolean(String k,boolean d){return (Boolean)state.getOrDefault(k,d);}
      public boolean contains(String k){return state.containsKey(k);}
      public Set<String> getStringSet(String k,Set<String> d){throw new UnsupportedOperationException();}
      public Editor edit(){throw new UnsupportedOperationException("Plan previews are read-only");}
      public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){throw new UnsupportedOperationException();}
      public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){throw new UnsupportedOperationException();}
    };
  }
}
