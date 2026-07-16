# Implemented Features

This file lists features found in the analyzed implementation.

## Device Lifecycle

- Stable generated device ID through `NexusConfig.getDeviceId()`.
- Foreground Android service with persistent notification.
- Automatic service start from `MainActivity`.
- Boot receiver for startup after reboot.
- JobScheduler recovery path.
- Heartbeat/ping every configured interval.
- Online/offline calculation on server using 5-minute window.

## Device Status and Diagnostics

Implemented by `DeviceStatusCollector` and `/api/status/:deviceId`:

- Manufacturer/model/Android version/SDK.
- Uptime.
- Battery percentage, temperature, charging state.
- Power save mode.
- Thermal status on supported Android versions.
- Battery optimization exemption state.
- Internal storage total/free bytes.
- Memory total/available/low-memory flag.
- Network transport and metered state.
- Accessibility enabled state.
- Notification listener enabled state.
- Notification permission state.
- External tree authorization state.
- App version and version code.

## Command System

Server supports command creation, read-only inspection, and ACK tracking:

- `sync_media`
- `get_location`
- `get_apps`
- `get_status`

Android command executor currently recognizes additional command names but intentionally returns `false` for capabilities that require user foreground consent or are restricted by recent Android versions:

- `take_screenshot`: returns false; MediaProjection consent required.
- `record_audio`: returns false; background microphone restrictions.
- `take_photo`: returns false; foreground/user flow required.
- `get_clipboard`: returns false; Android background clipboard limits.

## Media Sync

Implemented by `GallerySync`, `ExternalTreeSync`, and server `/api/media/:deviceId` routes.

- MediaStore image sync.
- MediaStore video sync.
- Incremental scans using `DATE_MODIFIED` cursor.
- Daily full reconciliation controlled by `MediaSyncPolicy`.
- Manual full sync through `sync_media` command.
- Retry queue for failed uploads.
- Bounded retry queue size.
- Upload from `content://` URI using OkHttp streaming `RequestBody`.
- Server-side multipart upload with Multer.
- Server-side metadata in `media/<deviceId>/meta.json`.
- Dedup by original filename and size.
- Pagination/type filtering in media GET endpoint.
- Image thumbnail cache using Sharp.
- Video thumbnail requests return unsupported status rather than crashing.
- Media ZIP job preparation/status/download.

## External Tree / SD Sync

Implemented by `ExternalTreeSync`:

- Opens Android document tree picker from `MainActivity`.
- Persists read URI permission.
- Saves selected tree URI.
- Walks external tree and syncs supported media MIME types.
- Reports external tree authorization in device status.

## Notifications and Events

Implemented by `NexusNotificationListener` and `/api/events`:

- NotificationListenerService integration.
- Filters out app’s own notifications and non-clearable ongoing notifications.
- WhatsApp and WhatsApp Business recognition.
- Rich WhatsApp extraction via `Notification.MessagingStyle.Message` when available.
- Fallback extraction from notification text lines / big text / normal text.
- Generic app notification extraction.
- Sanitization of control characters.
- Event ID generation for deduplication.
- Persistent local event queue.
- Flush on listener connected and after receiving event.
- Server-side dedup by `eventId`.
- Server-side sorting by `ts` or `timestamp`.

## Location

- Periodic GPS sync from `NexusService`.
- One-shot location sync on `get_location` command.
- Server persistence under `locations/<deviceId>.json`.
- Location broadcast to WebSocket clients.

## SMS

Implemented by `SMSSync`:

- Incremental query from `content://sms` with `date > lastSync`.
- Batch limit of 500 records per run.
- Sanitizes SMS body control characters.
- Advances cursor only after successful server confirmation.
- Server stores and sorts messages.
- Server dedup uses message `id`/`_id`.

## Call Log

Implemented by `CallLogSync`:

- Incremental query from Android `CallLog.Calls` with `DATE > lastSync`.
- Batch limit of 500 records per run.
- Captures number, cached name, type, duration, date.
- Advances cursor only after successful server confirmation.
- Server dedup uses composite key `number_date`.
- Server stores and sorts calls.

## Contacts

- Android contacts sync module exists.
- Server receives full contact array and stores it at `contacts/<deviceId>.json`.
- Stats endpoint counts contacts per device.

## Installed Apps

- `AppListSync` module exists.
- Server receives full installed app array and stores it at `apps/<deviceId>.json`.
- Command `get_apps` triggers app list sync.

## Audio / Screenshot / Camera Helpers

The codebase contains helper classes and backend endpoints for audio and screenshot upload. The active command executor intentionally does not claim success for background screenshot/audio/camera commands because they require user-visible Android consent or foreground flow.

Server endpoints present:

- `POST /api/audio/:deviceId`
- `GET /api/audio/:deviceId`
- `GET /api/audio/:deviceId/:filename`
- `POST /api/screenshot/:deviceId`
- `GET /api/screenshot/:deviceId`
- `GET /api/screenshot/:deviceId/:filename`

## Dashboard and Realtime

- Static dashboard under `/dashboard`.
- WebSocket broadcast for device pings, events, location, media, SMS, call logs, audio, screenshots, status, and commands.
- GPS dashboard assets.
- SMS/calls dashboard assets.
- SMS preview page.

## AI Chat

Optional `/api/ai/chat` endpoint:

- Builds context from internal stored Nexus data.
- Uses `GEMINI_API_KEY` if configured.
- Returns a fallback context message when AI key is absent.
