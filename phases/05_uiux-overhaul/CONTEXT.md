# Phase 05 — UI/UX Overhaul (full design-system revamp)

Execution spec transcribed from `v2-feature-program-plan.md` (approved). ICM convention: this file is the execution spec; `REPORT.md` carries completed evidence.

## Does

Comprehensive design overhaul replacing default Material 3 dynamic styling with a bespoke, cohesive visual identity (`AssistantPalette`: neutral slate surfaces, deep teal `#0FA4AB` accent, polished elevation, refined chat bubbles, animated tool status pills, rounded composer FAB, and a dedicated Settings screen).

**Constraint: Zero behavior changes** — `ChatReducer`, `ChatApiClient`, `SseFrameCodec`, and the underlying ViewModel state reducers remain untouched.

- **Theme (`core:designsystem/theme/`)**:
  - `Color.kt`: `LightColorScheme` (slate `#F4F6F8` background, surface `#FFFFFF`, surfaceVariant `#E8ECEF`, primary `#0FA4AB`, onPrimary `#FFFFFF`, secondary `#2C3E50`) and `DarkColorScheme` (background `#101418`, surface `#181E24`, surfaceVariant `#232B32`, primary `#0FA4AB`, onPrimary `#FFFFFF`, secondary `#8EADC8`).
  - `Type.kt`: tailored typography tokens for chat readability.
  - `Theme.kt`: fixed `AssistantTheme` disabling dynamic OS colors (`dynamicColor = false`).
- **Components (`core:designsystem/components/`)**:
  - `MessageBubble.kt`: User bubbles (8dp top-right / 20dp other corners), Assistant bubbles (20dp top-left / 8dp other corners), 84% max width, refined padding.
  - `MessageContent.kt`: formatted text renderer.
  - `ToolCallChip.kt`: pill surface with pulsing dots (`Started`), shimmer (`Progress`), checkmark (`Finished`), and alert badge (`Error`).
  - `StateComponents.kt`: consistent loading/error/empty state cards.
  - `Composer.kt`: rounded-24 input field with embedded send FAB, focus elevation, and IME padding.
  - `ModelStatusChip.kt`: top bar pill showing active cloud model or `ON DEVICE` with navigation to Settings.
- **Screens**:
  - `ChatScreen.kt`: Edge-to-edge TopAppBar with `ModelStatusChip` + Settings icon, messages LazyColumn, and bottom `Composer`.
  - `OnboardingScreen.kt`: Brand hero card, token paste field, clean error states.
  - `SettingsScreen.kt`: Dedicated settings screen providing backend model selector, on-device model management, Google account linkage, and app details.
  - `AppNavHost`: Navigation route `settings` wired to `SettingsScreen`.

## Verification

- `./gradlew testDebugUnitTest` remains green across all modules.
- `./gradlew :app:assembleDebug` succeeds and installs on `AJ4UVB4611033150`.
- Screencaps captured:
  1. Polished chat screen with `ModelStatusChip` and custom message bubbles.
  2. Dedicated Settings screen with model selector, on-device model manager, and Google account status.
  3. Redesigned Onboarding screen.
