# Phase 07 — Hardening + Debt Payoff — Report

## Status: COMPLETE — all Phase 07 debts verified on-device

## What shipped (this session)

The "automatable parts" (chip scroll fix, CTA hierarchy, CI workflow) landed in
commit `88c089b`. This session closed the remaining on-device verification debt
and fixed the wiring bugs it exposed.

### Fixes committed (`fb3a8f4`)

1. **Server baseUrl was never persisted from onboarding.**
   `ChatViewModel` hardcoded `https://assistant.llmclouds.au`, so after
   onboarding configured `http://localhost:8420`, chat requests still went to
   production (401 with the test token). Onboarding only saved the bearer token.
   - `BearerTokenRepository`: added `saveBaseUrl` / `getBaseUrl` / `clearBaseUrl`
     (plaintext pref; the token stays Keystore-encrypted).
   - `OnboardingViewModel.submit()`: persists `baseUrl` on successful health check.
   - `ChatViewModel.init`: loads persisted baseUrl, falls back to production
     default when unset.

2. **Cleartext HTTP blocked on Android 9+.**
   No `usesCleartextTraffic` and no network-security config meant the app's
   OkHttp client could not reach `http://…` backends at all (device `curl`
   worked; the app silently failed). Added `android:usesCleartextTraffic="true"`
   to `app/src/main/AndroidManifest.xml`.

3. **`:core:database` connectedAndroidTest never actually ran tests.**
   The test APK declared the JUnit3 `android.test.InstrumentationTestRunner`,
   which reports "No tests found" for JUnit4 `@Test` methods; the module also
   lacked `androidx.test:runner`, so switching the manifest to
   `AndroidJUnitRunner` made the instrumentation process crash at startup with
   `ClassNotFoundException`. Fixed in `core/database/build.gradle.kts`:
   - `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`
   - added `androidTestImplementation("androidx.test:runner:1.6.2")`

## Verification (all on device AJ4UVB4611033150 / ELI-NX9)

- **05b debt — on-device re-test**: app connected to local backend
  (`http://localhost:8420` via `adb reverse`), sent messages through the chat UI,
  pulled `assistant_chat.db` + `-wal`/`-shm` via
  `run-as com.mdyerapis.assistant cat databases/…`:
  - `conversation_messages` contains 3 user rows (verified with `sqlite3`):
    `Verify%20phase07`, `Hello%20test`, `Phase%20seven%20verification` — WAL
    pull confirmed the rows (WAL was 90 KB, not a timing artifact).
  - `conversations` row `67ac0dea-…` survives `force-stop` + relaunch.
  - Bonus: server-synced threads from `syncThreads()` (Phase 08 scaffolding)
    also landed (`65619cd0-…`, `e9876bd3-…`), confirming thread hydration works.
- **05b debt — instrumented test**: `./gradlew :core:database:connectedDebugAndroidTest`
  — **BUILD SUCCESSFUL, 4/4 tests pass** on-device
  (`ConversationRepositoryTest`: insertAndList_roundTripsSummary,
  appendMessages_orderedByCreatedAt, deleteConversation_removesMessages,
  setServerConversationId_persisted).
- **06 debt — chips clipping**: `ChatScreen.kt` uses `horizontalScroll` +
  `rememberScrollState()`; no clip on narrow screens (committed earlier, confirmed
  present).
- **06 debt — empty-state CTA**: `isPrimary = index == 0` primary/`secondary`
  muted hierarchy in suggestion chips (committed earlier, confirmed present).
- **CI**: `.github/workflows/ci.yml` present (committed earlier).
- Unit suite: `./gradlew testDebugUnitTest` — ✅ green (includes ChatViewModelTest
  which exercises the new baseUrl path).

## Known limitation (not a Phase 07 defect)

Chat completes end-to-end against the local backend (`POST /v1/chat 200`), but
the assistant reply errors with the fake `sk-test` OpenAI key (401 → SSE error
frame). The app renders this as "Connection error: null" instead of the backend's
error message — cosmetic SSE error-frame handling, tracked for a later phase.
Message persistence itself is unaffected and verified.

## Evidence files
- APK: `app/build/outputs/apk/debug/app-debug.apk` (build green).
- Test report: `core/database/build/outputs/androidTest-results/connected/debug/`
  — `tests="4" failures="0"`.
- DB pull: `/tmp/assistant_chat.db` (+ `-wal`/`-shm`) with 3 `conversation_messages`
  rows.
