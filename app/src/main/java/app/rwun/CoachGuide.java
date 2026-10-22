package app.rwun;

import java.util.List;
import java.util.Locale;

/** Offline editorial content and transparent rules. No remote model or inferred medical assessment. */
public final class CoachGuide {
  private CoachGuide() {}
  public static final String PROVENANCE = "Generated OpenRun guidance · original editorial content and rules, informed by the sources linked below. Not reviewed by a qualified coach. No live coach or medical assessment.";
  public record Article(String title, String category, String aim, String practice, String reflection, String source) {
    public String body() { return "FOCUS\n"+aim+"\n\nTRY THIS\n"+practice+"\n\nREFLECT\n"+reflection; }
  }
  public static final String NHS="https://www.nhs.uk/better-health/get-active/get-running-with-couch-to-5k/couch-to-5k-running-plan/";
  public static final String CDC="https://www.cdc.gov/physical-activity-basics/measuring/index.html";
  public static final String PAIN="https://www.nhs.uk/live-well/exercise/knee-pain-and-other-running-injuries/";
  public static List<Article> articles() { return List.of(
    new Article("GPS and connection checks","Practical","Check the basics before recording.","Enable precise location and device GPS, start outdoors and check the recording status. Battery restrictions can interrupt recording. Bluetooth heart rate is separate from Garmin account sync.","Did you verify the signal before starting?",""),
    new Article("Your training data","Practical","Keep control of what you share.","Core coaching works offline. Health Connect and cloud access are optional and separate. Route exports contain location data. Keep your backup passphrase safe; it cannot be recovered by the server.","Which connections do you want enabled?",""),
    new Article("Find your easy effort","Running","Use breathing as a guide rather than chasing a number.","Check whether you can talk comfortably; slow down or walk when needed. The CDC talk test describes relative intensity, not your personal training zones.","Could you keep the effort comfortable without watching pace?",CDC),
    new Article("Begin and finish gently","Running","Make the transitions part of your session.","Use the workout's five-minute warm-up and cool-down. Start with walking and follow the prescribed steps without skipping ahead.","Did you give yourself time to settle?",NHS),
    new Article("Return with run / walk","Recovery","Build a repeatable routine after time away.","Choose Return to running in Training preferences. Keep walking recoveries and leave rest days between beginner runs. This mode does not assess injury recovery.","Would repeating this session feel manageable?",NHS),
    new Article("When running hurts","Recovery","Pain takes priority over the schedule.","Stop running if it hurts. Seek qualified advice for severe, swollen, persistent or concerning symptoms. OpenRun cannot diagnose an injury or clear you to return.","Record pain honestly; review the training hold before planning another run.",PAIN),
    new Article("Controlled intervals","Running","Practise consistency across the main set.","Read each work and recovery step before starting. Choose a repeatable effort and use the full recovery. If control is slipping, end the quality work rather than racing the last repeat.","Were the final repeats as controlled as the first?",""),
    new Article("Tempo with control","Running","Treat tempo as a focused practice, not a race.","Follow the displayed tempo section after warming up. Pace is an estimate; use an easier effort when conditions demand it. Do not add extra tempo because the first section felt good.","What helped you stay controlled?",""),
    new Article("Approach the long run","Running","Give yourself an uncomplicated session.","Choose a familiar route with options to shorten it. Review the main-set distance separately from warm-up and cool-down. Leave room to slow down and finish without a sprint.","Which route or timing choice made the run easier to manage?",""),
    new Article("Missed sessions","Planning","Resume without catch-up mileage.","Use Skip or Reschedule in the workout details. Check neighbouring sessions before moving a hard run. A missed workout is not a debt to repay.","What is the smallest practical change for next week?",""),
    new Article("Read your weekly review","Planning","Make decisions from a pattern of sessions.","Open the weekly review on Today. Review effort, fatigue and mileage together. Any proposed reduction needs your approval; one comfortable run is not proof that you should train harder.","Does your upcoming week fit how you feel now?",""),
    new Article("Race-week organisation","Planning","Remove practical surprises.","Check travel, kit and the event instructions. Keep the existing plan's shorter sessions instead of adding a last-minute fitness test. Prepare an alternative if weather or transport changes.","What can you prepare today?",""),
    new Article("Prepare for your run","Practical","Reduce decisions at the door.","Choose comfortable kit, a familiar route and access to water. Make yourself visible in low light. Use familiar food rather than experimenting just before a run.","What would make the next departure simpler?",NHS),
    new Article("Use audio well","Practical","Keep spoken guidance useful.","Test the installed voice before leaving. Spoken cues depend on your phone's TTS engine and audio setting. Keep environmental sounds audible; read the workout first in case cues are interrupted.","Could you hear the cues without losing awareness?",""),
    new Article("Trust effort before GPS","Practical","Interpret the measurement before reacting.","Live pace can jump around buildings and trees. Wait for stable readings and use effort rather than accelerating to correct a brief GPS error. Route best efforts are GPS estimates.","Was a pace change yours, the terrain's or the signal's?",""),
    new Article("Reflect without judgement","Recovery","Keep a useful record of the session.","Save effort, fatigue and pain feedback. Add context in your own journal if useful. Imported activity totals cannot tell OpenRun how you felt; a fast time alone does not prove readiness.","Name one thing to repeat and one thing to simplify.","")
  ); }
  public static List<Article> find(String category,String query) {
    String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);
    return articles().stream().filter(a->category==null||category.equals("All")||a.category().equals(category)).filter(a->(a.title()+" "+a.body()).toLowerCase(Locale.ROOT).contains(q)).collect(java.util.stream.Collectors.toList());
  }
  public static String briefing(String title, boolean held, boolean benchmark) {
    if(held)return "Training is on hold. Review your pain or recovery feedback before running. This briefing does not clear you to resume.";
    String kind=title==null?"":title.toLowerCase(Locale.ROOT);
    String focus=kind.contains("walk")?"Keep the walking recoveries. Aim for a manageable return, not continuous running at any cost.":kind.contains("interval")?"Aim for controlled, repeatable work sections. Use each recovery; this is not a sprint test.":kind.contains("tempo")?"Settle into a controlled tempo after the warm-up. Ease off if the effort becomes unmanageable.":kind.contains("long")?"Start comfortably and choose a route you can shorten. Keep the long run easy.":"Keep this run comfortable. Slow down or walk if you cannot talk comfortably.";
    return "BEFORE YOU GO\nReview the steps, route and audio setting. Begin with the prescribed warm-up.\n\nTODAY'S FOCUS\n"+focus+"\n\nPACE\n"+(benchmark?"Your benchmark provides estimates, not a requirement. Conditions and effort take priority.":"No benchmark is set. Follow effort; no personalised pace is inferred.")+"\n\nAFTER\nFollow the cool-down and record effort, fatigue and pain. Stop if running hurts.";
  }
  public static String debrief(double km,long seconds,int effort,int fatigue,boolean pain,boolean feedback,boolean held) {
    if(!Double.isFinite(km)||km<=0||seconds<=0||seconds>180000||km>250)throw new IllegalArgumentException("Invalid completed session");
    String summary=String.format(Locale.ROOT,"Recorded %.2f km · %d:%02d recorded time.\n\n",km,seconds/60,seconds%60);
    if(pain&&feedback)return summary+"Pain was reported. Pause running and review your training hold. Seek qualified advice for concerning or persistent symptoms; this app cannot clear you to return.";
    if(held)return summary+"Your training hold remains in place. This activity does not clear it. Review recovery before another run.";
    if(!feedback)return summary+"Effort and fatigue were not reported. Imported totals do not establish recovery or readiness. No training increase is recommended from this activity.";
    if(effort<1||effort>10||fatigue<1||fatigue>5)throw new IllegalArgumentException("Invalid feedback");
    if(fatigue>=4||effort>=8)return summary+"You reported high effort or fatigue. Consider rest or an easier next session, and review the weekly pattern before changing your plan. No change has been applied by this briefing.";
    return summary+"Feedback saved. Keep the next planned session under review and check how you feel before starting. One run does not establish a fitness improvement or justify increasing your load.";
  }
}
