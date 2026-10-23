package cn.lianji.training;

import org.json.*;
import java.util.*;

/** Entirely invented test data. No dates, plans or records originate from a user. */
final class TestFixtures {
    private TestFixtures(){}

    static JSONObject exercise(String id,String name,int sets,int low,int high,String unit)throws Exception{
        JSONObject e=PlanOps.newExercise(new JSONObject().put("name",name).put("sets",sets)
            .put("low",low).put("high",high).put("unit",unit).put("rir","2")
            .put("rest",37).put("load","合成建议").put("note","合成备注"));
        return e.put("id",id);
    }
    static JSONObject set(String weight,String quantity,String rir)throws Exception{
        return new JSONObject().put("weight",weight).put("quantity",quantity).put("rir",rir).put("done",true);
    }
    static JSONObject session(int id,String date,String name,JSONObject... exercises)throws Exception{
        JSONArray es=new JSONArray();for(JSONObject e:exercises)es.put(e);
        return new JSONObject().put("id",id).put("date",date).put("week",1).put("name",name)
            .put("exercises",es).put("swim",new JSONObject().put("planned","7")).put("updatedAt",0);
    }
    static JSONObject backup()throws Exception{
        JSONObject press=exercise("fixture-press","合成器械甲",2,7,9,"次");
        press.getJSONArray("actual").put(0,set("12.5","7","0"));
        press.put("note","合成\"引号\",逗号");
        JSONObject hold=exercise("fixture-hold","合成器械乙",1,23,29,"秒");
        hold.getJSONArray("actual").put(0,set("空杆","23","1"));
        JSONObject carry=exercise("fixture-carry","合成器械丙",2,13,17,"米");
        carry.getJSONArray("actual").put(0,set("6","3 趟",""));
        carry.getJSONArray("actual").put(set("7","4 趟","2"));
        JSONObject row=exercise("fixture-row","合成器械丁",1,11,13,"次");
        JSONArray sessions=new JSONArray()
            .put(session(101,"2032-04-05","合成训练甲",press,hold))
            .put(session(102,"2032-04-09","合成训练乙",carry))
            .put(session(103,"2032-04-12","合成训练丙",row));
        JSONArray cells=new JSONArray().put(new JSONObject().put("cell","A1").put("value","合成历史"))
            .put(new JSONObject().put("cell","B2").put("value","示例,文本"))
            .put(new JSONObject().put("cell","C3").put("value","42"));
        return new JSONObject().put("format","lianji-backup").put("version",1).put("sessions",sessions)
            .put("metrics",new JSONArray()).put("legacy",new JSONArray().put(new JSONObject()
                .put("name","合成历史表").put("cells",cells)));
    }
    static List<JSONObject> sessions()throws Exception{
        JSONArray source=backup().getJSONArray("sessions");List<JSONObject> out=new ArrayList<>();
        for(int i=0;i<source.length();i++)out.add(source.getJSONObject(i));return out;
    }
}
