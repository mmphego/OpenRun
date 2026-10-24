package app.rwun;
import java.util.*;
/** Bounded mailbox; stale phase and pace advice cannot survive interruptions. */
public final class SpeechQueue {
 public enum Kind{PHASE,SYSTEM,HEART,PACE,SPLIT,EFFORT,BRIEFING}
 public record Cue(String key,String text,Kind kind,int step,long created,long expires){
  public Cue{if(key==null||text==null||text.isBlank()||kind==null||expires<created)throw new IllegalArgumentException("Invalid cue");}
  public int priority(){return switch(kind){case SYSTEM->6;case PHASE->5;case HEART->4;case BRIEFING->3;case PACE->2;case SPLIT->1;case EFFORT->0;};}
 }
 private final List<Cue> pending=new ArrayList<>();
 public void offer(Cue c,long now,int step){prune(now,step);pending.removeIf(old->old.key().equals(c.key())||c.kind()==Kind.PHASE&&old.step()!=c.step());pending.add(c);pending.sort(Comparator.comparingInt(Cue::priority).reversed().thenComparingLong(Cue::created));while(pending.size()>4)pending.remove(pending.size()-1);}
 public Cue poll(long now,int step){prune(now,step);return pending.isEmpty()?null:pending.remove(0);}
 private void prune(long now,int step){pending.removeIf(c->now>c.expires()||c.step()>=0&&c.step()!=step);}
 public void clear(){pending.clear();}public int size(){return pending.size();}
}
