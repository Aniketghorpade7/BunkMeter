# BunkMeter

BunkMeter is a native Android application designed to automate and track student attendance. It features an automated location-based verification system, offline-first data storage, and comprehensive background processing to ensure attendance is recorded seamlessly.

## 🛠 Tech Stack & Libraries
* **Language:** Java 11
* **Minimum SDK:** API 26 (Android 8.0)
* **Target SDK:** API 36
* **Database:** Room Persistence Library (v2.6.1)
* **Background Tasks:** WorkManager (v2.9.0) paired with Concurrent Futures
* **Location Services:** Google Play Services Location (v21.1.0)
* **Data Visualization:** MPAndroidChart (v3.1.0) for plotting attendance statistics
* **Data Export:** Apache POI (v5.2.3) for generating `.xlsx` Excel sheets

## 🏗 Architecture & Core Components

### Database (Room)
The local SQLite database is managed via Room (`AppDatabase.java`). It utilizes a fixed thread pool of 4 threads to ensure safe background database write operations.
The core relational entities include:
* `Attendance`: Stores granular records including timestamps, attendance status, the associated classroom ID, and a flag indicating if the location was physically verified.
* `Subject`, `Classroom`, and `Timetable`.

### Location & Geofencing
The `LocationHelper` class functions as a thin wrapper around the `FusedLocationProviderClient` to fetch high-accuracy location fixes via an asynchronous callback API.
* **Verification:** Evaluates if a user's current coordinates fall within a specific geofence radius of their classroom.
* **Spoofing Protection:** Includes security checks to detect mock or fake GPS providers.

### Background Processing
To guarantee location checks and attendance updates trigger at the right time, the application offloads tasks using several Android mechanisms:
* Integration with `WorkManager` (e.g., `LocationReadingWorker`), which pairs with `CallbackToFutureAdapter` to prevent shared thread-pool starvation when awaiting GPS resolutions.
* Foreground services (`AttendanceForegroundService`) designated for short service data synchronization.
* Broadcast receivers (`AttendanceActionReceiver`) to listen for system triggers.

## Permissions Overview
BunkMeter requests specific system permissions to deliver its feature set:
* **Location Systems:** `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, and `ACCESS_BACKGROUND_LOCATION` are strictly required to verify a user's classroom presence automatically in the background.
* **Power Management:** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` alongside Foreground Service permissions guarantees the OS does not terminate location checks during scheduled lecture hours.
* **Storage:** `WRITE_EXTERNAL_STORAGE` is utilized specifically to write out the Apache POI Excel (`.xlsx`) exports.
* **Notifications:** `POST_NOTIFICATIONS` is enforced for devices running Android 13+ (API 33) to allow attendance alerts and persistent foreground state icons.
* **Camera:** An optional permission requested via `android.hardware.camera`.

## Getting Started (Installation)

You can easily install BunkMeter directly on your Android device without needing to build it from source.

1. Navigate to the **[Releases](../../releases)** tab of this repository.
2. Download the latest `BunkMeter.apk` file from the **Assets** section.
3. Open the downloaded `.apk` file on your Android device (ensure you have allowed "Install from unknown sources" in your device settings).
4. Follow the on-screen prompts to install the application.
5. Launch **BunkMeter**, grant the necessary permissions (especially Location and Battery Optimization exclusions), and set up your timetable!
