# Phase 01.5 — tool-call UI proof

**Reads:** `docs/CONTRACT.md` (`tool_call_started`/`tool_call_progress`/`tool_call_finished` shapes), the `ChatReducer` and `ToolCallChip` built in phase 01.

**Does:** this phase is mostly verification, not new modules — the backend already exercises real tool calls (`remember`/`forget`, live-verified 2026-08-27). Confirm the Android side correctly renders the full lifecycle: a `tool_call_started` event shows a pending `ToolCallChip` ("Calling remember…"), a `tool_call_finished` with `ok: true` flips it to done, `ok: false` flips it to a failed state, and `delta` events before/after/interleaved with the tool call still land in the right place in the transcript (per `docs/CONTRACT.md`'s ordering-guarantees section — deltas can arrive before, after, or between `tool_call_*` events for the same turn).

**Writes:** likely just `ChatReducerTest.kt` additions covering interleaved delta/tool-call sequences, and any `ToolCallChip` visual state gaps found. If the phase-01 implementation already handles this correctly, this phase may close with no code changes — just the verification.

**Human check:** in the sideloaded app, send a message that causes the backend to call `remember` (e.g. "remember that my favourite colour is teal") — confirm the chip shows pending then done, and the assistant's final text still renders correctly around it.

**When done:** write `REPORT.md` recording what (if anything) needed fixing.
