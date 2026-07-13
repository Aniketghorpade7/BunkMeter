# BunkMeter — Codebase Context

Android attendance tracking app. Automatically detects if a student is in class via GPS, fires WorkManager-scheduled notifications, and lets them manually mark attendance. Built in Java with MVVM + Repository pattern.

---

## Project Config

- **Package**: `com.bunkmeter.app`
- **Language**: Java (not Kotlin)
- **Min SDK**: 26 | **Target/Compile SDK**: 36
- **Version**: 1.0 (versionCode 1)
- **DB name**: `bunkmeter_db` (Room, version 3)
- **Application class**: `BunkMeterApp` — applies Material You dynamic colors and kicks off `NotificationScheduler.scheduleDailySetupAt7AM`

---

## Architecture

```
UI (Fragment/Activity/ViewModel)
    ↓
Repository (off-thread via databaseWriteExecutor or ExecutorService)
    ↓
DAO (Room)
    ↓
AppDatabase (SQLite, singleton)
```

Background work: **WorkManager** workers (never bare threads for scheduled tasks).

---

## Data Models

All Room `@Entity` classes live in `model/`.

| Entity | Key Fields |
|---|---|
| `Subject` | `subjectId` (PK auto), `name`, `color` (hex string) |
| `Classroom` | `classroomId` (PK auto), `name`, `latitude`, `longitude`, `radius` (float, metres), `isActive` (soft-delete flag) |
| `Timetable` | `timetableId`, `subjectId` (FK→Subject CASCADE), `dayOfWeek` (0=Mon…5=Sat), `startTime`, `endTime` (both minutes-from-midnight), `classroomId` (FK→Classroom nullable), `type` |
| `Attendance` | `attendanceId`, `subjectId` (FK→Subject CASCADE), `date` ("yyyy-MM-dd"), `startTime`, `endTime`, `status` (int), `classroomId` (FK→Classroom nullable, CASCADE), `locationVerified` (boolean) |

### Non-entity models
- **`HomeLectureItem`** — result of the Room JOIN query for the Home screen. `timetableId = -1` means it's an *extra/temporary* class (not in the regular timetable).
- **`AttendanceStatus`** enum — `BUNK(0)`, `PRESENT(1)`, `CANCELLED(2)`. `Attendance.status` stores the raw int; use `getAttendanceStatus()` / `setAttendanceStatus()` everywhere (the old deprecated `Attendance.PRESENT`/`ABSENT` int constants were removed).

