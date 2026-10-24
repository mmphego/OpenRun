package app.rwun;
import java.util.concurrent.*;
/** Bound stalled platform calls without treating a missing acknowledgement as confirmed success. */
final class HealthTransfer {
 private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"health-transfer-timeout");t.setDaemon(true);return t;});
 static void bound(CompletableFuture<?> future){ScheduledFuture<?> timeout=TIMER.schedule(()->future.completeExceptionally(new IllegalStateException("Health Connect did not confirm completion. Check access and retry; stable IDs prevent duplicate exports.")),120,TimeUnit.SECONDS);future.whenComplete((v,e)->timeout.cancel(false));}
}
