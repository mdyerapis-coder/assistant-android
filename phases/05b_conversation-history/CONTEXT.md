# Phase 05b — Conversation History (Room DB)

## Goal
Persist Android chat history so conversations survive process death, and add a
"Sessions" screen to browse, resume, and delete past conversations. Backed by a
new Room database isolated from the existing `AppDatabase`.

## Constraints
- Do NOT modify `AppDatabase`/`ChatMessageEntity`/`ChatMessageDao` (schema bump
  would be needed). All new tables live in a separate `ChatDatabase`.
- Do NOT touch `ChatReducer`, `ChatApiClient`, `SseFrameCodec` (Phase 05 UI/UX
  zero-regression rule).
- Keep `core:database` free of `feature:chat` dependencies. Mode is stored as a
  plain `String`; `ChatViewModel` maps to `AppModelMode`.

## Architecture
- **New tables** (in `core:database/chat/`):
  - `conversations` (`ConversationEntity`): id, title, preview, modelId, mode,
    serverConversationId, createdAt, updatedAt.
  - `conversation_messages` (`MessageEntity`): id (PK = ChatMessage.id, so
    persistence is idempotent via REPLACE), conversationId, role, content,
    toolCallId, toolName, toolArgsJson, toolResult, isError, createdAt.
- **`ChatDatabase`** (`@Database(version = 1, exportSchema = false)`), built with
  `Room.databaseBuilder(context, ..., "assistant_chat.db")`.
- **`ConversationDao`** / **`MessageDao`**: observe+CRUD Flow queries.
- **`ConversationRepository`** implements **`ConversationStore`** (interface so
  `ChatViewModel` is unit-testable without Room/Robolectric).
- **DI**: `DatabaseModule` provides `ChatDatabase` + `ConversationRepository`;
  `ConversationStoreModule` binds `ConversationRepository → ConversationStore`.

## ViewModel wiring
- `ChatViewModel` constructor gains `conversationStore: ConversationStore`.
- `init`: collects `conversationStore.conversations` into
  `uiState.availableSessions`; hydrates the most recent conversation (restores
  `serverConversationId` as backend `conversationId` and loads its messages), or
  lazily creates one via `ensureConversation()`.
- `sendMessage` (both on-device and backend): persists the user message, and on
  completion the assistant message. Backend also stores the server
  `conversationId` (so a later `switchConversation` resumes the server thread).
- New methods: `startNewConversation()`, `switchConversation(id)`,
  `deleteConversation(id)`.
- `ChatUiState` gains `availableSessions: List<ConversationSummary>` and
  `activeConversationId: String?`.

## UI
- **`SessionsScreen`** (new, `feature/chat`): lists conversations by
  `updatedAt DESC`, tap-to-open, swipe-style delete (delete dialog), "+ New".
- **`ChatScreen`** top bar gains a `📂` action → `sessions` route.
- **`AppNavHost`** adds the `sessions` composable and wires `onNavigateSessions`.
