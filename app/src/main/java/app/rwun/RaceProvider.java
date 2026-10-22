package app.rwun;
import java.time.LocalDate;
import java.util.List;
/** Fetching and parsing are independent of persistence and UI. */
public interface RaceProvider {
 String id();
 String endpoint(int page,String incrementalSince);
 Page parse(String json,LocalDate today) throws Exception;
 record Page(List<RaceData.Race> races,boolean more,int pageCount,int skipped) {public Page {races=List.copyOf(races);}}
 default boolean incremental(){return false;}
 default String blockedReason(){return "";}
}
