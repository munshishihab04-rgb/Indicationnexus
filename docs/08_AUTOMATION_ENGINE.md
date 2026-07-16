# 08 — Automation Engine

## Purpose

The Automation Engine executes workflows built from:

```text
Trigger -> Condition -> Action -> Next
```

It must be rule-driven/config-driven, not hardcoded.

## Trigger Types

- notification;
- timer;
- boot;
- Wi-Fi change;
- Bluetooth change;
- battery threshold;
- charger connected/disconnected;
- app opened;
- text found;
- image found;
- OCR result;
- server command;
- manual user action.

## Condition Types

- equals / contains / regex;
- numeric comparison;
- time window;
- app foreground;
- permission granted;
- network type;
- battery level;
- screen classification;
- OCR text present;
- accessibility node present;
- previous action result.

## Action Types

- click;
- type;
- swipe;
- scroll;
- wait;
- launch app;
- close app where Android permits;
- share;
- copy;
- paste;
- back;
- home;
- recent apps;
- notification shade;
- screenshot with consent;
- OCR;
- vision;
- API request;
- enqueue job;
- send log;
- ask AI planner.

## Workflow Schema

```json
{
  "id": "workflow_login_helper",
  "enabled": true,
  "version": 1,
  "trigger": {
    "type": "app_opened",
    "packageName": "com.example.app"
  },
  "steps": [
    {
      "id": "wait_login",
      "action": "wait_text",
      "params": { "text": "Login", "timeoutMs": 10000 },
      "next": "click_continue"
    },
    {
      "id": "click_continue",
      "action": "click",
      "params": { "text": "Continue" },
      "next": "done"
    }
  ]
}
```

## Execution State

Room table: `automation_runs`

Track:

- workflow ID/version;
- trigger ID;
- current step;
- status;
- attempts;
- started/finished time;
- last error;
- result summary.

## Action Validation

Before executing any action:

1. Confirm workflow is enabled.
2. Confirm module is enabled.
3. Confirm permission exists.
4. Confirm app is allowed.
5. Confirm action rate limit.
6. Confirm target confidence.
7. Log planned action.
8. Execute.
9. Log result.

## AI Integration

The workflow may call AI planner for the next step, but the output must be validated against allowed actions and app policy before execution.
