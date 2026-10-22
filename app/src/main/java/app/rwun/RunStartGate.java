package app.rwun;

/** Readiness only: never equates requesting a service with confirmed recording. */
public final class RunStartGate {
  public enum Next { ACTIVE_RUN, TRAINING_HOLD, PENDING_RECORDING, LOCATION_PERMISSION, LOCATION_SETTINGS, READY }
  public static Next next(boolean active,boolean held,boolean pending,boolean precise,boolean gps){
    if(active)return Next.ACTIVE_RUN;
    if(held)return Next.TRAINING_HOLD;
    if(pending)return Next.PENDING_RECORDING;
    if(!precise)return Next.LOCATION_PERMISSION;
    if(!gps)return Next.LOCATION_SETTINGS;
    return Next.READY;
  }
  public enum Outcome { RECORDING, FAILED, WAITING, UNCONFIRMED }
  public static Outcome outcome(boolean active,String requested,String acknowledged,String failure,long now,long deadline){
    if(active)return Outcome.RECORDING;
    if(requested.equals(acknowledged)&&failure!=null&&!failure.isEmpty())return Outcome.FAILED;
    return now>=deadline?Outcome.UNCONFIRMED:Outcome.WAITING;
  }
}
