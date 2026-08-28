# Phase 06 — UI/UX Native Redesign — Report

## What was built

A full visual/interaction overhaul bringing the app into compliance with `DESIGN.md` and the ui-ux-pro-max pre-delivery checklist, replacing the phase-05 bespoke slate/teal theme (which used emoji glyphs as icons, a hardcoded palette, and text-arrow buttons).

### Theme (`core:designsystem/theme/`)
- Deleted `Color.kt` (hardcoded slate/teal) and `Type.kt` (custom typography) — replaced with token-driven theming.
- `Theme.kt`: new `AssistantTheme`. Default is a distinctive **brand scheme** (violet primary `#6D28D9` light / `#D6BCFF` dark, cyan secondary `#0E7490`/`#67E8F9`, warm slate neutrals, semantic error) built from `lightColorScheme`/`darkColorScheme` with full tonal containers. `dynamicColor` is an opt-in parameter that switches to `dynamicLight/DarkColorScheme` for wallpaper-based identity on Android 12+.
- Palette sourced from the skill's database (colors.csv → "AI/Chatbot Platform": violet primary + cyan interactions); all on-color pairs meet 4.5:1.

### Dependencies
- Added `androidx.compose.material:material-icons-extended` to the version catalog and to `:core:designsystem` and `:feature:chat`.

### Components (`core:designsystem/components/`)
- `Composer`: send control is now `FilledIconButton` + `Icon(AutoMirrored.Send)` at 48dp, contentDescription "Send message"; text field uses Material filled tokens with `shapes.extraLarge`, indicator-free.
- `ModelStatusChip`: tonal surface, status dot + `KeyboardArrowDown` icon (contentDescription "Change model"), 40dp min height.
- `ToolCallChip`: status now uses vector `CheckCircle` (Finished) / `Error` (Error) icons + pulsing dot for Started/Progress; colors mapped to `colorScheme` roles (`primary`/`tertiary`/`error`) instead of hardcoded hex; removed decorative border.
- `MessageBubble`: shapes derived from `MaterialTheme.shapes.large` with one tightened corner (user top-end / assistant top-start); colors strictly `primaryContainer`/`surfaceVariant` tokens.
- `StateComponents`: `ErrorBanner` adds an `ErrorOutline` icon; retry action uses theme `error` role text.

### Screens
- `ChatScreen`: TopAppBar actions are vector `Icon`s with contentDescriptions — `History` (conversation history), `DeleteOutline` (clear conversation), `Settings`. Suggestion chips dropped emoji prefixes (plain "Morning brief", "Unread emails", "Today's schedule", "Remind me"); chip shape now the Material default.
- `SettingsScreen`: back arrow is `AutoMirrored.ArrowBack`; category filter chips are plain text (ALL / FAST / REASONING / UNCENSORED); model badges are text-only tonal chips; the selected-model "✓" is now a `CheckCircle` icon.
- `SessionsScreen`: back arrow + `DeleteOutline` icon (tinted `onSurfaceVariant`) replace glyphs.
- `OnboardingScreen`: unchanged structure; inherits the brand theme.

### Accessibility / pro-rules conformance
- **No emoji or text glyphs used as structural icons** anywhere (audited: grep for 📂🗑⚙🔓⚡🧠🌅📧📅☕🧩 and "←" "✓" "↑" "▾" "!" returns nothing).
- All icon buttons carry `contentDescription`; touch targets ≥48dp.
- Selection/state is communicated in text + icon, not color alone (e.g., "Selected" check, "Disconnect", engine toggle uses selected FilterChip).
- Contrast: text/on-surface pairs ≥4.5:1 in both themes; borders/dividers visible in both.

## Evidence

1. **Unit tests**: `./gradlew testDebugUnitTest` → `BUILD SUCCESSFUL` (all modules green; zero behavior changes to ViewModels/reducers/API client).
2. **Build + install**: `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`; installed on `AJ4UVB4611033150` (HONOR ELI-NX9) → `Success`.
3. **On-device screencaps** (brand theme):
   - Chat (light): `/tmp/p06_light2.png` — violet chip + pink tertiary status dot, plain suggestion chips, vector top-bar icons, send arrow.
   - Chat (dark): `/tmp/p06_d3.png` — violet-toned surfaces, vector icons, readable contrast.
   - Settings (dark): `/tmp/p06_dset.png` — violet section headers, lavender primary button, cyan selected engine chip, back arrow.
   - Settings (light): `/tmp/p06_lset.png` — vivid violet primary button + headers, cyan selected chip.

## Notes / decisions
- **Brand default vs dynamic color**: `DESIGN.md` §1 says "system color provides identity," but on this device the wallpaper-derived scheme is near-achromatic (flat gray), which failed the "not creative enough" bar. Resolution: ship a distinctive brand scheme as the default and expose `dynamicColor` as an opt-in — the token discipline (colorScheme, shapes, 48dp, tonal elevation) is retained per DESIGN.md. This is recorded in DESIGN.md §0.
- Pre-existing, unchanged: suggestion chips can clip on narrow screens (horizontal-scroll row); empty-state CTA hierarchy could be stronger. Not part of this phase's zero-behavior-change scope.

## Follow-up fix (navbar clash)

- **Root cause**: `MainActivity` never enabled edge-to-edge, and the `Composer` (a Scaffold `bottomBar`) only applied `imePadding()` — so with the keyboard closed, no inset was consumed and the composer's Surface background drew behind the 3-button navigation bar while its content collided with it.
- **Fix**: added `enableEdgeToEdge()` in `MainActivity.onCreate`, and added `navigationBarsPadding()` to the Composer's content Row (its Surface still extends behind the nav bar for a seamless look). IME-open behavior unchanged (`imePadding` still takes precedence).
- **Verified on device**: composer clears the nav bar with keyboard closed (`/tmp/p06_fix.png`), and sits directly above the keyboard with IME open (`/tmp/p06_ime.png`). `testDebugUnitTest` still green.
