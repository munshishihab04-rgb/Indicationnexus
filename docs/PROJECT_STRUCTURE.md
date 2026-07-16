# Project Structure

Analyzed source layout:

```text
nexus-monorepo-github/
├── README.md
├── android/
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradle/wrapper/
│   └── app/
│       ├── build.gradle
│       └── src/
│           ├── main/
│           │   ├── AndroidManifest.xml
│           │   ├── java/com/nexus/app/
│           │   └── res/
│           └── test/java/com/nexus/app/
├── server/
│   ├── index.js
│   ├── package.json
│   ├── test-hardening.js
│   ├── test-reliability.js
│   └── dashboard/
│       ├── index.html
│       ├── nexus-gps.html
│       ├── nexus-gps.css
│       ├── nexus-gps.js
│       ├── nexus-sms-calls.html
│       ├── nexus-sms-calls.css
│       ├── nexus-sms-calls.js
│       └── nexus-sms-preview.html
└── docs/
    └── PROJECT_MEMORY.md
```

## Android Important Files

| File | Responsibility |
|---|---|
| `MainActivity.java` | Setup UI, permission requests, opens Android settings pages, starts service |
| `NexusService.java` | Foreground service, heartbeat, periodic sync, command execution |
| `NexusAPI.java` | HTTP client wrapper for all backend communication |
| `NexusConfig.java` | Device ID, server URL/token, sync cursors/config |
| `GallerySync.java` | MediaStore image/video sync, retry queue, full reconciliation |
| `ExternalTreeSync.java` | Sync authorized external tree/SD-card folder |
| `SMSSync.java` | Incremental SMS sync using Android SMS provider |
| `CallLogSync.java` | Incremental call log sync using Android CallLog provider |
| `ContactsSync.java` | Contacts sync |
| `LocationSync.java` | GPS/location sync |
| `NexusNotificationListener.java` | Notification listener, WhatsApp/generic event extraction, local queue flushing |
| `NotificationEventQueue.java` | Persistent dedup/queue for notification events |
| `DeviceStatusCollector.java` | Device health diagnostics: battery, thermal, storage, memory, network, permission state |
| `NexusAccessibilityService.java` | Accessibility service hook; current implementation records sanitized event text through `sendKeylog` |
| `BootReceiver.java` | Restarts service on boot |
| `RecoveryScheduler.java` | Schedules periodic recovery job |
| `NexusRecoveryJobService.java` | JobScheduler recovery service |
| `CameraHelper.java` | Camera photo helper |
| `AudioRecorder.java` | Audio recording helper |
| `ScreenshotHelper.java` | Screenshot helper |
| `AppListSync.java` | Installed application list sync |

## Server Important Files

| File | Responsibility |
|---|---|
| `server/index.js` | Full Express backend: auth, APIs, persistence, uploads, WebSockets, dashboard fallback |
| `server/package.json` | Node metadata and dependencies |
| `server/test-hardening.js` | Server hardening tests |
| `server/test-reliability.js` | Reliability tests |
| `server/dashboard/index.html` | Main dashboard UI |
| `server/dashboard/nexus-gps.*` | GPS dashboard pages/assets |
| `server/dashboard/nexus-sms-calls.*` | SMS/call dashboard pages/assets |
| `server/dashboard/nexus-sms-preview.html` | SMS preview dashboard page |

## Build/Runtime Metadata

Android Gradle configuration:

- `compileSdk`: 34
- `minSdk`: 26
- `targetSdk`: 34
- `applicationId`: `com.nexus.app`
- `versionCode`: 12
- `versionName`: `2.1`
- Dependencies: AndroidX Core, OkHttp, Gson, JUnit.

Server dependencies:

- `express` 5.2.1
- `multer` 2.0.2
- `sharp` 0.34.5
- `uuid` 11.1.0
- `ws` 8.18.3
- `cors` 2.8.5
