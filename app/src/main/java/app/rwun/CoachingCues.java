package app.rwun;

import java.util.ArrayList;
import java.util.List;

/** Distance main set bracketed by timed warm-up/cool-down. Active time excludes pauses. */
public final class CoachingCues {
  private int phase = 0, lastKm = 0;
  private double mainStartMetres;
  private long coolStart;
  private final double target;

  public CoachingCues(double targetKm) {
    target = targetKm * 1000;
  }

  public List<String> update(long seconds, double metres) {
    List<String> cues = new ArrayList<>();
    if (phase == 0) {
      phase = 1;
      cues.add("Begin five minutes of walking to warm up.");
    }
    if (phase == 1 && seconds >= 300) {
      phase = 2;
      mainStartMetres = metres;
      cues.add("Warm up complete. Begin your main run at a conversational effort.");
    }
    int km = (int) (metres / 1000);
    if (km > lastKm) {
      lastKm = km;
      cues.add(km + " kilometres recorded. Keep your effort comfortable.");
    }
    if (phase == 2 && metres - mainStartMetres >= target) {
      phase = 3;
      coolStart = seconds;
      cues.add("Main distance reached. Walk for five minutes to cool down.");
    }
    if (phase == 3 && seconds - coolStart >= 300) {
      phase = 4;
      cues.add("Workout complete. Stop and save when you are ready.");
    }
    return cues;
  }

  public String phase() {
    return switch (phase) {
      case 0, 1 -> "Warm-up · 5 min";
      case 2 -> "Main run";
      case 3 -> "Cool-down · 5 min";
      default -> "Workout complete";
    };
  }
}
