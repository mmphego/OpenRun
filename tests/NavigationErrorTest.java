package app.rwun;

import java.util.*;

public final class NavigationErrorTest {
  static int checks;
  static void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);}
  public static void main(String[] args){
    List<String> attempts=new ArrayList<>();
    check(SettingsNavigator.openHealth(action->{attempts.add(action);return true;}),"public health settings opens");
    check(attempts.equals(List.of("android.health.connect.action.HEALTH_HOME_SETTINGS")),"public home action replaces restricted permission action");
    attempts.clear();
    check(SettingsNavigator.openHealth(action->{attempts.add(action);if(action.equals(SettingsNavigator.HEALTH_HOME))throw new SecurityException("privileged activity");return true;}),"security rejection falls back to general settings");
    check(attempts.equals(List.of(SettingsNavigator.HEALTH_HOME,SettingsNavigator.SETTINGS)),"fallback order");
    check(!SettingsNavigator.openHealth(action->{throw new SecurityException("denied");}),"both blocked returns manual help state");
    check(!SettingsNavigator.openHealth(action->false),"missing activities returns manual help state");
    check(!UserErrors.message(new SecurityException("Intent { ProcessRecord pid=18233 requires android.permission.GRANT_RUNTIME_PERMISSIONS")).contains("ProcessRecord"),"permission error hides platform internals");
    check(UserErrors.message(new NumberFormatException("For input string" )).equals("Enter a valid number in each required field."),"numeric error is actionable");
    check(UserErrors.message(new IllegalArgumentException("Weekly distance must be between 4 and 120 km.")).contains("4 and 120"),"domain validation remains useful");
    check(UserErrors.message(new java.util.concurrent.CompletionException(new SecurityException("secret"))).contains("Access was denied"),"async errors are unwrapped safely");
    System.out.println("Navigation/error regression tests passed: "+checks+" assertions.");
  }
}
