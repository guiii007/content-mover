package com.contentmover.mobile;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class QueueDeleteTest {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  public static void main(String[] args)throws Exception {
    File root=Files.createTempDirectory(Paths.get(args[0]),"delete-").toFile(),pending=new File(root,"pending"),states=new File(root,"states");pending.mkdirs();QueueItems items=new QueueItems(pending,states);
    File failed=new File(pending,"failed.json");Files.write(failed.toPath(),new byte[]{1});items.mark("failed","失败内容","failed","离线");
    File retained=new File(pending,"retained.json");Files.write(retained.toPath(),new byte[]{2});items.mark("retained","其他条目","waiting","");
    items.delete("failed");check(!failed.exists(),"offline payload deleted");items.mark("failed","迟到错误","failed","");check(items.begin("failed")==null&&!items.saved("failed","迟到确认"),"deleted item cannot start or complete later");
    check(QueueState.list(states).size()==1&&retained.exists(),"deleted row cannot reappear and other payload is unchanged");
    try(ServerSocket server=new ServerSocket(0)){
      CountDownLatch accepted=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);AtomicReference<Throwable> unexpected=new AtomicReference<>();
      Thread peer=new Thread(()->{try(Socket socket=server.accept()){accepted.countDown();release.await(5,TimeUnit.SECONDS);}catch(Exception e){unexpected.set(e);}});peer.start();
      File upload=new File(pending,"upload.json");Files.write(upload.toPath(),new byte[]{3});items.mark("upload","上传中","sending","");ClipHttp.Cancellation token=items.begin("upload");
      new Thread(()->{try{ClipHttp.post("127.0.0.1",server.getLocalPort(),new byte[12*1024*1024],1000,10000,15000,token);unexpected.set(new AssertionError("cancelled request succeeded"));}
        catch(IOException expected){try{items.mark("upload","迟到错误","failed",expected.getMessage());}catch(Exception e){unexpected.set(e);}}
        finally{items.finish("upload",token);done.countDown();}}).start();
      check(accepted.await(3,TimeUnit.SECONDS),"active upload connected");items.delete("upload");check(done.await(2,TimeUnit.SECONDS),"delete promptly closes an active socket");release.countDown();peer.join(2000);
      check(unexpected.get()==null&&!upload.exists()&&QueueState.list(states).size()==1,"active deletion remains deleted after late failure");
    }
    File before=new File(pending,"before.json");Files.write(before.toPath(),new byte[]{4});ClipHttp.Cancellation cancelled=items.begin("before");items.delete("before");
    boolean rejected=false;try{ClipHttp.post("127.0.0.1",1,new byte[]{1},1000,1000,1000,cancelled);}catch(IOException e){rejected=e.getMessage().contains("已取消");}check(rejected,"delete before socket registration prevents connection");items.finish("before",cancelled);
    boolean invalid=false;try{items.delete("../outside");}catch(IOException e){invalid=true;}check(invalid,"invalid identifier cannot delete outside queue");
    items.delete("failed");check(retained.exists(),"repeat delete is safe");
    System.out.println("PASS: delete offline payload/receipt; preserve other items; no resurrection after late error or ack; cancel active upload; cancel before socket registration; validated IDs and repeat deletion");
  }
}
