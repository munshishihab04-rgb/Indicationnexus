# 15 — Security and Privacy

## Mandatory Boundaries

The system is for owner-authorized personal automation only.

It must not support:

- stealth installation;
- hiding user-visible indicators;
- bypassing Android permission prompts;
- root-only data extraction;
- MDM-only controls;
- credential, password, OTP, banking, or protected app data extraction;
- private app sandbox reading;
- unauthorized monitoring of another person’s device.

## Consent Requirements

The UI must clearly show:

- what modules are enabled;
- which permissions are required;
- what data is collected;
- where data is sent;
- how to pause/stop/delete data.

## Sensitive Data Classes

Treat these as sensitive:

- notifications;
- OCR text;
- screenshots;
- accessibility text;
- SMS/call logs;
- contacts;
- location;
- file names and media;
- installed apps;
- logs containing UI context.

## Data Minimization

- Capture only what enabled workflows need.
- Prefer local analysis over upload where possible.
- Do not persist raw screenshots by default.
- Redact configured patterns.
- Use retention limits.
- Allow per-module deletion.

## Transport Security

- HTTPS only.
- Certificate validation enabled.
- Optional certificate pinning for managed deployments.
- No tokens in query strings for production APIs.
- Device API keys stored securely.

## Command Security

- Commands must be whitelisted.
- Commands must include IDs.
- Commands must be ACKed.
- Commands must expire.
- The app validates every command against local config and permissions.
- Sensitive actions require confirmation mode if configured.

## Logging Security

Every action log should include:

- module;
- action;
- target summary;
- result;
- error code;
- timestamp;
- request/command ID.

Logs should avoid raw sensitive content unless explicitly required and configured.

## App Allowlist/Denylist

Remote Config should support:

```json
{
  "apps": {
    "allowlist": [],
    "denylist": [
      "com.android.settings",
      "com.android.vending"
    ]
  }
}
```

Use denylist for banking, password managers, authenticator apps, enterprise apps, and other sensitive categories.
