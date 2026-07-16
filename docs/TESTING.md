# Testing and Verification

## Existing Tests Found

Android unit tests exist under:

```text
android/app/src/test/java/com/nexus/app/
```

Detected test files:

- `NotificationEventQueueTest.java`
- `RestrictedSettingsGuideTest.java`
- `ExternalTreePolicyTest.java`
- `RecoveryScheduleTest.java`
- `ContactsSync` related behavior through module tests where present
- `ReliabilityPolicyTest.java`
- `RuntimeSyncPolicyTest.java`
- `MediaSyncPolicyTest.java`
- `MediaSyncLedgerTest.java`

Server test files:

- `server/test-hardening.js`
- `server/test-reliability.js`

## Android Test Themes

The tests and policy classes cover:

- Notification event queuing/dedup behavior.
- Restricted settings guidance.
- External tree policy.
- Recovery schedule interval safety.
- Command explicit ACK requirement.
- Cursor advancement only after confirmed success.
- Runtime sync policy.
- Media sync policy.
- Media sync ledger behavior.

Example from `ReliabilityPolicyTest`:

- Recovery interval must respect Android minimum safe interval.
- Commands require explicit acknowledgement.
- Cursors advance only after server confirmation.

## Build Verification

Android:

```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Server:

```bash
cd server
node --check index.js
npm install
node test-hardening.js
node test-reliability.js
```

## Server Smoke Tests

With `NEXUS_TOKEN` configured:

```bash
curl -i http://localhost:3000/api/devices
curl -H "X-Token: $NEXUS_TOKEN" http://localhost:3000/api/devices
curl -H "X-Token: $NEXUS_TOKEN" http://localhost:3000/api/stats
```

Expected:

- Unauthenticated request returns 401.
- Authenticated request returns JSON.

## Endpoint Verification Checklist

- [ ] `/api/ping` updates `devices.json`.
- [ ] `/api/devices` returns online state.
- [ ] `/api/commands/:deviceId` is read-only and does not consume commands.
- [ ] Command ACK removes a queued command.
- [ ] `/api/status/:deviceId` accepts and returns diagnostic status.
- [ ] Media upload writes file and metadata.
- [ ] Duplicate media returns `{ ok:true, dedup:true }`.
- [ ] Thumbnail endpoint creates cache for images.
- [ ] Video thumbnail request returns 415, not a crash.
- [ ] SMS/call uploads deduplicate correctly.
- [ ] `/api/stats` counts per-device streams.
- [ ] WebSocket rejects missing/bad token.
- [ ] WebSocket accepts correct token and receives broadcasts.

## Non-invasive Production Verification

When verifying a live instance, avoid commands that change state unless explicitly requested.

Safe checks:

- Read `/api/devices`.
- Read `/api/stats`.
- Read `/api/status/:deviceId`.
- Inspect command queue files directly if possible.
- Compare counts over time.

Avoid during read-only audits:

- `POST /api/commands`.
- ZIP export unless requested.
- Triggering sync/media/location commands.
- Restarting server.
- Deleting or modifying data.
