package com.contentmover.mobile;
import java.util.Arrays;
import java.security.SecureRandom;
public final class PinCryptoTest {
  static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
  public static void main(String[] args)throws Exception {
    byte[] key=new byte[32],salt=new byte[16];new SecureRandom().nextBytes(key);new SecureRandom().nextBytes(salt);
    byte[] wrapping=PinCrypto.derive("0123",salt),envelope=PinCrypto.encrypt(wrapping,key);
    check(Arrays.equals(key,PinCrypto.decrypt(wrapping,envelope)),"32-byte transport key unchanged, leading zero PIN");
    check(!Arrays.equals(envelope,PinCrypto.encrypt(wrapping,key)),"random encryption nonce");
    boolean rejected=false;try{PinCrypto.decrypt(PinCrypto.derive("0124",salt),envelope);}catch(Exception e){rejected=true;}check(rejected,"wrong PIN rejected");
    envelope[envelope.length-1]^=1;rejected=false;try{PinCrypto.decrypt(wrapping,envelope);}catch(Exception e){rejected=true;}check(rejected,"tampered envelope rejected");
    for(String pin:new String[]{"123","12345","abcd","１２３４"}){rejected=false;try{PinCrypto.derive(pin,salt);}catch(Exception e){rejected=true;}check(rejected,"invalid PIN rejected");}
    check(PinCrypto.delay(4)==0 && PinCrypto.delay(5)==30000 && PinCrypto.delay(6)==60000 && PinCrypto.delay(100)==600000,"progressive cooldown capped");
    System.out.println("PASS: key preservation, leading-zero PIN, random nonce, wrong PIN and tampering rejected, input validation, progressive cooldown");
  }
}
