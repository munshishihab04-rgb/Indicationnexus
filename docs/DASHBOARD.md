# Dashboard Documentation

## Location

Dashboard files are stored under:

```text
server/dashboard/
```

The server serves them at:

```text
/dashboard
```

The Express fallback route serves `index.html` for unmatched paths if the file exists.

## Dashboard Assets

| File | Purpose |
|---|---|
| `index.html` | Main dashboard entry |
| `nexus-gps.html` | GPS/location dashboard page |
| `nexus-gps.css` | GPS page styles |
| `nexus-gps.js` | GPS page logic |
| `nexus-sms-calls.html` | SMS/call log dashboard page |
| `nexus-sms-calls.css` | SMS/call page styles |
| `nexus-sms-calls.js` | SMS/call page logic |
| `nexus-sms-preview.html` | SMS preview page |

## Backend APIs Used by Dashboard

Expected dashboard API usage includes:

- `/api/devices`
- `/api/stats`
- `/api/media/:deviceId`
- `/api/thumb/:deviceId/:filename`
- `/api/events`
- `/api/location/:deviceId`
- `/api/sms/:deviceId`
- `/api/calllog/:deviceId`
- `/api/contacts/:deviceId`
- `/api/apps/:deviceId`
- `/api/status/:deviceId`
- `/api/commands/:deviceId`
- `/api/media/:deviceId/zip/*`

## WebSocket Live Updates

Dashboard clients can connect to the WebSocket server with the API token and receive broadcast messages:

```json
{
  "event": "device_ping",
  "data": {},
  "ts": 1234567890
}
```

Use broadcast events to refresh UI sections without full reload.

## UI Responsibilities

A safe dashboard should:

- Never expose or print the API token.
- Avoid fetching huge unpaginated arrays repeatedly.
- Show online/offline status using server-provided `lastSeen`/`online`.
- Clearly label sensitive streams such as SMS, calls, notifications, location, clipboard, and accessibility text.
- Provide explicit user-facing controls for command dispatch.
- Show command ACK status rather than assuming dispatch equals completion.

## Known Implementation Notes

- Server `GET /api/events` currently accepts `limit` but not full `offset` pagination in the analyzed code.
- Server media list endpoint supports `page` and `limit`.
- The command inspection endpoint is read-only and does not consume commands.
