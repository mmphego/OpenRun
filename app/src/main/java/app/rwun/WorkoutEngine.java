package app.rwun;

import java.util.ArrayList;
import java.util.List;

/** Deterministic step transitions and sustained, throttled pace guidance. */
public final class WorkoutEngine {
  private final WorkoutFactory.Workout workout;
  private int index, lastKm;
  private long startSeconds, outsideSince = -1, lastAlert = -60, lastSplitSeconds;
  private double startMetres, qualityMetres;
  private long qualitySeconds;
  private boolean started;

  public WorkoutEngine(WorkoutFactory.Workout workout) {
    this.workout = workout;
  }

  public List<String> update(long seconds, double metres, double pace) {
    List<String> output = new ArrayList<>();
    if (complete()) return output;
    if (!started) {
      started = true;
      startSeconds = seconds;
      startMetres = metres;
      output.add(announce());
    }
    var step = step();
    boolean finished =
        step.metres() > 0 && metres - startMetres >= step.metres()
            || step.seconds() > 0 && seconds - startSeconds >= step.seconds();
    if (finished) {
      if (step.quality()) {
        qualityMetres += Math.max(0, metres - startMetres);
        qualitySeconds += Math.max(0, seconds - startSeconds);
      }
      index++;
      startSeconds = seconds;
      startMetres = metres;
      outsideSince = -1;
      output.add(complete() ? "Workout complete. Finish and save when ready." : announce());
    }
    int km = (int) (metres / 1000);
    if (km > lastKm) {
      long delta = seconds - lastSplitSeconds;
      if (km == lastKm + 1 && delta > 0)
        output.add(
            "Kilometre " + km + ". Split pace approximately " + spoken(delta) + " per kilometre.");
      else output.add(km + " kilometres recorded.");
      lastKm = km;
      lastSplitSeconds = seconds;
    }
    if (!complete()
        && Double.isFinite(pace)
        && step().pace().defined()
        && seconds - startSeconds >= 15) {
      var range = step().pace();
      boolean tooFast = pace < range.fastSeconds() * .97,
          tooSlow = step().quality() && pace > range.slowSeconds() * 1.03;
      if (tooFast || tooSlow) {
        if (outsideSince < 0) outsideSince = seconds;
        if (seconds - outsideSince >= 15 && seconds - lastAlert >= 30) {
          output.add(
              tooFast
                  ? "Ease back slightly. You are quicker than this step's target."
                  : "If comfortable, run a little quicker toward your target pace. Effort comes"
                      + " first.");
          lastAlert = seconds;
          outsideSince = -1;
        }
      } else outsideSince = -1;
    } else outsideSince = -1;
    return output;
  }

  private String announce() {
    var s = step();
    return s.title()
        + ". "
        + s.target()
        + ". "
        + (s.pace().defined() ? "Target " + s.pace().label() : "Use a comfortable effort.");
  }

  private String spoken(long seconds) {
    return seconds / 60 + " minutes " + seconds % 60 + " seconds";
  }

  public boolean complete() {
    return index >= workout.steps().size();
  }

  public int stepIndex() {
    return index;
  }

  public WorkoutFactory.Step step() {
    return workout.steps().get(Math.min(index, workout.steps().size() - 1));
  }

  public String phase() {
    return complete()
        ? "Workout complete"
        : (index + 1) + " / " + workout.steps().size() + " · " + step().title();
  }

  public String remaining(long seconds, double metres) {
    if (complete()) return "Ready to save";
    return step().metres() > 0
        ? Math.max(0, Math.round(step().metres() - (metres - startMetres))) + " m remaining"
        : step().seconds() > 0
            ? Math.max(0, step().seconds() - (seconds - startSeconds)) + " sec remaining"
            : "Open distance";
  }

  public double qualityMetres() {
    return qualityMetres;
  }

  public long qualitySeconds() {
    return qualitySeconds;
  }

  public boolean qualityComplete() {
    if (qualitySeconds <= 0) return false;
    for (int i = index; i < workout.steps().size(); i++)
      if (workout.steps().get(i).quality()) return false;
    return true;
  }
}
