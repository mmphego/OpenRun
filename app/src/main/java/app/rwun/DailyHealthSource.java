package app.rwun;

import android.content.Context;
import android.health.connect.*;
import android.health.connect.datatypes.*;
import android.os.OutcomeReceiver;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only Health Connect adapter. No network, location, writes or raw-record persistence. */
final class DailyHealthSource {
    static final String STEPS="android.permission.health.READ_STEPS",SLEEP="android.permission.health.READ_SLEEP",
            RESTING_HR="android.permission.health.READ_RESTING_HEART_RATE";
    interface Source {
        CompletableFuture<DailyHealthRules.Movement> movement(Instant from,Instant to);
        CompletableFuture<String> recovery(Instant now,ZoneId zone,boolean sleep,boolean restingHr);
    }
    static Source create(Context context){
        if(!HealthAccess.available(context))throw new IllegalStateException("Health Connect requires Android 14+ and an available system service.");
        return new Api34(context.getApplicationContext());
    }
    @android.annotation.TargetApi(34)
    static final class Api34 implements Source {
        final HealthConnectManager manager;final Executor callback;
        Api34(Context context){manager=context.getSystemService(HealthConnectManager.class);callback=context.getMainExecutor();}
        static TimeInstantRangeFilter range(Instant from,Instant to){return new TimeInstantRangeFilter.Builder().setStartTime(from).setEndTime(to).build();}
        <T extends android.health.connect.datatypes.Record> CompletableFuture<List<T>> records(Class<T> type,Instant from,Instant to){
            CompletableFuture<List<T>> future=new CompletableFuture<>();
            try{
                manager.readRecords(new ReadRecordsRequestUsingFilters.Builder<>(type).setTimeRangeFilter(range(from,to)).setAscending(false).setPageSize(1000).build(),callback,
                        new OutcomeReceiver<ReadRecordsResponse<T>,HealthConnectException>(){
                            public void onResult(ReadRecordsResponse<T> response){
                                if(response.getNextPageToken()!=-1)future.completeExceptionally(new IllegalStateException("Too many health records; this check is incomplete."));
                                else future.complete(response.getRecords());
                            }
                            public void onError(HealthConnectException error){future.completeExceptionally(error);}
                        });
            }catch(Exception error){future.completeExceptionally(error);}
            return future.orTimeout(30,TimeUnit.SECONDS);
        }
        public CompletableFuture<DailyHealthRules.Movement> movement(Instant from,Instant to){
            CompletableFuture<Long> total=new CompletableFuture<>();
            try{
                var request=new AggregateRecordsRequest.Builder<Long>(range(from,to)).addAggregationType(StepsRecord.STEPS_COUNT_TOTAL).build();
                manager.aggregate(request,callback,new OutcomeReceiver<AggregateRecordsResponse<Long>,HealthConnectException>(){
                    public void onResult(AggregateRecordsResponse<Long> response){total.complete(response.get(StepsRecord.STEPS_COUNT_TOTAL));}
                    public void onError(HealthConnectException error){total.completeExceptionally(error);}
                });
            }catch(Exception error){total.completeExceptionally(error);}
            return total.orTimeout(30,TimeUnit.SECONDS).thenCombine(records(StepsRecord.class,from,to),
                    (steps,rows)->new DailyHealthRules.Movement(steps,freshThrough(rows,to),from,to));
        }
        /** Broad daily watch summaries do not establish recent movement freshness. */
        static Instant freshThrough(List<StepsRecord> rows,Instant now){
            return rows.stream().filter(r->!r.getEndTime().isAfter(now)&&!r.getStartTime().isAfter(r.getEndTime())
                    &&Duration.between(r.getStartTime(),r.getEndTime()).compareTo(Duration.ofMinutes(15))<=0)
                    .map(StepsRecord::getEndTime).max(Comparator.naturalOrder()).orElse(null);
        }
        public CompletableFuture<String> recovery(Instant now,ZoneId zone,boolean sleep,boolean restingHr){
            CompletableFuture<String> sleeping=sleep?records(SleepSessionRecord.class,now.minus(Duration.ofHours(36)),now).thenApply(rows->sleepContext(rows,now))
                    .exceptionally(error->"Sleep: unavailable or incomplete. You can enter your own estimate."):CompletableFuture.completedFuture("Sleep: access not enabled.");
            CompletableFuture<String> heart=restingHr?records(RestingHeartRateRecord.class,now.minus(Duration.ofDays(15)),now).thenApply(rows->restingContext(rows,now,zone))
                    .exceptionally(error->"Resting heart rate: unavailable or incomplete."):CompletableFuture.completedFuture("Resting heart rate: access not enabled.");
            return sleeping.thenCombine(heart,(a,b)->a+"\n\n"+b+"\n\nHealth Connect history can be delayed. Measurements do not establish readiness or alcohol consumption.");
        }
        static String sleepContext(List<SleepSessionRecord> rows,Instant now){
            var latest=rows.stream().filter(r->!r.getEndTime().isAfter(now)&&r.getEndTime().isAfter(now.minus(Duration.ofHours(24)))
                    &&Duration.between(r.getStartTime(),r.getEndTime()).toMinutes()>=120&&Duration.between(r.getStartTime(),r.getEndTime()).toMinutes()<=960)
                    .max(Comparator.comparing(SleepSessionRecord::getEndTime));
            if(latest.isEmpty())return "Sleep: no recent main sleep window available.";
            var row=latest.get();long minutes=Duration.between(row.getStartTime(),row.getEndTime()).toMinutes();
            return String.format(Locale.ROOT,"Latest sleep window: %dh %02dm · ended %s. This is the session window, not measured time asleep.",minutes/60,minutes%60,row.getEndTime());
        }
        static String restingContext(List<RestingHeartRateRecord> rows,Instant now,ZoneId zone){
            var newest=rows.stream().filter(r->!r.getTime().isAfter(now)&&r.getTime().isAfter(now.minus(Duration.ofHours(24))))
                    .max(Comparator.comparing(RestingHeartRateRecord::getTime));
            if(newest.isEmpty())return "Resting heart rate: no measurement within the last 24 hours.";
            var latest=newest.get();String origin=latest.getMetadata().getDataOrigin().getPackageName();
            LocalDate today=latest.getTime().atZone(zone).toLocalDate();Map<LocalDate,List<Long>> daily=new HashMap<>();
            for(var row:rows){LocalDate date=row.getTime().atZone(zone).toLocalDate();
                if(row.getMetadata().getDataOrigin().getPackageName().equals(origin)&&date.isBefore(today)&&!date.isBefore(today.minusDays(14)))
                    daily.computeIfAbsent(date,key->new ArrayList<>()).add(row.getBeatsPerMinute());
            }
            if(daily.size()<7)return "Latest resting heart rate: "+latest.getBeatsPerMinute()+" bpm. A same-source baseline needs at least seven prior days.";
            double baseline=median(daily.values().stream().map(Api34::median).toList());
            return String.format(Locale.ROOT,"Latest resting heart rate: %d bpm · prior same-source median %.0f bpm (%d days). Context, not a diagnosis.",latest.getBeatsPerMinute(),baseline,daily.size());
        }
        static double median(Collection<? extends Number> numbers){double[] values=numbers.stream().mapToDouble(Number::doubleValue).sorted().toArray();int n=values.length;return n%2==1?values[n/2]:(values[n/2-1]+values[n/2])/2;}
    }
}
