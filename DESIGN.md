# Assistant Android Design System

## 0. Research Log

- Existing surface audit: `AssistantTheme`, `ChatScreen`, `OnboardingScreen`, and the shared components under `core/designsystem`.
- Platform reference: Material 3 Compose components and Android dynamic color.
- Direction: a quiet, native Android utility surface. System color provides identity; compact tonal bands separate account and model controls from the conversation.
- Phase 06 update: on-device testing showed wallpaper-derived dynamic color renders near-achromatic (flat gray), which read as unfinished. Reversed the default — a fixed brand scheme (violet primary + cyan secondary, M3 tonal tokens) now provides identity; dynamic color remains an opt-in via `AssistantTheme(dynamicColor = true)`. Token discipline (colorScheme, shapes, spacing grid, tonal elevation, 48dp targets) is unchanged.
- Skill audit (ui-ux-pro-max pro-rules) drove the icon overhaul: no emoji or text glyphs as structural icons; vector `Icon`s with contentDescriptions; touch targets ≥48dp; state communicated in text + icon, not color alone.

## 1. Foundations

- Color: use `MaterialTheme.colorScheme` exclusively. The default is the brand scheme (violet primary, cyan secondary); `AssistantTheme(dynamicColor = true)` opts into Android 12+ wallpaper color. Never hardcode hex values in components.
- Typography: use `MaterialTheme.typography`. Body copy uses `bodyMedium`; screen headings use `headlineMedium`; component labels use their Material defaults.
- Spacing: use a 4 dp grid. Screen gutters are 16 dp, compact control gaps are 8 dp, small content gaps are 4 dp.
- Shape: use Material component defaults. Status markers may use `CircleShape`.
- Elevation: prefer tonal surface separation. Do not add decorative shadows.
- Motion: motion communicates state or navigation only. Respect platform animation settings through Compose and Material defaults.

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

- `MessageBubble`: resting message content.
- `ToolCallChip`: pending, completed, and failed tool execution.
- `LoadingIndicator`: active assistant response.
- Material `OutlinedTextField`: empty, focused, populated, and disabled composer states.
- Material exposed dropdown: loading, empty, collapsed-selected, expanded, focused, and disabled-during-send model states.
- Tonal header row: disconnected and connected Google states; model selection occupies a sibling row with the same surface treatment.

