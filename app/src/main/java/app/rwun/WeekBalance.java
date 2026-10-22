package app.rwun;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Scheduling conflicts, not a physiological load or readiness model. */
public final class WeekBalance {
  public static final List<String> TYPES=List.of("Cycling","Gym","Hiking","Mobility");
  public record Other(String id,LocalDate date,String type,int minutes,int effort,boolean completed) {
    public Other {if(id==null||!id.matches("[a-zA-Z0-9-]{1,80}")||date==null||!TYPES.contains(type)||minutes<5||minutes>720||effort<1||effort>10)throw new IllegalArgumentException("Check activity type, date, minutes (5–720) and effort (1–10)");}
    public boolean hard(){return effort>=7;}
  }
  public record Running(String id,LocalDate date,String title) {
    public boolean demanding(){String t=title.toLowerCase(Locale.ROOT);return t.contains("interval")||t.contains("tempo")||t.contains("long");}
  }
  public record Conflict(String runId,String otherId,String message) {}
  public static List<Conflict> conflicts(List<Running> runs,List<Other> others,LocalDate from,LocalDate to){
    List<Conflict> result=new ArrayList<>();
    for(var run:runs)if(!run.date().isBefore(from)&&run.date().isBefore(to))for(var other:others){long gap=Math.abs(ChronoUnit.DAYS.between(run.date(),other.date()));
      if(gap==0||(gap==1&&run.demanding()&&other.hard()))result.add(new Conflict(run.id(),other.id(),run.date()+" · "+run.title()+" and "+other.type()+" ("+other.date()+") "+(gap==0?"share a day. Review the combined effort.":"are a day apart with a hard effort logged/planned. Consider separating them.")));
    }return List.copyOf(result);
  }
}
