package cn.lianji.training;
import android.app.Instrumentation;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

final class PlanTests {
    int checks;void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static File file(Context c,byte[] data)throws Exception{File f=File.createTempFile("xlsx-test-",".xlsx",c.getCacheDir());try(FileOutputStream out=new FileOutputStream(f)){out.write(data);}return f;}
    static PlanXlsx.Table read(Context ctx,byte[] bytes)throws Exception{
        File f=file(ctx,bytes);try{List<PlanXlsx.Table> tables=PlanXlsx.read(f);if(tables.size()!=1)throw new AssertionError("expected one synthetic sheet");return tables.get(0);}finally{f.delete();}
    }
    int run(Instrumentation runner)throws Exception{
        Context ctx=runner.getTargetContext();ctx.deleteDatabase("plan-tests.db");Store store=new Store(ctx,"plan-tests.db");try{
            store.seed();store.merge(TestFixtures.backup());
            PlanXlsx.Table actual=read(ctx,PlanXlsx.write(TestFixtures.sessions(),false));
            check(actual.name.equals("训练计划"),"synthetic workbook sheet detected");
            check(actual.errors.isEmpty(),"synthetic workbook has no parse errors: "+actual.errors);
            check(actual.sessions.size()==3,"all synthetic sessions parsed");check(actual.rows==4,"all synthetic exercises parsed");check(actual.records==4,"all synthetic actual sets parsed");
            JSONObject importedCarry=actual.sessions.get(1).getJSONArray("exercises").getJSONObject(0);
            check(importedCarry.getJSONArray("actual").length()==3,"extra actual third set imported");
            check(!importedCarry.getJSONArray("actual").getJSONObject(1).optBoolean("done"),"blank gap in imported sets retained");
            check(Store.str(importedCarry.getJSONArray("actual").getJSONObject(0),"quantity").equals("3 趟"),"Excel text quantity retained");
            check(Store.str(actual.sessions.get(0).getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(0),"rir").equals("0"),"Excel RIR zero retained");
            PlanOps.ImportBatch first=PlanOps.prepare(store,actual.sessions,true);
            check(first.added==0&&first.updated==3,"same workbook merges existing sessions");check(first.conflicts==0,"same actual values do not conflict");
            store.savePlans(first.sessions,true);check(store.get("checkpoint","beforeExcelImport")!=null,"pre-import snapshot saved");
            check(store.list("session").size()==3,"no duplicates after import");
            PlanOps.ImportBatch repeated=PlanOps.prepare(store,actual.sessions,true);store.savePlans(repeated.sessions,true);
            check(store.list("session").size()==3,"repeated import remains idempotent");
            JSONObject local=store.get("session","101");JSONObject set=local.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(0);
            set.put("weight","14.25");store.saveSession(local);PlanOps.ImportBatch conflict=PlanOps.prepare(store,actual.sessions,true);
            check(conflict.conflicts==1,"different existing weight reported as conflict");store.savePlans(conflict.sessions,true);
            check(Store.str(store.get("session","101").getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(0),"weight").equals("14.25"),"import never overwrites existing value");
            JSONObject original=store.get("session","101"),exercise=original.getJSONArray("exercises").getJSONObject(0),fields=Store.cloneJson(exercise);
            String before=exercise.getJSONArray("actual").getJSONObject(0).toString();fields.put("sets",1).put("low",4).put("high",6).put("rir","1").put("rest",47);
            PlanOps.edit(original,0,fields);check(original.getJSONArray("exercises").getJSONObject(0).getInt("sets")==1,"target set count edited");
            check(original.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(0).toString().equals(before),"shrinking plan preserves recorded set");
            fields.put("sets",5);PlanOps.edit(original,0,fields);
            check(original.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").length()==5,"increasing plan creates blank sets");
            check(!original.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(4).optBoolean("done"),"new planned sets are not marked recorded");
            JSONObject replacement=Store.cloneJson(fields).put("name","合成替换器械");PlanOps.edit(original,0,replacement);
            check(Store.str(original.getJSONArray("exercises").getJSONObject(0),"name").equals("合成替换器械"),"replacement exercise inserted");
            check(!PlanOps.hasActual(original.getJSONArray("exercises").getJSONObject(0)),"replacement does not inherit old actuals");
            check(original.getJSONArray("exercises").getJSONObject(1).optBoolean("archived"),"old recorded exercise archived");
            check(Store.str(original.getJSONArray("exercises").getJSONObject(1).getJSONArray("actual").getJSONObject(0),"weight").equals("14.25"),"old weight remains in archived exercise");
            PlanOps.remove(original,1);check(original.getJSONArray("exercises").getJSONObject(1).optBoolean("archived"),"removing logged exercise retains history");
            int size=original.getJSONArray("exercises").length();PlanOps.remove(original,0);check(original.getJSONArray("exercises").length()==size-1,"unrecorded replacement can be removed");
            String firstName=Store.str(original.getJSONArray("exercises").getJSONObject(0),"name");PlanOps.move(original,0,1);
            check(Store.str(original.getJSONArray("exercises").getJSONObject(1),"name").equals(firstName),"reordering keeps exercise identity");
            JSONObject copy=PlanOps.copy(original,9001,"2032-06-18");
            check(copy.getInt("id")==9001&&copy.getString("date").equals("2032-06-18"),"copy creates new date and id");
            check(copy.getJSONArray("exercises").length()==1,"copy excludes archived exercises");
            for(int i=0;i<copy.getJSONArray("exercises").length();i++)check(!PlanOps.hasActual(copy.getJSONArray("exercises").getJSONObject(i)),"copy does not duplicate actual data");
            JSONObject newDay=Store.cloneJson(actual.sessions.get(0)).put("date","2032-05-01").put("name","合成新增训练");
            PlanOps.ImportBatch addition=PlanOps.prepare(store,Collections.singletonList(newDay),false);check(addition.added==1,"new date imports as new training");
            for(int i=0;i<addition.sessions.get(0).getJSONArray("exercises").length();i++)check(!PlanOps.hasActual(addition.sessions.get(0).getJSONArray("exercises").getJSONObject(i)),"plan-only import does not copy actual data");
            store.savePlans(addition.sessions,false);check(store.list("session").size()==4,"new training appended");
            PlanXlsx.Table roundtrip=read(ctx,PlanXlsx.write(store.list("session"),false));
            check(roundtrip.errors.isEmpty(),"exported workbook can be imported");check(roundtrip.sessions.size()==4,"Excel roundtrip preserves session count");
            PlanOps.ImportBatch rt=PlanOps.prepare(store,roundtrip.sessions,true);check(rt.added==0,"roundtrip does not duplicate sessions");check(rt.conflicts==0,"roundtrip preserves updated local records");
            PlanXlsx.Table template=read(ctx,PlanXlsx.write(Collections.emptyList(),true));
            check(template.errors.isEmpty()&&template.rows==2&&template.sessions.size()==1,"template imports as one session and two exercises");
            String prior=store.get("session","101").toString();boolean rejected=false;JSONObject bad=Store.cloneJson(actual.sessions.get(0));
            bad.getJSONArray("exercises").getJSONObject(0).put("sets",0);try{PlanOps.prepare(store,Collections.singletonList(bad),true);}catch(Exception expected){rejected=true;}
            check(rejected,"invalid set count rejected");check(store.get("session","101").toString().equals(prior),"invalid preview never writes local data");
            JSONObject badDate=Store.cloneJson(store.get("session","101")).put("date","not-a-date");rejected=false;
            JSONObject good=Store.cloneJson(store.get("session","102")).put("name","SHOULD_ROLL_BACK");
            try{store.savePlans(Arrays.asList(good,badDate),false);}catch(Exception expected){rejected=true;}
            check(rejected,"invalid batch date rejected");check(!store.get("session","102").getString("name").equals("SHOULD_ROLL_BACK"),"plan save is atomic on error");
            JSONObject withBlank=TestFixtures.exercise("gap-only","合成间隔动作",2,13,17,"米");
            withBlank.getJSONArray("actual").put(TestFixtures.set("8","5 趟","0"));PlanOps.resize(withBlank,1);
            check(withBlank.getJSONArray("actual").length()==3,"occupied extra set after a gap never lost");
            check(!withBlank.getJSONArray("actual").getJSONObject(0).optBoolean("done"),"resize preserves leading blank gap");
            JSONObject finish=new JSONObject().put("exercises",new JSONArray().put(new JSONObject().put("actual",new JSONArray().put(PlanOps.blankSet()).put(PlanOps.blankSet()))).put(new JSONObject().put("actual",new JSONArray().put(PlanOps.blankSet()))));
            check(!PlanOps.completesSession(finish,1,0),"last displayed set cannot finish when earlier sets remain");
            finish.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(0).put("done",true);
            finish.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(1).put("done",true);
            check(PlanOps.completesSession(finish,1,0),"last outstanding set completes session");
            check(!PlanOps.completesSession(finish,-1,-1),"completion preview does not mutate saved data");
            finish.getJSONArray("exercises").getJSONObject(1).getJSONArray("actual").getJSONObject(0).put("done",true);
            check(PlanOps.completesSession(finish,-1,-1),"all active sets completed");
            finish.getJSONArray("exercises").put(new JSONObject().put("archived",true).put("actual",new JSONArray().put(PlanOps.blankSet())));
            check(PlanOps.completesSession(finish,-1,-1),"archived unfinished sets do not block completion");
            check(!PlanOps.completesSession(finish,2,0),"editing archived history cannot complete active training");
            finish.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").put(PlanOps.blankSet());
            check(!PlanOps.completesSession(finish,1,0),"added extra set prevents premature completion");
            check(PlanOps.completesSession(finish,0,2),"out-of-order final outstanding set completes training");
            check(!PlanOps.completesSession(new JSONObject().put("exercises",new JSONArray()),-1,-1),"empty plan is not completed");
            return checks;
        }finally{store.close();ctx.deleteDatabase("plan-tests.db");}
    }
}
