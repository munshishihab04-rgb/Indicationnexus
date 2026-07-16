# Indication Nexus Documentation

This repository contains the structured technical documentation for **Nexus**, an Android + Node.js server/dashboard system for transparent, permission-based device backup, synchronization, telemetry, and diagnostics.

> Source analyzed: `/opt/data/nexus-monorepo-github`  
> Target GitHub repository: `munshishihab04-rgb/Indicationnexus`  
> Documentation generated from the real Android and server implementation, not from assumptions.

## Documentation Index

| Document | Purpose |
|---|---|
| [`docs/OVERVIEW.md`](docs/OVERVIEW.md) | Product overview, scope, architecture summary |
| [`docs/SPECIFICATION.md`](docs/SPECIFICATION.md) | Full target Nexus Personal Agent specification |
| [`docs/COVERAGE_MATRIX.md`](docs/COVERAGE_MATRIX.md) | Implemented vs missing coverage against the specification |
| [`docs/PROJECT_STRUCTURE.md`](docs/PROJECT_STRUCTURE.md) | Repository layout and important files |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Android ↔ server ↔ dashboard architecture |
| [`docs/FEATURES.md`](docs/FEATURES.md) | Complete implemented feature list |
| [`docs/API.md`](docs/API.md) | REST API and WebSocket endpoint documentation |
| [`docs/ANDROID_APP.md`](docs/ANDROID_APP.md) | Android app modules, services, permissions, sync logic |
| [`docs/SERVER.md`](docs/SERVER.md) | Node.js backend, storage, auth, media handling |
| [`docs/DATABASE_STORAGE.md`](docs/DATABASE_STORAGE.md) | File-based persistence model and data layout |
| [`docs/DASHBOARD.md`](docs/DASHBOARD.md) | Dashboard files and frontend responsibilities |
| [`docs/SECURITY_PRIVACY.md`](docs/SECURITY_PRIVACY.md) | Security model, privacy boundaries, sensitive capabilities |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) | Runtime configuration and deployment guide |
| [`docs/TESTING.md`](docs/TESTING.md) | Existing tests and verification checklist |
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md) | Implementation state and notable fixes |

## High-level System

Nexus has three main parts:

1. **Android app** (`com.nexus.app`)  
   Runs a foreground sync service, collects user-authorized data through Android platform APIs, sends data to the server, and accepts a small set of whitelisted commands.

2. **Node.js backend**  
   Express 5 server with token authentication, JSON/file-based persistence, media upload support, thumbnail generation, command queues, ACK tracking, WebSocket broadcasting, statistics, ZIP export, and optional AI context analysis.

3. **Dashboard**  
   Static web dashboard served from the backend for device status, media, GPS, SMS/call views, notifications/events, and operational monitoring.

## Safety and Privacy Position

This documentation describes the implementation for legitimate, transparent, owner-authorized backup and diagnostics. It does **not** document stealth installation, hidden operation, credential theft, OTP extraction, bypass of Android protections, or unauthorized monitoring.

The Android app requests explicit Android permissions and uses visible components such as a foreground service notification, notification listener settings, accessibility settings, and storage access framework.

## Quick Runtime Summary

| Component | Runtime |
|---|---|
| Android app package | `com.nexus.app` |
| Android min/target/compile SDK | min 26, target 34, compile 34 |
| App version analyzed | `2.1` / versionCode `12` |
| Backend | Node.js + Express `5.2.1` |
| Backend default port | `3000` |
| Backend auth | `X-Token` header or `?token=` query |
| Storage | JSON files + uploaded files under `NEXUS_DATA_DIR` |
| Dashboard route | `/dashboard` plus SPA fallback |

## Setup Pointers

See:

- [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) for server runtime configuration.
- [`docs/ANDROID_APP.md`](docs/ANDROID_APP.md) for Android permissions and service behavior.
- [`docs/API.md`](docs/API.md) for backend endpoints.
