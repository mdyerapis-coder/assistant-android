# Phase 10 — SMS via Skills Relay — Report

**Status:** code complete + unit-tested; on-device round-trip **BLOCKED on
device reconnection** (phone dropped off ADB mid-session; the local backend's
`device_tokens` table was empty, so no FCM token was registered to the local
backend during the test window).

Gate satisfied: `assistant-backend` `phases/06_skills-platform-sms/REPORT.md`
landed (`b0b49c5`); `docs/CONTRACT.md` re-copied into this repo (`e0b02de`).

## What shipped

- **`backend-client/.../SmsRelayApi.kt`** — `POST /v1/sms/results` client with
  the contract shapes from `docs/CONTRACT.md` "SMS relay (phase 06)"
  (`request_id`, `ok`, `error?`, `messages?: [{from_number, message, received_at}]`).
  Open methods for test stubbing; tolerant JSON (unknown keys ignored).
- **`app/.../fcm/SmsRelayController.kt`** — the relay executor:
  - `handle(action, data)` called from the FCM service on
    `action=send_sms|read_sms`.
  - `send_sms`: `SmsManager.sendTextMessage(phone, null, message, null, null)`
    → reports `{ok: true}`.
  - `read_sms`: queries `Telephony.Sms.Inbox` (optional `phone` filter,
    `limit` 1–100, date-desc) → reports `{ok: true, messages: [...]}` with
    ISO-8601 UTC timestamps.
  - Missing SEND_SMS/READ_SMS: reports `{ok: false, error: "sms permission
    not granted"}` to the backend, retains the action for retry, fires a
    notification that opens the app with `sms_permission_requested=true`.
  - Request-driven only: SMS is touched exclusively when an FCM action
    arrives; no background upload, no inbox mirroring, no periodic sync.
- **`app/.../fcm/SmsPermissionRationaleDialog.kt`** — Compose rationale dialog
  (shown on the notification deep-link) explaining why SMS access is needed
  before the system permission sheet; on grant, `retryPending()` re-runs the
  retained action.
- **`AssistantMessagingService`** — routes `action=send_sms|read_sms` data
  messages to the controller; all other pushes stay notification-only.
- **`MainActivity`** — hosts the rationale dialog + retry wiring.
- **Manifest** — `SEND_SMS` + `READ_SMS` runtime permissions.

## Verification

- `./gradlew testDebugUnitTest` — ✅ green (includes new `SmsRelayApiTest`:
  contract-shape encoding of send/failure/read payloads, unknown-field
  tolerance).
- `./gradlew :app:assembleDebug` — ✅ clean.
- Contract: `docs/CONTRACT.md` matches the backend's SMS section verbatim
  (re-copied `e0b02de`); no drift introduced.

## Blocked / pending (needs the phone back + a grant)

1. Reconnect AJ4UVB4611033150 via USB (`adb`).
2. Register the phone's FCM token to a backend that has Firebase credentials
   (local `service-account.json` or the VPS) — the local backend's
   `device_tokens` table was empty during the test window.
3. Trigger a `send_sms` tool call (via chat or direct tool invocation) →
   FCM push → phone executes → result POSTs to `/v1/sms/results` →
   `get_sms_result` reads it back.
4. User grants SEND_SMS/READ_SMS via the rationale dialog (deny path also
   verified: graceful failure reported to backend).

## Non-goals honored

- No background SMS upload or sync.
- No direct chat-tool SMS path — all SMS via the FCM relay.
- No `CONTRACT.md` invention — shapes copied by hand from the backend.
