package app.rwun;

/** Transparent, conservative workload rules. No diagnostic or race-readiness claims. */
public final class AdaptiveCoach {
  public record Feedback(int effort, int fatigue, boolean pain, double completedRatio) {
    public Feedback {
      if (effort < 1
          || effort > 10
          || fatigue < 1
          || fatigue > 5
          || !Double.isFinite(completedRatio)
          || completedRatio < 0
          || completedRatio > 3)
        throw new IllegalArgumentException("Check effort, fatigue and completion.");
    }
  }

  public record Advice(double factor, boolean rest, String reason) {}

  public Advice advise(Feedback f) {
    if (f.pain())
      return new Advice(
          0,
          true,
          "Pain reported. Pause scheduled training. Resume only when appropriate; seek professional"
              + " assessment for persistent or concerning symptoms.");
    if (f.effort() >= 8 || f.fatigue() >= 4)
      return new Advice(
          .8,
          false,
          "High effort or fatigue: reduce the next seven days by 20%, keeping every run easy.");
    if (f.completedRatio() < .7)
      return new Advice(
          .9,
          false,
          "Less than 70% of the target completed: reduce the next seven days by 10%. Do not make up"
              + " missed distance.");
    return new Advice(
        1,
        false,
        "Maintain the plan. One comfortable run is not sufficient evidence to increase your"
            + " workload.");
  }
}
