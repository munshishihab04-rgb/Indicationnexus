# 13 — Remote Config

## Purpose

Remote Config controls all module activation, intervals, limits, workflow rules, and safety settings.

No function should be permanently hardcoded as always-on.

## Config Example

```json
{
  "version": 42,
  "modules": {
    "device_manager": { "enabled": true },
    "permission_manager": { "enabled": true },
    "accessibility_engine": { "enabled": true },
    "vision_engine": { "enabled": true },
    "ocr_engine": { "enabled": true },
    "notification_engine": { "enabled": true },
    "automation_engine": { "enabled": true }
  },
  "intervals": {
    "heartbeatSeconds": 15,
    "configSyncMinutes": 5,
    "logUploadMinutes": 5
  },
  "limits": {
    "actionsPerMinute": 30,
    "maxWorkflowSteps": 50,
    "maxQueueSize": 10000
  },
  "privacy": {
    "storeScreenshots": false,
    "uploadScreenshots": false,
    "redactPatterns": ["\\b\\d{6}\\b"]
  },
  "apps": {
    "allowlist": [],
    "denylist": ["com.android.vending"]
  }
}
```

## Config Rules

- Config is fetched from server and cached in Room.
- Each config has a monotonically increasing version.
- Invalid config is rejected and previous config remains active.
- Modules receive `applyConfig()` updates.
- Dangerous/sensitive module changes should require local user confirmation if configured.

## Endpoint

```http
GET /config?deviceId=<id>&currentVersion=<version>
```

Response:

```json
{
  "ok": true,
  "version": 42,
  "config": {}
}
```

## Local Fallback

If server is unreachable:

1. Continue using last valid config.
2. Mark config stale after configured TTL.
3. Disable optional high-risk modules if config is too stale.
4. Continue heartbeat/logging if allowed.

## Feature Flag Pattern

```kotlin
if (config.isEnabled("ocr_engine")) {
    moduleRegistry.start("ocr_engine")
} else {
    moduleRegistry.stop("ocr_engine", "disabled_by_config")
}
```
