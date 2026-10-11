package com.contentmover.mobile;
import android.content.*;
import android.security.keystore.*;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** PIN envelope is additionally wrapped with a non-exportable Android Keystore key. */
public final class PinVault {
  static final String ALIAS="contentmover.pair.pin.v1";
  static byte[] sessionKey,sessionWrapping;
  static long expiry;
  static SharedPreferences prefs(Context c){return c.getSharedPreferences("pin",0);}
  static boolean configured(Context c){return prefs(c).contains("payload");}
  static boolean paired(Context c){return configured(c)||!c.getSharedPreferences("pair",0).getString("key","").isEmpty();}
  static synchronized boolean unlocked(){return sessionKey!=null && android.os.SystemClock.elapsedRealtime()<expiry;}
  static synchronized void lock(){if(sessionKey!=null)Arrays.fill(sessionKey,(byte)0);if(sessionWrapping!=null)Arrays.fill(sessionWrapping,(byte)0);sessionKey=null;sessionWrapping=null;expiry=0;}
  static javax.crypto.SecretKey deviceKey()throws Exception {
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
    if(!store.containsAlias(ALIAS)){
      KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");
      gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();
    }
    return (javax.crypto.SecretKey)store.getKey(ALIAS,null);
  }
  static byte[] deviceWrap(byte[] value,boolean encrypt)throws Exception {
    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
    if(encrypt){cipher.init(Cipher.ENCRYPT_MODE,deviceKey());byte[] iv=cipher.getIV(),body=cipher.doFinal(value),out=new byte[12+body.length];System.arraycopy(iv,0,out,0,12);System.arraycopy(body,0,out,12,body.length);return out;}
    if(value.length<28)throw new GeneralSecurityException("密码配置损坏，请重新扫码配对");
    cipher.init(Cipher.DECRYPT_MODE,deviceKey(),new GCMParameterSpec(128,value,0,12));return cipher.doFinal(value,12,value.length-12);
  }
  static synchronized void setup(Context c,String pin)throws Exception {
    byte[] key=Transfer.decode(c.getSharedPreferences("pair",0).getString("key",""));if(key.length!=32)throw new Exception("请先扫码连接电脑");
    byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);byte[] wrapping=PinCrypto.derive(pin,salt);
    save(c,key,wrapping,salt);setSession(key,wrapping);
  }
  static void save(Context c,byte[] key,byte[] wrapping,byte[] salt)throws Exception {
    String payload=Transfer.encode(deviceWrap(PinCrypto.encrypt(wrapping,key),true));
    if(!prefs(c).edit().putString("salt",Transfer.encode(salt)).putString("payload",payload).putInt("failures",0).putLong("until",0).commit())throw new Exception("无法保存密码，请重试");
    if(!c.getSharedPreferences("pair",0).edit().remove("key").commit())throw new Exception("无法更新配对，请重试");
  }
  static synchronized void replaceKey(Context c,byte[] key)throws Exception {
    if(!unlocked())throw new Exception("请先输入密码解锁");save(c,key,sessionWrapping,Transfer.decode(prefs(c).getString("salt","")));setSession(key,sessionWrapping.clone());
  }
  static synchronized void unlock(Context c,String pin)throws Exception {
    SharedPreferences p=prefs(c);long remaining=p.getLong("until",0)-System.currentTimeMillis();
    if(remaining>0)throw new Exception("输错次数较多，请等待 "+((remaining+999)/1000)+" 秒");
    byte[] envelope=deviceWrap(Transfer.decode(p.getString("payload","")),false);
    byte[] wrapping;
    try{wrapping=PinCrypto.derive(pin,Transfer.decode(p.getString("salt","")));byte[] key=PinCrypto.decrypt(wrapping,envelope);if(key.length!=32)throw new GeneralSecurityException();setSession(key,wrapping);}
    catch(Exception e){int failures=p.getInt("failures",0)+1;long delay=PinCrypto.delay(failures);p.edit().putInt("failures",failures).putLong("until",System.currentTimeMillis()+delay).commit();throw new Exception(delay>0?"输错次数较多，请稍后重试":"密码不正确");}
    p.edit().putInt("failures",0).putLong("until",0).commit();c.getSharedPreferences("pair",0).edit().remove("key").commit();
  }
  static void setSession(byte[] key,byte[] wrapping){lock();sessionKey=key.clone();sessionWrapping=wrapping.clone();expiry=android.os.SystemClock.elapsedRealtime()+5*60*1000;}
  static synchronized byte[] key(Context c)throws Exception {if(!configured(c))throw new Exception("请先设置 4 位密码");if(!unlocked()){lock();throw new Exception("请打开 App 输入 4 位密码，再重试发送");}return sessionKey.clone();}
  static synchronized void reset(Context c){lock();prefs(c).edit().clear().commit();c.getSharedPreferences("pair",0).edit().clear().commit();}
}
