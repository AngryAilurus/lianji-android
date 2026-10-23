package cn.lianji.training;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.*;
import java.util.*;

public class MainActivity extends Activity {
    static final int BG=Color.rgb(229,231,235),INK=Color.rgb(24,35,53),MUTED=Color.rgb(100,113,132),GREEN=Color.rgb(38,86,216),PALE=Color.rgb(226,234,252),LINE=Color.rgb(213,221,232),YELLOW=Color.rgb(100,113,132),WHITE=Color.WHITE,SURFACE=Color.rgb(255,255,255),HERO=Color.rgb(255,255,255);
    Store store;PlannerUi planner;AppUpdates updates;LinearLayout shell,content,nav;ScrollView scroll;TextView timerView;String page="训练",filter="周1";int currentId=-1;boolean historyMode=false;
    final Handler handler=new Handler();long timerEnd=0;String exportText="";Dialog setDialog,completionDialog;
    final Runnable tick=new Runnable(){public void run(){timerEnd=getPreferences(0).getLong("timerEnd",0);long seconds=Math.max(0,(timerEnd-System.currentTimeMillis()+999)/1000);if(timerView!=null){timerView.setVisibility(timerEnd>0?View.VISIBLE:View.GONE);timerView.setText(seconds>0?"◷  休息 "+(seconds/60)+":"+String.format(Locale.ROOT,"%02d",seconds%60)+"    点击结束":"✓  休息结束，可以开始下一组");}if(timerEnd>0&&seconds==0){timerEnd=0;getPreferences(0).edit().remove("timerEnd").apply();}handler.postDelayed(this,500);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);try{store=new Store(this);store.seed();planner=new PlannerUi(this);timerEnd=getPreferences(0).getLong("timerEnd",0);if(getIntent().hasExtra("timerSession"))currentId=getIntent().getIntExtra("timerSession",-1);if(state!=null){page=state.getString("page","训练");currentId=state.getInt("session",-1);}build();handler.post(tick);updates=new AppUpdates(this);updates.automatic();}catch(Exception e){new AlertDialog.Builder(this).setTitle("暂时无法打开记录").setMessage("为保护数据，未重置任何记录。\n"+e.getMessage()).setPositiveButton("关闭",(d,w)->finish()).setCancelable(false).show();}}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("page",page);state.putInt("session",currentId);}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);if(updates!=null)updates.close();if(setDialog!=null)setDialog.dismiss();if(completionDialog!=null)completionDialog.dismiss();if(planner!=null)planner.close();if(store!=null)store.close();super.onDestroy();}
    @Override protected void onResume(){super.onResume();if(updates!=null)updates.resume();}
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("tnum");if(bold)v.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));v.setLineSpacing(dp(3),1f);return v;}
    LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    LinearLayout horizontal(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    void gap(LinearLayout host,int h){View v=new View(this);host.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    void add(LinearLayout parent,View child){parent.addView(child);}
    TextView action(String title,boolean solid,Runnable run){TextView b=text(title,14,solid?WHITE:GREEN,true);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(48));b.setPadding(dp(14),dp(10),dp(14),dp(10));b.setBackground(bg(solid?GREEN:PALE,14));b.setContentDescription(title);b.setOnClickListener(v->run.run());return b;}
    LinearLayout card(LinearLayout parent){LinearLayout c=vertical();c.setPadding(dp(18),dp(17),dp(18),dp(17));c.setBackground(bg(SURFACE,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(c,p);return c;}
    void smallTitle(String title,String subtitle){add(content,text(title,28,INK,true));if(!subtitle.isEmpty()){gap(content,5);add(content,text(subtitle,13,MUTED,false));}gap(content,22);}
    void label(LinearLayout p,String title){add(p,text(title,12,MUTED,true));gap(p,7);}
    String s(JSONObject o,String k){return Store.str(o,k);}
    JSONArray exercises(JSONObject session){return session.optJSONArray("exercises");}
    JSONObject session(){return store.get("session",String.valueOf(currentId));}
    String today(){return LocalDate.now().toString();}
    String dateLabel(String date){try{return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.SIMPLIFIED_CHINESE));}catch(Exception e){return date;}}
    int[] progress(JSONObject session){int done=0,total=0;JSONArray es=exercises(session);if(es==null)return new int[]{0,0};for(int i=0;i<es.length();i++){if(es.optJSONObject(i).optBoolean("archived"))continue;JSONArray sets=es.optJSONObject(i).optJSONArray("actual");for(int j=0;j<sets.length();j++){total++;if(sets.optJSONObject(j).optBoolean("done"))done++;}}return new int[]{done,total};}
    boolean hasRecords(JSONObject session){JSONArray es=exercises(session);for(int i=0;i<es.length();i++)if(PlanOps.hasActual(es.optJSONObject(i)))return true;return false;}
    boolean complete(JSONObject session){return PlanOps.completesSession(session,-1,-1);}
    void build(){
        shell=vertical();shell.setBackgroundColor(BG);shell.setOnApplyWindowInsetsListener((v,insets)->{shell.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(shell);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);shell.requestApplyInsets();
        LinearLayout top=horizontal();top.setPadding(dp(22),dp(12),dp(22),dp(10));TextView brand=text("练记",22,INK,true);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));TextView update=text("更新",12,GREEN,true);update.setPadding(dp(10),dp(12),dp(10),dp(12));update.setOnClickListener(v->{if(updates!=null)updates.check(true);});add(top,update);TextView data=text("备份与导出 ↗",12,GREEN,true);data.setPadding(dp(10),dp(12),0,dp(12));data.setOnClickListener(v->dataMenu());add(top,data);add(shell,top);
        timerView=text("",13,GREEN,true);timerView.setGravity(Gravity.CENTER);timerView.setPadding(dp(12),dp(10),dp(12),dp(10));timerView.setBackgroundColor(PALE);timerView.setVisibility(View.GONE);timerView.setOnClickListener(v->stopTimer());add(shell,timerView);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);content=vertical();content.setPadding(dp(22),dp(16),dp(22),dp(24));scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=horizontal();nav.setPadding(dp(12),dp(6),dp(12),dp(8));nav.setBackgroundColor(SURFACE);for(String item:new String[]{"训练","计划","记录","身体"}){TextView t=text(item,14,item.equals(page)?GREEN:MUTED,item.equals(page));t.setGravity(Gravity.CENTER);t.setPadding(0,dp(16),0,dp(16));if(item.equals(page))t.setBackground(bg(PALE,13));nav.addView(t,new LinearLayout.LayoutParams(0,-2,1));t.setOnClickListener(v->{page=item;currentId=-1;build();});}add(shell,nav);
        if(currentId!=-1)showWorkout();else if(page.equals("训练"))home();else if(page.equals("计划"))plan();else if(page.equals("记录"))history();else body();
    }
    void home(){
        smallTitle("把每一组，记下来。",dateLabel(today()));
        List<JSONObject> all=store.list("session");JSONObject current=null,next=null,last=null;
        for(JSONObject x:all){if(!x.optBoolean("archived")){if(s(x,"date").equals(today()))current=x;if(s(x,"date").compareTo(today())>0&&next==null)next=x;}if(hasRecords(x))last=x;}
        JSONObject featured=current!=null?current:next;
        LinearLayout hero=card(content);hero.setBackground(bg(HERO,18));
        add(hero,text(all.isEmpty()?"从第一组开始":current==null?"今天休息  /  下一次训练":"今天的训练",12,YELLOW,true));gap(hero,13);
        if(featured!=null){
            add(hero,text(s(featured,"name"),30,INK,true));gap(hero,8);add(hero,text(dateLabel(s(featured,"date"))+"  ·  第"+featured.optInt("week")+"周",13,MUTED,false));gap(hero,20);
            int[] p=progress(featured);add(hero,text(PlanOps.activeCount(featured)+" 个动作    "+p[0]+" / "+p[1]+" 组已记录",14,INK,false));gap(hero,20);
            int id=featured.optInt("id");add(hero,action(current==null?"查看下一次训练 →":complete(featured)?"查看今日记录 →":p[0]>0?"继续训练 →":"开始记录 →",true,()->openSession(id)));
        }else if(all.isEmpty()){add(hero,text("添加你的训练计划",28,INK,true));gap(hero,14);add(hero,text("从Excel导入计划，或新建一次训练。",14,MUTED,false));gap(hero,20);add(hero,action("导入Excel计划 →",true,()->planner.pickExcel()));
        }else{add(hero,text("计划已结束",28,INK,true));gap(hero,14);add(hero,text("历史记录一直保留，可在计划页导入后续训练。",14,INK,false));}
        add(content,action("＋ 体重 / 腰围",false,()->metricDialog(today())));gap(content,24);
        if(last!=null){label(content,"最近一次记录");sessionCard(content,last,true);}
        LinearLayout info=card(content);add(info,text("少填一点，练得专心一点。",15,INK,true));gap(info,8);add(info,text("上一组自动带入，完成后可选择休息计时。重量支持“空杆”，距离支持“1来回”。所有记录仅保存在这台手机。",13,MUTED,false));
        add(content,text("计划由你创建或导入，训练记录仅保存在本机。\n建议定期导出完整备份，卸载App会清除本机数据。",12,MUTED,false));
    }
    void sessionCard(LinearLayout parent,JSONObject x,boolean compact){LinearLayout c=card(parent);LinearLayout row=horizontal();TextView title=text(s(x,"name"),17,INK,true);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));int[] p=progress(x);add(row,text(complete(x)?"已完成":hasRecords(x)?"已记 "+p[0]+" 组":"待记录",12,hasRecords(x)?GREEN:MUTED,true));add(c,row);gap(c,7);add(c,text(dateLabel(s(x,"date"))+(x.optInt("week")>0?" · 第"+x.optInt("week")+"周":""),12,MUTED,false));if(!compact){gap(c,8);add(c,text(PlanOps.activeCount(x)+" 个动作 · "+p[1]+" 组 · 游泳 "+s(x.optJSONObject("swim"),"planned")+" 分钟",12,MUTED,false));}int id=x.optInt("id");c.setOnClickListener(v->openSession(id));if(!compact&&page.equals("计划")){gap(c,13);add(c,action("编辑这次计划",false,()->planner.sessionMenu(id)));}if(x.optBoolean("archived")){gap(c,7);add(c,text("已移出计划 · 历史记录保留",12,MUTED,false));}}
    void plan(){smallTitle("你的训练计划","从Excel导入，或直接编辑训练日与动作。");
        LinearLayout actions=horizontal();actions.addView(action("导入Excel",true,()->planner.pickExcel()),new LinearLayout.LayoutParams(0,-2,1));View gutter=new View(this);actions.addView(gutter,new LinearLayout.LayoutParams(dp(10),1));actions.addView(action("＋ 新建训练",false,()->planner.createSession()),new LinearLayout.LayoutParams(0,-2,1));add(content,actions);gap(content,10);add(content,action("导出计划 / Excel模板",false,()->planner.excelMenu()));gap(content,20);
        int maxWeek=12;for(JSONObject x:store.list("session"))maxWeek=Math.min(52,Math.max(maxWeek,x.optInt("week")));HorizontalScrollView tabs=new HorizontalScrollView(this);tabs.setHorizontalScrollBarEnabled(false);LinearLayout weeks=horizontal();
        TextView all=action("全部",filter.equals("全部"),()->{filter="全部";build();});weeks.addView(all);for(int w=1;w<=maxWeek;w++){final int week=w;TextView button=action("第"+w+"周",filter.equals("周"+w),()->{filter="周"+week;build();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=dp(7);weeks.addView(button,lp);}tabs.addView(weeks);add(content,tabs);gap(content,18);
        int count=0;for(JSONObject x:store.list("session"))if(!x.optBoolean("archived")&&(filter.equals("全部")||filter.equals("周"+x.optInt("week")))){sessionCard(content,x,false);count++;}
        if(count==0)add(content,text("本周还没有训练，可新建或从Excel导入。",14,MUTED,false));
        gap(content,10);add(content,text("修改和导入计划不会清空已有记录。已移出的历史动作仍可在原训练中查看和恢复。",12,MUTED,false));
    }
    void history(){smallTitle("训练留下了痕迹。","完整保留重量、次数、余力和文字记录。");
        int count=0;List<JSONObject> all=store.list("session");Collections.reverse(all);for(JSONObject x:all)if(hasRecords(x)){sessionCard(content,x,false);count++;}
        if(count==0){LinearLayout empty=card(content);add(empty,text("第一组，从今天开始。",18,INK,true));gap(empty,8);add(empty,text("保存一组后，这里就会出现训练记录。",13,MUTED,false));}
        if(!store.list("legacy").isEmpty()){gap(content,12);add(content,action("查看去年Excel历史资料 →",false,this::legacyMenu));}
    }
    void openSession(int id){currentId=id;build();}
    void showWorkout(){JSONObject x=session();if(x==null){currentId=-1;home();return;}
        TextView back=text("‹ 返回"+page,14,GREEN,true);back.setPadding(0,0,0,dp(16));back.setOnClickListener(v->{currentId=-1;build();});add(content,back);
        smallTitle(s(x,"name"),dateLabel(s(x,"date"))+" · "+(x.optInt("week")==1?"恢复期":x.optInt("week")==0?"未分周训练":"第"+x.optInt("week")+"周"));
        add(content,action("编辑训练计划",false,()->planner.sessionMenu(x.optInt("id"))));gap(content,16);int[] p=progress(x);LinearLayout summary=horizontal();TextView progress=text(p[0]+" / "+p[1]+" 组已记录",14,GREEN,true);summary.addView(progress,new LinearLayout.LayoutParams(0,-2,1));add(summary,text("点击一组即可填写",12,MUTED,false));add(content,summary);gap(content,10);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(1,p[1]));bar.setProgress(p[0]);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));content.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));gap(content,20);
        JSONArray es=exercises(x);for(int i=0;i<es.length();i++)exerciseCard(x,es.optJSONObject(i),i);
        add(content,action("＋ 添加动作",false,()->addExercise(x)));gap(content,22);
        LinearLayout swim=card(content);add(swim,text("力量后的游泳",18,INK,true));gap(swim,7);JSONObject sw=x.optJSONObject("swim");add(swim,text("计划 "+s(sw,"planned")+" 分钟 · 可分段完成",13,MUTED,false));if(!s(sw,"minutes").isEmpty()){gap(swim,8);add(swim,text("已记 "+s(sw,"minutes")+" 分钟  "+s(sw,"distance")+" 米",15,GREEN,true));}gap(swim,12);add(swim,action("记录游泳与恢复",false,()->swimDialog(x)));
        add(content,text("记录自动保存在本机。历史日期可补记，计划日期不会自动改变。",12,MUTED,false));
    }
    void exerciseCard(JSONObject session,JSONObject e,int index){
        LinearLayout c=card(content);if(e.optBoolean("archived")){add(c,text("历史动作 · 已移出计划",12,MUTED,true));gap(c,8);}LinearLayout header=horizontal();TextView name=text(String.format(Locale.ROOT,"%02d",index+1)+"  "+s(e,"name"),17,INK,true);header.addView(name,new LinearLayout.LayoutParams(0,-2,1));TextView more=text("···",24,MUTED,true);more.setPadding(dp(12),0,0,0);more.setContentDescription(s(e,"name")+"设置");more.setOnClickListener(v->exerciseMenu(session,index));add(header,more);add(c,header);gap(c,7);
        String target=e.optInt("low")+"–"+e.optInt("high")+s(e,"unit")+(s(e,"rir").isEmpty()?"":" · 留 "+s(e,"rir")+" 次余力");add(c,text(target+" · 休息 "+e.optInt("rest")+"秒",12,MUTED,false));
        if(!s(e,"note").isEmpty()){gap(c,6);add(c,text(s(e,"note"),12,MUTED,false));}
        gap(c,13);LinearLayout head=horizontal();for(String label:new String[]{"组","重量 kg",s(e,"unit").equals("米")?"距离":"数量 "+s(e,"unit"),"RIR",""}){TextView t=text(label,11,MUTED,false);t.setGravity(Gravity.CENTER);head.addView(t,new LinearLayout.LayoutParams(0,-2,label.isEmpty()?.5f:label.equals("组")?.5f:1));}add(c,head);gap(c,5);
        JSONArray sets=e.optJSONArray("actual");for(int j=0;j<sets.length();j++){
            JSONObject set=sets.optJSONObject(j);boolean done=set.optBoolean("done");LinearLayout row=horizontal();row.setPadding(0,dp(12),0,dp(12));row.setBackground(bg(done?PALE:BG,12));String[] vals={String.valueOf(j+1),s(set,"weight").isEmpty()?"—":s(set,"weight"),s(set,"quantity").isEmpty()?"—":s(set,"quantity"),s(set,"rir").isEmpty()?"—":s(set,"rir"),done?"✓":"＋"};
            for(int k=0;k<vals.length;k++){TextView value=text(vals[k],k==4?19:14,done?GREEN:INK,k==4);value.setGravity(Gravity.CENTER);row.addView(value,new LinearLayout.LayoutParams(0,-2,k==0||k==4?.5f:1));}
            final int setIndex=j;row.setContentDescription(s(e,"name")+" 第"+(j+1)+"组 "+(done?"已记录":"填写"));row.setOnClickListener(v->setEditor(session.optInt("id"),index,setIndex));add(c,row);gap(c,6);
            if(!e.optBoolean("archived")&&j<sets.length()-1){final int seconds=set.optInt("restSeconds",e.optInt("rest",90));TextView rest=text("◷  组间休息 "+seconds+"秒 · 点击开始",12,GREEN,false);rest.setGravity(Gravity.CENTER);rest.setPadding(0,dp(8),0,dp(8));rest.setOnClickListener(v->{if(seconds>0){startTimer(seconds);toast("休息计时已开始");}else toast("此组无需休息");});add(c,rest);gap(c,6);}
        }
        TextView plus=text("＋ 加一组",13,GREEN,true);plus.setPadding(0,dp(8),0,dp(3));plus.setOnClickListener(v->{try{JSONObject fresh=store.get("session",String.valueOf(session.optInt("id")));JSONArray a=exercises(fresh).getJSONObject(index).getJSONArray("actual");if(a.length()>=100){toast("每个动作最多记录100组");return;}a.put(new JSONObject().put("weight","").put("quantity","").put("rir","").put("done",false));store.saveSession(fresh);rerender();}catch(Exception ex){error(ex);}});if(!e.optBoolean("archived"))add(c,plus);
    }
    void rerender(){int y=scroll.getScrollY();build();scroll.post(()->scroll.scrollTo(0,y));}
    JSONObject previousSet(JSONObject current,JSONObject exercise,int setIndex){
        JSONArray a=exercise.optJSONArray("actual");for(int j=setIndex-1;j>=0;j--)if(!s(a.optJSONObject(j),"quantity").isEmpty())return a.optJSONObject(j);
        List<JSONObject> all=store.list("session");Collections.reverse(all);
        for(JSONObject other:all){if(s(other,"date").compareTo(s(current,"date"))>=0)continue;JSONArray es=exercises(other);for(int i=0;i<es.length();i++){JSONObject e=es.optJSONObject(i);if(!s(e,"name").equals(s(exercise,"name")))continue;JSONArray sets=e.optJSONArray("actual");for(int j=sets.length()-1;j>=0;j--)if(sets.optJSONObject(j).optBoolean("done"))return sets.optJSONObject(j);}}
        return null;
    }
    EditText input(LinearLayout parent,String label,String value,String hint,boolean numeric){label(parent,label);EditText f=new EditText(this);f.setTextSize(17);f.setTextColor(INK);f.setSingleLine(true);f.setPadding(dp(12),dp(8),dp(12),dp(8));f.setBackground(bg(BG,10));f.setHint(hint);f.setText(value);f.setInputType(numeric?android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL:android.text.InputType.TYPE_CLASS_TEXT);f.setSelectAllOnFocus(true);f.setContentDescription(label);parent.addView(f,new LinearLayout.LayoutParams(-1,dp(48)));gap(parent,14);return f;}
    LinearLayout dialogBody(){LinearLayout b=vertical();b.setPadding(dp(22),dp(12),dp(22),dp(4));return b;}
    EditText stepper(LinearLayout body,String title,String value,String hint,double step){label(body,title);LinearLayout row=horizontal();TextView minus=action("−",false,()->{});row.addView(minus,new LinearLayout.LayoutParams(dp(48),dp(48)));EditText f=new EditText(this);f.setText(value);f.setHint(hint);f.setTextSize(23);f.setTextColor(INK);f.setGravity(Gravity.CENTER);f.setSingleLine(true);f.setSelectAllOnFocus(true);f.setContentDescription(title);boolean isRir=title.startsWith("RIR");f.setInputType(isRir?android.text.InputType.TYPE_CLASS_NUMBER:value.isEmpty()||value.matches("\\d+(\\.\\d+)?")?android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL:android.text.InputType.TYPE_CLASS_TEXT);LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(52),1);fp.leftMargin=dp(8);fp.rightMargin=dp(8);row.addView(f,fp);TextView plus=action("＋",false,()->{});row.addView(plus,new LinearLayout.LayoutParams(dp(48),dp(48)));minus.setOnClickListener(v->adjust(f,-step));plus.setOnClickListener(v->adjust(f,step));add(body,row);
        if(!isRir){TextView mode=text("切换文字 / 数字输入",11,GREEN,false);mode.setPadding(0,dp(7),0,dp(5));mode.setOnClickListener(v->{boolean numeric=(f.getInputType()&android.text.InputType.TYPE_MASK_CLASS)==android.text.InputType.TYPE_CLASS_NUMBER;f.setInputType(numeric?android.text.InputType.TYPE_CLASS_TEXT:android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);f.requestFocus();f.selectAll();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(f,InputMethodManager.SHOW_IMPLICIT);});add(body,mode);}gap(body,13);return f;}
    void adjust(EditText f,double delta){String value=f.getText().toString().trim();try{double next=Math.max(0,(value.isEmpty()?0:Double.parseDouble(value))+delta);f.setText(number(next));}catch(NumberFormatException ex){toast("文字记录请直接编辑，例如“空杆”或“1来回”");}}
    String number(double n){return n==(long)n?String.valueOf((long)n):String.valueOf(n);}
    void setEditor(int id,int exerciseIndex,int setIndex){try{
        JSONObject x=store.get("session",String.valueOf(id));JSONObject e=exercises(x).getJSONObject(exerciseIndex);JSONObject set=e.getJSONArray("actual").getJSONObject(setIndex);JSONObject previous=previousSet(x,e,setIndex);
        boolean finishing=PlanOps.completesSession(x,exerciseIndex,setIndex);
        boolean prefill=s(set,"weight").isEmpty()&&s(set,"quantity").isEmpty()&&previous!=null;
        String weight=s(set,"weight"),qty=s(set,"quantity"),rir=s(set,"rir");if(prefill){weight=s(previous,"weight");qty=s(previous,"quantity");rir=s(e,"rir").isEmpty()?"":s(previous,"rir");}
        LinearLayout b=dialogBody();add(b,text(s(x,"date")+" · "+(prefill?"已带入上次记录，可直接修改":"每次保存均保留原始填写"),12,MUTED,false));gap(b,17);
        EditText wf=stepper(b,"重量 kg（哑铃/农夫行走按每手）",weight,"自重可留空，支持空杆",2.5);
        EditText qf=stepper(b,"数量 "+s(e,"unit")+(s(e,"name").contains("分腿")||s(e,"name").contains("弓步")?"（每侧）":""),qty,e.optInt("low")+"–"+e.optInt("high")+s(e,"unit")+(s(e,"unit").equals("米")?"，也支持1来回":""),s(e,"unit").equals("次")?1:5);
        EditText rf=stepper(b,"RIR（还能做几次，可留空）",rir,s(e,"rir").isEmpty()?"此动作可不填":"目标 "+s(e,"rir"),1);
        EditText restField=finishing?null:input(b,"本组完成后休息（秒）",String.valueOf(set.optInt("restSeconds",e.optInt("rest",90))),"0–1800，0表示不休息",true);if(restField!=null)restField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final boolean[] withRest={false};
        add(b,text("首次重量建议："+s(e,"load"),12,MUTED,false));gap(b,12);TextView message=text("",12,0xffb13a27,false);add(b,message);
        ScrollView sc=new ScrollView(this);sc.addView(b);AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(s(e,"name")+" · 第"+(setIndex+1)+"组").setView(sc).setNegativeButton("取消",null).setPositiveButton(finishing?(set.optBoolean("done")?"保存修改 ✓":"完成本次训练 ✓"):"直接完成 ✓",null);if(set.optBoolean("done"))builder.setNeutralButton("标为未完成",null);AlertDialog dialog=builder.create();setDialog=dialog;
        Runnable save=()->{try{
            String w=wf.getText().toString().trim(),q=qf.getText().toString().trim(),r=rf.getText().toString().trim();
            if(q.isEmpty()){message.setText("请填写次数、秒数或行走距离。");return;}if(q.length()>80||w.length()>80){message.setText("填写内容过长。");return;}
            if(q.matches("[-+]?\\d+(\\.\\d+)?")&&Double.parseDouble(q)<=0){message.setText("数量应大于0。");return;}
            if(w.matches("-\\d+(\\.\\d+)?")){message.setText("重量不能为负数。");return;}
            if(!r.isEmpty()&&(!r.matches("\\d+")||Integer.parseInt(r)>10)){message.setText("RIR请输入0–10，或留空。");return;}
            int rest;try{rest=restField==null?set.optInt("restSeconds",e.optInt("rest",90)):Integer.parseInt(restField.getText().toString().trim());}catch(NumberFormatException invalid){message.setText("休息时间请输入0–1800秒。");return;}if(rest<0||rest>1800){message.setText("休息时间请输入0–1800秒。");return;}
            JSONObject fresh=store.get("session",String.valueOf(id));boolean wasComplete=complete(fresh);JSONObject actual=exercises(fresh).getJSONObject(exerciseIndex).getJSONArray("actual").getJSONObject(setIndex);actual.put("weight",w).put("quantity",q).put("rir",r).put("done",true).put("restSeconds",rest).put("updatedAt",System.currentTimeMillis());store.saveSession(fresh);dialog.dismiss();boolean finished=complete(fresh);if(finished)stopTimer();else if(withRest[0]&&rest>0)startTimer(rest);rerender();if(finished&&!wasComplete)showCompletion(fresh);else toast("已保存第"+(setIndex+1)+"组");
        }catch(Exception ex){message.setText("保存失败，原记录未清除："+ex.getMessage());}};
        add(b,action(finishing?(set.optBoolean("done")?"保存修改 ✓":"完成本次训练 ✓"):"完成并开始休息 ◷",true,()->{withRest[0]=!finishing;save.run();}));gap(b,12);
        dialog.setOnShowListener(d->{dialog.getButton(-1).setOnClickListener(v->{withRest[0]=false;save.run();});if(set.optBoolean("done"))dialog.getButton(-3).setOnClickListener(v->{try{persistSet(id,exerciseIndex,setIndex,s(set,"weight"),s(set,"quantity"),s(set,"rir"),false);dialog.dismiss();rerender();}catch(Exception ex){error(ex);}});});dialog.show();
    }catch(Exception ex){error(ex);}}
    void showCompletion(JSONObject session){
        if(isFinishing()||isDestroyed())return;if(completionDialog!=null)completionDialog.dismiss();
        Dialog dialog=new Dialog(this);completionDialog=dialog;dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=vertical();panel.setPadding(dp(24),dp(12),dp(24),dp(24));panel.setBackground(bg(WHITE,24));
        panel.addView(new CompletionView(this),new LinearLayout.LayoutParams(-1,dp(190)));
        TextView title=text("本次训练完成！",25,INK,true);title.setGravity(Gravity.CENTER);title.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(panel,title);gap(panel,10);
        int[] counts=progress(session);TextView summary=text(s(session,"name")+"\n"+PlanOps.activeCount(session)+" 个动作 · "+counts[0]+" 组已完成",15,MUTED,false);summary.setGravity(Gravity.CENTER);add(panel,summary);gap(panel,24);
        add(panel,action("查看训练记录",true,dialog::dismiss));dialog.setContentView(panel);
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attributes=dialog.getWindow().getAttributes();attributes.dimAmount=.4f;dialog.getWindow().setAttributes(attributes);}
        dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(360)),-2);
    }
    void persistSet(int id,int ei,int si,String weight,String qty,String rir,boolean done)throws Exception{JSONObject x=store.get("session",String.valueOf(id));JSONObject set=exercises(x).getJSONObject(ei).getJSONArray("actual").getJSONObject(si);set.put("weight",weight).put("quantity",qty).put("rir",rir).put("done",done).put("updatedAt",System.currentTimeMillis());store.saveSession(x);}
    void startTimer(int sec){
        if(sec<=0)return;
        timerEnd=System.currentTimeMillis()+Math.min(sec,1800)*1000L;
        getPreferences(0).edit().putLong("timerEnd",timerEnd).putInt("timerSession",currentId).apply();
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},41);
        startForegroundService(new Intent(this,RestTimerService.class).putExtra("end",timerEnd).putExtra("session",currentId));
    }
    void stopTimer(){timerEnd=0;getPreferences(0).edit().remove("timerEnd").apply();stopService(new Intent(this,RestTimerService.class));getSystemService(NotificationManager.class).cancel(RestTimerService.ID);if(timerView!=null)timerView.setVisibility(View.GONE);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==41){if(results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED){long end=getPreferences(0).getLong("timerEnd",0);if(end>System.currentTimeMillis())startForegroundService(new Intent(this,RestTimerService.class).putExtra("end",end).putExtra("session",getPreferences(0).getInt("timerSession",-1)));}else toast("通知未开启，可在系统设置中允许练记通知；App内仍可查看计时。");}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.hasExtra("timerSession")){currentId=intent.getIntExtra("timerSession",-1);build();}}

    void exerciseMenu(JSONObject session,int ei){planner.exerciseMenu(session.optInt("id"),ei);}
    void addExercise(JSONObject session){planner.exerciseEditor(session.optInt("id"),-1);}

    void swimDialog(JSONObject session){JSONObject sw=session.optJSONObject("swim");LinearLayout b=dialogBody();add(b,text("时间可包含组间休息，不要求连续游。",12,MUTED,false));gap(b,14);EditText minutes=input(b,"游泳分钟",s(sw,"minutes"),"可留空",true),distance=input(b,"距离 米",s(sw,"distance"),"可留空",true),rpe=input(b,"游泳RPE 0–10",s(sw,"rpe"),"可留空",true),sleep=input(b,"睡眠小时",s(sw,"sleep"),"可留空",true),note=input(b,"备注",s(sw,"note"),"疲劳、酸痛或技术练习",false);ScrollView scroll=new ScrollView(this);scroll.addView(b);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("游泳与恢复").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(button->{try{EditText[] fields={minutes,distance,rpe,sleep};double[] max={600,100000,10,24};for(int i=0;i<fields.length;i++){String val=fields[i].getText().toString().trim();if(!val.isEmpty()){double n=Double.parseDouble(val);if(n<0||n>max[i]){fields[i].setError("请检查数值范围");return;}}}JSONObject fresh=store.get("session",String.valueOf(session.optInt("id")));JSONObject value=new JSONObject().put("planned",s(sw,"planned")).put("minutes",minutes.getText().toString().trim()).put("distance",distance.getText().toString().trim()).put("rpe",rpe.getText().toString().trim()).put("sleep",sleep.getText().toString().trim()).put("note",note.getText().toString().trim());fresh.put("swim",value);store.saveSession(fresh);dialog.dismiss();rerender();}catch(Exception ex){toast("请输入有效数字");}}));dialog.show();}
    interface DateChosen{void accept(LocalDate date);}
    void pickDate(LocalDate initial,DateChosen done){new DatePickerDialog(this,(v,y,m,d)->done.accept(LocalDate.of(y,m+1,d)),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();}
    void body(){smallTitle("关注变化，不盯一天。","体重看周均值，腰围保持同一位置测量。");add(content,action("＋ 记录今天的体测",true,()->metricDialog(today())));gap(content,20);List<JSONObject> metrics=store.list("metric");
        LinearLayout summary=card(content);String weight="—",waist="—";double total=0;int count=0;LocalDate cutoff=LocalDate.now().minusDays(6);for(JSONObject m:metrics){if(!s(m,"weight").isEmpty())weight=s(m,"weight");if(!s(m,"waist").isEmpty())waist=s(m,"waist");try{LocalDate d=LocalDate.parse(s(m,"date"));if(!d.isBefore(cutoff)&&!d.isAfter(LocalDate.now())&&!s(m,"weight").isEmpty()){total+=Double.parseDouble(s(m,"weight"));count++;}}catch(Exception ignored){}}
        add(summary,text("最近体重  "+weight+" kg",23,INK,true));gap(summary,10);add(summary,text("最近腰围  "+waist+" cm",16,MUTED,false));gap(summary,12);add(summary,text("最近7天均重  "+(count==0?"—":String.format(Locale.ROOT,"%.1f",total/count))+" kg   ·   "+count+"次测量",13,GREEN,true));
        if(metrics.size()>1){LinearLayout chart=card(content);label(chart,"体重趋势 kg");chart.addView(new Trend(this,metrics),new LinearLayout.LayoutParams(-1,dp(155)));}
        Collections.reverse(metrics);for(JSONObject m:metrics){LinearLayout c=card(content);add(c,text(dateLabel(s(m,"date")),13,MUTED,false));gap(c,8);add(c,text((s(m,"weight").isEmpty()?"—":s(m,"weight"))+" kg    /    "+(s(m,"waist").isEmpty()?"—":s(m,"waist"))+" cm",19,INK,true));if(!s(m,"note").isEmpty()){gap(c,7);add(c,text(s(m,"note"),12,MUTED,false));}c.setOnClickListener(v->metricDialog(s(m,"date")));}
        if(metrics.isEmpty()){gap(content,20);add(content,text("还没有体测记录。每天只需填一个体重，腰围每周测一次即可。",14,MUTED,false));}
    }
    void metricDialog(String initial){final LocalDate[] date={LocalDate.parse(initial)};JSONObject old=store.get("metric",initial);LinearLayout b=dialogBody();TextView pick=action(dateLabel(initial),false,()->{});add(b,pick);gap(b,16);EditText weight=input(b,"体重 kg",s(old,"weight"),"例如 80.5",true),waist=input(b,"腰围 cm",s(old,"waist"),"可留空",true),note=input(b,"备注",s(old,"note"),"可留空",false);
        pick.setOnClickListener(v->pickDate(date[0],chosen->{date[0]=chosen;pick.setText(dateLabel(chosen.toString()));JSONObject existing=store.get("metric",chosen.toString());weight.setText(s(existing,"weight"));waist.setText(s(existing,"waist"));note.setText(s(existing,"note"));}));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("体重与腰围").setView(b).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(button->{try{String w=weight.getText().toString().trim(),a=waist.getText().toString().trim();if(w.isEmpty()&&a.isEmpty()){toast("至少填写体重或腰围");return;}if(!w.isEmpty()&&(Double.parseDouble(w)<=0||Double.parseDouble(w)>500)){weight.setError("请检查体重");return;}if(!a.isEmpty()&&(Double.parseDouble(a)<=0||Double.parseDouble(a)>400)){waist.setError("请检查腰围");return;}store.saveMetric(new JSONObject().put("date",date[0].toString()).put("weight",w).put("waist",a).put("note",note.getText().toString().trim()));d.dismiss();rerender();toast("体测已保存");}catch(Exception ex){toast("请输入有效数字");}}));d.show();}
    void legacyMenu(){List<JSONObject> legacy=store.list("legacy");String[] names=new String[legacy.size()];for(int i=0;i<legacy.size();i++)names[i]=s(legacy.get(i),"name");new AlertDialog.Builder(this).setTitle("去年原始训练资料").setItems(names,(d,which)->legacySheet(legacy.get(which))).setNegativeButton("关闭",null).show();}
    void legacySheet(JSONObject sheet){LinearLayout b=dialogBody();EditText search=input(b,"搜索动作或内容","","例如 卧推、深蹲",false);LinearLayout result=vertical();ScrollView sc=new ScrollView(this);sc.addView(result);b.addView(sc,new LinearLayout.LayoutParams(-1,dp(350)));final Runnable[] render=new Runnable[1];render[0]=()->{result.removeAllViews();String q=search.getText().toString().trim();JSONArray cells=sheet.optJSONArray("cells");int shown=0;for(int i=0;i<cells.length();i++){JSONObject cell=cells.optJSONObject(i);if(!q.isEmpty()&&!s(cell,"value").contains(q))continue;String adr=s(cell,"cell");int row=Integer.parseInt(adr.replaceAll("[A-Z]",""));int column=0;for(char c:adr.replaceAll("[0-9]","").toCharArray())column=column*26+c-'A'+1;
            LinearLayout item=vertical();item.setPadding(0,dp(9),0,dp(9));add(item,text(adr+"   "+s(cell,"value"),14,INK,true));StringBuilder context=new StringBuilder();if(!q.isEmpty()){for(int j=0;j<cells.length();j++){JSONObject other=cells.optJSONObject(j);String oa=s(other,"cell");int orow=Integer.parseInt(oa.replaceAll("[A-Z]","")),oc=0;for(char c:oa.replaceAll("[0-9]","").toCharArray())oc=oc*26+c-'A'+1;if(orow==row&&Math.abs(oc-column)<=2&&!oa.equals(adr))context.append(oa).append(" ").append(s(other,"value")).append("   ");}if(context.length()>0)add(item,text(context.toString(),11,MUTED,false));}add(result,item);if(++shown>=100){add(result,text("仅显示前100项，请输入关键词缩小范围。",12,MUTED,false));break;}}};search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int st,int before,int count){render[0].run();}public void afterTextChanged(Editable e){}});render[0].run();new AlertDialog.Builder(this).setTitle(s(sheet,"name")).setView(b).setPositiveButton("关闭",null).show();}
    void dataMenu(){new AlertDialog.Builder(this).setTitle("数据由你保管").setMessage("完整备份包含计划、逐组记录、游泳、体测与去年原始资料。卸载前请导出备份；本App不上传数据。恢复时按最后修改时间合并，保留本机更新的记录。")
        .setPositiveButton("导出完整备份",(d,w)->export(false)).setNeutralButton("更多选项",(d,w)->new AlertDialog.Builder(this).setTitle("导出与恢复").setItems(new String[]{"导出训练CSV（Excel可打开）","从完整备份恢复","导出体测CSV"},(a,which)->{if(which==0)export(true);else if(which==1){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,102);}else exportMetrics();}).setNegativeButton("关闭",null).show()).setNegativeButton("关闭",null).show();}
    void beginExport(String payload,String title,String mime)throws IOException{beginExportBytes(payload.getBytes(StandardCharsets.UTF_8),title,mime);}
    void beginExportBytes(byte[] payload,String title,String mime)throws IOException{
        android.util.AtomicFile pending=new android.util.AtomicFile(new File(getFilesDir(),"pending-export"));FileOutputStream stream=pending.startWrite();
        try{stream.write(payload);pending.finishWrite(stream);}catch(IOException ex){pending.failWrite(stream);throw ex;}
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,title),101);
    }
    void export(boolean csv){try{beginExport(csv?store.trainingCsv():store.export().toString(2),"练记-"+(csv?"训练记录":"完整备份")+"-"+today()+(csv?".csv":".json"),csv?"text/csv":"application/json");}catch(Exception ex){error(ex);}}
    void exportMetrics(){try{StringBuilder out=new StringBuilder("\uFEFF日期,体重kg,腰围cm,备注\r\n");for(JSONObject m:store.list("metric"))out.append(Store.csv(s(m,"date"))).append(',').append(Store.csv(s(m,"weight"))).append(',').append(Store.csv(s(m,"waist"))).append(',').append(Store.csv(s(m,"note"))).append("\r\n");beginExport(out.toString(),"练记-体测-"+today()+".csv","text/csv");}catch(Exception ex){error(ex);}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(request==103){planner.read(uri);return;}try{if(request==101){byte[] payload=new android.util.AtomicFile(new File(getFilesDir(),"pending-export")).readFully();if(payload.length==0)throw new IOException("备份内容为空，请重新导出");try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("无法打开保存位置");out.write(payload);}toast("导出成功，请妥善保存");}else if(request==102){JSONObject backup=new JSONObject(Store.read(getContentResolver().openInputStream(uri),10*1024*1024));Store.validate(backup);new AlertDialog.Builder(this).setTitle("合并备份记录？").setMessage("备份包含 "+backup.getJSONArray("sessions").length()+" 次训练和 "+backup.getJSONArray("metrics").length()+" 条体测。不会清空本机数据；同一训练以最后修改时间较新的版本为准。").setNegativeButton("取消",null).setPositiveButton("合并",(d,w)->{try{int n=store.merge(backup);currentId=-1;build();toast("合并完成，更新 "+n+" 条记录");}catch(Exception ex){error(ex);}}).show();}}catch(Exception ex){error(ex);}}
    void toast(String t){Toast.makeText(this,t,Toast.LENGTH_SHORT).show();}
    void error(Exception e){new AlertDialog.Builder(this).setTitle("操作未完成").setMessage(e.getMessage()==null?e.toString():e.getMessage()).setPositiveButton("知道了",null).show();}
    @Override public void onBackPressed(){if(currentId!=-1){currentId=-1;build();}else if(!page.equals("训练")){page="训练";build();}else super.onBackPressed();}
    final class Trend extends View {
        final List<Float> values=new ArrayList<>();final List<String> dates=new ArrayList<>();final List<Long> days=new ArrayList<>();final Paint paint=new Paint(3);
        Trend(Context c,List<JSONObject> metrics){super(c);for(JSONObject m:metrics)try{if(!s(m,"weight").isEmpty()){values.add(Float.parseFloat(s(m,"weight")));dates.add(s(m,"date").substring(5));days.add(LocalDate.parse(s(m,"date")).toEpochDay());}}catch(Exception ignored){}}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);if(values.size()<2){paint.setColor(MUTED);paint.setTextSize(dp(13));canvas.drawText("再记录一次体重，即可查看趋势",0,dp(60),paint);return;}float min=Collections.min(values)-.5f,max=Collections.max(values)+.5f,left=dp(36),top=dp(12),width=getWidth()-left-dp(12),height=getHeight()-dp(44);paint.setTextSize(dp(10));paint.setColor(MUTED);canvas.drawText(String.format(Locale.ROOT,"%.1f",max),0,top+dp(5),paint);canvas.drawText(String.format(Locale.ROOT,"%.1f",min),0,top+height,paint);paint.setColor(LINE);canvas.drawLine(left,top+height,getWidth(),top+height,paint);Path path=new Path();
            float lastX=0,lastY=0;long span=Math.max(1,days.get(days.size()-1)-days.get(0));for(int i=0;i<values.size();i++){float x=left+width*(days.get(i)-days.get(0))/span,y=top+height-(values.get(i)-min)/(max-min)*height;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);lastX=x;lastY=y;}paint.setColor(GREEN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(lastX,lastY,dp(4),paint);paint.setColor(MUTED);canvas.drawText(dates.get(0),left,getHeight()-dp(8),paint);canvas.drawText(dates.get(dates.size()-1),getWidth()-dp(40),getHeight()-dp(8),paint);
        }
    }
}
