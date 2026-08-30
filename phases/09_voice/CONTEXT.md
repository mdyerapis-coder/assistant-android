# Phase 09 — Voice Interaction

Execution spec. Adds on-device voice I/O to the chat surface; no `docs/CONTRACT.md` change.

## Does

- **STT via `android.speech.SpeechRecognizer`**: mic button in `Composer` — 48dp target, `contentDescription`, listening state animated per DESIGN.md §1 Motion (state only, respects platform animation settings).
- **TTS via `android.speech.tts.TextToSpeech`**: speaks assistant responses; visible toggle (contentDescription) to enable/mute, persists per session.
- **Both model modes**: STT populates `Composer` text and TTS reads streamed response in `AppModelMode.Backend` and `AppModelMode.OnDevice` (04); audio is presentation only.
- **No backend changes**: uses existing SSE chat contract; no new endpoints or `docs/CONTRACT.md` edits.

## Verification

- `./gradlew testDebugUnitTest` green; `./gradlew :app:assembleDebug` installs on `AJ4UVB4611033150`.
- Screencaps: Composer with mic idle + listening states, TTS toggle on/off — light and dark.
- On-device: spoken query via mic returns assistant text and audible TTS in both Backend and OnDevice modes; `RECORD_AUDIO` permission flow verified.

## Non-goals

- No server-side STT or audio upload.
- No wake-word / always-listening.
