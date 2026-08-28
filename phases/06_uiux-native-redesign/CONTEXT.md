# Phase 06 — UI/UX Native Redesign (execute DESIGN.md)

Execution spec. The phase 05 bespoke theme (slate/teal, emoji glyphs, custom typography) predates `DESIGN.md`; this phase brings the app into compliance with `DESIGN.md` and the ui-ux-pro-max pre-delivery checklist (pro-rules.md).

## Does

**Constraint: Zero behavior changes** — ViewModels, reducers, API client, and persistence untouched. Visual/interaction layer only.

- **Theme (`core:designsystem/theme/`)**: Replace hardcoded slate/teal `Color.kt` with `MaterialTheme.colorScheme` exclusively — dynamic color on Android 12+, Material 3 fallback below. Drop custom `Type.kt` (use Material defaults). `AssistantTheme` enables `dynamicColor` per DESIGN.md §1.
- **Icons**: Add `compose-material-icons-extended`. Replace every emoji/text glyph used as a structural icon (📂 🗑 ⚙ 🔓 ⚡ 🧠 ← ↑ ✓ !) with vector `Icon`s carrying `contentDescription`. (pro-rules: no emoji as structural icons; 48dp targets.)
- **Components (`core:designsystem/components/`)**:
  - `Composer`: send button becomes `Icon(ArrowUpward)` 48dp; remove transparent-border hack, use Material filled text field tokens.
  - `ModelStatusChip`: tonal surface, status dot + chevron icon, 48dp min height.
  - `ToolCallChip`: status icons (`CheckCircle`, `Error`) + pulsing dot; status colors via `colorScheme` roles (error) and tonal containers.
  - `MessageBubble`: keep asymmetric corners; colors via `primaryContainer`/`surfaceVariant` tokens only.
  - `StateComponents`: `ErrorBanner` with error icon + retry action.
- **Screens**:
  - `ChatScreen`: TopAppBar actions become `Icon`s with descriptions (history, delete, settings); suggestion chips drop emoji prefixes; empty state per DESIGN.md §4 plain language.
  - `SettingsScreen`: back arrow icon; category filter chips become plain text (ALL/FAST/REASONING/UNCENSORED); badges become text-only tonal chips; Google row keeps text status (selection communicated in text, not color alone).
  - `SessionsScreen`: back arrow + delete icons; keep card rows.
  - `OnboardingScreen`: unchanged structure; inherits theme.

## Verification

- `./gradlew testDebugUnitTest` green.
- `./gradlew :app:assembleDebug` succeeds; install on `AJ4UVB4611033150`.
- Screencaps: chat (empty + streaming), settings, sessions, onboarding — light and dark.
- Checklist: no emoji icons; all targets ≥48dp; text contrast 4.5:1 both themes; labels/descriptions on all icon buttons.
