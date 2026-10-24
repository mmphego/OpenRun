package app.rwun;

import android.content.*;
import java.time.*;
import java.util.*;
import org.json.*;

/** Separate private state, absent from RunStore backups, uploads, analytics and AI summaries. */
final class DailyHealthStore {
    static final Object LOCK=new Object();
    static final String FILE="openrun-daily-health";
    private final SharedPreferences preferences;
    DailyHealthStore(Context context){preferences=context.getSharedPreferences(FILE,Context.MODE_PRIVATE);}
    DailyHealthRules.Settings settings() {
        String saved=preferences.getString("settings","");if(saved.isEmpty())return DailyHealthRules.defaults();
        try {
            JSONObject json=new JSONObject(saved);Set<Integer> days=new HashSet<>();JSONArray list=json.getJSONArray("days");
            for(int i=0;i<list.length();i++)days.add(list.getInt(i));
            return new DailyHealthRules.Settings(json.getBoolean("movement"),json.getBoolean("checkin"),days,json.getInt("start"),json.getInt("end"),json.getInt("inactivity"),json.getInt("break"));
        }catch(Exception error){throw new IllegalStateException("Movement preferences could not be read. Your data has been kept.",error);}
    }
    void settings(DailyHealthRules.Settings value,Instant now) {
        synchronized(LOCK){try{
            var old=settings();boolean reset=old.movement()!=value.movement()||!old.days().equals(value.days())||old.startMinute()!=value.startMinute()
                    ||old.endMinute()!=value.endMinute()||old.inactivityMinutes()!=value.inactivityMinutes();
            JSONObject json=new JSONObject().put("movement",value.movement()).put("checkin",value.checkin()).put("days",new JSONArray(value.days()))
                    .put("start",value.startMinute()).put("end",value.endMinute()).put("inactivity",value.inactivityMinutes()).put("break",value.breakMinutes());
            var edit=preferences.edit().putString("settings",json.toString()).putLong("revision",revision()+1);
            if(reset)edit.putLong("enabledAt",now.toEpochMilli()).remove("delivered").remove("snooze").remove("skip").remove("status");
            commit(edit);
        }catch(JSONException error){throw new IllegalArgumentException(error);}}
    }
    long revision(){return preferences.getLong("revision",0);}
    boolean recoveryReads(){return preferences.getBoolean("recoveryReads",false);}
    void recoveryReads(boolean on){synchronized(LOCK){commit(preferences.edit().putBoolean("recoveryReads",on).putLong("revision",revision()+1));}}
    DailyHealthRules.MovementState evaluate(ZonedDateTime now,DailyHealthRules.Movement sample,boolean suppressed) {
        int count=now.toLocalDate().toString().equals(preferences.getString("day",""))?preferences.getInt("count",0):0;
        return DailyHealthRules.movement(settings(),now,sample,preferences.getLong("enabledAt",now.toInstant().toEpochMilli()),preferences.getLong("acknowledged",0),
                preferences.getLong("delivered",0),preferences.getLong("snooze",0),preferences.getString("skip",""),count,suppressed);
    }
    void delivered(ZonedDateTime now) {synchronized(LOCK){
        String day=now.toLocalDate().toString();int count=day.equals(preferences.getString("day",""))?preferences.getInt("count",0):0;
        commit(preferences.edit().putString("day",day).putInt("count",count+1).putLong("delivered",now.toInstant().toEpochMilli()));
    }}
    void acknowledge(Instant now){synchronized(LOCK){commit(preferences.edit().putLong("acknowledged",now.toEpochMilli()).remove("snooze"));}}
    void snooze(Instant now){synchronized(LOCK){commit(preferences.edit().putLong("snooze",now.plusSeconds(1800).toEpochMilli()).remove("delivered"));}}
    void skip(LocalDate day){synchronized(LOCK){commit(preferences.edit().putString("skip",day.toString()));}}
    void status(String text){synchronized(LOCK){commit(preferences.edit().putString("status",text));}}
    String status(){return preferences.getString("status","No movement check yet. Health Connect data may be missing or delayed.");}
    void saveCheckin(DailyHealthRules.Checkin value){synchronized(LOCK){try{
        JSONObject json=new JSONObject().put("at",value.at().toEpochMilli()).put("sleep",value.sleepHours()==null?JSONObject.NULL:value.sleepHours())
                .put("fatigue",value.fatigue()).put("soreness",value.soreness()).put("alcohol",value.alcohol().name()).put("illness",value.illness().name()).put("pain",value.pain());
        commit(preferences.edit().putString("checkin",json.toString()));
    }catch(JSONException error){throw new IllegalArgumentException(error);}}}
    DailyHealthRules.Checkin checkin(Instant now){synchronized(LOCK){
        String saved=preferences.getString("checkin","");if(saved.isEmpty())return null;
        try{
            JSONObject json=new JSONObject(saved);Instant at=Instant.ofEpochMilli(json.getLong("at"));
            if(at.isBefore(now.minus(Duration.ofDays(7)))){clearCheckin();return null;}
            return new DailyHealthRules.Checkin(at,json.isNull("sleep")?null:json.getDouble("sleep"),json.getInt("fatigue"),json.getInt("soreness"),
                    DailyHealthRules.Answer.valueOf(json.getString("alcohol")),DailyHealthRules.Answer.valueOf(json.getString("illness")),json.getBoolean("pain"));
        }catch(Exception error){throw new IllegalStateException("Your check-in could not be read. Delete it to start again.",error);}
    }}
    void clearCheckin(){synchronized(LOCK){commit(preferences.edit().remove("checkin"));}}
    void clear(){synchronized(LOCK){long next=revision()+1;commit(preferences.edit().clear().putLong("revision",next));}}
    private static void commit(SharedPreferences.Editor editor){if(!editor.commit())throw new IllegalStateException("Could not save movement and recovery preferences.");}
}
