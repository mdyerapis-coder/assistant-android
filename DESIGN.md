# Sable Design System

Product name **Sable** — *silent + able*, warm sand & terracotta ember (`#D97757`/`#E08A6B`), dark-first neutrals (`#171717`/`#F5F4EF`). Code package `com.mdyerapis.sable`, domain `sable.llmclouds.au`, scheme `sableapp://`. Code symbol `SableTheme` in `Theme.kt`. See `docs/sable-positioning.md` for landing/tiers, `docs/superpowers/specs/2026-09-01-sable-uiux-design.md` for full overhaul spec.

## 0. Research Log

- Existing surface audit: `SableTheme`, `ChatScreen`, `OnboardingScreen`, and the shared components under `core/designsystem`.
- Platform reference: Material 3 Compose components and Android dynamic color.
- Direction: a quiet, native Android utility surface with droid-inspired chrome. System color provides identity; compact tonal bands separate account and model controls from the conversation.
- Phase 06 update: on-device testing showed wallpaper-derived dynamic color renders near-achromatic (flat gray), which read as unfinished. Reversed the default — a fixed brand scheme (terracotta primary #D97757 / #E08A6B, warm neutral surfaces, dark-first default) now provides identity; dynamic color remains an opt-in via `SableTheme(dynamicColor = true)`. Color tokens read `MaterialTheme.colorScheme` exclusively; palette documented in `Theme.kt` §BrandLight/BrandDark.
- Skill audit (ui-ux-pro-max pro-rules) drove the icon overhaul: no emoji or text glyphs as structural icons; vector `Icon`s with contentDescriptions; touch targets ≥48dp; state communicated in text + icon, not color alone.
- Theme toggle persistence: DataStore/SharedPrefs stores user preference; overflow menu persist in ChatScreen retains last selection.
- Sessions as home: `AppNavHost` defaults to `sessions` destination when token present; `ChatScreen` scaffolds with slim header and single overflow menu (⋮).
- Message presentation restyled: assistant messages plain text on background (no bubble), user messages with subtle `surfaceContainerHigh` bubble (20dp rounded); Composer neutral with terracotta send button (28dp pill).
- Droid mascot: sable-animal droid hybrid icon at all densities, adaptive XML with monochrome layer. Onboarding tells 3-frame story: "Sable stays quiet. Sable remembers. Sable acts when you ask."
- Page transitions: `slideInHorizontally` forward, `slideOutHorizontally` back, 300ms `FastOutSlowIn`.
- Cloud Providers + Device info in Settings. HF API model refresh with device RAM filtering.

## 1. Foundations

- Color: use `MaterialTheme.colorScheme` exclusively. The default is the terracotta-primary brand scheme (`SableTheme` dark-first default: `#171717` background / `#212121` surface / `#F5F4EF` light); `SableTheme(dynamicColor = true)` opts into Android 12+ wallpaper color. Palette: BrandLight — primary #D97757, onBackground #1A1A1A, surface #F5F4EF, surfaceContainerHigh #2A2A2A, surfaceContainerHighest #333333; BrandDark — primary #E08A6B, onBackground #ECECEC, surface #171717, surfaceContainerHigh #2A2A2A, surfaceContainerHighest #333333. Never hardcode hex values in components (read all tokens from `MaterialTheme.colorScheme`).
- Typography: Google Sans (Inter fallback) for headlines (`headlineMedium`, `titleMedium`), Roboto for body/labels, JetBrains Mono for data/code surfaces. All via `SableTypography` in `Type.kt`.
- Spacing: use a 4 dp grid. Screen gutters are 16 dp, compact control gaps are 8 dp, small content gaps are 4 dp.
- Shape: `SableShapes` — cards 20dp, buttons 20dp, composer 28dp (pill), chips 16dp. Droid mascot `CircleShape` with 2dp terracotta ring.
- Elevation: prefer tonal surface separation. Droid chrome: 1dp `outlineVariant` panel-line below every `TopAppBar`. Composer gets `surfaceContainerHigh` fill for tactile inset.
- Motion: `slideInHorizontally`/`slideOutHorizontally` page transitions (300ms, `FastOutSlowIn`). Mic pulse 600ms `EaseInOut`. Onboarding `AnimatedContent` crossfade. Respect `prefers-reduced-motion`.

## 2. Layout

- Screens own safe drawing insets.
- Conversation content owns remaining vertical space and scrolls independently of account controls and the composer.
- Account and model controls form a compact header region above the conversation.
- Touch targets use Material component sizing and remain at least 48 dp.

## 3. Accessibility

- Every interactive control has a visible text label or content description.
- Selection is communicated in text, not color alone.
- Error text uses `colorScheme.error` and remains in the normal reading order.
- Components retain Material focus, pressed, disabled, and selected states.

## 4. Content

- Provider names are displayed as supplied by the authenticated backend catalog.
- The underlying model identifier is secondary supporting text.
- Empty and loading catalog states use plain language and never present a nonfunctional choice.

## 5. Reusable Primitives and States

- `MessageBubble`: resting message content (user 20dp rounded bubble, assistant plain text).
- `ToolCallChip`: pending, completed, and failed tool execution.
- `LoadingIndicator`: active assistant response.
- `Composer`: 28dp pill, `surfaceContainerHigh` fill, terracotta send, mic with 600ms pulse.
- Material `OutlinedTextField`: empty, focused, populated, and disabled composer states.
- Material exposed dropdown: loading, empty, collapsed-selected, expanded, focused, and disabled-during-send model states.
- Tonal header row: disconnected and connected Google states; model selection occupies a sibling row with the same surface treatment.
