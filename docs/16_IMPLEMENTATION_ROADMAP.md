# 16 — Implementation Roadmap

## Phase 1 — Foundation

Deliverables:

- Android project skeleton.
- Foreground service.
- Room database.
- Network client.
- Logger.
- Job queue.
- Module registry.
- Remote config client.

Acceptance criteria:

- App starts foreground service.
- Device ID persists.
- Config is fetched and cached.
- Modules can be enabled/disabled without code changes.
- Logs are saved locally and uploaded.

## Phase 2 — Device and Permission Layer

Deliverables:

- Device Manager.
- Permission Manager.
- Setup UI.
- Heartbeat.
- Health check.

Acceptance criteria:

- UI shows all permission states.
- Heartbeat includes health and module status.
- Missing permissions disable dependent modules.

## Phase 3 — Scheduler and Command Queue

Deliverables:

- WorkManager integration.
- Persistent jobs table.
- Command fetch and ACK.
- Retry/backoff.

Acceptance criteria:

- Commands survive restart.
- Jobs retry with backoff.
- ACK is sent only after command handling result.

## Phase 4 — Notification Engine

Deliverables:

- Notification listener.
- App parsers.
- Notification queue.
- Notification upload.
- Notification trigger integration.

Acceptance criteria:

- Events are deduplicated.
- Events persist before upload.
- Workflows can trigger from notification rules.

## Phase 5 — Accessibility Engine

Deliverables:

- Tree snapshot.
- Node search.
- Click/long-click/type/scroll/swipe.
- Wait element/text.
- Global actions.
- Action validation.

Acceptance criteria:

- Workflows can operate on supported UI nodes.
- Every action logs result.
- Rate limits and app policy are enforced.

## Phase 6 — Vision, OCR, Screen Analyzer

Deliverables:

- MediaProjection consent flow.
- ImageReader screenshot pipeline.
- OCR integration.
- Screen classifier.
- Fusion with Accessibility tree.

Acceptance criteria:

- OCR returns text + bounding boxes + confidence.
- Screen analyzer returns structured screen model.
- Raw screenshots are not stored unless configured.

## Phase 7 — Automation Engine

Deliverables:

- Workflow DSL.
- Trigger/condition/action engine.
- Persistent automation runs.
- Action executor.
- AI planner bridge.

Acceptance criteria:

- Workflows execute deterministic steps.
- Failed steps retry or stop according to policy.
- AI actions are validated before execution.

## Phase 8 — Backend API and Dashboard

Deliverables:

- `/v1` REST API.
- WebSocket command/config channel.
- Device dashboard.
- Logs and queue dashboard.
- Workflow/config editor.

Acceptance criteria:

- API schemas documented.
- Dashboard can enroll device, update config, send commands, inspect logs.
- No sensitive token leakage.

## Phase 9 — Hardening

Deliverables:

- Security review.
- Privacy controls.
- Retention cleanup.
- App denylist.
- Test suite.
- CI.

Acceptance criteria:

- Unit tests for module registry, config, queue, command ACK, workflow engine.
- Instrumentation tests for permission/setup flows.
- Server tests for auth, validation, command lifecycle.
