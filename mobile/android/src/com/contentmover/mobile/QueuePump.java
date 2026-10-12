package com.contentmover.mobile;
import java.io.File;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Reserve a lane for small clips even while an image upload is waiting. */
public final class QueuePump {
  public static final long SMALL_LIMIT=1024*1024;
  public static void drain(Supplier<File[]> pending,Consumer<File> send)throws InterruptedException {
    Thread images=new Thread(()->{
      HashSet<String> attempted=new HashSet<>();
      for(;;){File next=next(pending.get(),attempted,false);if(next==null)return;
        attempted.add(next.getName());send.accept(next);
      }
    },"content-mover-images");
    images.start();
    HashSet<String> attempted=new HashSet<>();
    try{
      for(;;){File next=next(pending.get(),attempted,true);
        if(next!=null){attempted.add(next.getName());send.accept(next);continue;}
        if(!images.isAlive())break;
        Thread.sleep(200);
      }
    }finally{images.join();}
  }
  private static File next(File[] files,HashSet<String> attempted,boolean small){
    for(File file:files)if((file.length()<=SMALL_LIMIT)==small&&!attempted.contains(file.getName()))return file;
    return null;
  }
}
