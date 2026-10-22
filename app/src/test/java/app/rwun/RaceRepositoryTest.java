package app.rwun;

import androidx.room.Room;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class RaceRepositoryTest {
    private RaceDatabase database;
    private RaceRepository repository;
    @Before public void open() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), RaceDatabase.class).allowMainThreadQueries().build();
        repository = new RaceRepository(database);
    }
    @After public void close() { database.close(); }
    private RaceData.Race race(String provider, String external, String fee) {
        return new RaceData.Race(provider, external, "2027-04-03", "Example 10 km", "Cape Town", "Western Cape", "ZA",
                "Example venue", "07:00", "Africa/Johannesburg", "Road", fee, "", "https://example.org/race", "", "",
                List.of(new RaceData.Distance(10000, "10 km", "Road")));
    }
    private List<RaceDatabase.Card> cards() {
        return repository.search(new RaceRepository.Filter("", "", "", "All", 0, "2027-01-01", "2027-12-31", false, 0));
    }
    @Test public void unchangedPayloadRetainsEditionDistanceAndSavedChoice() {
        assertEquals(1, repository.upsert(List.of(race("top-events", "1", "R100")), 1).inserted());
        String id = cards().get(0).id;
        String distance = repository.options(id).get(0).id;
        repository.save(id, distance);
        long version = repository.edition(id).contentVersion;
        assertEquals(1, repository.upsert(List.of(race("top-events", "1", "R100")), 2).unchanged());
        assertEquals(version, repository.edition(id).contentVersion);
        assertEquals(distance, repository.database().catalogue().saved(id).distanceId);
        assertEquals(2, database.catalogue().lastSeen(id));
    }
    @Test public void providerObservationsMergeAndKeepBothSources() {
        repository.upsert(List.of(race("top-events", "1", "R100")), 1);
        repository.upsert(List.of(race("bield", "2", "")), 2);
        assertEquals(1, cards().size());
        assertEquals(2, repository.references(cards().get(0).id).size());
        assertEquals("R100", repository.edition(cards().get(0).id).fee);
    }
    @Test public void changedContentUpdatesWithoutDeletingSavedRace() {
        repository.upsert(List.of(race("top-events", "1", "R100")), 1);
        String id = cards().get(0).id;
        repository.save(id, null);
        assertEquals(1, repository.upsert(List.of(race("top-events", "1", "R120")), 2).changed());
        assertEquals("R120", repository.edition(id).fee);
        assertTrue(repository.isSaved(id));
    }
    @Test public void databaseLeasePreventsOverlappingOwners() {
        assertTrue(repository.acquire("first", 1000));
        assertFalse(repository.acquire("second", 1001));
        repository.release("first");
        assertTrue(repository.acquire("second", 1002));
    }
}
