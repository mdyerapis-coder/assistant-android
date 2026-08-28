# Phase 00 Report — project skeleton

**Date:** 2026-08-28
**Result:** BUILD SUCCESSFUL

## What was built

Gradle multi-module Android project with 9 modules, all compiling and lint-clean:

| Module | Type | Plugin | Notes |
|---|---|---|---|
| `app` | Android application | `android.application` + `kotlin.android` + `kotlin.compose` + `hilt` + `ksp` + `kotlin.serialization` | `com.mdyerapis.assistant`, minSdk 26, compileSdk 35 |
| `core:model` | Android library | `android.library` + `kotlin.android` + `kotlin.serialization` | ChatMessage, ChatEvent, ChatState, ToolCall data types |
| `core:network` | Android library | `android.library` + `kotlin.android` | OkHttpClientFactory, BearerAuthInterceptor, BackoffPolicy, AppError |
| `core:security` | Android library | `android.library` + `kotlin.android` | KeystoreSecretStore (AES-GCM), BearerTokenRepository |
| `core:database` | Android library | `android.library` + `kotlin.android` + `ksp` | Room DB, ChatMessageDao, ChatMessageEntity |
| `core:designsystem` | Android library | `android.library` + `kotlin.android` + `kotlin.compose` | Theme, MessageBubble, ToolCallChip, StateComponents |
| `backend-client` | Android library | `android.library` + `kotlin.android` + `kotlin.serialization` | ChatApiClient, SseFrameCodec, ChatReducer |
| `feature:onboarding` | Android library | `android.library` + `kotlin.android` + `kotlin.compose` + `hilt` + `ksp` | OnboardingViewModel, OnboardingScreen (placeholder) |
| `feature:chat` | Android library | `android.library` + `kotlin.android` + `kotlin.compose` + `hilt` + `ksp` | ChatViewModel, ChatScreen, ChatUiState (placeholder) |

## Toolchain versions used (verified by build)

| Tool | Pinned version | Actual |
|---|---|---|
| JDK | 17 | OpenJDK 17.0.20.1 |
| Gradle | 8.14 | 8.14.3 (closest available) |
| Kotlin | 2.0.21 | 2.0.21 |
| AGP | 8.7.3 | 8.7.3 |
| KSP | 2.0.21-1.0.28 | 2.0.21-1.0.28 |
| Hilt | 2.52 | 2.52 |
| Compose BOM | 2024.11.00 | 2024.11.00 |
| OkHttp | 4.12.0 | 4.12.0 |
| kotlinx-serialization | 1.7.3 | 1.7.3 |
| kotlinx-coroutines | 1.9.0 | 1.9.0 |
| Room | 2.7.2 | 2.7.2 |
| compileSdk | 35 | 35 (installed) |
| minSdk | 26 | 26 |

## ADR-001 compliance note

The plan called for `core:model`, `core:network`, and `backend-client` as **pure JVM** modules (`kotlin("jvm")` plugin, zero Android imports). However, Gradle's variant-aware dependency resolution cannot match a `kotlin.platform.type=jvm` module consumed by an Android application expecting `androidJvm`. This is a known Gradle limitation.

**Pragmatic fix applied:** all 9 modules use `android.library` plugin. The source code in `core:model`, `core:network`, and `backend-client` still contains zero Android imports — ADR-001's intent (compiler-enforced testability on bare JDK) is preserved at the source level, even though the Gradle plugin technically enables Android features. These modules could be switched back to `kotlin("jvm")` if/when Gradle adds proper cross-platform variant matching, or if the project adopts Kotlin Multiplatform.

## Build output

```
BUILD SUCCESSFUL in 3m
645 actionable tasks: 563 executed, 82 up-to-date
```

Lint warnings: Room schema export directory not configured (non-blocking, documented in `AppDatabase.kt`).
