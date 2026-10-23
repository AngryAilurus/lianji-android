package cn.lianji.training;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

/** GitHub releases contain only app metadata; training data never leaves this device. */
final class AppUpdates {
    static final String REPO="https://github.com/AngryAilurus/lianji-android";
    static final String API="https://api.github.com/repos/AngryAilurus/lianji-android/releases/latest";
    final MainActivity a;
    volatile boolean closed;
    boolean busy, awaitingPermission;
    AlertDialog dialog;
    AppUpdates(MainActivity activity){a=activity;
        try{if(a.getPreferences(0).getLong("pendingUpdate",0)<=installed()){a.getPreferences(0).edit().remove("pendingUpdate").apply();apk().delete();}}catch(Exception ignored){}
    }
    long installed() throws Exception {return a.getPackageManager().getPackageInfo(a.getPackageName(),0).versionCode;}
    void automatic(){long last=a.getPreferences(0).getLong("updateCheck",0);if(System.currentTimeMillis()-last>86400000L)check(false);}
    void check(boolean manual){
        if(busy){if(manual)a.toast("正在检查或下载更新");return;}busy=true;
        if(manual&&a.getPreferences(0).getLong("pendingUpdate",0)>0&&apk().exists()){busy=false;install();return;}
        if(manual)a.toast("正在检查更新…");
        new Thread(()->{try{
            HttpURLConnection c=connect(API);String json;try{if(c.getResponseCode()!=200)throw new IOException("暂时无法获取版本信息");json=Store.read(c.getInputStream(),1024*1024);}finally{c.disconnect();}
            JSONObject release=new JSONObject(json);Release info=parse(release);
            a.getPreferences(0).edit().putLong("updateCheck",System.currentTimeMillis()).apply();
            long current=installed();post(()->{busy=false;if(info.code<=current){if(manual)a.toast("当前已是最新版本");return;}dialog=new AlertDialog.Builder(a).setTitle("发现新版本 "+info.version).setMessage(info.notes+"\n\n安装包 "+String.format(Locale.ROOT,"%.2f",info.size/1048576.0)+" MB\n覆盖安装将保留训练计划和记录。").setNegativeButton("稍后",null).setPositiveButton("立即更新",(d,w)->download(info)).show();});
        }catch(Exception e){post(()->{busy=false;if(manual)new AlertDialog.Builder(a).setTitle("检查更新失败").setMessage("请检查网络后重试。也可以打开发布页面手动下载。").setNegativeButton("关闭",null).setPositiveButton("打开发布页面",(d,w)->a.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(REPO+"/releases/latest")))).show();});}},"update-check").start();
    }
    static final class Release {int code;String version,notes,url,hash;long size;}
    static Release parse(JSONObject r)throws Exception {
        if(r.optBoolean("draft")||r.optBoolean("prerelease"))throw new IOException("非正式版本");
        Release v=new Release();v.version=r.getString("tag_name");
        // versionCode is published explicitly in release notes, independent of display version.
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("<!-- versionCode: (\\d+) -->").matcher(r.optString("body"));
        if(!m.find())throw new IOException("版本缺少更新标识");v.code=Integer.parseInt(m.group(1));
        v.notes=r.optString("body").replaceAll("(?s)<!--.*?-->","").trim();if(v.notes.length()>3000)v.notes=v.notes.substring(0,3000);
        JSONArray assets=r.getJSONArray("assets");for(int i=0;i<assets.length();i++){JSONObject asset=assets.getJSONObject(i);if(!asset.optString("name").endsWith(".apk"))continue;
            v.url=asset.getString("browser_download_url");v.size=asset.getLong("size");v.hash=asset.optString("digest");break;}
        if(v.url==null||!v.url.startsWith(REPO+"/releases/download/")||v.size<=0||v.size>100*1024*1024||!v.hash.matches("sha256:[a-fA-F0-9]{64}"))throw new IOException("安装包信息无效");return v;
    }
    static HttpURLConnection connect(String address)throws IOException {
        URL url=new URL(address);if(!"https".equals(url.getProtocol()))throw new IOException("需要安全连接");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","Lianji-Android");c.setRequestProperty("Accept","application/vnd.github+json");return c;
    }
    void post(Runnable r){a.handler.post(()->{if(!closed&&!a.isFinishing())r.run();});}
    File apk(){return new File(a.getCacheDir(),"update.apk");}
    void download(Release info){if(busy)return;busy=true;
        ProgressDialog progress=new ProgressDialog(a);progress.setTitle("下载更新 "+info.version);progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);progress.setMax(100);progress.setCancelable(false);progress.setButton(DialogInterface.BUTTON_NEGATIVE,"取消",(d,w)->{cancelled=true;});dialog=progress;cancelled=false;progress.show();
        new Thread(()->{File partial=null;try{partial=File.createTempFile("update-",".part",a.getCacheDir());
            HttpURLConnection c=connect(info.url);try{if(c.getResponseCode()!=200)throw new IOException("下载失败");try(InputStream in=c.getInputStream()){transfer(in,partial,info,()->closed||cancelled,p->post(()->progress.setProgress(p)));}
            }finally{c.disconnect();}
            verify(partial,info.code);if(closed||cancelled)throw new InterruptedIOException();if(apk().exists()&&!apk().delete())throw new IOException("无法替换旧安装包");if(!partial.renameTo(apk()))throw new IOException("无法保存安装包");
            a.getPreferences(0).edit().putLong("pendingUpdate",info.code).apply();post(()->{busy=false;progress.dismiss();install();});
        }catch(Exception e){if(partial!=null)partial.delete();post(()->{busy=false;progress.dismiss();if(!cancelled)new AlertDialog.Builder(a).setTitle("更新未完成").setMessage("下载或校验失败，请检查网络后重试。原有应用和训练数据未受影响。").setPositiveButton("知道了",null).show();});}},"update-download").start();
    }
    interface Cancel {boolean stopped();}
    interface Progress {void changed(int percent);}
    static void transfer(InputStream in,File target,Release info,Cancel cancel,Progress progress)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;int last=-1;
        try(FileOutputStream out=new FileOutputStream(target)){byte[] buf=new byte[16384];int n;
            while((n=in.read(buf))!=-1){if(cancel.stopped())throw new InterruptedIOException();total+=n;
                if(total>info.size)throw new IOException("安装包大小不符");out.write(buf,0,n);digest.update(buf,0,n);
                int pct=(int)(total*100/info.size);if(pct!=last){last=pct;progress.changed(pct);}}
            out.getFD().sync();}
        if(total!=info.size||!hex(digest.digest()).equalsIgnoreCase(info.hash.substring(7)))throw new IOException("安装包校验失败");
    }
    volatile boolean cancelled;
    static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte x:bytes)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}
    void verify(File file,long expected)throws Exception {
        PackageManager pm=a.getPackageManager();PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNATURES),current=pm.getPackageInfo(a.getPackageName(),PackageManager.GET_SIGNATURES);
        if(archive==null||!a.getPackageName().equals(archive.packageName)||archive.versionCode!=expected||archive.versionCode<=current.versionCode||archive.signatures==null||!Arrays.equals(archive.signatures,current.signatures))throw new IOException("应用签名或版本不符");
    }
    void install(){try{long code=a.getPreferences(0).getLong("pendingUpdate",0);verify(apk(),code);
        if(!a.getPackageManager().canRequestPackageInstalls()){dialog=new AlertDialog.Builder(a).setTitle("允许安装更新").setMessage("请在系统设置中允许练记安装应用，然后返回继续安装。").setNegativeButton("稍后",null).setPositiveButton("去设置",(d,w)->{awaitingPermission=true;a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())));}).show();return;}
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://"+a.getPackageName()+".updates/update.apk"),"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);a.startActivity(intent);
    }catch(Exception e){a.getPreferences(0).edit().remove("pendingUpdate").apply();a.toast("无法安装更新，请重新检查更新并下载");}}
    void resume(){if(awaitingPermission){awaitingPermission=false;if(a.getPackageManager().canRequestPackageInstalls())install();}}
    void close(){closed=true;cancelled=true;if(dialog!=null)dialog.dismiss();}
}
