package app.rwun;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;

public class RunHistoryTest {
  private static RunStore.Run run(String id, String date, String workout) {
    return new RunStore.Run(id, date, 5, 30, 5, 2, false, "GPS", "", 0, 1800, workout, 0, 0, 0, 0);
  }

  @Test
  public void filtersSortsAndGroupsWithoutDependingOnAndroidUi() {
    RunStore.Run early = run("early", "2026-01-03", "Easy");
    RunStore.Run later = run("later", "2026-01-03", "Intervals");
    RunStore.Run older = run("older", "2025-12-10", "Easy");
    List<RunStore.Run> all = List.of(early, later, older);

    assertEquals(List.of(RunHistory.ALL_WORKOUTS, "Easy", "Intervals"), RunHistory.workoutTypes(all));
    assertEquals(List.of(RunHistory.ALL_YEARS, "2026", "2025"), RunHistory.years(all));
    assertEquals(RunHistory.ALL_MONTHS, RunHistory.months().get(0));

    List<RunStore.Run> filtered = RunHistory.filter(all, RunHistory.ALL_WORKOUTS, "2026", "01", run -> run == later ? 2 : 1);
    assertEquals(List.of(later, early), filtered);
    Map<YearMonth, List<RunStore.Run>> grouped = RunHistory.groupByMonth(filtered);
    assertEquals(List.of(later, early), grouped.get(YearMonth.of(2026, 1)));
    assertTrue(RunHistory.filter(all, "Intervals", RunHistory.ALL_YEARS, RunHistory.ALL_MONTHS, ignored -> 0).contains(later));
  }

  @Test
  public void monthFilterValuesRemainIsoKeysInEveryLocale() {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("ar"));
      assertEquals("01", RunHistory.months().get(1));
    } finally {
      Locale.setDefault(previous);
    }
  }
}
