import app.rwun.CoachGuide;
public final class CoachGuideTest {
  static int n;static void check(boolean yes){n++;if(!yes)throw new AssertionError("case "+n);}
  public static void main(String[]args){
    check(CoachGuide.briefing("Intervals",true,true).contains("on hold"));
    check(!CoachGuide.briefing("Intervals",true,true).contains("repeatable"));
    check(CoachGuide.briefing("Intervals",false,true).contains("repeatable"));
    check(CoachGuide.briefing("Tempo run",false,true).contains("controlled tempo"));
    check(CoachGuide.briefing("Long easy run",false,true).contains("shorten"));
    check(CoachGuide.briefing("Run / walk",false,false).contains("walking recoveries"));
    check(CoachGuide.briefing(null,false,false).contains("No benchmark"));
    check(CoachGuide.debrief(5,1800,2,1,true,true,false).contains("Pause running"));
    check(CoachGuide.debrief(5,1800,2,1,false,true,true).contains("hold remains"));
    check(CoachGuide.debrief(5,1800,9,5,false,false,false).contains("not reported"));
    check(!CoachGuide.debrief(5,1800,9,5,false,false,false).contains("high effort"));
    check(CoachGuide.debrief(5,1800,8,1,false,true,false).contains("Consider rest"));
    check(CoachGuide.debrief(5,1800,3,4,false,true,false).contains("Consider rest"));
    check(CoachGuide.debrief(5,1800,3,1,false,true,false).contains("does not establish"));
    check(CoachGuide.debrief(5,1801,3,1,false,true,false).contains("30:01 recorded time"));
    check(CoachGuide.find("Strength","").isEmpty());
    check(CoachGuide.find("All","  INTERVALS ").size()==1);
    check(CoachGuide.find("All","zzzzzz").isEmpty());
    check(CoachGuide.find("Recovery",null).size()>=3);
    check(CoachGuide.articles().stream().allMatch(a->a.source().isEmpty()||a.source().startsWith("https://www.nhs.uk/")||a.source().startsWith("https://www.cdc.gov/")));
    for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,251}){boolean rejected=false;try{CoachGuide.debrief(bad,1800,3,1,false,true,false);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
    for(int e:new int[]{0,11}){boolean rejected=false;try{CoachGuide.debrief(5,1800,e,1,false,true,false);}catch(IllegalArgumentException ex){rejected=true;}check(rejected);}
    System.out.println(n+" coaching assertions passed");
  }
}
