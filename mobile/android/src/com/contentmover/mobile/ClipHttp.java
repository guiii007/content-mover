package com.contentmover.mobile;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** A bounded HTTP exchange: closing the socket also interrupts a stalled upload. */
public final class ClipHttp {
  public static final class Cancellation {
    private boolean cancelled;private Socket active;
    synchronized void attach(Socket socket)throws IOException {if(cancelled){socket.close();throw new IOException("已取消迁移");}active=socket;}
    synchronized void detach(Socket socket){if(active==socket)active=null;}
    public synchronized void cancel(){cancelled=true;if(active!=null)try{active.close();}catch(IOException ignored){}}
  }
  private static final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"content-mover-deadline");t.setDaemon(true);return t;});
  public static String post(String host,int port,byte[] payload,int connectMs,int readMs,int totalMs)throws IOException {
    return post(host,port,payload,connectMs,readMs,totalMs,new Cancellation());
  }
  public static String post(String host,int port,byte[] payload,int connectMs,int readMs,int totalMs,Cancellation cancellation)throws IOException {
    Socket socket=new Socket();AtomicBoolean expired=new AtomicBoolean();String phase="连接电脑";
    cancellation.attach(socket);
    ScheduledFuture<?> timer=deadlines.schedule(()->{expired.set(true);try{socket.close();}catch(IOException ignored){}},totalMs,TimeUnit.MILLISECONDS);
    try{
      socket.connect(new InetSocketAddress(host,port),connectMs);socket.setSoTimeout(readMs);
      OutputStream output=socket.getOutputStream();phase="上传内容";
      output.write(("POST /v1/clips HTTP/1.1\r\nHost: "+host+":"+port+"\r\nContent-Type: application/json\r\nContent-Length: "+payload.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
      for(int offset=0;offset<payload.length;offset+=32768)output.write(payload,offset,Math.min(32768,payload.length-offset));
      output.flush();phase="等待电脑保存确认";InputStream input=socket.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();int tail=0;
      while(header.size()<8192){int b=input.read();if(b<0)throw new EOFException("电脑连接已断开，内容仍保留在手机");header.write(b);tail=(tail<<8)|b;if(tail==0x0d0a0d0a)break;}
      if(tail!=0x0d0a0d0a)throw new IOException("电脑响应无效");
      String[] lines=new String(header.toByteArray(),StandardCharsets.US_ASCII).split("\r\n");
      if(!lines[0].startsWith("HTTP/1.1 200 "))throw new IOException("电脑未接受内容，请检查配对或内容大小");
      int length=-1;for(String line:lines){if(line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")){try{length=Integer.parseInt(line.substring(15).trim());}catch(NumberFormatException e){throw new IOException("电脑响应无效");}}}
      if(length<0||length>65536)throw new IOException("电脑响应无效");
      byte[] response=new byte[length];int offset=0;while(offset<length){int n=input.read(response,offset,length-offset);if(n<0)throw new EOFException("电脑保存确认不完整，内容仍保留在手机");offset+=n;}
      return new String(response,StandardCharsets.UTF_8);
    }catch(IOException e){if(expired.get()||e instanceof SocketTimeoutException)throw new IOException(phase+"超时，内容已保留，其他条目可继续发送",e);throw e;}
    finally{timer.cancel(false);cancellation.detach(socket);socket.close();}
  }
}
