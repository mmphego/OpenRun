package app.rwun;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Open real schema-1 layouts through the production Room builder; never clear user data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class RaceMigrationTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private RaceDatabase upgraded;

    @After public void cleanup() {
        if (upgraded != null) upgraded.close();
        context.deleteDatabase("race-catalogue.db");
    }

    @Test public void publishedBaselineMigratesWithoutLosingSelections() throws Exception {
        upgrade("app/schemas/app.rwun.RaceDatabase/1.json", false);
    }

    @Test public void rwun150MigratesAndRetainsMultiDayRange() throws Exception {
        upgrade("tests/fixtures/races/rwun-1.5.0-schema-1.json", true);
    }

    private void upgrade(String fixture, boolean hasRange) throws Exception {
        context.deleteDatabase("race-catalogue.db");
        JSONObject schema = new JSONObject(Files.readString(
                Path.of(System.getProperty("rwun.root"), fixture))).getJSONObject("database");
        JSONArray entities = schema.getJSONArray("entities");
        try (SQLiteDatabase old = context.openOrCreateDatabase("race-catalogue.db", 0, null)) {
            for (int i = 0; i < entities.length(); i++) {
                JSONObject entity = entities.getJSONObject(i);
                old.execSQL(entity.getString("createSql").replace("${TABLE_NAME}", entity.getString("tableName")));
                JSONArray indexes = entity.getJSONArray("indices");
                for (int j = 0; j < indexes.length(); j++)
                    old.execSQL(indexes.getJSONObject(j).getString("createSql")
                            .replace("${TABLE_NAME}", entity.getString("tableName")));
            }
            JSONArray setup = schema.getJSONArray("setupQueries");
            for (int i = 0; i < setup.length(); i++) old.execSQL(setup.getString(i));
            insert(old, entities, "race_editions", Map.of("id", "existing-race", "name", "Existing race",
                    "date", "2027-04-03", "endDate", "2027-04-05", "contentHash", "existing-hash"));
            insert(old, entities, "race_distances", Map.of("id", "existing-distance", "editionId", "existing-race",
                    "optionKey", "10000:Road", "metres", 10000, "available", 1));
            insert(old, entities, "saved_races", Map.of("editionId", "existing-race", "distanceId", "existing-distance",
                    "goalPlanId", "existing-plan", "reminderMinutes", 60));
            insert(old, entities, "race_refs", Map.of("provider", "top-events", "externalId", "provider-original-id",
                    "editionYear", "2027", "editionId", "existing-race", "payload", "{\"original\":true}"));
            old.setVersion(1);
        }
        upgraded = RaceDatabase.open(context);
        var sql = upgraded.getOpenHelper().getWritableDatabase(); // Runs migration and Room schema validation.
        assertEquals(2, sql.getVersion());
        try (var row = sql.query("SELECT e.date,e.endDate,e.contentHash,s.distanceId,s.goalPlanId,s.reminderMinutes,r.externalId,r.payload "
                + "FROM race_editions e JOIN saved_races s ON s.editionId=e.id JOIN race_refs r ON r.editionId=e.id")) {
            assertTrue(row.moveToFirst());
            assertEquals("2027-04-03", row.getString(0));
            assertEquals(hasRange ? "2027-04-05" : "2027-04-03", row.getString(1));
            assertEquals("existing-hash", row.getString(2));
            assertEquals("existing-distance", row.getString(3));
            assertEquals("existing-plan", row.getString(4));
            assertEquals(60, row.getInt(5));
            assertEquals("provider-original-id", row.getString(6));
            assertEquals("{\"original\":true}", row.getString(7));
            assertFalse(row.moveToNext());
        }
    }

    private static void insert(SQLiteDatabase database, JSONArray entities, String table, Map<String, Object> values)
            throws Exception {
        for (int i = 0; i < entities.length(); i++) {
            JSONObject entity = entities.getJSONObject(i);
            if (!table.equals(entity.getString("tableName"))) continue;
            ContentValues row = new ContentValues();
            JSONArray fields = entity.getJSONArray("fields");
            for (int j = 0; j < fields.length(); j++) {
                JSONObject field = fields.getJSONObject(j);
                String column = field.getString("columnName");
                Object value = values.get(column);
                if (value instanceof Integer) row.put(column, (Integer) value);
                else if (value != null) row.put(column, value.toString());
                else if (field.getBoolean("notNull")) {
                    if ("INTEGER".equals(field.getString("affinity"))) row.put(column, 0);
                    else row.put(column, "");
                }
            }
            database.insertOrThrow(table, null, row);
            return;
        }
        throw new IllegalArgumentException("Unknown schema table " + table);
    }
}
