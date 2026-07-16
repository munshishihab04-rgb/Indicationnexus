# Server Documentation

## Runtime

The backend is a Node.js Express 5 server implemented in `server/index.js`.

| Item | Value |
|---|---|
| Main file | `server/index.js` |
| Default port | `3000` |
| Framework | Express 5.2.1 |
| Upload middleware | Multer 2.0.2 |
| Image processing | Sharp 0.34.5 |
| WebSocket | ws 8.18.3 |
| ID generation | uuid 11.1.0 |

## Environment Variables

| Variable | Purpose | Default |
|---|---|---|
| `PORT` | Server listen port | `3000` |
| `NEXUS_ROOT` | Runtime root | `/opt/data/nexus-local` |
| `NEXUS_DATA_DIR` | Data directory | `$NEXUS_ROOT/data` |
| `NEXUS_DASHBOARD_DIR` | Dashboard static directory | `$NEXUS_ROOT/dashboard` |
| `NEXUS_TOKEN` | API token | Built-in fallback in code |
| `GEMINI_API_KEY` | Optional AI chat key | Empty/unconfigured |

## Middleware

- CORS configured with `origin: false`.
- Request logging for non-dashboard/non-thumbnail paths.
- JSON body limit: 50 MB.
- URL-encoded body limit: 50 MB.
- Static dashboard mount: `/dashboard`.
- Error handler returns JSON with `ok:false`.
- SPA fallback route: `GET /{*path}`.

## Authentication

`checkAuth()` reads token from:

- `req.headers['x-token']`
- `req.query.token`

It compares against `TOKEN` with `crypto.timingSafeEqual` after matching Buffer length.

## Helpers

| Helper | Purpose |
|---|---|
| `readJSON(file, def)` | Read JSON with fallback default |
| `writeJSON(file, data)` | Atomic JSON write with temp file + rename |
| `safeDeviceId(value)` | Allows only `A-Za-z0-9._-`, max 128 chars |
| `safeFilename(value)` | Basename + character sanitization, max 240 chars |
| `bounded(items, max)` | Caps arrays to `MAX_RECORDS` |
| `deviceFile(sub, id, ext)` | Maps per-device JSON storage path |
| `broadcast(event, data)` | Sends WebSocket message to live clients |

## Upload Handling

### Media

- Destination: `data/media/<deviceId>/`
- Filename: sanitized original name.
- Limit: 200 MB.
- Metadata: `data/media/<deviceId>/meta.json`.

### Audio

- Destination: `data/audio/<deviceId>/`
- Filename: timestamp prefix + sanitized original name.
- Limit: 100 MB.
- Metadata: `data/audio/<deviceId>/meta.json`.

### Screenshots

- Destination: `data/screenshots/<deviceId>/`
- Filename: timestamp prefix + sanitized original name.
- Limit: 50 MB.
- Metadata: `data/screenshots/<deviceId>/meta.json`.

## WebSocket Server

The server creates a WebSocket server on the same HTTP server.

Authentication:

```text
?token=<NEXUS_TOKEN>
```

State:

- `wsClients`: all connected clients.
- `deviceSockets`: map of `deviceId -> ws` for devices that register.

Messages:

- `{ "type": "register", "deviceId": "..." }` registers a device socket.
- `{ "type": "ping" }` receives `{ "type": "pong" }`.

Connection cleanup removes clients and device socket mapping.

## Command Queue

Command storage path:

```text
data/commands/<deviceId>.json
```

ACK storage path:

```text
data/command-acks/<deviceId>.json
```

Command flow:

1. Dashboard/API queues command through `POST /api/commands/:deviceId`.
2. Server stores command with UUID and timestamp.
3. If device has WebSocket registration, server pushes command immediately.
4. Android also receives commands in `/api/ping` response.
5. Android ACKs command by ID.
6. Server removes command and appends ACK record.

## Statistics

`GET /api/stats` reads all devices and per-device JSON/meta files to compute:

- Total devices.
- Media count.
- Event count.
- Call count.
- SMS count.
- Contacts count.
- Apps count.
- Screenshots count.
- Audio count.
- Per-device online state, battery, network, lastSeen.

## ZIP Jobs

The server stores ZIP jobs in memory in `zipJobs`.

Flow:

1. `POST /api/media/:deviceId/zip/prepare` starts `zip -r` over media directory.
2. `GET /api/media/:deviceId/zip/status?jobId=...` checks status.
3. `GET /api/media/:deviceId/zip/download?jobId=...` downloads zip when done.

Jobs are deleted from memory after one hour.

## AI Chat

`POST /api/ai/chat` builds a context summary from stored device data and optionally calls Gemini Flash if `GEMINI_API_KEY` is configured.

If no API key exists, the endpoint returns a fallback response showing context availability rather than failing.
