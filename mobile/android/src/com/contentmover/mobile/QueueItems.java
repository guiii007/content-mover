package com.contentmover.mobile;
import java.io.*;
import java.util.HashMap;

/** Serialize removal, upload registration and receipts so deleted items cannot reappear. */
public final class QueueItems {
  private final File pending,states;
  private final HashMap<String,ClipHttp.Cancellation> active=new HashMap<>();
  public QueueItems(File pending,File states){this.pending=pending;this.states=states;}
  private File payload(String id)throws IOException {if(!id.matches("[a-zA-Z0-9-]+"))throw new IOException("Invalid queue ID");return new File(pending,id+".json");}
  public synchronized ClipHttp.Cancellation begin(String id)throws IOException {
    if(!payload(id).exists())return null;
    ClipHttp.Cancellation token=new ClipHttp.Cancellation();active.put(id,token);return token;
  }
  public synchronized void finish(String id,ClipHttp.Cancellation token){if(active.get(id)==token)active.remove(id);}
  public synchronized void mark(String id,String title,String state,String detail)throws IOException {
    if(!payload(id).exists())return;
    QueueState.write(states,id,title,state,detail);QueueState.prune(states);
  }
  public synchronized boolean saved(String id,String title)throws IOException {
    File file=payload(id);if(!file.exists())return false;
    if(!file.delete())throw new IOException("无法更新待发送列表");
    QueueState.write(states,id,title,"saved","");QueueState.prune(states);return true;
  }
  public synchronized void delete(String id)throws IOException {
    File file=payload(id);
    if(file.exists()&&!file.delete())throw new IOException("无法删除，请稍后重试");
    ClipHttp.Cancellation token=active.get(id);if(token!=null)token.cancel();
    QueueState.remove(states,id);
  }
}
