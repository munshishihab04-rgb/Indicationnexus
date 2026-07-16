# Deployment Guide

## Server Deployment

### Requirements

- Node.js runtime.
- npm.
- `zip` command installed if using media ZIP export.
- Storage volume with enough capacity for media/audio/screenshots.
- HTTPS reverse proxy recommended.

### Install

```bash
cd server
npm install
```

### Environment

Set runtime variables outside Git:

```bash
export PORT=3000
export NEXUS_ROOT=/opt/data/nexus-local
export NEXUS_DATA_DIR=/opt/data/nexus-local/data
export NEXUS_DASHBOARD_DIR=/opt/data/nexus-local/dashboard
export NEXUS_TOKEN='replace-with-strong-token'
# Optional:
export GEMINI_API_KEY='...'
```

### Start

```bash
cd server
npm start
```

or directly:

```bash
node index.js
```

Expected startup log:

```text
Nexus server running on port 3000
```

### Health Checks

Authenticated read checks:

```bash
curl -H "X-Token: $NEXUS_TOKEN" http://localhost:3000/api/devices
curl -H "X-Token: $NEXUS_TOKEN" http://localhost:3000/api/stats
```

Unauthorized requests should return 401:

```bash
curl http://localhost:3000/api/devices
```

## Dashboard Deployment

Dashboard static files are served from `NEXUS_DASHBOARD_DIR`. In the source repo, dashboard files are under:

```text
server/dashboard/
```

If deploying from a copied runtime directory, make sure `NEXUS_DASHBOARD_DIR` points to the actual dashboard folder.

## Android Build

From the Android project root:

```bash
cd android
./gradlew assembleDebug
```

The app configuration is in:

```text
android/app/build.gradle
```

Key values:

- `applicationId "com.nexus.app"`
- `minSdk 26`
- `targetSdk 34`
- `compileSdk 34`
- `versionName "2.1"`

## Android Runtime Setup

On the device, the user must grant/enable relevant capabilities:

- Runtime permissions requested by the app.
- Notification listener access.
- Accessibility service, if that stream is required.
- Battery optimization exemption, if continuous sync is required.
- Background location set to “Allow all the time” if background GPS is required.
- External folder/SD access through document tree picker if external-tree sync is required.

## Reverse Proxy Recommendations

Use a reverse proxy such as nginx/Caddy with:

- TLS certificate.
- Request body size large enough for media uploads.
- Upload timeout long enough for mobile networks.
- No logging of query tokens.
- IP allowlist/VPN for dashboard where possible.

## Data Backup

Back up the entire `NEXUS_DATA_DIR`:

```text
data/
├── devices.json
├── *.json stream directories
├── media/
├── audio/
├── screenshots/
├── thumbs/
└── zips/
```

Exclude temporary ZIPs if storage is limited.

## Git Hygiene

Never commit:

- `data/`
- media/audio/screenshot uploads
- `.env`
- API tokens
- private keys
- build outputs
- APK artifacts unless intentionally publishing a release
