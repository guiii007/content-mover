package com.contentmover.mobile;
import android.content.Context;import android.net.Uri;import android.util.Base64;import org.json.*;import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.security.*;import javax.crypto.*;import javax.crypto.spec.*;

public final class Transfer {
  static final java.util.concurrent.atomic.AtomicBoolean sending=new java.util.concurrent.atomic.AtomicBoolean();
  static byte[] derive(byte[] key,String purpose)throws Exception{MessageDigest hash=MessageDigest.getInstance("SHA-256");hash.update(key);return hash.digest(purpose.getBytes(StandardCharsets.US_ASCII));}
  static byte[] decode(String value){return Base64.decode(value,Base64.NO_WRAP);}
  static String encode(byte[] value){return Base64.encodeToString(value,Base64.NO_WRAP);}
  static String seal(byte[] key,String text)throws Exception{byte[] iv=new byte[16];new SecureRandom().nextBytes(iv);Cipher aes=Cipher.getInstance("AES/CBC/PKCS5Padding");aes.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(derive(key,"enc"),"AES"),new IvParameterSpec(iv));byte[] data=aes.doFinal(text.getBytes(StandardCharsets.UTF_8));Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(derive(key,"mac"),"HmacSHA256"));mac.update(iv);return new JSONObject().put("iv",encode(iv)).put("data",encode(data)).put("mac",encode(mac.doFinal(data))).toString();}
  static String open(byte[] key,String text)throws Exception{JSONObject env=new JSONObject(text);byte[] iv=decode(env.getString("iv")),data=decode(env.getString("data")),signature=decode(env.getString("mac"));Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(derive(key,"mac"),"HmacSHA256"));mac.update(iv);if(!MessageDigest.isEqual(signature,mac.doFinal(data)))throw new IOException("电脑确认验证失败");Cipher aes=Cipher.getInstance("AES/CBC/PKCS5Padding");aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(derive(key,"enc"),"AES"),new IvParameterSpec(iv));return new String(aes.doFinal(data),StandardCharsets.UTF_8);}
  static boolean privateHost(String host){return AddressPolicy.local(host);}
  static boolean vpnConnected(Context context){android.net.ConnectivityManager manager=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);if(manager==null)return false;for(android.net.Network network:manager.getAllNetworks()){android.net.NetworkCapabilities caps=manager.getNetworkCapabilities(network);if(caps!=null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN))return true;}return false;}
  static void pair(Context context,String value)throws Exception{Uri uri=Uri.parse(value.trim());if(!"contentmover".equals(uri.getScheme()) || !"pair".equals(uri.getHost()) || !"1".equals(uri.getQueryParameter("v")))throw new IOException("不是内容迁移的配对二维码");String host=uri.getQueryParameter("host"),port=uri.getQueryParameter("port"),key=uri.getQueryParameter("key");String mode=uri.getQueryParameter("mode");boolean remote="remote".equals(mode);if((mode!=null && !remote && !"lan".equals(mode)) || !AddressPolicy.allowed(host,remote) || !"47831".equals(port) || decode(key).length!=32)throw new IOException("配对内容无效");if(PinVault.configured(context)){PinVault.replaceKey(context,decode(key));context.getSharedPreferences("pair",0).edit().putString("host",host).putBoolean("remote",remote).commit();}else context.getSharedPreferences("pair",0).edit().putString("host",host).putString("key",key).putBoolean("remote",remote).commit();}
  static File queue(Context context){File path=new File(context.getFilesDir(),"pending");path.mkdirs();return path;}
  static File[] pending(Context context){File[] files=queue(context).listFiles((dir,name)->name.endsWith(".json"));if(files==null)return new File[0];java.util.Arrays.sort(files,(a,b)->{int order=Long.compare(a.lastModified(),b.lastModified());return order!=0?order:a.getName().compareTo(b.getName());});return files;}
  static byte[] read(InputStream input,int max)throws IOException{ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1){if(result.size()+count>max)throw new IOException("内容过大");result.write(bytes,0,count);}return result.toByteArray();}
  static String enqueue(Context context,JSONObject clip)throws Exception{byte[] serialized=clip.toString().getBytes(StandardCharsets.UTF_8);if(serialized.length>22000000)throw new IOException("本条内容编码后超过传输上限，请减少图片或勾选缩小后重试。");String id=clip.getString("Id");File temp=new File(queue(context),id+".tmp"),destination=new File(queue(context),id+".json");try(FileOutputStream output=new FileOutputStream(temp)){output.write(serialized);output.getFD().sync();}if(!temp.renameTo(destination))throw new IOException("无法保存待发送内容");mark(context,id,clip.optString("Title","手机摘录"),"waiting","");return id;}
  static final DispatchLoop dispatcher=new DispatchLoop();
  static void mark(Context context,String id,String title,String state,String detail){
    try{QueueState.write(new File(context.getFilesDir(),"queue-state"),id,title.trim().isEmpty()?"手机摘录":title,state,detail);QueueState.prune(new File(context.getFilesDir(),"queue-state"));}catch(IOException ignored){}
  }
  static void dispatch(Context context){final Context app=context.getApplicationContext();dispatcher.request(()->{
    try{sendPending(app);}catch(Exception e){for(File file:pending(app)){
      String id=file.getName().replace(".json","");String title="手机摘录";
      try{for(java.util.Properties row:QueueState.list(new File(app.getFilesDir(),"queue-state")))if(id.equals(row.getProperty("id"))){title=row.getProperty("title",title);break;}}catch(Exception ignored){}
      mark(app,id,title,"failed",e.getMessage()==null?"连接暂不可用":e.getMessage());
    }}
  });}
  static synchronized String sendPending(Context context)throws Exception {
    sending.set(true);
    try{
      String host=context.getSharedPreferences("pair",0).getString("host","");boolean remote=context.getSharedPreferences("pair",0).getBoolean("remote",false);
      if(!AddressPolicy.allowed(host,remote))throw new IOException("请先扫码连接电脑");
      if(remote&&!vpnConnected(context))throw new IOException("请保持 Tailscale 连接");
      byte[] key=PinVault.key(context);int count=0,failed=0;
      java.util.HashSet<String> attempted=new java.util.HashSet<>();
      for(;;){File next=null;for(File file:pending(context))if(!attempted.contains(file.getName())){next=file;break;}if(next==null)break;
        File file=next;attempted.add(file.getName());String id=file.getName().replace(".json",""),heading="手机摘录";
        try{
          JSONObject clip;try(FileInputStream input=new FileInputStream(file)){clip=new JSONObject(new String(read(input,22000000),StandardCharsets.UTF_8));}
          heading=clip.optString("Title","手机摘录");mark(context,id,heading,"sending","");
          byte[] payload=seal(key,clip.toString()).getBytes(StandardCharsets.UTF_8);
          HttpURLConnection connection=(HttpURLConnection)new URL("http://"+host+":47831/v1/clips").openConnection();
          try{
            connection.setConnectTimeout(remote?15000:4500);connection.setReadTimeout(remote?60000:20000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");connection.setFixedLengthStreamingMode(payload.length);
            try(OutputStream output=connection.getOutputStream()){output.write(payload);}
            if(connection.getResponseCode()!=200)throw new IOException("电脑未接受内容，请检查配对或内容大小");
            String response;try(InputStream input=connection.getInputStream()){response=new String(read(input,65536),StandardCharsets.UTF_8);}
            JSONObject ack=new JSONObject(open(key,response));
            if(!ack.optBoolean("saved")||!clip.getString("Id").equals(ack.getString("id")))throw new IOException("保存确认不匹配");
            if(!file.delete())throw new IOException("无法更新待发送列表");
            mark(context,id,heading,"saved","");count++;
          }finally{connection.disconnect();}
        }catch(Exception e){failed++;mark(context,id,heading,"failed",e instanceof java.net.SocketTimeoutException?"连接超时，请稍后重试":e.getMessage()==null?"发送失败，请重试":e.getMessage());}
      }
      return "电脑已保存 "+count+" 条"+(failed>0?"，待重试 "+failed+" 条":"");
    }finally{sending.set(false);}
  }
}
