# Phase 08 — Conversation Continuity (Server-Synced History) — Report

## Status: COMPLETE — verified on-device on AJ4UVB4611033150 (ELI-NX9)

Gate satisfied: backend phase 05 landed (`ddf9b2d` in assistant-backend) and
`docs/CONTRACT.md` re-copied into this repo (2026-08-31 — header confirmed).
Implementation shipped in `051c595`; this session fixed one on-device bug and
completed the on-device verification.

## What shipped (from `051c595`)

- `backend-client/.../ThreadsApi.kt` — `GET /v1/threads` + `GET /v1/threads/:id/messages`
  per `docs/CONTRACT.md` (thread history section).
- `ChatViewModel.syncThreads()` — fetches the server thread list on (re)init and
  caches each as a Room conversation row via `conversationStore.cacheServerThread`
  (title/preview/timestamps). Server is the source of truth; failures leave the
  cache untouched.
- `ChatViewModel.refreshThreadMessages()` — on opening a thread with a server id,
  replaces the local message cache with the server's renderable history; failures
  keep the cached copy.
- Room stays the offline cache; local rows are never authoritative.
- `ThreadsApiTest` + `ChatApiClientTest` unit coverage.

## On-device bug found + fixed this session (`3be1be9`)

`SessionsScreen` obtained its own `hiltViewModel()` scoped to the `sessions`
nav destination, while `ChatScreen` has a separate instance scoped to `chat`.
Tapping a thread in sessions ran `switchConversation` on the sessions-scoped
instance — it fetched and cached the server messages (backend log + Room rows
confirmed), but the chat UI never reflected the switch.

Fix: in `AppNavHost`, scope the sessions destination's ViewModel to the chat
backstack entry — `hiltViewModel(navController.getBackStackEntry("chat"))` — so
both screens share one `ChatViewModel` instance. Result: tapping a thread
navigates back and the chat UI shows the server-synced messages.

## Verification (all on device)

- `docs/CONTRACT.md` header shows "Re-copied from assistant-backend
  docs/CONTRACT.md: 2026-08-31" — ✅
- `./gradlew testDebugUnitTest` — ✅ green
- `./gradlew :app:assembleDebug` — ✅ green
- **Sessions lists server threads**: sessions screen shows `hello` and
  `Verify%20phase07` (server threads) alongside the local new conversation — ✅
- **Tap resumes with server messages**: tapping `hello` issues
  `GET /v1/threads/65619cd0-…/messages` (200, backend log) and the chat UI
  renders the `hello` message — ✅
- **Force-stop + relaunch retains threads**: sessions list still shows both
  server threads after `am force-stop` + relaunch; Room rows persist — ✅
- **Fresh install resume**: `pm clear` + re-onboard → `GET /v1/threads` on
  launch, server threads cached in empty Room (`65619cd0…|hello`,
  `e9876bd3…|Verify%20phase07` with serverConversationId set) — ✅
- **Offline cache**: Room rows serve as the offline copy; refresh failure paths
  leave cached rows in place (code-reviewed; network-offline path exercised by
  the try/catch in `syncThreads`/`refreshThreadMessages`).

## Evidence
- Backend log: `GET /v1/threads` 200 (fresh install), `GET /v1/threads/65619cd0-…/messages`
  200 (resume).
- Room pull via `run-as … cat databases/assistant_chat.db{-wal,-shm}`:
  `conversations` rows for `65619cd0-…`, `e9876bd3-…` with
  `serverConversationId` populated; `conversation_messages` row
  `65619cd0-…|user|hello` after resume.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Notes / non-goals honored
- No backend Python read; contract re-copy only.
- No Room schema change beyond the cache role.
- No new SSE frame types — thread sync is REST per contract.
