package app.rwun;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToLongFunction;

/** Pure history filtering and grouping; Android presentation remains in MainActivity. */
final class RunHistory {
  static final String ALL_WORKOUTS = "All workouts";
  static final String ALL_YEARS = "All years";
  static final String ALL_MONTHS = "All months";

  private RunHistory() {}

  static List<String> workoutTypes(List<RunStore.Run> runs) {
    Set<String> values = new TreeSet<>();
    for (RunStore.Run run : runs) values.add(run.workoutType());
    List<String> result = new ArrayList<>();
    result.add(ALL_WORKOUTS);
    result.addAll(values);
    return result;
  }

  static List<String> years(List<RunStore.Run> runs) {
    Set<String> values = new TreeSet<>(Comparator.reverseOrder());
    for (RunStore.Run run : runs) values.add(run.date().substring(0, 4));
    List<String> result = new ArrayList<>();
    result.add(ALL_YEARS);
    result.addAll(values);
    return result;
  }

  static List<String> months() {
    List<String> result = new ArrayList<>();
    result.add(ALL_MONTHS);
    for (int month = 1; month <= 12; month++) result.add(String.format("%02d", month));
    return result;
  }

  static List<RunStore.Run> filter(
      List<RunStore.Run> runs,
      String workoutType,
      String year,
      String month,
      ToLongFunction<RunStore.Run> startedAt) {
    List<RunStore.Run> result = new ArrayList<>();
    for (RunStore.Run run : runs) {
      if (!ALL_WORKOUTS.equals(workoutType) && !run.workoutType().equals(workoutType)) continue;
      if (!ALL_YEARS.equals(year) && !run.date().startsWith(year)) continue;
      if (!ALL_MONTHS.equals(month) && !run.date().substring(5, 7).equals(month)) continue;
      result.add(run);
    }
    result.sort(
        Comparator.comparing(RunStore.Run::date)
            .thenComparingLong(startedAt)
            .reversed());
    return result;
  }

  static Map<YearMonth, List<RunStore.Run>> groupByMonth(List<RunStore.Run> runs) {
    Map<YearMonth, List<RunStore.Run>> result = new LinkedHashMap<>();
    for (RunStore.Run run : runs)
      result.computeIfAbsent(YearMonth.from(LocalDate.parse(run.date())), ignored -> new ArrayList<>())
          .add(run);
    return result;
  }
}
