# Android App Documentation

## App Identity

| Item | Value |
|---|---|
| Package | `com.nexus.app` |
| App label | `Nexus Sync` |
| minSdk | 26 |
| targetSdk | 34 |
| compileSdk | 34 |
| versionName | `2.1` |
| versionCode | `12` |

## Manifest Components

| Component | Class | Purpose |
|---|---|---|
| Activity | `.MainActivity` | Setup UI and permission/status screen |
| Service | `.NexusService` | Foreground sync service |
| Service | `.NexusRecoveryJobService` | Scheduled recovery service |
| Service | `.NexusNotificationListener` | Notification listener |
| Service | `.NexusAccessibilityService` | Accessibility service hook |
| Receiver | `.BootReceiver` | Boot completed receiver |

## Manifest Permissions

The app declares permissions for:

- Internet/network state.
- Media read access (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, legacy `READ_EXTERNAL_STORAGE`).
- SMS, call log, contacts.
- Fine/coarse/background location.
- Audio recording and camera.
- Boot completed.
- Foreground service/data sync.
- Notifications.
- Wake lock.
- Ignore battery optimizations request.
- Phone state.

## MainActivity

`MainActivity` performs onboarding/setup:

- Shows device ID and server URL.
- Requests runtime permissions.
- Opens notification listener settings.
- Opens accessibility settings with Android restricted-settings guidance.
- Opens battery optimization exemption request.
- Opens app details page to configure background location.
- Opens document tree picker for SD/external folder authorization.
- Starts `NexusService` as foreground service.
- Displays permission/status indicators.

## NexusService

`NexusService` is the core runtime service.

### Startup

- Initializes `Handler` on main looper.
- Gets device ID from `NexusConfig`.
- Creates `NexusAPI` client.
- Schedules recovery.
- Creates notification channel.
- Starts itself in foreground with persistent notification.

### Periodic Tasks

- Heartbeat: `api.ping(cmds -> handleCommands(cmds))`.
- Media/PIM sync: `runSync(false)` through single-thread executor.
- GPS sync: `LocationSync.syncOnce()`.

### Sync Locking

`AtomicBoolean syncRunning` prevents overlapping sync executions.

### Command Execution

Recognized commands:

| Command | Android behavior |
|---|---|
| `sync_media` | Runs full media sync |
| `get_location` | Sends one location point |
| `get_apps` | Sends installed apps |
| `get_status` | Sends device diagnostics |
| `take_screenshot` | Returns false; user consent required |
| `record_audio` | Returns false; background mic restrictions |
| `take_photo` | Returns false; foreground/user flow required |
| `get_clipboard` | Returns false; background clipboard restrictions |

Each command with ID is ACKed through `NexusAPI.acknowledgeCommand()`.

## NexusAPI

`NexusAPI` wraps OkHttp calls.

### HTTP Client

- Connect timeout: 15 seconds.
- Write timeout: 60 seconds.
- Read timeout: 30 seconds.

### Functions

- `ping()` sends heartbeat/status and receives commands.
- `pollCommands()` exists but service no longer uses separate duplicate polling.
- `acknowledgeCommand()` ACKs command IDs.
- `sendDeviceStatus()` sends diagnostic JSON.
- `sendEventsNow()` / `sendEvents()` upload notification events.
- `sendLocation()` uploads GPS point.
- `sendCallLog()` uploads call records.
- `sendSMS()` uploads SMS records.
- `sendContacts()` uploads contacts.
- `sendKeylog()` uploads accessibility text events.
- `sendClipboard()` uploads clipboard record.
- `sendBrowserHistory()` uploads browser history records.
- `sendApps()` uploads installed app list.
- `uploadMedia()` uploads File or `content://` media URI.
- `uploadAudio()` uploads audio file.
- `uploadScreenshot()` uploads screenshot file.

## GallerySync

`GallerySync` handles MediaStore image/video backup.

- Maintains state in `nexus_media_state_v3` SharedPreferences.
- Tracks `last_image`, `last_video`, `last_full` cursors.
- Runs retry queue before scanning.
- Performs incremental MediaStore query by `DATE_MODIFIED > cursor`.
- Performs daily full reconciliation when policy says so.
- `syncFullNow()` forces image+video full scan.
- Upload failures are added to bounded retry queue.
- Successful uploads remove corresponding retry items.

## SMS and Call Sync

`SMSSync` and `CallLogSync` use incremental cursors stored through `NexusConfig`.

Reliability rule: cursors are advanced only after the server confirms successful upload.

- SMS batch limit: 500.
- Call log batch limit: 500.
- SMS control characters are sanitized before sending.

## Notification Listener

`NexusNotificationListener` captures notification events after user enables notification listener access.

Important behavior:

- Ignores own app notifications.
- Ignores non-clearable ongoing notifications.
- Extracts WhatsApp/WhatsApp Business messages using MessagingStyle when available.
- Falls back to notification text lines or big text.
- Extracts generic notification title/body.
- Generates deterministic `eventId` for deduplication.
- Uses `NotificationEventQueue` to persist pending events and mark sent events.

## DeviceStatusCollector

Collects diagnostic metadata only, not user content:

- Device model/version.
- Battery/thermal/power state.
- Storage/memory.
- Network type/metered state.
- Permission/service states.
- App version.

## Recovery Components

- `BootReceiver`: starts service after boot.
- `RecoveryScheduler`: schedules periodic job.
- `NexusRecoveryJobService`: invokes service recovery from JobScheduler.
- `ReliabilityPolicy`: policy constants covered by tests.
