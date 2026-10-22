package app.rwun;

import java.util.List;

/** Original review rule; never changes a benchmark automatically. */
public final class PaceInsights {
  public record Observation(
      double pace, double target, int effort, int fatigue, boolean pain, long benchmark) {}

  public record Suggestion(long benchmarkSeconds, String reason) {}

  public Suggestion suggest(long benchmark, List<Observation> all) {
    if (benchmark == 0) return null;
    var recent =
        all.stream()
            .filter(o -> o.benchmark() == benchmark && o.pace() > 0 && o.target() > 0)
            .limit(3)
            .toArray(Observation[]::new);
    if (recent.length < 3) return null;
    boolean comfortable = true, struggling = true;
    for (var r : recent) {
      if (r.pain()) return null;
      comfortable &= r.effort() <= 6 && r.fatigue() <= 2 && r.pace() < r.target() * .98;
      struggling &= r.effort() >= 8 && r.pace() > r.target() * 1.08;
    }
    if (comfortable)
      return new Suggestion(
          Math.round(benchmark * .99),
          "Three completed quality sessions were quicker than their targets at reported effort ≤6"
              + " and fatigue ≤2. Review a 1% quicker benchmark; this is a heuristic, not a race"
              + " prediction.");
    if (struggling)
      return new Suggestion(
          Math.round(benchmark * 1.02),
          "Three completed quality sessions were slower than their targets at reported effort ≥8."
              + " Review a 2% slower benchmark and your recovery.");
    return null;
  }
}
