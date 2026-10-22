package app.rwun;

/** User-facing failures do not expose Android intents, process IDs or stack traces. */
final class UserErrors {
  static String message(Throwable error){
    while(error instanceof java.util.concurrent.CompletionException&&error.getCause()!=null)error=error.getCause();
    if(error instanceof SecurityException)return "Access was denied. Check the app’s permissions in Android Settings, then try again.";
    if(error instanceof NumberFormatException)return "Enter a valid number in each required field.";
    if(error instanceof java.io.IOException)return "The file could not be read or saved. Check the file and available storage, then try again.";
    String message=error.getMessage();
    if(message==null||message.trim().isEmpty()||message.length()>220||message.contains("Intent {")||message.contains("ProcessRecord")||message.contains("android.permission."))return "This action is unavailable right now. Check permissions and try again.";
    return message;
  }
}
