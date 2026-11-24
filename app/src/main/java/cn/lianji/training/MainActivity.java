package cn.lianji.training;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
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
import java.util.function.Consumer;

public class MainActivity extends Activity {
    static final int BG=Color.rgb(229,231,235),INK=Color.rgb(24,35,53),MUTED=Color.rgb(100,113,132),GREEN=Color.rgb(38,86,216),PALE=Color.rgb(226,234,252),LINE=Color.rgb(213,221,232),WHITE=Color.WHITE,SURFACE=Color.rgb(255,255,255);
    private static final DateTimeFormatter WORKOUT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final int CURRENT_PLAN_WEEK = -2;
    private static final int ALL_PLAN_WEEKS = -1;
    Store store;PlannerUi planner;AppUpdates updates;LinearLayout shell,content,nav;ScrollView scroll;TextView timerView;String page="训练";int currentId=-1;boolean historyMode=false;
    int planWeek = CURRENT_PLAN_WEEK;
    private PlanEditorUi planEditor;
    private String planEditorToken;
    final Handler handler=new Handler();long timerEnd=0;String exportText="";Dialog setDialog,completionDialog;
    final Runnable tick = new Runnable() {
        @Override public void run() {
            timerEnd = getPreferences(0).getLong("timerEnd", 0);
            long seconds = Math.max(0, (timerEnd - System.currentTimeMillis() + 999) / 1000);
            if (timerView != null) {
                timerView.setVisibility(timerEnd > 0 ? View.VISIBLE : View.GONE);
                if (timerEnd > 0) {
                    String label = seconds > 0
                        ? String.format(Locale.ROOT, "休息 %d:%02d", seconds / 60, seconds % 60) : "休息结束";
                    if (!TextUtils.equals(timerView.getText(), label)) {
                        timerView.setText(label);
                        timerView.setContentDescription(seconds > 0 ? label + "，结束休息计时" : label);
                    }
                }
            }
            if (timerEnd > 0 && seconds == 0) {
                timerEnd = 0;
                getPreferences(0).edit().remove("timerEnd").apply();
            }
            handler.postDelayed(this, 500);
        }
    };
    @Override public void onCreate(Bundle state){super.onCreate(state);try{store=new Store(this);store.seed();planner=new PlannerUi(this);timerEnd=getPreferences(0).getLong("timerEnd",0);if(getIntent().hasExtra("timerSession"))currentId=getIntent().getIntExtra("timerSession",-1);if(state!=null){page=state.getString("page","训练");currentId=state.getInt("session",-1);planWeek=state.getInt("planWeek",CURRENT_PLAN_WEEK);restorePlanEditor(state);}build();handler.post(tick);updates=new AppUpdates(this);updates.automatic();}catch(Exception e){new AlertDialog.Builder(this).setTitle("暂时无法打开记录").setMessage("为保护数据，未重置任何记录。\n"+e.getMessage()).setPositiveButton("关闭",(d,w)->finish()).setCancelable(false).show();}}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("page",page);state.putInt("session",currentId);state.putInt("planWeek",planWeek);savePlanEditorState(state);}
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
    Drawable icon(int drawable, int color) {
        Drawable icon = getDrawable(drawable).mutate();
        icon.setTint(color);
        return icon;
    }
    ImageButton iconButton(int drawable, String label, Runnable click) {
        return iconButton(drawable, label, GREEN, PALE, v -> click.run());
    }
    ImageButton iconButton(int drawable, String label, int tint, int fill, View.OnClickListener click) {
        ImageButton button = new ImageButton(this);
        button.setImageDrawable(icon(drawable, tint));
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x25305ac4),
            bg(fill, 14), bg(WHITE, 14)));
        button.setContentDescription(label);
        button.setTooltipText(label);
        button.setOnClickListener(click);
        return button;
    }
    LinearLayout card(LinearLayout parent){LinearLayout c=vertical();c.setPadding(dp(18),dp(17),dp(18),dp(17));c.setBackground(bg(SURFACE,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(c,p);return c;}
    LinearLayout titleRow(String title, int size, View... controls) {
        LinearLayout row = horizontal();
        TextView heading = text(title, size, INK, true);
        heading.setMaxLines(2);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        heading.setAccessibilityHeading(true);
        row.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        for (View control : controls) add(row, control);
        return row;
    }
    void label(LinearLayout p,String title){add(p,text(title,12,MUTED,true));gap(p,7);}
    String s(JSONObject o,String k){return Store.str(o,k);}
    JSONArray exercises(JSONObject session){return session.optJSONArray("exercises");}
    JSONObject session(){return store.get("session",String.valueOf(currentId));}
    String today(){return LocalDate.now().toString();}
    String dateLabel(String date){try{return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M月d日 EEEE",Locale.SIMPLIFIED_CHINESE));}catch(Exception e){return date;}}
    int[] progress(JSONObject session){int done=0,total=0;JSONArray es=exercises(session);if(es==null)return new int[]{0,0};for(int i=0;i<es.length();i++){if(es.optJSONObject(i).optBoolean("archived"))continue;JSONArray sets=es.optJSONObject(i).optJSONArray("actual");for(int j=0;j<sets.length();j++){total++;if(sets.optJSONObject(j).optBoolean("done"))done++;}}return new int[]{done,total};}
    boolean complete(JSONObject session){return PlanOps.completesSession(session,-1,-1);}
    void build(){
        shell=vertical();shell.setBackgroundColor(BG);shell.setOnApplyWindowInsetsListener((v,insets)->{shell.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        setContentView(shell);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);shell.requestApplyInsets();
        if (planEditor != null) { planEditor.render(); return; }
        JSONObject workout = currentId == -1 ? null : session();
        if (workout == null) currentId = -1;
        addToolbar(workout);
        timerView = text("", 14, GREEN, true);
        timerView.setGravity(Gravity.CENTER);
        timerView.setMinHeight(dp(48));
        timerView.setPadding(dp(16), 0, dp(16), 0);
        timerView.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_timer, GREEN),
            null, icon(R.drawable.ic_close, GREEN), null);
        timerView.setBackgroundColor(PALE);
        timerView.setVisibility(View.GONE);
        timerView.setTooltipText("结束休息计时");
        timerView.setOnClickListener(v -> stopTimer());
        add(shell, timerView);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);content=vertical();content.setPadding(dp(22),dp(16),dp(22),dp(24));scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        addNavigation();
        if(workout!=null)showWorkout(workout);else if(page.equals("训练"))home();else if(page.equals("计划"))plan();else if(page.equals("记录"))history();else body();
    }
    private void addToolbar(JSONObject workout) {
        LinearLayout top;
        if (workout == null) {
            top = titleRow("练记", 22,
                iconButton(R.drawable.ic_backup, "备份与导出", GREEN, Color.TRANSPARENT, v -> dataMenu()),
                iconButton(R.drawable.ic_update, "检查更新", GREEN, Color.TRANSPARENT, v -> {
                    if (updates != null) updates.check(true);
                }));
            top.setPadding(dp(22), dp(4), dp(12), dp(4));
        } else {
            top = titleRow(s(workout, "name"), 22,
                iconButton(R.drawable.ic_edit, "编辑训练计划", () -> planner.editPlan(workout.optInt("id"))));
            ImageButton back = iconButton(R.drawable.ic_back, "返回" + page, GREEN, Color.TRANSPARENT, v -> {
                currentId = -1;
                build();
            });
            top.addView(back, 0);
            top.setPadding(dp(8), dp(4), dp(12), dp(4));
        }
        top.setBackgroundColor(SURFACE);
        add(shell, top);
    }
    private void addNavigation() {
        nav = horizontal();
        nav.setPadding(dp(12), dp(6), dp(12), dp(8));
        nav.setBackgroundColor(SURFACE);
        addDestination("训练", R.drawable.ic_workout);
        addDestination("计划", R.drawable.ic_calendar);
        addDestination("记录", R.drawable.ic_history);
        addDestination("身体", R.drawable.ic_scale);
        add(shell, nav);
    }
    private void addDestination(String item, int drawable) {
        boolean selected = item.equals(page);
        int tint = selected ? GREEN : MUTED;
        TextView tab = text(item, 12, tint, selected);
        tab.setGravity(Gravity.CENTER);
        tab.setMinHeight(dp(52));
        tab.setCompoundDrawablesRelativeWithIntrinsicBounds(null, icon(drawable, tint), null, null);
        tab.setCompoundDrawablePadding(dp(2));
        tab.setPadding(0, dp(4), 0, dp(4));
        if (selected) tab.setBackground(bg(PALE, 13));
        tab.setSelected(selected);
        tab.setOnClickListener(v -> {
            if (item.equals("计划")) planWeek = CURRENT_PLAN_WEEK;
            page = item;
            currentId = -1;
            build();
        });
        nav.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
    }
    void openPlanEditor(JSONObject original) throws Exception {
        planEditorToken = UUID.randomUUID().toString();
        planEditor = new PlanEditorUi(this, new PlanEditorDraft(original, today()), null);
        build();
    }

    void openPlanAction(JSONObject original, int index) throws Exception {
        openPlanEditor(original);
        planEditor.openAction(index);
    }

    void closePlanEditor(int savedId, boolean changeLocation) {
        if (planEditor == null) return;
        int editingId = planEditor.draft.session().optInt("id");
        int previousScroll = planEditor.originScroll;
        if (changeLocation) currentId = editingId < 0 ? savedId : -1;
        clearPlanEditor();
        build();
        if (!changeLocation) {
            ScrollView frame = scroll;
            frame.post(() -> frame.scrollTo(0, previousScroll));
        }
    }

    void openCopiedPlan(int id) {
        clearPlanEditor();
        openSession(id);
    }

    private void clearPlanEditor() {
        planEditor = null;
        if (planEditorToken != null) planEditorFile().delete();
        planEditorToken = null;
    }

    private android.util.AtomicFile planEditorFile() {
        return new android.util.AtomicFile(new File(getCacheDir(), "plan-editor-" + planEditorToken));
    }

    private void savePlanEditorState(Bundle state) {
        if (planEditor == null) return;
        android.util.AtomicFile file = planEditorFile();
        FileOutputStream out = null;
        try {
            out = file.startWrite();
            out.write(planEditor.draft.state().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
            state.putString("planEditorToken", planEditorToken);
            state.putBundle("planEditorView", planEditor.saveViewState());
        } catch (Exception ex) {
            if (out != null) file.failWrite(out);
            android.util.Log.w("PlanEditor", "Unable to preserve editor state", ex);
        }
    }

    private void restorePlanEditor(Bundle state) {
        String token = state.getString("planEditorToken");
        Bundle view = state.getBundle("planEditorView");
        if (token == null || !token.matches("[0-9a-f-]{36}") || view == null) return;
        planEditorToken = token;
        try {
            String contents = new String(planEditorFile().readFully(), StandardCharsets.UTF_8);
            planEditor = new PlanEditorUi(this, new PlanEditorDraft(contents), view);
        } catch (Exception ex) {
            clearPlanEditor();
            toast("未能恢复编辑页面，已保存的计划不受影响。");
        }
    }

    void home(){
        String date = today();
        add(content, titleRow("训练", 28,
            iconButton(R.drawable.ic_scale, "记录体重与腰围", () -> metricDialog(date))));
        add(content, text(dateLabel(date), 13, MUTED, false));
        gap(content, 18);
        List<JSONObject> sessions = store.list("session");
        List<JSONObject> upcoming = new ArrayList<>();
        String upcomingDate = null;
        JSONObject last = null;
        for (JSONObject session : sessions) {
            if (PlanOps.hasRecords(session)) last = session;
            String plannedDate = s(session, "date");
            if (session.optBoolean("archived") || plannedDate.compareTo(date) < 0) continue;
            if (upcomingDate == null) upcomingDate = plannedDate;
            if (upcomingDate.equals(plannedDate)) upcoming.add(session);
        }
        if (upcoming.isEmpty()) {
            LinearLayout empty = card(content);
            add(empty, text(sessions.isEmpty() ? "添加训练计划" : "暂无接下来的训练", 20, INK, true));
            gap(empty, 14);
            add(empty, action("新建训练", true, planner::createSession));
            gap(empty, 8);
            add(empty, action("导入Excel计划", false, planner::pickExcel));
        } else {
            boolean today = date.equals(upcomingDate);
            label(content, today ? "今天的训练" : "下一训练日 · " + dateLabel(upcomingDate));
            for (JSONObject session : upcoming) {
                LinearLayout workout = card(content);
                add(workout, text(s(session, "name"), 22, INK, true));
                gap(workout, 8);
                int[] p = progress(session);
                String plannedSwim = s(session.optJSONObject("swim"), "planned");
                List<String> details = new ArrayList<>();
                if (p[1] > 0) details.add(p[0] + " / " + p[1] + " 组已完成");
                if (!plannedSwim.isEmpty()) details.add("计划游泳 " + plannedSwim + " 分钟");
                if (!details.isEmpty()) {
                    add(workout, text(TextUtils.join(" · ", details), 14, MUTED, false));
                    gap(workout, 12);
                }
                int id = session.optInt("id");
                if (!today) {
                    add(workout, action("查看训练", true, () -> openSession(id)));
                } else if (complete(session)) {
                    add(workout, action("查看今日记录", true, () -> openSession(id)));
                } else if (session.optLong(PlanOps.STARTED_AT) > 0) {
                    add(workout, action("继续训练", true, () -> openSession(id)));
                } else {
                    add(workout, action("开始训练", true, () -> startWorkout(id)));
                }
            }
        }
        if (last != null) {
            gap(content, 8);
            label(content, "最近一次记录");
            sessionCard(content, last, false);
        }
    }
    void sessionCard(LinearLayout parent, JSONObject session, boolean inPlan) {
        LinearLayout card = card(parent);
        LinearLayout row = horizontal();
        TextView title = text(s(session, "name"), 17, INK, true);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        int id = session.optInt("id");
        int[] p = progress(session);
        boolean recorded = PlanOps.hasRecords(session);
        boolean started = session.optLong(PlanOps.STARTED_AT) > 0;
        String status = complete(session) ? "已完成" : p[0] > 0 ? "已完成 " + p[0] + " 组"
            : started ? "已开始" : recorded ? "有记录" : "待记录";
        if (inPlan) {
            add(row, iconButton(R.drawable.ic_edit, "编辑 " + s(session, "name") + " 的计划", GREEN, Color.TRANSPARENT,
                v -> planner.editPlan(id)));
        } else {
            add(row, text(status, 12, recorded ? GREEN : MUTED, true));
        }
        add(card, row);
        if (!inPlan) gap(card, 7);
        int week = session.optInt("week");
        String schedule = dateLabel(s(session, "date")) + (week > 0 ? " · 第" + week + "周" : "");
        if (inPlan && (recorded || started)) schedule += " · " + status;
        add(card, text(schedule, 12, MUTED, false));
        JSONObject swim = session.optJSONObject("swim");
        String plannedSwim = s(swim, "planned");
        String actualSwim = swimSummary(swim);
        if (inPlan) {
            gap(card, 8);
            List<String> details = new ArrayList<>();
            int actionCount = PlanOps.activeCount(session);
            if (actionCount > 0) details.add(actionCount + " 个动作 · " + p[1] + " 组");
            if (!plannedSwim.isEmpty()) details.add("计划游泳 " + plannedSwim + " 分钟");
            if (!details.isEmpty()) add(card, text(TextUtils.join(" · ", details), 12, MUTED, false));
        } else {
            addWorkoutTimes(card, session);
            if (!actualSwim.isEmpty()) {
                gap(card, 8);
                add(card, text(actualSwim, 12, MUTED, false));
            }
        }
        card.setOnClickListener(v -> openSession(id));
        if (session.optBoolean("archived")) {
            gap(card, 7);
            add(card, text("已移出计划 · 历史记录保留", 12, MUTED, false));
        }
    }
    void addWorkoutTimes(LinearLayout parent, JSONObject session) {
        long startedAt = session.optLong(PlanOps.STARTED_AT);
        long endedAt = session.optLong(PlanOps.ENDED_AT);
        ZoneId zone = ZoneId.systemDefault();
        if (startedAt > 0) {
            gap(parent, 7);
            add(parent, text("开始  " + WORKOUT_TIME.format(Instant.ofEpochMilli(startedAt).atZone(zone)), 13, MUTED, false));
        }
        if (endedAt > 0) {
            gap(parent, 7);
            add(parent, text("结束  " + WORKOUT_TIME.format(Instant.ofEpochMilli(endedAt).atZone(zone)), 13, MUTED, false));
        }
    }
    String swimSummary(JSONObject swim) {
        List<String> details = new ArrayList<>();
        String minutes = s(swim, "minutes"), distance = s(swim, "distance");
        String rpe = s(swim, "rpe"), sleep = s(swim, "sleep"), note = s(swim, "note");
        if (!minutes.isEmpty()) details.add("游泳 " + minutes + " 分钟");
        if (!distance.isEmpty()) details.add(distance + " 米");
        if (!rpe.isEmpty()) details.add("RPE " + rpe);
        if (!sleep.isEmpty()) details.add("睡眠 " + sleep + " 小时");
        if (!note.isEmpty()) details.add(note);
        return TextUtils.join(" · ", details);
    }
    void plan(){
        add(content, titleRow("训练计划", 28,
            iconButton(R.drawable.ic_import, "导入 Excel", GREEN, Color.TRANSPARENT, v -> planner.pickExcel()),
            iconButton(R.drawable.ic_spreadsheet, "Excel 模板与导出", GREEN, Color.TRANSPARENT, v -> planner.excelMenu()),
            iconButton(R.drawable.ic_add, "新建训练", planner::createSession)));
        gap(content, 16);
        List<JSONObject> sessions = store.list("session");
        int maxWeek = 12;
        for (JSONObject session : sessions) maxWeek = Math.min(52, Math.max(maxWeek, session.optInt("week")));
        HorizontalScrollView tabs = new HorizontalScrollView(this);
        tabs.setHorizontalScrollBarEnabled(false);
        LinearLayout weeks = horizontal();
        addPlanWeekTab(weeks, "本周", CURRENT_PLAN_WEEK);
        addPlanWeekTab(weeks, "全部", ALL_PLAN_WEEKS);
        for (int week = 1; week <= maxWeek; week++) addPlanWeekTab(weeks, "第" + week + "周", week);
        tabs.addView(weeks);
        add(content, tabs);
        int selectedIndex = planWeek == CURRENT_PLAN_WEEK ? 0 : planWeek == ALL_PLAN_WEEKS ? 1 : planWeek + 1;
        View selectedTab = weeks.getChildAt(selectedIndex);
        if (selectedIndex > 0 && selectedTab != null) {
            tabs.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    tabs.removeOnLayoutChangeListener(this);
                    tabs.scrollTo(selectedTab.getLeft() - dp(7), 0);
                }
            });
        }
        gap(content, 18);
        List<JSONObject> visible = planWeek == CURRENT_PLAN_WEEK
            ? PlanOps.sessionsInWeek(sessions, LocalDate.now()) : sessions;
        int count = 0;
        for (JSONObject session : visible) {
            if (session.optBoolean("archived") || (planWeek >= 0 && session.optInt("week") != planWeek)) continue;
            sessionCard(content, session, true);
            count++;
        }
        if(count==0)add(content,text(planWeek == CURRENT_PLAN_WEEK
            ? "本周暂无训练" : "暂无训练",14,MUTED,false));
    }
    void addPlanWeekTab(LinearLayout weeks, String label, int week) {
        TextView button = action(label, week == planWeek, () -> {
            planWeek = week;
            build();
        });
        button.setSelected(week == planWeek);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-2, -2);
        if (weeks.getChildCount() > 0) layout.leftMargin = dp(7);
        weeks.addView(button, layout);
    }
    void history(){
        add(content, titleRow("训练记录", 28));
        gap(content, 16);
        int count=0;List<JSONObject> all=store.list("session");Collections.reverse(all);for(JSONObject x:all)if(PlanOps.hasRecords(x)){sessionCard(content,x,false);count++;}
        if(count==0){LinearLayout empty=card(content);add(empty,text("暂无记录",18,INK,true));gap(empty,8);add(empty,text("开始训练或保存训练数据后，可在这里查看。",13,MUTED,false));}
        if(!store.list("legacy").isEmpty()){gap(content,12);add(content,action("查看去年Excel历史资料 →",false,this::legacyMenu));}
    }
    void openSession(int id){currentId=id;build();}
    void startWorkout(int id) {
        try {
            JSONObject session = store.get("session", String.valueOf(id));
            if (session != null && PlanOps.startWorkout(session, System.currentTimeMillis())) {
                store.saveSession(session);
            }
            openSession(id);
        } catch (Exception ex) {
            error(ex);
        }
    }
    void showWorkout(JSONObject x){
        int week = x.optInt("week");
        String phase = week == 1 ? "恢复期" : week == 0 ? "未分周" : "第" + week + "周";
        add(content, text(dateLabel(s(x, "date")) + " · " + phase, 13, MUTED, false));
        int id = x.optInt("id");
        if (x.optLong(PlanOps.STARTED_AT) > 0) {
            addWorkoutTimes(content, x);
        } else if (!x.optBoolean("archived") && !complete(x)) {
            gap(content, 12);
            add(content, action("开始训练", true, () -> startWorkout(id)));
        }
        int[] p = progress(x);
        if (p[1] > 0) {
            gap(content, 16);
            add(content, text(p[0] + " / " + p[1] + " 组已记录", 14, GREEN, true));
            gap(content, 8);
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(p[1]);
            bar.setProgress(p[0]);
            bar.setProgressTintList(ColorStateList.valueOf(GREEN));
            content.addView(bar, new LinearLayout.LayoutParams(-1, dp(5)));
        }
        gap(content, 16);
        add(content, titleRow("训练动作", 20, iconButton(R.drawable.ic_add, "添加动作", () -> addExercise(x))));
        gap(content, 8);
        ExerciseReorder reorder = !page.equals("记录") && !x.optBoolean("archived") && PlanOps.activeCount(x) > 1
            ? new ExerciseReorder(this, (from, to) -> reorderWorkout(x, from, to)) : null;
        JSONArray es = exercises(x);
        for (int i = 0; i < es.length(); i++) exerciseCard(x, es.optJSONObject(i), i, reorder);
        LinearLayout swim = card(content);
        add(swim, titleRow("游泳与恢复", 18,
            iconButton(R.drawable.ic_edit, "记录游泳与恢复", GREEN, Color.TRANSPARENT, v -> swimDialog(x))));
        JSONObject sw = x.optJSONObject("swim");
        String plannedSwim = s(sw, "planned");
        if (!plannedSwim.isEmpty()) {
            gap(swim, 7);
            add(swim, text("计划游泳 " + plannedSwim + " 分钟", 13, MUTED, false));
        }
        String actualSwim = swimSummary(sw);
        if (!actualSwim.isEmpty()) {
            gap(swim, 8);
            add(swim, text(actualSwim, 15, GREEN, true));
        }
    }
    private void reorderWorkout(JSONObject shown, int from, int to) {
        try {
            JSONObject fresh = session();
            if (fresh == null || !fresh.toString().equals(shown.toString())) {
                rerender();
                toast("训练已更新，请重新拖动");
                return;
            }
            PlanOps.reorderExercises(fresh, from, to);
            store.savePlans(Collections.singletonList(fresh), false);
            rerender();
        } catch (Exception ex) {
            error(ex);
        }
    }

    void exerciseCard(JSONObject session, JSONObject e, int index, ExerciseReorder reorder) {
        LinearLayout card = card(content);
        boolean archived = e.optBoolean("archived");
        String name = s(e, "name");
        int id = session.optInt("id");
        if (archived) add(card, text("历史动作 · 已移出计划", 12, MUTED, true));
        LinearLayout heading = titleRow(name, 17, iconButton(R.drawable.ic_edit, "编辑 " + name,
            GREEN, Color.TRANSPARENT, v -> planner.editAction(id, index)));
        if (reorder != null && !archived) reorder.addHandle(heading, card, index, name);
        add(card, heading);
        String unit = s(e, "unit");
        String target = e.optInt("low") + "–" + e.optInt("high") + unit;
        if (!s(e, "rir").isEmpty()) target += " · RIR " + s(e, "rir");
        add(card, text(target + " · 休息 " + e.optInt("rest") + " 秒", 12, MUTED, false));
        gap(card, 13);
        LinearLayout columns = horizontal();
        String[] labels = {"组", "重量 kg", unit.equals("米") ? "距离" : "数量 " + unit, "RIR", ""};
        for (int column = 0; column < labels.length; column++) {
            TextView label = text(labels[column], 11, MUTED, false);
            label.setGravity(Gravity.CENTER);
            columns.addView(label, new LinearLayout.LayoutParams(0, -2, column == 0 || column == 4 ? .5f : 1));
        }
        add(card, columns);
        gap(card, 5);
        JSONArray sets = e.optJSONArray("actual");
        for (int j = 0; j < sets.length(); j++) {
            JSONObject set = sets.optJSONObject(j);
            boolean done = set.optBoolean("done");
            int tint = done ? GREEN : INK;
            LinearLayout row = horizontal();
            row.setMinimumHeight(dp(48));
            row.setPadding(0, dp(8), 0, dp(8));
            row.setBackground(bg(done ? PALE : BG, 12));
            String[] values = {String.valueOf(j + 1), s(set, "weight"), s(set, "quantity"), s(set, "rir")};
            for (int k = 0; k < values.length; k++) {
                TextView value = text(values[k].isEmpty() ? "—" : values[k], 14, tint, false);
                value.setGravity(Gravity.CENTER);
                row.addView(value, new LinearLayout.LayoutParams(0, -2, k == 0 ? .5f : 1));
            }
            ImageView mark = new ImageView(this);
            mark.setImageDrawable(icon(done ? R.drawable.ic_check : R.drawable.ic_add, tint));
            mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(mark, new LinearLayout.LayoutParams(0, dp(24), .5f));
            final int setIndex = j;
            row.setContentDescription(name + " 第" + (j + 1) + "组 " + (done ? "已记录" : "填写"));
            row.setOnClickListener(v -> setEditor(id, index, setIndex));
            add(card, row);
            gap(card, 4);
            if (!archived && j < sets.length() - 1) {
                add(card, restControl(set.optInt("restSeconds", e.optInt("rest", 90))));
                gap(card, 4);
            }
        }
        if (!archived) {
            ImageButton extra = iconButton(R.drawable.ic_add_set, name + " 加一组", GREEN, Color.TRANSPARENT,
                v -> addWorkoutSet(id, index));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) extra.getLayoutParams();
            params.gravity = Gravity.END;
            add(card, extra);
        }
    }

    private View restControl(int seconds) {
        TextView rest = text(seconds + " 秒", 12, GREEN, true);
        rest.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_timer, GREEN), null, null, null);
        rest.setCompoundDrawablePadding(dp(6));
        rest.setPadding(dp(12), 0, dp(12), 0);
        rest.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(48));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        rest.setLayoutParams(params);
        rest.setContentDescription("开始休息 " + seconds + " 秒");
        rest.setTooltipText("开始休息计时");
        rest.setOnClickListener(v -> {
            if (seconds == 0) {
                toast("此组无需休息");
                return;
            }
            startTimer(seconds);
            toast("休息计时已开始");
        });
        return rest;
    }

    private void addWorkoutSet(int id, int index) {
        try {
            JSONObject fresh = store.get("session", String.valueOf(id));
            JSONArray sets = exercises(fresh).getJSONObject(index).getJSONArray("actual");
            if (sets.length() >= 100) {
                toast("每个动作最多记录100组");
                return;
            }
            sets.put(new JSONObject().put("weight", "").put("quantity", "").put("rir", "").put("done", false));
            store.saveSession(fresh);
            rerender();
        } catch (Exception ex) {
            error(ex);
        }
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
    EditText stepper(LinearLayout body, String title, String value, String hint, double step) {
        EditText field = new EditText(this);
        field.setText(value);
        field.setHint(hint);
        field.setTextSize(23);
        field.setTextColor(INK);
        field.setGravity(Gravity.CENTER);
        field.setSingleLine(true);
        field.setSelectAllOnFocus(true);
        field.setContentDescription(title);
        boolean isRir = title.startsWith("RIR");
        boolean numeric = value.isEmpty() || value.matches("\\d+(\\.\\d+)?");
        int decimal = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        field.setInputType(isRir ? InputType.TYPE_CLASS_NUMBER : numeric ? decimal : InputType.TYPE_CLASS_TEXT);
        if (isRir) {
            label(body, title);
        } else {
            LinearLayout caption = horizontal();
            caption.addView(text(title, 12, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
            ImageButton mode = iconButton(R.drawable.ic_keyboard, numeric ? "切换文字输入" : "切换数字输入",
                GREEN, Color.TRANSPARENT, v -> {
                    boolean wasNumeric = (field.getInputType() & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER;
                    field.setInputType(wasNumeric ? InputType.TYPE_CLASS_TEXT : decimal);
                    String next = wasNumeric ? "切换数字输入" : "切换文字输入";
                    v.setContentDescription(next);
                    v.setTooltipText(next);
                    field.requestFocus();
                    field.selectAll();
                    InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    keyboard.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
                });
            add(caption, mode);
            add(body, caption);
        }
        LinearLayout row = horizontal();
        add(row, iconButton(R.drawable.ic_minus, "减少 " + title, () -> adjust(field, -step)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1);
        params.setMarginStart(dp(8));
        params.setMarginEnd(dp(8));
        row.addView(field, params);
        add(row, iconButton(R.drawable.ic_add, "增加 " + title, () -> adjust(field, step)));
        add(body, row);
        gap(body, 13);
        return field;
    }
    void adjust(EditText f,double delta){String value=f.getText().toString().trim();try{double next=Math.max(0,(value.isEmpty()?0:Double.parseDouble(value))+delta);f.setText(number(next));}catch(NumberFormatException ex){toast("文字记录请直接编辑，例如“空杆”或“1来回”");}}
    String number(double n){return n==(long)n?String.valueOf((long)n):String.valueOf(n);}
    void setEditor(int id,int exerciseIndex,int setIndex){try{
        JSONObject x=store.get("session",String.valueOf(id));JSONObject e=exercises(x).getJSONObject(exerciseIndex);JSONObject set=e.getJSONArray("actual").getJSONObject(setIndex);JSONObject previous=previousSet(x,e,setIndex);
        boolean finishing=PlanOps.completesSession(x,exerciseIndex,setIndex);
        boolean prefill=s(set,"weight").isEmpty()&&s(set,"quantity").isEmpty()&&previous!=null;
        String weight=s(set,"weight"),qty=s(set,"quantity"),rir=s(set,"rir");if(prefill){weight=s(previous,"weight");qty=s(previous,"quantity");rir=s(e,"rir").isEmpty()?"":s(previous,"rir");}
        LinearLayout b=dialogBody();
        if (prefill) {
            add(b, text("已带入上次记录", 12, MUTED, false));
            gap(b, 6);
        }
        EditText wf=stepper(b,"重量 kg（哑铃/农夫行走按每手）",weight,"自重可留空，支持空杆",2.5);
        EditText qf=stepper(b,"数量 "+s(e,"unit")+(s(e,"name").contains("分腿")||s(e,"name").contains("弓步")?"（每侧）":""),qty,e.optInt("low")+"–"+e.optInt("high")+s(e,"unit")+(s(e,"unit").equals("米")?"，也支持1来回":""),s(e,"unit").equals("次")?1:5);
        EditText rf=stepper(b,"RIR（还能做几次，可留空）",rir,s(e,"rir").isEmpty()?"此动作可不填":"目标 "+s(e,"rir"),1);
        EditText restField=finishing?null:input(b,"本组完成后休息（秒）",String.valueOf(set.optInt("restSeconds",e.optInt("rest",90))),"0–1800，0表示不休息",true);if(restField!=null)restField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final boolean[] withRest={false};
        TextView message = text("", 12, 0xffb13a27, false);
        message.setVisibility(View.GONE);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(b, message);
        ScrollView sc=new ScrollView(this);sc.addView(b);AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(s(e,"name")+" · 第"+(setIndex+1)+"组").setView(sc).setNegativeButton("取消",null).setPositiveButton(finishing?(set.optBoolean("done")?"保存修改":"完成本次训练"):"直接完成",null);if(set.optBoolean("done"))builder.setNeutralButton("标为未完成",null);AlertDialog dialog=builder.create();setDialog=dialog;
        Consumer<String> showError = issue -> {
            message.setText(issue);
            message.setVisibility(View.VISIBLE);
            sc.post(() -> sc.smoothScrollTo(0, message.getBottom()));
        };
        Runnable save=()->{try{
            String w=wf.getText().toString().trim(),q=qf.getText().toString().trim(),r=rf.getText().toString().trim();
            if(q.isEmpty()){showError.accept("请填写次数、秒数或行走距离。");return;}if(q.length()>80||w.length()>80){showError.accept("填写内容过长。");return;}
            if(q.matches("[-+]?\\d+(\\.\\d+)?")&&Double.parseDouble(q)<=0){showError.accept("数量应大于0。");return;}
            if(w.matches("-\\d+(\\.\\d+)?")){showError.accept("重量不能为负数。");return;}
            if(!r.isEmpty()&&(!r.matches("\\d+")||Integer.parseInt(r)>10)){showError.accept("RIR请输入0–10，或留空。");return;}
            int rest;try{rest=restField==null?set.optInt("restSeconds",e.optInt("rest",90)):Integer.parseInt(restField.getText().toString().trim());}catch(NumberFormatException invalid){showError.accept("休息时间请输入0–1800秒。");return;}if(rest<0||rest>1800){showError.accept("休息时间请输入0–1800秒。");return;}
            JSONObject fresh = store.get("session", String.valueOf(id));
            boolean wasComplete = complete(fresh);
            JSONObject actual = exercises(fresh).getJSONObject(exerciseIndex).getJSONArray("actual").getJSONObject(setIndex);
            long now = System.currentTimeMillis();
            actual.put("weight", w).put("quantity", q).put("rir", r).put("done", true)
                .put("restSeconds", rest).put("updatedAt", now);
            boolean finished = PlanOps.updateWorkoutCompletion(fresh, wasComplete, now);
            store.saveSession(fresh);
            dialog.dismiss();
            if (finished) stopTimer();
            else if (withRest[0] && rest > 0) startTimer(rest);
            rerender();
            if (finished && !wasComplete) showCompletion(fresh);
            else toast("已保存第" + (setIndex + 1) + "组");
        }catch(Exception ex){showError.accept("保存失败，原记录未清除："+ex.getMessage());}};
        if (!finishing) {
            add(b, action("完成并开始休息", true, () -> {
                withRest[0] = true;
                save.run();
            }));
        }
        gap(b, 12);
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

    void addExercise(JSONObject session){planner.addExercise(session.optInt("id"));}

    void swimDialog(JSONObject session){JSONObject sw=session.optJSONObject("swim");LinearLayout b=dialogBody();add(b,text("时间可包含组间休息，不要求连续游。",12,MUTED,false));gap(b,14);EditText minutes=input(b,"游泳分钟",s(sw,"minutes"),"可留空",true),distance=input(b,"距离 米",s(sw,"distance"),"可留空",true),rpe=input(b,"游泳RPE 0–10",s(sw,"rpe"),"可留空",true),sleep=input(b,"睡眠小时",s(sw,"sleep"),"可留空",true),note=input(b,"备注",s(sw,"note"),"疲劳、酸痛或技术练习",false);ScrollView scroll=new ScrollView(this);scroll.addView(b);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("游泳与恢复").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(button->{try{EditText[] fields={minutes,distance,rpe,sleep};double[] max={600,100000,10,24};for(int i=0;i<fields.length;i++){String val=fields[i].getText().toString().trim();if(!val.isEmpty()){double n=Double.parseDouble(val);if(n<0||n>max[i]){fields[i].setError("请检查数值范围");return;}}}JSONObject fresh=store.get("session",String.valueOf(session.optInt("id")));JSONObject value=new JSONObject().put("planned",s(sw,"planned")).put("minutes",minutes.getText().toString().trim()).put("distance",distance.getText().toString().trim()).put("rpe",rpe.getText().toString().trim()).put("sleep",sleep.getText().toString().trim()).put("note",note.getText().toString().trim());fresh.put("swim",value);store.saveSession(fresh);dialog.dismiss();rerender();}catch(Exception ex){toast("请输入有效数字");}}));dialog.show();}
    interface DateChosen{void accept(LocalDate date);}
    void pickDate(LocalDate initial,DateChosen done){new DatePickerDialog(this,(v,y,m,d)->done.accept(LocalDate.of(y,m+1,d)),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();}
    void body(){
        add(content, titleRow("体测", 28,
            iconButton(R.drawable.ic_add, "记录今天的体测", () -> metricDialog(today()))));
        gap(content, 16);
        List<JSONObject> metrics=store.list("metric");
        LinearLayout summary=card(content);String weight="—",waist="—";double total=0;int count=0;LocalDate cutoff=LocalDate.now().minusDays(6);for(JSONObject m:metrics){if(!s(m,"weight").isEmpty())weight=s(m,"weight");if(!s(m,"waist").isEmpty())waist=s(m,"waist");try{LocalDate d=LocalDate.parse(s(m,"date"));if(!d.isBefore(cutoff)&&!d.isAfter(LocalDate.now())&&!s(m,"weight").isEmpty()){total+=Double.parseDouble(s(m,"weight"));count++;}}catch(Exception ignored){}}
        add(summary,text("最近体重  "+weight+" kg",23,INK,true));gap(summary,10);add(summary,text("最近腰围  "+waist+" cm",16,MUTED,false));gap(summary,12);add(summary,text("最近7天均重  "+(count==0?"—":String.format(Locale.ROOT,"%.1f",total/count))+" kg   ·   "+count+"次测量",13,GREEN,true));
        if(metrics.size()>1){LinearLayout chart=card(content);label(chart,"体重趋势 kg");chart.addView(new Trend(this,metrics),new LinearLayout.LayoutParams(-1,dp(155)));}
        Collections.reverse(metrics);
        for (JSONObject metric : metrics) {
            LinearLayout card = card(content);
            LinearLayout row = horizontal();
            LinearLayout details = vertical();
            String date = s(metric, "date");
            add(details, text(dateLabel(date), 13, MUTED, false));
            gap(details, 8);
            String measuredWeight = s(metric, "weight"), measuredWaist = s(metric, "waist");
            add(details, text((measuredWeight.isEmpty() ? "—" : measuredWeight) + " kg   /   "
                + (measuredWaist.isEmpty() ? "—" : measuredWaist) + " cm", 19, INK, true));
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            add(row, iconButton(R.drawable.ic_edit, "编辑 " + dateLabel(date) + " 体测",
                GREEN, Color.TRANSPARENT, v -> metricDialog(date)));
            add(card, row);
            if (!s(metric, "note").isEmpty()) {
                gap(card, 7);
                add(card, text(s(metric, "note"), 12, MUTED, false));
            }
            card.setOnClickListener(v -> metricDialog(date));
        }
        if (metrics.isEmpty()) add(content, text("暂无体测记录", 14, MUTED, false));
    }
    void metricDialog(String initial){final LocalDate[] date={LocalDate.parse(initial)};JSONObject old=store.get("metric",initial);LinearLayout b=dialogBody();TextView pick=action(dateLabel(initial),false,()->{});add(b,pick);gap(b,16);EditText weight=input(b,"体重 kg",s(old,"weight"),"例如 80.5",true),waist=input(b,"腰围 cm",s(old,"waist"),"可留空",true),note=input(b,"备注",s(old,"note"),"可留空",false);
        pick.setOnClickListener(v->pickDate(date[0],chosen->{date[0]=chosen;pick.setText(dateLabel(chosen.toString()));JSONObject existing=store.get("metric",chosen.toString());weight.setText(s(existing,"weight"));waist.setText(s(existing,"waist"));note.setText(s(existing,"note"));}));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("体重与腰围").setView(b).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(button->{try{String w=weight.getText().toString().trim(),a=waist.getText().toString().trim();if(w.isEmpty()&&a.isEmpty()){toast("至少填写体重或腰围");return;}if(!w.isEmpty()&&(Double.parseDouble(w)<=0||Double.parseDouble(w)>500)){weight.setError("请检查体重");return;}if(!a.isEmpty()&&(Double.parseDouble(a)<=0||Double.parseDouble(a)>400)){waist.setError("请检查腰围");return;}store.saveMetric(new JSONObject().put("date",date[0].toString()).put("weight",w).put("waist",a).put("note",note.getText().toString().trim()));d.dismiss();rerender();toast("体测已保存");}catch(Exception ex){toast("请输入有效数字");}}));d.show();}
    void legacyMenu(){List<JSONObject> legacy=store.list("legacy");String[] names=new String[legacy.size()];for(int i=0;i<legacy.size();i++)names[i]=s(legacy.get(i),"name");new AlertDialog.Builder(this).setTitle("去年原始训练资料").setItems(names,(d,which)->legacySheet(legacy.get(which))).setNegativeButton("关闭",null).show();}
    void legacySheet(JSONObject sheet){LinearLayout b=dialogBody();EditText search=input(b,"搜索动作或内容","","例如 卧推、深蹲",false);LinearLayout result=vertical();ScrollView sc=new ScrollView(this);sc.addView(result);b.addView(sc,new LinearLayout.LayoutParams(-1,dp(350)));final Runnable[] render=new Runnable[1];render[0]=()->{result.removeAllViews();String q=search.getText().toString().trim();JSONArray cells=sheet.optJSONArray("cells");int shown=0;for(int i=0;i<cells.length();i++){JSONObject cell=cells.optJSONObject(i);if(!q.isEmpty()&&!s(cell,"value").contains(q))continue;String adr=s(cell,"cell");int row=Integer.parseInt(adr.replaceAll("[A-Z]",""));int column=0;for(char c:adr.replaceAll("[0-9]","").toCharArray())column=column*26+c-'A'+1;
            LinearLayout item=vertical();item.setPadding(0,dp(9),0,dp(9));add(item,text(adr+"   "+s(cell,"value"),14,INK,true));StringBuilder context=new StringBuilder();if(!q.isEmpty()){for(int j=0;j<cells.length();j++){JSONObject other=cells.optJSONObject(j);String oa=s(other,"cell");int orow=Integer.parseInt(oa.replaceAll("[A-Z]","")),oc=0;for(char c:oa.replaceAll("[0-9]","").toCharArray())oc=oc*26+c-'A'+1;if(orow==row&&Math.abs(oc-column)<=2&&!oa.equals(adr))context.append(oa).append(" ").append(s(other,"value")).append("   ");}if(context.length()>0)add(item,text(context.toString(),11,MUTED,false));}add(result,item);if(++shown>=100){add(result,text("仅显示前100项，请输入关键词缩小范围。",12,MUTED,false));break;}}};search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int st,int before,int count){render[0].run();}public void afterTextChanged(Editable e){}});render[0].run();new AlertDialog.Builder(this).setTitle(s(sheet,"name")).setView(b).setPositiveButton("关闭",null).show();}
    void dataMenu(){new AlertDialog.Builder(this).setTitle("备份与导出").setMessage("完整备份包含训练计划、训练记录和体测。换手机或卸载前可以先导出。")
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
    @Override public void onBackPressed(){if(planEditor!=null){planEditor.back();return;}if(currentId!=-1){currentId=-1;build();}else if(!page.equals("训练")){page="训练";build();}else super.onBackPressed();}
    final class Trend extends View {
        final List<Float> values=new ArrayList<>();final List<String> dates=new ArrayList<>();final List<Long> days=new ArrayList<>();final Paint paint=new Paint(3);
        Trend(Context c,List<JSONObject> metrics){super(c);for(JSONObject m:metrics)try{if(!s(m,"weight").isEmpty()){values.add(Float.parseFloat(s(m,"weight")));dates.add(s(m,"date").substring(5));days.add(LocalDate.parse(s(m,"date")).toEpochDay());}}catch(Exception ignored){}}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);if(values.size()<2){paint.setColor(MUTED);paint.setTextSize(dp(13));canvas.drawText("再记录一次体重，即可查看趋势",0,dp(60),paint);return;}float min=Collections.min(values)-.5f,max=Collections.max(values)+.5f,left=dp(36),top=dp(12),width=getWidth()-left-dp(12),height=getHeight()-dp(44);paint.setTextSize(dp(10));paint.setColor(MUTED);canvas.drawText(String.format(Locale.ROOT,"%.1f",max),0,top+dp(5),paint);canvas.drawText(String.format(Locale.ROOT,"%.1f",min),0,top+height,paint);paint.setColor(LINE);canvas.drawLine(left,top+height,getWidth(),top+height,paint);Path path=new Path();
            float lastX=0,lastY=0;long span=Math.max(1,days.get(days.size()-1)-days.get(0));for(int i=0;i<values.size();i++){float x=left+width*(days.get(i)-days.get(0))/span,y=top+height-(values.get(i)-min)/(max-min)*height;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);lastX=x;lastY=y;}paint.setColor(GREEN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(lastX,lastY,dp(4),paint);paint.setColor(MUTED);canvas.drawText(dates.get(0),left,getHeight()-dp(8),paint);canvas.drawText(dates.get(dates.size()-1),getWidth()-dp(40),getHeight()-dp(8),paint);
        }
    }
}
