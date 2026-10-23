package app.rwun;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.*;
import androidx.sqlite.db.SupportSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.room.migration.Migration;
import java.util.List;

/** Race content, provider observations, HTTP validators and user decisions have independent write lifecycles. */
@Database(entities={RaceDatabase.Edition.class,RaceDatabase.Option.class,RaceDatabase.Reference.class,RaceDatabase.Seen.class,RaceDatabase.HttpPage.class,RaceDatabase.SyncState.class,RaceDatabase.Saved.class},version=2,exportSchema=true)
public abstract class RaceDatabase extends RoomDatabase {
 public abstract CatalogueDao catalogue();
 /** Both the public baseline and Rwun 1.5.0 used schema version 1. Preserve either layout. */
 public static final Migration MIGRATION_1_2=new Migration(1,2){
  @Override public void migrate(@NonNull SupportSQLiteDatabase database){
   boolean hasEndDate=false;
   try(android.database.Cursor columns=database.query("PRAGMA table_info(`race_editions`)")){
    int name=columns.getColumnIndexOrThrow("name");
    while(columns.moveToNext())if("endDate".equals(columns.getString(name)))hasEndDate=true;
   }
   if(!hasEndDate)database.execSQL("ALTER TABLE `race_editions` ADD COLUMN `endDate` TEXT NOT NULL DEFAULT ''");
   database.execSQL("UPDATE `race_editions` SET `endDate`=`date` WHERE `endDate`=''");
   database.execSQL("CREATE INDEX IF NOT EXISTS `index_race_seen_provider_requestUrl` ON `race_seen` (`provider`,`requestUrl`)");
  }
 };
 public static RaceDatabase open(Context context){return Room.databaseBuilder(context.getApplicationContext(),RaceDatabase.class,"race-catalogue.db").addMigrations(MIGRATION_1_2).setJournalMode(JournalMode.WRITE_AHEAD_LOGGING).build();}
 @Entity(tableName="race_editions",indices={@Index({"country","date"}),@Index({"nameKey","date","country"})})
 public static class Edition {
  @PrimaryKey @NonNull public String id="";
  @NonNull public String name="",nameKey="",date="",endDate="",city="",province="",country="",venue="",time="",timezone="",surface="",fee="",description="",contentHash="";
  public long contentVersion=1;
 }
 @Entity(tableName="race_distances",foreignKeys=@ForeignKey(entity=Edition.class,parentColumns="id",childColumns="editionId"),indices={@Index(value={"editionId","optionKey"},unique=true),@Index("metres")})
 public static class Option {
  @PrimaryKey @NonNull public String id="";
  @NonNull public String editionId="",optionKey="",label="",surface="";public int metres;public boolean available=true;
 }
 @Entity(tableName="race_refs",primaryKeys={"provider","externalId","editionYear"},foreignKeys=@ForeignKey(entity=Edition.class,parentColumns="id",childColumns="editionId"),indices=@Index("editionId"))
 public static class Reference {
  @NonNull public String provider="",externalId="",editionYear="",editionId="",sourceUrl="",entryUrl="",contentHash="",payload="";
 }
 @Entity(tableName="race_seen",primaryKeys={"provider","externalId","editionYear"},indices={@Index("editionId"),@Index({"provider","requestUrl"})})
 public static class Seen {
  @NonNull public String provider="",externalId="",editionYear="",editionId="",revision="",requestUrl="";public long lastSeen;
 }
 @Entity(tableName="race_http",primaryKeys={"provider","requestUrl"})
 public static class HttpPage {
  @NonNull public String provider="",requestUrl="",etag="",modified="";public int pageCount;public boolean more;public long checked;
 }
 @Entity(tableName="race_sync")
 public static class SyncState {
  @PrimaryKey @NonNull public String provider="";
  @NonNull public String error="",incrementalSince="",leaseOwner="";public long attempted,success,leaseUntil,lastFull;public int pages,skipped;public boolean truncated;
 }
 @Entity(tableName="saved_races",foreignKeys={@ForeignKey(entity=Edition.class,parentColumns="id",childColumns="editionId"),@ForeignKey(entity=Option.class,parentColumns="id",childColumns="distanceId")},indices=@Index("distanceId"))
 public static class Saved {
  @PrimaryKey @NonNull public String editionId="";
  public String distanceId;public long savedAt;public Integer reminderMinutes;@NonNull public String goalPlanId="";
 }
 public static class Card {
  @NonNull public String id="",name="",date="",endDate="",city="",province="",country="",venue="",time="",timezone="",surface="",fee="";
  @NonNull public String distances="";public long lastSeen;public boolean saved;
 }
 @Dao public interface CatalogueDao {
  @Query("SELECT * FROM race_editions WHERE id=:id LIMIT 1") Edition edition(String id);
  @Query("SELECT * FROM race_editions WHERE nameKey=:name AND date=:date AND country=:country LIMIT 8") List<Edition> candidates(String name,String date,String country);
  @Query("SELECT * FROM race_refs WHERE provider=:provider AND externalId=:external AND editionYear=:year LIMIT 1") Reference reference(String provider,String external,String year);
  @Query("SELECT * FROM race_refs WHERE editionId=:id ORDER BY CASE provider WHEN 'top-events' THEN 0 WHEN 'bield' THEN 1 WHEN 'runsignup' THEN 2 ELSE 3 END,provider,externalId") List<Reference> references(String id);
  @Query("SELECT * FROM race_distances WHERE editionId=:id ORDER BY metres,optionKey") List<Option> options(String id);
  @Query("SELECT * FROM race_distances WHERE id=:id LIMIT 1") Option option(String id);
  @Query("SELECT * FROM saved_races WHERE editionId=:id LIMIT 1") Saved saved(String id);
  @Query("SELECT * FROM race_sync WHERE provider=:id LIMIT 1") SyncState sync(String id);
  @Query("SELECT * FROM race_sync WHERE provider NOT IN ('_seed','_lease','_job') ORDER BY provider") List<SyncState> states();
  @Query("SELECT * FROM race_http WHERE provider=:provider AND requestUrl=:url LIMIT 1") HttpPage page(String provider,String url);
  @RawQuery List<Card> search(SupportSQLiteQuery query);
  @Query("SELECT DISTINCT country FROM race_editions WHERE country!='' ORDER BY country LIMIT 250") List<String> countries();
  @Query("UPDATE race_seen SET lastSeen=:now WHERE provider=:provider") void confirmProvider(String provider,long now);
  @Query("UPDATE race_seen SET lastSeen=:now WHERE provider=:provider AND requestUrl=:url") void confirmPage(String provider,String url,long now);
  @Query("SELECT MAX(lastSeen) FROM race_seen WHERE editionId=:id") long lastSeen(String id);
  @Query("UPDATE race_sync SET leaseOwner=:owner,leaseUntil=:until WHERE provider='_lease' AND (leaseUntil<:now OR leaseOwner=:owner)") int acquire(String owner,long now,long until);
  @Query("UPDATE race_sync SET leaseUntil=0,leaseOwner='' WHERE provider='_lease' AND leaseOwner=:owner") void release(String owner);
  @Insert(onConflict=OnConflictStrategy.IGNORE) long insertState(SyncState state);
  @Insert(onConflict=OnConflictStrategy.ABORT) void insertEdition(Edition row);
  @Update void updateEdition(Edition row);
  @Insert(onConflict=OnConflictStrategy.ABORT) void insertOption(Option row);
  @Update void updateOption(Option row);
  @Insert(onConflict=OnConflictStrategy.REPLACE) void reference(Reference row);
  @Insert(onConflict=OnConflictStrategy.REPLACE) void seen(Seen row);
  @Insert(onConflict=OnConflictStrategy.REPLACE) void page(HttpPage row);
  @Insert(onConflict=OnConflictStrategy.REPLACE) void state(SyncState row);
  @Insert(onConflict=OnConflictStrategy.REPLACE) void save(Saved row);
  @Query("DELETE FROM saved_races WHERE editionId=:id") void unsave(String id);
 }
}
