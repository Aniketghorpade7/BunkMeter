# BunkMeter — Bugs & Design Issues

Found by static analysis of the full source. Issues are grouped by severity.

> **Update (geofencing rework):** The location system was rewritten from GPS-polling
> to the Android Geofencing API. That rework, plus targeted fixes, resolved most of
> the issues below. Each entry is now tagged **✅ Resolved**, **➖ Obsolete** (the code
> no longer exists), or **⬜ Still open**. See the status table at the bottom.

---

## Critical Bugs (wrong behavior in production)

### 1. Saturday lectures never appear on the Home screen — ✅ Resolved

**Files**: `HomeViewModel.java` vs `DailySetupWorker.java`

`HomeViewModel.getMappedDay()` only maps Monday–Friday and returns `-1` for both Saturday and Sunday, treating Saturday as a "no lectures" day:

```java
// HomeViewModel.java
if (currentDayOfWeek == Calendar.FRIDAY) return 4;
return -1; // Saturday / Sunday — WRONG, Saturday is day 5
```

`DailySetupWorker.getMappedDay()` correctly maps Saturday → `5`:

```java
if (currentDayOfWeek == Calendar.SATURDAY) return 5;
return -1;
```

**Impact**: On Saturday, WorkManager fires all scheduled notifications (PreLecture, LectureStart, GPS checks) because `DailySetupWorker` queries Saturday correctly. But the Home screen shows "No lectures today" because `HomeViewModel` returns `-1` → empty LiveData. Students can't manually mark attendance from the UI on Saturday.

**Fix**: Add `if (currentDayOfWeek == Calendar.SATURDAY) return 5;` to `HomeViewModel.getMappedDay()`.

---

### 2. `endTime` is never set when creating a new Attendance record — ✅ Resolved

**File**: `AttendanceRepository.java` — `updateAttendanceStatus()` upsert branch

When no existing record is found and a new `Attendance` is inserted, `endTime` is never set. It defaults to `0`:

```java
Attendance newAttendance = new Attendance();
newAttendance.setSubjectId(subjectId);
newAttendance.setDate(date);
newAttendance.setStartTime(startTime);
newAttendance.setStatus(status);
newAttendance.setLocationVerified(false);
// endTime is never set → stored as 0 in DB
attendanceDao.insertAttendance(newAttendance);
```

The method signature is `updateAttendanceStatus(subjectId, date, startTime, classroomId, status)` — `endTime` isn't even a parameter.

**Impact**: Every attendance record created via a notification action (Present/Bunk/Cancel from `AttendanceActionReceiver`) or from the Home screen has `endTime = 0` in the database. This silently corrupts duration-based stats and any future export that calculates lecture length.

**Fix**: Pass `endTime` through the call chain — add it as a parameter to `updateAttendanceStatus()`, thread it through `AttendanceActionReceiver`, `LocationReadingWorker`, and `HomeViewModel.markAttendance()`.

---

### 3. Marking attendance from the Home screen doesn't dismiss the active lecture notification — ✅ Resolved

**Files**: `HomeViewModel.java`, `AttendanceNotificationHelper.java`

`LectureStartWorker` fires an ongoing notification at lecture start (channel `active_lecture_channel`). When the user marks attendance by tapping a button in `AttendanceActionReceiver`, that receiver cancels the notification explicitly:

```java
// AttendanceActionReceiver.java — correctly cancels
nm.cancel(notificationId);
```

`LocationReadingWorker` also cancels it after auto-marking:

```java
int activeNotifId = Objects.hash("active", subjectId, date, startTime);
nm.cancel(activeNotifId);
```

But when the user marks attendance from the Home screen UI buttons, `HomeViewModel.markAttendance()` only calls the repository — it never touches the notification:

```java
public void markAttendance(int subjectId, int startTime, Integer classroomId, int status) {
    attendanceRepo.updateAttendanceStatus(...);  // DB write only
    // Active lecture notification is still showing!
}
```

**Impact**: Students who mark attendance via the app UI still see an ongoing, non-dismissible "Are you in class?" notification for the rest of the lecture duration.

**Fix**: In `HomeViewModel.markAttendance()`, compute the notification ID (`Objects.hash("active", subjectId, todayDate, startTime)`) and cancel it via `NotificationManager`.

---

