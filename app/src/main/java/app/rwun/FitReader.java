package app.rwun;
import java.util.*;import java.time.*;import java.security.*;
/** Original, bounded FIT activity decoder. Standard running sessions; no developer-field interpretation. */
final class FitReader {
  private record Field(int number,int size,int type){}private record Definition(int global,boolean big,List<Field> fields,int developerBytes){}
  private static long unsigned(byte[] data,int at,int size,boolean big){long out=0;for(int i=0;i<size;i++)out|=(data[at+i]&255L)<<(8*(big?size-1-i:i));return out;}
  static int crc(byte[] bytes,int length){int crc=0;for(int i=0;i<length;i++){crc^=bytes[i]&255;for(int bit=0;bit<8;bit++)crc=(crc&1)!=0?(crc>>>1)^0xA001:crc>>>1;}return crc;}
  static ActivityFiles.Imported parse(byte[] data)throws Exception{return parse(data,false);}
  static ActivityFiles.Imported parse(byte[] data,boolean cycling)throws Exception{
    if(data.length<14||data.length>10*1024*1024)throw new IllegalArgumentException("Invalid FIT file size");int header=data[0]&255;if((header!=12&&header!=14)||((data[1]&255)>>4)>2||header>data.length-2)throw new IllegalArgumentException("Invalid FIT header");
    if(data[8]!='.'||data[9]!='F'||data[10]!='I'||data[11]!='T')throw new IllegalArgumentException("Not a FIT file");long size=unsigned(data,4,4,false);if(size+header+2!=data.length)throw new IllegalArgumentException("Truncated or chained FIT files are unsupported");
    if(header>=14&&unsigned(data,12,2,false)!=0&&crc(data,header)!=0)throw new IllegalArgumentException("FIT header checksum failed");if(crc(data,data.length-2)!=unsigned(data,data.length-2,2,false))throw new IllegalArgumentException("FIT checksum failed");
    Definition[] definitions=new Definition[16];int pos=header,end=header+(int)size,segment=0,sessions=0,hrTotal=0,hrCount=0;long timestamp=-1,first=-1,last=-1;double metres=0,elapsed=0;boolean activity=false,running=false;List<ActivityFiles.Sample> points=new ArrayList<>();
    while(pos<end){int h=data[pos++]&255;boolean compressed=(h&128)!=0;int local=compressed?(h>>5)&3:h&15;
      if(!compressed&&(h&64)!=0){if(pos+5>end)throw new IllegalArgumentException("Truncated FIT definition");pos++;boolean big=data[pos++]!=0;int global=(int)unsigned(data,pos,2,big);pos+=2;int count=data[pos++]&255;List<Field> fields=new ArrayList<>();if(pos+3*count>end)throw new IllegalArgumentException("Truncated FIT fields");for(int i=0;i<count;i++){fields.add(new Field(data[pos]&255,data[pos+1]&255,data[pos+2]&255));pos+=3;}int developer=0;if((h&32)!=0){if(pos>=end)throw new IllegalArgumentException("Truncated developer definition");int n=data[pos++]&255;if(pos+3*n>end)throw new IllegalArgumentException("Truncated developer fields");for(int i=0;i<n;i++){developer+=data[pos+1]&255;pos+=3;}}definitions[local]=new Definition(global,big,fields,developer);continue;}
      Definition def=definitions[local];if(def==null)throw new IllegalArgumentException("FIT data has no definition");Map<Integer,Long> values=new HashMap<>();
      if(compressed){if(timestamp<0)throw new IllegalArgumentException("Compressed FIT timestamp has no reference");long next=(timestamp&~31L)|(h&31);if(next<timestamp)next+=32;timestamp=next;values.put(253,timestamp);}
      for(Field field:def.fields){if(compressed&&field.number==253&&field.size==4)continue;if(pos+field.size>end)throw new IllegalArgumentException("Truncated FIT record");if(field.size>0&&field.size<=4){long value=unsigned(data,pos,field.size,def.big);long invalid=(1L<<(field.size*8))-1;if((field.type&31)==5)invalid=0x7fffffffL;if(value!=invalid)values.put(field.number,value);}pos+=field.size;}
      if(pos+def.developerBytes>end)throw new IllegalArgumentException("Truncated FIT developer payload");pos+=def.developerBytes;if(values.containsKey(253))timestamp=values.get(253);
      if(def.global==0)activity=values.getOrDefault(0,-1L)==4;
      if(def.global==12&&values.getOrDefault(0,-1L)==1)running=true;
      if(def.global==21&&values.getOrDefault(0,-1L)==0&&values.getOrDefault(1,-1L)!=0)segment++;
      if(def.global==18){if(++sessions>1)throw new IllegalArgumentException("Import one FIT running session at a time");running=values.getOrDefault(5,-1L)==(cycling?2:1);if(values.containsKey(2))first=values.get(2);metres=values.getOrDefault(9,0L)/100.;elapsed=values.getOrDefault(7,0L)/1000.;}
      if(def.global==20&&timestamp>=0){if(first<0)first=timestamp;last=Math.max(last,timestamp);int hr=(int)(long)values.getOrDefault(3,0L);if(hr>0&&hr<250){hrTotal+=hr;hrCount++;}if(values.containsKey(0)&&values.containsKey(1)){double lat=(int)(long)values.get(0)*(180./2147483648.),lon=(int)(long)values.get(1)*(180./2147483648.);if(Math.abs(lat)<=90&&Math.abs(lon)<=180)points.add(new ActivityFiles.Sample(lat,lon,(timestamp+631065600L)*1000,hr,segment));}if(points.size()>150000)throw new IllegalArgumentException("Too many FIT route points");if(sessions==0&&values.containsKey(5))metres=Math.max(metres,values.get(5)/100.);}
    }
    long seconds=Math.round(elapsed>0?elapsed:last-first);if(!activity||!running||first<0||metres<=0||metres>(cycling?1000000:250000)||seconds<1||seconds>180000)throw new IllegalArgumentException("Choose a single matching running/cycling FIT activity with distance and duration");
    StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))hash.append(String.format(Locale.US,"%02x",b&255));return new ActivityFiles.Imported(hash.toString(),Instant.ofEpochSecond(first+631065600L).atZone(ZoneId.systemDefault()).toLocalDate().toString(),metres/1000,seconds,hrCount==0?0:hrTotal/hrCount,List.copyOf(points));
  }
}
