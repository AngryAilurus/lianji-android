package cn.lianji.training;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public final class Store extends SQLiteOpenHelper {
    final Context context;
    public Store(Context context) { this(context, "lianji.db"); }
    public Store(Context context,String name) { super(context,name,null,1);this.context=context; }
    @Override public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE items(kind TEXT NOT NULL,k TEXT NOT NULL,payload TEXT NOT NULL,updated INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(kind,k))"); }
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next) { throw new IllegalStateException("暂不支持此数据库升级"); }
    public static String str(JSONObject o,String k) { return o==null||!o.has(k)||o.isNull(k)?"":o.optString(k,""); }
    public static JSONObject cloneJson(JSONObject o) { try{return new JSONObject(o.toString());}catch(JSONException e){throw new IllegalStateException(e);} }
    public void seed() throws Exception {
        if(get("meta","seed")!=null)return;
        JSONObject seed=new JSONObject(read(context.getAssets().open("seed.json"),10*1024*1024));
        merge(seed);
        put("meta","seed",new JSONObject().put("version",1));
    }
    public JSONObject get(String kind,String key) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM items WHERE kind=? AND k=?",new String[]{kind,key})){
            return c.moveToFirst()?new JSONObject(c.getString(0)):null;
        }catch(JSONException e){throw new IllegalStateException("记录无法读取",e);}
    }
    public void put(String kind,String key,JSONObject data) {
        ContentValues v=new ContentValues();v.put("kind",kind);v.put("k",key);v.put("payload",data.toString());v.put("updated",data.optLong("updatedAt",0));
        getWritableDatabase().insertWithOnConflict("items",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public List<JSONObject> list(String kind) {
        ArrayList<JSONObject> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM items WHERE kind=?",new String[]{kind})){
            while(c.moveToNext())out.add(new JSONObject(c.getString(0)));
        }catch(JSONException e){throw new IllegalStateException(e);}
        Collections.sort(out,(a,b)->str(a,"date").compareTo(str(b,"date")));
        return out;
    }
    public void saveSession(JSONObject session) {
        try{session.put("updatedAt",System.currentTimeMillis());put("session",String.valueOf(session.getInt("id")),session);}catch(JSONException e){throw new IllegalStateException(e);}
    }
    public void saveMetric(JSONObject metric) {
        try{metric.put("updatedAt",System.currentTimeMillis());put("metric",metric.getString("date"),metric);}catch(JSONException e){throw new IllegalStateException(e);}
    }
    public void savePlans(List<JSONObject> sessions,boolean checkpoint)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            if(checkpoint)put("checkpoint","beforeExcelImport",export());
            Set<Integer> ids=new HashSet<>();for(JSONObject session:sessions){int id=session.getInt("id");if(!ids.add(id))throw new Exception("训练编号重复，未写入任何计划");java.time.LocalDate.parse(session.getString("date"));JSONArray es=session.getJSONArray("exercises");if(es.length()>200)throw new Exception("每次训练最多200个动作");
                JSONObject old=get("session",String.valueOf(id));session.put("updatedAt",Math.max(System.currentTimeMillis(),old==null?0:old.optLong("updatedAt",0)+1));put("session",String.valueOf(id),session);
            }db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public JSONObject export() throws JSONException {
        JSONObject out=new JSONObject().put("format","lianji-backup").put("version",1).put("exportedAt",System.currentTimeMillis());
        for(String[] pair:new String[][]{{"session","sessions"},{"metric","metrics"},{"legacy","legacy"}}){JSONArray a=new JSONArray();for(JSONObject o:list(pair[0]))a.put(o);out.put(pair[1],a);}
        return out;
    }
    public static void validate(JSONObject data) throws Exception {
        if(!"lianji-backup".equals(data.optString("format"))||data.optInt("version")!=1)throw new Exception("这不是练记版本1的备份文件");
        JSONArray sessions=data.getJSONArray("sessions"),metrics=data.getJSONArray("metrics"),legacy=data.getJSONArray("legacy");
        if(sessions.length()>10000||metrics.length()>50000||legacy.length()>50)throw new Exception("备份记录过多");
        Set<Integer> ids=new HashSet<>();
        for(int i=0;i<sessions.length();i++){
            JSONObject s=sessions.getJSONObject(i);int id=s.getInt("id");if(id<=0||!ids.add(id))throw new Exception("训练编号重复或无效");LocalDate.parse(s.getString("date"));
            if(str(s,"name").isEmpty())throw new Exception("训练名称缺失");
            JSONArray exercises=s.getJSONArray("exercises");if(exercises.length()>200)throw new Exception("单次动作过多");Set<String> exerciseIds=new HashSet<>();
            for(int j=0;j<exercises.length();j++){
                JSONObject e=exercises.getJSONObject(j);if(str(e,"id").isEmpty()||!exerciseIds.add(e.getString("id")))throw new Exception("动作编号重复或无效");
                if(str(e,"name").isEmpty())throw new Exception("动作名称缺失");JSONArray actual=e.getJSONArray("actual");if(actual.length()>100)throw new Exception("组数过多");
                for(int k=0;k<actual.length();k++){JSONObject set=actual.getJSONObject(k);for(String field:new String[]{"weight","quantity","rir"})if(str(set,field).length()>240)throw new Exception("单组内容过长");}
            }
        }
        Set<String> dates=new HashSet<>();
        for(int i=0;i<metrics.length();i++){String date=metrics.getJSONObject(i).getString("date");LocalDate.parse(date);if(!dates.add(date))throw new Exception("体测日期重复");}
        Set<String> names=new HashSet<>();
        for(int i=0;i<legacy.length();i++){JSONObject l=legacy.getJSONObject(i);if(!names.add(l.getString("name")))throw new Exception("历史表名重复");if(l.getJSONArray("cells").length()>100000)throw new Exception("历史记录过多");}
    }
    public int merge(JSONObject data) throws Exception {
        validate(data);SQLiteDatabase db=getWritableDatabase();int changed=0;db.beginTransaction();
        try{
            for(String[] pair:new String[][]{{"session","sessions"},{"metric","metrics"},{"legacy","legacy"}}){
                JSONArray a=data.getJSONArray(pair[1]);
                for(int i=0;i<a.length();i++){
                    JSONObject incoming=a.getJSONObject(i);String key=pair[0].equals("session")?String.valueOf(incoming.getInt("id")):incoming.getString(pair[0].equals("metric")?"date":"name");
                    JSONObject current=get(pair[0],key);
                    if(current==null||incoming.optLong("updatedAt",0)>current.optLong("updatedAt",0)){put(pair[0],key,incoming);changed++;}
                }
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return changed;
    }
    public static String read(InputStream in,int max) throws IOException {
        try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1){if(out.size()+n>max)throw new IOException("文件超过10MB，无法导入");out.write(bytes,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    static String csv(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    public String trainingCsv() throws JSONException {
        StringBuilder out=new StringBuilder("\uFEFF日期,训练,动作,组,重量原文,次数或距离原文,单位,RIR,已完成,备注\r\n");
        for(JSONObject s:list("session")){JSONArray es=s.getJSONArray("exercises");for(int i=0;i<es.length();i++){
            JSONObject e=es.getJSONObject(i);JSONArray sets=e.getJSONArray("actual");for(int j=0;j<sets.length();j++){
                JSONObject set=sets.getJSONObject(j);if(!set.optBoolean("done")&&str(set,"weight").isEmpty()&&str(set,"quantity").isEmpty()&&str(set,"rir").isEmpty())continue;
                String[] row={str(s,"date"),str(s,"name"),str(e,"name"),String.valueOf(j+1),str(set,"weight"),str(set,"quantity"),str(e,"unit"),str(set,"rir"),set.optBoolean("done")?"是":"否",str(e,"note")};
                for(int k=0;k<row.length;k++){if(k>0)out.append(',');out.append(csv(row[k]));}out.append("\r\n");
            }
        }}return out.toString();
    }
}
