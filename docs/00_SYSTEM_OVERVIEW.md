# 00 — System Overview

## Objective

Build a modular Android personal automation agent for the owner of the device.

The agent must use only official Android APIs and manually granted permissions. It should provide the maximum automation possible without root, MDM, private APIs, or hidden bypasses.

## Architectural Principles

- **Modular architecture**: every capability is a module with a clear lifecycle.
- **Remote configurable**: every feature, interval, limit, and trigger can be enabled/disabled remotely.
- **No hardcoded behavior**: business logic comes from config, workflows, or server instructions.
- **Asynchronous execution**: no long-running operation blocks UI or service lifecycle.
- **Persistent local queues**: jobs, commands, logs, OCR results, notifications, and actions survive restarts.
- **Automatic retries**: retry policy is centralized and observable.
- **Structured logging**: every action records input summary, decision, result, error, and timestamp.
- **Clean REST API**: backend endpoints are versioned, documented, and schema-driven.
- **Extensible by design**: adding a new module should require minimal core changes.

## High-Level Architecture

```text
Android App
├── Core Runtime
│   ├── Module Registry
│   ├── Remote Config Client
│   ├── Local Database
│   ├── Job Queue
│   ├── Logger
│   └── Network Client
├── Modules
│   ├── Device Manager
│   ├── Permission Manager
│   ├── Accessibility Engine
│   ├── Vision/OCR Engine
│   ├── Notification Engine
│   ├── Automation Engine
│   ├── Scheduler
│   ├── Screen Analyzer
│   ├── App Controller
│   ├── File Manager
│   └── AI Engine
└── Android APIs
    ├── AccessibilityService
    ├── NotificationListenerService
    ├── MediaProjection
    ├── WorkManager / AlarmManager
    ├── MediaStore / SAF
    ├── PackageManager / UsageStatsManager
    └── Settings / PowerManager / ConnectivityManager

Backend Server
├── Device enrollment
├── Heartbeat and health ingestion
├── Remote config
├── Command queue
├── Workflow storage
├── OCR/vision/AI endpoints
├── Logs ingestion
├── File upload/download
└── Dashboard/API clients
```

## Runtime Flow

```text
1. App starts and initializes core services.
2. Device registers or loads existing enrollment.
3. Remote config is fetched and cached locally.
4. Module registry starts only enabled modules.
5. Scheduler restores pending jobs from Room.
6. Notification/accessibility/vision/device events become triggers.
7. Automation engine evaluates trigger + conditions.
8. Action executor validates permission and safety.
9. Action runs asynchronously.
10. Result is logged, persisted, and ACKed if command-driven.
```

## Recommended Package Layout

```text
app/src/main/java/<package>/
├── core/
│   ├── config/
│   ├── db/
│   ├── logging/
│   ├── modules/
│   ├── network/
│   ├── queue/
│   └── security/
├── device/
├── permissions/
├── accessibility/
├── vision/
├── ocr/
├── notifications/
├── automation/
├── scheduler/
├── screen/
├── apps/
├── files/
├── ai/
└── ui/
```
