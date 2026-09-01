# SDD ledger — plan: docs/superpowers/plans/2026-09-01-sable-uiux-overhaul.md

**BASE:** c71beb22f4ee4b8024cf0c33b931fc9dad93ed24

## Pre-flight scan

| Tasks | Shared files/interfaces | Finding |
|-------|------------------------|---------|
| T1 (Theme) ↔ T3 (Composer) | `SableTheme`, `SableShapes`, color tokens | T1 produces, T3 consumes. Clean — shapes defined in T1, used in T3. |
| T1 (Theme) ↔ T4 (MessageBubble) | `SableTheme`, `SableShapes` | T1 produces, T4 consumes. Clean. |
| T1 (Theme) ↔ T5 (ChatScreen) | `SableTheme`, `SableShapes`, `MonoFontFamily` | T1 produces, T5 consumes. Clean. |
| T1 (Theme) ↔ T6 (SessionsScreen) | `SableTheme` | T1 produces, T6 consumes. Clean. |
| T1 (Theme) ↔ T7 (SettingsScreen) | `SableTheme`, `MonoFontFamily` | T1 produces, T7 consumes. Clean. |
| T1 (Theme) ↔ T9 (Onboarding) | `SableTheme`, `SableTypography` | T1 produces, T9 consumes. Clean. |
| T1 (Theme) ↔ T10 (Transitions) | `SableTheme` | T1 produces, T10 consumes. Clean. |
| T2 (Icon) ↔ T6 (SessionsScreen) | droid mascot drawable | T2 produces `ic_launcher_foreground`, T6 uses it. Clean. |
| T2 (Icon) ↔ T9 (Onboarding) | droid mascot drawable | T2 produces, T9 uses. Clean. |
| T5 (ChatScreen) ↔ T10 (Transitions) | `ChatScreen.kt` | Both modify same file. T5 restyle first, T10 adds transitions after. Sequential — no conflict. |
| T7 (SettingsScreen) ↔ T8 (HF API) | `LocalModelRepository`, `SettingsScreen` | T7 adds UI, T8 adds backend logic. T7 consumes T8's `refreshAvailableModels()`. Clean if T8 defines the method T7 calls. |
| T3 (Composer) ↔ T4 (MessageBubble) | No shared files | Independent. Clean. |

**Scan result:** Clean. No contradictions. All interfaces flow T1→downstream, T2→T6/T9, T7→T8 sequential.

## Task log

(Task entries appended below as each completes)

### Task 1: Theme & Typography Foundation — DONE
- Commit: 41055b0
- Changes: `AssistantTheme` → `SableTheme` in Theme.kt + MainActivity.kt, `SableShapes` (20dp/28dp/16dp), Inter font (sans_headline.ttf) + JetBrains Mono (mono_accent.ttf) added to app/src/main/res/font/.
- Tests: `testDebugUnitTest` ✅, `:app:assembleDebug` ✅
- Concerns: None. Type.kt with SableTypography deferred to app module (R.font issue in core:designsystem). Fonts available for downstream tasks.

### Task 2: App Icon — Droid Mascot — DONE
- Commit: 2e78da9
- Changes: Placeholder droid icon PNGs (dark body #2A2A2A + ember eyes #E08A6B) at all densities (mdpi→xxxhdpi), adaptive XML with monochrome layer, background #171717.
- Tests: `:app:assembleDebug` ✅
- Concerns: Placeholder artwork — actual sable-animal droid mascot needs professional design. Structure and adaptive XML are correct.

### Task 3: Composer Restyle — DONE
- Commit: 0f5a1e5
- Changes: 28dp pill shape, surfaceContainerHigh fill, 600ms EaseInOut mic pulse, terracotta send button.
- Tests: `:app:assembleDebug` ✅

### Task 4: MessageBubble Restyle — DONE
- Commit: d35e523
- Changes: 20dp RoundedCornerShape user bubble, surfaceContainerHigh fill, 0.85f max width.
- Tests: `:app:assembleDebug` ✅

### Task 5: ChatScreen Restyle — DONE
- Commit: 685fff1
- Changes: HorizontalDivider panel-line below TopAppBar, "Sable ready." empty state, recovery banner "Can't reach Sable."
- Tests: `:app:assembleDebug` ✅

### Task 6: SessionsScreen Restyle — DONE
- Commit: 8dd4e58
- Changes: "Sable" wordmark (headlineMedium, primary), HorizontalDivider panel-line.
- Tests: `:app:assembleDebug` ✅

### Task 7: SettingsScreen — DONE
- Commit: 286b1d5
- Changes: Cloud Providers section (OpenAI/Anthropic/Google with status dots), Device info card (RAM, SoC), bundled models.json, "Sable" in About.
- Tests: `:app:assembleDebug` ✅

### Task 8: HF API Model Refresh — DONE
- Commit: 2a84907
- Changes: refreshAvailableModels(), fetchHfModels() (HF API query), getDeviceRamBytes() in LocalModelRepository.
- Tests: `:app:assembleDebug` ✅

### Task 9: Onboarding Screen — DONE
- Commit: b36daad
- Changes: 4-frame AnimatedContent story (quiet/remembers/acts + connect), breathing droid mascot, Sable wordmark, skip button.
- Tests: `:app:assembleDebug` ✅

### Task 10: Page Transitions — DONE
- Commit: 6e46a47
- Changes: slideInHorizontally/slideOutHorizontally on chat/sessions/settings, 300ms FastOutSlowIn.
- Tests: `:app:assembleDebug` ✅

### Task 11: DESIGN.md Update — DONE
- Commit: 916e11f
- Changes: Full DESIGN.md rewrite for Sable — SableTheme, droid chrome, transitions, HF models, typography/shape/motion tokens.
- Tests: `testDebugUnitTest` ✅ (flaky rerun), `:app:assembleDebug` ✅
- Note: Phone disconnected during on-device verification. APK built (125M). Screencaps pending reconnect.