## Logical Issues (not crashes, but incorrect behavior)

### 4. `LocationReadingWorker` retry in the bounce zone can overlap with later readings — ➖ Obsolete

**File**: `LocationReadingWorker.java`, `AttendanceScheduler.java`

When the student is outside the classroom radius but within 100 m (the "bounce zone"), the worker returns `Result.retry()`:

```java
} else if (distanceInMeters <= 100f) {
    completer.set(Result.retry()); // retries with exponential backoff
    return;
}
```

The three readings are enqueued as independent `OneTimeWorkRequest`s with delays of +10, +15, +20 minutes. A retried reading #1 (with exponential backoff) can fire AFTER reading #2 or #3 have already run and completed. When it finally fires:
- `readingIndex` is still `1`, so the "final reading" check (`readingIndex == 3`) never triggers even if readings 2 and 3 already ran.
- The score accumulation is unpredictable since readings arrive out-of-order.

**Fix**: Either chain the readings sequentially (reading 2 starts only after reading 1 completes), or limit `Result.retry()` retries, or remove the bounce zone retry and just score 0 for "outside radius".

---

### 5. Classroom setup notification fires with no daily debounce from the Home screen — ✅ Resolved

**Files**: `HomeViewModel.java`, `DailySetupWorker.java`

`DailySetupWorker` fires the "classroom not set up" notification at most once per day via a SharedPreferences guard:

```java
if (!todayDate.equals(lastNotifiedDay)) {
    AttendanceNotificationHelper.triggerCreateClassroomNotification(getApplicationContext());
    prefs.edit().putString("classroom_notif_date", todayDate).apply();
}
```

`HomeViewModel.markAttendance()` fires it on **every tap** with no guard:

```java
if (classroomId == null || classroomId == 0) {
    AttendanceNotificationHelper.triggerCreateClassroomNotification(getApplication());
}
```

Since the notification ID is stable (`Objects.hash("classroom_prompt")`), the same notification is just re-posted each time rather than stacking up. But a notification the user dismissed will reappear every time they mark attendance for a classroom-less subject.

**Fix**: Apply the same `classroom_notif_date` SharedPreferences guard in `HomeViewModel`.

---

### 6. `FOREGROUND_SERVICE_TYPE_SHORT_SERVICE` check uses wrong API level — ➖ Obsolete

**File**: `AttendanceForegroundService.java`

The service calls `startForeground` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE` gated on API 29 (Q):

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(sessionId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
} else {
    startForeground(sessionId, notification);
}
```

`FOREGROUND_SERVICE_TYPE_SHORT_SERVICE` was introduced in API 34 (Android 14, `UPSIDE_DOWN_CAKE`), not API 29. On API 29–33, passing this type constant is undefined behavior — the constant resolves to its integer value (`0x800`) at compile time, but the OS doesn't recognise it and may log errors or behave unexpectedly.

**Fix**: Change the check to `Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE` (API 34).

---

## Design Issues (code quality / maintainability)

### 7. `getMappedDay()` duplicated across three classes — ✅ Resolved

