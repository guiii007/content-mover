package com.contentmover.mobile;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Local PIN protects an existing pairing; it never replaces the transport key. */
public final class PinCrypto {
  public static byte[] derive(String pin,byte[] salt)throws Exception {
    if(pin==null || !pin.matches("[0-9]{4}"))throw new IllegalArgumentException("请输入 4 位数字密码");
    PBEKeySpec spec=new PBEKeySpec(pin.toCharArray(),salt,120000,256);
    try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
  }
  public static byte[] encrypt(byte[] wrapping,byte[] key)throws Exception {
    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(wrapping,"AES"));
    byte[] iv=cipher.getIV(),data=cipher.doFinal(key),result=new byte[12+data.length];
    if(iv.length!=12)throw new GeneralSecurityException("Invalid IV");
    System.arraycopy(iv,0,result,0,12);System.arraycopy(data,0,result,12,data.length);return result;
  }
  public static byte[] decrypt(byte[] wrapping,byte[] payload)throws Exception {
    if(payload.length<28)throw new GeneralSecurityException("Invalid payload");
    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(wrapping,"AES"),new GCMParameterSpec(128,payload,0,12));
    return cipher.doFinal(payload,12,payload.length-12);
  }
  public static long delay(int failures){return failures<5?0:Math.min(600000L,30000L << Math.min(5,failures-5));}
}
