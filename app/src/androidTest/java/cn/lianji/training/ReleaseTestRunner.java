package cn.lianji.training;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Validates clean installation and explicit plan import using invented records only. */
public final class ReleaseTestRunner extends Instrumentation {
    int checks;
    void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();Store store=null;File plan=null;try{
        c.deleteDatabase("release-tests.db");store=new Store(c,"release-tests.db");store.seed();
        check(store.list("session").isEmpty()&&store.list("metric").isEmpty()&&store.list("legacy").isEmpty(),"fresh install empty");
        plan=PlanTests.file(c,PlanXlsx.write(TestFixtures.sessions(),false));
        List<PlanXlsx.Table> tables=PlanXlsx.read(plan);check(tables.size()==1,"one plan sheet");PlanXlsx.Table table=tables.get(0);
        check(table.errors.isEmpty(),"exported synthetic plan parses without errors");check(table.sessions.size()==3,"synthetic training days parsed");
        check(table.rows==4,"synthetic exercises parsed");check(table.records==4,"synthetic recorded sets parsed");
        PlanOps.ImportBatch batch=PlanOps.prepare(store,table.sessions,true);check(batch.added==3,"all days import into empty install");store.savePlans(batch.sessions,true);
        check(store.list("session").size()==3,"all imported sessions persist");int actual=0;for(JSONObject day:store.list("session")){
            JSONArray es=day.getJSONArray("exercises");for(int i=0;i<es.length();i++){JSONArray sets=es.getJSONObject(i).getJSONArray("actual");for(int j=0;j<sets.length();j++)if(sets.getJSONObject(j).optBoolean("done"))actual++;}}
        check(actual==4,"all actual records imported");String before=store.export().getJSONArray("sessions").toString();store.seed();
        check(store.export().getJSONArray("sessions").toString().equals(before),"initialization never clears existing plans");
        PlanOps.ImportBatch repeated=PlanOps.prepare(store,table.sessions,true);check(repeated.added==0,"reimport adds no duplicates");store.savePlans(repeated.sessions,true);
        check(store.list("session").size()==3,"reimport preserves session count");
        c.deleteDatabase("release-plan-only.db");Store planOnly=new Store(c,"release-plan-only.db");try{
            planOnly.seed();PlanOps.ImportBatch clean=PlanOps.prepare(planOnly,table.sessions,false);planOnly.savePlans(clean.sessions,true);
            for(JSONObject day:planOnly.list("session")){JSONArray es=day.getJSONArray("exercises");for(int i=0;i<es.length();i++)check(!PlanOps.hasActual(es.getJSONObject(i)),"plan-only import leaves actuals blank");}
        }finally{planOnly.close();c.deleteDatabase("release-plan-only.db");}
        result.putString("stream","PASS: "+checks+" checks: empty install, synthetic XLSX import, plan-only import, reimport and upgrade retention.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}finally{if(plan!=null)plan.delete();if(store!=null)store.close();c.deleteDatabase("release-tests.db");}}
}
