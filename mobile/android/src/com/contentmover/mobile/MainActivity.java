package com.contentmover.mobile;
import android.app.*;import android.app.job.*;import android.content.*;import android.net.Uri;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;

public class MainActivity extends Activity {
  EditText title,text,note,source;TextView status,pictures;Button send;LinearLayout gallery,queueList;final Handler queueHandler=new Handler();boolean queueVisible;final Runnable queueRefresh=new Runnable(){public void run(){if(!queueVisible)return;refreshQueue();queueHandler.postDelayed(this,1000);}};CheckBox shrink;int previewVersion;ArrayList<Uri> incoming=new ArrayList<>();volatile boolean busy;boolean shared;
  int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  TextView label(LinearLayout root,String value){TextView view=new TextView(this);view.setText(value);view.setTextSize(16);view.setPadding(0,dp(12),0,dp(6));root.addView(view);return view;}
  EditText field(LinearLayout root,String hint,int lines){EditText view=new EditText(this);view.setHint(hint);view.setTextSize(16);view.setMinLines(lines);view.setGravity(Gravity.TOP);if(lines==1)view.setSingleLine(true);root.addView(view,new LinearLayout.LayoutParams(-1,-2));return view;}
  Button button(LinearLayout root,String value,Runnable action){Button view=new Button(this);view.setText(value);root.addView(view,new LinearLayout.LayoutParams(-1,dp(48)));view.setOnClickListener(v->{if(!busy)action.run();});return view;}
  @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().setStatusBarColor(0xff2159a5);ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(22),dp(26),dp(22),dp(26));root.setBackgroundColor(0xfff7f9fc);scroll.addView(root);setContentView(scroll);scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});label(root,"内容迁移 · 手机 → 电脑").setTextSize(25);label(root,"同一 Wi-Fi 可直接发送；远程发送需两端连接 Tailscale。");button(root,"输入 4 位密码连接电脑",()->requireUnlock(()->{update();Transfer.dispatch(this);Toast.makeText(this,"已连接，可以发送内容",Toast.LENGTH_SHORT).show();}));button(root,"扫码连接 / 更换电脑",()->{if(PinVault.configured(this))requireUnlock(()->startActivityForResult(new Intent(this,ScanActivity.class),10));else startActivityForResult(new Intent(this,ScanActivity.class),10);});button(root,"忘记密码 / 重新配对",()->new AlertDialog.Builder(this).setTitle("重新配对").setMessage("将清除本机配对与密码，需要重新扫描电脑二维码。待发送内容和电脑文件会保留。").setNegativeButton("取消",null).setPositiveButton("重新配对",(d,w)->{PinVault.reset(this);update();}).show());status=label(root,"");label(root,"标题");title=field(root,"手机摘录",1);label(root,"原文");text=field(root,"选中文字后分享，也可在这里粘贴",4);label(root,"备注（支持 Markdown）");note=field(root,"为什么保存这段内容？",3);label(root,"出处（可选）");source=field(root,"网页链接或来源名称",1);pictures=label(root,"没有图片");gallery=new LinearLayout(this);gallery.setOrientation(1);root.addView(gallery);shrink=new CheckBox(this);shrink.setText("缩小图片再发送（最长边 2560，JPEG 画质 90%）");root.addView(shrink);label(root,"默认保留原图。大图可勾选缩小；缩小会降低分辨率与画质。").setTextSize(13);button(root,"添加截图 / 图片",()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(intent,11);});send=button(root,"发送到电脑",()->saveAndSend());label(root,"迁移列表");queueList=new LinearLayout(this);queueList.setOrientation(1);root.addView(queueList);button(root,"重试待发送内容",()->retry());label(root,"电脑未连接时，内容会先保存在手机。远程发送会使用移动流量。\n最多 10 张图、图片合计 16 MB；文字不会自动改写。\n文件按电脑收到内容的日期归档。").setTextSize(13);
    Intent intent=getIntent();String action=intent.getAction();shared=Intent.ACTION_PROCESS_TEXT.equals(action) || Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action);CharSequence selected=Intent.ACTION_PROCESS_TEXT.equals(action)?intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT):intent.getCharSequenceExtra(Intent.EXTRA_TEXT);if(selected!=null)text.setText(selected);CharSequence givenTitle=intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT);if(givenTitle==null)givenTitle=intent.getCharSequenceExtra(Intent.EXTRA_TITLE);if(givenTitle!=null)title.setText(givenTitle);if(Intent.ACTION_SEND.equals(action)){Uri image=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(image!=null)incoming.add(image);}if(Intent.ACTION_SEND_MULTIPLE.equals(action)){ArrayList<Uri> list=intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);if(list!=null)incoming.addAll(list);}if(saved!=null){title.setText(saved.getString("title"));text.setText(saved.getString("text"));note.setText(saved.getString("note"));source.setText(saved.getString("source"));ArrayList<Uri> restored=saved.getParcelableArrayList("images");if(restored!=null)incoming=restored;shrink.setChecked(saved.getBoolean("shrink"));}update();
  }
  void update(){showImages();pictures.setText(incoming.size()+" 张图片，将按添加顺序保存");updateStatus();}
  void updateStatus(){String host=getSharedPreferences("pair",0).getString("host","");status.setText((PinVault.configured(this)?(PinVault.unlocked()?"电脑连接已就绪\n":"请输入 4 位密码连接电脑\n"):"配对后请设置 4 位密码\n")+(host.isEmpty()?"尚未配对电脑":(getSharedPreferences("pair",0).getBoolean("remote",false)?"远程电脑：":"局域网电脑：")+host)+"\n待发送："+Transfer.pending(this).length+" 条");}
  @Override protected void onResume(){super.onResume();queueVisible=true;if(status!=null)update();queueHandler.removeCallbacks(queueRefresh);queueHandler.post(queueRefresh);}
  @Override protected void onPause(){queueVisible=false;queueHandler.removeCallbacks(queueRefresh);super.onPause();}
  @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("title",title.getText().toString());state.putString("text",text.getText().toString());state.putString("note",note.getText().toString());state.putString("source",source.getText().toString());state.putParcelableArrayList("images",incoming);state.putBoolean("shrink",shrink.isChecked());}
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK || data==null)return;if(request==10){try{Transfer.pair(this,data.getStringExtra("pair"));update();if(!PinVault.configured(this))requireUnlock(()->{update();message("密码已设置，配对信息已保留，可以发送内容。");});else Toast.makeText(this,"配对已保存，可以发送内容",Toast.LENGTH_SHORT).show();}catch(Exception e){message(e.getMessage());}}if(request==11){if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)incoming.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)incoming.add(data.getData());if(incoming.size()>10){incoming=new ArrayList<>(incoming.subList(0,10));message("最多添加 10 张图片");}update();}}
  void schedule(){JobInfo job=new JobInfo.Builder(47831,new ComponentName(this,SendJob.class)).setRequiredNetworkType(getSharedPreferences("pair",0).getBoolean("remote",false)?JobInfo.NETWORK_TYPE_ANY:JobInfo.NETWORK_TYPE_UNMETERED).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build();((JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE)).schedule(job);}
  void saveAndSend(){
    if(busy)return;if(!PinVault.unlocked()){requireUnlock(()->saveAndSend());return;}
    if(text.getText().toString().trim().isEmpty()&&incoming.isEmpty()){message("请填写文字或添加图片");return;}
    if(incoming.size()>10){message("最多添加 10 张图片");return;}
    final String original=text.getText().toString(),heading=title.getText().toString(),remark=note.getText().toString(),origin=source.getText().toString();
    final boolean optimize=shrink.isChecked();final ArrayList<Uri> images=new ArrayList<>(incoming);
    busy=true;setInputsEnabled(false);send.setText("正在保存到列表…");
    new Thread(()->{boolean queued=false;try{
      if(original.length()>200000||remark.length()>200000||heading.length()>1000||origin.length()>4000)throw new IOException("文字过长");
      JSONObject clip=new JSONObject().put("Id",UUID.randomUUID().toString().replace("-","")).put("Text",original).put("Title",heading).put("Note",remark).put("Source",origin);
      JSONArray attachments=new JSONArray();long total=0;
      for(int i=0;i<images.size();i++){byte[] bytes=ImageAttachments.prepare(this,images.get(i),optimize,i+1);total+=bytes.length;if(total>ImageAttachments.TOTAL_LIMIT)throw new IOException("图片合计超过 16 MB，请勾选缩小或移除部分图片。");attachments.put(new JSONObject().put("Data",Transfer.encode(bytes)));}
      clip.put("Images",attachments);Transfer.enqueue(this,clip);queued=true;
      runOnUiThread(()->{busy=false;setInputsEnabled(true);send.setText("发送到电脑");title.setText("");text.setText("");note.setText("");source.setText("");incoming.clear();update();refreshQueue();Toast.makeText(this,"已加入列表，可以继续写下一条",Toast.LENGTH_SHORT).show();});
      try{schedule();}catch(Exception ignored){}Transfer.dispatch(this);
    }catch(Exception|OutOfMemoryError e){final boolean retained=queued;runOnUiThread(()->{busy=false;setInputsEnabled(true);send.setText("发送到电脑");if(retained){refreshQueue();Toast.makeText(this,"内容已在列表中，请稍后重试",Toast.LENGTH_LONG).show();}else message("尚未保存，内容仍在当前页面。\n"+e.getMessage());});}}).start();
  }
  void retry(){if(busy)return;requireUnlock(()->{schedule();Transfer.dispatch(this);Toast.makeText(this,"正在后台重试，可以继续编辑",Toast.LENGTH_SHORT).show();refreshQueue();});}
  String queueFingerprint="";
  void refreshQueue(){
    if(queueList==null)return;updateStatus();
    try{
      int shown=0;java.util.List<java.util.Properties> rows=QueueState.list(new File(getFilesDir(),"queue-state"));
      String fingerprint=rows.toString()+Transfer.sending.get()+Transfer.pending(this).length;if(fingerprint.equals(queueFingerprint))return;queueList.removeAllViews();
      for(java.util.Properties row:rows){if(shown++==12)break;
        String state=row.getProperty("state","waiting"),name=row.getProperty("title","手机摘录");
        String caption="saved".equals(state)?"电脑已保存":"sending".equals(state)&&Transfer.sending.get()?"正在迁移…":"failed".equals(state)?"等待重试":"等待迁移";
        TextView item=new TextView(this);item.setText(name+"\n"+caption+("failed".equals(state)?" · "+row.getProperty("detail",""):""));item.setTextSize(14);item.setPadding(dp(12),dp(10),dp(12),dp(10));item.setTextColor("saved".equals(state)?0xff24754c:"failed".equals(state)?0xffad4d25:0xff2159a5);
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(0xffffffff);background.setCornerRadius(dp(10));LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setBackground(background);
        card.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        if(!"saved".equals(state)){
          String id=row.getProperty("id");Button remove=new Button(this);remove.setText("删除");remove.setTextSize(13);remove.setTextColor(0xffad4d25);remove.setContentDescription("删除待发送内容："+name);
          LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(dp(64),dp(48));action.rightMargin=dp(6);card.addView(remove,action);
          remove.setOnClickListener(v->confirmDelete(id,name));
        }
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.bottomMargin=dp(6);queueList.addView(card,layout);
      }
      if(rows.isEmpty())label(queueList,Transfer.pending(this).length>0?"已有待发送内容，连接后点重试":"发送后会显示在这里；可继续编辑下一条。");
      else if(rows.size()>12)label(queueList,"显示最近 12 条；其余待发送内容仍保留在手机。").setTextSize(12);
      queueFingerprint=fingerprint;
    }catch(Exception e){queueFingerprint="";queueList.removeAllViews();label(queueList,"列表暂时无法读取，待发送内容仍保留。");}
  }
  void confirmDelete(String id,String name){
    new AlertDialog.Builder(this).setTitle("删除这条迁移内容？").setMessage(name+"\n将删除手机中的待发送文字和图片，并停止重试。如果电脑已经收到，不会删除电脑文件。")
      .setNegativeButton("取消",null).setPositiveButton("删除",(dialog,which)->{
        new Thread(()->{try{Transfer.delete(this,id);runOnUiThread(()->{refreshQueue();Toast.makeText(this,"已删除，不再重试",Toast.LENGTH_SHORT).show();});}
          catch(Exception e){runOnUiThread(()->message(e.getMessage()));}},"content-mover-delete").start();
      }).show();
  }
  void setInputsEnabled(boolean enabled){send.setEnabled(enabled);title.setEnabled(enabled);text.setEnabled(enabled);note.setEnabled(enabled);source.setEnabled(enabled);shrink.setEnabled(enabled);}
  void showImages(){
    final int version=++previewVersion;gallery.removeAllViews();
    for(int i=0;i<incoming.size();i++){
      final int index=i;final Uri uri=incoming.get(i);
      LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(0,dp(8),0,dp(8));gallery.addView(card);
      TextView detail=label(card,"第 "+(i+1)+" 张 · 正在读取预览…");
      ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setContentDescription("第 "+(i+1)+" 张图片预览");card.addView(image,new LinearLayout.LayoutParams(-1,dp(160)));
      button(card,"移除这张图片",()->{if(version==previewVersion){incoming.remove(index);update();}});
      new Thread(()->{
        android.graphics.Bitmap bitmap=null;String description;boolean oversized=false;
        try{long bytes=ImageAttachments.sourceSize(this,uri);android.graphics.BitmapFactory.Options info=ImageAttachments.bounds(this,uri);bitmap=ImageAttachments.thumbnail(this,uri);description="第 "+(index+1)+" 张 · "+ImageAttachments.size(bytes)+" · "+info.outWidth+" × "+info.outHeight;oversized=bytes>ImageAttachments.SINGLE_LIMIT || (long)info.outWidth*info.outHeight>24000000;if(oversized)description+="\n原图超限：请勾选缩小或移除";}
        catch(Exception | OutOfMemoryError error){description="第 "+(index+1)+" 张预览失败，请重新选择图片";}
        final android.graphics.Bitmap preview=bitmap;final String infoText=description;final boolean warning=oversized;
        runOnUiThread(()->{if(version!=previewVersion || isDestroyed()){if(preview!=null)preview.recycle();return;}detail.setText(infoText);if(warning)detail.setTextColor(0xffb33a22);if(preview!=null)image.setImageBitmap(preview);});
      }).start();
    }
  }
  @Override protected void onDestroy(){previewVersion++;super.onDestroy();}
  void requireUnlock(Runnable after){
    if(PinVault.unlocked()){after.run();return;}
    if(!PinVault.paired(this)){message("首次使用请先扫码连接电脑，之后只需输入 4 位密码。");return;}
    final boolean setup=!PinVault.configured(this);final PinEntry entry=new PinEntry(setup);
    Dialog dialog=new Dialog(this);ScrollView scroll=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPadding(dp(24),dp(24),dp(24),dp(16));box.setBackgroundColor(0xfff7f9fc);scroll.addView(box);dialog.setContentView(scroll);
    TextView heading=label(box,setup?"设置 4 位密码":"输入 4 位密码");heading.setGravity(Gravity.CENTER);heading.setTextSize(22);heading.setTextColor(0xff2159a5);
    TextView dots=label(box,"○   ○   ○   ○");dots.setTextSize(28);dots.setGravity(Gravity.CENTER);dots.setTextColor(0xff2159a5);
    TextView hint=label(box,"首次扫码后，以后输入密码即可连接");hint.setTextSize(13);hint.setGravity(Gravity.CENTER);
    final Runnable render=()->{StringBuilder value=new StringBuilder();for(int i=0;i<4;i++)value.append(i<entry.length()?"●   ":"○   ");dots.setText(value.toString().trim());dots.setContentDescription("已输入 "+entry.length()+" 位密码");heading.setText(setup?(entry.confirming()?"再次输入密码":"设置 4 位密码"):"输入 4 位密码");};
    for(int r=0;r<4;r++){LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER);box.addView(line);
      for(int c=0;c<3;c++){final int number=r<3?r*3+c+1:c==1?0:-1;final boolean cancel=r==3&&c==0,erase=r==3&&c==2;
        TextView key=new TextView(this);key.setGravity(Gravity.CENTER);key.setText(cancel?"取消":erase?"删除":Integer.toString(number));key.setContentDescription(cancel?"取消输入":erase?"删除一位":Integer.toString(number));key.setTextSize(number<0?15:28);key.setTextColor(0xff2159a5);
        android.graphics.drawable.GradientDrawable face=new android.graphics.drawable.GradientDrawable();face.setShape(android.graphics.drawable.GradientDrawable.OVAL);face.setColor(0xffffffff);face.setStroke(dp(1),0xffa8c1e3);
        key.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0xffd5e4f7),face,null));
        int size=Math.min(dp(72),(getResources().getDisplayMetrics().widthPixels-dp(100))/3);LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(size,size);layout.setMargins(dp(5),dp(5),dp(5),dp(5));line.addView(key,layout);
        key.setOnClickListener(v->{if(busy)return;if(cancel){dialog.dismiss();return;}if(erase){entry.delete();render.run();return;}
          String complete;try{complete=entry.digit(number);}catch(IllegalArgumentException e){entry.restart();hint.setText(e.getMessage());render.run();return;}render.run();if(complete==null)return;
          final String password=complete;busy=true;dialog.setCancelable(false);hint.setText("正在连接…");
          new Thread(()->{String error=null;try{if(setup)PinVault.setup(this,password);else PinVault.unlock(this,password);}catch(Exception e){error=e.getMessage();}
            final String failure=error;runOnUiThread(()->{busy=false;if(isFinishing()||isDestroyed()){dialog.dismiss();return;}dialog.setCancelable(true);update();if(failure!=null){entry.restart();hint.setText(failure);render.run();}else{dialog.dismiss();after.run();}});
          }).start();
        });
      }
    }
    dialog.show();dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(Math.min(dp(340),getResources().getDisplayMetrics().widthPixels-dp(32)),WindowManager.LayoutParams.WRAP_CONTENT);
  }
  void message(String value){if(isFinishing() || isDestroyed())return;new AlertDialog.Builder(this).setTitle("内容迁移").setMessage(value).setPositiveButton("好",null).show();}
}
