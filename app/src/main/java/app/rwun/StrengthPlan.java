package app.rwun;

import java.util.List;

/** Original bodyweight routines. Session completion is explicitly reported by the runner. */
public final class StrengthPlan {
  public record Exercise(String name, String prescription, String cue) {}
  public record Routine(String id, String title, String focus, List<Exercise> exercises) {}

  public static List<Routine> routines() {
    return java.util.stream.Stream.concat(List.of(
      new Routine("foundation", "Runner foundation", "Legs, hips and trunk · about 20 minutes",
        List.of(
          new Exercise("Chair squat", "2 sets × 8–12 repetitions", "Sit back towards a stable chair. Stand with control."),
          new Exercise("Supported reverse lunge", "2 sets × 6–8 each side", "Use a stable support; take a comfortable step backwards."),
          new Exercise("Calf raise", "2 sets × 10–15 repetitions", "Rise and lower slowly. Use support for balance."),
          new Exercise("Glute bridge", "2 sets × 10–12 repetitions", "Press through your feet without arching your lower back."),
          new Exercise("Bird dog", "2 sets × 6–8 each side", "Keep your trunk steady as you extend the opposite arm and leg."))),
      new Routine("mobility", "Stability & mobility", "Balance and easy movement · about 12 minutes",
        List.of(
          new Exercise("Supported single-leg balance", "2 × 20–30 seconds each side", "Keep a support within reach and look straight ahead."),
          new Exercise("Ankle rocks", "2 sets × 8 each side", "Gently move your knee forwards while keeping your heel down."),
          new Exercise("Hip hinge", "2 sets × 8–10 repetitions", "Move from your hips with a comfortable range and a long spine."),
          new Exercise("Standing hip circles", "1 set × 6 each direction", "Use support and make small, controlled circles."),
          new Exercise("Gentle calf stretch", "2 × 20 seconds each side", "Use a wall for support. Hold a comfortable stretch.")))).stream(), List.of(
      new Routine("dumbbells","Dumbbell foundation","Dumbbells · legs, back and trunk · 20 minutes",List.of(
        new Exercise("Goblet squat","2 sets × 8 repetitions","Hold a light dumbbell close to your chest. Use a comfortable squat depth."),
        new Exercise("Dumbbell hip hinge","2 sets × 8 repetitions","Keep the weights close and bend at the hips with a long spine."),
        new Exercise("Supported dumbbell row","2 sets × 8 each side","Support yourself on a stable bench and move the elbow towards the hip."),
        new Exercise("Suitcase carry","2 × 30 seconds each side","Walk upright with a light weight at one side; avoid leaning."))),
      new Routine("yoga","Gentle runner yoga","Easy movement and breathing · 10 minutes",List.of(
        new Exercise("Mountain pose","3 × 4 comfortable breaths","Stand tall, relax your shoulders and breathe naturally."),
        new Exercise("Supported low lunge","2 × 20 seconds each side","Use a folded towel under the knee and stay within a comfortable range."),
        new Exercise("Seated gentle twist","2 × 15 seconds each side","Rotate gently without pulling with your arms."),
        new Exercise("Resting breath","1 × 60 seconds","Rest in a comfortable position and allow breathing to settle."))),
      new Routine("pilates","Pilates control","Trunk and hip control · 15 minutes",List.of(
        new Exercise("Supine heel slide","2 sets × 8 each side","Slide one heel away while keeping your pelvis steady."),
        new Exercise("Bent-knee opening","2 sets × 8 each side","Let one knee open a small distance without rolling your hips."),
        new Exercise("Side-lying leg lift","2 sets × 8 each side","Lift a small distance and keep hips stacked."),
        new Exercise("Glute bridge","2 sets × 8 repetitions","Raise and lower slowly without arching your back."))),
      new Routine("postrun","Post-run reset","Gentle mobility · 10 minutes",List.of(
        new Exercise("Easy walking","1 × 2 minutes","Let your effort and breathing return towards resting levels."),
        new Exercise("Ankle circles","1 set × 8 each direction","Move slowly through a comfortable range."),
        new Exercise("Supported calf stretch","2 × 20 seconds each side","Keep a comfortable stretch without bouncing."),
        new Exercise("Gentle hip movement","1 × 60 seconds","Choose small comfortable hip circles and relaxed walking.")))
    ).stream()).collect(java.util.stream.Collectors.toList());
  }

  public static Routine configured(Routine routine,int level,int minutes){
    if(level<1||level>3||!List.of(10,20,30).contains(minutes))throw new IllegalArgumentException("Invalid strength settings");
    int count=minutes==10?Math.min(3,routine.exercises().size()):routine.exercises().size();
    var exercises=routine.exercises().subList(0,count).stream().map(e->new Exercise(e.name(),level>1?e.prescription().replace("2 sets",(level+1)+" sets"):e.prescription(),e.cue())).collect(java.util.stream.Collectors.toList());
    return new Routine(routine.id(),routine.title(),routine.focus()+" · level "+level,exercises);
  }

  public static Routine find(String id) {
    return routines().stream().filter(r -> r.id().equals(id)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown strength routine"));
  }
}
