# Phase 03 Report — Google OAuth connect flow

**Date:** 2026-08-28  
**Result:** Android-side OAuth flow complete; live calendar/email answer blocked by backend OpenAI HTTP 429

## What shipped

- Chrome Custom Tab launch for `GET /oauth/google/start`; no WebView and no Google tokens on the phone.
- `sableapp://oauth-complete` deep-link handling in `MainActivity`.
- A live Google connection row in chat with connected/disconnected status and Connect/Disconnect actions.
- `singleTop` activity delivery so OAuth return reuses the existing app activity instead of stacking a duplicate.
- An observable OAuth completion signal from `MainActivity` to `ChatViewModel`, which refreshes backend connection status on warm return.
- Chat client initialization when `ChatViewModel` is created, restoring the real send/stream path.
- Safe drawing insets around the chat screen so the composer remains above Android system navigation on targetSdk 35 devices.

## Regression coverage

- `GoogleOAuthCompletionNotifierTest` locks the observable completion signal.
- Failing-first result: Kotlin compilation failed because the completion notifier did not exist.
- Passing result: all 34 discovered debug unit tests pass.

## Build and diagnostics

- `./gradlew testDebugUnitTest :app:assembleDebug` — `BUILD SUCCESSFUL`.
- `./gradlew lintDebug` — `BUILD SUCCESSFUL`; 0 errors.
- Official JetBrains Kotlin LSP `LS-262.9593.0` — no diagnostics in changed Kotlin files.
- Debug APK installed on device `AJ4UVB4611033150`.

## On-device verification

- Before the fix, Android task inspection showed two `MainActivity` records after `sableapp://oauth-complete`.
- After the fix, Android reported that the deep-link intent was delivered to the existing top activity, and the assistant task contained exactly one `MainActivity`.
- The app rendered `Google connected` after the OAuth return.
- UIAutomator measured the Send label ending at y=2450 and the navigation bar starting at y=2534, leaving 84 px of safe clearance.
- A real chat submission rendered in the conversation and reached the live backend.
- Two independent visual reviewers passed the fresh 1200×2664 current-build capture with no blocking layout, clipping, system-bar, typography, or interaction findings.

## External limitation

The live backend returned OpenAI HTTP 429: its token-plan usage limit is exhausted. That proves the Android request path is active, but it prevents the final successful calendar/email answer until the backend account receives more credits or its allowance resets. No Android change can clear that backend quota condition.
