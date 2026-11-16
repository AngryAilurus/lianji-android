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
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("确认导入预览").setView(sc).setNegativeButton("取消",null).setPositiveButton("确认导入",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(button->{try{PlanOps.ImportBatch batch=PlanOps.prepare(a.store,table.sessions,actual.isChecked());a.store.savePlans(batch.sessions,true);dialog.dismiss();a.page="计划";a.currentId=-1;a.planWeek=table.sessions.get(0).optInt("week",1);a.build();a.toast("导入完成，已有记录已保留");}catch(Exception ex){a.error(ex);}}));dialog.show();
    }
    int nextId(){int id=1000;for(JSONObject x:a.store.list("session"))id=Math.max(id,x.optInt("id")+1);return id;}
    void archivedSessions() {
        List<JSONObject> archived = new ArrayList<>();
        for (JSONObject session : a.store.list("session")) if (session.optBoolean("archived")) archived.add(session);
        if (archived.isEmpty()) { a.toast("没有已移出的训练计划"); return; }
        String[] names = new String[archived.size()];
        for (int i = 0; i < names.length; i++) names[i] = s(archived.get(i),"date") + " " + s(archived.get(i),"name");
        new AlertDialog.Builder(a).setTitle("已移出的训练计划")
            .setItems(names,(d,i) -> editPlan(archived.get(i).optInt("id")))
            .setNegativeButton("关闭",null).show();
    }

    void createSession() {
        try { a.openPlanEditor(null); } catch (Exception ex) { a.error(ex); }
    }

    void editPlan(int id) {
        try { a.openPlanEditor(requirePlan(id)); } catch (Exception ex) { a.error(ex); }
    }

    void addExercise(int id) { editAction(id,-1); }

    void editAction(int id,int index) {
        try {
            JSONObject session = requirePlan(id);
            if (index >= 0 && session.getJSONArray("exercises").getJSONObject(index).optBoolean("archived")) {
                a.openPlanEditor(session);
            } else {
                a.openPlanAction(session,index);
            }
        } catch (Exception ex) { a.error(ex); }
    }

    private JSONObject requirePlan(int id) throws Exception {
        JSONObject session = get(id);
        if (session == null) throw new Exception("找不到这次训练。");
        return session;
    }

    void copySession(int id) {
        try {
            JSONObject original = requirePlan(id);
            a.pickDate(LocalDate.parse(s(original,"date")).plusDays(7), date -> {
                try {
                    JSONObject copy = PlanOps.copy(requirePlan(id),nextId(),date.toString());
                    for (JSONObject existing : a.store.list("session")) {
                        if (!existing.optBoolean("archived") && PlanOps.key(existing).equals(PlanOps.key(copy))) {
                            throw new Exception("该日期已有同名训练，请选择其他日期");
                        }
                    }
                    a.store.savePlans(Collections.singletonList(copy), false);
                    a.openCopiedPlan(copy.getInt("id"));
                    a.toast("已复制计划，未复制实际组记录");
                } catch (Exception ex) { a.error(ex); }
            });
        } catch (Exception ex) { a.error(ex); }
    }
}
