package app.rwun;

import java.util.Locale;

/** Original, explicit benchmark heuristic. Users review estimates; effort takes priority. */
public record PaceProfile(double distanceKm, long seconds, boolean speedWork) {
  public enum Zone {
    EASY,
    STEADY,
    TEMPO,
    INTERVAL,
    RECOVERY
  }

  public record Range(double fastSeconds, double slowSeconds) {
    public Range {
      if (!Double.isFinite(fastSeconds)
          || !Double.isFinite(slowSeconds)
          || fastSeconds < 0
          || slowSeconds < fastSeconds) throw new IllegalArgumentException("Invalid pace range");
    }

    public static Range effort() {
      return new Range(0, 0);
    }

    public boolean defined() {
      return fastSeconds > 0;
    }

    public double middle() {
      return (fastSeconds + slowSeconds) / 2;
    }

    public String label() {
      return defined() ? format(fastSeconds) + "–" + format(slowSeconds) + " min/km" : "By effort";
    }
  }

  public PaceProfile {
    if (!Double.isFinite(distanceKm)
        || distanceKm < 5
        || distanceKm > 42.2
        || seconds < 0
        || seconds > 86400
        || seconds > 0 && (seconds / distanceKm < 120 || seconds / distanceKm > 1800))
      throw new IllegalArgumentException(
          "Enter a plausible recent 5 km, 10 km, half-marathon or marathon time.");
  }

  public static PaceProfile unset() {
    return new PaceProfile(5, 0, false);
  }

  public long equivalent5k() {
    return seconds == 0 ? 0 : Math.round(seconds * Math.pow(5 / distanceKm, 1.06));
  }

  public Range range(Zone zone) {
    if (seconds == 0 || zone == Zone.RECOVERY) return Range.effort();
    double pace = equivalent5k() / 5.0;
    return switch (zone) {
      case EASY -> new Range(pace * 1.22, pace * 1.45);
      case STEADY -> new Range(pace * 1.15, pace * 1.25);
      case TEMPO -> new Range(pace * 1.08, pace * 1.16);
      case INTERVAL -> new Range(pace, pace * 1.06);
      default -> Range.effort();
    };
  }

  public static String format(double seconds) {
    if (!Double.isFinite(seconds) || seconds <= 0) return "—";
    long s = Math.round(seconds);
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
  }
}
