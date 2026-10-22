import app.rwun.*;
import java.time.LocalDate;
import java.util.List;

public class CoachingV3Test {
  private static int checks;

  public static void main(String[] args) {
    PaceProfile p = new PaceProfile(5, 1800, true);
    check(
        p.range(PaceProfile.Zone.TEMPO).fastSeconds()
            < p.range(PaceProfile.Zone.EASY).fastSeconds(),
        "tempo faster than easy");
    check(
        new PaceProfile(5, 1500, true).range(PaceProfile.Zone.EASY).fastSeconds()
            < p.range(PaceProfile.Zone.EASY).fastSeconds(),
        "benchmark personalisation");
    check(PaceProfile.format(365).equals("6:05"), "pace formatting");
    check(
        !PaceProfile.unset().range(PaceProfile.Zone.EASY).defined(),
        "no invented pace without benchmark");
    var config = new PlanEngine.Config(12, 3, 24, 10, LocalDate.of(2026, 10, 19));
    var sessions = new PlanEngine().generate(config, p);
    check(
        sessions.stream().anyMatch(s -> s.title().equals("Intervals")),
        "intervals added for experienced runner");
    check(
        sessions.stream().anyMatch(s -> s.title().equals("Tempo run")), "tempo sessions alternate");
    check(
        new PlanEngine()
            .generate(config, PaceProfile.unset()).stream()
                .noneMatch(s -> s.title().equals("Intervals")),
        "no unsolicited speed work");
    for (var s : sessions) {
      var workout = WorkoutFactory.create(s, p);
      check(!workout.steps().isEmpty(), "workout steps");
      check(
          workout.steps().stream().allMatch(st -> st.metres() >= 0 && st.seconds() >= 0),
          "nonnegative steps");
    }
    var workout =
        new WorkoutFactory.Workout(
            List.of(
                new WorkoutFactory.Step(
                    "Warm-up", PaceProfile.Zone.RECOVERY, 0, 10, PaceProfile.Range.effort()),
                new WorkoutFactory.Step(
                    "Interval", PaceProfile.Zone.INTERVAL, 100, 0, new PaceProfile.Range(300, 330)),
                new WorkoutFactory.Step(
                    "Cool-down", PaceProfile.Zone.RECOVERY, 0, 10, PaceProfile.Range.effort())));
    WorkoutEngine e = new WorkoutEngine(workout);
    check(!e.update(0, 0, Double.NaN).isEmpty(), "start cue");
    e.update(10, 0, Double.NaN);
    check(e.stepIndex() == 1, "warm-up transition");
    e.update(31, 50, 400);
    check(
        e.update(47, 70, 400).stream().anyMatch(s -> s.contains("quicker")),
        "sustained slow pace alert");
    check(e.update(48, 75, 400).isEmpty(), "pace alert throttled");
    e.update(50, 100, 310);
    check(e.stepIndex() == 2, "distance stage transition");
    e.update(60, 105, Double.NaN);
    check(e.complete(), "cool-down completion");
    check(
        e.qualityMetres() == 100 && e.qualitySeconds() == 40,
        "quality metrics exclude warmup and cooldown");
    RollingPace rolling = new RollingPace();
    rolling.add(0, 0);
    rolling.add(10, 20);
    check(Double.isNaN(rolling.value(10)), "no premature pace estimate");
    rolling.add(20, 50);
    check(Math.abs(rolling.value(20) - 400) < .001, "rolling pace");
    check(Double.isNaN(rolling.value(40)), "stale GPS does not drive pace cues");
    rolling.reset();
    check(Double.isNaN(rolling.value(50)), "pause resets pace window");
    var observations =
        List.of(
            new PaceInsights.Observation(300, 320, 5, 2, false, 1800),
            new PaceInsights.Observation(300, 320, 5, 2, false, 1800),
            new PaceInsights.Observation(300, 320, 5, 2, false, 1800));
    check(
        new PaceInsights().suggest(1800, observations).benchmarkSeconds() < 1800,
        "consistent comfortable quality data prompts faster review");
    check(
        new PaceInsights().suggest(1800, observations.subList(0, 2)) == null,
        "insufficient evidence does not change pace");
    check(
        new PaceInsights().suggest(1700, observations) == null,
        "old benchmark observations not reused");
    var withRecentPain = new java.util.ArrayList<PaceInsights.Observation>();
    withRecentPain.add(new PaceInsights.Observation(300, 320, 5, 2, true, 1800));
    withRecentPain.addAll(observations);
    check(
        new PaceInsights().suggest(1800, withRecentPain) == null,
        "recent pain must not be filtered out to manufacture a faster recommendation");
    var struggling =
        List.of(
            new PaceInsights.Observation(360, 320, 8, 2, false, 1800),
            new PaceInsights.Observation(360, 320, 8, 2, false, 1800),
            new PaceInsights.Observation(360, 320, 8, 2, false, 1800));
    check(
        new PaceInsights().suggest(1800, struggling).benchmarkSeconds() > 1800,
        "consistent struggle prompts slower review");
    boolean invalid = false;
    try {
      new PaceProfile(5, 10, true);
    } catch (IllegalArgumentException expected) {
      invalid = true;
    }
    check(invalid, "implausible benchmark rejected");
    System.out.println("Coaching v0.3 tests passed: " + checks + " assertions.");
  }

  private static void check(boolean condition, String label) {
    checks++;
    if (!condition) throw new AssertionError(label);
  }
}
