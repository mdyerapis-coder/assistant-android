# Phase 10 — SMS via Skills Relay

**Gated on:** `assistant-backend` `phases/06_skills-platform-sms` — do not start until that phase has a `REPORT.md`. No split-brain SMS.

## Does
- **Permissions:** Request `SEND_SMS` / `READ_SMS` at runtime only. Show rationale UI explaining why *before* `requestPermissions()`; handle deny/never-ask-again via `shouldShowRequestPermissionRationale`.
- **Relay (phone executes):** Backend SMS skill triggers phone via FCM data message `{action:"sms.send"|"sms.read", id, args_json}`; phone executes via `SmsManager` / SMS provider and POSTs result to backend endpoint (e.g. `POST /v1/sms/result {id, ok, summary}`).
- **Request-driven only:** No background upload, no periodic sync, no inbox mirroring — phone touches SMS only when an FCM action arrives.

## Verification
- Grant → send SMS via skill completes; deny → graceful failure with rationale.
- FCM `sms.send` / `sms.read` round-trips to backend result endpoint; no SMS touched without FCM.
- `./gradlew :app:assembleDebug` clean; no new permissions outside this phase.

## Non-goals
- No background SMS upload or sync.
- No direct `POST /v1/chat` SMS tool — all SMS via FCM relay.
- No `CONTRACT.md` drift — additions copied by hand from backend; this repo never edits backend Python.

## CONTRACT.md additions (hand-copy from backend)
When backend lands, copy its SSE `tool_call_*` SMS shapes and result endpoint to `docs/CONTRACT.md` verbatim — do not invent.
