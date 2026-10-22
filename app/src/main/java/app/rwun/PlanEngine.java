package app.rwun;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Original deterministic demonstration planner; not a clinically validated coach. */
public final class PlanEngine {
  public record Session(String id, LocalDate date, String title, double km, String instructions) {}

  public record Config(int weeks, int days, double weeklyKm, double goalKm, LocalDate start) {
    public Config {
      if (weeks < 4
          || weeks > 24
          || days < 2
          || days > 5
          || !Double.isFinite(weeklyKm)
          || weeklyKm < 4
          || weeklyKm > 120
          || !Double.isFinite(goalKm)
          || goalKm < 5
          || goalKm > 42.2
          || start == null) throw new IllegalArgumentException("Check plan settings.");
    }
  }

  public List<Session> generate(Config c) {
    return generate(c, PaceProfile.unset());
  }

  public List<Session> generate(Config c, PaceProfile profile) {
    List<Session> result = new ArrayList<>();
    int[][] offsets = {{0, 4}, {0, 2, 5}, {0, 2, 4, 6}, {0, 1, 3, 4, 6}};
    for (int w = 0; w < c.weeks(); w++) {
      // Conservative illustrative progression, a recovery week every fourth week, then taper.
      double volume =
          Math.min(
              c.weeklyKm() * Math.pow(1.04, Math.min(w, c.weeks() - 3)),
              Math.max(c.weeklyKm(), c.goalKm() * 1.5));
      if ((w + 1) % 4 == 0) volume *= .8;
      if (w == c.weeks() - 2) volume *= .8;
      if (w == c.weeks() - 1) volume *= .6;
      double longShare = c.days() == 2 ? .55 : .35;
      for (int d = 0; d < c.days(); d++) {
        boolean longer = d == c.days() - 1;
        double distance = volume * (longer ? longShare : (1 - longShare) / (c.days() - 1));
        String title = longer ? "Long easy run" : "Easy run";
        String instructions =
            "Keep a conversational effort. Walk breaks are welcome. Stop if you experience pain,"
                + " dizziness or chest discomfort.";
        if (!longer && d == 1 && w > 1 && (w + 1) % 4 != 0) {
          title = "Easy run + strides";
          instructions +=
              " If comfortable, finish with 4 relaxed 15-second strides, walking 60 seconds between"
                  + " each. These are not sprints.";
        }
        if (profile.speedWork()
            && profile.seconds() > 0
            && c.days() >= 3
            && c.weeklyKm() >= 15
            && c.weeks() >= 6
            && d == 1
            && !longer
            && w >= 2
            && w < c.weeks() - 2
            && (w + 1) % 4 != 0
            && distance >= 2.4) {
          title = (w / 2) % 2 == 1 ? "Intervals" : "Tempo run";
          instructions =
              "Follow the structured steps. Targets are estimates from your recent race, not"
                  + " instructions to override fatigue or pain.";
        }
        result.add(
            new Session(
                w + "-" + d,
                c.start().plusWeeks(w).plusDays(offsets[c.days() - 2][d]),
                title,
                Math.round(distance * 10) / 10.0,
                instructions));
      }
    }
    return List.copyOf(result);
  }

  public static String distance(double km) {
    return String.format(Locale.US, "%.1f km", km);
  }
}
