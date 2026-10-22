package app.rwun;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Original, inspectable planning rules. An LLM cannot change these sessions. */
public final class RaceTraining {
  public record Evidence(LocalDate date,double km,long seconds,int effort,int fatigue,boolean pain,boolean reported) {
    public Evidence {if(date==null||!Double.isFinite(km)||km<0||km>250||seconds<1||effort<0||effort>10||fatigue<0||fatigue>5)throw new IllegalArgumentException("Invalid running evidence");}
  }
  public record Review(int runs28,double km28,long daysSinceLast,double baseline,double factor,boolean hold,String reason) {}
  public record Draft(PlanEngine.Config config,PlanPreferences preferences,List<PlanEngine.Session> sessions,Review review,String warning) {}
  public static Review review(List<Evidence> evidence,LocalDate today,boolean held) {
    List<Evidence> recent=evidence.stream().filter(e->!e.date().isAfter(today)&&!e.date().isBefore(today.minusDays(27))).collect(java.util.stream.Collectors.toList());
    LocalDate last=evidence.stream().map(Evidence::date).filter(d->!d.isAfter(today)).max(LocalDate::compareTo).orElse(null);
    long gap=last==null?-1:ChronoUnit.DAYS.between(last,today);double km=recent.stream().mapToDouble(Evidence::km).sum();
    boolean pain=held||recent.stream().anyMatch(e->!e.date().isBefore(today.minusDays(6))&&e.reported()&&e.pain());
    var training=TrainingReview.review(evidence.stream().filter(e->e.km()>0).map(e->new TrainingReview.Entry(e.date(),e.km(),e.effort(),e.fatigue(),e.reported())).collect(java.util.stream.Collectors.toList()),today);
    double factor=gap>=28?.65:gap>=14?.8:1;
    String reason=gap<0?"No recorded running history. Enter your current weekly distance; no fitness is inferred.":gap>=28?"No run recorded for at least 28 days: offer a lower-volume run/walk return week.":gap>=14?"No run recorded for at least 14 days: offer a lighter, easy return week.":"Recent running is recorded. Maintain the plan unless the evidence supports a reduction.";
    if(training.reduce()){factor=Math.min(factor,.8);reason+=" "+training.reason();}
    if(pain){factor=1;reason="A training hold or recent reported pain needs review. No schedule changes are offered and no hold is cleared.";}
    return new Review(recent.size(),Math.round(km*10)/10.,gap,Math.round(km/4*10)/10.,factor,pain,reason);
  }
  public static Draft draft(LocalDate today,LocalDate race,double goal,int weeks,Set<DayOfWeek> days,DayOfWeek longDay,double enteredVolume,PaceProfile profile,List<Evidence> evidence,boolean held){
    if(!Double.isFinite(goal)||goal<5||goal>42.2)throw new IllegalArgumentException("Structured race plans support 5 km, 10 km, half marathon and marathon.");
    Review review=review(evidence,today,held);
    double volume=enteredVolume;
    if(!Double.isFinite(volume)||volume<4||volume>120)throw new IllegalArgumentException("Enter current weekly running between 4 and 120 km.");
    // A complete 28-day window includes inactive days; do not extrapolate a single run.
    if(review.runs28()>=3&&review.baseline()>=4)volume=Math.min(volume,review.baseline());
    volume=Math.max(4,Math.floor(volume*review.factor()*10)/10.);
    PlanEngine.Config config=RacePlanner.config(today,race,weeks,days.size(),volume,goal);
    boolean returning=review.daysSinceLast()>=14;
    PlanPreferences preferences=new PlanPreferences(returning?"Return to running":"Race preparation",days,longDay);
    List<PlanEngine.Session> out=new ArrayList<>();
    for(var s:preferences.generate(config,returning?new PaceProfile(profile.distanceKm(),profile.seconds(),false):profile)){
      long remaining=ChronoUnit.DAYS.between(s.date(),race);
      // Do not put a long/quality workout immediately before the target event.
      double km=s.km();String title=s.title(),instructions=s.instructions();
      if(remaining<=7){km=Math.max(.5,Math.min(km,goal<=10?2:4));title="Easy race-week run";instructions="Short, conversational effort. No catch-up mileage. Rest instead if fatigued. Race day is shown separately in Calendar.";}
      if(remaining<=2){km=Math.min(km,1.5);title="Optional easy shakeout";instructions="Optional short easy movement. Rest is welcome; this is not a fitness test.";}
      out.add(new PlanEngine.Session(s.id(),s.date(),title,km,instructions));
    }
    String warning="Original rules; race readiness and a finish time are not guaranteed. ";
    if(config.start().isAfter(today))warning+="Your race build starts "+config.start()+"; no training is scheduled before that date. ";
    if((goal>=21&&volume<15)||(goal>=42&&volume<30)||weeks<(goal>=42?16:goal>=21?10:6))warning+="Your current volume or available time may be insufficient for this distance. Consider a shorter event or later date; this plan does not compress missed training. ";
    if(review.hold())warning+="Training remains paused. ";
    return new Draft(config,preferences,List.copyOf(out),review,warning);
  }
  public static Draft goalDraft(LocalDate today,double goal,int weeks,Set<DayOfWeek> days,DayOfWeek longDay,double volume,PaceProfile profile,List<Evidence> evidence,boolean held,String mode){
    Review review=review(evidence,today,held);if(!Double.isFinite(volume)||volume<4||volume>120)throw new IllegalArgumentException("Enter current weekly running between 4 and 120 km.");
    if(review.runs28()>=3&&review.baseline()>=4)volume=Math.min(volume,review.baseline());volume=Math.max(4,Math.floor(volume*review.factor()*10)/10.);
    String programme=review.daysSinceLast()>=14||mode.equals("start")||mode.equals("return")?"Return to running":mode.equals("general")?"Maintenance":"Race preparation";
    PlanEngine.Config config=new PlanEngine.Config(weeks,days.size(),volume,goal,today);PlanPreferences preferences=new PlanPreferences(programme,days,longDay);
    PaceProfile actual=programme.equals("Return to running")?new PaceProfile(profile.distanceKm(),profile.seconds(),false):profile;
    return new Draft(config,preferences,preferences.generate(config,actual),review,"Original running rules. This is a distance goal, not a race entry or a guarantee of readiness.");
  }
  public static List<ScheduleProposer.Change> changes(List<PlanEngine.Session> pending,Review review,LocalDate today){
    if(review.hold()||review.factor()>=1)return List.of();
    return pending.stream().filter(s->s.date().isAfter(today)&&!s.date().isAfter(today.plusDays(7))).map(s->new ScheduleProposer.Change(s.id(),s.date(),s.date(),review.daysSinceLast()>=28?"Run / walk":"Easy recovery run",Math.max(.1,Math.floor(s.km()*review.factor()*10)/10),false,review.reason())).collect(java.util.stream.Collectors.toList());
  }
}
