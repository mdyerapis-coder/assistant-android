# Phase 08 — Conversation Continuity (Server-Synced History)

Companion: `assistant-backend` `phases/05_conversation-continuity`. **Gated** — do not start until that phase has landed and UPDATED `docs/CONTRACT.md` has been hand-re-copied from `assistant-backend/docs/CONTRACT.md`. This repo never reads backend Python; `docs/CONTRACT.md` is the sole wire truth.

## Does

- **Server-synced history**: fetch thread list + messages from backend per UPDATED `docs/CONTRACT.md` (thread list / sync events). Server is source of truth.
- **Room as offline cache**: `assistant_chat.db` (05b) remains for offline read; hydrate/cache from server on launch and sync, do not treat local rows as authoritative.
- **Sessions screen lists server threads** (not local-only rows); tap resumes thread and loads its server messages.
- **Resume on new install**: fresh install with empty Room fetches threads/messages from server and resumes mid-conversation.

## Verification

- `docs/CONTRACT.md` header shows re-copy date from backend after companion lands.
- `./gradlew testDebugUnitTest` green; `./gradlew :app:assembleDebug` green.
- On device: sessions lists server threads; tap resumes with server messages; force-stop + relaunch retains threads.
- Fresh install (clear data / new device) fetches and resumes a prior thread; offline shows cached threads.

## Non-goals

- No backend Python read; no hand-patched contract drift — re-copy only.
- No new Room schema beyond cache role; no local-authoritative thread creation.
- No push/sync outside `docs/CONTRACT.md` events.
