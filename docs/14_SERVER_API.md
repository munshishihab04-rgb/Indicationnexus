# 14 — Server API Specification

## API Principles

- Version all APIs under `/v1`.
- Use JSON envelopes.
- Use per-device API keys after enrollment.
- Validate all payloads against schemas.
- Return stable error codes.
- Keep command delivery idempotent.
- Require ACK for commands.

## Auth

Recommended headers:

```http
Authorization: Bearer <device-api-key>
X-Device-Id: <device-id>
X-Request-Id: <uuid>
```

Enrollment can use a short-lived setup token.

## Standard Response

```json
{
  "ok": true,
  "data": {},
  "meta": {
    "requestId": "uuid",
    "serverTime": 1730000000000
  }
}
```

Error response:

```json
{
  "ok": false,
  "error": {
    "code": "invalid_payload",
    "message": "Payload validation failed"
  },
  "meta": {
    "requestId": "uuid"
  }
}
```

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v1/device/register` | Register/enroll device |
| `POST` | `/v1/heartbeat` | Heartbeat and health status |
| `POST` | `/v1/automation/run` | Submit automation run result or request run |
| `POST` | `/v1/vision/analyze` | Analyze image/screenshot |
| `POST` | `/v1/ocr` | OCR image/screenshot |
| `POST` | `/v1/notification` | Upload normalized notification |
| `POST` | `/v1/logs` | Upload logs |
| `POST` | `/v1/upload` | Upload file |
| `GET` | `/v1/commands` | Fetch pending commands |
| `POST` | `/v1/ack` | Acknowledge command/job |
| `GET` | `/v1/config` | Fetch remote config |

## Register Device

```http
POST /v1/device/register
```

```json
{
  "setupToken": "short-lived-token",
  "device": {
    "deviceId": "uuid",
    "installId": "uuid",
    "appVersion": "1.0.0",
    "model": "Pixel",
    "sdk": 35
  }
}
```

## Heartbeat

```http
POST /v1/heartbeat
```

```json
{
  "deviceId": "uuid",
  "timestamp": 1730000000000,
  "health": {},
  "modules": [],
  "queue": {
    "jobs": 10,
    "logs": 3,
    "commands": 0
  },
  "configVersion": 42
}
```

## Commands

```http
GET /v1/commands?limit=20
```

```json
{
  "ok": true,
  "data": {
    "commands": [
      {
        "id": "uuid",
        "type": "run_workflow",
        "payload": {},
        "createdAt": 1730000000000
      }
    ]
  }
}
```

ACK:

```http
POST /v1/ack
```

```json
{
  "commandId": "uuid",
  "status": "accepted",
  "result": {},
  "timestamp": 1730000000000
}
```

## Logs

```http
POST /v1/logs
```

```json
{
  "deviceId": "uuid",
  "logs": [
    {
      "id": "uuid",
      "level": "info",
      "module": "automation_engine",
      "event": "action_completed",
      "message": "Clicked target",
      "data": {},
      "createdAt": 1730000000000
    }
  ]
}
```

## WebSocket

Path:

```text
/v1/ws?deviceId=<id>
```

Events:

- `command.created`
- `config.updated`
- `workflow.updated`
- `device.ping`
- `job.updated`
- `log.created`
