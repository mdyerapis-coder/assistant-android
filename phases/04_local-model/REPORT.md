# Phase 04 — Local Model (on-device inference) Report

## What was built

1. **MediaPipe Tasks GenAI Integration (`:feature:localmodel`)**:
   - Added `com.google.mediapipe:tasks-genai:0.10.20` dependency.
   - Built `:feature:localmodel` module with `LocalModelState`, `LocalModelSpec`, and `LocalModelInfo`.
   - `LlmInferenceService`: Singleton service wrapping `LlmInference` with asynchronous token streaming callbacks (`onPartial`), coroutine cancellation, and non-blocking background dispatch.

2. **Multi-Model Catalog & Download Manager (`LocalModelRepository`)**:
   - Multi-model catalog supporting `Gemma 3 1B-IT`, `Gemma 2 2B-IT`, `Qwen 2.5 1.5B`, and custom user-provided URLs/models.
   - Streaming download manager with byte-level progress reporting and SHA-256 integrity verification.
   - Model selection, switching, and deletion lifecycle.
   - Isolated `downloadClient` preventing internal backend auth tokens from leaking to external CDNs / model hosts (fixing 401 on external CDNs).
   - Domain-scoped `BearerAuthInterceptor` in `core:network`.

3. **Chat & UI Integration (`:feature:chat`)**:
   - `ModelPreferenceRepository` extended with `AppModelMode { Backend, OnDevice }` persistence.
   - `ChatViewModel` streaming reducer dispatching to local inference when `OnDevice` is active and skipping backend API calls.
   - `ChatScreen` mode switcher (`CLOUD` vs `ON DEVICE`), installed model selector dropdown, and comprehensive multi-model management dialog.

## Evidence

1. **Automated Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```
   Output: `BUILD SUCCESSFUL` across all modules (including `LocalModelRepositoryTest` multi-model tests and `ChatViewModelTest` mode-switch tests).

2. **On-Device Installation & Verification**:
   - Built and installed `app-debug.apk` onto connected device `AJ4UVB4611033150` (HONOR ELI-NX9).
   - Captured screencaps verifying mode toggle, model status, and the multi-model download catalog UI on device.
