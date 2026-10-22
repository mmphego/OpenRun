import app.rwun.*;

public class FeatureTests {
  private static int checks;

  public static void main(String[] args) {
    GpsAccumulator g = new GpsAccumulator(0);
    check(g.add(new GpsAccumulator.Point(0, 0, 5, 1000, 1000)), "first fix");
    check(!g.add(new GpsAccumulator.Point(0, 0.000001, 5, 2000, 2000)), "jitter ignored");
    check(!g.add(new GpsAccumulator.Point(0, 0.1, 5, 3000, 3000)), "teleport rejected");
    check(!g.add(new GpsAccumulator.Point(0, .0001, 100, 4000, 4000)), "poor accuracy rejected");
    check(g.add(new GpsAccumulator.Point(0, .0001, 5, 5000, 5000)), "normal movement accepted");
    check(g.metres() > 11 && g.metres() < 12, "haversine distance");
    double before = g.metres();
    g.breakSegment();
    g.add(new GpsAccumulator.Point(1, 1, 5, 6000, 6000));
    check(g.metres() == before, "pause jump excluded");
    g.add(new GpsAccumulator.Point(2, 2, 5, 60000, 60000));
    check(g.metres() == before && g.gaps() == 1, "lost signal gap excluded");
    check(!g.add(new GpsAccumulator.Point(2, 2, 5, 60000, 60000)), "duplicate fix rejected");
    check(
        !g.add(new GpsAccumulator.Point(Double.NaN, 0, 5, 61000, 61000)),
        "invalid coordinate rejected");
    GpsAccumulator recovery = new GpsAccumulator(0);
    recovery.add(new GpsAccumulator.Point(0, 0, 5, 1000, 1000));
    recovery.add(new GpsAccumulator.Point(0, 1, 5, 2000, 2000));
    recovery.add(new GpsAccumulator.Point(0, 1.00001, 5, 4000, 4000));
    check(
        recovery.add(new GpsAccumulator.Point(0, 1.00002, 5, 6000, 6000)),
        "consistent fixes establish a new anchor after a large jump");
    check(recovery.metres() == 0 && recovery.gaps() == 1, "re-anchoring does not count the jump");
    recovery.add(new GpsAccumulator.Point(0, 1.0001, 5, 8000, 8000));
    check(recovery.metres() > 8 && recovery.metres() < 10, "distance resumes from the new anchor");
    AdaptiveCoach a = new AdaptiveCoach();
    check(a.advise(new AdaptiveCoach.Feedback(3, 1, true, 1)).rest(), "pain pauses training");
    check(
        a.advise(new AdaptiveCoach.Feedback(8, 2, false, 1)).factor() == .8,
        "high effort reduces load");
    check(
        a.advise(new AdaptiveCoach.Feedback(3, 4, false, 1)).factor() == .8,
        "fatigue reduces load");
    check(
        a.advise(new AdaptiveCoach.Feedback(4, 2, false, .5)).factor() == .9,
        "partial completion reduces load");
    check(
        a.advise(new AdaptiveCoach.Feedback(2, 1, false, 1)).factor() == 1,
        "comfortable single run does not increase load");
    expectInvalid(() -> new AdaptiveCoach.Feedback(0, 1, false, 1));
    expectInvalid(() -> new AdaptiveCoach.Feedback(3, 6, false, 1));
    CoachingCues c = new CoachingCues(1);
    check(c.update(0, 0).size() == 1, "initial warmup cue");
    check(c.update(299, 100).isEmpty(), "no early main cue");
    check(
        c.update(300, 100).size() == 1 && c.phase().equals("Main run"), "main phase after warmup");
    check(c.update(400, 1000).size() == 1, "one kilometre cue");
    check(c.update(401, 1000).isEmpty(), "no duplicate kilometre cue");
    check(
        c.update(500, 1100).size() == 1 && c.phase().contains("Cool-down"),
        "distance excludes warmup");
    check(c.update(799, 1150).isEmpty(), "no early finish");
    check(
        c.update(800, 1150).size() == 1 && c.phase().equals("Workout complete"),
        "cooldown completed");
    check(c.update(900, 1150).isEmpty(), "no repeat finish cue");
    System.out.println("Feature tests passed: " + checks + " assertions.");
  }

  static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }

  static void expectInvalid(Runnable r) {
    try {
      r.run();
    } catch (IllegalArgumentException expected) {
      checks++;
      return;
    }
    throw new AssertionError("Invalid input accepted");
  }
}
