package com.contentmover.mobile;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class UploadQueueTest {
  static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
  public static void main(String[] args)throws Exception {
    File root=Files.createTempDirectory(Paths.get(args[0]),"upload-").toFile();File big=new File(root,"old-large.json"),small=new File(root,"new-text.json"),later=new File(root,"later-text.json");
    try(RandomAccessFile file=new RandomAccessFile(big,"rw")){file.setLength(6*1024*1024);}
    Files.write(small.toPath(),new byte[]{1});
    CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),firstSaved=new CountDownLatch(1),laterSaved=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();AtomicInteger attempts=new AtomicInteger();
    Thread drain=new Thread(()->{try{QueuePump.drain(()->root.listFiles((d,n)->n.endsWith(".json")),file->{
      if(file.equals(big)){attempts.incrementAndGet();started.countDown();try{check(release.await(5,TimeUnit.SECONDS),"release image upload");}catch(Exception e){throw new RuntimeException(e);}return;}
      file.delete();if(file.equals(small))firstSaved.countDown();if(file.equals(later))laterSaved.countDown();
    });}catch(Throwable t){error.set(t);}});
    drain.start();check(started.await(2,TimeUnit.SECONDS),"image request started");check(firstSaved.await(2,TimeUnit.SECONDS),"small clip completes before slow image");
    Files.write(later.toPath(),new byte[]{2});check(laterSaved.await(2,TimeUnit.SECONDS),"newly enqueued text also completes while image waits");
    release.countDown();drain.join(3000);check(!drain.isAlive()&&error.get()==null,"drain ends cleanly");check(big.exists()&&attempts.get()==1,"failed image kept and attempted only once per drain");
    try(ServerSocket server=new ServerSocket(0)){
      CountDownLatch close=new CountDownLatch(1);Thread peer=new Thread(()->{try(Socket socket=server.accept()){socket.setReceiveBufferSize(1024);close.await(3,TimeUnit.SECONDS);}catch(Exception ignored){}});peer.start();
      long begin=System.nanoTime();boolean timedOut=false;
      try{ClipHttp.post("127.0.0.1",server.getLocalPort(),new byte[12*1024*1024],1000,2000,400);}catch(IOException e){timedOut=e.getMessage().contains("超时");}
      close.countDown();peer.join(2000);check(timedOut&&TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin)<2500,"total deadline interrupts blocked socket upload");
    }
    byte[] largePayload=new byte[6*1024*1024];new java.util.Random(42).nextBytes(largePayload);
    try(ServerSocket server=new ServerSocket(0)){
      Thread peer=new Thread(()->{try(Socket socket=server.accept()){
        InputStream input=socket.getInputStream();int tail=0;while(tail!=0x0d0a0d0a){int n=input.read();if(n<0)return;tail=(tail<<8)|n;}
        byte[] received=new byte[largePayload.length];int offset=0;while(offset<received.length){int n=input.read(received,offset,received.length-offset);if(n<0)throw new EOFException();offset+=n;}check(java.util.Arrays.equals(received,largePayload),"6 MB original payload preserved exactly");
        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII));
      }catch(Exception e){error.set(e);}});peer.start();
      check("{}".equals(ClipHttp.post("127.0.0.1",server.getLocalPort(),largePayload,1000,2000,5000)),"bounded transport returns complete acknowledgement");peer.join(2000);check(error.get()==null,"mock receiver succeeded");
    }
    System.out.println("PASS: 6 MB image cannot block text or new arrivals; failed payload retained; one attempt per drain; stalled upload interrupted by deadline; complete HTTP acknowledgement");
  }
}
