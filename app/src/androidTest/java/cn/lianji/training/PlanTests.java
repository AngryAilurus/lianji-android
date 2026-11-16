package cn.lianji.training;
import android.app.Instrumentation;
import android.content.Context;
import org.json.*;
import java.io.*;
import java.time.LocalDate;
import java.util.*;

final class PlanTests {
    int checks;void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static File file(Context c,byte[] data)throws Exception{File f=File.createTempFile("xlsx-test-",".xlsx",c.getCacheDir());try(FileOutputStream out=new FileOutputStream(f)){out.write(data);}return f;}
    static PlanXlsx.Table read(Context ctx,byte[] bytes)throws Exception{
        File f=file(ctx,bytes);try{List<PlanXlsx.Table> tables=PlanXlsx.read(f);if(tables.size()!=1)throw new AssertionError("expected one synthetic sheet");return tables.get(0);}finally{f.delete();}
    }

    private static JSONObject exerciseAt(JSONObject session, int index) throws JSONException {
        return session.getJSONArray("exercises").getJSONObject(index);
    }

    private static List<String> activeExerciseNames(JSONObject session) throws JSONException {
        List<String> names = new ArrayList<>();
        JSONArray exercises = session.getJSONArray("exercises");
        for (int i = 0; i < exercises.length(); i++) {
            JSONObject exercise = exercises.getJSONObject(i);
            if (!exercise.optBoolean("archived")) names.add(exercise.getString("name"));
        }
        return names;
    }

    void testPlanEditorDraft() throws Exception {
        JSONObject logged = TestFixtures.exercise("draft-logged", "合成动作甲", 3, 7, 9, "次");
        JSONObject historical = TestFixtures.exercise("draft-historical", "合成历史动作", 1, 8, 10, "次")
                .put("archived", true);
        JSONObject second = TestFixtures.exercise("draft-second", "合成动作乙", 1, 11, 13, "次");
        logged.getJSONArray("actual").put(2, TestFixtures.set("16.5", "7", "0"));
        historical.getJSONArray("actual").put(0, TestFixtures.set("9", "8", "1"));
        long start = 1_970_000_000_000L;
        long end = start + 600_000L;
        JSONObject original = TestFixtures.session(7001, "2032-06-05", "合成草稿训练", logged, historical, second)
                .put(PlanOps.STARTED_AT, start).put(PlanOps.ENDED_AT, end);
        original.getJSONObject("swim").put("minutes", "11").put("distance", "247").put("note", "合成游泳记录");
        String before = original.toString();
        List<JSONObject> saved = Collections.singletonList(original);
        PlanEditorDraft draft = new PlanEditorDraft(original, "2032-06-04");
        check(!draft.dirty(), "an untouched editor is not dirty");
        draft.details("2032-06-06", "合成草稿改名", "5", "20");
        draft.applyExercise(0, Store.cloneJson(logged).put("sets", 1), false);
        draft.moveExercise(0, 1);
        JSONObject moved = draft.session();
        check(activeExerciseNames(moved).equals(Arrays.asList("合成动作乙", "合成动作甲")),
                "moving an active exercise skips archived history in one step");
        JSONArray exercises = moved.getJSONArray("exercises");
        int loggedIndex = -1;
        for (int i = 0; i < exercises.length(); i++) {
            if ("draft-logged".equals(exerciseAt(moved, i).getString("id"))) loggedIndex = i;
        }
        check(loggedIndex >= 0, "moving an exercise preserves its identity");
        draft.removeExercise(loggedIndex);
        check(exerciseAt(draft.session(), loggedIndex).optBoolean("archived"),
                "removing a logged exercise keeps it as draft history");
        draft.restoreExercise(loggedIndex);
        check(!exerciseAt(draft.session(), loggedIndex).optBoolean("archived"),
                "restoring a logged exercise keeps the same action and history");
        check(draft.dirty() && original.toString().equals(before), "draft changes do not mutate the saved session");

        PlanEditorDraft restored = new PlanEditorDraft(draft.state());
        List<JSONObject> batch = restored.prepare(saved, 8001);
        check(restored.dirty() && batch.size() == 1, "restoring a draft retains its unsaved status and makes one save payload");
        JSONObject updated = batch.get(0);
        check(updated.getInt("id") == 7001 && updated.getString("date").equals("2032-06-06")
                        && updated.getString("name").equals("合成草稿改名") && updated.getInt("week") == 5,
                "saving a restored draft retains training details and the existing id");
        JSONObject updatedSwim = updated.getJSONObject("swim");
        check(updatedSwim.getString("planned").equals("20") && updatedSwim.getString("minutes").equals("11")
                        && updatedSwim.getString("distance").equals("247") && updatedSwim.getString("note").equals("合成游泳记录"),
                "changing planned swim minutes preserves the actual swim record");
        check(updated.getLong(PlanOps.STARTED_AT) == start && updated.getLong(PlanOps.ENDED_AT) == end,
                "editing a training plan retains its recorded start and end times");
        JSONObject updatedLogged = exerciseAt(updated, loggedIndex);
        check(updatedLogged.getInt("sets") == 1 && updatedLogged.getJSONArray("actual").length() == 3
                        && updatedLogged.getJSONArray("actual").getJSONObject(2).toString()
                        .equals(logged.getJSONArray("actual").getJSONObject(2).toString()),
                "reducing planned sets keeps the recorded set after a gap");
        check(original.toString().equals(before), "preparing a save payload leaves the original unchanged");
    }

