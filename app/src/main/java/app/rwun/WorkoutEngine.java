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
  private int paceSegments;private long paceAnchorSeconds;private double firstMainPace,lastMainPace,paceSum,paceSquares;

  public WorkoutEngine(WorkoutFactory.Workout workout) {
    this.workout = workout;
  }

  public record Event(String text,SpeechQueue.Kind kind){}
  public List<String> update(long seconds,double metres,double pace){List<String> out=new ArrayList<>();for(Event e:updateEvents(seconds,metres,pace))out.add(e.text());return out;}
  public List<Event> updateEvents(long seconds,double metres,double pace){
    List<Event> output = new ArrayList<>();
    if (complete()) return output;
    if (!started) {
      started = true;
      startSeconds = seconds;
      startMetres = metres;
      output.add(new Event(announce(),SpeechQueue.Kind.PHASE));
    }
    var step = step();
    if(index==1&&workout.steps().size()<=3&&step.zone()==PaceProfile.Zone.EASY&&step.seconds()==0){int km=(int)((metres-startMetres)/1000);if(km>paceSegments){long elapsed=seconds-startSeconds;if(km==paceSegments+1&&elapsed>paceAnchorSeconds){double split=elapsed-paceAnchorSeconds;if(paceSegments==0)firstMainPace=split;lastMainPace=split;paceSum+=split;paceSquares+=split*split;}else{firstMainPace=0;lastMainPace=0;}paceSegments=km;paceAnchorSeconds=elapsed;}}
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
      output.add(new Event(complete()?"Workout complete. Finish and save when ready.":announce(),SpeechQueue.Kind.PHASE));
    }
    int km = (int) (metres / 1000);
    if (km > lastKm) {
      long delta = seconds - lastSplitSeconds;
      if (km == lastKm + 1 && delta > 0)
        output.add(new Event("Kilometre "+km+". Split pace approximately "+spoken(delta)+" per kilometre.",SpeechQueue.Kind.SPLIT));
      else output.add(new Event(km+" kilometres recorded.",SpeechQueue.Kind.SPLIT));
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
          output.add(new Event(
              tooFast
                  ? "Ease back slightly. You are quicker than this step's target."
                  : "If comfortable, run a little quicker toward your target pace. Effort comes"
                      + " first.",SpeechQueue.Kind.PACE));
          lastAlert = seconds;
          outsideSince = -1;
        }
      } else outsideSince = -1;
    } else outsideSince = -1;
    return output;
  }

  int paceSegments(){return firstMainPace>0&&lastMainPace>0?paceSegments:0;}
  double firstMainPace(){return firstMainPace;}double lastMainPace(){return lastMainPace;}
  double paceVariation(){if(paceSegments()<2||paceSum<=0)return 0;double mean=paceSum/paceSegments;return Math.sqrt(Math.max(0,paceSquares/paceSegments-mean*mean))/mean;}
  String announcement(){return complete()?"Workout complete. Finish and save when ready.":announce();}
  double remainingMetres(double metres){return step().metres()>0?Math.max(0,step().metres()-(metres-startMetres)):0;}
  boolean finalEasyDistance(){return !complete()&&index==workout.steps().size()-2&&step().zone()==PaceProfile.Zone.EASY&&step().metres()>0;}
  private String announce() {
    var s = step();
    return s.title()
        + ". "
        + (s.seconds()>0?s.seconds()/60+" minutes "+s.seconds()%60+" seconds":s.target())
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
