package app.rwun;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Credentials and the optional backup passphrase are encrypted with an app-owned Android Keystore key. */
final class SyncVault {
  private static final Object LOCK=new Object();
  private final Context context;
  SyncVault(Context c){context=c.getApplicationContext();}
  private SecretKey key() throws Exception{
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
    if(store.containsAlias("rwun.sync"))return (SecretKey)store.getKey("rwun.sync",null);
    KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder("rwun.sync",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
  }
  JSONObject read(){ synchronized(LOCK) {
    try{String encoded=context.getSharedPreferences("sync_vault",0).getString("data","");if(encoded.isEmpty())return new JSONObject();byte[] raw=Base64.decode(encoded,Base64.NO_WRAP);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,java.util.Arrays.copyOfRange(raw,0,12)));return new JSONObject(new String(cipher.doFinal(raw,12,raw.length-12),StandardCharsets.UTF_8));}
    catch(Exception e){throw new IllegalStateException("Sync credentials cannot be opened on this device. Clear sync settings and sign in again.",e);}
   }}
  void write(JSONObject data){ synchronized(LOCK) {
    try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=cipher.doFinal(data.toString().getBytes(StandardCharsets.UTF_8));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();out.write(cipher.getIV());out.write(encrypted);if(!context.getSharedPreferences("sync_vault",0).edit().putString("data",Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)).commit())throw new IllegalStateException("Could not save sync settings");}
    catch(Exception e){throw new IllegalStateException("Could not protect sync settings",e);}
   }}
  interface Update {void apply(JSONObject data)throws Exception;}
  void update(Update operation)throws Exception{synchronized(LOCK){JSONObject data=read();operation.apply(data);write(data);}}
  void clear(){ synchronized(LOCK) {if(!context.getSharedPreferences("sync_vault",0).edit().clear().commit())throw new IllegalStateException("Could not clear sync settings"); }}
}
