# Architecture

## System Architecture

```text
+-------------------------------+        HTTPS/HTTP + X-Token        +-------------------------------+
| Android App                   |  -------------------------------->  | Node.js Express Server         |
| package: com.nexus.app        |                                    | server/index.js                |
|                               |  <--------------------------------  | JSON responses + commands      |
| - Foreground NexusService     |                                    |                               |
| - Permission/setup UI         |                                    | - Auth middleware              |
| - Sync modules                |                                    | - JSON/file storage            |
| - Notification listener       |                                    | - Upload handlers              |
| - Recovery jobs/boot receiver |                                    | - WebSocket broadcaster        |
+---------------+---------------+                                    +---------------+---------------+
                |                                                                    |
                | Android platform APIs                                               | Static files + REST + WS
                v                                                                    v
+-------------------------------+                                    +-------------------------------+
| Android OS Providers/Services |                                    | Dashboard Browser              |
| MediaStore, SMS, CallLog,     |                                    | /dashboard + API endpoints     |
| Contacts, Location, Settings, |                                    |                               |
| NotificationListener, SAF     |                                    |                               |
+-------------------------------+                                    +-------------------------------+
```

## Android Layer

The Android application is centered on `NexusService`, a foreground service. It starts periodic jobs through a `Handler` and uses a single-threaded `ExecutorService` to avoid overlapping syncs.

### Scheduling

- Ping/heartbeat: every `NexusConfig.PING_INTERVAL_MS`.
- Media sync: delayed first run, then every `MediaSyncPolicy.INCREMENTAL_INTERVAL_MS`.
- GPS sync: every `RuntimeSyncPolicy.GPS_INTERVAL_MS`.
- Recovery: `RecoveryScheduler` and `NexusRecoveryJobService` keep the service alive using Android-supported scheduling.
- Boot recovery: `BootReceiver` starts service after boot.

### Command Delivery

The app does **not** use duplicate HTTP polling in the current service logic. Commands are returned in the `/api/ping` response. Each command has an ID and is acknowledged through:

```text
POST /api/commands/:deviceId/:commandId/ack
```

The server keeps commands until ACK for protocol v2 clients.

### Data Collection Boundaries

Data comes from explicit Android APIs and permissions:

- MediaStore for media.
- Storage Access Framework for user-selected external trees.
- SMS provider after `READ_SMS` permission.
- CallLog provider after `READ_CALL_LOG` permission.
- Contacts provider after `READ_CONTACTS` permission.
- Location APIs after location permissions.
- NotificationListenerService after user enables notification listener access.
- AccessibilityService after user enables accessibility service.

## Server Layer

The backend is a single Express 5 app in `server/index.js`.

### Core Functions

- `checkAuth(req,res,next)` validates `X-Token` or query `token` using timing-safe comparison.
- `readJSON(file, def)` and `writeJSON(file, data)` implement JSON persistence with atomic temp-file rename.
- `safeDeviceId()` and `safeFilename()` constrain path inputs.
- `bounded(items)` caps stored collections to `MAX_RECORDS`.
- `deviceFile(sub,id)` maps per-device JSON files.
- `broadcast(event,data)` sends updates to WebSocket clients.

### Persistence

The server stores state in directories under `DATA_DIR`:

```text
data/
├── devices.json
├── commands/<deviceId>.json
├── command-acks/<deviceId>.json
├── events/<deviceId>.json
├── locations/<deviceId>.json
├── sms/<deviceId>.json
├── calllog/<deviceId>.json
├── contacts/<deviceId>.json
├── apps/<deviceId>.json
├── browser/<deviceId>.json
├── keylog/<deviceId>.json
├── clipboard/<deviceId>.json
├── status/<deviceId>.json
├── media/<deviceId>/
│   └── meta.json
├── thumbs/<deviceId>/
├── audio/<deviceId>/
│   └── meta.json
├── screenshots/<deviceId>/
│   └── meta.json
└── zips/
```

## Real-time Updates

The backend creates a WebSocket server attached to the same HTTP server. Clients authenticate via `?token=`. It supports:

- Dashboard clients receiving live broadcasts.
- Device WebSocket registration through message `{ "type": "register", "deviceId": "..." }`.
- Direct command push to registered device sockets.
- Ping/pong liveness checks every 25 seconds.

## Dashboard Layer

The dashboard is served as static assets from `DASHBOARD_DIR` mounted at `/dashboard`. A fallback route serves `index.html` for SPA-like navigation.

## Reliability Architecture

Reliability mechanisms present in the implementation:

- Foreground service with visible notification.
- Boot receiver.
- JobScheduler recovery.
- Command ACK protocol.
- Atomic JSON writes on server.
- Media upload retry queue on Android.
- Cursor advancement only after successful server confirmation for SMS/calls.
- Notification event queue with dedup IDs and retry flushing.
- Bounded server collections to prevent unbounded JSON growth.
