# ADR-012 Option C — deepen on-device mode (not Chaquopy)

**Verdict for this repo: Option C is the ship path.** Option A is a documented
no-go in [`chaquopy-wheels.md`](./chaquopy-wheels.md). Option B (rewrite the
FastAPI backend in Kotlin/Ktor inside the APK) is out of scope.

This spike maps what `:feature:localmodel` already does versus the remote
`ChatApiClient` / SSE path, names the gaps, and records the shippable
deepening: a shared `ChatTransport` seam so chat can use MediaPipe without
the remote LLM server, plus **P1 local reminders** (Room + WorkManager) so
“remind me…” works with no FastAPI scheduler and no FCM.

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
provider key or Google refresh tokens (ADR-005, ADR-007). Cloud reminder
*delivery* is still FCM from the VPS (phase 02.5).

```
ChatScreen ─► ChatViewModel ─► RemoteChatTransport ─► ChatApiClient
                                      │
                                      ▼
                               SseFrameCodec ─► ChatEvent ─► ChatReducer ─► UI
```

## On-device path after this slice

```
ChatScreen ─► ChatViewModel ─► LocalChatTransport
                                      │
                    ┌─────────────────┴──────────────────┐
                    ▼                                    ▼
         LocalReminderGateway                  LlmInferenceService
         (regex parser, Room,                  (MediaPipe, needs
          WorkManager)                          downloaded .task)
                    │                                    │
                    └──────────► ChatEvent ──────────────┘
                                      │
                                      ▼
                               ChatReducer ─► UI  (same fold as SSE)
```

Reminder create/list/cancel is **not** an LLM tool loop. Small MediaPipe
models hallucinate due times, so `LocalReminderParser` matches a short
English grammar before inference. Unrelated turns still go to MediaPipe.
Calendar / Gmail / SMS still have no in-process executor — that would be
Option B / P2.

## Gap matrix

| Capability | Offline / on-device today | Still needs hosted FastAPI | ADR-013 attach |
|---|---|---|---|
| LLM chat tokens | **Yes**, after a MediaPipe `.task` is downloaded (download itself needs network once) | No | — |
| Conversation history on device | **Yes**, Room (`ConversationStore`). Local turns now include last-N messages in the MediaPipe prompt | Server thread list / resume still `GET /v1/threads` when a bearer exists | — |
| Tool loop (remember, calendar, gmail, SMS skill) | **No** | **Yes** — those tools run inside the SSE turn | Not Option C's job to reimplement |
| Google OAuth / Calendar / Gmail | **No** on the phone | **Yes** — confidential client + encrypted tokens on the VPS | **O1** thin relay on `assistant.llmclouds.au`. Phone still opens a Custom Tab at `/oauth/google/start`. **Never** a `client_secret` in the APK |
| Reminder *creation* | **Yes (P1)** — Room `reminders` table + regex parser on the local chat path. Does **not** need model weights | Cloud mode still uses the backend `create_reminder` tool | Local store, not a Google API |
| Reminder *delivery* while offline | **Yes (P1)** — WorkManager unique work + optional `AlarmManager.setExactAndAllowWhileIdle`; `NotificationManager` on channel `sable_reminders_v2` | Cloud mode still uses FCM from the VPS. Local mode does **not** register or wait on FCM for self-delivery | **P1** |
| SMS send/read | **No** on-device as a tool | Today: FCM → phone SMS APIs → `POST /v1/sms/results` | **P2** in-process SMS when tools run on-device |
| Memory (user_facts / search_past_conversations) | **No** | **Yes** | Unchanged |
| App entry without bearer | **Yes** — onboarding "Continue on-device without the server" | Cloud chips / Google Connect still require a token | — |

## First shippable deepening (ChatTransport + skip-cloud)

1. **`ChatTransport` seam** in `:backend-client` — `RemoteChatTransport` wraps
   `ChatApiClient` + `SseFrameCodec`; `LocalChatTransport` in
   `:feature:localmodel` wraps MediaPipe and emits the same `ChatEvent`s.
   `ChatViewModel.sendMessage` picks a transport; it does not have two UI
   mutation paths.
2. **On-device prompt** (`LocalPromptBuilder`) injects a reduced-assistant
   preamble and the recent transcript so the local model is not amnesiac and
   is told not to fake calendar/email/SMS (reminders are handled before the
   prompt).
3. **Onboarding skip** — enter the app without `/v1/health`. Sets
   `BearerTokenRepository.on_device_access` and forces `AppModelMode.OnDevice`.
4. **Honest UX** — chat banner, settings capability card (O1/P1/P2), Google
   Connect disabled without a cloud session, cloud mode refused without a
   bearer token.
5. **Engine reuse** — `LlmInferenceService` caches `LlmInference` across turns
   for the same model path; `stopGenerating` closes it.

## P1 now covers (WorkManager reminders)

Shipped on the same Option C branch:

1. **Local store** — Room table `reminders` on `ChatDatabase` v2
   (`MIGRATION_1_2`). `ReminderStore` create / list pending / cancel / mark
   fired. Fired is a compare-and-swap (`status = pending` → `fired`) so
   WorkManager and the exact-alarm receiver cannot notify twice.
2. **Create / list / cancel from local chat** — `LocalReminderParser` then
   `DefaultLocalReminderGateway`. Matches “remind me to … in 30 minutes”,
   “tomorrow at 9am”, “at 3pm”, “what are my reminders”, “cancel reminder …”.
   Emits the same `tool_call_started` / `finished` + spoken delta the cloud
   tool loop uses. **No model download required.** Unrelated chat still needs
   weights; calendar/Gmail/SMS stay an honest “not installed / switch to
   Cloud Assistant” failure.
