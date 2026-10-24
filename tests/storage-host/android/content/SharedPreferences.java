package android.content;

import java.util.Map;

public interface SharedPreferences {
  Map<String, ?> getAll();

  boolean contains(String key);

  String getString(String key, String value);

  int getInt(String key, int value);

  long getLong(String key, long value);

  float getFloat(String key, float value);

  boolean getBoolean(String key, boolean value);

  Editor edit();
  default java.util.Set<String> getStringSet(String key,java.util.Set<String> value){throw new UnsupportedOperationException();}
  interface OnSharedPreferenceChangeListener {void onSharedPreferenceChanged(SharedPreferences preferences,String key);}
  default void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){}
  default void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener){}


  interface Editor {
    Editor clear();

    Editor remove(String key);

    Editor putInt(String key, int value);

    Editor putLong(String key, long value);

    Editor putFloat(String key, float value);

    Editor putString(String key, String value);

    Editor putBoolean(String key, boolean value);

    boolean commit();
  }
}
