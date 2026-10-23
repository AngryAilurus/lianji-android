package cn.lianji.training;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Import preview and full plan editor, kept out of the workout logging flow. */
public final class PlannerUi {
    final MainActivity a;final ExecutorService worker=Executors.newSingleThreadExecutor();ProgressDialog pending;
    PlannerUi(MainActivity activity){a=activity;}
    void close(){worker.shutdownNow();if(pending!=null)pending.dismiss();}
    JSONObject get(int id){return a.store.get("session",String.valueOf(id));}
    String s(JSONObject o,String key){return Store.str(o,key);}
    void show(){a.rerender();}
    void pickExcel(){a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/octet-stream","application/vnd.ms-excel"}),103);}
    void excelMenu(){new AlertDialog.Builder(a).setTitle("Excel训练计划").setItems(new String[]{"导入Excel计划（.xlsx）","导出当前计划（含已填记录）","下载空白格式模板","导出最近一次导入前的备份","查看已移出的训练计划"},(d,which)->{
        if(which==0)pickExcel();else if(which==4)archivedSessions();else if(which==3){try{JSONObject prior=a.store.get("checkpoint","beforeExcelImport");if(prior==null){a.toast("还没有进行过Excel导入");return;}a.beginExport(prior.toString(2),"练记-导入前备份-"+a.today()+".json","application/json");}catch(Exception ex){a.error(ex);}}
        else try{a.beginExportBytes(PlanXlsx.write(a.store.list("session"),which==2),which==2?"练记-计划导入模板.xlsx":"练记-训练计划-"+a.today()+".xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");}catch(Exception ex){a.error(ex);}
    }).setNegativeButton("关闭",null).show();}
    void read(Uri uri){ProgressDialog waiting=new ProgressDialog(a);pending=waiting;waiting.setMessage("正在读取Excel，尚未修改本机计划…");waiting.setCancelable(false);waiting.show();worker.execute(()->{
        File file=null;try{file=File.createTempFile("plan-import-",".xlsx",a.getCacheDir());try(InputStream in=a.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){if(in==null)throw new IOException("无法读取选定文件");byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){if((total+=n)>PlanXlsx.MAX_FILE)throw new IOException("文件超过10MB");out.write(b,0,n);}}
            List<PlanXlsx.Table> tables=PlanXlsx.read(file);a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed())return;waiting.dismiss();chooseSheet(tables);});
        }catch(Exception ex){a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed())return;waiting.dismiss();a.error(new IOException("未导入任何内容。\n"+(ex instanceof java.util.zip.ZipException?"请选择.xlsx文件，旧版.xls请先另存为.xlsx。":ex.getMessage())));});}
        finally{if(file!=null)file.delete();}
    });}
    void chooseSheet(List<PlanXlsx.Table> tables){
        if(tables.size()==1){preview(tables.get(0));return;}String[] names=new String[tables.size()];int selected=0;for(int i=0;i<names.length;i++){PlanXlsx.Table t=tables.get(i);names[i]=t.name+"  ·  "+t.rows+"个动作"+(t.errors.isEmpty()?"":" · 有格式错误");if(t.name.equals("力量训练记录")||t.name.equals("训练计划"))selected=i;}
        final int[] selection={selected};new AlertDialog.Builder(a).setTitle("选择要导入的工作表").setSingleChoiceItems(names,selected,(d,which)->selection[0]=which).setNegativeButton("取消",null).setPositiveButton("预览",(d,w)->preview(tables.get(selection[0]))).show();
    }
    void preview(PlanXlsx.Table table){
        if(!table.errors.isEmpty()){StringBuilder msg=new StringBuilder("整张表暂未导入，请修正后重试。\n\n");for(int i=0;i<Math.min(12,table.errors.size());i++)msg.append(table.errors.get(i)).append('\n');new AlertDialog.Builder(a).setTitle("表格有"+table.errors.size()+"处格式问题").setMessage(msg.toString()).setPositiveButton("知道了",null).show();return;}
        if(table.sessions.isEmpty()){a.toast("这张表没有可导入的训练动作");return;}
        LinearLayout b=a.dialogBody();a.add(b,a.text(table.name,17,MainActivity.INK,true));a.gap(b,10);CheckBox actual=new CheckBox(a);actual.setText("同时导入表内已填写记录（只补空白）");actual.setChecked(true);actual.setTextSize(13);a.add(b,actual);a.gap(b,10);TextView stats=a.text("",14,MainActivity.GREEN,true);a.add(b,stats);a.gap(b,10);a.add(b,a.text("同日期、同名训练会合并。已有动作不会因表中缺少而被删除；已有非空记录不会被Excel覆盖。确认导入前会自动保留完整备份。",12,MainActivity.MUTED,false));a.gap(b,16);
        for(int i=0;i<Math.min(6,table.sessions.size());i++){JSONObject x=table.sessions.get(i);a.add(b,a.text(s(x,"date")+"  "+s(x,"name"),14,MainActivity.INK,true));JSONArray es=x.optJSONArray("exercises");for(int j=0;j<Math.min(6,es.length());j++){JSONObject e=es.optJSONObject(j);a.add(b,a.text("  "+s(e,"name")+"  "+e.optInt("sets")+"组 × "+e.optInt("low")+"–"+e.optInt("high")+s(e,"unit"),12,MainActivity.MUTED,false));}a.gap(b,10);}if(table.sessions.size()>6)a.add(b,a.text("其余"+(table.sessions.size()-6)+"次训练也将一并导入。",12,MainActivity.MUTED,false));
        Runnable update=()->{try{PlanOps.ImportBatch batch=PlanOps.prepare(a.store,table.sessions,actual.isChecked());stats.setText(batch.added+"次新增 · "+batch.updated+"次合并 · "+batch.exercises+"个动作\n补入"+batch.filled+"个记录字段 · 保留"+batch.conflicts+"个本机不同值");}catch(Exception ex){stats.setText(ex.getMessage());}};actual.setOnCheckedChangeListener((v,checked)->update.run());update.run();ScrollView sc=new ScrollView(a);sc.addView(b);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("确认导入预览").setView(sc).setNegativeButton("取消",null).setPositiveButton("确认导入",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(button->{try{PlanOps.ImportBatch batch=PlanOps.prepare(a.store,table.sessions,actual.isChecked());a.store.savePlans(batch.sessions,true);dialog.dismiss();a.page="计划";a.currentId=-1;a.filter="周"+table.sessions.get(0).optInt("week",1);a.build();a.toast("导入完成，已有记录已保留");}catch(Exception ex){a.error(ex);}}));dialog.show();
    }
    int nextId(){int id=1000;for(JSONObject x:a.store.list("session"))id=Math.max(id,x.optInt("id")+1);return id;}
    void archivedSessions(){List<JSONObject> archived=new ArrayList<>();for(JSONObject x:a.store.list("session"))if(x.optBoolean("archived"))archived.add(x);if(archived.isEmpty()){a.toast("没有已移出的训练计划");return;}String[] names=new String[archived.size()];for(int i=0;i<names.length;i++)names[i]=s(archived.get(i),"date")+" "+s(archived.get(i),"name");new AlertDialog.Builder(a).setTitle("已移出的训练计划").setItems(names,(d,which)->sessionEditor(archived.get(which).optInt("id"))).setNegativeButton("关闭",null).show();}
    void createSession(){sessionEditor(-1);}
    void sessionMenu(int id){JSONObject x=get(id);new AlertDialog.Builder(a).setTitle(s(x,"name")+" · 编辑计划").setItems(new String[]{"日期、名称、周次与游泳计划","编辑动作、组数、次数与RIR","新增动作","复制训练到其他日期","从计划移除（保留记录）"},(d,which)->{
        if(which==0)sessionEditor(id);else if(which==1)exerciseChooser(id);else if(which==2)exerciseEditor(id,-1);else if(which==3)copySession(id);else new AlertDialog.Builder(a).setTitle("从计划移除？").setMessage("不会删除已填写的训练记录，仍可在“记录”页查看。若需恢复，可在该训练的计划设置中恢复显示。").setNegativeButton("取消",null).setPositiveButton("移出计划",(q,w)->{try{JSONObject fresh=get(id);fresh.put("archived",true);a.store.savePlans(Collections.singletonList(fresh),false);a.currentId=-1;a.build();}catch(Exception ex){a.error(ex);}}).show();
    }).setNegativeButton("关闭",null).show();}
    void sessionEditor(int id){JSONObject original=id<0?null:get(id);LinearLayout b=a.dialogBody();final LocalDate[] date={LocalDate.parse(original==null?a.today():s(original,"date"))};TextView pick=a.action(a.dateLabel(date[0].toString()),false,()->{});pick.setOnClickListener(v->a.pickDate(date[0],d->{date[0]=d;pick.setText(a.dateLabel(d.toString()));}));a.label(b,"训练日期");a.add(b,pick);a.gap(b,16);
        EditText name=a.input(b,"训练名称",original==null?"新训练":s(original,"name"),"例如 上肢A",false),week=a.input(b,"周次",original==null?"1":s(original,"week"),"1–52，0表示未分周",true),swim=a.input(b,"计划游泳分钟",s(original==null?null:original.optJSONObject("swim"),"planned"),"可留空",true);CheckBox hidden=new CheckBox(a);hidden.setText("在训练计划中显示");hidden.setChecked(original==null||!original.optBoolean("archived"));a.add(b,hidden);week.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);a.add(b,a.text("仅修改这一天。已有组记录随训练保留，不会清空。",12,MainActivity.MUTED,false));
        ScrollView sc=new ScrollView(a);sc.addView(b);AlertDialog d=new AlertDialog.Builder(a).setTitle(id<0?"新建训练计划":"编辑训练计划").setView(sc).setNegativeButton("取消",null).setPositiveButton("保存计划",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(button->{try{String n=name.getText().toString().trim();int w=Integer.parseInt(week.getText().toString());String minutes=swim.getText().toString().trim();if(n.isEmpty()||w<0||w>52)throw new Exception("请填写名称和0–52的周次");if(!minutes.isEmpty()&&(Double.parseDouble(minutes)<0||Double.parseDouble(minutes)>600))throw new Exception("游泳时间请输入0–600分钟");JSONObject fresh=id<0?new JSONObject().put("id",nextId()).put("exercises",new JSONArray()).put("swim",new JSONObject()):get(id);fresh.put("date",date[0].toString()).put("name",n).put("week",w).put("archived",!hidden.isChecked());fresh.getJSONObject("swim").put("planned",minutes);for(JSONObject other:a.store.list("session"))if(other.optInt("id")!=fresh.optInt("id")&&!other.optBoolean("archived")&&PlanOps.key(other).equals(PlanOps.key(fresh))&&!fresh.optBoolean("archived"))throw new Exception("这一天已有同名训练，请使用不同名称");a.store.savePlans(Collections.singletonList(fresh),false);d.dismiss();a.currentId=fresh.getInt("id");a.build();if(id<0)exerciseEditor(fresh.getInt("id"),-1);}catch(Exception ex){a.toast(ex.getMessage()==null?"请检查填写内容":ex.getMessage());}}));d.show();
    }
    void exerciseChooser(int id){JSONArray es=get(id).optJSONArray("exercises");if(es.length()==0){exerciseEditor(id,-1);return;}String[] names=new String[es.length()];for(int i=0;i<names.length;i++)names[i]=(es.optJSONObject(i).optBoolean("archived")?"历史保留 · ":"")+(i+1)+". "+s(es.optJSONObject(i),"name");new AlertDialog.Builder(a).setTitle("选择要编辑的动作").setItems(names,(d,which)->exerciseMenu(id,which)).setNegativeButton("关闭",null).show();}
    void exerciseMenu(int id,int index){JSONObject e=get(id).optJSONArray("exercises").optJSONObject(index);if(e.optBoolean("archived")){new AlertDialog.Builder(a).setTitle("历史动作已保留").setMessage("这个动作已移出计划，其记录没有删除。").setPositiveButton("恢复到计划",(d,w)->{try{JSONObject x=get(id);x.getJSONArray("exercises").getJSONObject(index).put("archived",false);a.store.savePlans(Collections.singletonList(x),false);show();}catch(Exception ex){a.error(ex);}}).setNegativeButton("关闭",null).show();return;}
        new AlertDialog.Builder(a).setTitle(s(e,"name")).setItems(new String[]{"编辑动作与目标","上移一位","下移一位","从计划移除（保留已填记录）"},(d,which)->{if(which==0)exerciseEditor(id,index);else if(which==3)new AlertDialog.Builder(a).setTitle("移除这个计划动作？").setMessage("已有重量、次数、RIR和备注会保留为历史动作。").setNegativeButton("取消",null).setPositiveButton("移除",(q,w)->mutateOrder(id,index,0)).show();else mutateOrder(id,index,which==1?-1:1);}).setNegativeButton("关闭",null).show();
    }
    void mutateOrder(int id,int index,int delta){try{JSONObject x=get(id);if(delta==0)PlanOps.remove(x,index);else PlanOps.move(x,index,delta);a.store.savePlans(Collections.singletonList(x),false);show();}catch(Exception ex){a.error(ex);}}
    void exerciseEditor(int id,int index){
        JSONObject session=get(id),old=index<0?null:session.optJSONArray("exercises").optJSONObject(index);LinearLayout b=a.dialogBody();
        EditText name=a.input(b,"动作名称",s(old,"name"),"例如 杠铃卧推",false),sets=a.input(b,"计划组数",old==null?"2":s(old,"sets"),"1–30",true),low=a.input(b,"次数/秒数/距离下限",old==null?"8":s(old,"low"),"",true),high=a.input(b,"次数/秒数/距离上限",old==null?"12":s(old,"high"),"",true);
        a.label(b,"数量单位");Spinner unit=new Spinner(a);String[] units={"次","秒","米"};unit.setAdapter(new ArrayAdapter<>(a,android.R.layout.simple_spinner_dropdown_item,units));unit.setSelection(Math.max(0,Arrays.asList(units).indexOf(s(old,"unit"))));a.add(b,unit);a.gap(b,14);
        EditText rir=a.input(b,"目标RIR",s(old,"rir"),"0–10，可留空",true),rest=a.input(b,"休息秒数",old==null?"90":s(old,"rest"),"0–1800",true),load=a.input(b,"重量建议",s(old,"load"),"例如 每手10kg",false),note=a.input(b,"动作备注",s(old,"note"),"可留空",false);
        for(EditText field:new EditText[]{sets,low,high,rir,rest})field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);CheckBox future=new CheckBox(a);future.setText("同时应用到此后同名训练日");future.setTextSize(13);a.add(b,future);a.add(b,a.text("减少组数会保留已填的额外组。更换已有记录的动作名称或单位时，旧动作会作为历史保留，新动作从空白开始。",12,MainActivity.MUTED,false));
        ScrollView sc=new ScrollView(a);sc.addView(b);AlertDialog d=new AlertDialog.Builder(a).setTitle(index<0?"添加计划动作":"编辑计划动作").setView(sc).setNegativeButton("取消",null).setPositiveButton("保存动作",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(button->{try{
            JSONObject fields=new JSONObject().put("name",name.getText().toString().trim()).put("sets",Integer.parseInt(sets.getText().toString())).put("low",Integer.parseInt(low.getText().toString())).put("high",Integer.parseInt(high.getText().toString())).put("unit",unit.getSelectedItem().toString()).put("rir",rir.getText().toString().trim()).put("rest",Integer.parseInt(rest.getText().toString())).put("load",load.getText().toString().trim()).put("note",note.getText().toString().trim());PlanOps.validateExercise(fields);
            final List<JSONObject> targets=new ArrayList<>();for(JSONObject candidate:a.store.list("session")){
                boolean chosen=candidate.optInt("id")==id||future.isChecked()&&!candidate.optBoolean("archived")&&s(candidate,"name").equals(s(session,"name"))&&s(candidate,"date").compareTo(s(session,"date"))>0;if(!chosen)continue;
                if(index<0)candidate.getJSONArray("exercises").put(PlanOps.newExercise(fields));else{
                    int targetIndex=candidate.optInt("id")==id?index:-1;if(targetIndex<0){JSONArray es=candidate.getJSONArray("exercises");for(int j=0;j<es.length();j++)if(!es.getJSONObject(j).optBoolean("archived")&&s(es.getJSONObject(j),"name").equals(s(old,"name"))){targetIndex=j;break;}}
                    if(targetIndex<0)continue;PlanOps.edit(candidate,targetIndex,fields);
                }targets.add(candidate);
            }
            Runnable save=()->{try{a.store.savePlans(targets,false);d.dismiss();show();a.toast("已更新"+targets.size()+"次训练，已有记录保留");}catch(Exception ex){a.error(ex);}};
            if(targets.size()>1)new AlertDialog.Builder(a).setTitle("更新后续计划？").setMessage("将修改"+targets.size()+"次同名训练中的这个动作，保留已填写记录。").setNegativeButton("取消",null).setPositiveButton("确认更新",(q,w)->save.run()).show();else save.run();
        }catch(Exception ex){a.toast(ex.getMessage()==null?"请检查数字格式":ex.getMessage());}}));d.show();
    }
    void copySession(int id){JSONObject original=get(id);a.pickDate(LocalDate.parse(s(original,"date")).plusDays(7),date->{try{JSONObject copy=PlanOps.copy(original,nextId(),date.toString());for(JSONObject existing:a.store.list("session"))if(!existing.optBoolean("archived")&&PlanOps.key(existing).equals(PlanOps.key(copy)))throw new Exception("该日期已有同名训练，请选择其他日期");a.store.savePlans(Collections.singletonList(copy),false);a.currentId=copy.getInt("id");a.build();a.toast("已复制计划，未复制实际组记录");}catch(Exception ex){a.error(ex);}});}
}
