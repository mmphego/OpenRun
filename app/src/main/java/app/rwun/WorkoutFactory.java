package app.rwun;

import java.util.ArrayList;
import java.util.List;

public final class WorkoutFactory {
  public record Step(
      String title, PaceProfile.Zone zone, double metres, long seconds, PaceProfile.Range pace) {
    public Step {
      if (title == null
          || zone == null
          || pace == null
          || !Double.isFinite(metres)
          || metres < 0
          || seconds < 0
          || metres > 0 && seconds > 0) throw new IllegalArgumentException("Invalid workout step");
    }

    public boolean quality() {
      return zone == PaceProfile.Zone.TEMPO || zone == PaceProfile.Zone.INTERVAL;
    }

    public String target() {
      return metres > 0
          ? Math.round(metres) + " m"
          : seconds > 0
              ? seconds / 60 + ":" + String.format(java.util.Locale.US, "%02d", seconds % 60)
              : "Open distance";
    }
  }

  public record Workout(List<Step> steps) {
    public Workout {
      steps = List.copyOf(steps);
      if (steps.isEmpty()) throw new IllegalArgumentException("Workout needs steps");
    }
  }

  public static Workout create(PlanEngine.Session session, PaceProfile profile) {
    List<Step> steps = new ArrayList<>();
    steps.add(timed("Warm-up walk / easy jog", 300));
    double metres = session.km() * 1000;
    if(session.title().equals("Run / walk")){int reps=Math.max(3,Math.min(10,(int)(metres/200)));for(int i=0;i<reps;i++){steps.add(new Step("Easy jog "+(i+1),PaceProfile.Zone.EASY,metres/reps,0,PaceProfile.Range.effort()));steps.add(timed("Walk recovery",60));}}else if (session.title().equals("Intervals") && metres >= 2400) {
      int reps = metres >= 4000 ? 5 : 4;
      double repeat = metres >= 4000 ? 400 : 200;
      double easy = (metres - reps * repeat) / 2;
      steps.add(distance("Easy lead-in", PaceProfile.Zone.EASY, easy, profile));
      for (int r = 0; r < reps; r++) {
        steps.add(
            distance(
                "Interval " + (r + 1) + " / " + reps, PaceProfile.Zone.INTERVAL, repeat, profile));
        if (r < reps - 1) steps.add(timed("Recovery walk / jog", 90));
      }
      steps.add(distance("Easy finish", PaceProfile.Zone.EASY, easy, profile));
    } else if (session.title().equals("Tempo run") && metres >= 2000) {
      double tempo = metres * .5;
      steps.add(distance("Easy lead-in", PaceProfile.Zone.EASY, metres * .25, profile));
      steps.add(distance("Controlled tempo", PaceProfile.Zone.TEMPO, tempo, profile));
      steps.add(distance("Easy finish", PaceProfile.Zone.EASY, metres * .25, profile));
    } else steps.add(distance(session.title(), PaceProfile.Zone.EASY, metres, profile));
    steps.add(timed("Cool-down walk", 300));
    double coach=coachPace(session.instructions());
    if(coach>0&&!session.title().equals("Intervals")&&!session.title().equals("Tempo run")&&!session.title().equals("Run / walk")){
      for(int i=0;i<steps.size();i++){Step step=steps.get(i);if(step.metres()>0)steps.set(i,new Step(step.title(),step.zone(),step.metres(),step.seconds(),new PaceProfile.Range(coach,Math.min(1800,coach*1.08))));}
    }
    return new Workout(steps);
  }

  static double coachPace(String instructions){
    var match=java.util.regex.Pattern.compile("^Coach pace: ([0-9]{1,2}):([0-9]{2}) min/km\\.\\n").matcher(instructions);
    if(!match.find())return 0;
    int seconds=Integer.parseInt(match.group(1))*60+Integer.parseInt(match.group(2));
    return Integer.parseInt(match.group(2))<60&&seconds>=180&&seconds<=1800?seconds:0;
  }

  public static Workout free() {
    return new Workout(
        List.of(
            timed("Warm-up walk / easy jog", 300),
            new Step("Free run", PaceProfile.Zone.EASY, 0, 0, PaceProfile.Range.effort())));
  }

  private static Step timed(String title, long seconds) {
    return new Step(title, PaceProfile.Zone.RECOVERY, 0, seconds, PaceProfile.Range.effort());
  }

  private static Step distance(
      String title, PaceProfile.Zone zone, double metres, PaceProfile profile) {
    return new Step(title, zone, metres, 0, profile.range(zone));
  }
}
