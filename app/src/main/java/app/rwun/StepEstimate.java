package app.rwun;
/** Cumulative Android counter deltas. Re-baseline at each resume; never invent missing steps. */
final class StepEstimate {
 // A transparent starting assumption, not a measured personal step length.
 static final double STARTER_WALK_METRES=.70;
 static double sessionLength(double calibrated,String kind){return calibrated>=.2&&calibrated<=("Walk".equals(kind)?1.5:2)?calibrated:("Walk".equals(kind)?STARTER_WALK_METRES:0);}
 static boolean noSteps(long seconds,long count,boolean enabled){return enabled&&seconds>=30&&count==0;}
 static String warning(long seconds,long count,long quietSeconds,boolean enabled,boolean paused){
  if(!enabled||paused)return "";
  if(noSteps(seconds,count,true))return "NO STEPS YET · if you are walking, check Physical activity permission and phone placement.";
  if(count>0&&quietSeconds>=60)return "NO RECENT STEPS · if you are still walking, check tracking. Time is continuing; distance may be incomplete.";
  return "";
 }
 record Count(long steps,long raw,long eventNanos,boolean partial) {}
 static Count accept(Count c,long raw,long nanos,long resumedNanos){
  if(raw<0||nanos<resumedNanos||nanos<=c.eventNanos())return c;
  if(c.raw()<0)return new Count(c.steps(),raw,nanos,c.partial());
  long delta=raw-c.raw();double seconds=(nanos-c.eventNanos())/1e9;
  if(delta<0||delta>Math.max(10,seconds*5))return new Count(c.steps(),raw,nanos,true);
  return new Count(Math.min(1000000,c.steps()+delta),raw,nanos,c.partial());
 }
 static double length(double km,long steps,String kind){double m=km*1000/steps;double max=kind.equals("Walk")?1.5:2;
  if(!Double.isFinite(km)||km<=0||steps<100||!Double.isFinite(m)||m<.2||m>max)throw new IllegalArgumentException("Calibrate over at least 100 detected steps with a realistic machine distance");return m;}
 static double km(long steps,double metres){if(steps<0||!Double.isFinite(metres)||metres<.2||metres>2)return 0;return steps*metres/1000;}
}
