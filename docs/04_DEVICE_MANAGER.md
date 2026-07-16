# 04 — Device Manager

## Responsibilities

- Generate and persist device identity.
- Register device with backend.
- Send heartbeat.
- Collect health status.
- Collect inventory.
- Report module health.
- Track last config version.

## Android APIs

- `Build`
- `Build.VERSION`
- `Settings.Secure`
- `ActivityManager`
- `PackageManager`
- `BatteryManager`
- `ConnectivityManager`
- `StorageManager`
- `StatFs`
- `PowerManager`

## Device Identity

Preferred identity strategy:

1. Generate a random UUID on first run.
2. Store in encrypted/shared preferences or Room.
3. Do not use resettable or privacy-sensitive identifiers as primary ID.
4. Include Android ID only if necessary and disclosed.

```kotlin
interface DeviceIdentityStore {
    suspend fun getOrCreateDeviceId(): String
}
```

## Registration Payload

```json
{
  "deviceId": "uuid",
  "installId": "uuid",
  "appVersion": "1.0.0",
  "appVersionCode": 1,
  "manufacturer": "Google",
  "model": "Pixel",
  "androidVersion": "15",
  "sdk": 35,
  "capabilities": ["accessibility", "notifications", "ocr"],
  "createdAt": 1730000000000
}
```

## Heartbeat Payload

```json
{
  "deviceId": "uuid",
  "timestamp": 1730000000000,
  "battery": {
    "percent": 75,
    "charging": true,
    "temperatureC": 32.1,
    "powerSaveMode": false
  },
  "network": {
    "transport": "wifi",
    "metered": false,
    "connected": true
  },
  "storage": {
    "totalBytes": 128000000000,
    "freeBytes": 64000000000
  },
  "memory": {
    "totalBytes": 8000000000,
    "availableBytes": 3200000000,
    "lowMemory": false
  },
  "permissions": {},
  "modules": [],
  "queueDepth": 12,
  "configVersion": 42
}
```

## Health Check

Health should include:

- last heartbeat success;
- last config sync;
- pending jobs;
- failed jobs;
- module status;
- network state;
- battery state;
- database size;
- log queue size.

## Inventory

Inventory can include:

- installed apps if module enabled and disclosed;
- supported permissions;
- device capabilities;
- camera/microphone availability only as hardware metadata;
- storage roots visible through official APIs.

Do not inventory private app data.
