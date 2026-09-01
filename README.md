# IronPulse

A native Android workout, nutrition and body tracker written in Java. Plan a
weekly training split, log sets against it, track personal records, cardio,
macros and body weight — and get the whole thing scored back at you.

**It has no `INTERNET` permission.** Everything is stored in the app's private
storage on the device. There is no account, no sync, no analytics and no
third-party SDK; the only way data leaves the phone is a backup file the user
exports themselves.

## Features

- **Workout plans** — exercises scheduled across weekdays, with rest days, custom split templates and a dynamic date timeline
- **Set logging** — per-set weight and reps, with a rest timer that keeps running when the app is closed
- **Split assessment** — scores your weekly programme out of 100 on push/pull/legs balance, sets per session, training days and rest periods, then explains the score
- **Progress** — history charts (training volume or estimated 1RM), a month heatmap, personal records, and a body-weight trend against a goal
- **Macros** — daily targets, quick-add foods, a macro preset calculator and a 30-day calories-vs-goal chart
- **Plate calculator** — per-side breakdown for a given bar and target weight, in kg or lb
- **Awards** — 11 achievements derived from your existing data, with unlock dates that persist even if the streak later breaks
- **Home-screen widget** — current streak and today's progress
- **Backup & restore** — export every data file as a zip, import it back

## Stack

Java 17 · Android Gradle Plugin 8.2.2 · compileSdk/targetSdk 36 · minSdk 26
Material Components 1.11 · RecyclerView · Gson 2.10.1 · JUnit 4.13.2

No dependency injection framework, no charting library, no Room. 40 classes,
~5,500 lines.

## Architecture

```
com.ironpulse
  data/      AppRepository (singleton, Gson persistence), StreakCalculator,
             Achievements, Units, LocalDate/DayOfWeek type adapters
  model/     ExerciseData, SetLog, BodyWeightEntry, CardioEntry, RecordData, Food
  notify/    reminders, rest timer, quick-log action, boot receiver
  ui/        MainActivity + 5 fragments, 4 custom Canvas views, More-screen tabs
  widget/    WorkoutWidgetProvider
```

`AppRepository` is the single source of truth: one singleton holding every list
in memory, persisted to 12 JSON files through Gson on a single-thread executor.
`LocalDate` and `DayOfWeek` need custom type adapters because Gson has no
built-in support for `java.time`.

The UI is a `MainActivity` with five bottom-nav fragments. `MoreFragment` is a
slim host for four tab classes (`PRsTab`, `MacrosTab`, `AssessmentTab`,
`SettingsTab`) — it was one 826-line fragment before that split.

## Key technical decisions

**Zero network permissions, by design.** The manifest requests `VIBRATE`,
`POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED` — and nothing else. There is no
`INTERNET` permission, so the app is structurally incapable of transmitting user
data, rather than merely promising not to. Backups go through a `FileProvider`,
so the user chooses where each export lands.

**Streak rules live in a class with no Android imports.** `StreakCalculator`
takes a small `Schedule` interface and returns an integer, which makes the
walk-back logic testable on the JVM. The rules are less obvious than they look:
rest days and days with nothing planned are skipped silently so they don't break
a chain, but skipping without limit would let one exercise added today inflate a
streak across a six-month gap — so at most six consecutive skips are allowed.
There is also a `computePotentialStreak` used for "complete today to extend",
and a test asserting the two agree.

**Charts are drawn, not imported.** `HistoryChartView`, `WeightChartView`,
`MonthHeatmapView` and `MacroRingsView` are custom `View` subclasses drawing
directly to `Canvas`. A charting dependency would have been faster to write and
considerably larger; the R8-shrunk release APK is 1.95 MB.

**Exercises are keyed by UUID, not name.** History was originally keyed by
exercise name, so renaming an exercise orphaned its records and two exercises
with the same name collided. Every `ExerciseData` now carries a UUID assigned on
load if missing, and completion snapshots match by ID — which is what made
renaming safe.

**Achievements persist their unlock date.** The rules are evaluated against
current data, but once earned the unlock date is written to `achievements.json`,
so a badge stays earned even if the underlying streak later breaks.

**Notifications survive the process and the reboot.** The rest timer is backed by
`AlarmManager` rather than an in-process countdown, so it fires whether or not
the app is alive; `BootReceiver` re-registers alarms after a restart. The rest
notification carries a "Log set" action that repeats the last set and chains the
next rest without opening the app, and the exercise screen recounts on resume.

## Testing

28 JUnit tests, run on the JVM with no emulator:

```bash
./gradlew test
```

They cover the streak rules (rest-day skips, the six-skip cap, the
potential-streak invariant), `ExerciseData` parsing and the migration from the
legacy `"3x10"` sets string to canonical integers, and unit-conversion
round-trips. These are the parts where a silent regression would corrupt stored
data or quietly show the wrong number — the UI is verified by hand.

## Building

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/
./gradlew assembleRelease    # needs keystore.properties, see below
```

The release build runs R8 with `minifyEnabled` and `shrinkResources`.
`proguard-rules.pro` keeps the Gson model classes, which are otherwise stripped
and take the saved data with them.

Release signing reads `keystore.properties` from the project root
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Both that file and the
keystore are untracked; without them the release build is simply unsigned.

## Privacy

Full policy in [docs/privacy-policy.md](docs/privacy-policy.md). The short
version: everything stays on the device.