3. **Due-time delivery** — `WorkManagerReminderScheduler` enqueues
   `OneTimeWorkRequest<LocalReminderWorker>` as unique work
   `local-reminder-{id}` with `ExistingWorkPolicy.REPLACE` and an initial
   delay of `dueAt - now`. If `SCHEDULE_EXACT_ALARM` is granted,
   `AlarmManager.setExactAndAllowWhileIdle` also fires
   `ReminderAlarmReceiver`, which enqueues the **same** unique work with
   delay 0. `LocalReminderFiring` de-dupes. `AndroidReminderNotifier` posts
   a high-importance notification on `sable_reminders_v2` (same channel as
   FCM, so cloud and local share sound/importance).
4. **Cloud FCM path unchanged** — `AssistantMessagingService` still shows
   backend-pushed reminders when a bearer is present. Local mode does not
   call `DeviceTokenRegistrar` without a token, and does not need FCM to
   deliver its own due reminders.
5. **Permissions / process death** — `POST_NOTIFICATIONS` (requested when
   on-device chat is shown), `SCHEDULE_EXACT_ALARM` (optional; approximate
   WorkManager delay remains if the user denies exact alarms). `App`
   implements `Configuration.Provider` + `HiltWorkerFactory` and re-binds
   pending rows on start.

What this is **not**: a full assistant in the APK. Calendar, Gmail, SMS, and
memory still degrade honestly. There is still no Google `client_secret` in
the APK.

## How to verify

### Unit tests (this environment)

```bash
./gradlew :core:model:test \
          :core:database:testDebugUnitTest \
          :backend-client:testDebugUnitTest \
          :feature:localmodel:testDebugUnitTest \
          :feature:chat:testDebugUnitTest \
          :app:testDebugUnitTest \
          :app:assembleDebug
```

Load-bearing cases:

- `LocalReminderParserTest` — duration / tomorrow-at / at-time / list / cancel / needs-when
- `LocalReminderFiringTest` — first fire wins; cancel and unknown id are no-ops
- `LocalReminderGatewayTest` — create schedules, list reads store, cancel unschedules
- `LocalChatTransportTest.reminderCreateWorksWithoutModelWeights`
- `LocalChatTransportTest.calendarWithoutModelIsHonestNotInstalledError`
- `ChatViewModelTest.onDeviceReminderCreate_worksWithoutLocalModel`
- `LocalReminderWorkTest` — unique work name + shared FCM channel id
- Existing Option C cases: `RemoteChatTransportTest`, `LocalPromptBuilderTest`,
  `ChatViewModelTest.onDeviceSend_streamsThroughChatReducerAndPersistsAssistant`,
  `onDeviceAccessWithoutToken_startsInOnDeviceMode`,
  `setBackendModeWithoutToken_staysOffCloud`

WorkManager / `AlarmManager` themselves are not exercised in JVM unit tests
(no Robolectric worker driver here). The worker body is `LocalReminderFiring`,
which is unit-tested; unique-name / channel contracts are unit-tested.

### Sideload (Declan / device)

This cloud agent has no phone. On hardware:

1. `./gradlew :app:assembleDebug` and `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. Cold start → onboarding → **Continue on-device without the server** (do not
   paste a bearer). App should land in chat, On-Device mode, download dialog.
   Dismiss the dialog if you only want reminders (they do not need weights).
3. Allow notifications if Android 13+ prompts. Optional: Settings → Alarms &
   reminders → allow exact alarms for Sable (otherwise WorkManager may fire a
   few minutes late in Doze).
4. Send **Remind me to stretch in 30 minutes** (or tap the empty-state chip).
   Chat should show a `create_reminder` chip and a confirmation that this is
   on-device WorkManager, **no** `POST /v1/chat`, **no** FCM register.
5. Shorten the wait: `adb shell cmd jobscheduler run -f com.mdyerapis.sable <job-id>`
   is OEM-specific; easier: “remind me to ping in 15 seconds”, leave the app,
   expect a high-importance **Reminder** notification. Tap it → MainActivity.
6. “what are my reminders” lists pending rows; “cancel reminder ping” removes
   the work. A cancelled reminder must not notify.
7. Airplane mode after creating a 15-second reminder: it must still fire
   locally. Cloud Calendar / Gmail questions still refuse.
8. Download a catalog `.task` (needs network once; Gemma 3n is ~3 GB). After
   `LocalModelState.Ready`, send "Say hello". Tokens should stream with **no**
   call to `POST /v1/chat`.
9. Settings → **Connect cloud assistant** still reaches bearer onboarding.
   After a token, Cloud Assistant + FCM reminders work as before. Switching
   back to On-Device LLM must not send `local:` conversation ids to `/v1/chat`.

### What you will not see

- A Google `client_secret` anywhere in the APK
- Chaquopy / Option A
- SMS tools without the FCM relay (until P2)
- On-device calendar or Gmail (O1 stays the relay)
- Local-mode reminder delivery via FCM (P1 is WorkManager + `NotificationManager`)

## Follow-ups (not this slice)

| ID | Work |
|---|---|
| O1 (backend + a thin Android Custom Tab already exists) | Keep OAuth on `assistant.llmclouds.au`; do not add PKCE-with-secret on device |
| P1 | **Done on this branch** — WorkManager + local notifications + Room create/list/cancel |
| P2 | In-process SMS when/if a local tool loop exists — still not Option B |
| Later Option C | Optional tiny on-device memory store; do not scrape Hugging Face for GGUF (engine cannot run them) |
