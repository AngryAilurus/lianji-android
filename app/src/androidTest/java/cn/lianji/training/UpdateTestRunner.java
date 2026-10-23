package cn.lianji.training;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;

/** Exercises metadata rejection, truncated/corrupt downloads and signed APK validation. */
public final class UpdateTestRunner extends Instrumentation {
    int checks;
    void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    interface Checked {void run()throws Exception;}
    void rejects(Checked action,String label)throws Exception {boolean rejected=false;try{action.run();}catch(Exception expected){rejected=true;}check(rejected,label);}
    JSONObject metadata()throws Exception{return new JSONObject().put("tag_name","v1.3.1").put("body","<!-- versionCode: 6 -->\n测试更新").put("assets",new JSONArray().put(new JSONObject().put("name","lianji-1.3.1.apk").put("browser_download_url",AppUpdates.REPO+"/releases/download/v1.3.1/lianji-1.3.1.apk").put("size",4).put("digest","sha256:"+AppUpdates.hex(MessageDigest.getInstance("SHA-256").digest(new byte[]{1,2,3,4})))));}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){Bundle result=new Bundle();File file=null;MainActivity a=null;try{
        AppUpdates.Release info=AppUpdates.parse(metadata());check(info.code==6&&info.version.equals("v1.3.1")&&!info.notes.contains("versionCode"),"version code distinct from display name");
        JSONObject noCode=metadata().put("body","无标识");rejects(()->AppUpdates.parse(noCode),"missing version metadata");
        JSONObject beta=metadata().put("prerelease",true);rejects(()->AppUpdates.parse(beta),"ignore prerelease");
        JSONObject draft=metadata().put("draft",true);rejects(()->AppUpdates.parse(draft),"ignore draft");
        JSONObject bad=metadata();bad.getJSONArray("assets").getJSONObject(0).put("digest","");rejects(()->AppUpdates.parse(bad),"missing checksum");
        JSONObject foreign=metadata();foreign.getJSONArray("assets").getJSONObject(0).put("browser_download_url","https://example.com/update.apk");rejects(()->AppUpdates.parse(foreign),"foreign download source");
        JSONObject oversized=metadata();oversized.getJSONArray("assets").getJSONObject(0).put("size",101*1024*1024);rejects(()->AppUpdates.parse(oversized),"oversized package");
        rejects(()->AppUpdates.connect("http://example.com/update.apk"),"reject unencrypted source");
        file=File.createTempFile("update-test-",".apk",getTargetContext().getCacheDir());final File target=file;final int[] progress={0};
        AppUpdates.transfer(new ByteArrayInputStream(new byte[]{1,2,3,4}),file,info,()->false,p->progress[0]=p);check(file.length()==4&&progress[0]==100,"complete download and progress");
        rejects(()->AppUpdates.transfer(new ByteArrayInputStream(new byte[]{1,2}),target,info,()->false,p->{}),"truncated download");
        rejects(()->AppUpdates.transfer(new ByteArrayInputStream(new byte[]{1,2,3,5}),target,info,()->false,p->{}),"corrupt download");
        rejects(()->AppUpdates.transfer(new ByteArrayInputStream(new byte[]{1,2,3,4,5}),target,info,()->false,p->{}),"overlong download");
        rejects(()->AppUpdates.transfer(new ByteArrayInputStream(new byte[]{1,2,3,4}),target,info,()->true,p->{}),"cancelled download");
        a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));AppUpdates updates=new AppUpdates(a);
        File candidate=new File(getTargetContext().getCacheDir(),"candidate.apk");try(InputStream in=getContext().getAssets().open("candidate.apk");OutputStream out=new FileOutputStream(candidate)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        updates.verify(candidate,6);check(true,"higher version signed by same key accepted");
        rejects(()->updates.verify(candidate,7),"metadata version mismatch");
        File installed=new File(getTargetContext().getApplicationInfo().sourceDir);rejects(()->updates.verify(installed,5),"same version refused");
        rejects(()->updates.verify(target,6),"invalid APK rejected");
        File wrong=new File(getTargetContext().getCacheDir(),"wrong.apk");try(InputStream in=getContext().getAssets().open("wrong.apk");OutputStream out=new FileOutputStream(wrong)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        rejects(()->updates.verify(wrong,6),"foreign signing key rejected");
        Uri uri=Uri.parse("content://cn.lianji.training.updates/update.apk");check("application/vnd.android.package-archive".equals(getTargetContext().getContentResolver().getType(uri)),"provider installer MIME type");
        try(Cursor c=getTargetContext().getContentResolver().query(uri,null,null,null,null)){check(c.moveToFirst()&&c.getString(0).endsWith(".apk"),"provider display name");}
        rejects(()->getTargetContext().getContentResolver().openFileDescriptor(Uri.parse("content://cn.lianji.training.updates/../signing.jks"),"r"),"provider blocks unrelated paths");
        rejects(()->getTargetContext().getContentResolver().openFileDescriptor(uri,"w"),"provider read only");
        result.putString("stream","PASS: "+checks+" update checks\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}finally{if(file!=null)file.delete();if(a!=null){final MainActivity activity=a;runOnMainSync(activity::finish);}}}
}
