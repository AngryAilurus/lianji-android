package cn.lianji.training;
import android.app.*;
import android.os.*;
import android.content.*;
import org.json.*;

public final class TestRunner extends Instrumentation {
    int checks=0;
    void check(boolean truth,String label){if(!truth)throw new AssertionError(label);checks++;}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    @Override public void onStart(){Bundle result=new Bundle();Store data=null,copy=null;Context ctx=getTargetContext();try{
        ctx.deleteDatabase("lianji-test.db");ctx.deleteDatabase("lianji-test-copy.db");data=new Store(ctx,"lianji-test.db");data.seed();
        check(data.list("session").isEmpty(),"fresh install has no training plan");
        check(data.list("metric").isEmpty()&&data.list("legacy").isEmpty(),"fresh install has no measurements or history");
        data.seed();check(data.list("session").isEmpty(),"repeated launch stays empty before import");
        data.merge(TestFixtures.backup());
        check(data.list("session").size()==3,"three synthetic sessions explicitly imported");
        JSONObject one=data.get("session","101");JSONArray es=one.getJSONArray("exercises");
        check(es.getJSONObject(0).getJSONArray("actual").getJSONObject(0).getString("weight").equals("12.5"),"decimal weight retained");
        check(es.getJSONObject(0).getJSONArray("actual").getJSONObject(0).getString("rir").equals("0"),"RIR zero retained");
        check(es.getJSONObject(0).getString("note").equals("合成\"引号\",逗号"),"quoted note retained");
        check(es.getJSONObject(1).getJSONArray("actual").getJSONObject(0).getString("weight").equals("空杆"),"text weight retained");
        JSONObject carry=data.get("session","102").getJSONArray("exercises").getJSONObject(0);
        check(carry.getJSONArray("actual").length()==3,"extra third set retained");
        check(!carry.getJSONArray("actual").getJSONObject(1).optBoolean("done"),"gap before extra set remains blank");
        check(carry.getJSONArray("actual").getJSONObject(0).getString("quantity").equals("3 趟"),"text quantity retained");
        int legacy=0,done=0;
        for(JSONObject l:data.list("legacy"))legacy+=l.getJSONArray("cells").length();check(legacy==3,"synthetic historical cells retained");
        for(JSONObject session:data.list("session"))for(int i=0;i<session.getJSONArray("exercises").length();i++){
            JSONArray sets=session.getJSONArray("exercises").getJSONObject(i).getJSONArray("actual");
            for(int j=0;j<sets.length();j++)if(sets.getJSONObject(j).optBoolean("done"))done++;
        }
        check(done==4,"all synthetic recorded sets retained");
        JSONObject future=data.get("session","103");future.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(0)
            .put("weight","14.25").put("quantity","11").put("rir","0").put("done",true);data.saveSession(future);
        data.saveMetric(new JSONObject().put("date","2032-04-06").put("weight","71.2").put("waist","81.3").put("note","合成体测"));
        JSONObject originalExport=data.export();data.close();data=new Store(ctx,"lianji-test.db");
        check(data.get("session","103").getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(0).getString("weight").equals("14.25"),"set survives store reopening");
        check(data.get("metric","2032-04-06").getString("waist").equals("81.3"),"metric survives store reopening");
        data.seed();check(data.get("session","103").optLong("updatedAt")>0,"repeat seed never resets edited session");
        copy=new Store(ctx,"lianji-test-copy.db");check(copy.merge(originalExport)==5,"full backup imports sessions, metric and historical sheet");
        check(copy.export().getJSONArray("sessions").toString().equals(originalExport.getJSONArray("sessions").toString()),"backup preserves all session data exactly");
        check(copy.export().getJSONArray("legacy").toString().equals(originalExport.getJSONArray("legacy").toString()),"backup preserves historical cells exactly");
        check(copy.trainingCsv().contains("\"空杆\""),"CSV preserves text weight");
        check(copy.trainingCsv().contains("\"3 趟\""),"CSV preserves text quantity");
        check(copy.trainingCsv().contains("\"合成\"\"引号\"\",逗号\""),"CSV escapes quotes and commas");
        check(copy.trainingCsv().startsWith("\uFEFF"),"CSV supports Excel UTF-8 detection");
        JSONObject local=copy.get("session","103");local.put("name","本机更新版本").put("updatedAt",System.currentTimeMillis()+100000);copy.put("session","103",local);
        copy.merge(originalExport);check(copy.get("session","103").getString("name").equals("本机更新版本"),"older import does not overwrite newer local data");
        String before=copy.export().getJSONArray("sessions").toString();JSONObject broken=Store.cloneJson(originalExport);broken.getJSONArray("sessions").getJSONObject(0).put("date","bad-date");
        boolean rejected=false;try{copy.merge(broken);}catch(Exception expected){rejected=true;}
        check(rejected,"invalid backup rejected before mutation");check(copy.export().getJSONArray("sessions").toString().equals(before),"invalid backup leaves all local sessions intact");
        checks+=new PlanTests().run(this);
        result.putString("stream","PASS: "+checks+" checks. Synthetic-data integrity, XLSX import, duplicate import, conflicts, Excel roundtrip, template, plan editing, archiving, copying, transactional rollback, SQLite persistence and backup tested.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable error){result.putString("stream","FAIL: "+error+"\n");finish(Activity.RESULT_CANCELED,result);}finally{if(data!=null)data.close();if(copy!=null)copy.close();ctx.deleteDatabase("lianji-test.db");ctx.deleteDatabase("lianji-test-copy.db");}}
}
