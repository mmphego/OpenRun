package app.rwun;

import java.util.*;

/** Continuous GPS best efforts, with interpolation at actual distance boundaries. Never extrapolates average pace. */
public final class BestEfforts {
  public record Fix(double lat,double lon,long millis,int segment){}
  public static final double[] DISTANCES={1000,1609.344,3218.688,5000,10000,21097.5,42195};
  public static Map<Double,Double> calculate(List<Fix> fixes){
    Map<Double,Double> best=new TreeMap<>();List<Double> d=new ArrayList<>(),t=new ArrayList<>();Fix previous=null;
    for(Fix point:fixes){
      double gap=previous==null?0:distance(previous,point),seconds=previous==null?0:(point.millis-previous.millis)/1000.;
      boolean valid=Double.isFinite(point.lat)&&Double.isFinite(point.lon)&&Math.abs(point.lat)<=90&&Math.abs(point.lon)<=180;
      if(!valid||previous==null||point.segment!=previous.segment||seconds<=0||seconds>30||gap/Math.max(.001,seconds)>12){scan(d,t,best);d.clear();t.clear();if(valid){d.add(0.);t.add(point.millis/1000.);}previous=valid?point:null;continue;}
      d.add(d.get(d.size()-1)+gap);t.add(point.millis/1000.);previous=point;
    }
    scan(d,t,best);return best;
  }
  private static void scan(List<Double> d,List<Double> t,Map<Double,Double> best){
    if(d.size()<2)return;
    for(double target:DISTANCES){
      int left=0,right=1;
      for(int end=1;end<d.size();end++){
        double start=d.get(end)-target;if(start<0)continue;
        while(left+1<end&&d.get(left+1)<=start)left++;
        if(d.get(left+1)>d.get(left)){double at=t.get(left)+(start-d.get(left))/(d.get(left+1)-d.get(left))*(t.get(left+1)-t.get(left));put(best,target,t.get(end)-at);}
      }
      for(int start=0;start<d.size()-1;start++){
        right=Math.max(right,start+1);double finish=d.get(start)+target;while(right<d.size()&&d.get(right)<finish)right++;
        if(right>=d.size())break;
        double span=d.get(right)-d.get(right-1);if(span>0){double at=t.get(right-1)+(finish-d.get(right-1))/span*(t.get(right)-t.get(right-1));put(best,target,at-t.get(start));}
      }
    }
  }
  private static void put(Map<Double,Double> out,double target,double seconds){if(seconds>0&&Double.isFinite(seconds))out.merge(target,seconds,Math::min);}
  private static double distance(Fix a,Fix b){double lat=Math.toRadians(b.lat-a.lat),lon=Math.toRadians(b.lon-a.lon),h=Math.sin(lat/2)*Math.sin(lat/2)+Math.cos(Math.toRadians(a.lat))*Math.cos(Math.toRadians(b.lat))*Math.sin(lon/2)*Math.sin(lon/2);return 6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));}
}
