package app.rwun;

import android.content.Context;
import android.health.connect.*;
import android.health.connect.datatypes.*;
import android.os.OutcomeReceiver;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Foreground, selected aggregate readings. Values stay in the screen, never become workouts. */
final class HealthSummary {
    static CompletableFuture<String> read(Context c){
        if(!HealthAccess.available(c)||!HealthPreferences.enabled(c,false))return HealthSync.failed(new SecurityException("Enable reading first."));
        return new Api34(c).read();
    }
    @android.annotation.TargetApi(34)
    private static final class Api34 {
        final Context context;final HealthConnectManager manager;
        final Instant now=Instant.now(),start=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Api34(Context c){context=c;manager=c.getSystemService(HealthConnectManager.class);}
        <T> CompletableFuture<T> aggregate(AggregationType<T> type){
            CompletableFuture<T> result=new CompletableFuture<>();
            try{manager.aggregate(new AggregateRecordsRequest.Builder<T>(DailyHealthSource.Api34.range(start,now)).addAggregationType(type).build(),context.getMainExecutor(),
                new OutcomeReceiver<AggregateRecordsResponse<T>,HealthConnectException>(){
                    public void onResult(AggregateRecordsResponse<T> response){result.complete(response.get(type));}
                    public void onError(HealthConnectException error){result.completeExceptionally(error);}
                });}catch(Exception error){result.completeExceptionally(error);}
            return result.orTimeout(30,TimeUnit.SECONDS);
        }
        boolean allowed(int index){return HealthPreferences.selected(context,index)&&HealthAccess.granted(context,HealthPreferences.READ_PERMISSIONS[index]);}
        CompletableFuture<String> read(){
            List<CompletableFuture<String>> readings=new ArrayList<>();
            if(allowed(2))readings.add(aggregate(StepsRecord.STEPS_COUNT_TOTAL).thenApply(v->"Steps today: "+(v==null?"unavailable":v)).exceptionally(e->"Steps: unavailable."));
            if(allowed(3))readings.add(aggregate(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL).thenApply(v->"Active calories today: "+(v==null?"unavailable":String.format(Locale.ROOT,"%.0f kcal",v.getInKilocalories()))).exceptionally(e->"Active calories: unavailable."));
            if(allowed(1))readings.add(aggregate(HeartRateRecord.BPM_AVG).thenApply(v->"Average heart rate today: "+(v==null?"unavailable":v+" bpm")).exceptionally(e->"Heart rate: unavailable."));
            if(allowed(4)||allowed(5))readings.add(DailyHealthSource.create(context).recovery(now,ZoneId.systemDefault(),allowed(4),allowed(5)));
            if(readings.isEmpty())return CompletableFuture.completedFuture("");
            return CompletableFuture.allOf(readings.toArray(new CompletableFuture<?>[0])).thenApply(v->String.join("\n\n",readings.stream().map(CompletableFuture::join).toList()));
        }
    }
}
