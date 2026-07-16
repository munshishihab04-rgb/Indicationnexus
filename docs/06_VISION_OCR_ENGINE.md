# 06 — Vision, OCR, and Screen Analyzer

## Purpose

The Vision/OCR layer helps the agent understand screens when Accessibility nodes are unavailable, incomplete, or misleading.

Screen capture must require explicit user consent through Android MediaProjection.

## Android APIs

- `MediaProjection`
- `MediaProjectionManager`
- `VirtualDisplay`
- `ImageReader`
- `Bitmap`
- `Canvas`

## OCR Libraries

Recommended options:

- ML Kit Text Recognition for on-device OCR.
- Tesseract if offline/custom OCR is required.

## Screen Capture Flow

```text
1. User explicitly grants MediaProjection consent.
2. App creates VirtualDisplay backed by ImageReader.
3. ImageReader receives latest frame.
4. Frame is converted to Bitmap.
5. Bitmap is optionally downscaled/redacted.
6. OCR/vision analysis runs.
7. Raw screenshots are discarded unless retention is explicitly enabled.
8. Analysis result is persisted.
```

## OCR Output

```json
{
  "screenId": "uuid",
  "timestamp": 1730000000000,
  "items": [
    {
      "text": "Continue",
      "boundingBox": { "left": 420, "top": 1800, "right": 900, "bottom": 1910 },
      "confidence": 0.98,
      "language": "en"
    }
  ]
}
```

## Vision Output

```json
{
  "screenId": "uuid",
  "objects": [
    {
      "label": "button",
      "boundingBox": { "left": 420, "top": 1800, "right": 900, "bottom": 1910 },
      "confidence": 0.91
    }
  ],
  "icons": [],
  "classification": {
    "screen": "login",
    "confidence": 0.87
  }
}
```

## Screen Analyzer Output

```json
{
  "screen": "login",
  "confidence": 0.88,
  "buttons": [],
  "fields": [],
  "texts": [],
  "images": [],
  "source": ["accessibility", "ocr", "vision"]
}
```

## Screen Classes

Initial classifier labels:

- `login`
- `home`
- `chat`
- `popup`
- `dialog`
- `error`
- `loading`
- `form`
- `settings`
- `unknown`

## Fusion Strategy

```text
Accessibility tree + OCR + Vision + foreground app + notification context
                       ↓
                Screen Analyzer
                       ↓
             Structured screen model
                       ↓
             Automation / AI Engine
```

## Privacy Controls

- Do not keep screenshots by default.
- Store OCR text only if the module is enabled.
- Add app denylist for sensitive apps.
- Redact configured patterns before upload/logging.
- Require explicit user-visible setup for screen capture.
