package app.rwun;

import java.util.ArrayDeque;

/** 30-second GPS-derived pace; rejects short, stale and stationary windows. */
public final class RollingPace {
  private record Sample(long seconds, double metres) {}

  private final ArrayDeque<Sample> samples = new ArrayDeque<>();

  public void add(long seconds, double metres) {
    if (!samples.isEmpty() && seconds <= samples.peekLast().seconds()) return;
    samples.add(new Sample(seconds, metres));
    while (samples.size() > 2 && seconds - samples.peekFirst().seconds() > 30)
      samples.removeFirst();
  }

  public double value(long seconds) {
    if (samples.size() < 2 || seconds - samples.peekLast().seconds() > 10) return Double.NaN;
    var a = samples.peekFirst();
    var b = samples.peekLast();
    long dt = b.seconds() - a.seconds();
    double d = b.metres() - a.metres();
    return dt >= 15 && d >= 20 ? dt / d * 1000 : Double.NaN;
  }

  public void reset() {
    samples.clear();
  }
}
