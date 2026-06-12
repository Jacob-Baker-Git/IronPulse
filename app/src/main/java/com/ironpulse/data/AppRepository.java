package com.ironpulse.data;
import android.content.Context;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.ironpulse.model.*;
import java.io.*;
import java.lang.reflect.Type;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class AppRepository {
    /** Every JSON file the app persists — used by save, clear, and backup. */
    public static final String[] DATA_FILES = {
            "exercises.json","body.json","records.json","cardio.json","setlogs.json",
            "rest_days.json","custom_splits.json","completed.json","foods.json",
            "foodlog.json","prefs.json"};

    private static AppRepository instance;
    private final Context context;
    private final Gson gson;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    public final List<ExerciseData> exercises = new ArrayList<>();
    public final Map<LocalDate, List<ExerciseData>> completed = new HashMap<>();
    public final List<BodyWeightEntry> bodyEntries = new ArrayList<>();
    public final List<RecordData> records = new ArrayList<>();
    public final List<CardioEntry> cardio = new ArrayList<>();
    public final List<SetLog> setLogs = new ArrayList<>();
    public final Set<java.time.DayOfWeek> restDays = new HashSet<>();
    /** Custom split templates. Each entry: [0]=name, [1..n]="exercise|weight|setsxreps|rest" */
    public final List<List<String>> customSplits = new ArrayList<>();
    public final String[] macroGoals  = {"","","",""};
    public final String[] macroActual = {"","","",""};
    /** Quick-add food log, keyed by day. Each entry is a macro snapshot. */
    public final Map<LocalDate, List<Food>> foodLog = new HashMap<>();
    /** User-saved foods/meals shown as quick-add chips. */
    public final List<Food> savedFoods = new ArrayList<>();
    public double heightCm = 0;
    /** Body-weight goal tracking. startWeightKg is the anchor entered in the macro calculator. */
    public double startWeightKg = 0;
    public double goalWeightKg = 0;
    public boolean darkMode = true;
    /** "Male" / "Female" — asked once on first launch, changeable in Settings. */
    public String gender = "";
    /** Show weights in pounds instead of kilograms (kg is always stored). */
    public boolean useLbs = false;
    /** Daily workout reminder (fires only on days with unfinished exercises). */
    public boolean reminderEnabled = false;
    public int reminderHour = 18;
    public int reminderMinute = 0;

    private AppRepository(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .registerTypeAdapter(DayOfWeek.class, new DayOfWeekAdapter())
                .setPrettyPrinting().create();
        load();
    }

    public static AppRepository get(Context ctx) {
        if (instance == null) instance = new AppRepository(ctx);
        return instance;
    }

    /** Drops the singleton so the next get() re-reads from disk (used after import). */
    public static void invalidate() { instance = null; }

    public List<ExerciseData> getExercisesForDate(LocalDate date) {
        List<ExerciseData> result = new ArrayList<>();
        for (ExerciseData ex : exercises) {
            LocalDate added = ex.getAddedDate();
            // Only show from addedDate onwards (never before the exercise was created)
            if (added == null || added.isAfter(date)) continue;
            long diff = java.time.temporal.ChronoUnit.DAYS.between(added, date);
            if (diff % 7 == 0) result.add(ex);
        }
        return result;
    }

    public boolean isRestDay(LocalDate date) {
        // Rest days are recurring by DayOfWeek but only meaningful for today/future
        if (date.isBefore(LocalDate.now())) return false;
        return restDays.contains(date.getDayOfWeek());
    }

    /** Raw weekday check — used by streak logic where past dates must still skip rest days */
    public boolean isRestWeekday(LocalDate date) {
        return restDays.contains(date.getDayOfWeek());
    }

    public boolean isDateComplete(LocalDate date) {
        List<ExerciseData> planned = getExercisesForDate(date);
        if (planned.isEmpty()) return false;
        List<ExerciseData> done = completed.getOrDefault(date, Collections.emptyList());
        return done.containsAll(planned);
    }

    public void markComplete(LocalDate date, ExerciseData ex, boolean done) {
        List<ExerciseData> list = completed.computeIfAbsent(date, d -> new ArrayList<>());
        if (done) { if (!list.contains(ex)) list.add(ex); } else list.remove(ex);
        saveAsync();
    }

    /** Adapter feeding live plan data into the pure, unit-tested streak rules. */
    private final StreakCalculator.Schedule schedule = new StreakCalculator.Schedule() {
        @Override public boolean hasPlanned(LocalDate d) { return !getExercisesForDate(d).isEmpty(); }
        @Override public boolean isComplete(LocalDate d) { return isDateComplete(d); }
        @Override public boolean isRestWeekday(LocalDate d) { return AppRepository.this.isRestWeekday(d); }
    };

    public int computeStreak() {
        return StreakCalculator.computeStreak(LocalDate.now(), schedule);
    }

    /** Streak that WOULD exist if today is completed — see {@link StreakCalculator}. */
    public int computePotentialStreak() {
        return StreakCalculator.computePotentialStreak(LocalDate.now(), schedule);
    }

    public double estimateVolume(ExerciseData ex) {
        double sets = ex.getSets(), reps = ex.getRepsPerSet();
        if (ex.isBodyweight()) return sets*reps;
        double w=ex.getWeightKg();
        return w>0?w*sets*reps:sets*reps;
    }

    /** Renames an exercise and carries its logged-set history along with it. */
    public void renameExercise(ExerciseData ex, String newName) {
        String old = ex.getName();
        if (newName == null || newName.trim().isEmpty() || newName.equals(old)) return;
        ex.setName(newName.trim());
        for (SetLog s : setLogs)
            if (s.getExerciseName().equals(old)) s.setExerciseName(newName.trim());
        saveAsync();
    }

    public String classifyExercise(String name) {
        if (name==null) return "Other";
        String n=name.toLowerCase();
        if (n.contains("bench")||n.contains("press")||n.contains("chest")||n.contains("push")||n.contains("shoulder")) return "Push";
        if (n.contains("row")||n.contains("pull")||n.contains("lat")||n.contains("deadlift")||n.contains("back")) return "Pull";
        if (n.contains("squat")||n.contains("leg")||n.contains("calf")||n.contains("lunge")||n.contains("ham")||n.contains("glute")) return "Legs";
        if (n.contains("curl")||n.contains("tricep")||n.contains("bicep")||n.contains("extension")) return "Arms";
        if (n.contains("plank")||n.contains("crunch")||n.contains("abs")||n.contains("core")) return "Core";
        return "Other";
    }

    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable pendingSave = this::saveAsync;

    /** Coalesces rapid-fire saves (e.g. a TextWatcher firing per keystroke) into one. */
    public void saveDebounced() {
        mainHandler.removeCallbacks(pendingSave);
        mainHandler.postDelayed(pendingSave, 400);
    }

    public void saveAsync() {
        // Snapshot all data on the calling (UI) thread so background serialization
        // never races against UI mutations (prevents ConcurrentModificationException
        // and corrupt JSON files).
        final List<ExerciseData> exSnap     = new ArrayList<>(exercises);
        final List<BodyWeightEntry> bwSnap  = new ArrayList<>(bodyEntries);
        final List<RecordData> recSnap      = new ArrayList<>(records);
        final List<CardioEntry> cardSnap    = new ArrayList<>(cardio);
        final List<SetLog> slSnap           = new ArrayList<>(setLogs);
        final List<String> rdSnap           = new ArrayList<>();
        for (java.time.DayOfWeek d : restDays) rdSnap.add(d.name());
        final List<List<String>> csSnap     = new ArrayList<>();
        for (List<String> c : customSplits) csSnap.add(new ArrayList<>(c));
        final Map<String,List<String>> compSnap = new HashMap<>();
        for (Map.Entry<LocalDate,List<ExerciseData>> e : completed.entrySet()) {
            List<String> names = new ArrayList<>();
            // Full data so deleted-exercise history can be reconstructed after restart:
            // name|addedDate|weight|reps|restSeconds|id
            for (ExerciseData ex : e.getValue())
                names.add(ex.getName()+"|"+ex.getAddedDate()+"|"
                        +(ex.isBodyweight()?"BW":ex.getWeight())+"|"+ex.getReps()+"|"+ex.getRestSeconds()
                        +"|"+ex.getId());
            compSnap.put(e.getKey().toString(), names);
        }
        final List<Food> foodsSnap = new ArrayList<>(savedFoods);
        final Map<String,List<Food>> foodLogSnap = new HashMap<>();
        for (Map.Entry<LocalDate,List<Food>> e : foodLog.entrySet())
            foodLogSnap.put(e.getKey().toString(), new ArrayList<>(e.getValue()));
        final Map<String,Object> prefsSnap = new HashMap<>();
        prefsSnap.put("macroGoals", macroGoals.clone());
        prefsSnap.put("macroActual", macroActual.clone());
        prefsSnap.put("heightCm", heightCm);
        prefsSnap.put("startWeightKg", startWeightKg);
        prefsSnap.put("goalWeightKg", goalWeightKg);
        prefsSnap.put("darkMode", darkMode);
        prefsSnap.put("gender", gender);
        prefsSnap.put("useLbs", useLbs);
        prefsSnap.put("reminderEnabled", reminderEnabled);
        prefsSnap.put("reminderHour", reminderHour);
        prefsSnap.put("reminderMinute", reminderMinute);

        ioExecutor.execute(() -> {
            saveJson("exercises.json", exSnap);
            saveJson("body.json", bwSnap);
            saveJson("records.json", recSnap);
            saveJson("cardio.json", cardSnap);
            saveJson("setlogs.json", slSnap);
            saveJson("rest_days.json", rdSnap);
            saveJson("custom_splits.json", csSnap);
            saveJson("completed.json", compSnap);
            saveJson("foods.json", foodsSnap);
            saveJson("foodlog.json", foodLogSnap);
            saveJson("prefs.json", prefsSnap);
        });
    }

    private void load() {
        List<ExerciseData> ex=loadJson("exercises.json",new TypeToken<List<ExerciseData>>(){}.getType());
        if (ex!=null) exercises.addAll(ex);
        // Migrate pre-id / string-reps records in place
        for (ExerciseData e : exercises) e.normalize();
        List<BodyWeightEntry> bw=loadJson("body.json",new TypeToken<List<BodyWeightEntry>>(){}.getType());
        if (bw!=null) bodyEntries.addAll(bw);
        List<RecordData> rec=loadJson("records.json",new TypeToken<List<RecordData>>(){}.getType());
        if (rec!=null) records.addAll(rec);
        List<CardioEntry> card=loadJson("cardio.json",new TypeToken<List<CardioEntry>>(){}.getType());
        if (card!=null) cardio.addAll(card);
        List<SetLog> sl=loadJson("setlogs.json",new TypeToken<List<SetLog>>(){}.getType());
        if (sl!=null) setLogs.addAll(sl);
        List<String> rd=loadJson("rest_days.json",new TypeToken<List<String>>(){}.getType());
        if (rd!=null) for (String s:rd) { try { restDays.add(java.time.DayOfWeek.valueOf(s)); } catch(Exception ignored) {} }
        List<List<String>> cs=loadJson("custom_splits.json",new TypeToken<List<List<String>>>(){}.getType());
        if (cs!=null) customSplits.addAll(cs);
        Map<String,List<String>> comp=loadJson("completed.json",new TypeToken<Map<String,List<String>>>(){}.getType());
        if (comp!=null) {
            for (Map.Entry<String,List<String>> e:comp.entrySet()) {
                try {
                    LocalDate date=LocalDate.parse(e.getKey());
                    List<ExerciseData> dayEx=new ArrayList<>(getExercisesForDate(date));
                    List<ExerciseData> doneList=new ArrayList<>();
                    for (String pair:e.getValue()) {
                        String[] parts=pair.split("\\|"); if (parts.length<2) continue;
                        String nm=parts[0]; LocalDate ad=LocalDate.parse(parts[1]);
                        String id=parts.length>=6?parts[5]:null;
                        // Consume the match so duplicate names each map to a distinct instance.
                        // Stable id wins (rename-proof); name+addedDate covers old snapshots.
                        boolean matched=false;
                        for (Iterator<ExerciseData> it=dayEx.iterator(); it.hasNext();) {
                            ExerciseData x=it.next();
                            boolean hit = id!=null ? id.equals(x.getId())
                                    : x.getName().equals(nm)&&x.getAddedDate().equals(ad);
                            if (hit) { doneList.add(x); it.remove(); matched=true; break; }
                        }
                        // No live match — exercise was deleted. Reconstruct a placeholder
                        // from the extended format (name|addedDate|weight|reps|rest) so
                        // History keeps showing its volume data.
                        if (!matched && parts.length>=5) {
                            try {
                                doneList.add(new ExerciseData(nm, parts[2], parts[3],
                                        Integer.parseInt(parts[4]), ad));
                            } catch(Exception ignored){}
                        }
                    }
                    if (!doneList.isEmpty()) completed.put(date,doneList);
                } catch(Exception ignored){}
            }
        }
        List<Food> sf=loadJson("foods.json",new TypeToken<List<Food>>(){}.getType());
        if (sf!=null) savedFoods.addAll(sf);
        Map<String,List<Food>> fl=loadJson("foodlog.json",new TypeToken<Map<String,List<Food>>>(){}.getType());
        if (fl!=null) for (Map.Entry<String,List<Food>> e:fl.entrySet()) {
            try { foodLog.put(LocalDate.parse(e.getKey()), new ArrayList<>(e.getValue())); } catch(Exception ignored){}
        }
        Map<String,Object> prefs=loadJson("prefs.json",new TypeToken<Map<String,Object>>(){}.getType());
        if (prefs!=null) {
            Object h=prefs.get("heightCm"); if (h instanceof Number) heightCm=((Number)h).doubleValue();
            Object sw=prefs.get("startWeightKg"); if (sw instanceof Number) startWeightKg=((Number)sw).doubleValue();
            Object gw=prefs.get("goalWeightKg"); if (gw instanceof Number) goalWeightKg=((Number)gw).doubleValue();
            Object d=prefs.get("darkMode"); if (d instanceof Boolean) darkMode=(Boolean)d;
            Object g=prefs.get("gender"); if (g instanceof String) gender=(String)g;
            Object u=prefs.get("useLbs"); if (u instanceof Boolean) useLbs=(Boolean)u;
            Object re=prefs.get("reminderEnabled"); if (re instanceof Boolean) reminderEnabled=(Boolean)re;
            Object rh=prefs.get("reminderHour"); if (rh instanceof Number) reminderHour=((Number)rh).intValue();
            Object rm=prefs.get("reminderMinute"); if (rm instanceof Number) reminderMinute=((Number)rm).intValue();
            Object mg=prefs.get("macroGoals"); if (mg instanceof List) { List<?> l=(List<?>)mg; for (int i=0;i<Math.min(4,l.size());i++) macroGoals[i]=String.valueOf(l.get(i)); }
            Object ma=prefs.get("macroActual"); if (ma instanceof List) { List<?> l=(List<?>)ma; for (int i=0;i<Math.min(4,l.size());i++) macroActual[i]=String.valueOf(l.get(i)); }
        }
        Units.setUseLbs(useLbs);
        if (records.isEmpty()) {
            records.add(new RecordData("Squat",""));
            records.add(new RecordData("Bench Press",""));
            records.add(new RecordData("Deadlift",""));
            records.add(new RecordData("Overhead Press",""));
            records.add(new RecordData("Barbell Row",""));
        }
    }

    public void clearAll() {
        exercises.clear(); completed.clear(); bodyEntries.clear();
        records.clear(); cardio.clear(); setLogs.clear(); restDays.clear(); customSplits.clear();
        foodLog.clear(); savedFoods.clear();
        Arrays.fill(macroGoals,""); Arrays.fill(macroActual,"");
        startWeightKg = 0; goalWeightKg = 0;
        gender = ""; // re-asked on next launch
        for (String f : DATA_FILES) new File(context.getFilesDir(),f).delete();
    }

    /** Sum of a macro across today's logged foods. idx: 0=cals 1=protein 2=carbs 3=fats */
    public double todayMacro(int idx) {
        double sum = 0;
        for (Food f : foodLog.getOrDefault(LocalDate.now(), Collections.emptyList())) sum += f.macro(idx);
        return sum;
    }

    private <T> void saveJson(String filename, T data) {
        // Write to a temp file then rename so a crash mid-write can never
        // corrupt (and therefore lose) the existing data file.
        File target=new File(context.getFilesDir(),filename);
        File tmp=new File(context.getFilesDir(),filename+".tmp");
        try (Writer w=new FileWriter(tmp)) { gson.toJson(data,w); }
        catch(IOException e) {
            android.util.Log.e("IronPulse","Failed to save "+filename,e);
            tmp.delete(); return;
        }
        if (!tmp.renameTo(target)) {
            target.delete();
            if (!tmp.renameTo(target))
                android.util.Log.e("IronPulse","Failed to replace "+filename);
        }
    }

    private <T> T loadJson(String filename, Type type) {
        File f=new File(context.getFilesDir(),filename);
        if (!f.exists()) return null;
        try (Reader r=new FileReader(f)) { return gson.fromJson(r,type); }
        catch(JsonSyntaxException e) {
            android.util.Log.e("IronPulse","Corrupt JSON in "+filename+" — resetting",e);
            f.delete(); return null;
        } catch(Exception e) {
            android.util.Log.e("IronPulse","Failed to load "+filename,e);
            return null;
        }
    }
}
