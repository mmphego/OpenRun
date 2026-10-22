package app.rwun;

/** Public Health Connect home action from AndroidX, followed by ordinary system settings. */
final class SettingsNavigator {
  static final String HEALTH_HOME="android.health.connect.action.HEALTH_HOME_SETTINGS";
  static final String SETTINGS="android.settings.SETTINGS";
  interface Launcher { boolean launch(String action); }
  static boolean openHealth(Launcher launcher){
    for(String action:new String[]{HEALTH_HOME,SETTINGS}){
      try{if(launcher.launch(action))return true;}catch(SecurityException blocked){/* Try the next public entry point. */}
    }
    return false;
  }
}
