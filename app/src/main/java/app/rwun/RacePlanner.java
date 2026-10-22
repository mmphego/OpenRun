package app.rwun;
import java.time.*;
/** Date anchoring of the original programme, not a race-readiness assessment. */
public final class RacePlanner {
 public static PlanEngine.Config config(LocalDate today,LocalDate race,int weeks,int days,double volume,double goal){if(weeks<4||weeks>24||race==null||today==null)throw new IllegalArgumentException("Choose a race and 4–24 weeks");LocalDate start=race.minusDays(weeks*7L);if(start.isBefore(today))throw new IllegalArgumentException("This race is too soon for the selected plan length. Choose fewer weeks or a later race; no compressed catch-up plan is created.");return new PlanEngine.Config(weeks,days,volume,goal,start);}
}
