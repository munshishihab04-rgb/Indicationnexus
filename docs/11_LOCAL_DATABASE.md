# 11 — Local Database

## Database Technology

Use Room for persistent local state and queues.

## Required Tables

- `device`
- `jobs`
- `automation`
- `automation_runs`
- `notifications`
- `screens`
- `ocr`
- `vision`
- `logs`
- `permissions`
- `config`
- `commands`

## Entity Overview

### device

```sql
CREATE TABLE device (
  device_id TEXT PRIMARY KEY,
  install_id TEXT NOT NULL,
  enrolled INTEGER NOT NULL,
  api_key_alias TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

### commands

```sql
CREATE TABLE commands (
  id TEXT PRIMARY KEY,
  type TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  status TEXT NOT NULL,
  received_at INTEGER NOT NULL,
  acked_at INTEGER,
  result_json TEXT
);
```

### config

```sql
CREATE TABLE config (
  key TEXT PRIMARY KEY,
  value_json TEXT NOT NULL,
  version INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

### logs

```sql
CREATE TABLE logs (
  id TEXT PRIMARY KEY,
  level TEXT NOT NULL,
  module TEXT NOT NULL,
  event TEXT NOT NULL,
  message TEXT,
  data_json TEXT,
  created_at INTEGER NOT NULL,
  uploaded_at INTEGER
);
```

### notifications

```sql
CREATE TABLE notifications (
  id TEXT PRIMARY KEY,
  package_name TEXT NOT NULL,
  app_name TEXT,
  title TEXT,
  body TEXT,
  sender TEXT,
  conversation TEXT,
  timestamp INTEGER NOT NULL,
  uploaded_at INTEGER
);
```

### screens

```sql
CREATE TABLE screens (
  id TEXT PRIMARY KEY,
  package_name TEXT,
  class_name TEXT,
  screen_type TEXT,
  accessibility_json TEXT,
  created_at INTEGER NOT NULL
);
```

### ocr

```sql
CREATE TABLE ocr (
  id TEXT PRIMARY KEY,
  screen_id TEXT NOT NULL,
  text TEXT NOT NULL,
  bounds_json TEXT NOT NULL,
  confidence REAL,
  created_at INTEGER NOT NULL
);
```

### vision

```sql
CREATE TABLE vision (
  id TEXT PRIMARY KEY,
  screen_id TEXT NOT NULL,
  result_json TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
```

## DAO Requirements

Each queue-like table needs:

- insert;
- mark running;
- mark succeeded;
- mark failed;
- fetch pending by priority;
- prune old records;
- count pending.

## Migration Rules

- Every schema change must include a Room migration.
- Never destructive-migrate production data silently.
- Keep config version separate from database schema version.

## Data Retention

Remote Config should control retention:

```json
{
  "retention": {
    "logsDays": 14,
    "notificationsDays": 7,
    "ocrDays": 3,
    "screensDays": 1,
    "jobsDays": 30
  }
}
```
