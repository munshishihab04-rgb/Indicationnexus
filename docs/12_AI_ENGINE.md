# 12 — AI Engine

## Purpose

The AI Engine converts current context into a proposed next action.

It must not directly execute actions. It returns a plan that the Automation Engine validates before execution.

## Inputs

- screenshot or screen image when user granted screen capture;
- OCR results;
- Accessibility tree;
- notification event;
- foreground app;
- workflow history;
- device state;
- remote config;
- user instruction;
- recent action results.

## Context Envelope

```json
{
  "requestId": "uuid",
  "timestamp": 1730000000000,
  "foregroundApp": {
    "packageName": "com.example.app",
    "label": "Example"
  },
  "screen": {
    "classification": "form",
    "accessibilityTree": {},
    "ocr": [],
    "vision": []
  },
  "notification": null,
  "history": [],
  "allowedActions": ["click", "type", "wait", "back"],
  "constraints": {
    "noCredentials": true,
    "requireConfirmationForSensitive": true
  }
}
```

## AI Output

```json
{
  "action": "click",
  "target": {
    "text": "Continue",
    "viewId": null,
    "coordinates": { "x": 540, "y": 1820 }
  },
  "confidence": 0.91,
  "reason": "The screen appears to be a form and the Continue button is visible.",
  "next": "wait_element",
  "requiresConfirmation": false
}
```

## Validation Layer

Before execution:

- action must be in `allowedActions`;
- target must be found or coordinate confidence must be high;
- app must be allowed;
- module must be enabled;
- permission must exist;
- rate limit must pass;
- sensitive action policy must pass;
- workflow must not exceed max steps.

## AI Safety Rules

- Do not ask AI to recover or expose passwords, OTPs, tokens, banking data, or credentials.
- Do not execute AI actions without validation.
- Use confidence thresholds.
- Support user confirmation mode.
- Store compact action reasoning and result logs.
- Do not upload raw screenshots unless explicitly configured.

## Local vs Server AI

| Mode | Pros | Cons |
|---|---|---|
| Local model | More private, works offline | Higher device cost, limited accuracy |
| Server AI | More powerful, easier updates | Requires data minimization and transport security |
| Hybrid | Balanced | More complex |