    void testPlanEditorFutureSync() throws Exception {
        String planName = "合成同步训练";
        JSONObject first = TestFixtures.exercise("current-first", "合成同步动作", 2, 7, 9, "次");
        JSONObject second = TestFixtures.exercise("current-second", "合成本次动作", 1, 11, 13, "次");
        JSONObject now = TestFixtures.session(7101, "2032-06-05", planName, first, second);
        JSONObject hiddenExercise = Store.cloneJson(first).put("id", "future-hidden").put("archived", true);
        JSONObject futureFirst = Store.cloneJson(first).put("id", "future-first");
        futureFirst.getJSONArray("actual").put(1, TestFixtures.set("18", "8", "0"));
        JSONObject future = TestFixtures.session(7102, "2032-06-12", planName, hiddenExercise,
                futureFirst, Store.cloneJson(second).put("id", "future-second"));
        JSONObject earlier = TestFixtures.session(7103, "2032-05-29", planName, Store.cloneJson(first));
        JSONObject different = TestFixtures.session(7104, "2032-06-13", "合成其他训练", Store.cloneJson(first));
        JSONObject archived = TestFixtures.session(7105, "2032-06-19", planName, Store.cloneJson(first))
                .put("archived", true);
        List<JSONObject> saved = Arrays.asList(earlier, now, future, different, archived);
        String savedBefore = saved.toString();
        PlanEditorDraft draft = new PlanEditorDraft(now, "2032-06-04");
        draft.applyExercise(0, Store.cloneJson(first).put("rest", 52), true);
        draft.applyExercise(1, Store.cloneJson(second).put("rest", 64), false);
        draft.applyExercise(-1, TestFixtures.exercise("add", "合成新增动作", 2, 5, 6, "次"), true);
        int removedIndex = draft.session().getJSONArray("exercises").length();
        draft.applyExercise(-1, TestFixtures.exercise("remove", "合成取消动作", 1, 3, 4, "次"), true);
        draft.removeExercise(removedIndex);

        List<JSONObject> batch = new PlanEditorDraft(draft.state()).prepare(saved, 8001);
        check(batch.size() == 2 && batch.get(0).getInt("id") == 7101 && batch.get(1).getInt("id") == 7102,
                "only the selected session and later active sessions with the same name are saved");
        JSONObject updated = batch.get(0), synced = batch.get(1);
        check(exerciseAt(updated, 1).getInt("rest") == 64 && exerciseAt(synced, 2).getInt("rest") == 37,
                "edits without future sync affect only the selected session");
        check(exerciseAt(synced, 0).getInt("rest") == 37 && exerciseAt(synced, 1).getInt("rest") == 52,
                "future sync edits the active match and leaves archived history alone");
        check(exerciseAt(synced, 1).getJSONArray("actual").getJSONObject(1).toString()
                        .equals(futureFirst.getJSONArray("actual").getJSONObject(1).toString()),
                "a synced edit preserves sets already recorded in the future session");
        List<String> expectedNames = Arrays.asList("合成同步动作", "合成本次动作", "合成新增动作");
        check(activeExerciseNames(updated).equals(expectedNames) && activeExerciseNames(synced).equals(expectedNames)
                        && !PlanOps.hasActual(exerciseAt(synced, 3)),
                "a synced addition adds blank sets, and canceling another addition never affects a future session");
        check(saved.toString().equals(savedBefore), "preparing future edits never mutates their saved inputs");
    }

