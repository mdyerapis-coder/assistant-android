# ADR-012 Option C — deepen on-device mode (not Chaquopy)

**Verdict for this repo: Option C is the ship path.** Option A is a documented
no-go in [`chaquopy-wheels.md`](./chaquopy-wheels.md). Option B (rewrite the
FastAPI backend in Kotlin/Ktor inside the APK) is out of scope.

This spike maps what `:feature:localmodel` already does versus the remote
`ChatApiClient` / SSE path, names the gaps, and records the first shippable
deepening: a shared `ChatTransport` seam so chat can use MediaPipe without
the remote LLM server, with honest UX when tools still need the hosted
backend.

Source ADRs (backend repo, not copied here):

- [012-embedded-backend-in-apk.md](https://github.com/mdyerapis-coder/assistant-backend/blob/master/docs/adr/012-embedded-backend-in-apk.md)
- ADR-013 (locked by Declan): **O1** thin Google OAuth relay on
  `assistant.llmclouds.au`; **P1** WorkManager + local notifications for
  reminder/automation delivery when offline; **P2** in-process SMS when tools
  run on-device.

Non-goals (explicit):

- Do **not** reintroduce Chaquopy or depend on `:spikes:chaquopy-wheels`.
- Do **not** implement Option B (full Kotlin backend-in-APK).
- Do **not** put a Google `client_secret` on the device (ADR-007 / O1).

## What already worked before this slice (phase 04)

`:feature:localmodel` is a real MediaPipe GenAI module (`tasks-genai` in
`feature/localmodel/build.gradle.kts`), not a stub:

| Piece | Today |
|---|---|
| Model catalog / download | `LocalModelRepository` streams `.task` / `.bin` weights to `filesDir/models/`, SHA-256 optional, isolated OkHttp client so the backend bearer is **not** sent to Hugging Face |
| Engine | `LlmInferenceService` wraps `LlmInference.generateResponseAsync` and streams incremental tokens |
| Mode flag | `AppModelMode { Backend, OnDevice }` in `ModelPreferenceRepository` |
| Chat routing | `ChatViewModel.sendMessage` used to call `LlmInferenceService.generate` directly when `OnDevice`, skipping `ChatApiClient` |
| UI | Settings chips (Cloud / On-Device), download dialog, chat empty-state copy, composer placeholder |

That was enough to *talk to a local model after a cloud onboarding*. It was
not enough to treat on-device as a first-class transport, or to open the app
without a live FastAPI health check.

## Remote path (unchanged contract)

`POST /v1/chat` bearer-authed SSE, frames in `docs/CONTRACT.md`, parsed by
`SseFrameCodec`, folded by `ChatReducer`. Tool calls (`tool_call_started` /
`progress` / `finished`) execute **on the server**. The phone never holds the
provider key or Google refresh tokens (ADR-005, ADR-007).

```
ChatScreen ─► ChatViewModel ─► RemoteChatTransport ─► ChatApiClient
                                      │
                                      ▼
                               SseFrameCodec ─► ChatEvent ─► ChatReducer ─► UI
```

## On-device path after this slice

```
ChatScreen ─► ChatViewModel ─► LocalChatTransport ─► LlmInferenceService (MediaPipe)
                                      │
                                      ▼
                               ChatEvent.Delta / MessageCompleted / Error
                                      │
                                      ▼
                               ChatReducer ─► UI  (same fold as SSE)
```

`LocalChatTransport` never emits `tool_call_*`. There is no in-process tool
executor — that would be Option B.

## Gap matrix

| Capability | Offline / on-device today | Still needs hosted FastAPI | ADR-013 attach |
|---|---|---|---|
| LLM chat tokens | **Yes**, after a MediaPipe `.task` is downloaded (download itself needs network once) | No | — |
| Conversation history on device | **Yes**, Room (`ConversationStore`). Local turns now include last-N messages in the MediaPipe prompt | Server thread list / resume still `GET /v1/threads` when a bearer exists | — |
| Tool loop (remember, reminders, calendar, gmail, SMS skill) | **No** | **Yes** — tools run inside the SSE turn | Not Option C's job to reimplement |
| Google OAuth / Calendar / Gmail | **No** on the phone | **Yes** — confidential client + encrypted tokens on the VPS | **O1** thin relay on `assistant.llmclouds.au`. Phone still opens a Custom Tab at `/oauth/google/start`. **Never** a `client_secret` in the APK |
| Reminder *creation* | **No** (it is a backend tool) | **Yes** | Stays server-side until a local store exists |
| Reminder *delivery* while offline | **No** (FCM from the VPS, phase 02.5) | Push needs the server reachable at fire time | **P1** WorkManager + local notifications |
| SMS send/read | **No** on-device as a tool | Today: FCM → phone SMS APIs → `POST /v1/sms/results` | **P2** in-process SMS when tools run on-device |
| Memory (user_facts / search_past_conversations) | **No** | **Yes** | Unchanged |
| App entry without bearer | **Yes, this slice** — onboarding "Continue on-device without the server" | Cloud chips / Google Connect still require a token | — |

## First shippable deepening (this PR)

1. **`ChatTransport` seam** in `:backend-client` — `RemoteChatTransport` wraps
   `ChatApiClient` + `SseFrameCodec`; `LocalChatTransport` in
   `:feature:localmodel` wraps MediaPipe and emits the same `ChatEvent`s.
   `ChatViewModel.sendMessage` picks a transport; it does not have two UI
   mutation paths.
2. **On-device prompt** (`LocalPromptBuilder`) injects a reduced-assistant
   preamble and the recent transcript so the local model is not amnesiac and
   is told not to fake tools.
3. **Onboarding skip** — enter the app without `/v1/health`. Sets
   `BearerTokenRepository.on_device_access` and forces `AppModelMode.OnDevice`.
4. **Honest UX** — chat banner, settings capability card (O1/P1/P2), Google
   Connect disabled without a cloud session, cloud mode refused without a
   bearer token.
5. **Engine reuse** — `LlmInferenceService` caches `LlmInference` across turns
   for the same model path; `stopGenerating` closes it.

What this is **not**: a full assistant in the APK. Tools degrade: the model is
instructed to say so; the UI says so; Google/reminders/SMS stay on the relay
plan.

## How to verify

### Unit tests (this environment)

```bash
./gradlew :backend-client:testDebugUnitTest \
          :feature:localmodel:testDebugUnitTest \
          :feature:chat:testDebugUnitTest
```

Load-bearing cases:

- `RemoteChatTransportTest` — SSE frames → `ChatEvent`; HTTP 503 → retryable error
- `LocalPromptBuilderTest` / `LocalChatTransportTest` — preamble + history;
  no `tool_call_*`; dummy `.task` file + fake `LlmInferenceService` streams deltas
- `ChatViewModelTest.onDeviceSend_streamsThroughChatReducerAndPersistsAssistant`
- `ChatViewModelTest.onDeviceAccessWithoutToken_startsInOnDeviceMode`
- `ChatViewModelTest.setBackendModeWithoutToken_staysOffCloud`

### Sideload (Declan / device)

This cloud agent has no phone. On hardware:

1. `./gradlew :app:assembleDebug` and `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. Cold start → onboarding → **Continue on-device without the server** (do not
   paste a bearer). App should land in chat, On-Device mode, download dialog.
3. Download a catalog `.task` (needs network once; Gemma 3n is ~3 GB). After
   `LocalModelState.Ready`, send "Say hello". Tokens should stream with **no**
   call to `POST /v1/chat` (logcat / mitm / airplane mode after download).
4. Ask "what's on my calendar?" — the model should refuse / redirect to Cloud
   Assistant; the on-device banner stays visible; Google Connect is disabled.
5. Settings → **Connect cloud assistant** still reaches bearer onboarding.
   After a token, Cloud Assistant + tools work as before. Switching back to
   On-Device LLM must not send `local:` conversation ids to `/v1/chat`.

### What you will not see

- Tool-call chips in on-device turns
- Reminders firing without the VPS (until P1)
- SMS tools without the FCM relay (until P2)
- A Google `client_secret` anywhere in the APK

## Follow-ups (not this slice)

| ID | Work |
|---|---|
| O1 (backend + a thin Android Custom Tab already exists) | Keep OAuth on `assistant.llmclouds.au`; do not add PKCE-with-secret on device |
| P1 | WorkManager + local notifications for reminder delivery when the VPS cannot push |
| P2 | In-process SMS when/if a local tool loop exists — still not Option B |
| Later Option C | Optional tiny on-device memory store; do not scrape Hugging Face for GGUF (engine cannot run them) |
