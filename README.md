# Personal Android Automation Agent — Technical Setup

This repository is a **new technical setup** for an owner-authorized Android personal automation agent.

The system is designed for one purpose: give the owner of an Android device a modular assistant that can observe permitted signals, understand app/screen context, run workflows, and execute actions using only official Android APIs and permissions manually granted by the user.

## Core Constraints

- No root.
- No MDM.
- No hidden bypasses.
- No private app sandbox access.
- No credential, password, banking, OTP, or protected-database extraction.
- Only Android APIs officially available to normal apps.
- Every sensitive permission must be granted manually by the device owner.
- Every module must be configurable and disableable remotely.

## Documentation Index

| Document | Purpose |
|---|---|
| [`docs/00_SYSTEM_OVERVIEW.md`](docs/00_SYSTEM_OVERVIEW.md) | System purpose, architecture, principles |
| [`docs/01_ANDROID_PROJECT_SETUP.md`](docs/01_ANDROID_PROJECT_SETUP.md) | Android project setup, Gradle, package layout |
| [`docs/02_MODULE_ARCHITECTURE.md`](docs/02_MODULE_ARCHITECTURE.md) | Module registry, lifecycle, contracts |
| [`docs/03_PERMISSION_MANAGER.md`](docs/03_PERMISSION_MANAGER.md) | Permission handling and settings flows |
| [`docs/04_DEVICE_MANAGER.md`](docs/04_DEVICE_MANAGER.md) | Device identity, heartbeat, inventory, health |
| [`docs/05_ACCESSIBILITY_ENGINE.md`](docs/05_ACCESSIBILITY_ENGINE.md) | Accessibility automation engine |
| [`docs/06_VISION_OCR_ENGINE.md`](docs/06_VISION_OCR_ENGINE.md) | Screen capture, OCR, vision, screen analyzer |
| [`docs/07_NOTIFICATION_ENGINE.md`](docs/07_NOTIFICATION_ENGINE.md) | Notification listener and app-specific parsing |
| [`docs/08_AUTOMATION_ENGINE.md`](docs/08_AUTOMATION_ENGINE.md) | Trigger → condition → action workflows |
| [`docs/09_SCHEDULER_AND_JOBS.md`](docs/09_SCHEDULER_AND_JOBS.md) | WorkManager, AlarmManager, retries, priorities |
| [`docs/10_APP_FILE_NETWORK_MANAGERS.md`](docs/10_APP_FILE_NETWORK_MANAGERS.md) | App controller, file manager, network manager |
| [`docs/11_LOCAL_DATABASE.md`](docs/11_LOCAL_DATABASE.md) | Room schema and persistent queues |
| [`docs/12_AI_ENGINE.md`](docs/12_AI_ENGINE.md) | AI context, action planning, validation |
| [`docs/13_REMOTE_CONFIG.md`](docs/13_REMOTE_CONFIG.md) | Remote feature flags, intervals, limits |
| [`docs/14_SERVER_API.md`](docs/14_SERVER_API.md) | REST/WebSocket backend API specification |
| [`docs/15_SECURITY_PRIVACY.md`](docs/15_SECURITY_PRIVACY.md) | Safety, privacy, permissions, audit controls |
| [`docs/16_IMPLEMENTATION_ROADMAP.md`](docs/16_IMPLEMENTATION_ROADMAP.md) | Build phases and acceptance criteria |

## Target Stack

| Layer | Recommended Technology |
|---|---|
| Android language | Kotlin preferred, Java acceptable |
| Android architecture | MVVM + repository pattern + module registry |
| Async | Kotlin Coroutines / WorkManager; Java Executor fallback |
| Local database | Room |
| HTTP | Retrofit + OkHttp |
| WebSocket | OkHttp WebSocket |
| OCR | ML Kit Text Recognition or Tesseract |
| Vision | MediaProjection + ImageReader + optional server AI |
| Backend | Node.js/Fastify or Express, or Kotlin/Spring if preferred |
| API format | JSON REST + WebSocket events |
| Auth | Device enrollment token + per-device API key |

## Final Behavior Goal

The agent should act as a personal assistant:

1. observes permitted device signals;
2. understands screen and notification context;
3. chooses the next action through rules or AI;
4. executes only permitted actions through official Android APIs;
5. logs each decision and result;
6. retries safely;
7. stays modular enough to add new automations without changing the foundation.
