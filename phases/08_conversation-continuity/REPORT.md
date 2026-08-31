# Phase 08 — Conversation Continuity (Server-Synced History)

**Status:** code complete + unit-tested + `:app:assembleDebug` green. Backend companion (assistant-backend phase 05) is deployed and live-verified. No phone was attached this session, so on-device verification is the remaining gate (see below).

## What landed

### 1. `backend-client/ThreadsApi.kt` — the wire half of the contract

`listThreads()` / `listMessages(threadId)` against `GET /v1/threads` and `GET /v1/threads/{id}/messages` per `docs/CONTRACT.md` Part 2. Methods `open` so tests stub them without a network; decoder uses `Json { ignoreUnknownKeys = true }` to honor the contract's tolerant-parsing rule (ignore unknown fields, never reject). `ThreadsApiTest` covers both response shapes, the tolerance rule, and empty-list defaults — pure JVM, no device.

### 2. `core:database` — Room as a cache, server is the source of truth

- `ConversationDao.getByServerId(serverId)` — merge lookup so a thread that already has a locally-created row (before its first send assigned the server id) keeps that row's id; active UI references stay valid across sync.
- `ConversationStore` gains `cacheServerThread(...)` (upsert by server id, returns local id) and `replaceMessages(...)` (delete + reinsert the server's renderable history). `ConversationRepository` implements both; no schema bump, no migration — the existing tables already carry `serverConversationId`.
- Room rows for synced threads are keyed by the server thread id; a locally-created row that later gets a server id is merged in place, not duplicated.

### 3. `feature:chat` — sync + resume

- `ChatViewModel` is now `open` with a `protected open fun createThreadsApi(...)` seam. Dagger cannot provision a lambda, so the constructor stays at its seven Hilt-provided deps and tests subclass to inject a fake `ThreadsApi` before the init block runs `initClient`.
- `syncThreads()` — fetch the thread list, cache each conversation row (no message fetch; messages load on open). Called from `initClient` (every app open → fresh-install rehydrate) and from `SessionsScreen`'s `LaunchedEffect(Unit)` (pull-to-fresh on screen entry). Offline → cached list stays untouched.
- `switchConversation(id)` — after the existing local-hydrate, if the conversation has a server id it calls `refreshThreadMessages` to replace the cache with the server's renderable history; failures leave the cached copy in place. The reducer then renders the authoritative messages.
- `parseIsoEpochMs` — ISO-8601 (`OffsetDateTime.parse`) → epoch millis, `0L` on parse failure; server timestamps feed `updatedAt` so server threads sort correctly alongside locally-created ones.

### 4. `ChatViewModelTest` — fakes now mirror Room's reactivity

`FakeConversationStore.messagesFor` previously returned a *snapshot* `MutableStateFlow` — a real Room query re-emits after a write. The fake now keeps a live per-conversation `MutableStateFlow` updated by `appendMessage`/`replaceMessages`, so `switchConversation`'s server-replace-then-render actually fires the collector. New tests: `switchConversation_loadsItsHistory` (server messages replace the cache) and `syncThreads_cachesServerThreadsAsConversations` (init-time sync populates the sessions list). Both pump the test scheduler until the `withContext(Dispatchers.IO)` continuation lands (bounded) — that's the only honest way to test a real-IO coroutine without a dispatcher-injection seam.

## Deviations from the phase CONTEXT

None on behavior. `docs/CONTRACT.md` was re-copied from the backend's merged contract (header carries the 2026-08-31 re-copy date the CONTEXT's verification asks for) — the companion landed and the contract carries no SSE sync frames (REST pull covers every verification here), so the contract side is satisfied without new event types.

## Verification

- `:backend-client:testDebugUnitTest` — green (ThreadsApi serialization/tolerance).
- `:feature:chat:testDebugUnitTest` — green, incl. the two new continuity tests.
- `./gradlew testDebugUnitTest :app:assembleDebug` — green (full suite + debug APK builds).
- `:core:database:compileDebugAndroidTestKotlin` — green (the new DAO query + repository methods compile under the device-gated androidTest source set; a matching `ConversationRepositoryTest` row for `cacheServerThread`/`replaceMessages` belongs in `core:database/src/androidTest` next, run on a device).

## Not done yet (phone-side; needs a device)

1. Sessions screen lists server threads after `syncThreads`; tap resumes with server messages; force-stop + relaunch retains them.
2. Fresh install (clear data) with the same bearer token fetches and resumes a prior thread; offline shows the cached threads.
3. The on-device `connectedAndroidTest` (`ConversationRepositoryTest`) for the new cache methods — no device this session.

Backend that feeds this (`assistant-backend` phase 05) is already deployed and live-verified: `GET /v1/threads` and `GET /v1/threads/{id}/messages` return real data from `https://assistant.llmclouds.au`.
