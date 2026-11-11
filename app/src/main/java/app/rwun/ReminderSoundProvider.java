package app.rwun;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.ParcelFileDescriptor;import java.io.*;
/** Only the generated reminder audio can be read through explicitly granted URIs. */
public final class ReminderSoundProvider extends ContentProvider {
 public boolean onCreate(){return true;}
 public String getType(Uri uri){return "audio/wav";}
 public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode)||!"/movement.wav".equals(uri.getPath()))throw new FileNotFoundException();return ParcelFileDescriptor.open(new File(getContext().getFilesDir(),"movement-reminder.wav"),ParcelFileDescriptor.MODE_READ_ONLY);}
 public Cursor query(Uri u,String[] p,String s,String[] args,String order){return null;}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 public int delete(Uri u,String s,String[] args){throw new UnsupportedOperationException();}
 public int update(Uri u,ContentValues v,String s,String[] args){throw new UnsupportedOperationException();}
}
