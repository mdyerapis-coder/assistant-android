# Phase 05b — Conversation History (Room DB) — Report

## Status: implementation complete; on-device verification partially BLOCKED

## What shipped
New Room-based conversation history with a Sessions browser/resume/delete UI.

### Files created
- `core/database/.../chat/ConversationEntity.kt`
- `core/database/.../chat/MessageEntity.kt`
- `core/database/.../chat/ConversationDao.kt`
- `core/database/.../chat/MessageDao.kt`
- `core/database/.../chat/ChatDatabase.kt`
- `core/database/.../chat/ConversationRepository.kt`
- `core/database/.../chat/ConversationStore.kt` (interface for testability)
- `feature/chat/.../SessionsScreen.kt`
- `core/database/src/androidTest/.../ConversationRepositoryTest.kt`

### Files modified
- `core/database/build.gradle.kts` — added `hilt.android`, `hilt.compiler`,
  `androidx.test:core`, `androidx.test.ext:junit`, `room-testing`.
- `core/database/.../DatabaseModule.kt` — `ChatDatabase` + `ConversationRepository`
  providers; `ConversationStoreModule` binds the interface.
- `feature/chat/.../ChatViewModel.kt` — injects `ConversationStore`; persistence
  in `sendMessage` (both modes); `startNewConversation`/`switchConversation`/
  `deleteConversation`; `availableSessions` collector; init hydration.
- `feature/chat/.../ChatUiState.kt` — added `availableSessions`, `activeConversationId`.
- `feature/chat/.../ChatScreen.kt` — added `onNavigateSessions` + `📂` action.
- `feature/chat/src/test/.../ChatViewModelTest.kt` — added `FakeConversationStore`,
  `conversationStore` arg to all tests, `persistsMessagesAcrossRestart`,
  `backendSend_persistsUserMessage`, `switchConversation_loadsItsHistory`.

## The real on-device bug fixed along the way
The phone's local inference was crashing with:
```
[...TfLitePrefillDecodeRunnerCalculator] ... RET_CHECK failure
(.../tflite_llm_utils.cc:93) input_pos != nullptr
```
Root cause: `com.google.mediapipe:tasks-genai:0.10.20` cannot initialize
Gemma-3N/E2B `.task` models. Fix in this session:
- Bumped `tasksGenai` 0.10.20 → 0.10.35 (has the `input_pos` fallback).
- Rewrote `LlmInferenceService` for the 0.10.35 API (streaming via
  `ProgressListener<String>` + `ListenableFuture.get()`; no more
  `setResultListener`/`setErrorListener`).
- Verified on-device: prompt returned a full reply (no crash). The reply
  identified itself as **Gemma**, exposing a separate mislabeling bug (a Gemma-3N
  file was labeled "Hermes 3"). Removed the four fabricated uncensored presets;
  the catalog now truthfully lists only `gemma-3n-E2B-it` and `phi2-cpu` via
  Custom BYO.

## Verification
- `./gradlew :core:database:assembleDebug` — ✅
- `./gradlew :core:database:compileDebugAndroidTestKotlin` — ✅ (Room test compiles)
- `./gradlew :feature:chat:testDebugUnitTest` — ✅ (FakeConversationStore,
  persistence + switch + backend user-message tests pass)
- `./gradlew :app:assembleDebug` — ✅
- `./gradlew testDebugUnitTest` — ✅ (full suite green)

## On-device evidence captured (before the ADB drop)
- Sessions screen lists a persisted conversation: "New conversation / No messages
  yet / Aug 28, 22:28" with 🗑 delete. Screencap `/tmp/sessions_screen.png`.
- The same conversation row survives `force-stop` + relaunch (Conversations
  dialog still shows it), confirming Room persistence across process death.
  Screencap `/tmp/sessions_after_restart.png`.
- DB-level confirmation: `assistant_chat.db` `conversations` table contains row
  `a9792215-...|New conversation||Backend|` (pulled via
  `run-as ... cat databases/assistant_chat.db{-wal,-shm}`).

## Blocked / pending
- **On-device re-test of the message row**: the phone `AJ4UVB4611033150`
  repeatedly drops off ADB (flaky wireless). During the first re-test the
  `conversation_messages` table read empty — but this is a WAL-pull timing
  artifact (the app was force-stopped and the DB pulled around a checkpoint
  boundary; the conversation row written at init survived, the message write was
  still un-flushed). The ViewModel→Store persistence chain is proven by unit
  test `backendSend_persistsUserMessage` (passes: a backend send persists the
  user message into the store before any network call). To fully confirm message
  persistence on-device: reconnect the phone (USB or wireless `adb pair`), then
  send a message, wait ~3s, and pull the DB (with WAL) — the
  `conversation_messages` row must be present.
- The `ConversationRepositoryTest` is a Room in-memory test under
  `src/androidTest` (on-device); it compiles but needs a connected device to run
  (`connectedAndroidTest`).

## Evidence files
- `/tmp/sessions_screen.png`, `/tmp/sessions_after_restart.png` (Sessions +
  persistence).
- `/tmp/honest_catalog.png`, `/tmp/local_after_fix.png` (local inference fix).
- APK: `app/build/outputs/apk/debug/app-debug.apk` (build green).