### Key conventions
- **Time**: stored as **minutes from midnight** (e.g. 9:30 AM = 570)
- **Date**: stored as **"yyyy-MM-dd"** string
- **Day of week**: `0=Mon, 1=Tue, 2=Wed, 3=Thu, 4=Fri, 5=Sat, -1=Sun` (no lectures Sunday)
- **Status ints**: `0=Bunk, 1=Present, 2=Cancelled` — null in `HomeLectureItem.attendanceStatus` = Pending
- **Day mapping lives in ONE place**: `utils/DateUtils.calendarToAppDay()`. Never re-implement the Calendar→app-day mapping inline (that's what caused the old Saturday bug).

---

## Database Layer

**`AppDatabase`** — singleton, 4-thread write executor (`databaseWriteExecutor`). Always use this executor for DB writes.

Migration: `MIGRATION_2_3` — rewrites Attendance table, converts `classroomId=0` to NULL (proper nullable FK).

### DAOs

| DAO | Notable queries |
|---|---|
| `AttendanceDao` | `getSpecificAttendance(subjectId, date, startTime)` — checks for existing record; `updateAttendanceStatus(...)` — direct SQL update; `getLiveAttendanceForDate(date)` — LiveData; `getTodaysLecturesLive` is on **TimetableDao** |
| `SubjectDao` | Standard CRUD; `getSubjectById(id)` sync |
| `TimetableDao` | `getConflictingSlots` / `deleteConflicts` — overlap detection; `getTodaysLecturesLive(dayOfWeek, todayDate)` — big UNION JOIN query used by HomeViewModel; `getTimetableAndExtraForDaySync` — used by DailySetupWorker |
| `ClassroomDao` | `softDelete(id)` sets `isActive=0`; only `getActiveClassrooms()` returns rows |
| `ResetDao` | Delete order: Attendance → Timetable → Subject → Classroom (FK constraint order) |

---

## Repositories

All in `repository/`. Each wraps a DAO and handles threading.

- **`AttendanceRepository`**: `updateAttendanceStatus(...)` — **upsert logic**: checks for existing record, updates if found, inserts new if not. Auto-looks up `classroomId` **and `endTime`** from the Timetable when not supplied (the endTime lookup fixes the old "endTime = 0" bug). Overloads: with/without `endTime`, and int/`AttendanceStatus` variants.
- **`TimetableRepository`**: every mutation (insert/update/delete/replaceConflicts) calls `NotificationScheduler.rescheduleTodaysScheduleNow(application)` after the DB write.
- **`ClassroomRepository`**: callback-style API (`OnClassroomsLoadedListener`, etc.) + Handler for main-thread callbacks. After insert/update/softDelete it calls `GeofenceManager.registerAllClassrooms(application)` to keep the OS geofences in sync.
- **`ResetRepository`**: sequential delete via `newSingleThreadExecutor`, callbacks to UI thread.
- **`SubjectRepository`**: standard CRUD.

---

## Scheduler / Workers

All background work uses **WorkManager**. Workers are in `scheduler/`.

### Daily chain kicked off by `NotificationScheduler`

```
App start
  └→ NotificationScheduler.scheduleDailySetupAt7AM()
        └→ DailySetupWorker (periodic, every 24h, starts at 7 AM)
```

`rescheduleTodaysScheduleNow()`: cancels all `TAG_TODAYS_SCHEDULE` work, immediately enqueues a fresh `DailySetupWorker`.

### `DailySetupWorker`

For each lecture today (from `getTimetableAndExtraForDaySync`):
1. **`GreetingWorker`** — fires at `(firstLecture - 30min)` or 8:45 AM if no lectures. Shows "Good morning, X lectures today."
2. **`PreLectureWorker`** — fires at `(startTime - 10 min)`. "Class starting in 10 minutes."
3. **`LectureStartWorker`** — fires at `startTime`. Shows the manual "Are you in class?" prompt — but first checks if geofencing already marked the lecture, and skips if so.
4. **`OngoingLectureWorker`** — fires at `startTime + 30 min`. If still unmarked → re-shows the prompt (it no longer starts a foreground service).
5. **`AutoBunkWorker`** — fires at `endTime`. If still unmarked → records BUNK (safety net so lectures never stay Pending forever).

All workers tagged `TAG_TODAYS_SCHEDULE = "todays_schedule"`. The per-lecture workers that must be cancellable on a decision (`OngoingLectureWorker`, `AutoBunkWorker`) are additionally tagged `SESSION_<sessionId>`. The id comes from `AttendanceLogic.sessionId(subjectId, date, startTime)` — the **single source** used by both the scheduler and the cancellers (`GeofenceBroadcastReceiver`, `AttendanceActionReceiver`), so they can't drift apart and silently break cancellation.

### Automatic Attendance — Geofencing (replaced the old GPS-polling system)

Automatic "am I in class?" detection uses the **Android Geofencing API**, not per-lecture GPS polling. The OS watches the user's location (fused WiFi + cell + GPS) and notifies us on geofence entry. This works indoors and doesn't drain battery.

**`GeofenceManager`** (`scheduler/`) — `registerAllClassrooms(context)` rebuilds the full OS geofence set from every active classroom. Idempotent (removes then re-adds). Called from:
- `BunkMeterApp.onCreate` (every cold start)
- `ClassroomRepository` after any insert/update/softDelete
- `BootReceiver` after `BOOT_COMPLETED` (geofences are wiped on reboot)
- the background-permission grant callback in `AddEditClassroomBottomSheet`

Geofence request ID = the classroom's primary key (as a String). Radius is clamped to a **100 m floor** (`MIN_GEOFENCE_RADIUS_METERS`) because the OS can't reliably detect smaller geofences. Transition = `GEOFENCE_TRANSITION_ENTER`. No-ops if location permission (incl. background on API 29+) isn't granted → app degrades gracefully to manual marking.

**`GeofenceBroadcastReceiver`** (`receiver/`) — receives ENTER events. Uses `goAsync()` to do DB work off the main thread. For each entered classroom it asks `TimetableDao.getActiveLecturesForClassroomSync(day, classroomId, nowMins)` ("is a lecture in THIS room right now?", with a 15-min early grace window). If a lecture is active and not already marked → marks PRESENT and cancels that lecture's `SESSION_<id>` work + active notification.

**`BootReceiver`** (`receiver/`) — re-registers geofences after reboot. Needs `RECEIVE_BOOT_COMPLETED`.

### Permissions for geofencing

`ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` (the "Allow all the time" grant, requested separately on API 29+ via `AddEditClassroomBottomSheet.maybeRequestBackgroundLocation()`). Without background permission, geofencing silently doesn't run and the manual notification path takes over.

---

## Notifications

All built in `AttendanceNotificationHelper`. Notification IDs use `Objects.hash(...)` — deterministic, collision-resistant.

| Method | Channel | When |
|---|---|---|
| `triggerActiveLectureNotification` | `active_lecture_channel` | At lecture start (and the +30 min reminder) — ongoing, auto-dismisses after the remaining lecture duration |
| `triggerCreateClassroomNotification` | `create_classroom_prompt` | When attendance marked for a subject with no classroom (debounced once/day) |

Notification actions (Present/Bunk/Cancel) broadcast to **`AttendanceActionReceiver`**.

### `AttendanceActionReceiver`

Handles `ACTION_PRESENT`, `ACTION_BUNK`, `ACTION_CANCEL`:
1. Dismisses the notification
2. Calls `AttendanceRepository.updateAttendanceStatus`
3. Cancels `SESSION_<sessionId>` WorkManager work (stops the OngoingLecture reminder + AutoBunkWorker)

---

## UI

### Navigation

`MainActivity` — BottomNavigationView with 3 tabs, requests `POST_NOTIFICATIONS` on Android 13+.

| Tab | Fragment/Activity |
|---|---|
| Home | `HomeFragment` |
| Subjects | `LectureListFragment` |
| Settings | `SettingsFragment` |

### Home Screen

- **`HomeViewModel`** — exposes `LiveData<List<HomeLectureItem>>` from `TimetableDao.getTodaysLecturesLive`. `markAttendance()` calls repository; UI auto-updates via LiveData. If no classroom → fires classroom notification.
- **`LectureAdapter`** — status colors: grey=PENDING, green=PRESENT, red=BUNKED, grey=CANCELLED.
- FAB → `AddEditLectureDialog` with `isTemporary=true` to add an extra class for today.

### Settings Screen (`SettingsFragment`)

Links to: `TimetableActivity`, `ClassroomActivity`, `EditProfileActivity`, `ExportActivity`. Has a Reset flow (shows warning BottomSheet → optionally exports first → calls `ResetRepository`).

### Subject Detail (`SubjectDetailActivity` + `SubjectViewModel`)

Per-subject view: pie chart (MPAndroidChart), attendance heatmap grid (`HeatmapAdapter`, `AttendanceGridAdapter`), lecture list (`LectureListFragment`). Stats: total/present/absent counts.

### Timetable (`TimetableActivity`)

`TimetableAdapter` + `DayAdapter`. Uses `AddEditLectureDialog` for adding/editing slots. Conflict detection via `TimetableRepository.getConflicts`.

### Classroom (`ClassroomActivity` + fragments)

`ClassroomListFragment` → `ClassroomAdapter` → `AddEditClassroomBottomSheet`. Captures lat/lng/radius for each room.

### Export (`ExportActivity` + `ExcelGenerator` + `ExportWorker`)

Apache POI generates `.xlsx`. `ExportWorker` is a WorkManager worker. Triggered from Settings, can precede a reset.

### Profile (`EditProfileActivity`)

Stores student name, PRN, department, semester, profile photo, ID card photo in SharedPreferences.

---

## Key Libraries

| Lib | Purpose |
|---|---|
| Room 2.6.1 | SQLite ORM |
| WorkManager 2.9.0 | Background scheduling |
| play-services-location 21.1.0 | FusedLocationProviderClient (GPS) |
| Apache POI 5.2.3 | Excel export |
| MPAndroidChart v3.1.0 | Pie/bar charts |
| AmbilWarna 2.0.1 | Color picker |
| Material Components 1.11.0 | Material You dynamic colors |
| Lifecycle LiveData/ViewModel KTX 2.7.0 | MVVM reactive data |

---

## SharedPreferences

| File | Key | Purpose |
|---|---|---|
| `bunkmeter_prefs` | `classroom_notif_date` | Prevents repeated classroom setup notifications per day (used by both `DailySetupWorker` and `HomeViewModel`) |

> The old `attendance_temp_<lectureId>` GPS-score prefs (via `TempReadingStorage`) were removed along with the GPS-polling system.

---

## File Map (source tree)

```
app/src/main/java/com/bunkmeter/app/
├── BunkMeterApp.java                   # Application class
├── database/
│   ├── AppDatabase.java                # Room DB singleton, migrations, executor
│   ├── AttendanceDao.java
│   ├── SubjectDao.java
│   ├── TimetableDao.java               # HOME JOIN query + getActiveLecturesForClassroomSync (geofence)
│   ├── ClassroomDao.java
│   └── ResetDao.java
├── model/
│   ├── Subject.java
│   ├── Classroom.java
│   ├── Attendance.java
│   ├── Timetable.java
│   ├── HomeLectureItem.java            # JOIN result for home screen
│   └── AttendanceStatus.java           # Enum: BUNK=0, PRESENT=1, CANCELLED=2
├── repository/
│   ├── AttendanceRepository.java       # Upsert logic
│   ├── SubjectRepository.java
│   ├── TimetableRepository.java        # Auto-reschedules on every mutation
│   ├── ClassroomRepository.java        # Callback-style async API
│   └── ResetRepository.java
├── scheduler/
│   ├── NotificationScheduler.java      # Entry point: 7AM periodic + reschedule
│   ├── DailySetupWorker.java           # Orchestrates daily workers + AutoBunkWorker
│   ├── GreetingWorker.java             # Good morning notification
│   ├── PreLectureWorker.java           # 10-min warning
│   ├── LectureStartWorker.java         # Shows manual prompt (skips if already marked)
│   ├── OngoingLectureWorker.java       # +30min reminder (re-shows prompt if unmarked)
│   ├── AutoBunkWorker.java             # Marks BUNK at lecture end if still pending
│   └── GeofenceManager.java            # Registers/removes OS classroom geofences
├── notifications/
│   └── AttendanceNotificationHelper.java
├── receiver/
│   ├── AttendanceActionReceiver.java   # Handles Present/Bunk/Cancel actions
│   ├── GeofenceBroadcastReceiver.java  # OS geofence ENTER → auto-mark PRESENT
│   └── BootReceiver.java               # Re-registers geofences after reboot
├── location/
│   └── SystemCheckUtils.java           # Battery optimization dialog
├── ui/
│   ├── main/MainActivity.java
│   ├── home/
│   │   ├── HomeFragment.java
│   │   ├── HomeViewModel.java
│   │   └── LectureAdapter.java
│   ├── subject/
│   │   ├── SubjectDetailActivity.java
│   │   ├── SubjectViewModel.java
│   │   ├── SubjectAdapter.java
│   │   ├── LectureListFragment.java
│   │   ├── AttendanceGridAdapter.java
│   │   └── HeatmapAdapter.java
│   └── settings/
│       ├── SettingsFragment.java
│       ├── AddEditLectureDialog.java   # Shared dialog for timetable + extra class
│       ├── TimetableActivity.java
│       ├── timetable/
│       │   ├── TimetableAdapter.java
│       │   ├── DayAdapter.java
│       │   ├── ManageSubjectsBottomSheet.java
│       │   └── AddSubjectDialog.java
│       ├── ClassroomActivity.java
│       ├── classroom/
│       │   ├── ClassroomListFragment.java
│       │   ├── ClassroomAdapter.java
│       │   └── AddEditClassroomBottomSheet.java
│       ├── EditProfileActivity.java
│       ├── ExportActivity.java
│       └── export/
│           ├── ExcelGenerator.java
│           └── ExportWorker.java
└── utils/
    ├── PermissionUtils.java
    ├── DateUtils.java                  # Single source of truth for day/time mapping
    └── AttendanceLogic.java            # Pure decision rules: sessionId, lecture-time window

# MockDataGenerator lives in src/debug/java (real impl) + src/release/java (no-op stub),
# NOT in src/main — so test-data injection can never ship in a release build.
```

### Tests (`app/src/test/java/com/bunkmeter/app/`)

Host-side JUnit4 (runs on the JVM, no emulator) — possible because the rules
live in the Android-free `DateUtils` / `AttendanceLogic`:
- `DateUtilsTest` — day-of-week mapping (guards the Saturday regression), date/time helpers.
- `AttendanceLogicTest` — `sessionId` determinism/uniqueness/non-zero, and the
  inclusive lecture-time window (start − 15 min … end).

Run with `./gradlew testDebugUnitTest` (or the green gutter arrow in Android Studio).

---

## Attendance decision flow (current design)

Three layers cooperate; the first to act cancels the rest for that lecture:

1. **Geofencing (automatic)** — OS fires `GeofenceBroadcastReceiver` on entering a classroom. If a lecture is active there → mark PRESENT.
2. **Manual notification** — `LectureStartWorker` shows a Present/Bunk/Cancel prompt at start (skipped if geofencing already marked it); `OngoingLectureWorker` re-shows it at +30 min if still pending.
3. **Auto-bunk safety net** — `AutoBunkWorker` marks BUNK at lecture end if nothing else decided.

`SESSION_<id>` is the WorkManager tag that ties a lecture's cancellable jobs together; `GeofenceBroadcastReceiver` and `AttendanceActionReceiver` both cancel it on a decision.

If background location permission is denied, layer 1 is simply inactive and layers 2–3 carry the whole flow — no crashes, no broken state.
