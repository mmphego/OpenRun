package android.content;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Test-only Android storage adapter. It does not claim to test Android's disk lifecycle. */
public class Context {
  public static final int MODE_PRIVATE = 0;
  private final File folder;
  private final Map<String, MemoryPreferences> preferences = new HashMap<>();

  public Context(File folder) {
    this.folder = folder;
    folder.mkdirs();
  }

  public SharedPreferences getSharedPreferences(String name, int mode) {
    return preferences.computeIfAbsent(name, n -> new MemoryPreferences());
  }

  public File getFileStreamPath(String name) {
    return new File(folder, name);
  }

  public FileInputStream openFileInput(String name) throws IOException {
    return new FileInputStream(getFileStreamPath(name));
  }

  public FileOutputStream openFileOutput(String name, int mode) throws IOException {
    return new FileOutputStream(getFileStreamPath(name));
  }

  public boolean deleteFile(String name) {
    return getFileStreamPath(name).delete();
  }

  public int preferenceCommits(){return preferences.get("rwun").commits;}

  private static final class MemoryPreferences implements SharedPreferences {
    private int commits;
    private final Map<String, Object> values = new HashMap<>();

    public Map<String, ?> getAll() {
      return new HashMap<>(values);
    }

    public boolean contains(String k) {
      return values.containsKey(k);
    }

    public String getString(String k, String v) {
      return (String) values.getOrDefault(k, v);
    }

    public int getInt(String k, int v) {
      return (Integer) values.getOrDefault(k, v);
    }

    public long getLong(String k, long v) {
      return (Long) values.getOrDefault(k, v);
    }

    public float getFloat(String k, float v) {
      return (Float) values.getOrDefault(k, v);
    }

    public boolean getBoolean(String k, boolean v) {
      return (Boolean) values.getOrDefault(k, v);
    }

    public Editor edit() {
      return new Editor() {
        private boolean clear;
        private final Map<String, Object> updates = new HashMap<>();

        public Editor clear() {
          clear = true;
          return this;
        }

        public Editor remove(String k) {
          updates.put(k, null);
          return this;
        }

        public Editor putInt(String k, int v) {
          updates.put(k, v);
          return this;
        }

        public Editor putLong(String k, long v) {
          updates.put(k, v);
          return this;
        }

        public Editor putFloat(String k, float v) {
          updates.put(k, v);
          return this;
        }

        public Editor putString(String k, String v) {
          updates.put(k, v);
          return this;
        }

        public Editor putBoolean(String k, boolean v) {
          updates.put(k, v);
          return this;
        }

        public boolean commit() {
          commits++;
          if (clear) values.clear();
          updates.forEach(
              (k, v) -> {
                if (v == null) values.remove(k);
                else values.put(k, v);
              });
          return true;
        }
      };
    }
  }
}
