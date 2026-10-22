package app.rwun;
import java.io.*;import java.nio.charset.StandardCharsets;
public final class FitReaderHostTest {
  static int assertions;static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
  static void u32(ByteArrayOutputStream out,long value){for(int i=0;i<4;i++)out.write((int)(value>>(8*i))&255);}
  static byte[] fixture(boolean running)throws Exception{
    ByteArrayOutputStream data=new ByteArrayOutputStream();data.write(new byte[]{0x40,0,0,0,0,1,0,1,0,0,4});
    data.write(new byte[]{0x41,0,0,20,0,4,(byte)253,4,(byte)134,0,4,(byte)133,1,4,(byte)133,3,1,2});
    for(int i=0;i<4;i++){data.write(1);u32(data,1000000000L+i*20);u32(data,0);u32(data,i*10000);data.write(140+i);}
    data.write(new byte[]{0x42,0,0,18,0,4,2,4,(byte)134,5,1,0,7,4,(byte)134,9,4,(byte)134});data.write(2);u32(data,1000000000);data.write(running?1:2);u32(data,100000);u32(data,100000);
    ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(new byte[]{14,0x20,0,0});u32(out,data.size());out.write(".FIT".getBytes(StandardCharsets.US_ASCII));out.write(new byte[]{0,0});out.write(data.toByteArray());int crc=FitReader.crc(out.toByteArray(),out.size());out.write(crc&255);out.write(crc>>8);return out.toByteArray();
  }
  public static void main(String[] args)throws Exception{
    byte[] known="123456789".getBytes(StandardCharsets.US_ASCII);check(FitReader.crc(known,known.length)==0xBB3D,"standard CRC16 check value");
    byte[] bytes=fixture(true);var result=ActivityFiles.parse(bytes);check(result.km()==1&&result.seconds()==100,"FIT session totals");check(result.points().size()==4&&result.avgHr()==141,"FIT points and HR");check(result.id().equals(ActivityFiles.parse(bytes).id()),"stable import identity");
    bytes[bytes.length-1]^=1;boolean bad=false;try{ActivityFiles.parse(bytes);}catch(IllegalArgumentException expected){bad=true;}check(bad,"bad FIT checksum rejected");
    boolean sport=false;try{ActivityFiles.parse(fixture(false));}catch(IllegalArgumentException expected){sport=true;}check(sport,"non-running FIT rejected");
    boolean truncated=false;try{ActivityFiles.parse(java.util.Arrays.copyOf(fixture(true),20));}catch(IllegalArgumentException expected){truncated=true;}check(truncated,"truncated FIT rejected");
    var ride=ActivityFiles.parse(fixture(false),true);check(ride.km()==1&&ride.seconds()==100&&ride.points().size()==4,"cycling FIT import");boolean mismatch=false;try{ActivityFiles.parse(fixture(true),true);}catch(IllegalArgumentException expected){mismatch=true;}check(mismatch,"running FIT rejected in cycling mode");
    System.out.println("FIT host tests passed: "+assertions+" assertions.");
  }
}
