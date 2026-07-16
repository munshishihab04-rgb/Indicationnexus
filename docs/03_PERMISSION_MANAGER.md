# 03 — Permission Manager

## Responsibilities

The Permission Manager tracks all runtime permissions and special Android settings required by modules.

It must:

- check permission state;
- explain why a permission is needed;
- open the correct Android settings screen;
- persist permission state snapshots;
- notify module registry when state changes;
- disable modules when required permissions are missing.

## Permission Types

| Capability | Android Mechanism |
|---|---|
| Notifications | `POST_NOTIFICATIONS`, `NotificationManager.areNotificationsEnabled()` |
| Notification listener | `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` |
| Accessibility | `Settings.ACTION_ACCESSIBILITY_SETTINGS` |
| Media | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, legacy storage permission |
| Battery optimization | `PowerManager.isIgnoringBatteryOptimizations()` |
| Overlay | `Settings.canDrawOverlays()` if overlay module exists |
| Background location | `ACCESS_BACKGROUND_LOCATION` + app details screen |
| External storage tree | Storage Access Framework `ACTION_OPEN_DOCUMENT_TREE` |
| Usage stats | `Settings.ACTION_USAGE_ACCESS_SETTINGS` |
| Screen capture | `MediaProjectionManager.createScreenCaptureIntent()` |

## State Model

```kotlin
data class PermissionState(
    val key: String,
    val granted: Boolean,
    val requiredBy: List<String>,
    val lastCheckedAt: Long,
    val canRequestInApp: Boolean,
    val settingsAction: String?
)
```

## Recommended Permission Table

Room table: `permissions`

| Column | Type | Notes |
|---|---|---|
| `key` | String primary key | e.g. `notification_listener` |
| `granted` | Boolean | Current state |
| `required_by` | String JSON | Module IDs |
| `last_checked_at` | Long | Epoch millis |
| `last_prompted_at` | Long nullable | User prompt tracking |
| `settings_action` | String nullable | Android settings action |

## UI Rules

- Do not spam permission requests.
- Explain the exact module and capability before opening settings.
- Show current state and last checked time.
- Allow the user to disable a module instead of granting permission.
- Never mislabel a sensitive permission.

## Special Settings Functions

```kotlin
fun openNotificationListenerSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
    intent.data = Uri.parse("package:${context.packageName}")
    context.startActivity(intent)
}
```

## Module Gating

A module starts only if:

1. remote config enables it;
2. all required permissions are granted;
3. device policy allows it;
4. battery/network conditions satisfy module limits.
