# 05 — Accessibility Engine

## Purpose

The Accessibility Engine performs user-authorized UI automation through Android `AccessibilityService` APIs.

It is the main automation layer when UI nodes are available.

## Android APIs

- `AccessibilityService`
- `AccessibilityNodeInfo`
- `AccessibilityEvent`
- `GestureDescription`
- `dispatchGesture()`
- `performAction()`
- `findAccessibilityNodeInfosByText()`
- `findAccessibilityNodeInfosByViewId()`
- `getRootInActiveWindow()`

## Core Actions

| Action | API Strategy |
|---|---|
| click | `node.performAction(ACTION_CLICK)` or gesture fallback |
| long click | `ACTION_LONG_CLICK` or long gesture |
| swipe | `dispatchGesture()` path |
| scroll | `ACTION_SCROLL_FORWARD/BACKWARD` or gesture |
| type text | `ACTION_SET_TEXT` when supported |
| copy text | node selection/copy actions where available |
| find button | traverse tree by class/clickable/text/content description |
| open menu | click node or global action if supported |
| detect screen | tree snapshot + heuristics + OCR/vision fusion |
| wait element | repeat tree scan until timeout |
| wait text | repeat text scan until timeout |

## Accessibility Tree Snapshot

```kotlin
data class UiNodeSnapshot(
    val id: String?,
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val className: String?,
    val packageName: String?,
    val bounds: Rect,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
    val children: List<UiNodeSnapshot>
)
```

## Action Command Schema

```json
{
  "type": "click",
  "target": {
    "text": "Continue",
    "viewId": "com.example:id/continue",
    "bounds": null
  },
  "timeoutMs": 10000,
  "fallback": "gesture_center"
}
```

## Wait Element Algorithm

```text
1. Capture current root using getRootInActiveWindow().
2. Traverse tree breadth-first.
3. Match by viewId, text, content description, class, or predicate.
4. If found, return node reference/snapshot.
5. If not found, delay 250–500 ms.
6. Repeat until timeout.
7. Emit structured failure log.
```

## Gesture Fallback

Use `dispatchGesture()` only when:

- node action is unavailable;
- target coordinates are known from OCR/vision/tree bounds;
- action is allowed by workflow policy;
- confidence exceeds configured threshold.

## Global Actions

Supported where Android allows:

- Back: `GLOBAL_ACTION_BACK`
- Home: `GLOBAL_ACTION_HOME`
- Recents: `GLOBAL_ACTION_RECENTS`
- Notifications: `GLOBAL_ACTION_NOTIFICATIONS`
- Quick Settings: `GLOBAL_ACTION_QUICK_SETTINGS`

## Safety Controls

- Require module enabled in Remote Config.
- Require accessibility permission enabled by user.
- Limit actions per minute.
- Add app allowlist/denylist.
- Add workflow-specific confirmation for sensitive actions.
- Log every automation action.
- Never automate credential/password/OTP extraction or protected flows.
