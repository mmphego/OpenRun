package app.rwun;

/** Platform-independent filtering. Gaps and pause/resume never create a straight-line jump. */
public final class GpsAccumulator {
  public record Point(double lat, double lon, double accuracy, long monotonicMs, long epochMs) {}

  private final double maximumSpeed;
  private Point previous, candidate;
  private int consistentCandidates;
  private double metres;
  private int accepted, rejected, gaps;

  public GpsAccumulator(double initialMetres) {
    this(initialMetres,12);
  }
  public GpsAccumulator(double initialMetres,double maxSpeed) {
    if(!Double.isFinite(initialMetres)||initialMetres<0||!Double.isFinite(maxSpeed)||maxSpeed<=0||maxSpeed>50)throw new IllegalArgumentException("Invalid GPS accumulator");
    metres=initialMetres;maximumSpeed=maxSpeed;
  }

  public boolean add(Point p) {
    if (!Double.isFinite(p.lat())
        || !Double.isFinite(p.lon())
        || !Double.isFinite(p.accuracy())
        || Math.abs(p.lat()) > 90
        || Math.abs(p.lon()) > 180
        || p.accuracy() < 0
        || p.accuracy() > 30) {
      rejected++;
      return false;
    }
    if (previous != null) {
      long delta = p.monotonicMs() - previous.monotonicMs();
      if (delta <= 0) {
        rejected++;
        return false;
      }
      if (delta > 30000) {
        gaps++;
        candidate = null;
        consistentCandidates = 0;
        previous = p;
        accepted++;
        return true;
      }
      double distance = distance(previous.lat(), previous.lon(), p.lat(), p.lon());
      if (distance / (delta / 1000.0) > maximumSpeed) {
        long candidateDelta = candidate == null ? 0 : p.monotonicMs() - candidate.monotonicMs();
        boolean consistent =
            candidate != null
                && candidateDelta > 0
                && candidateDelta <= 10000
                && distance(candidate.lat(), candidate.lon(), p.lat(), p.lon())
                        / (candidateDelta / 1000.0)
                    <= maximumSpeed;
        consistentCandidates = consistent ? consistentCandidates + 1 : 1;
        candidate = p;
        if (consistentCandidates >= 3) {
          previous = p;
          candidate = null;
          consistentCandidates = 0;
          gaps++;
          accepted++;
          return true;
        }
        rejected++;
        return false;
      }
      candidate = null;
      consistentCandidates = 0;
      // Accumulate movement only beyond a small jitter threshold; keep anchor for slow movement.
      if (distance < 3) {
        return false;
      }
      metres += distance;
    }
    previous = p;
    accepted++;
    return true;
  }

  public void breakSegment() {
    previous = null;
    candidate = null;
    consistentCandidates = 0;
  }

  public double metres() {
    return metres;
  }

  public int accepted() {
    return accepted;
  }

  public int rejected() {
    return rejected;
  }

  public int gaps() {
    return gaps;
  }

  public static double distance(double lat1, double lon1, double lat2, double lon2) {
    double a =
        Math.pow(Math.sin(Math.toRadians(lat2 - lat1) / 2), 2)
            + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(Math.toRadians(lon2 - lon1) / 2), 2);
    return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
  }
}
