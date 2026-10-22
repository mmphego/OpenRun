package app.rwun;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class CalendarExport {
  private static String escape(String value){return value.replace("\\","\\\\").replace("\r","").replace("\n","\\n").replace(";","\\;").replace(",","\\,");}
  private static void line(StringBuilder out,String text){int bytes=0;for(int i=0;i<text.length();){int cp=text.codePointAt(i);String ch=new String(Character.toChars(cp));int length=ch.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(bytes+length>73){out.append("\r\n ");bytes=1;}out.append(ch);bytes+=length;i+=Character.charCount(cp);}out.append("\r\n");}
  public static String build(String planId,List<PlanEngine.Session> sessions){
    StringBuilder out=new StringBuilder();line(out,"BEGIN:VCALENDAR");line(out,"VERSION:2.0");line(out,"PRODID:-//OpenRun//Training calendar//EN");line(out,"CALSCALE:GREGORIAN");
    for(var session:sessions){line(out,"BEGIN:VEVENT");line(out,"UID:"+escape(planId+":"+session.id()+"@rwun"));line(out,"DTSTAMP:"+DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now()));line(out,"DTSTART;VALUE=DATE:"+session.date().format(DateTimeFormatter.BASIC_ISO_DATE));line(out,"DTEND;VALUE=DATE:"+session.date().plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE));line(out,"SUMMARY:"+escape("OpenRun · "+session.title()+" · "+PlanEngine.distance(session.km())));line(out,"DESCRIPTION:"+escape(session.instructions()));line(out,"END:VEVENT");}
    line(out,"END:VCALENDAR");return out.toString();
  }
}
