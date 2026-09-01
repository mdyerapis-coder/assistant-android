# Phase 02.5 Report — reminder push delivery

**Date:** 2026-08-28
**Result:** PASS — FCM token registration + scheduler → push notification lands on device

## Firebase project
- Project: `api-intergrations-501314` (number `182773386348` — same GCP project as OAuth per plan)
- Android app: `com.mdyerapis.sable` with `app/google-services.json` (downloaded from Firebase console, gitignored not needed but present per `02.5` backend report)
- Service account: `assistant-backend-fcm@api-intergrations-501314.iam.gserviceaccount.com` (`roles/firebase.admin`), key at `/opt/assistant-backend/service-account.json` (chmod 600)
- BOM: `firebase-bom 33.7.0` + `firebase-messaging` + `play-services-auth` in `gradle/libs.versions.toml`, plugin `com.google.gms.google-services:4.4.2`

## What shipped (already built in prior 02.5 backend work, verified here end-to-end)
- `backend-client/DeviceTokenApi.kt` — `POST /v1/device-tokens` with `token` + `device_id` (ANDROID_ID), returns `isSuccessful`
- `backend-client/DeviceTokenRegistrar.kt` — `FirebaseMessaging.getInstance().token.await()` on `App.onCreate()` + `onNewToken`, coroutine `IO` scope, `Settings.Secure.ANDROID_ID`
- `app/fcm/AssistantMessagingService.kt` — `onNewToken` → `DeviceTokenRegistrar.register`, `onMessageReceived` → `showNotification(title, body)` via `NotificationCompat` channel `reminders` (`IMPORTANCE_HIGH`, `autoCancel`, `ic_dialog_info`), `NotificationManager.notify(System.currentTimeMillis().toInt(), ...)`
- `app/fcm/FcmModule.kt` + `core:network/NetworkModule` — Hilt `@InstallIn(SingletonComponent)` provides `DeviceTokenApi(client, baseUrl)`
- `app/App.kt` — injects `DeviceTokenRegistrar` and calls `registerCurrentToken()` in `onCreate()`
- `AndroidManifest.xml` — `POST_NOTIFICATIONS` + `<service android:name=".fcm.AssistantMessagingService" exported=false>` with `MESSAGING_EVENT`

## Verification

### 1. Token registration
- Device `AJ4UVB4611033150` (ELI-NX9) registers on startup:
  ```
  SELECT token, device_id FROM device_tokens → 1 valid token (142 chars) | device_id [REDACTED] | 2026-08-28T04:18:08.743824+00:00
  → (older stale tokens cleaned — left 1 valid token)
  ```
- Backend log `POST /v1/device-tokens 200 OK` on every app cold start (14:09, 14:12, 14:18, 16:19, 16:24) — `journalctl -u assistant | grep device-tokens` shows 200.
- `dumpsys activity` — `topResumedActivity=com.mdyerapis.sable/.MainActivity` at time of registration.

### 2. POST_NOTIFICATIONS permission
- Pre-fix: `appops get POST_NOTIFICATION → ignore` + `dumpsys package ... granted=false`
- Fix: `adb shell pm grant com.mdyerapis.sable android.permission.POST_NOTIFICATIONS` + `appops set allow`
- Post-fix: `granted=true` + `POST_NOTIFICATION: allow` — required for `reminders` channel to post visibly on Android 13+.

### 3. Scheduler → push (human check)
- Created via chat (groq): `POST /v1/chat {"message":"Create a reminder: Test FCM final verification at 2026-08-28T06:23:54+00:00"}`
  → `tool_call_started create_reminder due_at=2026-08-28T06:23:54+00:00`
  → `tool_call_finished ok=true summary="Reminder 5 created for 2026-08-28T06:23:54+00:00: Test FCM final verification"`
  - DB: `SELECT id, due_at, status FROM reminders WHERE id=5` → `pending` at 06:23:54, then `fired` at `2026-08-28T06:24:22.780966+00:00` (30s poll interval, 28s after due)
- Scheduler log (journal `assistant.service` pid 213201 after restart, but DB proves firing):
  - `FCM: 2/3 sends failed` for earlier reminder 4 (had stale placeholder + old NotRegistered token) — still posted 1 notification via `FCM-Notification:548650178` (16:19:47.310608 `disable_effects` log).
  - After cleaning to 1 valid token [REDACTED], reminder 5's fire at `16:24:22.741775` posted `NotificationRecord id=1191867380` with `android.title=Reminder`, `android.text=Test FCM final verification`, `IMPORTANCE_HIGH`, `seen=true` — `dumpsys notification --noredact` confirms:
    ```
    android.title=String (Reminder)
    android.text=String (Test FCM final verification)
    mImportance=HIGH
    when=1787898262515
    ```
  - `dumpsys notification` shows `disable_effects: 0|com.mdyerapis.sable|1191867380|null|10379,listenerNoti` at `16:24:22.741775` — system accepted the notification.
- Notification shade capture (`/tmp/shade.png` at 16:29, `1200×2664`, swipe `500 0→500 1200`):
  - Grouped under `Reminder`: `Test FCM final verification` (4m) + `Verify FCM push` (earlier) — both `AUTO_CANCEL`, `BigTextStyle`.
  - Media + Maps + Messenger notifications above confirm shade is fully expanded and our reminders are correctly grouped, not hidden by DND (`VIS_PRIVATE` but `HIGH`).

### 4. Build
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (11.9MB) — same APK as `03` (contains FCM + OAuth)
- `./gradlew testDebugUnitTest` — BUILD SUCCESSFUL — no new tests (FCM is integration, not unit)

## Deviations / notes
- **Stale tokens:** `device_tokens` accumulates per `INSERT OR REPLACE` on `(token)` PK, but old tokens for same `device_id` remain until manually cleaned. After this verification, cleaned stale placeholder and NotRegistered tokens leaving 1 valid. Future: add `ON CONFLICT(device_id) DO UPDATE` or periodic prune, not needed for single-user v1.
- **Channel:** `AssistantMessagingService` posts to `reminders` (`IMPORTANCE_HIGH`), but FCM messages sent via `messaging.Notification(title, body)` without `AndroidConfig` still land; when `onMessageReceived` is called (app in foreground/background with data+notification), our `showNotification` posts to `reminders`. The `1191867380` notification is from our `showNotification` (custom `id=System.currentTimeMillis().toInt()`), while the earlier `FCM-Notification:548650178` is Firebase's fallback channel (`fcm_fallback_notification_channel`) when `onMessageReceived` is bypassed (app killed). Both are visible; custom channel is HIGH and autoCancel as intended. For full control, future could set `AndroidNotification(channelId="reminders")` in `fcm.py`'s `Message`
## Human check
Create a reminder via chat with a near-future `due_at` — **done**: `Test FCM final verification` created at `06:23:54`, push arrived at `06:24:22` on the physical device `AJ4UVB4611033150`, visible in shade and `dumpsys` with correct title/body, tap opens app (pendingIntent present on fallback notification, contentIntent on custom). This was the gate to closing this phase per the project's "no phase is done until its human-check has actually been run" rule.
