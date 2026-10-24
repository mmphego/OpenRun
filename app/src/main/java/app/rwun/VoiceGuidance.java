package app.rwun;
/** Session-local, bounded effort reminders. Observations never mutate a plan. */
public final class VoiceGuidance {
 public enum Mode{QUIET,STANDARD,DETAILED}
 public record Settings(Mode mode,int checkSeconds,boolean splits,int heartLimit){
  public Settings{if(mode==null||checkSeconds<180||checkSeconds>900||heartLimit!=0&&(heartLimit<100||heartLimit>220))throw new IllegalArgumentException("Choose 3–15 minute checks and an optional 100–220 bpm alert threshold");}
  public static Settings defaults(){return new Settings(Mode.STANDARD,300,true,0);}
 }
 public record Advice(String text,boolean heartRate){}
 private long lastCheck,highSince=-1,lastHeart=-120,lastUpdate=-1;private boolean nearFinish;
 public String update(long seconds,int bpm,long age,boolean easy,boolean complete,Settings s){return evaluate(seconds,bpm,age,easy,complete,s).text();}
 public Advice evaluate(long seconds,int bpm,long age,boolean easy,boolean complete,Settings s){
  if(lastUpdate>=0&&seconds-lastUpdate>5)highSince=-1;lastUpdate=seconds;
  if(complete||!easy){highSince=-1;return new Advice("",false);}
  if(s.heartLimit()>0&&bpm>=s.heartLimit()&&age>=0&&age<=10000){if(highSince<0)highSince=seconds;if(seconds-highSince>=30&&seconds-lastHeart>=120){lastHeart=seconds;highSince=-1;return new Advice("Your heart rate has stayed above your chosen alert threshold. Check how you feel. Ease off or walk if needed. This is not a medical assessment.",true);}}else highSince=-1;
  if(s.mode()!=Mode.QUIET&&seconds-lastCheck>=s.checkSeconds()){lastCheck=seconds;return new Advice(s.mode()==Mode.DETAILED?"Effort check. Can you speak a comfortable sentence? Ease off or walk if needed. Relax your shoulders and keep the effort repeatable.":"Effort check. Can you speak comfortably? Slow down or walk if needed.",false);}
  return new Advice("",false);
 }
 public String nearingFinish(double remaining,boolean finalMainStep,Settings s){if(!nearFinish&&finalMainStep&&remaining>0&&remaining<=300&&s.mode()!=Mode.QUIET){nearFinish=true;return "Final section. Keep the same comfortable effort. There is no need to sprint.";}return "";}
 public void resetAfterPause(long seconds){highSince=-1;lastCheck=seconds;lastUpdate=seconds;}
}
