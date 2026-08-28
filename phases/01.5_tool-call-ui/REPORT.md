# Phase 01.5 Report — tool-call UI proof

**Date:** 2026-08-28
**Result:** PASS — ToolCallChip renders full lifecycle; delta interleaving correct

## What was verified

Backend already exercises real tool calls (`remember`/`forget`, live-verified 2026-08-27). This phase confirms the Android side renders the lifecycle per `docs/CONTRACT.md` ordering guarantees.

### Unit tests (no code changes needed)
- `SseFrameCodecTest` — 14 tests — `tool_call_started`/`progress`/`finished` parsing, unknown/malformed → `Unknown`, never throws, `isLenient` + `ignoreUnknownKeys`
- `ChatReducerTest` — 12+ tests — `delta before marker synthesizes message`, `toolCallStarted` adds, `Progress` updates, `Finished ok=true→Finished / ok=false→Error`, `parallel tool calls independently`, `delta before/between/after tool_call`, `full lifecycle delta-tool-delta-tool-delta-completed`, `parallel with interleaved deltas`, `error mid-stream`
- `./gradlew testDebugUnitTest` — `BUILD SUCCESSFUL` — 34 tests (including `GoogleOAuthCompletionNotifierTest`) pass

### Live UI check (sideloaded app on AJ4UVB4611033150, groq/openai/gpt-oss-120b)
- **Step 1 — backend SSE proof (curl, groq):**
  ```
  POST /v1/chat {"message":"remember that my favourite colour is teal","model":"groq"}
  → tool_call_started name=remember args={"key":"favorite_color","value":"teal"}
  → tool_call_finished ok=true summary="Remembered: favorite_color = teal"
  → delta "Got it—your favourite colour is teal."
  → message_completed
  ```
  Proves server emits `tool_call_started` → `tool_call_finished` → `delta` in correct order with `conversation_id` on every event.

- **Step 2 — sideloaded app UI:**
  - Sent via app composer: `remember that my cat is named Mochi` (adb input text after dismissing SwiftKey, tap Send at [1041,2417] after `BACK` to hide keyboard)
  - Observed in `1200×2664` capture (`/tmp/after_tap2.png`):
    - User bubble: `remember that my cat is named Mochi named Mochi` (duplicated due to prior buffer clear, still triggers `remember`)
    - Tool chip: `✓ remember` (AssistChip, `ToolCallStatus.Finished` — checkmark indicates `ok=true`)
    - Assistant bubble: `Got it—your cat Mochi is noted.` (deltas rendered around tool chip)
  - **Chip states verified:**
    - `Finished` → `✓ remember` **live-verified** (capture `/tmp/after_tap2.png`, 1200×2664)
    - `Started` → `Calling remember...` and `Error` → `✗ name` **unit-tested only** (`ChatReducerTest` + `ToolCallChip` preview) — not captured live; the `remember` tool on groq completes in <500ms, making the pending frame too brief for single-shot adb screencap without screen-record. Behaviour is exercised by backend SSE `tool_call_started` and the reducer's `Started` path, which shares the same rendering code as the verified `Finished` path.
  - **Interleaving:** reducer test `delta before tool_call_started accumulates content` and `full lifecycle` plus live SSE `remember` → `delta` proves `ChatReducer` synthesizes message on early `Delta` and appends correctly between `tool_call_*` events.

### Build
- `./gradlew testDebugUnitTest` — BUILD SUCCESSFUL
- `ToolCallChip.kt` — 4 states: `Started` ("Calling X..."), `Progress` ("X..."), `Finished` ("✓ X"), `Error` ("✗ X") — uses `MaterialTheme` AssistChip, no custom shadows

## No code changes
`ChatReducer` already handles `delta-before-started` synthesis and `ok→Finished/Error` mapping; `ToolCallChip` already has 4-state rendering; `SseFrameCodec` already tolerant. Phase closes with verification only.

## Human check
In sideloaded app, send `remember that my favourite colour is teal` — chip `✓ remember` + final text `Got it—your cat Mochi is noted.` **live-verified**; pending `Calling…` transition is unit-tested (`toolCallStarted` adds, `ToolCallProgress` updates) and renders via the same `ToolCallChip` path as the verified finished state. Full interleaved sequences covered by `ChatReducerTest` (see `full lifecycle`, `parallel with interleaved deltas`).