    void testPlanEditorValidation() throws Exception {
        JSONObject first = TestFixtures.exercise("validate-first", "合成验证动作", 1, 7, 9, "次");
        JSONObject original = TestFixtures.session(7201, "2032-06-05", "合成验证训练", first).put("updatedAt", 10);
        JSONObject future = TestFixtures.session(7202, "2032-06-12", "合成验证训练", Store.cloneJson(first));
        List<JSONObject> saved = Arrays.asList(original, future);
        String before = saved.toString();
        PlanEditorDraft draft = new PlanEditorDraft(original, "2032-06-04");
        draft.details("2032-06-12", "合成验证训练", "2", "");
        draft.applyExercise(0, Store.cloneJson(first).put("rest", 54), true);
        boolean rejected = false;
        try { draft.prepare(saved, 8001); } catch (Exception expected) { rejected = true; }
        check(rejected && draft.dirty() && saved.toString().equals(before),
                "a duplicate date and name rejects the whole save without discarding or partially saving the draft");

        draft.details("2032-06-31", "合成验证训练", "2", "");
        rejected = false;
        try { new PlanEditorDraft(draft.state()).prepare(saved, 8001); } catch (Exception expected) { rejected = true; }
        check(rejected && saved.toString().equals(before),
                "an invalid calendar date cannot be saved after restoring the draft");

        draft.details("2032-06-05", "合成验证训练", "2", "");
        check(draft.prepare(saved, 8001).size() == 2 && saved.toString().equals(before),
                "correcting a validation error makes the original current and future edits saveable");
        JSONObject changed = Store.cloneJson(original).put("updatedAt", 11);
        changed.getJSONObject("swim").put("minutes", "9");
        List<JSONObject> concurrent = Arrays.asList(changed, future);
        String concurrentBefore = concurrent.toString();
        rejected = false;
        try { new PlanEditorDraft(draft.state()).prepare(concurrent, 8001); } catch (Exception expected) { rejected = true; }
        check(rejected && concurrent.toString().equals(concurrentBefore),
                "a restored stale draft cannot overwrite a newer training record or modify future sessions");

        PlanEditorDraft fresh = new PlanEditorDraft(null, "2032-06-04");
        check(!fresh.dirty() && fresh.session().getInt("id") == -1, "a new plan does not yet have a saved id");
        fresh.details("2032-06-06", "合成新计划", "3", "12");
        fresh.applyExercise(-1, first, false);
        List<JSONObject> added = fresh.prepare(saved, 8001);
        check(added.size() == 1 && added.get(0).getInt("id") == 8001
                        && added.get(0).getString("date").equals("2032-06-06") && saved.toString().equals(before),
                "preparing a new plan allocates its id only in the save payload");
    }

