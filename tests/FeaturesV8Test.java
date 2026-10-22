import app.rwun.*;
import java.time.*;import java.util.*;
public final class FeaturesV8Test {
  static int assertions;static void check(boolean ok,String label){assertions++;if(!ok)throw new AssertionError(label);}
  public static void main(String[] args)throws Exception{
    LocalDate start=LocalDate.of(2026,10,19);
    for(int mask=0;mask<128;mask++){Set<DayOfWeek> days=new HashSet<>();for(int i=0;i<7;i++)if((mask&(1<<i))!=0)days.add(DayOfWeek.of(i+1));if(days.size()<2||days.size()>5)continue;
      for(String mode:PlanPreferences.MODES){DayOfWeek longDay=days.iterator().next();var c=new PlanEngine.Config(8,days.size(),20,21.1,start);var sessions=new PlanPreferences(mode,days,longDay).generate(c,new PaceProfile(5,1800,true));check(sessions.size()==8*days.size(),"session count");Set<LocalDate> dates=new HashSet<>();for(var s:sessions){check(days.contains(s.date().getDayOfWeek()),"preferred day");check(dates.add(s.date()),"no collisions");check(s.km()>0&&Double.isFinite(s.km()),"positive mileage");check(!s.date().isBefore(start)&&s.date().isBefore(start.plusWeeks(8)),"within plan");if(s.id().endsWith("-"+(days.size()-1)))check(s.date().getDayOfWeek()==longDay,"long run weekday");}}
    }
    String calendar=CalendarExport.build("test",List.of(new PlanEngine.Session("1",start,"Easy; run, test","".length()+3,"Line 1\nLine 2")));
    check(calendar.contains("DTEND;VALUE=DATE:20261020"),"all-day end is exclusive");check(calendar.contains("Easy\\; run\\, test"),"calendar escaping");check(calendar.contains("Line 1\\nLine 2"),"calendar newline escaping");
    List<BestEfforts.Fix> route=new ArrayList<>();for(int i=0;i<=200;i++)route.add(new BestEfforts.Fix(0,i*.0001,i*2000L,0));var best=BestEfforts.calculate(route);check(best.containsKey(1000.),"GPS 1km detected");check(Math.abs(best.get(1000.)-179.864)<.1,"distance boundary interpolation");check(!best.containsKey(5000.),"no extrapolated 5km");
    var shortRoutes=List.of(new BestEfforts.Fix(0,0,1000,0),new BestEfforts.Fix(0,.1,2000,0));check(BestEfforts.calculate(shortRoutes).isEmpty(),"GPS teleport rejected");
    List<BestEfforts.Fix> paused=new ArrayList<>();for(int i=0;i<80;i++)paused.add(new BestEfforts.Fix(0,i*.0001,i*2000L,i<40?0:1));check(BestEfforts.calculate(paused).isEmpty(),"best effort does not bridge pauses");
    byte[] plain="private route and training".getBytes();char[] pass="a long backup passphrase".toCharArray();byte[] first=BackupCipher.encrypt(plain,pass),second=BackupCipher.encrypt(plain,pass);check(!Arrays.equals(first,second),"fresh encryption nonces/salts");check(Arrays.equals(plain,BackupCipher.decrypt(first,pass)),"backup crypto roundtrip");first[first.length-1]^=1;boolean damaged=false;try{BackupCipher.decrypt(first,pass);}catch(IllegalArgumentException expected){damaged=true;}check(damaged,"tamper rejection");boolean wrong=false;try{BackupCipher.decrypt(second,"wrong long password".toCharArray());}catch(IllegalArgumentException expected){wrong=true;}check(wrong,"wrong password rejected");
    check(StrengthPlan.routines().size()==6,"expanded original library");check(StrengthPlan.configured(StrengthPlan.find("foundation"),2,10).exercises().size()==3,"short strength session");check(StrengthPlan.configured(StrengthPlan.find("foundation"),2,20).exercises().get(0).prescription().startsWith("3 sets"),"progressive strength sets");
    var walk=new PlanEngine.Session("r",start,"Run / walk",2,"easy");check(WorkoutFactory.create(walk,PaceProfile.unset()).steps().stream().anyMatch(s->s.title().equals("Walk recovery")),"return programme uses executable walking steps");
    var tired=TrainingReview.review(List.of(new TrainingReview.Entry(start,5,9,4,true),new TrainingReview.Entry(start.plusDays(1),5,8,4,true)),start.plusDays(2));check(tired.reduce(),"multiple hard feedback sessions reduce workload");
    var imported=TrainingReview.review(List.of(new TrainingReview.Entry(start,5,9,4,false),new TrainingReview.Entry(start.plusDays(1),5,9,4,false)),start.plusDays(2));check(!imported.reduce(),"imported neutral feedback never drives adjustment");
    System.out.println("V0.8 feature tests passed: "+assertions+" assertions.");
  }
}
