# Nexus Overview

## Purpose

Nexus is an Android-to-server synchronization platform. The Android app gathers data available through user-granted Android permissions and sends it to a Node.js backend. The backend persists the data, exposes REST APIs, broadcasts live events to dashboards through WebSockets, and provides operational tools such as media ZIP export and statistics.

## Main Capabilities

- Device registration through heartbeat/ping.
- Device health and status telemetry.
- Command queue with explicit ACK protocol.
- Media backup using MediaStore/scoped storage.
- Optional external folder/SD-card sync through Storage Access Framework.
- GPS/location sync.
- SMS sync.
- Call log sync.
- Contacts sync.
- Notification event sync, including richer WhatsApp message extraction where Android exposes `MessagingStyle` data.
- Installed app list sync.
- File upload handling for media, audio, and screenshots.
- Thumbnail generation for images using Sharp.
- Dashboard static frontend.
- WebSocket live event broadcasting.
- Statistics endpoint for device totals.
- Media ZIP preparation/download jobs.
- Optional AI chat endpoint using Gemini API context over stored Nexus data.

## Component Map

```text
Android App
  ├─ MainActivity: permission/setup UI
  ├─ NexusService: foreground sync scheduler and command executor
  ├─ NexusAPI: HTTP client for backend APIs
  ├─ GallerySync / ExternalTreeSync: media sync
  ├─ SMSSync / CallLogSync / ContactsSync: PIM data sync
  ├─ LocationSync: GPS sync
  ├─ NexusNotificationListener: notification event capture
  ├─ DeviceStatusCollector: non-content diagnostics
  └─ RecoveryScheduler / JobService / BootReceiver: service resilience

Node.js Server
  ├─ Express REST API
  ├─ X-Token auth middleware
  ├─ JSON file persistence
  ├─ Multer upload handling
  ├─ Sharp thumbnail generation
  ├─ WebSocket live updates
  ├─ Command queue + ACK storage
  └─ Dashboard static hosting
```

## Data Flow

```text
1. Android app starts foreground NexusService.
2. NexusService sends /api/ping every 30 seconds.
3. Server updates devices.json and returns pending commands.
4. Android app executes whitelisted commands and ACKs each command.
5. Periodic sync jobs send media, GPS, SMS, calls, contacts, notifications, apps, and status.
6. Server stores JSON/files under NEXUS_DATA_DIR.
7. Dashboard reads REST endpoints and receives WebSocket broadcast events.
```

## Implementation Notes

- Backend code lives in `server/index.js`.
- Android source lives in `android/app/src/main/java/com/nexus/app/`.
- Dashboard static files live in `server/dashboard/`.
- Existing operational notes live in `docs/PROJECT_MEMORY.md` in the source project.