    void testWorkoutTimes() throws Exception {
        long start = 1_970_000_000_000L;
        long end = start + 600_000L;
        JSONObject first = TestFixtures.exercise("first", "合成动作一", 1, 8, 12, "次");
        JSONObject second = TestFixtures.exercise("second", "合成动作二", 1, 8, 12, "次");
        JSONObject archived = TestFixtures.exercise("archived", "已移出的动作", 1, 8, 12, "次").put("archived", true);
        JSONObject session = new JSONObject().put("exercises", new JSONArray().put(first).put(second).put(archived));
        check(!PlanOps.hasRecords(session), "viewing an unstarted plan does not create a record");
        check(PlanOps.startWorkout(session, start), "starting a workout records the start time");
        check(PlanOps.hasRecords(session), "started workouts are visible before the first set");
        check(!PlanOps.startWorkout(session, start + 1000) && session.getLong(PlanOps.STARTED_AT) == start,
            "resuming preserves the first start time");
        JSONObject firstSet = first.getJSONArray("actual").getJSONObject(0);
        JSONObject secondSet = second.getJSONArray("actual").getJSONObject(0);
        secondSet.put("done", true);
        check(!PlanOps.updateWorkoutCompletion(session, false, end - 1000) && !session.has(PlanOps.ENDED_AT),
            "another unfinished action prevents recording an end time");
        firstSet.put("done", true);
        check(PlanOps.updateWorkoutCompletion(session, false, end) && session.getLong(PlanOps.ENDED_AT) == end,
            "saving the final outstanding set records the end time even when an archived set is unfinished");
        check(PlanOps.updateWorkoutCompletion(session, true, end + 1000) && session.getLong(PlanOps.ENDED_AT) == end,
            "editing a completed workout preserves its end time");
        firstSet.put("done", false);
        first.getJSONArray("actual").put(PlanOps.blankSet());
        check(!PlanOps.updateWorkoutCompletion(session, false, end + 2000) && session.getLong(PlanOps.ENDED_AT) == end,
            "new unfinished sets do not create a new end time");
        firstSet.put("done", true);
        first.getJSONArray("actual").getJSONObject(1).put("done", true);
        check(PlanOps.updateWorkoutCompletion(session, false, end + 3000)
                && session.getLong(PlanOps.ENDED_AT) == end + 3000
                && session.getLong(PlanOps.STARTED_AT) == start,
            "finishing additional sets updates only the end time");

        JSONObject copy = PlanOps.copy(session, 9002, "2032-06-19");
        check(!copy.has(PlanOps.STARTED_AT) && !copy.has(PlanOps.ENDED_AT),
            "copying a plan does not inherit workout times");
        JSONObject oldWorkout = new JSONObject().put("exercises", new JSONArray().put(first).put(second));
        check(PlanOps.updateWorkoutCompletion(oldWorkout, false, end) && !oldWorkout.has(PlanOps.ENDED_AT),
            "backfilling a workout without a recorded start does not add a timestamp");
        check(!PlanOps.startWorkout(oldWorkout, start) && !oldWorkout.has(PlanOps.STARTED_AT),
            "already completed legacy workouts cannot acquire a new start time");
    }
    void testCalendarWeekSelection() throws Exception {
        JSONObject previous = TestFixtures.session(1, "2032-12-26", "上周");
        JSONObject monday = TestFixtures.session(2, "2032-12-27", "周一").put("week", 8);
        JSONObject archived = TestFixtures.session(3, "2032-12-29", "已移出").put("archived", true);
        JSONObject sunday = TestFixtures.session(4, "2033-01-02", "周日").put("week", 0);
        JSONObject next = TestFixtures.session(5, "2033-01-03", "下周").put("week", 8);
        List<JSONObject> sessions = Arrays.asList(previous, monday, archived, sunday, next);
        check(PlanOps.sessionsInWeek(sessions, LocalDate.parse("2033-01-01")).equals(Arrays.asList(monday, sunday)),
            "a rest day selects the active Monday-to-Sunday sessions across a year boundary");
        check(PlanOps.sessionsInWeek(sessions, LocalDate.parse("2033-01-03")).equals(Collections.singletonList(next)),
            "the selected calendar week advances on Monday");
        check(PlanOps.sessionsInWeek(sessions, LocalDate.parse("2033-01-10")).isEmpty(),
            "a week without sessions stays empty");
    }
    int run(Instrumentation runner)throws Exception{
        Context ctx=runner.getTargetContext();ctx.deleteDatabase("plan-tests.db");Store store=new Store(ctx,"plan-tests.db");try{
            testWorkoutTimes();
            testCalendarWeekSelection();
            testPlanEditorDraft();
            testPlanEditorFutureSync();
            testPlanEditorValidation();
            store.seed();store.merge(TestFixtures.backup());
            PlanXlsx.Table actual=read(ctx,PlanXlsx.write(TestFixtures.sessions(),false));
            check(actual.name.equals("训练计划"),"synthetic workbook sheet detected");
            check(actual.errors.isEmpty(),"synthetic workbook has no parse errors: "+actual.errors);
            check(actual.sessions.size()==3,"all synthetic sessions parsed");check(actual.rows==4,"all synthetic exercises parsed");check(actual.records==4,"all synthetic actual sets parsed");
            JSONObject importedCarry=actual.sessions.get(1).getJSONArray("exercises").getJSONObject(0);
            check(importedCarry.getJSONArray("actual").length()==3,"extra actual third set imported");
            check(!importedCarry.getJSONArray("actual").getJSONObject(1).optBoolean("done"),"blank gap in imported sets retained");
            check(Store.str(importedCarry.getJSONArray("actual").getJSONObject(0),"quantity").equals("3 趟"),"Excel text quantity retained");
            check(Store.str(actual.sessions.get(0).getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(0),"rir").equals("0"),"Excel RIR zero retained");
            PlanOps.ImportBatch first=PlanOps.prepare(store,actual.sessions,true);
            check(first.added==0&&first.updated==3,"same workbook merges existing sessions");check(first.conflicts==0,"same actual values do not conflict");
            store.savePlans(first.sessions,true);check(store.get("checkpoint","beforeExcelImport")!=null,"pre-import snapshot saved");
            check(store.list("session").size()==3,"no duplicates after import");
            PlanOps.ImportBatch repeated=PlanOps.prepare(store,actual.sessions,true);store.savePlans(repeated.sessions,true);
            check(store.list("session").size()==3,"repeated import remains idempotent");
            JSONObject local=store.get("session","101");JSONObject set=local.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(0);
            set.put("weight","14.25");store.saveSession(local);PlanOps.ImportBatch conflict=PlanOps.prepare(store,actual.sessions,true);
            check(conflict.conflicts==1,"different existing weight reported as conflict");store.savePlans(conflict.sessions,true);
            check(Store.str(store.get("session","101").getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(0),"weight").equals("14.25"),"import never overwrites existing value");
            JSONObject original=store.get("session","101"),exercise=original.getJSONArray("exercises").getJSONObject(0),fields=Store.cloneJson(exercise);
            String before=exercise.getJSONArray("actual").getJSONObject(0).toString();fields.put("sets",1).put("low",4).put("high",6).put("rir","1").put("rest",47);
            PlanOps.edit(original,0,fields);check(original.getJSONArray("exercises").getJSONObject(0).getInt("sets")==1,"target set count edited");
            check(original.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(0).toString().equals(before),"shrinking plan preserves recorded set");
            fields.put("sets",5);PlanOps.edit(original,0,fields);
            check(original.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").length()==5,"increasing plan creates blank sets");
            check(!original.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(4).optBoolean("done"),"new planned sets are not marked recorded");
            JSONObject replacement=Store.cloneJson(fields).put("name","合成替换器械");PlanOps.edit(original,0,replacement);
            check(Store.str(original.getJSONArray("exercises").getJSONObject(0),"name").equals("合成替换器械"),"replacement exercise inserted");
            check(!PlanOps.hasActual(original.getJSONArray("exercises").getJSONObject(0)),"replacement does not inherit old actuals");
            check(original.getJSONArray("exercises").getJSONObject(1).optBoolean("archived"),"old recorded exercise archived");
            check(Store.str(original.getJSONArray("exercises").getJSONObject(1).getJSONArray("actual").getJSONObject(0),"weight").equals("14.25"),"old weight remains in archived exercise");
            PlanOps.remove(original,1);check(original.getJSONArray("exercises").getJSONObject(1).optBoolean("archived"),"removing logged exercise retains history");
            int size=original.getJSONArray("exercises").length();PlanOps.remove(original,0);check(original.getJSONArray("exercises").length()==size-1,"unrecorded replacement can be removed");
            String firstName=Store.str(original.getJSONArray("exercises").getJSONObject(0),"name");PlanOps.move(original,0,1);
            check(Store.str(original.getJSONArray("exercises").getJSONObject(1),"name").equals(firstName),"reordering keeps exercise identity");
            JSONObject copy=PlanOps.copy(original,9001,"2032-06-18");
            check(copy.getInt("id")==9001&&copy.getString("date").equals("2032-06-18"),"copy creates new date and id");
            check(copy.getJSONArray("exercises").length()==1,"copy excludes archived exercises");
            for(int i=0;i<copy.getJSONArray("exercises").length();i++)check(!PlanOps.hasActual(copy.getJSONArray("exercises").getJSONObject(i)),"copy does not duplicate actual data");
            JSONObject newDay=Store.cloneJson(actual.sessions.get(0)).put("date","2032-05-01").put("name","合成新增训练");
            PlanOps.ImportBatch addition=PlanOps.prepare(store,Collections.singletonList(newDay),false);check(addition.added==1,"new date imports as new training");
            for(int i=0;i<addition.sessions.get(0).getJSONArray("exercises").length();i++)check(!PlanOps.hasActual(addition.sessions.get(0).getJSONArray("exercises").getJSONObject(i)),"plan-only import does not copy actual data");
            store.savePlans(addition.sessions,false);check(store.list("session").size()==4,"new training appended");
            PlanXlsx.Table roundtrip=read(ctx,PlanXlsx.write(store.list("session"),false));
            check(roundtrip.errors.isEmpty(),"exported workbook can be imported");check(roundtrip.sessions.size()==4,"Excel roundtrip preserves session count");
            PlanOps.ImportBatch rt=PlanOps.prepare(store,roundtrip.sessions,true);check(rt.added==0,"roundtrip does not duplicate sessions");check(rt.conflicts==0,"roundtrip preserves updated local records");
            PlanXlsx.Table template=read(ctx,PlanXlsx.write(Collections.emptyList(),true));
            check(template.errors.isEmpty()&&template.rows==2&&template.sessions.size()==1,"template imports as one session and two exercises");
            String prior=store.get("session","101").toString();boolean rejected=false;JSONObject bad=Store.cloneJson(actual.sessions.get(0));
            bad.getJSONArray("exercises").getJSONObject(0).put("sets",0);try{PlanOps.prepare(store,Collections.singletonList(bad),true);}catch(Exception expected){rejected=true;}
            check(rejected,"invalid set count rejected");check(store.get("session","101").toString().equals(prior),"invalid preview never writes local data");
            JSONObject badDate=Store.cloneJson(store.get("session","101")).put("date","not-a-date");rejected=false;
            JSONObject good=Store.cloneJson(store.get("session","102")).put("name","SHOULD_ROLL_BACK");
            try{store.savePlans(Arrays.asList(good,badDate),false);}catch(Exception expected){rejected=true;}
            check(rejected,"invalid batch date rejected");check(!store.get("session","102").getString("name").equals("SHOULD_ROLL_BACK"),"plan save is atomic on error");
            JSONObject withBlank=TestFixtures.exercise("gap-only","合成间隔动作",2,13,17,"米");
            withBlank.getJSONArray("actual").put(TestFixtures.set("8","5 趟","0"));PlanOps.resize(withBlank,1);
            check(withBlank.getJSONArray("actual").length()==3,"occupied extra set after a gap never lost");
            check(!withBlank.getJSONArray("actual").getJSONObject(0).optBoolean("done"),"resize preserves leading blank gap");
            JSONObject finish=new JSONObject().put("exercises",new JSONArray().put(new JSONObject().put("actual",new JSONArray().put(PlanOps.blankSet()).put(PlanOps.blankSet()))).put(new JSONObject().put("actual",new JSONArray().put(PlanOps.blankSet()))));
            check(!PlanOps.completesSession(finish,1,0),"last displayed set cannot finish when earlier sets remain");
            finish.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(0).put("done",true);
            finish.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").getJSONObject(1).put("done",true);
            check(PlanOps.completesSession(finish,1,0),"last outstanding set completes session");
            check(!PlanOps.completesSession(finish,-1,-1),"completion preview does not mutate saved data");
            finish.getJSONArray("exercises").getJSONObject(1).getJSONArray("actual").getJSONObject(0).put("done",true);
            check(PlanOps.completesSession(finish,-1,-1),"all active sets completed");
            finish.getJSONArray("exercises").put(new JSONObject().put("archived",true).put("actual",new JSONArray().put(PlanOps.blankSet())));
            check(PlanOps.completesSession(finish,-1,-1),"archived unfinished sets do not block completion");
            check(!PlanOps.completesSession(finish,2,0),"editing archived history cannot complete active training");
            finish.getJSONArray("exercises").getJSONObject(0).getJSONArray("actual").put(PlanOps.blankSet());
            check(!PlanOps.completesSession(finish,1,0),"added extra set prevents premature completion");
            check(PlanOps.completesSession(finish,0,2),"out-of-order final outstanding set completes training");
            check(!PlanOps.completesSession(new JSONObject().put("exercises",new JSONArray()),-1,-1),"empty plan is not completed");
            JSONObject swimPlan = new JSONObject().put("exercises", new JSONArray());
            check(!PlanOps.hasRecords(swimPlan), "older sessions without a swim object still work");
            swimPlan.put("swim", new JSONObject().put("planned", "30"));
            check(!PlanOps.hasRecords(swimPlan), "planned swim minutes do not count as a record");
            for (String field : new String[]{"minutes", "distance", "rpe", "sleep", "note"}) {
                swimPlan.put("swim", new JSONObject().put(field, field.equals("note") ? "合成备注" : "0"));
                check(PlanOps.hasRecords(swimPlan), "standalone swim or recovery field appears in history: " + field);
            }
            check(PlanOps.hasRecords(store.get("session", "101")), "strength records still appear in history");
            return checks;
        }finally{store.close();ctx.deleteDatabase("plan-tests.db");}
    }
}
