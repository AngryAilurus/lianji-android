package cn.lianji.training;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static cn.lianji.training.MainActivity.*;

final class PlanEditorUi {
    private static final int PLAN = -2;
    private static final int REMOVE = 0xffa53333;
    private static final String[] UNITS = {"次", "秒", "米"};
    private static final String[] KEYS = {"name", "sets", "low", "high", "rir", "rest", "load", "note"};

    final MainActivity a;
    final PlanEditorDraft draft;
    final int originScroll;
    private int selected = PLAN;
    private int planScroll;
    private int restoreScroll;
    private String date;
    private String name;
    private String week;
    private String swim;
    private EditText nameInput, weekInput, swimInput;
    private EditText[] actionInputs;
    private Spinner unitInput;
    private CheckBox syncInput;
    private TextView error;
    private Bundle actionState;

    PlanEditorUi(MainActivity activity, PlanEditorDraft draft, Bundle state) {
        a = activity;
        this.draft = draft;
        JSONObject x = draft.session();
        originScroll = state == null ? (a.scroll == null ? 0 : a.scroll.getScrollY()) : state.getInt("originScroll");
        date = state == null ? s(x, "date") : state.getString("date");
        name = state == null ? s(x, "name") : state.getString("name");
        week = state == null ? s(x, "week") : state.getString("week");
        swim = state == null ? s(x.optJSONObject("swim"), "planned") : state.getString("swim");
        if (state != null) {
            selected = state.getInt("selected", PLAN);
            planScroll = state.getInt("planScroll");
            restoreScroll = state.getInt("scroll");
            actionState = state.getBundle("action");
        }
    }

    Bundle saveViewState() {
        capture();
        Bundle out = new Bundle();
        out.putInt("originScroll", originScroll);
        out.putInt("planScroll", planScroll);
        out.putInt("scroll", a.scroll.getScrollY());
        out.putInt("selected", selected);
        out.putString("date", date);
        out.putString("name", name);
        out.putString("week", week);
        out.putString("swim", swim);
        out.putBundle("action", actionState);
        return out;
    }

    private void capture() {
        if (nameInput != null) {
            name = value(nameInput);
            week = value(weekInput);
            swim = value(swimInput);
        }
        if (actionInputs != null) {
            actionState = new Bundle();
            for (int i = 0; i < KEYS.length; i++) actionState.putString(KEYS[i], value(actionInputs[i]));
            actionState.putInt("unit", unitInput.getSelectedItemPosition());
            actionState.putBoolean("sync", syncInput != null && syncInput.isChecked());
        }
    }