The Calendar-day → app-day-number mapping is copy-pasted in `HomeViewModel`, `DailySetupWorker`, and `AttendanceRepository`. Each copy is slightly different (the HomeViewModel one is wrong — see bug #1). This is also the root cause of the Saturday bug.

**Fix**: Extract to a single static utility method in a `DateUtils` or `TimeUtils` class and use it everywhere.

---

### 8. `ClassroomRepository` creates its own `ExecutorService` and never shuts it down — ✅ Resolved

Every other repository routes writes through `AppDatabase.databaseWriteExecutor` (a shared 4-thread pool). `ClassroomRepository` creates a new `Executors.newSingleThreadExecutor()` per instance and never calls `executor.shutdown()`:

```java
public ClassroomRepository(Application application) {
    ...
    executor = Executors.newSingleThreadExecutor(); // leaks thread
}
```

Since `ClassroomRepository` is typically created inside Fragments (not in a ViewModel or singleton), this leaks a thread on every Fragment recreation.

**Fix**: Use `AppDatabase.databaseWriteExecutor` for writes (matching other repos) and a `Handler(Looper.getMainLooper())` for the callbacks.

---

### 9. `TempReadingStorage` Javadoc references a class that doesn't exist — ➖ Obsolete

```java
 * {@link com.bunkmeter.app.scheduler.EvaluationWorker} can make smarter decisions
```

There is no `EvaluationWorker` in the codebase — evaluation was inlined into `LocationReadingWorker`. This is a stale reference left over from a refactor.

**Fix**: Update the Javadoc to reference `LocationReadingWorker`.

---

### 10. `MockDataGenerator` is in the main source set with no production guard — ✅ Resolved

`utils/MockDataGenerator.java` lives in the main source set alongside production code. If any call to it is accidentally left in or introduced during development, it can silently insert fake data into a real user's database.

**Fix**: Move it to `src/debug/java/...` (a debug-only source set) so it is never compiled into release builds.

---

### 11. Incomplete migration from `Attendance` int constants to `AttendanceStatus` enum — ✅ Resolved

`Attendance.java` still declares `@Deprecated` constants `PRESENT = 1` and `ABSENT = 0`. The field is named `ABSENT` but semantically maps to `BUNK`. Some places in `LectureAdapter` still compare raw ints (`item.attendanceStatus == 1`, `== 0`, `== 2`) instead of using `AttendanceStatus.fromInt()`. The enum migration was started but never completed.

**Fix**: Remove the deprecated constants from `Attendance`, update `LectureAdapter` to use `AttendanceStatus.fromInt(item.attendanceStatus)`, and replace all raw int comparisons.

---

## Summary Table

| # | Severity | Status | File(s) | Issue |
|---|---|---|---|---|
| 1 | Critical | ✅ Resolved | `HomeViewModel` → `DateUtils` | Saturday lectures never shown (missing day mapping) |
| 2 | Critical | ✅ Resolved | `AttendanceRepository` | `endTime = 0` in all newly created Attendance rows |
| 3 | Critical | ✅ Resolved | `HomeViewModel` | Active lecture notification not dismissed on manual mark |
| 4 | Logical | ➖ Obsolete | ~~`LocationReadingWorker`~~ | Retry in bounce zone causes out-of-order readings (file deleted) |
| 5 | Logical | ✅ Resolved | `HomeViewModel` | Classroom notification re-fires on every attendance mark |
| 6 | Logical | ➖ Obsolete | ~~`AttendanceForegroundService`~~ | Wrong API level check (service deleted) |
| 7 | Design | ✅ Resolved | `DateUtils` | `getMappedDay()` duplicated and inconsistent |
| 8 | Design | ✅ Resolved | `ClassroomRepository` | Own ExecutorService, never shut down (thread leak) |
| 9 | Design | ➖ Obsolete | ~~`TempReadingStorage`~~ | Javadoc references non-existent class (file deleted) |
| 10 | Design | ✅ Resolved | `MockDataGenerator` | Debug utility compiled into production build |
| 11 | Design | ✅ Resolved | `Attendance`, `LectureAdapter`, `SubjectViewModel` | Incomplete migration to `AttendanceStatus` enum |

### How the remaining items were resolved

- **#8** — `ClassroomRepository` now routes all DB work through the shared `AppDatabase.databaseWriteExecutor` (matching every other repository). The per-instance single-thread executor and its leak are gone.
- **#10** — Real `MockDataGenerator` moved to `src/debug/java/...`; a no-op stub with the same public API lives in `src/release/java/...`. The 3-months-fake-data logic can no longer compile into or run in a release build, while `SettingsFragment`'s dev button still compiles for both variants.
- **#11** — `LectureAdapter` now maps status through `AttendanceStatus.fromInt()` (switch on the enum) and uses `AttendanceStatus.*.value` for the action buttons; `SubjectViewModel` uses `getAttendanceStatus() == AttendanceStatus.PRESENT`; the deprecated `PRESENT`/`ABSENT` constants were deleted from `Attendance`.

### Dead code removed

- `AttendanceNotificationHelper.triggerFallbackNotification(...)` and its `CHANNEL_FALLBACK` (`attendance_fallback`) constant — only caller was the deleted `LocationReadingWorker`.
- `model/Lecture.java` — legacy model with no remaining references.
- `androidx.concurrent:concurrent-futures` (both `1.1.0` and `1.2.0` entries) — was only needed for `CallbackToFutureAdapter` in the deleted `LocationReadingWorker`.
