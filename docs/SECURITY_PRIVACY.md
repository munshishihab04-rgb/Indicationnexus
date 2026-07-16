# Security and Privacy

## Security Model

Nexus uses a simple shared-token model for backend API access.

- Token source: `NEXUS_TOKEN` environment variable or fallback in code.
- Token accepted via `X-Token` header or `?token=` query.
- Token comparison uses timing-safe comparison.
- WebSocket clients authenticate through query `?token=`.

## Important Security Boundaries

This documentation is for transparent, authorized device backup/diagnostics only.

Do not use this system for:

- Stealth installation.
- Hidden surveillance.
- Credential, OTP, password, banking, or protected app data extraction.
- Bypassing Android permission/consent systems.
- Accessing private app sandboxes or protected databases.
- Monitoring devices without informed owner/user authorization.

## Sensitive Capabilities Present in Codebase

The codebase includes endpoints/classes capable of handling sensitive personal data:

- SMS records.
- Call logs.
- Contacts.
- Location history.
- Notification text.
- Accessibility event text.
- Clipboard snapshots.
- Media files.
- Audio/screenshot uploads.
- Installed app list.

These require strong operational controls.

## Android Transparency

The Android implementation uses visible/user-controlled Android components:

- Foreground service notification.
- Runtime permission dialogs.
- Notification listener settings screen.
- Accessibility settings screen.
- Battery optimization settings.
- Storage Access Framework folder picker.

The command executor intentionally returns failure for background actions that require user-visible consent or are restricted by Android:

- Screenshot capture.
- Background audio recording.
- Background camera photo.
- Background clipboard access.

## Recommended Hardening

Before production use:

1. Always set `NEXUS_TOKEN` through environment, not source code.
2. Serve the server only over HTTPS.
3. Put the server behind a reverse proxy with TLS.
4. Restrict dashboard access by IP/VPN or additional login.
5. Rotate tokens regularly.
6. Do not pass tokens in URLs where logs may capture query strings; prefer `X-Token`.
7. Encrypt backups and stored media.
8. Exclude all runtime data and `.env` files from Git.
9. Use a proper database with access controls for large production data.
10. Add audit logs for command creation and data export.
11. Add per-device enrollment/authorization records.
12. Add role-based access for dashboard users.
13. Add retention policies for high-risk streams.

## Privacy-by-Design Recommendations

- Make all enabled data streams visible in the Android UI.
- Add per-stream toggles.
- Add a clear stop-sync button.
- Add data retention controls.
- Avoid collecting streams that are not needed.
- Show last upload time and destination server URL to the device user.
- Require explicit confirmation for sensitive exports such as ZIP download.

## Known Risk Areas to Review

- Shared token authentication is simple and should be replaced with scoped API keys or user auth for multi-user deployments.
- JSON file storage has no per-record encryption.
- Query-token authentication can leak in logs.
- High-volume personal data streams require clear consent and retention controls.
- `GET /api/token` is intentionally disabled and returns 404; keep it disabled.
