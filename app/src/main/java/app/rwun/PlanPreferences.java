package app.rwun;

import java.time.*;
import java.util.*;

/** Schedule and programme choices. All progression is original and deterministic. */
public record PlanPreferences(String mode,Set<DayOfWeek> weekdays,DayOfWeek longDay) {
  public static final List<String> MODES=List.of("Race preparation","Base building","Maintenance","Return to running");
  public PlanPreferences {
    if(!MODES.contains(mode)||weekdays==null||weekdays.size()<2||weekdays.size()>5||longDay==null||!weekdays.contains(longDay))throw new IllegalArgumentException("Choose 2–5 training days including your long-run day.");
    weekdays=Set.copyOf(weekdays);
  }
  public List<PlanEngine.Session> generate(PlanEngine.Config config,PaceProfile profile){
    if(weekdays.size()!=config.days())throw new IllegalArgumentException("Select exactly "+config.days()+" training days.");
    List<PlanEngine.Session> base=new PlanEngine().generate(config,profile),out=new ArrayList<>();
    for(int w=0;w<config.weeks();w++){
      LocalDate start=config.start().plusWeeks(w);List<LocalDate> ordinary=new ArrayList<>();LocalDate longer=null;
      for(int i=0;i<7;i++){LocalDate date=start.plusDays(i);if(weekdays.contains(date.getDayOfWeek())){if(date.getDayOfWeek()==longDay)longer=date;else ordinary.add(date);}}
      // Place a quality session as far from the long run as this availability permits.
      final LocalDate longDate=longer;
      if(ordinary.size()>1){LocalDate quality=ordinary.stream().max(Comparator.comparingInt(d->{int gap=(int)Math.abs(java.time.temporal.ChronoUnit.DAYS.between(d,longDate));return Math.min(gap,7-gap);})).get();ordinary.remove(quality);ordinary.add(1,quality);}
      for(int d=0;d<config.days();d++){
        var old=base.get(w*config.days()+d);LocalDate date=d==config.days()-1?longer:ordinary.get(d);String title=old.title(),instructions=old.instructions();double km=old.km();
        if(!mode.equals("Race preparation")){
          title=d==config.days()-1?"Long easy run":"Easy run";
          double share=d==config.days()-1?(config.days()==2?.55:.35):(1-(config.days()==2?.55:.35))/(config.days()-1);
          double volume=config.weeklyKm();
          if(mode.equals("Base building"))volume*=Math.pow(1.02,Math.min(w,8));
          if(mode.equals("Return to running")){volume*=.65*Math.pow(1.02,Math.min(w,8));title="Run / walk";instructions="Alternate an easy jog with walking. Stay conversational; take extra walking whenever needed.";}
          if((w+1)%4==0)volume*=.8;
          km=Math.round(volume*share*10)/10.;
        }
        if((title.equals("Intervals")||title.equals("Tempo run"))&&Math.min(Math.abs(date.getDayOfWeek().getValue()-longDay.getValue()),7-Math.abs(date.getDayOfWeek().getValue()-longDay.getValue()))<2){title="Easy run";instructions="An easy session keeps your quality work and long run from being back to back.";}
        out.add(new PlanEngine.Session(old.id(),date,title,km,instructions));
      }
    }
    out.sort(Comparator.comparing(PlanEngine.Session::date));return List.copyOf(out);
  }
}
