package com.contentmover.mobile;
import java.io.*;
import java.util.*;

/** Small durable receipts; image payloads are never read by the queue UI. */
public final class QueueState {
  public static synchronized void remove(File folder,String id)throws IOException {
    if(!id.matches("[a-zA-Z0-9-]+"))throw new IOException("Invalid queue ID");
    for(String suffix:new String[]{".properties",".tmp"}){File file=new File(folder,id+suffix);if(file.exists()&&!file.delete())throw new IOException("无法删除迁移记录，请重试");}
  }
  public static synchronized void write(File folder,String id,String title,String state,String detail)throws IOException {
    if(!id.matches("[a-zA-Z0-9-]+"))throw new IOException("Invalid queue ID");
    folder.mkdirs();File dest=new File(folder,id+".properties"),temp=new File(folder,id+".tmp");Properties p=new Properties();
    if(dest.exists())try(InputStream input=new FileInputStream(dest)){p.load(input);}
    if(!p.containsKey("created"))p.setProperty("created",Long.toString(System.currentTimeMillis()));
    p.setProperty("id",id);p.setProperty("title",title);p.setProperty("state",state);p.setProperty("detail",detail);
    try(FileOutputStream out=new FileOutputStream(temp)){p.store(out,null);out.getFD().sync();}
    java.nio.file.Files.move(temp.toPath(),dest.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }
  public static synchronized List<Properties> list(File folder)throws IOException {
    ArrayList<Properties> rows=new ArrayList<>();File[] files=folder.listFiles((d,n)->n.endsWith(".properties"));
    if(files!=null)for(File file:files){Properties p=new Properties();try(InputStream input=new FileInputStream(file)){p.load(input);}rows.add(p);}
    rows.sort((a,b)->Long.compare(Long.parseLong(b.getProperty("created","0")),Long.parseLong(a.getProperty("created","0"))));return rows;
  }
  public static synchronized void prune(File folder)throws IOException {
    int sent=0;for(Properties p:list(folder))if("saved".equals(p.getProperty("state"))&&++sent>20)new File(folder,p.getProperty("id")+".properties").delete();
  }
}
