package app.rwun;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.List;

/** Local transfer intent, independent of system permission grants. No health values stored. */
final class HealthPreferences {
    static final String CALORIES="android.permission.health.READ_ACTIVE_CALORIES_BURNED";
    static final String[] LABELS={"Runs, walks & distance", "Heart rate", "Steps", "Active calories", "Sleep", "Resting heart rate"};
    static final String[][] READ_PERMISSIONS={HealthSync.PERMISSIONS, {HealthSync.HEART_RATE}, {DailyHealthSource.STEPS},
            {CALORIES}, {DailyHealthSource.SLEEP}, {DailyHealthSource.RESTING_HR}};
    private static SharedPreferences state(Context c){return c.getSharedPreferences("health-connect-controls",Context.MODE_PRIVATE);}
    static boolean enabled(Context c,boolean write){return state(c).getBoolean(write?"write":"read",true);}
    static void enabled(Context c,boolean write,boolean enabled){state(c).edit().putBoolean(write?"write":"read",enabled).apply();}
    static boolean selected(Context c,int index){
        boolean legacy=index==0;
        if(index>0)legacy=c.checkSelfPermission(READ_PERMISSIONS[index][0])==PackageManager.PERMISSION_GRANTED;
        return state(c).getBoolean("type:"+index,legacy);
    }
    static void select(Context c,boolean[] selected){
        SharedPreferences.Editor edit=state(c).edit();
        for(int i=0;i<LABELS.length;i++)edit.putBoolean("type:"+i,selected[i]);
        edit.apply();
    }
    static String[] requested(Context c){
        List<String> permissions=new ArrayList<>();
        for(int i=0;i<LABELS.length;i++)if(selected(c,i))java.util.Collections.addAll(permissions,READ_PERMISSIONS[i]);
        return permissions.toArray(new String[0]);
    }
    static void selectPermission(Context c,String permission){
        for(int i=0;i<READ_PERMISSIONS.length;i++)for(String candidate:READ_PERMISSIONS[i])
            if(candidate.equals(permission)){state(c).edit().putBoolean("type:"+i,true).apply();return;}
    }
    static boolean allows(Context c,String permission){
        if(permission.startsWith("android.permission.health.WRITE_"))return enabled(c,true);
        if(!permission.startsWith("android.permission.health.READ_"))return true;
        if(!enabled(c,false))return false;
        for(int i=0;i<READ_PERMISSIONS.length;i++)for(String candidate:READ_PERMISSIONS[i])
            if(candidate.equals(permission))return selected(c,i);
        return true;
    }
    static boolean anyReadGranted(Context c){
        if(!enabled(c,false))return false;
        for(int i=0;i<LABELS.length;i++)if(selected(c,i)&&HealthAccess.granted(c,READ_PERMISSIONS[i]))return true;
        return false;
    }
    static String summary(Context c){
        List<String> names=new ArrayList<>();
        for(int i=0;i<LABELS.length;i++)if(selected(c,i))names.add(LABELS[i]+(HealthAccess.granted(c,READ_PERMISSIONS[i])?"":" (not allowed)"));
        return names.isEmpty()?"No data selected":String.join(" · ",names);
    }
}
