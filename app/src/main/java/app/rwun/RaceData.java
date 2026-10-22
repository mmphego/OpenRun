package app.rwun;

import android.text.Html;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Provider-neutral facts. Empty strings mean unknown; no guessed geographic or entry data. */
public final class RaceData {
 public record Distance(int metres,String label,String surface) {
  public Distance { if(metres<100||metres>1000000)throw new IllegalArgumentException("Invalid race distance");label=text(label,180);surface=terrain(surface); }
  public String key(){return metres+":"+surface;}
 }
 public record Race(String provider,String externalId,String date,String name,String city,String province,String country,
                    String venue,String time,String timezone,String surface,String fee,String description,String url,String entryUrl,
                    String revision,List<Distance> distances) {
  public Race {
   if(externalId==null||externalId.isBlank()||externalId.length()>200)throw new IllegalArgumentException("Missing provider identity");
   LocalDate.parse(date);name=text(name,250);if(name.isBlank())throw new IllegalArgumentException("Missing race name");
   city=text(city,180);province=text(province,180);country=countryCode(country);venue=text(venue,300);time=time==null?"":time;
   timezone=timezone==null?"":timezone;surface=terrain(surface);fee=text(fee,180);description=text(description,1800);
   url=RaceData.url(url);entryUrl=RaceData.url(entryUrl);revision=revision==null?"":revision;distances=List.copyOf(distances);
  }
  public String editionKey(){return externalId+":"+LocalDate.parse(date).getYear();}
  public String nameKey(){return normal(name.replaceAll("\\b(?:19|20)\\d{2}\\b", " "));}
  public JSONObject json() {
   try {JSONObject j=new JSONObject();j.put("name",name).put("date",date).put("city",city).put("province",province).put("country",country).put("venue",venue)
    .put("time",time).put("timezone",timezone).put("surface",surface).put("fee",fee).put("description",description).put("url",url).put("entryUrl",entryUrl);
    JSONArray a=new JSONArray();distances.stream().sorted(Comparator.comparing(Distance::key)).forEach(d->{try{a.put(new JSONObject().put("metres",d.metres).put("label",d.label).put("surface",d.surface));}catch(Exception e){throw new IllegalArgumentException(e);}});return j.put("distances",a);
   }catch(Exception e){throw new IllegalArgumentException(e);}
  }
  public String hash(){return digest(json().toString());}
 }
 public static Race fromJson(String provider,String external,String revision,String json) {
  try {JSONObject j=new JSONObject(json);List<Distance> options=new ArrayList<>();JSONArray a=j.optJSONArray("distances");if(a!=null)for(int i=0;i<a.length();i++){JSONObject d=a.getJSONObject(i);options.add(new Distance(d.getInt("metres"),d.optString("label"),d.optString("surface")));}
   return new Race(provider,external,j.getString("date"),j.getString("name"),j.optString("city"),j.optString("province"),j.optString("country"),j.optString("venue"),j.optString("time"),j.optString("timezone"),j.optString("surface"),j.optString("fee"),j.optString("description"),j.optString("url"),j.optString("entryUrl"),revision,options);
  }catch(Exception e){throw new IllegalArgumentException(e);}
 }
 public static String text(String raw,int max){if(raw==null)return "";String clean=raw.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>"," ");clean=Html.fromHtml(clean,Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("[\\p{Cntrl}\\s\\u00a0]+"," ").trim();return clean.substring(0,Math.min(max,clean.length()));}
 public static String normal(String value){return Normalizer.normalize(text(value,1000),Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll(" +"," ");}
 public static String terrain(String raw){String s=raw==null?"":raw.toLowerCase(Locale.ROOT);if(s.contains("trail")||s.contains("cross country"))return "Trail";if(s.contains("road"))return "Road";return "Unknown";}
 public static String countryCode(String value){String v=text(value,100);if(v.length()==2)return v.toUpperCase(Locale.ROOT);for(String c:Locale.getISOCountries())if(new Locale("",c).getDisplayCountry(Locale.ENGLISH).equalsIgnoreCase(v))return c;return "";}
 public static String countryName(String code){return code.length()==2?new Locale("",code).getDisplayCountry(Locale.ENGLISH):"Country not supplied";}
 public static String url(String raw){try{if(raw==null||raw.length()>2000)return "";URI u=URI.create(raw.trim());if(!List.of("https","http").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)return "";return u.toString();}catch(Exception e){return "";}}
 public static String digest(String value){try{StringBuilder b=new StringBuilder();for(byte n:MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))b.append(String.format(Locale.ROOT,"%02x",n&255));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 public static List<Distance> distances(String explicit,String surface){
  String s=text(explicit,4000);Map<String,Distance> out=new LinkedHashMap<>();Matcher m=Pattern.compile("(?i)(?<![-\\d.])(\\d+(?:[.,]\\d+)?)\\s*(kilomet(?:er|re)s?|km|miles?|mi|k)\\b").matcher(s);
  while(m.find()){double n=Double.parseDouble(m.group(1).replace(',','.'));String unit=m.group(2).toLowerCase(Locale.ROOT);int metres=(int)Math.round(n*(unit.startsWith("mi")?1609.344:1000));if(metres>=100&&metres<=1000000){Distance d=new Distance(metres,m.group().trim(),surface);out.put(d.key(),d);}}
  if(out.isEmpty()){if(Pattern.compile("(?i)\\bhalf[ -]marathon\\b").matcher(s).find())out.put("half",new Distance(21098,"Half marathon",surface));else if(Pattern.compile("(?i)\\bmarathon\\b").matcher(s).find())out.put("full",new Distance(42195,"Marathon",surface));}
  return List.copyOf(out.values());
 }
 public static LocalDate date(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Date not supplied");for(DateTimeFormatter f:List.of(DateTimeFormatter.ISO_LOCAL_DATE,DateTimeFormatter.ofPattern("M/d/uuuu")))try{return LocalDate.parse(value.split("[ T]")[0],f);}catch(Exception ignored){}throw new IllegalArgumentException("Unsupported race date");}
 public static String time(String value){try{if(value==null||!value.contains(":"))return "";String s=value.replace('T',' ');String part=s.substring(s.indexOf(' ')+1);for(DateTimeFormatter f:List.of(DateTimeFormatter.ofPattern("H:mm:ss"),DateTimeFormatter.ofPattern("H:mm")))try{return LocalTime.parse(part,f).toString();}catch(Exception ignored){}return "";}catch(Exception e){return "";}}
}
