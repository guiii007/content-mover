package com.contentmover.mobile;
/** Coalesces requests but never loses an enqueue during an active send. */
public final class DispatchLoop {
  private boolean running,requested;
  public synchronized void request(Runnable drain){
    requested=true;if(running)return;running=true;
    new Thread(()->{
      for(;;){synchronized(this){if(!requested){running=false;return;}requested=false;}
        try{drain.run();}catch(Exception ignored){}
      }
    },"content-mover-queue").start();
  }
}
