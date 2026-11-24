package cn.lianji.training;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
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
    private static final int SETTINGS = -3;
    private static final int REMOVE = 0xffa53333;
    private static final String[] UNITS = {"次", "秒", "米"};
    private static final String[] KEYS = {"name", "sets", "low", "high", "rir", "rest"};
    private static final String[] SETTING_KEYS = {"date", "name", "week", "swim"};

    final MainActivity a;
    final PlanEditorDraft draft;
    final int originScroll;
    private int selected;
    private int planScroll;
    private int restoreScroll;
    private EditText nameInput, weekInput, swimInput;
    private EditText[] actionInputs;
    private Spinner unitInput;
    private CheckBox syncInput;
    private TextView error;
    private Bundle actionState;
    private Bundle settingsState;

    PlanEditorUi(MainActivity activity, PlanEditorDraft draft, Bundle state) {
        a = activity;
        this.draft = draft;
        originScroll = state == null ? (a.scroll == null ? 0 : a.scroll.getScrollY()) : state.getInt("originScroll");
        if (state == null) {
            selected = draft.session().optInt("id") < 0 ? SETTINGS : PLAN;
        } else {
            selected = state.getInt("selected", PLAN);
            planScroll = state.getInt("planScroll");
            restoreScroll = state.getInt("scroll");
            actionState = state.getBundle("action");
            settingsState = state.getBundle("settings");
        }
    }

    Bundle saveViewState() {
        capture();
        Bundle out = new Bundle();
        out.putInt("originScroll", originScroll);
        out.putInt("planScroll", planScroll);
        out.putInt("scroll", a.scroll.getScrollY());
        out.putInt("selected", selected);
        out.putBundle("action", actionState);
        out.putBundle("settings", settingsState);
        return out;
    }

    private void capture() {
        if (nameInput != null) {
            settingsState.putString("name", value(nameInput));
            settingsState.putString("week", value(weekInput));
            settingsState.putString("swim", value(swimInput));
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
        boolean inSettings = selected == SETTINGS;
        boolean inAction = selected >= -1;
        String title = inSettings ? "计划设置" : inAction ? (selected < 0 ? "添加动作" : "编辑动作")
            : isNew ? "新建训练计划" : "编辑训练计划";
        LinearLayout top = a.titleRow(title, 20);
        top.setBackgroundColor(SURFACE);
        top.setPadding(a.dp(8), a.dp(4), a.dp(12), a.dp(4));
        top.addView(a.iconButton(R.drawable.ic_back, "返回", GREEN, Color.TRANSPARENT, v -> back()), 0);
        if (selected == PLAN) a.add(top, a.iconButton(R.drawable.ic_settings, "计划设置", this::openSettings));
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
        if (inSettings) renderSettings();
        else if (inAction) renderAction();
        else renderPlan(x, isNew);

        divider(a.shell);
        LinearLayout footer = a.vertical();
        footer.setBackgroundColor(SURFACE);
        footer.setPadding(a.dp(20), a.dp(9), a.dp(20), a.dp(9));
        error = a.text("", 13, REMOVE, false);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        error.setVisibility(View.GONE);
        a.add(footer, error);
        if (inSettings) a.add(footer, a.action("完成设置", true, this::applySettings));
        else a.add(footer, a.action(inAction ? (selected < 0 ? "加入动作列表" : "应用动作修改") : "保存计划",
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
        LinearLayout summary = a.horizontal();
        LinearLayout details = a.vertical();
        String name = s(x, "name");
        TextView planName = a.text(name.isEmpty() ? "未命名训练" : name, 17, INK, true);
        planName.setMaxLines(2);
        planName.setEllipsize(TextUtils.TruncateAt.END);
        a.add(details, planName);
        a.gap(details, 4);
        int week = x.optInt("week");
        String schedule = a.dateLabel(s(x, "date")) + (week == 0 ? "  ·  未分周" : "  ·  第 " + week + " 周");
        String swim = s(x.optJSONObject("swim"), "planned");
        if (!swim.isEmpty()) schedule += "  ·  计划游泳 " + swim + " 分钟";
        a.add(details, a.text(schedule, 13, MUTED, false));
        if (archived) {
            a.gap(details, 5);
            a.add(details, a.text("已移出计划", 13, MUTED, false));
        }
        summary.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        if (!isNew) {
            a.add(summary, a.iconButton(R.drawable.ic_more, "计划管理", MUTED, Color.TRANSPARENT,
                v -> showPlanManagement(v, archived)));
        }
        a.add(a.card(a.content), summary);

        a.add(a.content, a.titleRow("训练动作", 20,
            a.iconButton(R.drawable.ic_add, "添加动作", () -> openAction(-1))));
        a.gap(a.content, 8);
        JSONArray exercises = x.optJSONArray("exercises");
        int activeCount = PlanOps.activeCount(x);
        ExerciseReorder reorder = activeCount > 1
            ? new ExerciseReorder(a, (from, to) -> mutate(() -> draft.moveExercise(from, to))) : null;
        for (int i = 0; i < exercises.length(); i++) {
            JSONObject e = exercises.optJSONObject(i);
            if (!e.optBoolean("archived")) actionCard(e, i, reorder);
        }
        if (activeCount == 0) {
            LinearLayout empty = a.card(a.content);
            a.add(empty, a.text("暂无训练动作", 14, MUTED, false));
        }
        boolean historical = false;
        for (int i = 0; i < exercises.length(); i++) {
            JSONObject e = exercises.optJSONObject(i);
            if (!e.optBoolean("archived")) continue;
            if (!historical) {
                heading(a.content, "已移出的动作");
                historical = true;
            }
            final int index = i;
            String exerciseName = s(e, "name");
            a.add(a.card(a.content), a.titleRow(exerciseName, 15,
                a.iconButton(R.drawable.ic_restore, "恢复 " + exerciseName + " 到计划",
                    () -> mutate(() -> draft.restoreExercise(index)))));
        }
    }

    private void showPlanManagement(View anchor, boolean archived) {
        PopupMenu menu = new PopupMenu(a, anchor, Gravity.END);
        menu.getMenu().add("复制到其他日期").setOnMenuItemClickListener(item -> {
            copy();
            return true;
        });
        menu.getMenu().add(archived ? "恢复到计划" : "移出计划").setOnMenuItemClickListener(item -> {
            mutate(() -> draft.setArchived(!archived));
            return true;
        });
        menu.show();
    }

    private Bundle currentSettings() {
        JSONObject x = draft.session();
        Bundle values = new Bundle();
        values.putString("date", s(x, "date"));
        values.putString("name", s(x, "name"));
        values.putString("week", s(x, "week"));
        values.putString("swim", s(x.optJSONObject("swim"), "planned"));
        return values;
    }

    private void openSettings() {
        planScroll = a.scroll.getScrollY();
        selected = SETTINGS;
        settingsState = currentSettings();
        a.build();
    }

    private void renderSettings() {
        if (settingsState == null) settingsState = currentSettings();
        LinearLayout details = a.card(a.content);
        nameInput = a.input(details, "训练名称", settingsState.getString("name"), "输入训练名称", false);
        a.label(details, "训练日期");
        TextView dateButton = a.action("", false, () -> {});
        dateButton.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, a.icon(R.drawable.ic_calendar, GREEN), null);
        updateDateButton(dateButton);
        dateButton.setOnClickListener(v -> pickDate(dateButton));
        a.add(details, dateButton);
        a.gap(details, 13);
        LinearLayout inputs = a.horizontal();
        inputs.setGravity(Gravity.TOP);
        weekInput = a.input(column(inputs, false), "计划周次", settingsState.getString("week"), "0 为未分周", true);
        integer(weekInput);
        swimInput = a.input(column(inputs, true), "计划游泳（分钟）", settingsState.getString("swim"), "可留空", true);
        a.add(details, inputs);
        a.add(a.content, a.text("返回后点「保存计划」生效。", 13, MUTED, false));
    }

    private void updateDateButton(TextView button) {
        String date = a.dateLabel(settingsState.getString("date"));
        button.setText(date);
        button.setContentDescription("训练日期：" + date);
    }

    private void pickDate(TextView button) {
        hideKeyboard();
        a.pickDate(LocalDate.parse(settingsState.getString("date")), d -> {
            settingsState.putString("date", d.toString());
            updateDateButton(button);
        });
    }

    private void applySettings() {
        capture();
        try {
            draft.details(settingsState.getString("date"), settingsState.getString("name"),
                settingsState.getString("week"), settingsState.getString("swim"));
            returnToPlan();
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private boolean settingsChanged() {
        if (settingsState == null) return false;
        Bundle original = currentSettings();
        for (String key : SETTING_KEYS) {
            if (!settingsState.getString(key).equals(original.getString(key))) return true;
        }
        return false;
    }

    private void actionCard(JSONObject e, int index, ExerciseReorder reorder) {
        LinearLayout card = a.card(a.content);
        String name = s(e, "name");
        LinearLayout row = a.titleRow(name, 17,
            a.iconButton(R.drawable.ic_edit, "编辑 " + name, GREEN, Color.TRANSPARENT, v -> openAction(index)),
            a.iconButton(R.drawable.ic_trash, "移除 " + name, REMOVE, Color.TRANSPARENT,
                v -> mutate(() -> draft.removeExercise(index))));
        if (reorder != null) reorder.addHandle(row, card, index, name);
        a.add(card, row);
        a.gap(card, 6);
        String targets = s(e, "sets") + " 组  ×  " + s(e, "low") + "–" + s(e, "high") + " " + s(e, "unit")
            + "  ·  休息 " + s(e, "rest") + " 秒";
        if (!s(e, "rir").isEmpty()) targets += "  ·  RIR " + s(e, "rir");
        a.add(card, a.text(targets, 13, MUTED, false));
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
        String[] defaults = {"", "2", "8", "12", "", "90"};
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
        LinearLayout targets = a.horizontal();
        targets.setGravity(android.view.Gravity.TOP);
        actionInputs[4] = a.input(column(targets,false),"目标 RIR",v.getString("rir"),"可留空",true);
        actionInputs[5] = a.input(column(targets,true),"休息（秒）",v.getString("rest"),"0–1800",true);
        a.add(extra,targets);
        for (int i = 1; i <= 5; i++) integer(actionInputs[i]);

        if (draft.session().optInt("id") >= 0 && !draft.session().optBoolean("archived")) {
            syncInput = new CheckBox(a);
            syncInput.setText("同步到后续同名训练");
            syncInput.setTextSize(14);
            syncInput.setTextColor(INK);
            syncInput.setChecked(v.getBoolean("sync"));
            a.add(a.content, syncInput);
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
            JSONObject previous = selected < 0 ? null : draft.session().getJSONArray("exercises").getJSONObject(selected);
            fields.put("load", s(previous, "load")).put("note", s(previous, "note"));
            draft.applyExercise(selected, fields, actionState.getBoolean("sync"));
            returnToPlan();
        } catch (NumberFormatException ex) {
            showError("请填写组数、每组范围和休息秒数。");
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    void back() {
        capture();
        hideKeyboard();
        if (selected == SETTINGS) {
            if (settingsChanged()) confirmDiscard("放弃本次设置修改？", this::leaveSettings);
            else leaveSettings();
        } else if (selected != PLAN) {
            if (actionChanged()) confirmDiscard("放弃此动作的修改？", this::returnToPlan);
            else returnToPlan();
        } else if (draft.dirty()) confirmDiscard("放弃本次计划修改？", () -> a.closePlanEditor(-1, false));
        else a.closePlanEditor(-1, false);
    }

    private void leaveSettings() {
        if (draft.session().optInt("id") < 0 && !draft.dirty()) a.closePlanEditor(-1, false);
        else returnToPlan();
    }

    private boolean actionChanged() {
        if (actionState == null) return false;
        Bundle original = originalAction();
        for (String key : KEYS) if (!actionState.getString(key).equals(original.getString(key))) return true;
        return actionState.getInt("unit") != original.getInt("unit")
            || actionState.getBoolean("sync") != original.getBoolean("sync");
    }

    private void returnToPlan() {
        hideKeyboard();
        selected = PLAN;
        actionState = null;
        settingsState = null;
        restoreScroll = planScroll;
        a.build();
    }

    private void confirmDiscard(String title, Runnable discard) {
        new AlertDialog.Builder(a).setTitle(title).setMessage("未保存的修改会丢失。")
            .setNegativeButton("继续编辑", null).setPositiveButton("放弃修改", (d,w) -> discard.run()).show();
    }

    private void copy() {
        capture();
        hideKeyboard();
        if (draft.dirty()) {
            showError("请先保存修改，再复制训练。");
            return;
        }
        a.planner.copySession(draft.session().optInt("id"));
    }

    private void savePlan() {
        capture();
        hideKeyboard();
        if (s(draft.session(), "name").isEmpty()) {
            openSettings();
            showError("请填写训练名称。");
            return;
        }
        try {
            boolean isNew = draft.session().optInt("id") < 0;
            PlanEditorDraft proposal = new PlanEditorDraft(draft.state());
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
