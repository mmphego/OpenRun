package app.rwun;
public final class RunStartTest {
 static int n;static void check(boolean value,String label){n++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args){
  for(int flags=0;flags<32;flags++){
   boolean active=(flags&1)>0,hold=(flags&2)>0,pending=(flags&4)>0,precise=(flags&8)>0,gps=(flags&16)>0;
   var result=RunStartGate.next(active,hold,pending,precise,gps);
   check((result==RunStartGate.Next.READY)==(!active&&!hold&&!pending&&precise&&gps),"only ready with all prerequisites");
   if(active)check(result==RunStartGate.Next.ACTIVE_RUN,"active run remains reachable during hold or permission changes");
   if(!active&&hold)check(result==RunStartGate.Next.TRAINING_HOLD,"hold cannot be bypassed");
   if(!active&&!hold&&pending)check(result==RunStartGate.Next.PENDING_RECORDING,"pending run cannot be overwritten");
  }
  check(RunStartGate.next(false,false,false,true,false)==RunStartGate.Next.LOCATION_SETTINGS,"GPS off directs to settings");
  check(RunStartGate.next(false,false,false,false,false)==RunStartGate.Next.LOCATION_PERMISSION,"permission before GPS");
  check(RunStartGate.outcome(false,"new","old","GPS disabled",100,1000)==RunStartGate.Outcome.WAITING,"ignore stale service failure");
  check(RunStartGate.outcome(false,"new","new","GPS disabled",100,1000)==RunStartGate.Outcome.FAILED,"correlated failure is surfaced");
  check(RunStartGate.outcome(false,"new","new","",100,1000)==RunStartGate.Outcome.WAITING,"acknowledgement alone not recording");
  check(RunStartGate.outcome(false,"new","new","",1000,1000)==RunStartGate.Outcome.UNCONFIRMED,"timeout not false success");
  check(RunStartGate.outcome(true,"new","new","",1001,1000)==RunStartGate.Outcome.RECORDING,"actual activity overrides elapsed confirmation timeout");
  check(RunStartGate.outcome(true,"new","old","old error",100,1000)==RunStartGate.Outcome.RECORDING,"existing active service stays accessible");
  System.out.println(n+" recording-start assertions passed");
 }
}
