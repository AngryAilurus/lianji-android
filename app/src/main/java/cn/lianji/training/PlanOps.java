package cn.lianji.training;

import org.json.*;
import java.time.LocalDate;
import java.util.*;

/** Plan mutations never discard recorded sets. Archived items remain in history/backups. */
public final class PlanOps {
    private PlanOps(){}
    static String s(JSONObject o,String key){return Store.str(o,key);}
    public static JSONObject blankSet()throws JSONException{return new JSONObject().put("weight","").put("quantity","").put("rir","").put("done",false);}
    public static boolean recorded(JSONObject set){return set.optBoolean("done")||!s(set,"weight").isEmpty()||!s(set,"quantity").isEmpty()||!s(set,"rir").isEmpty();}
    public static boolean hasActual(JSONObject e){JSONArray a=e.optJSONArray("actual");if(a!=null)for(int i=0;i<a.length();i++)if(recorded(a.optJSONObject(i)))return true;return false;}
    /** Whether all active sets will be done after saving the selected set; -1 checks current state. */
    public static boolean completesSession(JSONObject session,int exerciseIndex,int setIndex){
        JSONArray es=session.optJSONArray("exercises");if(es==null)return false;boolean any=false,selected=exerciseIndex<0;
        for(int i=0;i<es.length();i++){JSONObject e=es.optJSONObject(i);if(e.optBoolean("archived"))continue;JSONArray sets=e.optJSONArray("actual");if(sets==null)continue;
            for(int j=0;j<sets.length();j++){any=true;if(i==exerciseIndex&&j==setIndex){selected=true;continue;}if(!sets.optJSONObject(j).optBoolean("done"))return false;}
        }return any&&selected;
    }
    public static int activeCount(JSONObject session){int n=0;JSONArray es=session.optJSONArray("exercises");for(int i=0;i<es.length();i++)if(!es.optJSONObject(i).optBoolean("archived"))n++;return n;}
    public static void validateExercise(JSONObject e)throws Exception{
        if(s(e,"name").trim().isEmpty()||s(e,"name").length()>100)throw new Exception("动作名称请填写1–100字");
        int sets=e.getInt("sets"),low=e.getInt("low"),high=e.getInt("high"),rest=e.getInt("rest");
        if(sets<1||sets>30)throw new Exception("计划组数请输入1–30");
        if(low<1||high<low||high>10000)throw new Exception("数量下限应大于0，上限不小于下限，最大10000");
        if(rest<0||rest>1800)throw new Exception("休息请输入0–1800秒");
        if(!Arrays.asList("次","秒","米").contains(s(e,"unit")))throw new Exception("单位请选择次、秒或米");
        String rir=s(e,"rir");if(!rir.isEmpty()&&(!rir.matches("\\d+")||Integer.parseInt(rir)>10))throw new Exception("目标RIR请输入0–10，或留空");
    }
    public static JSONObject newExercise(JSONObject fields)throws Exception{
        validateExercise(fields);JSONObject out=Store.cloneJson(fields);out.put("id","plan-"+UUID.randomUUID()).put("archived",false);
        JSONArray actual=new JSONArray();for(int i=0;i<out.getInt("sets");i++)actual.put(blankSet());out.put("actual",actual);return out;
    }
    static void applyFields(JSONObject target,JSONObject fields)throws JSONException{for(String k:new String[]{"name","sets","low","high","unit","rir","rest","load","note"})target.put(k,fields.opt(k));}
    /** Keep the highest occupied index and all its gaps, including RIR=0 and text. */
    public static void resize(JSONObject e,int count)throws JSONException{
        JSONArray old=e.optJSONArray("actual");int keep=count;if(old!=null)for(int i=0;i<old.length();i++)if(recorded(old.optJSONObject(i)))keep=Math.max(keep,i+1);
        JSONArray next=new JSONArray();for(int i=0;i<keep;i++)next.put(old!=null&&i<old.length()?old.getJSONObject(i):blankSet());e.put("actual",next).put("sets",count);
    }
    public static void edit(JSONObject session,int index,JSONObject fields)throws Exception{
        validateExercise(fields);JSONArray es=session.getJSONArray("exercises");JSONObject old=es.getJSONObject(index);
        boolean replacement=!s(old,"name").equals(s(fields,"name"))||!s(old,"unit").equals(s(fields,"unit"));
        if(replacement&&hasActual(old)){
            JSONObject fresh=newExercise(fields);JSONArray next=new JSONArray();for(int i=0;i<es.length();i++){if(i==index){next.put(fresh);old.put("archived",true);}next.put(es.getJSONObject(i));}session.put("exercises",next);
        }else{applyFields(old,fields);resize(old,fields.getInt("sets"));}
    }
    public static void remove(JSONObject session,int index)throws Exception{
        JSONArray es=session.getJSONArray("exercises");JSONObject e=es.getJSONObject(index);
        if(hasActual(e))e.put("archived",true);else es.remove(index);
    }
    public static void move(JSONObject session,int index,int delta)throws JSONException{
        JSONArray es=session.getJSONArray("exercises");int other=index+delta;if(other<0||other>=es.length())return;Object a=es.get(index),b=es.get(other);es.put(index,b);es.put(other,a);
    }
    public static JSONObject copy(JSONObject old,int id,String date)throws Exception{
        LocalDate.parse(date);JSONObject out=Store.cloneJson(old);out.put("id",id).put("date",date).put("updatedAt",0).put("archived",false);
        JSONArray es=new JSONArray(),source=old.getJSONArray("exercises");for(int i=0;i<source.length();i++)if(!source.getJSONObject(i).optBoolean("archived"))es.put(newExercise(source.getJSONObject(i)));out.put("exercises",es);
        out.put("swim",new JSONObject().put("planned",s(old.optJSONObject("swim"),"planned")));return out;
    }
    public static String key(JSONObject session){return s(session,"date")+"\n"+s(session,"name");}
    /** Build a complete preview in memory. Nothing is written before confirmation. */
    public static final class ImportBatch {
        public final List<JSONObject> sessions=new ArrayList<>();public int added,updated,exercises,filled,conflicts,recordedSessions;
    }
    public static ImportBatch prepare(Store store,List<JSONObject> incoming,boolean includeActual)throws Exception{
        ImportBatch batch=new ImportBatch();Map<String,JSONObject> existing=new HashMap<>();int nextId=1000;
        for(JSONObject session:store.list("session")){nextId=Math.max(nextId,session.optInt("id")+1);if(!session.optBoolean("archived")){
            if(existing.containsKey(key(session)))throw new Exception("本机有同日期同名训练，请先修改名称以明确导入目标："+s(session,"date")+" "+s(session,"name"));existing.put(key(session),session);
        }}
        Set<String> seen=new HashSet<>();
        for(JSONObject imported:incoming){
            if(!seen.add(key(imported)))throw new Exception("导入数据包含重复训练日，请检查表格");
            JSONObject before=existing.get(key(imported));JSONObject target=before==null?new JSONObject().put("id",nextId++).put("date",s(imported,"date")).put("name",s(imported,"name")).put("week",imported.optInt("week",0)).put("exercises",new JSONArray()).put("swim",new JSONObject()):Store.cloneJson(before);
            if(before==null)batch.added++;else batch.updated++;
            if(before!=null){JSONArray old=before.getJSONArray("exercises");boolean history=false;for(int i=0;i<old.length();i++)history|=hasActual(old.getJSONObject(i));if(history)batch.recordedSessions++;}
            target.put("week",imported.optInt("week",target.optInt("week",0)));
            JSONObject swim=target.optJSONObject("swim");if(swim==null)swim=new JSONObject();JSONObject inSwim=imported.optJSONObject("swim");if(inSwim!=null&&!s(inSwim,"planned").isEmpty())swim.put("planned",s(inSwim,"planned"));target.put("swim",swim);
            JSONArray incomingEs=imported.getJSONArray("exercises");Map<String,Integer> occurrence=new HashMap<>();Set<String> usedIds=new HashSet<>();
            for(int i=0;i<incomingEs.length();i++){
                JSONObject in=incomingEs.getJSONObject(i);validateExercise(in);batch.exercises++;JSONArray es=target.getJSONArray("exercises");String name=s(in,"name");int ordinal=occurrence.getOrDefault(name,0);occurrence.put(name,ordinal+1);int index=-1,seenName=0;
                // Stable IDs are accepted only inside an already matched date + training name.
                String sourceId=s(in,"sourceExerciseId");if(!sourceId.isEmpty())for(int j=0;j<es.length();j++)if(!es.getJSONObject(j).optBoolean("archived")&&s(es.getJSONObject(j),"id").equals(sourceId)&&!usedIds.contains(sourceId)){index=j;break;}
                if(index<0)for(int j=0;j<es.length();j++){JSONObject e=es.getJSONObject(j);if(!e.optBoolean("archived")&&s(e,"name").equals(name)){if(seenName++==ordinal&&!usedIds.contains(s(e,"id"))){index=j;break;}}}
                JSONObject dest;
                if(index<0){dest=newExercise(in);es.put(dest);}else{
                    edit(target,index,in);dest=target.getJSONArray("exercises").getJSONObject(index);if(dest.optBoolean("archived"))throw new Exception("动作匹配异常");
                }
                usedIds.add(s(dest,"id"));
                if(includeActual&&in.optJSONArray("actual")!=null){JSONArray source=in.getJSONArray("actual"),actual=dest.getJSONArray("actual");for(int j=0;j<source.length();j++){
                    JSONObject from=source.getJSONObject(j);if(!recorded(from))continue;while(actual.length()<=j)actual.put(blankSet());JSONObject to=actual.getJSONObject(j);
                    for(String field:new String[]{"weight","quantity","rir"}){String value=s(from,field),local=s(to,field);if(!value.isEmpty()){if(local.isEmpty()){to.put(field,value);batch.filled++;}else if(!local.equals(value))batch.conflicts++;}}
                    if(!s(to,"quantity").isEmpty()&&from.optBoolean("done"))to.put("done",true);
                }}
            }
            target.put("updatedAt",System.currentTimeMillis());batch.sessions.add(target);
        }
        return batch;
    }
}
