package app.rwun;
import java.util.ArrayList;
/** Original repeatable stages; not a calendar deadline or medical clearance. */
public final class BeginnerProgramme {
 private BeginnerProgramme(){}
 private static final int[] RUN={60,75,90,105,120,135,150,165,180,225,300,420,660,1380,1500,1620,1800};
 private static final int[] REPS={6,6,6,6,6,6,6,6,6,5,4,3,2,1,1,1,1};
 public static int count(){return RUN.length;}
 private static void check(int s){if(s<0||s>=count())throw new IllegalArgumentException("Choose a valid beginner stage");}
 public static int runSeconds(int s){check(s);return RUN[s];}
 public static int repetitions(int s){check(s);return REPS[s];}
 public static int totalSeconds(int s){check(s);return 600+RUN[s]*REPS[s]+90*(REPS[s]-1);}
 public static String duration(int s){return s%60==0?s/60+" min":s/60+" min "+s%60+" sec";}
 public static String describe(int s){check(s);return "Stage "+(s+1)+": "+REPS[s]+" × "+duration(RUN[s])+" comfortable running"+(REPS[s]>1?", with 90 sec walking between sections":"")+". Five-minute warm-up and cool-down included. Total "+duration(totalSeconds(s))+".";}
 public static int stageFromId(String id){if(id==null||!id.startsWith("beginner:"))return -1;try{int s=Integer.parseInt(id.split(":")[1]);check(s);return s;}catch(RuntimeException e){throw new IllegalArgumentException("Invalid beginner session",e);}}
 public static WorkoutFactory.Workout workout(int s){check(s);var steps=new ArrayList<WorkoutFactory.Step>();steps.add(step("Warm-up walk",300,PaceProfile.Zone.RECOVERY));for(int i=0;i<REPS[s];i++){steps.add(step("Comfortable run "+(i+1)+" of "+REPS[s],RUN[s],PaceProfile.Zone.EASY));if(i+1<REPS[s])steps.add(step("Walk recovery. No need to make up time",90,PaceProfile.Zone.RECOVERY));}steps.add(step("Cool-down walk",300,PaceProfile.Zone.RECOVERY));return new WorkoutFactory.Workout(steps);}
 private static WorkoutFactory.Step step(String title,int seconds,PaceProfile.Zone zone){return new WorkoutFactory.Step(title,zone,0,seconds,PaceProfile.Range.effort());}
}
