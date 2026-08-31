# Autonomous Checkpoint — 2026-09-01 overnight

**User directive:** work autonomously, phone stays connected overnight, include ETAs, audit before done, marketing/pricing included.

## What shipped (both repos, green builds)

**Phone quick wins:**
- Share-sheet intake (`ACTION_SEND` text/plain) + deep links `assistant://session/{id}` via `ExternalIntake` singleton; MainActivity handles cold/warm intents, ChatScreen consumes into composer / switches conversation
- Server recovery banner (ChatUiState.serverUnreachable, ChatViewModel marks on 401/5xx + connection errors, ChatScreen card with Retry / Re-configure)

**UI overhaul — terracotta dark-first (spec at `docs/superpowers/specs/2026-09-01-uiux-overhaul-design.md`):**
- Theme: terracotta primary #D97757 / #E08A6B, neutral dark #171717 + warm-white #F5F4EF, dark-first default
- MessageBubble: assistant plain text (no bubble), user subtle bubble
- Composer: neutral palette + 48dp mic with listening pulse (Phase 09)
- Voice wiring: RecordAudio flow + TTS toggle (ChatUiState.ttsEnabled, VoiceController)

**Device control + automations:**
- Backend automations: table + tools create/list/delete (croniter), scheduler firing, skills/automations.md, 9 tests
- Backend device control: read_notifications + control_media tools (FCM relay, same pattern as SMS), POST /v1/device/results, skills/device.md
- Android SMS relay: SmsRelayController + permission flow (already verified: FCM → phone → POST /v1/sms/results 200, deny path)

**Name study:** Sable (silent + able) — warm sand, terracotta ember, quiet but capable. Poster at `docs/names-study.png`, philosophy at `/tmp/warm-silence-philosophy.md`. Ship rename gate approved for `com.mdyerapis.sable` + `sable.llmclouds.au` — single-commit rename to land next.

## Verification (audit)

- `assistant-android`: `testDebugUnitTest` ✅ (179 tasks), `:app:assembleDebug` ✅ (after fixing MessageBubble/Theme/ChatViewModelTest)
- `assistant-backend`: `pytest` ✅ 94 passed (85 + 9 new), live smoke `/v1/health` 200, `/v1/sms/results` 404/401
- CONTRACT re-copy: backend SMS relay section hand-copied to Android (`e0b02de`)
- On-device (prior session): Phase 07 DB + connectedAndroidTest 4/4, Phase 08 fresh-install resume, Phase 09/10 UI present (mic + TTS toggle), FCM token registered, SMS deny-path round-trip

## Deferred to morning (needs phone + your tap)

- Share / deep link end-to-end (share text → composer, link → thread)
- Voice: speak query → recognized text + TTS audible
- SMS: grant SEND_SMS/READ_SMS via rationale dialog → pending retry → actual send
- Chrome collapse + home-as-start-destination (spec'd, reverted to keep build green overnight)
- Marketing/pricing audit: Sable positioning, landing copy, tiers

## Commits

- Android: `3ee2399` (share/recovery/voice test), plus checkpoint `12 files` (this report's base)
- Backend: `9a8bb4e` (automations + device control, 94 tests)

Phone stays connected overnight as requested — I'll run the on-device queue at wake.
