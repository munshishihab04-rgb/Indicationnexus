# Database and Storage Model

Nexus currently uses file-based storage, not SQL.

## Storage Root

The effective data directory is:

```text
process.env.NEXUS_DATA_DIR || path.join(NEXUS_ROOT, 'data')
```

where `NEXUS_ROOT` defaults to:

```text
/opt/data/nexus-local
```

## Atomic Writes

Server writes JSON through `writeJSON()`:

1. Creates parent directory if needed.
2. Writes JSON to a temp file.
3. Renames temp file to target path.

This reduces risk of partially written JSON files.

## Main Files and Directories

| Path | Type | Description |
|---|---|---|
| `devices.json` | Object | Device registry keyed by deviceId |
| `commands/<deviceId>.json` | Array | Pending commands |
| `command-acks/<deviceId>.json` | Array | Recent command acknowledgements |
| `events/<deviceId>.json` | Array | Notification events |
| `locations/<deviceId>.json` | Array | GPS/location history |
| `sms/<deviceId>.json` | Array | SMS records |
| `calllog/<deviceId>.json` | Array | Call records |
| `contacts/<deviceId>.json` | Array | Contact records |
| `browser/<deviceId>.json` | Array | Browser history records |
| `keylog/<deviceId>.json` | Array | Accessibility text events |
| `clipboard/<deviceId>.json` | Array | Clipboard snapshots |
| `apps/<deviceId>.json` | Array | Installed apps |
| `status/<deviceId>.json` | Object | Device diagnostic status |
| `media/<deviceId>/meta.json` | Array | Media metadata |
| `audio/<deviceId>/meta.json` | Array | Audio metadata |
| `screenshots/<deviceId>/meta.json` | Array | Screenshot metadata |
| `thumbs/<deviceId>/` | Files | Cached JPEG thumbnails |
| `zips/` | Files | Generated media ZIP archives |

## Record Limits

`MAX_RECORDS` is set to `10000`. The server uses `bounded()` to retain only the newest bounded array content for several streams.

## Device Registry Shape

`devices.json` stores objects similar to:

```json
{
  "device-id": {
    "deviceId": "device-id",
    "deviceName": "name",
    "model": "manufacturer model",
    "androidVersion": "14",
    "battery": 80,
    "network": "WiFi",
    "ip": "optional",
    "reliabilityProtocol": 2,
    "status": {},
    "lastSeen": 1234567890,
    "online": true
  }
}
```

## Media Metadata Shape

```json
{
  "name": "IMG_0001.jpg",
  "size": 123456,
  "mime": "image/jpeg",
  "ts": 1234567890
}
```

Dedup key: same `name` and `size`.

## Command Shape

```json
{
  "id": "uuid",
  "type": "get_status",
  "params": {},
  "ts": 1234567890
}
```

## ACK Shape

```json
{
  "id": "uuid",
  "type": "get_status",
  "params": {},
  "ts": 1234567890,
  "success": true,
  "detail": "accepted",
  "ackedAt": 1234567999
}
```

## Operational Considerations

- JSON files can grow up to 10,000 entries for high-volume streams.
- For very large production use, migrate high-volume streams to SQLite/PostgreSQL.
- Back up `data/` regularly; all runtime state lives there.
- Do not commit `data/` to Git.
- Uploaded media/audio/screenshot files should be excluded from repository storage.
