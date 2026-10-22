package app.rwun;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/**
 * Bounded portable ZIP backup. Restore validates first, stages new routes, then commits
 * preferences.
 */
final class BackupManager {
  private static final int LIMIT = 50 * 1024 * 1024;

  static void exportTo(Context context, RunStore store, OutputStream output) throws Exception { synchronized(RunStore.LOCK) {
    if(!store.checkpoint().isEmpty()||store.indoorPending())throw new IllegalStateException("Finish or recover the pending run before backup/restore");
    JSONObject data = store.backupPreferences();
    RunStore.validatePreferences(data);
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      zip.putNextEntry(new ZipEntry("manifest.json"));
      zip.write(
          new JSONObject()
              .put("app", "Rwun")
              .put("schema", 1)
              .put("preferences", data)
              .toString()
              .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      Set<String> files = new HashSet<>();
      JSONArray runs = allRows(data);
      for (int i = 0; i < runs.length(); i++) {
        String route = runs.getJSONObject(i).optString("route", "");
        if (!route.isEmpty() && files.add(route)) {
          zip.putNextEntry(new ZipEntry(route));
          try (InputStream in = context.openFileInput(route)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) zip.write(buffer, 0, n);
          }
          zip.closeEntry();
        }
      }
    }
   }}

  private static JSONArray allRows(JSONObject data)throws Exception{JSONArray out=new JSONArray(data.optString("runs","[]"));if(data.has("cycling")){JSONObject cycle=new JSONObject(data.getString("cycling"));for(String key:List.of("rides","archivedRecordings")){JSONArray rows=CyclingData.rows(cycle,key);for(int i=0;i<rows.length();i++)out.put(rows.getJSONObject(i));}}if(data.has("v103")){JSONArray walks=WalkData.outdoor(new JSONObject(data.getString("v103")));for(int i=0;i<walks.length();i++)out.put(walks.getJSONObject(i));}return out;}
  private static JSONArray slice(JSONArray rows,int from,int to)throws Exception{JSONArray out=new JSONArray();for(int i=from;i<to;i++)out.put(rows.getJSONObject(i));return out;}
  private static void validateRoute(byte[] bytes) throws Exception {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
      String line;
      int points = 0;
      while ((line = reader.readLine()) != null) {
        if (++points > 150000) throw new IllegalArgumentException("Too many route points");
        JSONObject p = new JSONObject(line);
        double lat = p.getDouble("lat"), lon = p.getDouble("lon");
        if (!Double.isFinite(lat)
            || !Double.isFinite(lon)
            || Math.abs(lat) > 90
            || Math.abs(lon) > 180
            || p.getLong("time") < 0
            || p.optInt("hr", 0) < 0
            || p.optInt("hr", 0) > 250
            || p.optInt("segment", 0) < 0)
          throw new IllegalArgumentException("Invalid saved route point");
      }
    }
  }

  static void restoreFrom(Context context, RunStore store, InputStream input) throws Exception { synchronized(RunStore.LOCK) {
    if(!store.checkpoint().isEmpty()||store.indoorPending())throw new IllegalStateException("Finish or recover the pending run before backup/restore");
    Map<String, byte[]> entries = new HashMap<>();
    int total = 0;
    try (ZipInputStream zip = new ZipInputStream(input)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        String name = entry.getName();
        if (entry.isDirectory()
            || !name.equals("manifest.json") && !name.matches("route-[a-zA-Z0-9-]+\\.jsonl")
            || entries.containsKey(name)
            || entries.size() > 10000)
          throw new IllegalArgumentException("Unsupported backup entry");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = zip.read(buffer)) != -1) {
          total += n;
          if (total > LIMIT) throw new IllegalArgumentException("Expanded backup exceeds 50 MB");
          out.write(buffer, 0, n);
        }
        entries.put(name, out.toByteArray());
      }
    }
    for (var item : entries.entrySet())
      if (!item.getKey().equals("manifest.json")) validateRoute(item.getValue());
    if (!entries.containsKey("manifest.json"))
      throw new IllegalArgumentException("Backup manifest is missing");
    JSONObject manifest =
        new JSONObject(new String(entries.get("manifest.json"), StandardCharsets.UTF_8));
    if (!manifest.getString("app").equals("Rwun") || manifest.getInt("schema") != 1)
      throw new IllegalArgumentException("Unsupported backup format");
    JSONObject data = manifest.getJSONObject("preferences");
    RunStore.validatePreferences(data);
    JSONArray rows = allRows(data);
    Map<String, String> replacements = new HashMap<>();
    List<String> staged = new ArrayList<>();
    try {
      for (int i = 0; i < rows.length(); i++) {
        JSONObject run = rows.getJSONObject(i);
        String old = run.optString("route", "");
        if (old.isEmpty()) continue;
        if (!entries.containsKey(old))
          throw new IllegalArgumentException("A referenced route is missing");
        String replacement = replacements.get(old);
        if (replacement == null) {
          replacement = "route-" + UUID.randomUUID() + ".jsonl";
          try (FileOutputStream file = context.openFileOutput(replacement, Context.MODE_PRIVATE)) {
            file.write(entries.get(old));
            file.getFD().sync();
          }
          staged.add(replacement);
          replacements.put(old, replacement);
        }
        run.put("route", replacement);
      }
      int position=new JSONArray(data.optString("runs","[]")).length();data.put("runs",slice(rows,0,position).toString());
      if(data.has("cycling")){JSONObject cycle=new JSONObject(data.getString("cycling"));int count=CyclingData.rows(cycle,"rides").length();cycle.put("rides",slice(rows,position,position+count));position+=count;int archived=CyclingData.rows(cycle,"archivedRecordings").length();if(archived>0)cycle.put("archivedRecordings",slice(rows,position,position+archived));position+=archived;data.put("cycling",cycle.toString());}
      if(data.has("v103")){JSONObject local=new JSONObject(data.getString("v103"));int count=WalkData.outdoor(local).length();if(count>0)local.put("walks",slice(rows,position,position+count));data.put("v103",local.toString());}
      store.restorePreferences(data);
    } catch (Exception error) {
      for (String file : staged) context.deleteFile(file);
      throw error;
    }
   }}
}
