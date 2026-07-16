# Nexus Personal Agent — Technical Specification

This document captures the **target architecture** requested for Nexus Personal Agent.

> Scope: Android personal automation agent used exclusively by the device owner.  
> Constraint: only official Android APIs and manually granted permissions.  
> No root, no MDM, no hidden bypasses, no unauthorized monitoring.

## Objective

Build an Android personal agent for maximum owner-authorized automation possible without root and without MDM.

The agent should:

- observe the screen;
- understand context using Accessibility, OCR, Vision, notifications, and local history;
- decide actions through rules or AI instructions;
- execute only actions permitted by Android APIs and user-granted permissions;
- stay modular and extensible so new automation modules can be added without changing the core architecture.

## Core Principles

- Modular architecture.
- Every function can be enabled/disabled through Remote Config.
- No behavior hardcoded into the core engine.
- All operations are asynchronous.
- Local database with persistent queues.
- Automatic retries.
- Complete logging.
- Clean REST APIs.
- Design ready for new modules.

## Target Modules

### 1. Device Manager

Responsibilities:

- device identification;
- heartbeat;
- status;
- health check;
- inventory.

Android APIs:

- `Build`
- `Build.VERSION`
- `Settings.Secure`
- `ActivityManager`
- `PackageManager`
- `BatteryManager`
- `ConnectivityManager`
- `StorageManager`
- `StatFs`

### 2. Permission Manager

Manages:

- notification permission;
- accessibility;
- notification listener;
- media;
- battery optimization;
- overlay;
- background activity;
- autostart/vendor settings where Android exposes official settings screens.

Android APIs:

- `Settings`
- `PowerManager`
- `NotificationManager`
- `PackageManager`
- `Environment`

### 3. Accessibility Engine

The core automation engine using:

- `AccessibilityService`
- `AccessibilityNodeInfo`
- `AccessibilityEvent`
- `GestureDescription`
- `dispatchGesture()`
- `performAction()`
- `findAccessibilityNodeInfosByText()`
- `findAccessibilityNodeInfosByViewId()`
- `getRootInActiveWindow()`

Required functions:

- click;
- long click;
- swipe;
- scroll;
- type text;
- copy text;
- find button;
- open menu;
- detect screen;
- wait element;
- wait text.

### 4. Vision Engine

Used when Accessibility nodes are unavailable or insufficient.

Android APIs:

- `MediaProjection`
- `ImageReader`
- `Bitmap`
- `Canvas`

AI analysis:

- OCR;
- image detection;
- icon detection;
- screen classification;
- UI detection.

The agent must support operation even when Accessibility elements are unavailable, but only after user grants screen-capture permission where required by Android.

### 5. OCR

Possible libraries:

- ML Kit Text Recognition;
- Tesseract.

Required output:

```json
{
  "text": "example",
  "coordinates": { "x": 0, "y": 0 },
  "boundingBox": { "left": 0, "top": 0, "right": 100, "bottom": 20 },
  "confidence": 0.98
}
```

### 6. Notification Engine

Android API:

- `NotificationListenerService`

Read fields:

- `Notification.EXTRA_TITLE`
- `Notification.EXTRA_TEXT`
- `MessagingStyle`
- `extras`

Supported target apps:

- WhatsApp;
- Telegram;
- Gmail;
- SMS;
- Messenger.

### 7. Automation Engine

Workflow model:

```text
Trigger -> Condition -> Action -> Next
```

Triggers:

- notification;
- timer;
- boot;
- Wi-Fi;
- Bluetooth;
- battery;
- charger;
- app opened;
- text found;
- image found;
- OCR result;
- server command.

Actions:

- click;
- type;
- swipe;
- scroll;
- wait;
- launch app;
- close app where Android permits;
- share;
- copy;
- paste;
- back;
- home;
- recent;
- notification interaction;
- screenshot with user-granted MediaProjection;
- OCR;
- vision;
- API request.

### 8. Scheduler

Android APIs/libraries:

- `WorkManager`
- `AlarmManager`

Required support:

- periodic tasks;
- one-time tasks;
- retry;
- priority.

### 9. Screen Analyzer

The agent must classify screens such as:

- login screen;
- home;
- chat;
- popup;
- dialog;
- error;
- loading;
- form.

Target output:

```json
{
  "screen": "login",
  "buttons": [],
  "fields": [],
  "texts": [],
  "images": []
}
```

### 10. App Controller

Android APIs:

- `PackageManager`
- `UsageStatsManager`
- `Intent`
- `ActivityManager`

Required functions:

- open app;
- verify app installed;
- start activity;
- check foreground app where permitted;
- open settings screens.

### 11. File Manager

Android APIs:

- `MediaStore`
- `DocumentFile`
- Storage Access Framework (`SAF`)

Required functions:

- search file;
- copy;
- move;
- delete when Android grants permission;
- upload;
- download.

### 12. Network Manager

Libraries:

- Retrofit;
- OkHttp;
- WebSocket.

Required support:

- REST;
- WebSocket;
- upload;
- download;
- retry;
- heartbeat.

### 13. Local Database

Target database: Room.

Required tables:

- `device`
- `jobs`
- `automation`
- `notifications`
- `screens`
- `ocr`
- `vision`
- `logs`
- `permissions`
- `config`
- `commands`

### 14. AI Engine

Inputs:

- screenshot;
- OCR;
- Accessibility tree;
- notification;
- history;
- context.

Outputs:

- click;
- swipe;
- type;
- wait;
- open;
- next action.

Recommended output envelope:

```json
{
  "action": "click",
  "target": { "text": "Continue", "x": 120, "y": 500 },
  "confidence": 0.91,
  "reason": "The current screen is a login form and the Continue button is visible.",
  "next": "wait_element"
}
```

### 15. Remote Config

Target JSON example:

```json
{
  "vision": true,
  "ocr": true,
  "automation": true,
  "notifications": true,
  "heartbeat": 15,
  "scheduler": 5
}
```

All module activation, intervals, limits, and risky/sensitive capabilities should be controlled by Remote Config.

### 16. API Server

Target API surface:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/device/register` | Register device |
| `POST` | `/heartbeat` | Heartbeat/status |
| `POST` | `/automation/run` | Run automation workflow |
| `POST` | `/vision/analyze` | Analyze screenshot/image |
| `POST` | `/ocr` | OCR analysis |
| `POST` | `/notification` | Upload notification event |
| `POST` | `/logs` | Upload logs |
| `POST` | `/upload` | Upload file |
| `GET` | `/commands` | Fetch commands |
| `POST` | `/ack` | Acknowledge command |
| `GET` | `/config` | Fetch remote config |

## Final Behavior Goal

Nexus Personal Agent should behave like an intelligent personal assistant:

1. observe screen and notifications;
2. understand current app/screen/context;
3. choose next action through rules or AI;
4. execute actions through official Android APIs;
5. respect manually granted permissions;
6. log decisions and outcomes;
7. retry safely;
8. stay extensible through modules, Remote Config, and clean APIs.
