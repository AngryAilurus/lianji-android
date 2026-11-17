package cn.lianji.training;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Holds plan changes until the editor can save the whole batch in one transaction. */
final class PlanEditorDraft {
    private static final int MAX_EXERCISES = 200;

    private final JSONObject original;
    private final JSONObject draft;
    // Recorded replacements get a new ID; keep their original identity for future matching.
    private final Map<String, String> origins = new HashMap<>();
    private final Set<String> future = new HashSet<>();

    PlanEditorDraft(JSONObject original, String today) throws Exception {
        this.original = original == null
            ? new JSONObject().put("id", -1).put("date", today).put("name", "").put("week", 1)
                .put("swim", new JSONObject()).put("exercises", new JSONArray())
            : Store.cloneJson(original);
        draft = editableCopy(this.original);
        JSONArray actions = exercises(draft);
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.getJSONObject(i);
            String id = s(action, "id");
            if (!action.optBoolean("archived")) origins.put(id, id);
        }
    }

    PlanEditorDraft(String state) throws Exception {
        JSONObject saved = new JSONObject(state);
        original = saved.getJSONObject("original");
        draft = saved.getJSONObject("draft");
        JSONObject sourceIds = saved.getJSONObject("origins");
        for (Iterator<String> keys = sourceIds.keys(); keys.hasNext(); ) {
            String id = keys.next();
            origins.put(id, sourceIds.getString(id));
        }
        JSONArray sync = saved.getJSONArray("future");
        for (int i = 0; i < sync.length(); i++) future.add(sync.getString(i));
    }

    String state() {
        try {
            return new JSONObject().put("original", original).put("draft", draft)
                .put("origins", new JSONObject(origins)).put("future", new JSONArray(future)).toString();
        } catch (JSONException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** This snapshot is for rendering; all changes should go through the editor methods. */
    JSONObject session() { return draft; }

    boolean dirty() {
        return !future.isEmpty() || !sameJson(draft, editableCopy(original));
    }

    void details(String date, String name, String week, String swim) throws Exception {
        String plannedDate = date.trim();
        String plannedName = name.trim();
        String plannedSwim = swim.trim();
        int plannedWeek = validateDetails(plannedDate, plannedName, week.trim(), plannedSwim);
        draft.put("date", plannedDate).put("name", plannedName).put("week", plannedWeek);
        draft.getJSONObject("swim").put("planned", plannedSwim);
    }

    void setArchived(boolean archived) throws JSONException { draft.put("archived", archived); }

    boolean syncsExercise(int index) {
        return future.contains(s(exercises(draft).optJSONObject(index), "id"));
    }

    void applyExercise(int index, JSONObject fields, boolean sync) throws Exception {
        JSONObject values = new JSONObject();
        PlanOps.applyFields(values, fields);
        PlanOps.validateExercise(values);
        JSONArray actions = exercises(draft);
        if (index == -1) {
            requireCapacity(actions.length() + 1);
            JSONObject item = PlanOps.newExercise(values);
            actions.put(item);
            if (sync) future.add(s(item, "id"));
            return;
        }

        JSONObject item = activeExercise(index);
        String oldId = s(item, "id");
        String origin = origins.get(oldId);
        int priorIndex = origin == null || origin.equals(oldId) ? -1 : indexOf(actions, origin);
        JSONObject prior = actions.optJSONObject(priorIndex);
        if (prior != null && prior.optBoolean("archived") && sameExerciseKind(prior, values)) {
            // Reverting an unsaved rename restores the original training history to the active row.
            PlanOps.edit(draft, priorIndex, values);
            prior.put("archived", false);
            JSONArray restored = new JSONArray();
            for (int i = 0; i < actions.length(); i++) {
                if (i == index) restored.put(prior);
                else if (i != priorIndex) restored.put(actions.get(i));
            }
            draft.put("exercises", restored);
            item = prior;
        } else {
            if (!sameExerciseKind(item, values) && PlanOps.hasActual(item)) {
                requireCapacity(actions.length() + 1);
            }
            PlanOps.edit(draft, index, values);
            item = exercises(draft).getJSONObject(index);
        }

        String id = s(item, "id");
        origins.remove(oldId);
        if (origin != null) origins.put(id, origin);
        future.remove(oldId);
        if (sync) future.add(id);
    }

    void moveExercise(int from, int to) throws JSONException {
        PlanOps.reorderExercises(draft, from, to);
    }

    void removeExercise(int index) throws Exception {
        String id = s(activeExercise(index), "id");
        PlanOps.remove(draft, index);
        origins.remove(id);
        future.remove(id);
    }

    void restoreExercise(int index) throws Exception {
        JSONObject item = exercises(draft).getJSONObject(index);
        if (!item.optBoolean("archived")) return;
        item.put("archived", false);
        String id = s(item, "id");
        JSONObject source = exercises(original).optJSONObject(indexOf(exercises(original), id));
        if (source == null || source.optBoolean("archived")) return;

        // A restored original owns its old identity again. Its detached replacement no longer
        // implies changing the old action on future days.
        for (Iterator<Map.Entry<String, String>> entries = origins.entrySet().iterator(); entries.hasNext(); ) {
            Map.Entry<String, String> entry = entries.next();
            if (id.equals(entry.getValue())) {
                future.remove(entry.getKey());
                entries.remove();
            }
        }
        origins.put(id, id);
    }

    List<JSONObject> prepare(List<JSONObject> saved, int nextId) throws Exception {
        JSONObject current = Store.cloneJson(draft);
        validateDetails(current);
        requireCapacity(exercises(current).length());
        int originalId = original.getInt("id");
        boolean creating = originalId < 0;
        JSONObject latest = null;
        for (JSONObject candidate : saved) {
            int candidateId = candidate.optInt("id", -1);
            if (!creating && candidateId == originalId) {
                latest = candidate;
                continue;
            }
            if (creating && candidateId == nextId) throw new Exception("训练编号已被占用，请重试");
            if (!current.optBoolean("archived") && !candidate.optBoolean("archived")
                    && PlanOps.key(current).equals(PlanOps.key(candidate))) {
                throw new Exception("这一天已有同名训练，请使用不同名称");
            }
        }
        if (creating) {
            if (nextId <= 0) throw new Exception("新训练编号无效");
            current.put("id", nextId);
        } else if (latest == null || latest.optLong("updatedAt") != original.optLong("updatedAt")
                || !sameJson(latest, original)) {
            throw new Exception("训练已在其他位置更新，请重新打开计划后再编辑");
        }

        List<JSONObject> changes = new ArrayList<>();
        changes.add(current);
        if (future.isEmpty() || current.optBoolean("archived")) return changes;
        JSONObject anchor = creating ? current : original;
        for (JSONObject candidate : saved) {
            if (candidate.optInt("id") == originalId || candidate.optBoolean("archived")
                    || !s(candidate, "name").equals(s(anchor, "name"))
                    || s(candidate, "date").compareTo(s(anchor, "date")) <= 0) continue;
            JSONObject changed = prepareFuture(candidate);
            if (changed != null && !sameJson(changed, candidate)) changes.add(changed);
        }
        return changes;
    }

    private JSONObject prepareFuture(JSONObject candidate) throws Exception {
        JSONObject changed = null;
        JSONArray actions = exercises(draft);
        for (int i = 0; i < actions.length(); i++) {
            JSONObject item = actions.getJSONObject(i);
            String id = s(item, "id");
            if (item.optBoolean("archived") || !future.contains(id)) continue;
            String origin = origins.get(id);
            JSONObject fields = new JSONObject();
            PlanOps.applyFields(fields, item);
            if (origin == null) {
                if (changed == null) changed = Store.cloneJson(candidate);
                JSONArray target = exercises(changed);
                requireCapacity(target.length() + 1);
                target.put(PlanOps.newExercise(fields));
                continue;
            }
            // Locate the counterpart in the untouched future plan, so a pending rename cannot
            // change which action another pending edit matches on the same day.
            String targetId = counterpartId(origin, exercises(candidate));
            if (targetId == null) continue;
            if (changed == null) changed = Store.cloneJson(candidate);
            JSONArray target = exercises(changed);
            int at = indexOf(target, targetId);
            JSONObject previous = target.getJSONObject(at);
            if (!sameExerciseKind(previous, fields) && PlanOps.hasActual(previous)) {
                requireCapacity(target.length() + 1);
            }
            PlanOps.edit(changed, at, fields);
        }
        return changed;
    }

    private String counterpartId(String origin, JSONArray target) throws JSONException {
        JSONArray source = exercises(original);
        int index = indexOf(source, origin);
        if (index < 0) return null;
        String name = s(source.getJSONObject(index), "name");
        int ordinal = 0;
        for (int i = 0; i < index; i++) {
            JSONObject item = source.getJSONObject(i);
            if (!item.optBoolean("archived") && name.equals(s(item, "name"))) ordinal++;
        }
        for (int i = 0; i < target.length(); i++) {
            JSONObject item = target.getJSONObject(i);
            if (!item.optBoolean("archived") && name.equals(s(item, "name")) && ordinal-- == 0) {
                return s(item, "id");
            }
        }
        return null;
    }

    private static void validateDetails(JSONObject current) throws Exception {
        int week = validateDetails(s(current, "date"), s(current, "name"), s(current, "week"),
            s(current.optJSONObject("swim"), "planned"));
        current.put("week", week);
    }

    private static int validateDetails(String date, String name, String weekValue, String minutes) throws Exception {
        try { LocalDate.parse(date); }
        catch (RuntimeException ex) { throw new Exception("请选择有效的训练日期"); }
        if (name.isEmpty()) throw new Exception("请填写训练名称");
        int week;
        try { week = Integer.parseInt(weekValue); }
        catch (NumberFormatException ex) { throw new Exception("周次请输入0–52"); }
        if (week < 0 || week > 52) throw new Exception("周次请输入0–52");
        if (!minutes.isEmpty()) {
            double value;
            try { value = Double.parseDouble(minutes); }
            catch (NumberFormatException ex) { throw new Exception("计划游泳时间请输入0–600分钟"); }
            if (!Double.isFinite(value) || value < 0 || value > 600) {
                throw new Exception("计划游泳时间请输入0–600分钟");
            }
        }
        return week;
    }

    private JSONObject activeExercise(int index) throws Exception {
        JSONObject item = exercises(draft).getJSONObject(index);
        if (item.optBoolean("archived")) throw new Exception("请先恢复已移出的动作");
        return item;
    }

    private static JSONObject editableCopy(JSONObject source) {
        JSONObject copy = Store.cloneJson(source);
        try {
            JSONObject swim = copy.optJSONObject("swim");
            if (swim == null) swim = new JSONObject();
            swim.put("planned", s(swim, "planned"));
            copy.put("swim", swim).put("archived", copy.optBoolean("archived"))
                .put("week", copy.optInt("week", 0));
            return copy;
        } catch (JSONException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void requireCapacity(int count) throws Exception {
        if (count > MAX_EXERCISES) throw new Exception("每次训练最多200个动作（含历史动作）");
    }

    private static boolean sameExerciseKind(JSONObject first, JSONObject second) {
        return s(first, "name").equals(s(second, "name")) && s(first, "unit").equals(s(second, "unit"));
    }

    private static int indexOf(JSONArray list, String id) {
        for (int i = 0; i < list.length(); i++) {
            if (id.equals(s(list.optJSONObject(i), "id"))) return i;
        }
        return -1;
    }

    private static JSONArray exercises(JSONObject session) { return session.optJSONArray("exercises"); }
    private static String s(JSONObject object, String key) { return Store.str(object, key); }

    private static boolean sameJson(Object first, Object second) {
        if (first instanceof JSONObject && second instanceof JSONObject) {
            JSONObject a = (JSONObject) first, b = (JSONObject) second;
            if (a.length() != b.length()) return false;
            for (Iterator<String> keys = a.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (!b.has(key) || !sameJson(a.opt(key), b.opt(key))) return false;
            }
            return true;
        }
        if (first instanceof JSONArray && second instanceof JSONArray) {
            JSONArray a = (JSONArray) first, b = (JSONArray) second;
            if (a.length() != b.length()) return false;
            for (int i = 0; i < a.length(); i++) if (!sameJson(a.opt(i), b.opt(i))) return false;
            return true;
        }
        return java.util.Objects.equals(first, second);
    }
}
