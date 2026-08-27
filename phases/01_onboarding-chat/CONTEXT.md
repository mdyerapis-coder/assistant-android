# Phase 01 — onboarding + chat screen end-to-end (the proof milestone)

**Reads:** `docs/CONTRACT.md` (the exact SSE event shapes and request body — implement `SseFrameCodec.kt` against this, not against guesses), `docs/plan.md` §2 (module responsibilities, the streamed-chat pattern paragraph), `docs/adr/001` (pure modules), `docs/adr/002` (errors carry intent), `docs/adr/003` (Keystore, not EncryptedSharedPreferences).

**Does:**
- `core:model` — `ChatMessage`, `ChatEvent` (sealed: `Delta`, `MessageStarted`, `MessageCompleted`, `ToolCallStarted`/`Progress`/`Finished`, `Error`, `Unknown` — the `Unknown` branch is load-bearing, see `docs/CONTRACT.md`'s tolerant-parsing note), `ChatState`, `ToolCall`.
- `core:network` — `OkHttpClientFactory`, `BearerAuthInterceptor`, `BackoffPolicy` (full-jitter), `AppError` (sealed: `Retryable`/`AuthExpired`/`Fatal`/`ToolExecutionFailed`).
- `core:security` — `KeystoreSecretStore` (AES-GCM via Android Keystore), `BearerTokenRepository`.
- `core:database` — Room, `ChatMessageDao`/`ChatMessageEntity` (chat history only, thin in v1).
- `core:designsystem` — `Theme`, `MessageBubble`, `MessageContent`, `StateComponents`, `ToolCallChip` (renders a collapsed "Calling X…" / "✓ done" pill — the seam tool events render into, ahead of any real tool existing on the Android side).
- `backend-client` — `ChatApiClient` (`POST /v1/chat`, reads the OkHttp SSE response body), `SseFrameCodec` (parses `data: {...}` per `docs/CONTRACT.md`; `[DONE]` → completion; unknown `type` → `Unknown`, never throws), `ChatReducer` (**pure** `(ChatState, ChatEvent) -> ChatState`, no coroutines/IO, must handle a `delta` arriving before any "message started" marker by synthesizing the message — `docs/CONTRACT.md`'s ordering-guarantees section). Write `SseFrameCodecTest.kt` and `ChatReducerTest.kt` — these two are the actual correctness proof for this phase, exercise them harder than the UI.
- `feature:onboarding` — paste the bearer token → `GET /v1/health` → store via `BearerTokenRepository` if 200, else show an error and don't store.
- `feature:chat` — `ChatViewModel` (owns `StateFlow<ChatState>`, feeds `ChatReducer`), `ChatUiState`, `ChatScreen` (`collectAsStateWithLifecycle()`, renders `MessageBubble` list + inline `ToolCallChip`s).

**Writes:** everything listed above, under the module tree from phase 00.

**Human check:** get a real bearer token from Mason (generated server-side by `assistant-backend/scripts/gen_bearer_token.py` — never invent one), sideload the debug APK, paste the token, send a message, watch tokens stream into the UI **in real time, not as one final blob**. The backend half of this (`POST /v1/chat`) is done and live-verified as of 2026-08-27 — if the request fails, the bug is almost certainly on this side (wrong header, wrong SSE parsing, wrong base URL), not the server.

**When done:** write `REPORT.md` recording the verification result and the backend URL/token used (token itself: reference that it came from Mason, don't paste the actual value into the report).
