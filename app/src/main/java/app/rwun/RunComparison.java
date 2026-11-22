package app.rwun;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.function.ToLongFunction;

/** Pace comparisons use only known, chronologically unambiguous and similar activities. */
final class RunComparison {
  enum Band { FASTER, SAME, SLOWER, MUCH_SLOWER }
  record Result(Band band, double percent, String previousDate) {}
  private record TimedRun(RunStore.Run run, long started) {}
  private RunComparison() {}

  static Map<String, Result> compare(List<RunStore.Run> runs, ToLongFunction<RunStore.Run> started) {
    TreeMap<String, List<TimedRun>> days = new TreeMap<>();
    for (RunStore.Run run : runs) {
      if (usable(run)) days.computeIfAbsent(run.date(), ignored -> new ArrayList<>()).add(new TimedRun(run, started.applyAsLong(run)));
    }
    Map<String, Result> results = new HashMap<>();
    for (List<TimedRun> day : days.values()) for (TimedRun current : day) {
      TimedRun previous = null;
      boolean ambiguous = false;
      boolean unknownOrder = false;
      for (TimedRun candidate : day) {
        if (candidate == current || !similar(current.run(), candidate.run())) continue;
        if (candidate.started() <= 0 || current.started() <= 0 || candidate.started() == current.started()) {
          unknownOrder = true;
        } else if (candidate.started() < current.started()) {
          if (previous != null && candidate.started() == previous.started()) ambiguous = true;
          else if (previous == null || candidate.started() > previous.started()) { previous = candidate; ambiguous = false; }
        }
      }
      if (ambiguous || unknownOrder) continue;
      if (previous == null) {
        for (List<TimedRun> olderDay : days.headMap(current.run().date(), false).descendingMap().values()) {
          for (TimedRun candidate : olderDay) {
            if (!similar(current.run(), candidate.run())) continue;
            if (previous == null) previous = candidate;
            else if (candidate.started() <= 0 || previous.started() <= 0 || candidate.started() == previous.started()) ambiguous = true;
            else if (candidate.started() > previous.started()) previous = candidate;
          }
          if (previous != null) break;
        }
      }
      if (previous == null || ambiguous) continue;
      double percent = ((current.run().seconds() / current.run().km())
          / (previous.run().seconds() / previous.run().km()) - 1) * 100;
      Band band = Math.abs(percent) < 0.5 ? Band.SAME : percent < 0 ? Band.FASTER
          : percent + 1e-9 < 15 ? Band.SLOWER : Band.MUCH_SLOWER;
      results.put(current.run().sessionId(), new Result(band, percent, previous.run().date()));
    }
    return results;
  }

  private static boolean usable(RunStore.Run run) {
    return Double.isFinite(run.km()) && run.km() > 0 && run.seconds() > 0 && venue(run) != 0;
  }
  private static boolean similar(RunStore.Run a, RunStore.Run b) {
    return usable(b) && a.workoutType().equals(b.workoutType()) && venue(a) == venue(b)
        && Math.abs(a.km() / b.km() - 1) <= 0.2 + 1e-9;
  }
  // Imports with no saved route do not reliably identify indoor versus outdoor.
  private static int venue(RunStore.Run run) {
    if (run.source().startsWith("Indoor")) return 1;
    if (run.source().equals("GPS") || !run.routeFile().isEmpty()) return 2;
    return 0;
  }
}
