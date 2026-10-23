package app.rwun;
import android.app.Application;import androidx.room.Room;import java.nio.file.*;import java.time.*;import java.util.*;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
/** Explicit opt-in network validation; never runs in the normal offline regression suite. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,manifest=Config.NONE,application=Application.class)
public class RaceLiveValidationTest {
 @Test public void publicFeedsPopulateRoomAndSecondReadLeavesRaceContentUntouched()throws Exception{
  Assume.assumeTrue(Boolean.getBoolean("rwun.liveRaceFeeds"));RaceDatabase db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),RaceDatabase.class).allowMainThreadQueries().build();
  try {RaceRepository repo=new RaceRepository(db);var providers=List.<RaceProvider>of(new TopEventsRaceProvider(),new BieldRaceProvider());var first=new RaceSyncCoordinator(repo,new RaceHttp(),providers).sync();assertEquals("Provider errors: "+db.catalogue().states().stream().map(s->s.provider+": "+s.error).toList(),0,first.failed());assertEquals(2,first.succeeded());
   String from=LocalDate.now().toString(),to=LocalDate.now().plusYears(3).toString();var sa=repo.search(new RaceRepository.Filter("","","ZA","All",0,from,to,false,0));assertFalse(sa.isEmpty());
   Map<String,Long> versions=new HashMap<>();Map<String,String> hashes=new HashMap<>();try(var c=db.getOpenHelper().getReadableDatabase().query("SELECT id,contentVersion FROM race_editions")){while(c.moveToNext()){versions.put(c.getString(0),c.getLong(1));hashes.put(c.getString(0),repo.edition(c.getString(0)).contentHash);}}
   var second=new RaceSyncCoordinator(repo,new RaceHttp(),providers).sync();assertEquals("Provider errors: "+db.catalogue().states().stream().map(s->s.provider+": "+s.error).toList(),0,second.failed());for(var v:versions.entrySet())if(hashes.get(v.getKey()).equals(repo.edition(v.getKey()).contentHash))assertEquals("Unchanged content must retain its version",v.getValue().longValue(),repo.edition(v.getKey()).contentVersion);
   JSONObject out=new JSONObject().put("status","passed").put("scope","Live HTTPS adapters/coordinator + host Room SQLite, not emulator/S23").put("raceEditions",versions.size()).put("southAfricanFirstPage",sa.size());JSONArray states=new JSONArray();for(var s:db.catalogue().states())states.put(new JSONObject().put("provider",s.provider).put("pages",s.pages).put("skipped",s.skipped).put("error",s.error));out.put("providers",states);Path path=Path.of(System.getProperty("rwun.evidence"),"live-feeds.json");Files.createDirectories(path.getParent());Files.write(path,out.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }finally{db.close();}
 }
}
