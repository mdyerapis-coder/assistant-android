# Phase 04 — Local Model (on-device inference)

Execution spec transcribed from `v2-feature-program-plan.md` (approved). ICM convention: this file is the execution spec; `REPORT.md` carries completed evidence. Independent of backend track.

## Does

Adds on-device LLM inference to the Android app using Google MediaPipe Tasks GenAI runtime and lightweight Gemma task models (downloaded on-demand, keeping APK small).

- **Dependency**: `com.google.mediapipe:tasks-genai` in `gradle/libs.versions.toml`.
- New module `:feature:localmodel`:
  - `LocalModelState` sealed hierarchy: `NotInstalled`, `Downloading(progress: Float)`, `Ready(path: String)`, `Error(message: String)`.
  - `LocalModelRepository` (@Singleton): manages model downloads via OkHttp streaming with progress and optional SHA-256 verification to `filesDir/models/gemma-3-1b-it.task`. Persists state via SharedPreferences / DataStore.
  - `LlmInferenceService` (@Singleton): wraps `com.google.mediapipe.tasks.genai.llminference.LlmInference`, generates streamed completions via `generateResponseAsync` on `Dispatchers.Default`, cancels generation on request, emits tokens through `onPartial: (String) -> Unit`.
- `feature:chat` integration:
  - `ModelPreferenceRepository` extended with `appModelMode: Flow<AppModelMode>` (`enum AppModelMode { Backend, OnDevice }`).
  - `ChatViewModel`: when mode is `OnDevice`, routes user messages directly to `LlmInferenceService.generate`, streaming tokens into `ChatUiState.currentContent` (reusing existing UI streaming mechanism). When `NotInstalled`, prompts user to install.
  - In-memory messages for on-device conversational sessions (no Room persistence for local mode; tool execution stays backend-mediated).
- Entry UI: Top-bar chip to toggle `OnDevice` vs `Backend` and a model download dialog with progress indicator.

## Verification

- `./gradlew :feature:localmodel:testDebugUnitTest :feature:chat:testDebugUnitTest` passes.
- `LocalModelRepositoryTest` exercises download streaming, progress emission, SHA-256 verification, and error handling.
- `./gradlew :app:assembleDebug` succeeds and installs on `AJ4UVB4611033150`.
- Screencaps / ADB execution verifying mode toggle, model download flow, and on-device chat streaming.

## Non-goals

- No tool calling in local mode (conversational only; tools and memory remain backend-mediated).
- No model weights bundled in the APK (weights downloaded at runtime).
