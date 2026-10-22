package app.rwun;
import java.util.*;
/** GPS kilometre splits from continuous samples only. Pause/gap boundaries invalidate crossing splits. */
public final class WorkoutReview {
 public record Split(int kilometre,double seconds){}
 public static List<Split> splits(List<BestEfforts.Fix> points){List<Split> out=new ArrayList<>();double distance=0,lapSeconds=0,next=1000;boolean valid=true;int index=1;BestEfforts.Fix prev=null;
  for(var p:points){if(prev==null){prev=p;continue;}double sec=(p.millis()-prev.millis())/1000.;double metres=distance(prev.lat(),prev.lon(),p.lat(),p.lon());boolean good=p.segment()==prev.segment()&&sec>0&&sec<=30&&metres/sec<=12;
   if(!good){valid=false;prev=p;continue;}double start=distance;distance+=metres;double remainingSec=sec;
   while(distance>=next&&metres>0){double portion=(next-start)/metres;double part=sec*portion;lapSeconds+=part;if(valid)out.add(new Split(index,lapSeconds));index++;next+=1000;start=next-1000;metres=distance-start;remainingSec-=part;sec=remainingSec;lapSeconds=0;valid=true;}
   lapSeconds+=remainingSec;prev=p;
  }return List.copyOf(out);
 }
 private static double distance(double a,double b,double c,double d){double x=Math.toRadians(c-a),y=Math.toRadians(d-b);double h=Math.pow(Math.sin(x/2),2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.pow(Math.sin(y/2),2);return 6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));}
 public static String consistency(List<Split> splits){if(splits.size()<2)return "At least two uninterrupted kilometre splits are needed to compare consistency.";double avg=splits.stream().mapToDouble(Split::seconds).average().orElse(0),min=splits.stream().mapToDouble(Split::seconds).min().orElse(0),max=splits.stream().mapToDouble(Split::seconds).max().orElse(0);return String.format(Locale.ROOT,"Split range %.0f seconds; average %.0f sec/km. Terrain, pauses and GPS quality affect interpretation; this is not a fitness score.",max-min,avg);}
}
