package com.contentmover.mobile;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class QueueInteractionTest {
  static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
  public static void main(String[] args)throws Exception {
    PinEntry login=new PinEntry(false);check(login.digit(0)==null,"not submitted early");login.digit(1);login.digit(2);login.delete();login.digit(2);check("0123".equals(login.digit(3)),"fourth digit submits, leading zero and delete");
    PinEntry setup=new PinEntry(true);for(int n:new int[]{1,2,3,4})check(setup.digit(n)==null,"first setup needs confirmation");check(setup.confirming(),"confirmation stage");setup.digit(1);setup.digit(2);setup.digit(3);boolean failed=false;try{setup.digit(5);}catch(IllegalArgumentException e){failed=true;}check(failed&&!setup.confirming()&&setup.length()==0,"mismatch resets setup");
    for(int n:new int[]{0,0,0,1})setup.digit(n);setup.digit(0);setup.digit(0);setup.digit(0);check("0001".equals(setup.digit(1)),"confirmed setup");
    File root=Files.createTempDirectory(Paths.get(args[0]),"queue-").toFile(),states=new File(root,"states");
    QueueState.write(states,"first","标题一","waiting","");QueueState.write(states,"first","标题一","failed","离线");
    check("failed".equals(QueueState.list(states).get(0).getProperty("state")),"offline state survives reread");
    CountDownLatch started=new CountDownLatch(1),acknowledge=new CountDownLatch(1),done=new CountDownLatch(1);AtomicInteger runs=new AtomicInteger();DispatchLoop loop=new DispatchLoop();
    Runnable drain=()->{try{if(runs.incrementAndGet()==1){QueueState.write(states,"first","标题一","sending","");started.countDown();check(acknowledge.await(5,TimeUnit.SECONDS),"slow server released");QueueState.write(states,"first","标题一","saved","");}else{QueueState.write(states,"second","标题二","saved","");done.countDown();}}catch(Exception e){throw new RuntimeException(e);}};
    loop.request(drain);check(started.await(5,TimeUnit.SECONDS),"first send started");
    QueueState.write(states,"second","标题二","waiting","");loop.request(drain);
    List<Properties> before=QueueState.list(states);check(before.stream().anyMatch(p->"second".equals(p.getProperty("id"))&&"waiting".equals(p.getProperty("state"))),"second item accepted while first waits for acknowledgement");
    check(before.stream().noneMatch(p->"saved".equals(p.getProperty("state"))),"no success shown before acknowledgement");
    acknowledge.countDown();check(done.await(5,TimeUnit.SECONDS),"enqueue during send is not lost");
    check(QueueState.list(states).stream().allMatch(p->"saved".equals(p.getProperty("state"))),"both completed");
    QueueState.write(states,"unfinished","不能丢","failed","待重试");for(int i=0;i<25;i++)QueueState.write(states,"sent"+i,"完成","saved","");QueueState.prune(states);
    List<Properties> finalRows=QueueState.list(states);check(finalRows.stream().filter(p->"saved".equals(p.getProperty("state"))).count()==20,"completed history bounded");check(finalRows.stream().anyMatch(p->"unfinished".equals(p.getProperty("id"))),"pruning never removes failed item");
    System.out.println("PASS: four-dot keypad setup/confirmation/delete; offline receipt persistence; enqueue while prior send waits; completion timing; no lost dispatch; completed history pruning preserves failures");
  }
}
