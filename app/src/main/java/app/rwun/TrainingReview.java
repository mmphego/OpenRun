package app.rwun;
import java.time.*;import java.util.*;
/** Transparent multi-session workload review. It never increases mileage or clears a hold. */
public final class TrainingReview {
  public record Entry(LocalDate date,double km,int effort,int fatigue,boolean feedbackRecorded){}
  public record Review(double weekKm,double priorWeeklyKm,boolean reduce,String reason){}
  public static Review review(List<Entry> entries,LocalDate today){
    double week=0,prior=0;int feedback=0,hard=0;LocalDate oldest=today;
    for(var run:entries){if(run.date.isAfter(today))continue;oldest=run.date.isBefore(oldest)?run.date:oldest;if(!run.date.isBefore(today.minusDays(6))){week+=run.km;if(run.feedbackRecorded){feedback++;if(run.effort>=8||run.fatigue>=4)hard++;}}else if(!run.date.isBefore(today.minusDays(27)))prior+=run.km;}
    double baseline=prior/3;boolean tired=feedback>=2&&hard>=2;boolean spike=!oldest.isAfter(today.minusDays(27))&&baseline>=5&&week>baseline*1.3;
    String reason=tired?"At least two recent sessions had high effort or fatigue. Consider reducing the next seven days by 20%.":spike?"This week exceeds the preceding three-week average by more than 30%. Consider a 20% reduction rather than adding more mileage.":feedback<2?"Not enough recent reported feedback for a multi-session recommendation. Imported effort is not inferred.":"No workload reduction is suggested by these simple rules. This is not an assessment of injury readiness.";
    return new Review(week,baseline,tired||spike,reason);
  }
}