    void render() {
        nameInput = weekInput = swimInput = null;
        actionInputs = null;
        syncInput = null;
        unitInput = null;
        a.timerView = null;
        JSONObject x = draft.session();
        boolean isNew = x.optInt("id") < 0;
        boolean inAction = selected != PLAN;
        LinearLayout top = a.horizontal();
        top.setBackgroundColor(SURFACE);
        top.setPadding(a.dp(12), a.dp(8), a.dp(16), a.dp(8));
        TextView back = a.action("‹ 返回", false, this::back);
        back.setContentDescription("返回");
        a.add(top, back);
        TextView title = a.text(inAction ? (selected < 0 ? "添加动作" : "编辑动作")
            : isNew ? "新建训练计划" : "编辑训练计划", 20, INK, true);
        title.setPadding(a.dp(14), 0, 0, 0);
        title.setAccessibilityHeading(true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        a.add(a.shell, top);
        divider(a.shell);

        a.scroll = new ScrollView(a);
        a.scroll.setFillViewport(true);
        a.scroll.setClipToPadding(false);
        a.content = a.vertical();
        a.content.setFocusableInTouchMode(true);
        a.content.setPadding(a.dp(20), a.dp(16), a.dp(20), a.dp(16));
        a.scroll.addView(a.content);
        a.shell.addView(a.scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (inAction) renderAction();
        else renderPlan(x, isNew);

        divider(a.shell);
        LinearLayout footer = a.vertical();
        footer.setBackgroundColor(SURFACE);
        footer.setPadding(a.dp(20), a.dp(9), a.dp(20), a.dp(9));
        error = a.text("", 13, REMOVE, false);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setVisibility(View.GONE);
        a.add(footer, error);
        a.add(footer, a.action(inAction ? (selected < 0 ? "加入动作列表" : "应用动作修改") : "保存计划",
            true, inAction ? this::applyAction : this::savePlan));
        a.add(a.shell, footer);
        a.content.requestFocus();
        ScrollView frame = a.scroll;
        int y = restoreScroll;
        restoreScroll = 0;
        frame.post(() -> frame.scrollTo(0, y));
    }

    private void renderPlan(JSONObject x, boolean isNew) {
        boolean archived = x.optBoolean("archived");
        if (archived) {
            LinearLayout notice = a.card(a.content);
            a.add(notice, a.text("此训练已移出计划，历史记录仍保留。", 14, MUTED, false));
        }

        LinearLayout details = a.card(a.content);
        heading(details, "训练安排");
        nameInput = a.input(details, "训练名称", name, "输入训练名称", false);
        a.label(details, "训练日期");
        a.add(details, a.action(dateLabel(), false, this::pickDate));
        a.gap(details, 13);
        LinearLayout inputs = a.horizontal();
        inputs.setGravity(android.view.Gravity.TOP);
        LinearLayout weekField = column(inputs, false), swimField = column(inputs, true);
        weekInput = a.input(weekField, "计划周次", week, "0 为未分周", true);
        integer(weekInput);
        swimInput = a.input(swimField, "计划游泳（分钟）", swim, "可留空", true);
        a.add(details, inputs);

        LinearLayout header = a.horizontal();
        TextView listTitle = a.text("训练动作", 20, INK, true);
        listTitle.setAccessibilityHeading(true);
        LinearLayout listLabel = a.vertical();
        a.add(listLabel, listTitle);
        int activeCount = PlanOps.activeCount(x);
        a.add(listLabel, a.text(activeCount > 1 ? "拖动 ☰ 调整顺序" : activeCount + " 个动作", 13, MUTED, false));
        header.addView(listLabel, new LinearLayout.LayoutParams(0, -2, 1));
        a.add(header, a.action("＋ 添加动作", false, () -> openAction(-1)));
        a.add(a.content, header);
        a.gap(a.content, 10);
        JSONArray exercises = x.optJSONArray("exercises");
        ExerciseReorder reorder = activeCount > 1
            ? new ExerciseReorder(a, (from, to) -> mutate(() -> draft.moveExercise(from, to))) : null;
        int count = 0;
        for (int i = 0; i < exercises.length(); i++) {
            JSONObject e = exercises.optJSONObject(i);
            if (!e.optBoolean("archived")) actionCard(e, i, ++count, reorder);
        }
        if (count == 0) {
            LinearLayout empty = a.card(a.content);
            a.add(empty, a.text("还没有训练动作。", 14, MUTED, false));
        }
        a.gap(a.content, 12);
        boolean historical = false;
        for (int i = 0; i < exercises.length(); i++) {
            JSONObject e = exercises.optJSONObject(i);
            if (!e.optBoolean("archived")) continue;
            if (!historical) {
                heading(a.content, "已移出的动作");
                historical = true;
            }
            final int index = i;
            LinearLayout row = a.card(a.content);
            a.add(row, a.text(s(e, "name"), 15, INK, true));
            a.gap(row, 6);
            a.add(row, a.text("已保留填写过的组记录", 12, MUTED, false));
            a.gap(row, 8);
            a.add(row, a.action("恢复到计划", false, () -> mutate(() -> draft.restoreExercise(index))));
        }
        if (!isNew) {
            LinearLayout options = a.card(a.content);
            heading(options, "计划管理");
            a.add(options, a.action("复制训练到其他日期", false, this::copy));
            a.gap(options, 8);
            a.add(options, button(archived ? "恢复到训练计划" : "从计划移除",
                archived ? GREEN : REMOVE, () -> mutate(() -> draft.setArchived(!archived))));
            if (archived) {
                a.gap(options, 6);
                a.add(options, a.text("恢复显示后，点击「保存计划」生效。", 12, MUTED, false));
            } else {
                a.gap(options, 6);
                a.add(options, a.text("移出计划后，已填写的训练记录仍可查看。", 12, MUTED, false));
            }
        }
    }

    private String dateLabel() {
        return a.dateLabel(date) + "  ▾";
    }

    private void pickDate() {
        hideKeyboard();
        a.pickDate(LocalDate.parse(date), d -> {
            date = d.toString();
            refreshPlan();
        });
    }

    private void actionCard(JSONObject e, int index, int number, ExerciseReorder reorder) {
        LinearLayout card = a.card(a.content);
        LinearLayout row = a.horizontal();
        if (reorder != null) reorder.addHandle(row, card, index, s(e, "name"));
        TextView name = a.text(String.format(java.util.Locale.ROOT, "%02d  %s", number, s(e, "name")), 17, INK, true);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView edit = a.action("编辑", false, () -> openAction(index));
        edit.setContentDescription("编辑 " + s(e, "name"));
        a.add(row, edit);
        a.add(card, row);
        a.gap(card, 6);
        String targets = s(e, "sets") + " 组  ×  " + s(e, "low") + "–" + s(e, "high") + " " + s(e, "unit")
            + "  ·  休息 " + s(e, "rest") + " 秒";
        if (!s(e, "rir").isEmpty()) targets += "  ·  RIR " + s(e, "rir");
        a.add(card, a.text(targets, 13, MUTED, false));
        if (!s(e, "load").isEmpty()) {
            a.gap(card, 5);
            a.add(card, a.text("建议重量：" + s(e, "load"), 12, MUTED, false));
        }
        a.gap(card, 8);
        LinearLayout controls = a.horizontal();
        TextView remove = button("移除", REMOVE, () -> mutate(() -> draft.removeExercise(index)));
        remove.setContentDescription("移除 " + s(e, "name"));
        a.add(controls, remove);
        a.add(card, controls);
    }

    void openAction(int index) {
        capture();
        hideKeyboard();
        planScroll = a.scroll.getScrollY();
        selected = index;
        actionState = null;
        a.build();
    }

    private Bundle originalAction() {
        JSONObject e = selected < 0 ? null : draft.session().optJSONArray("exercises").optJSONObject(selected);
        String[] defaults = {"", "2", "8", "12", "", "90", "", ""};
        Bundle v = new Bundle();
        for (int i = 0; i < KEYS.length; i++) v.putString(KEYS[i], e == null ? defaults[i] : s(e, KEYS[i]));
        v.putInt("unit", e == null ? 0 : Math.max(0, Arrays.asList(UNITS).indexOf(s(e, "unit"))));
        v.putBoolean("sync", draft.syncsExercise(selected));
        return v;
    }

    private void renderAction() {
        Bundle v = actionState == null ? originalAction() : actionState;
        actionInputs = new EditText[KEYS.length];
        LinearLayout basic = a.card(a.content);
        heading(basic, "动作与目标");
        actionInputs[0] = a.input(basic, "动作名称", v.getString("name"), "输入你要练的动作", false);
        LinearLayout row = a.horizontal();
        row.setGravity(android.view.Gravity.TOP);
        LinearLayout sets = column(row, false), units = column(row, true);
        actionInputs[1] = a.input(sets, "计划组数", v.getString("sets"), "1–30", true);
        a.label(units, "数量单位");
        unitInput = new Spinner(a);
        unitInput.setContentDescription("数量单位");
        unitInput.setPadding(a.dp(9),0,a.dp(8),0);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, UNITS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitInput.setAdapter(adapter);
        unitInput.setSelection(v.getInt("unit"));
        units.addView(unitInput, new LinearLayout.LayoutParams(-1,a.dp(48)));
        a.add(basic, row);
        row = a.horizontal();
        row.setGravity(android.view.Gravity.TOP);
        actionInputs[2] = a.input(column(row, false), "每组下限", v.getString("low"), "例如 8", true);
        actionInputs[3] = a.input(column(row, true), "每组上限", v.getString("high"), "例如 12", true);
        a.add(basic, row);

        LinearLayout extra = a.card(a.content);
        heading(extra, "强度、休息与备注");
        LinearLayout targets = a.horizontal();
        targets.setGravity(android.view.Gravity.TOP);
        actionInputs[4] = a.input(column(targets,false),"目标 RIR",v.getString("rir"),"可留空",true);
        actionInputs[5] = a.input(column(targets,true),"休息（秒）",v.getString("rest"),"0–1800",true);
        a.add(extra,targets);
        actionInputs[6] = a.input(extra, "建议重量", v.getString("load"), "可留空", false);
        actionInputs[7] = a.input(extra, "动作备注", v.getString("note"), "可留空", false);
        for (int i = 1; i <= 5; i++) integer(actionInputs[i]);

        if (draft.session().optInt("id") >= 0 && !draft.session().optBoolean("archived")) {
            syncInput = new CheckBox(a);
            syncInput.setText("同时应用到此后同名训练日");
            syncInput.setTextSize(14);
            syncInput.setTextColor(INK);
            syncInput.setChecked(v.getBoolean("sync"));
            a.add(a.content, syncInput);
            a.add(a.content,a.text("只同步这个动作，实际训练记录会保留。",12,MUTED,false));
        }
    }

    private void applyAction() {
        capture();
        try {
            JSONObject fields = new JSONObject();
            for (String key : KEYS) {
                String field = actionState.getString(key);
                if (Arrays.asList("sets","low","high","rest").contains(key)) fields.put(key,Integer.parseInt(field));
                else fields.put(key,field);
            }
            fields.put("unit", UNITS[actionState.getInt("unit")]);
            draft.applyExercise(selected, fields, actionState.getBoolean("sync"));
            closeAction();
        } catch (NumberFormatException ex) {
            showError("请填写组数、每组范围和休息秒数。");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    void back() {
        capture();
        hideKeyboard();
        if (selected != PLAN) {
            if (actionChanged()) confirmDiscard("放弃此动作的修改？", this::closeAction);
            else closeAction();
        } else if (changed()) confirmDiscard("放弃本次计划修改？", () -> a.closePlanEditor(-1, false));
        else a.closePlanEditor(-1, false);
    }

    private boolean actionChanged() {
        if (actionState == null) return false;
        Bundle original = originalAction();
        for (String key : KEYS) if (!actionState.getString(key).equals(original.getString(key))) return true;
        return actionState.getInt("unit") != original.getInt("unit")
            || actionState.getBoolean("sync") != original.getBoolean("sync");
    }

    private void closeAction() {
        hideKeyboard();
        selected = PLAN;
        actionState = null;
        restoreScroll = planScroll;
        a.build();
    }

    private boolean changed() {
        JSONObject x = draft.session();
        return draft.dirty() || !date.equals(s(x,"date")) || !name.equals(s(x,"name"))
            || !week.equals(s(x,"week")) || !swim.equals(s(x.optJSONObject("swim"),"planned"));
    }

    private void confirmDiscard(String title, Runnable discard) {
        new AlertDialog.Builder(a).setTitle(title).setMessage("未保存的修改会丢失。")
            .setNegativeButton("继续编辑", null).setPositiveButton("放弃修改", (d,w) -> discard.run()).show();
    }

    private void copy() {
        capture();
        hideKeyboard();
        if (changed()) {
            showError("请先保存修改，再复制训练。");
            return;
        }
        a.planner.copySession(draft.session().optInt("id"));
    }

    private void savePlan() {
        capture();
        hideKeyboard();
        try {
            boolean isNew = draft.session().optInt("id") < 0;
            PlanEditorDraft proposal = new PlanEditorDraft(draft.state());
            proposal.details(date, name, week, swim);
            int nextId = a.planner.nextId();
            List<JSONObject> targets = proposal.prepare(a.store.list("session"),nextId);
            Runnable save = () -> {
                try {
                    List<JSONObject> latest = proposal.prepare(a.store.list("session"),a.planner.nextId());
                    if (!sameTargets(latest,targets)) {
                        showError("后续计划已变化，请重新确认保存。");
                        return;
                    }
                    a.store.savePlans(latest,false);
                    JSONObject result = latest.get(0);
                    a.closePlanEditor(result.optInt("id"), isNew || result.optBoolean("archived"));
                    a.toast(latest.size() > 1 ? "计划已保存，并更新 " + (latest.size()-1) + " 次后续训练" : "计划已保存");
                } catch (Exception ex) {
                    showError(ex.getMessage());
                }
            };
            if (targets.size() > 1) new AlertDialog.Builder(a).setTitle("同时更新后续计划？")
                .setMessage("会修改 " + (targets.size()-1) + " 次后续同名训练中的对应动作，已填写的记录会保留。")
                .setNegativeButton("取消",null).setPositiveButton("确认保存",(d,w)->save.run()).show();
            else save.run();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private boolean sameTargets(List<JSONObject> latest,List<JSONObject> confirmed) {
        if (latest.size() != confirmed.size()) return false;
        for (int i = 0; i < latest.size(); i++) {
            if (latest.get(i).optInt("id") != confirmed.get(i).optInt("id")) return false;
        }
        return true;
    }

    private interface Mutation {void run() throws Exception;}
    private void mutate(Mutation change) {
        capture();
        hideKeyboard();
        try { change.run(); refreshPlan(); }
        catch (Exception ex) { showError(ex.getMessage()); }
    }

    private void refreshPlan() {
        capture();
        restoreScroll = a.scroll.getScrollY();
        a.build();
    }

    private void heading(LinearLayout host,String title) {
        TextView heading = a.text(title,18,INK,true);
        heading.setAccessibilityHeading(true);
        a.add(host,heading);
        a.gap(host,12);
    }

    private TextView button(String title,int color,Runnable click) {
        TextView b = a.action(title,false,click);
        b.setTextColor(color);
        if (color == REMOVE) b.setBackground(a.bg(0xffffefef,14));
        return b;
    }

    private LinearLayout column(LinearLayout row,boolean right) {
        LinearLayout col = a.vertical();
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,-2,1);
        if (right) p.leftMargin = a.dp(12);
        row.addView(col,p);
        return col;
    }

    private void divider(LinearLayout host) {
        View line = new View(a);
        line.setBackgroundColor(LINE);
        host.addView(line, new LinearLayout.LayoutParams(-1,a.dp(1)));
    }

    private static void integer(EditText input) {input.setInputType(InputType.TYPE_CLASS_NUMBER);}
    private static String value(EditText input) {return input.getText().toString().trim();}
    private static String s(JSONObject x,String key) {return Store.str(x,key);}

    private void hideKeyboard() {
        View focused = a.getCurrentFocus();
        if (focused != null) ((InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(focused.getWindowToken(),0);
    }

    private void showError(String message) {
        error.setText(message == null ? "无法保存，请检查填写内容。" : message);
        error.setVisibility(View.VISIBLE);
    }
}
