# 07 — Notification Engine

## Purpose

The Notification Engine observes notifications through `NotificationListenerService` after the user enables notification listener access.

Notifications can trigger workflows, update context, and provide structured messages to the AI engine.

## Android API

- `NotificationListenerService`
- `StatusBarNotification`
- `Notification.extras`
- `Notification.MessagingStyle`

## Fields to Read

- `Notification.EXTRA_TITLE`
- `Notification.EXTRA_TEXT`
- `Notification.EXTRA_BIG_TEXT`
- `Notification.EXTRA_TEXT_LINES`
- `Notification.EXTRA_SUB_TEXT`
- `Notification.EXTRA_MESSAGES`
- `extras`

## Supported App Parsers

Initial parsers:

- WhatsApp
- Telegram
- Gmail
- SMS app notifications
- Messenger
- Generic fallback parser

## Normalized Notification Event

```json
{
  "id": "deterministic-hash",
  "packageName": "org.telegram.messenger",
  "appName": "Telegram",
  "conversation": "Contact or chat",
  "sender": "Sender",
  "title": "Title",
  "body": "Message body",
  "timestamp": 1730000000000,
  "source": "messaging_style",
  "complete": true,
  "rawType": "notification"
}
```

## Queueing

Notifications should be persisted before upload:

1. Receive notification.
2. Parse and normalize.
3. Deduplicate by deterministic ID.
4. Save to Room `notifications` table.
5. Enqueue upload job.
6. Mark uploaded after server confirmation.

## Trigger Integration

A notification can produce automation triggers:

```json
{
  "trigger": "notification",
  "packageName": "com.google.android.gm",
  "conditions": [
    { "field": "title", "operator": "contains", "value": "Invoice" }
  ],
  "workflowId": "save_invoice_attachment"
}
```

## Privacy Controls

- Allow disabling notification content capture while keeping notification metadata.
- Allow app allowlist/denylist.
- Redact sensitive patterns before upload if configured.
- Never hide the requirement for notification listener permission.
