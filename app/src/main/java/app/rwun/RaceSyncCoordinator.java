package app.rwun;
import java.time.*;import java.time.format.DateTimeFormatter;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;

/** Single-flight across Activity/job calls, with an expiring Room lease across processes/restarts. */
public final class RaceSyncCoordinator {
 private static final AtomicBoolean RUNNING=new AtomicBoolean();
 private final RaceRepository repository;private final RaceHttp http;private final List<RaceProvider> providers;
 public RaceSyncCoordinator(RaceRepository repository,RaceHttp http,List<RaceProvider> providers){this.repository=repository;this.http=http;this.providers=List.copyOf(providers);}
 public record Result(boolean alreadyRunning,boolean retry,int succeeded,int failed){}
 public void cancel(){http.cancel();}
 public Result sync()throws Exception {
  if(!RUNNING.compareAndSet(false,true))return new Result(true,false,0,0);
  String owner=UUID.randomUUID().toString();ExecutorService pool=Executors.newFixedThreadPool(2);List<Future<Boolean>> work=new ArrayList<>();boolean lease=false;
  try {lease=repository.acquire(owner,System.currentTimeMillis());if(!lease)return new Result(true,false,0,0);
   for(RaceProvider provider:providers)work.add(pool.submit(()->syncProvider(provider)));
   int ok=0,bad=0;long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(6);
   for(Future<Boolean> f:work)try{Boolean success=f.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);if(success!=null){if(success)ok++;else bad++;}}catch(ExecutionException e){bad++;}catch(TimeoutException e){cancel();f.cancel(true);bad++;}
   return new Result(false,bad>0,ok,bad);
  }finally{for(Future<?> f:work)if(!f.isDone())f.cancel(true);pool.shutdownNow();boolean ended=false;try{ended=pool.awaitTermination(20,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}if(lease&&ended)repository.release(owner);RUNNING.set(false);}
 }
 private Boolean syncProvider(RaceProvider provider){var dao=repository.database().catalogue();RaceDatabase.SyncState state=dao.sync(provider.id());if(state==null){state=new RaceDatabase.SyncState();state.provider=provider.id();}state.attempted=System.currentTimeMillis();state.error="";state.pages=0;state.skipped=0;state.truncated=false;
  if(!provider.blockedReason().isEmpty()){state.error=provider.blockedReason();dao.state(state);repository.notifyChanged();return null;}
  dao.state(state);repository.notifyChanged();String since=provider.incremental()?state.incrementalSince:"";
  // Periodic complete reconciliation; modified_since is used only by the documented RunSignup adapter.
  if(state.lastFull==0||state.attempted-state.lastFull>7*86400000L)since="";
  Set<String> pageHashes=new HashSet<>();long started=state.attempted;
  try {for(int page=1;page<=20;page++){
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();String url=provider.endpoint(page,since);var previous=dao.page(provider.id(),url);var response=http.get(url,previous);boolean more;int pageCount;
   if(response.status()==304){if(previous==null)throw new IllegalStateException("304 without a cached page");more=previous.more;pageCount=previous.pageCount;dao.confirmPage(provider.id(),url,System.currentTimeMillis());}
   else {var parsed=provider.parse(response.body(),LocalDate.now());more=parsed.more();pageCount=parsed.pageCount();state.skipped+=parsed.skipped();String hash=RaceData.digest(response.body());if(!pageHashes.add(hash)&&more)throw new IllegalStateException("Provider repeated a page; pagination stopped safely");
    RaceDatabase.HttpPage cached=new RaceDatabase.HttpPage();cached.provider=provider.id();cached.requestUrl=url;cached.etag=response.etag();cached.modified=response.modified();cached.more=more;cached.pageCount=pageCount;cached.checked=System.currentTimeMillis();
    repository.database().runInTransaction(()->{repository.upsert(parsed.races(),cached.checked,url);dao.page(cached);});
   }
   state.pages=page;
   if(!more||pageCount>0&&page>=pageCount){state.success=System.currentTimeMillis();state.error=state.skipped>0?state.skipped+" malformed records skipped; other listings updated.":"";if(state.skipped==0){state.incrementalSince=Instant.ofEpochMilli(started).atZone(ZoneId.of("America/New_York")).toLocalDate().minusDays(2).format(DateTimeFormatter.ofPattern("M/d/uuuu"));if(since.isEmpty())state.lastFull=state.success;}dao.state(state);repository.notifyChanged();return true;}
  }state.truncated=true;throw new IllegalStateException("Provider exceeded 20 pages; catalogue is partial. Existing races were retained.");
  }catch(Exception e){state.error=Thread.currentThread().isInterrupted()?"Sync interrupted; cached races retained.":e.getMessage()==null?"Provider could not update; cached races retained.":e.getMessage();dao.state(state);repository.notifyChanged();return false;}
 }
}
