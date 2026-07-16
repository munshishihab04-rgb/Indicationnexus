# Changelog / Implementation State

This changelog summarizes implementation state observed from the analyzed source.

## Current Version Snapshot

- Android app version: `2.1` / versionCode `12`.
- Backend: Express 5.2.1 single-file server.
- Storage: file-based JSON + uploaded files.
- Dashboard: static assets under `server/dashboard`.

## Implemented Reliability Improvements

Observed directly in code comments/tests:

- WebSocket device socket map is populated on device registration.
- WebSocket close handler cleans up clients/device mapping to avoid leaks.
- Online threshold changed to 5 minutes.
- Event timestamp sorting handles `ts` and `timestamp`.
- Media sorting handles `ts` and `serverTime`.
- Video thumbnail requests return unsupported rather than crashing Sharp.
- `/api/token` endpoint is disabled and returns 404.
- Command inspection endpoint is read-only.
- Protocol v2 commands remain queued until explicit ACK.
- Android service receives commands through ping response and avoids duplicate HTTP polling.
- Android command ACK sends accepted/unsupported status.
- SMS and call cursors advance only after successful server confirmation.
- Media sync includes retry queue and daily reconciliation.
- Notification listener has queue/dedup behavior.

## Implemented Server Modules

- Auth middleware.
- Device heartbeat.
- Device listing.
- Event upload/listing.
- Location upload/listing.
- Command queue and ACK storage.
- Media upload/list/download/thumb/ZIP.
- Audio upload/list/download.
- Screenshot upload/list/download.
- SMS/call/contacts endpoints.
- Browser/keylog/clipboard/apps endpoints.
- Device status diagnostics.
- Global stats.
- Optional AI chat.
- Dashboard static serving.
- WebSocket broadcasting.

## Implemented Android Modules

- Setup activity and permission UI.
- Foreground service.
- API client.
- Media sync.
- External tree sync.
- SMS sync.
- Call log sync.
- Contacts sync.
- GPS sync.
- Notification event sync.
- Device diagnostics.
- Recovery scheduler/job service.
- Boot receiver.
- Accessibility service hook.
- App list sync.
- Camera/audio/screenshot helper classes.

## Known Design Limitations

- Shared-token auth is simple and should be upgraded for multi-user production.
- File JSON storage is easy to operate but not ideal for very high-volume or multi-writer workloads.
- `/api/events` has limit support but no full offset pagination in the analyzed server code.
- ZIP jobs are memory-only; jobs disappear after restart.
- Some sensitive helper classes/endpoints exist even when Android command executor does not actively allow background execution.
- Query-token support can leak tokens through logs; prefer `X-Token`.

## Recommended Next Milestones

- Add scoped API keys or user login for dashboard.
- Add per-stream privacy controls in Android UI.
- Add retention settings per data stream.
- Add database backend for large datasets.
- Add export audit log.
- Add pagination to all high-volume GET endpoints.
- Add CI workflow for Android tests and server checks.
- Add OpenAPI specification generated from `docs/API.md`.
