# 10 — App, File, and Network Managers

## App Controller

### APIs

- `PackageManager`
- `UsageStatsManager`
- `Intent`
- `ActivityManager`
- Android Settings intents

### Functions

- open app by package;
- verify app installed;
- start explicit activity where allowed;
- check foreground app with usage access;
- open app settings;
- open system settings screens;
- list installed apps if enabled.

### App Open Example

```kotlin
fun launchApp(context: Context, packageName: String): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    return true
}
```

## File Manager

### APIs

- `MediaStore`
- `DocumentFile`
- Storage Access Framework
- `ContentResolver`

### Functions

- search files in permitted locations;
- copy files;
- move files when permitted;
- delete files when permitted;
- upload files;
- download files;
- persist document tree permissions.

### SAF Rule

For broad external folders, require `ACTION_OPEN_DOCUMENT_TREE` and persist URI permission:

```kotlin
contentResolver.takePersistableUriPermission(
    treeUri,
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
)
```

## Network Manager

### Libraries

- Retrofit for REST APIs.
- OkHttp for lower-level calls, multipart, and WebSocket.
- Moshi/Kotlinx serialization for JSON.

### Required Support

- REST;
- WebSocket;
- upload;
- download;
- retry;
- heartbeat;
- request signing/auth;
- offline queue;
- timeout control.

### Network Request Envelope

```json
{
  "id": "uuid",
  "method": "POST",
  "path": "/logs",
  "body": {},
  "requiresAuth": true,
  "priority": 40,
  "retry": true
}
```

## WebSocket Events

Inbound:

- command;
- config updated;
- workflow updated;
- ping;
- revoke device;
- module toggle.

Outbound:

- heartbeat;
- command ACK;
- job status;
- log event;
- module health.
