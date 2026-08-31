# Phase 09 — Voice Interaction — Report

**Status:** code complete + build/unit-test green; on-device verification
(spoken query, audible TTS, RECORD_AUDIO flow) **pending device reconnection**
(phone dropped off ADB mid-session).

## What shipped (`db0bde1`)

- **`VoiceController`** (`feature/chat/.../VoiceController.kt`) — wraps
  `android.speech.SpeechRecognizer` (free-form STT, no partial results) and
  `android.speech.tts.TextToSpeech` (async init, `LANG_MISSING_DATA`/`LANG_NOT_SUPPORTED`
  guarded). `isListening`/`ttsEnabled`/`isTtsReady` as Compose state; `destroy()`
  releases both engines (called from a `DisposableEffect`).
- **Composer mic button** (`core/designsystem/.../Composer.kt`) — 48dp target,
  `contentDescription` ("Start voice input" / "Stop listening"), idle vs
  listening states (pulsing scale animation, error-container tint while
  listening), stop action. State-only animation per DESIGN.md §1 Motion.
- **ChatScreen wiring** —
  - Mic tap → `RECORD_AUDIO` check; missing → `RequestPermission` launcher;
    granted → `startListening`; recognized text fills the composer.
  - TTS toggle in the top bar (`VolumeUp`/`VolumeOff` + contentDescription),
    calls `viewModel.toggleTts()`.
  - `LaunchedEffect(messages.size)` speaks each completed assistant message
    (both `AppModelMode.Backend` and `AppModelMode.OnDevice`); audio is
    presentation only.
- **ChatUiState + ChatViewModel** — `ttsEnabled` flag + `toggleTts()`,
  persists per session (survives recomposition; reset per VM scope).
- **Manifest** — `RECORD_AUDIO` permission.

No `docs/CONTRACT.md` change — voice rides the existing SSE chat contract.

## Verification

- `./gradlew testDebugUnitTest` — ✅ green (179 tasks, no regressions).
- `./gradlew :app:assembleDebug` — ✅ clean.
- No new permissions beyond `RECORD_AUDIO` (phase-scoped).

## Pending (needs the phone back)

1. Reconnect AJ4UVB4611033150 via USB.
2. Grant `RECORD_AUDIO` → mic tap → spoken query → assistant text reply.
3. Enable TTS toggle → reply audible in Backend mode; repeat in OnDevice mode.
4. Screencaps: mic idle + listening, TTS toggle on/off, light + dark
   (required by the phase CONTEXT).

## Non-goals honored

- No server-side STT, no audio upload.
- No wake-word / always-listening.
