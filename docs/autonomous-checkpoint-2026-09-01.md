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

**Name study:** Sable (silent + able) — warm sand, terracotta ember, quiet but capable. Poster at `docs/names-study.png`, philosophy at `/tmp/warm-silence-philosophy.md`. Gate approved, **shipped**: single-commit rename `com.mdyerapis.assistant` → `com.mdyerapis.sable`, `assistant.llmclouds.au` → `sable.llmclouds.au`, `assistantapp://` → `sableapp://`, app label `Assistant` → `Sable`, namespaces + `google-services.json` patched, `assembleDebug` + `testDebugUnitTest` green (`81f2abd` chrome + this commit).

**Chrome collapse (spec §2):** Chat TopAppBar → `ModelStatusChip` + `MoreVert` overflow (`Conversation history`, TTS toggle, `Clear conversation`, `Settings`, `Model: …`), reclaims vertical space. Verified on `AJ4UVB4611033150` (overflow bounds `[1070,191][1148,269]`, menu shows history/TTS/settings).

**Home-as-start:** `AppNavHost` `startDestination = sessions` when token present (verified), Sessions empty now shows `+ New` + scrollable `SuggestionChip` row (`Morning brief` primary `primaryContainer`, others `surfaceVariant`) that starts a new conversation and sends the prompt.

**Marketing/pricing:** `docs/sable-positioning.md` — hero *Quietly capable*, three tiers: Local (free, on-device LLM offline), Cloud BYO (free app + your OpenAI/VPS), Hosted ($9→$15/mo, $90/yr, managed `sable.llmclouds.au` + SQLite backup/uptime). FAQ covers no always-listening, banner `Retry`, second-device continuity.

## Verification (audit)

- `assistant-android`: `testDebugUnitTest` ✅, `:app:assembleDebug` ✅ (after Sable rename + Sessions chips), chrome overflow + sessions home verified on `AJ4UVB4611033150`, share `ACTION_SEND` with full `https://example.com/page` via `ExternalIntake(replay=1)` delivers to `EditText`, voice mic `isListening` True/False toggles (`Stop/Start voice input`), `RECORD_AUDIO` + `SEND_SMS/READ_SMS` granted, recovery banner `Can't reach… Retry/Re-configure` → `Retry` clears after backend `{"status":"ok"}`.
- `assistant-backend`: `pytest` ✅ 94 passed, supervised `assistant-backend` at `127.0.0.1:8420` stable (`{"status":"ok"}`) after `ASSISTANT_BEARER_TOKEN` env + `EADDRINUSE` fix (old `39568` killed, `139176` live).
- CONTRACT re-copy: backend SMS relay section hand-copied to Android (`e0b02de`) — now needs re-copy for `sable.*` domain (covered in rename commit).
- On-device: prior Phase 07 DB + connectedAndroidTest 4/4, Phase 08 resume, Phase 09/10 UI present (mic + TTS), FCM token, SMS deny-path — plus this session's share/recovery/overflow checks.

## Deferred

- Backend `sable.llmclouds.au` Caddy + TLS cutover (still `assistant.llmclouds.au` on VPS until DNS).
- Play listing screenshots (light/dark home, empty chat with suggestions, overflow).

## Commits

- Android: `3ee2399` (share/recovery/voice test), `105cf84` (share/deep-link to chat from any destination), `62f8d8c` (sessions as startDestination), `81f2abd` (chrome collapse), this commit (Sable rename + Sessions chips + DESIGN + positioning)
- Backend: `9a8bb4e` (automations + device control, 94 tests) — Sable domain rename pending companion PR in `assistant-backend`.

Phone stays warm on `sable` (`com.mdyerapis.sable`) — ready for Hosted cutover when you are.
