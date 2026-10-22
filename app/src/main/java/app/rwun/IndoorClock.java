package app.rwun;
/** Persisted monotonic timer. Reboot recovers only the last checkpoint, never wall-clock guesses. */
public record IndoorClock(long seconds,long resumedAt,int boot,boolean paused,boolean interrupted) {
  public IndoorClock {if(seconds<0||seconds>180000||resumedAt<0)throw new IllegalArgumentException("Invalid indoor timer");}
  public long value(long now,int currentBoot){return Math.min(180000,seconds+(!paused&&currentBoot==boot&&now>=resumedAt?(now-resumedAt)/1000:0));}
  public IndoorClock snapshot(long now,int currentBoot){boolean lost=currentBoot!=boot||now<resumedAt;long elapsed=value(now,currentBoot);long anchor=!lost&&!paused?resumedAt+(elapsed-seconds)*1000:now;return new IndoorClock(elapsed,anchor,currentBoot,paused||lost,interrupted||lost);}
  public IndoorClock toggle(long now,int currentBoot){IndoorClock saved=snapshot(now,currentBoot);return new IndoorClock(saved.seconds,now,currentBoot,!saved.paused,saved.interrupted);}
}
