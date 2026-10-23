package app.rwun;

import android.content.Context;
import androidx.sqlite.db.SimpleSQLiteQuery;
import java.time.*;import java.util.*;import java.util.concurrent.*;
import org.json.*;

/** The catalogue UI reads only Room. No network layer may write user-owned plan or saved-race fields. */
public final class RaceRepository {
 private static volatile RaceRepository instance;private final RaceDatabase db;private final RaceDatabase.CatalogueDao dao;
 private final CopyOnWriteArraySet<Runnable> listeners=new CopyOnWriteArraySet<>();
 public static final ExecutorService READS=Executors.newFixedThreadPool(2);
 public static RaceRepository get(Context c){if(instance==null)synchronized(RaceRepository.class){if(instance==null)instance=new RaceRepository(RaceDatabase.open(c));}return instance;}
 public RaceRepository(RaceDatabase database){db=database;dao=db.catalogue();}
 public RaceDatabase database(){return db;}
 public void observe(Runnable r){listeners.add(r);}public void removeObserver(Runnable r){listeners.remove(r);}public void notifyChanged(){for(Runnable r:listeners)r.run();}
 public record WriteCount(int inserted,int changed,int unchanged){}
 public WriteCount upsert(List<RaceData.Race> races,long now){return upsert(races,now,"");}
 public WriteCount upsert(List<RaceData.Race> races,long now,String pageUrl){int[] count={0,0,0};db.runInTransaction(()->{for(RaceData.Race race:races){
  String year=String.valueOf(LocalDate.parse(race.date()).getYear());RaceDatabase.Reference ref=dao.reference(race.provider(),race.externalId(),year);
  if(ref!=null&&ref.contentHash.equals(race.hash())){seen(race,ref.editionId,now,pageUrl);count[2]++;continue;}
  String edition=ref==null?match(race):ref.editionId;
  if(edition==null){RaceDatabase.Edition fresh=new RaceDatabase.Edition();fresh.id="race-"+UUID.randomUUID();fresh.name=race.name();fresh.nameKey=race.nameKey();fresh.date=race.date();fresh.endDate=race.endDate();fresh.country=race.country();dao.insertEdition(fresh);edition=fresh.id;count[0]++;}else count[1]++;
  RaceDatabase.Reference next=new RaceDatabase.Reference();next.provider=race.provider();next.externalId=race.externalId();next.editionYear=year;next.editionId=edition;next.sourceUrl=race.url();next.entryUrl=race.entryUrl();next.contentHash=race.hash();next.payload=race.json().toString();dao.reference(next);seen(race,edition,now,pageUrl);materialise(edition);
 }});if(count[0]+count[1]>0)notifyChanged();return new WriteCount(count[0],count[1],count[2]);}
 private void seen(RaceData.Race race,String edition,long now,String pageUrl){RaceDatabase.Seen s=new RaceDatabase.Seen();s.provider=race.provider();s.externalId=race.externalId();s.editionYear=String.valueOf(LocalDate.parse(race.date()).getYear());s.editionId=edition;s.revision=race.revision();s.requestUrl=pageUrl;s.lastSeen=now;dao.seen(s);}
 private String match(RaceData.Race race){if(race.nameKey().isBlank()||race.country().isBlank())return null;List<RaceDatabase.Edition> candidates=dao.candidates(race.nameKey(),race.date(),race.country());if(candidates.size()>=8)return null;String match=null;for(var c:candidates){
  boolean place=!race.city().isBlank()&&!c.city.isBlank()&&!RaceData.normal(race.city()).isBlank()&&RaceData.normal(race.city()).equals(RaceData.normal(c.city));
  boolean venue=!race.venue().isBlank()&&!c.venue.isBlank()&&!RaceData.normal(race.venue()).isBlank()&&RaceData.normal(race.venue()).equals(RaceData.normal(c.venue));
  boolean exactUrl=!race.url().isBlank()&&java.net.URI.create(race.url()).getPath()!=null&&java.net.URI.create(race.url()).getPath().length()>1&&dao.references(c.id).stream().anyMatch(r->r.sourceUrl.equals(race.url()));
  boolean compatible=c.surface.equals("Unknown")||race.surface().equals("Unknown")||c.surface.equals(race.surface());
  // Exact name/date/country plus a venue or event URL; city/distance alone is not enough.
  boolean citiesCompatible=race.city().isBlank()||c.city.isBlank()||place;
  if(compatible&&citiesCompatible&&(venue||exactUrl)){if(match!=null)return null;match=c.id;}
 }return match;}
 private void materialise(String id){List<RaceDatabase.Reference> refs=dao.references(id);List<RaceData.Race> sources=refs.stream().map(r->RaceData.fromJson(r.provider,r.externalId,"",r.payload)).collect(java.util.stream.Collectors.toList());RaceData.Race primary=sources.get(0);RaceDatabase.Edition row=dao.edition(id);String[] fields={"name","city","province","venue","time","timezone","fee","description"};Map<String,String> values=new HashMap<>();
  for(String key:fields)for(RaceData.Race source:sources){String value=source.json().optString(key);if(!value.isBlank()){values.put(key,value);break;}}
  row.name=values.getOrDefault("name",primary.name());row.nameKey=primary.nameKey();row.date=primary.date();row.endDate=primary.endDate();row.country=primary.country();row.city=values.getOrDefault("city","");row.province=values.getOrDefault("province","");row.venue=values.getOrDefault("venue","");row.time=values.getOrDefault("time","");row.timezone=values.getOrDefault("timezone","");row.fee=values.getOrDefault("fee","");row.description=values.getOrDefault("description","");row.surface=primary.surface();
  row.time="";row.timezone=primary.timezone();for(var source:sources)if(source.date().equals(primary.date())&&!source.time().isEmpty()){row.time=source.time();row.timezone=source.timezone();break;}
  String hash=RaceData.digest(row.name+"|"+row.date+"|"+row.endDate+"|"+row.city+"|"+row.province+"|"+row.country+"|"+row.venue+"|"+row.time+"|"+row.timezone+"|"+row.surface+"|"+row.fee+"|"+row.description);
  if(!row.contentHash.equals(hash)){row.contentHash=hash;row.contentVersion++;dao.updateEdition(row);}
  Map<String,RaceData.Distance> options=new LinkedHashMap<>();for(var source:sources)for(var d:source.distances())options.putIfAbsent(d.key(),d);
  Map<String,RaceDatabase.Option> existing=new HashMap<>();for(var o:dao.options(id))existing.put(o.optionKey,o);
  for(var d:options.values()){var o=existing.remove(d.key());if(o==null){o=new RaceDatabase.Option();o.id="distance-"+UUID.randomUUID();o.editionId=id;o.optionKey=d.key();o.metres=d.metres();o.label=d.label();o.surface=d.surface();dao.insertOption(o);}else if(!o.available||!o.label.equals(d.label())){o.available=true;o.label=d.label();dao.updateOption(o);}}
  for(var o:existing.values())if(o.available){o.available=false;dao.updateOption(o);} // Retain saved/goal-referenced distances.
 }
 public record Filter(String query,String location,String country,String surface,int metres,String from,String to,boolean savedOnly,int offset){}
 public List<RaceDatabase.Card> search(Filter f){String q="%"+f.query().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";String place="%"+f.location().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
  return dao.search(new SimpleSQLiteQuery("SELECT e.id,e.name,e.date,e.endDate,e.city,e.province,e.country,e.venue,e.time,e.timezone,e.surface,e.fee,COALESCE((SELECT GROUP_CONCAT(ROUND(metres/1000.0,2)||' km',' · ') FROM race_distances o WHERE o.editionId=e.id AND o.available=1),'') AS distances,COALESCE((SELECT MAX(lastSeen) FROM race_seen v WHERE v.editionId=e.id),0) AS lastSeen,EXISTS(SELECT 1 FROM saved_races s WHERE s.editionId=e.id) AS saved FROM race_editions e WHERE e.endDate>=? AND e.date<=? AND (?='' OR e.country=?) AND (?='All' OR e.surface=? OR EXISTS(SELECT 1 FROM race_distances o WHERE o.editionId=e.id AND o.available=1 AND o.surface=?)) AND (e.name LIKE ? ESCAPE '\\' OR e.city LIKE ? ESCAPE '\\' OR e.venue LIKE ? ESCAPE '\\') AND (e.city LIKE ? ESCAPE '\\' OR e.province LIKE ? ESCAPE '\\' OR e.venue LIKE ? ESCAPE '\\') AND (?=0 OR EXISTS(SELECT 1 FROM race_distances o WHERE o.editionId=e.id AND o.available=1 AND ABS(o.metres-?)<=110)) AND (?=0 OR EXISTS(SELECT 1 FROM saved_races s WHERE s.editionId=e.id)) ORDER BY e.date,e.name,e.id LIMIT 31 OFFSET ?",new Object[]{f.from(),f.to(),f.country(),f.country(),f.surface(),f.surface(),f.surface(),q,q,q,place,place,place,f.metres(),f.metres(),f.savedOnly()?1:0,f.offset()}));}
 public List<RaceDatabase.Option> options(String id){return dao.options(id);}public List<RaceDatabase.Reference> references(String id){return dao.references(id);}public RaceDatabase.Edition edition(String id){return dao.edition(id);}public List<String> countries(){return dao.countries();}
 public void save(String edition,String option){db.runInTransaction(()->{if(dao.edition(edition)==null)throw new IllegalArgumentException("Race missing");if(option!=null){var d=dao.option(option);if(d==null||!d.editionId.equals(edition))throw new IllegalArgumentException("Distance does not belong to race");}var saved=dao.saved(edition);if(saved==null){saved=new RaceDatabase.Saved();saved.editionId=edition;saved.savedAt=System.currentTimeMillis();}if(option!=null)saved.distanceId=option;dao.save(saved);});notifyChanged();}
 public void unsave(String id){dao.unsave(id);notifyChanged();}
 public boolean isSaved(String id){return dao.saved(id)!=null;}
 public JSONObject planningRace(String id,String option){try{var e=dao.edition(id);var d=dao.option(option);if(e!=null&&!e.date.equals(e.endDate))throw new IllegalArgumentException("The source lists a multi-day event. Confirm the actual race day and add it manually before planning.");if(e==null||d==null||!d.editionId.equals(id)||!d.available)throw new IllegalArgumentException("Review this race distance; it is no longer listed by a source.");String source=dao.references(id).stream().map(r->r.sourceUrl).filter(s->!s.isEmpty()).findFirst().orElse("");JSONObject j=new JSONObject().put("id",d.id).put("editionId",e.id).put("distanceId",d.id).put("name",e.name).put("city",e.city).put("country",RaceData.countryName(e.country)).put("date",e.date).put("km",d.metres/1000d).put("surface",d.surface).put("source",source).put("venue",e.venue);if(e.city.isEmpty())j.remove("city");if(e.country.isEmpty())j.remove("country");if(source.isEmpty())j.remove("source");long seen=dao.lastSeen(e.id);if(seen>0)j.put("verified",Instant.ofEpochMilli(seen).atZone(ZoneOffset.UTC).toLocalDate().toString());return j;}catch(Exception e){throw new IllegalArgumentException(e);}}
 public boolean acquire(String owner,long now){RaceDatabase.SyncState s=new RaceDatabase.SyncState();s.provider="_lease";dao.insertState(s);return dao.acquire(owner,now,now+12*60000)==1;}
 public void release(String owner){dao.release(owner);notifyChanged();}
 public void seed(Context c){if(dao.sync("_seed")!=null)return;db.runInTransaction(()->{if(dao.sync("_seed")!=null)return;try{JSONArray rows;String legacyWarning="";android.util.AtomicFile old=new android.util.AtomicFile(new java.io.File(c.getFilesDir(),"race-catalogue.json"));try(var in=old.openRead()){rows=new JSONObject(RaceHttp.read(in,4*1024*1024)).getJSONArray("races");RaceCatalogue.validate(rows);}catch(Exception missing){legacyWarning=missing instanceof java.io.FileNotFoundException?"":"Previous catalogue unreadable; retained the file and imported the starter list.";try(var in=c.getResources().openRawResource(R.raw.races)){rows=new JSONObject(RaceHttp.read(in,4*1024*1024)).getJSONArray("races");}}
   RaceCatalogue.validate(rows);for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);upsert(List.of(new RaceData.Race("starter",r.getString("id"),r.getString("date"),r.getString("name"),r.getString("city"),"",r.getString("country"),"","","",r.getString("surface"),"","",r.getString("source"),"","",List.of(new RaceData.Distance((int)Math.round(r.getDouble("km")*1000),r.getDouble("km")+" km",r.getString("surface"))))),LocalDate.parse(r.getString("verified")).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());}
   RaceDatabase.SyncState flag=new RaceDatabase.SyncState();flag.provider="_seed";flag.error=legacyWarning;dao.state(flag);
  }catch(Exception e){throw new IllegalStateException("Could not import the previous race catalogue. It has been retained.",e);}});}
}
