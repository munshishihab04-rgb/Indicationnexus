# Personal Android Automation Agent

An owner-authorized, on-device automation agent for Android. Observes signals (notifications, screen content, accessibility events) and executes workflows configured remotely. No root, no MDM — standard Android APIs only.

## Quick Start

### Backend

```bash
cd backend
pip install -r requirements.txt
cp .env.example .env   # set SETUP_TOKEN
uvicorn src.main:app --host 0.0.0.0 --port 9000
# Dashboard: http://localhost:9000/
```

### Android APK

1. Set `SERVER_BASE_URL` and `SETUP_TOKEN` in `android/app/build.gradle` (or `.env`)
2. `cd android && gradle assembleDebug`
3. Install `app/build/outputs/apk/debug/app-debug.apk`
4. Open app → grant permissions when prompted → agent starts automatically

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Android App (com.personal.agent)                           │
│                                                             │
│  AgentForegroundService ──► ModuleRegistry                  │
│                                 │                           │
│         ┌───────────────────────┼───────────────────────┐   │
│         ▼           ▼           ▼           ▼           ▼   │
│   DeviceMgr  PermMgr  NotifEngine  A11yEngine  VisionEngine │
│                          │            │              │       │
│                     NotifListener  A11yService  ScreenRecord │
│                          │            │                      │
│                     ─────┴────────────┘                      │
│                          ▼                                   │
│                    AutomationEngine ◄── WorkflowRunner       │
│                          │                                   │
│                    JobExecutor  WorkflowRepository           │
│                                                              │
│  WorkManager: Heartbeat · ConfigSync · LogUpload ·           │
│               CommandPoll · Cleanup · NotificationUpload     │
│                                                              │
│  Room DB: device · jobs · automation · notifications ·       │
│           screens · ocr · vision · logs · commands ·         │
│           permissions · config                               │
└──────────────────────────────────┬──────────────────────────┘
                                   │ HTTPS + WebSocket
┌──────────────────────────────────▼──────────────────────────┐
│  FastAPI Backend (port 9000)                                 │
│                                                              │
│  REST API /v1/                                               │
│    device/register · heartbeat · config · commands · ack     │
│    logs · notifications · vision/analyze · ocr              │
│    automation/workflow · automation/run · status             │
│    accessibility/action                                      │
│                                                              │
│  WebSocket /ws/{device_id}   — real-time dashboard push      │
│  WebSocket /ws/admin          — all-device stream            │
│                                                              │
│  Dashboard /                  — single-file dark-theme SPA   │
│    Live device list · Stats · Module health · Log stream     │
│    Accessibility actions · Workflow builder · Raw commands   │
└──────────────────────────────────────────────────────────────┘
```

## Implementation Status

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Foundation: skeleton, Room DB (12 tables), workers stub, backend API | ✅ Done |
| 2 | Workers wired: heartbeat, config sync, log upload, command poll, enrollment | ✅ Done |
| 3 | Job queue (priority + exponential backoff), command dispatch, DeviceManagerModule | ✅ Done |
| 4 | Notification Engine: parser (WhatsApp/Telegram/Gmail/SMS/generic), queue, upload | ✅ Done |
| 5 | Accessibility Engine: tree builder, node finder, action executor, gesture engine | ✅ Done |
| 6 | Vision/OCR Engine: MediaProjection capture, ML Kit OCR, screen classifier | ✅ Done |
| 7 | Automation Engine: workflow DSL, trigger/condition/action, WorkflowRunner | ✅ Done |
| 8 | Dashboard + WebSocket real-time push | ✅ Done |
| 9 | Hardening: pytest suite, unit tests, ProGuard, CI, error handlers | ✅ Done |

## Modules

| Module ID | Class | Trigger |
|-----------|-------|---------|
| `device_manager` | `DeviceManagerModule` | Always enabled |
| `permission_manager` | `PermissionManager` | Always enabled |
| `notification_engine` | `NotificationEngine` | Requires notification listener access |
| `accessibility_engine` | `AccessibilityEngine` | Requires accessibility service |
| `vision_engine` | `VisionEngine` | Requires MediaProjection |
| `automation_engine` | `AutomationEngine` | Always enabled |

## Workflow DSL Example

```json
{
  "id": "wf-whatsapp-reply",
  "name": "Auto-open WhatsApp on OTP",
  "enabled": true,
  "version": 1,
  "trigger": {
    "type": "NOTIFICATION_RECEIVED",
    "packageName": "com.whatsapp",
    "bodyContains": "OTP"
  },
  "conditions": [
    { "type": "NETWORK_WIFI" },
    { "type": "BATTERY_ABOVE", "intValue": 20 }
  ],
  "steps": [
    { "id": "s1", "action": { "type": "HOME" }, "onFail": "CONTINUE" },
    { "id": "s2", "action": { "type": "LAUNCH_APP", "packageName": "com.whatsapp" }, "onFail": "ABORT" },
    { "id": "s3", "action": { "type": "WAIT_TEXT", "targetText": "Chats", "timeoutMs": 5000 }, "onFail": "ABORT" },
    { "id": "s4", "action": { "type": "OCR_TEXT" }, "onFail": "CONTINUE" },
    { "id": "s5", "action": { "type": "LOG_EVENT", "logMessage": "OCR: {{ocr_text}}" }, "onFail": "CONTINUE" }
  ],
  "createdAt": 0,
  "updatedAt": 0
}
```

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | /health | None | Server health |
| POST | /v1/device/register | Setup token | Enroll device |
| POST | /v1/heartbeat | Device | Send health snapshot |
| GET | /v1/config | Device | Fetch remote config |
| GET | /v1/commands | Device | Poll pending commands |
| POST | /v1/ack | Device | ACK command result |
| POST | /v1/logs | Device | Upload log batch |
| POST | /v1/notification | Device | Upload notification |
| POST | /v1/vision/analyze | Device | Upload vision result |
| POST | /v1/ocr | Device | Upload OCR result |
| POST | /v1/commands | Admin | Send command to device |
| POST | /v1/accessibility/action | Admin | Send accessibility action |
| POST | /v1/automation/workflow | Admin | Install workflow |
| POST | /v1/automation/run | Admin | Trigger workflow run |
| GET | /v1/automation/workflows | Admin | List workflows |
| GET | /v1/notifications | Device | Recent notifications |
| GET | /v1/status | None | Server + device list |
| WS | /ws/{device_id} | None | Real-time events stream |
| WS | /ws/admin | None | All-device event stream |

## CI

GitHub Actions (`.github/workflows/ci.yml`):
- **backend-test**: pytest on every push, in-memory SQLite
- **android-build**: `gradle assembleDebug`, uploads APK artifact
- **backend-lint**: ruff (advisory)

## Running Tests

```bash
# Backend
cd backend
PYTHONPATH=. python -m pytest tests/ -v

# Android unit tests (JVM, no emulator needed)
cd android
gradle test --no-daemon
```
