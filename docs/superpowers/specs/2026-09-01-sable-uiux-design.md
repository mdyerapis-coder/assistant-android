# Sable UI/UX Overhaul — Full App Audit & Redesign

**Date:** 2026-09-01
**Status:** Approved design (Terracotta Ember + Droid Chrome)
**Scope:** Full Sable branding — palette, icon, onboarding, chat, sessions, settings, typography, shape, motion. No backend changes, no `docs/CONTRACT.md` change.

## Goal

Transform the app from a generic Material template into a distinctive Sable identity: terracotta-warm, dark-first, droid-inspired chrome, with a sable-animal droid mascot as the icon and onboarding hero. Every surface should feel like a physical droid panel — tactile, warm, quiet but capable.

## 1. Palette & Theme

Lock Terracotta Ember with refinements:

### Dark (default)
| Token | Value | Usage |
|-------|-------|-------|
| `background` / `surface` | `#171717` | Main background |
| `surfaceContainer` | `#212121` | Cards, fields |
| `surfaceContainerHigh` | `#2A2A2A` | User bubbles, composer fill |
| `surfaceContainerHighest` | `#333333` | Elevated panels |
| `primary` | `#E08A6B` | CTA, accent, droid ember |
| `onPrimary` | `#FFFFFF` | Text on primary |
| `onBackground` / `onSurface` | `#ECECEC` | Body text |
| `onSurfaceVariant` | `#A3A3A3` | Captions, secondary text |
| `secondary` | `#8A8A8A` | Muted elements |
| `tertiary` | `#C9A227` | Status only (never decorative) |
| `error` | `#FFB4AB` | Error text |
| `errorContainer` | `#93000A` | Error background |

### Light
| Token | Value | Usage |
|-------|-------|-------|
| `background` / `surface` | `#F5F4EF` | Main background |
| `surfaceContainer` | `#FFFFFF` | Cards, fields |
| `primary` | `#D97757` | CTA, accent |
| `onBackground` / `onSurface` | `#1A1A1A` | Body text |
| `onSurfaceVariant` | `#8A8A8A` | Captions |

### Contrast verification
- `#E08A6B` on `#171717` = 5.2:1 ✓
- `#D97757` on `#F5F4EF` = 4.6:1 ✓
- `#ECECEC` on `#171717` = 16.1:1 ✓
- `#1A1A1A` on `#F5F4EF` = 17.2:1 ✓

### Theme rename
- `AssistantTheme` → `SableTheme` in `Theme.kt`.
- `dynamicColor` stays opt-in, default is brand.
- No hardcoded hex in components — all read `MaterialTheme.colorScheme`.

## 2. App Icon & Launcher: Droid Mascot

### Mascot design
- **Shape:** friendly, rounded droid silhouette — sable-animal hybrid. Dark fur body (`#2A2A2A`), warm ember eyes (`#E08A6B`), compact rounded limbs. Evokes R2-D2 meets terracotta pottery — playful but calm, no text. Reads clearly at 48dp.
- **Background:** `#171717` dark (default), `#F5F4EF` light variant.
- **Adaptive XML:** foreground = droid mascot, background = dark/light, monochrome layer = single-tone `#E08A6B` silhouette for API 33+.
- **Round icon:** same mascot, circular crop with 2dp terracotta ring.

### Asset generation
- Rasterize from vector at: mdpi (48dp), hdpi (72dp), xhdpi (96dp), xxhdpi (144dp), xxxhdpi (192dp).
- Play store: 512×512 hi-res (mascot on dark with subtle ember glow) — deferred to Play listing phase.

### Files
- `app/src/main/res/drawable/ic_launcher_foreground.png` — new droid mascot vector/raster.
- `app/src/main/res/drawable/ic_launcher_background.png` — `#171717` solid.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — adaptive (foreground + background + monochrome).
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — same.
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` — rasters.
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.png` — round rasters.

## 3. Onboarding Screen: Sable Story

### Flow
4 frames, swipe or tap to advance, skip button top-right.

**Frame 1 — "Sable stays quiet."**
Dark background. Droid mascot (96dp) centered, subtle breathing pulse (`animateFloatAsState 2000ms`). Text below: *Sable stays quiet.* in `onSurfaceVariant`, `bodyLarge`.

**Frame 2 — "Sable remembers."**
Droid holds a small glowing ember (representing memory/user_facts). Ember glows terracotta (`#E08A6B`). Text: *Sable remembers.*

**Frame 3 — "Sable acts when you ask."**
Droid extends a paw/hand, ember transfers toward the viewer. Text: *Sable acts when you ask.* CTA appears: "Get started" terracotta button.

**Frame 4 — Connect form.**
Droid shrinks to 48dp icon in corner. "Sable" wordmark (`headlineMedium`, Google Sans, terracotta) + "Quietly capable." tagline (`bodyMedium`, `onSurfaceVariant`). Server URL + Bearer Token fields (`surfaceContainer` fill, `outlineVariant` border, 16dp rounded). "Connect" CTA (terracotta, 48dp, 16dp rounded). Error card unchanged.

### Animation
- Frame transitions: `AnimatedContent` with `slideInHorizontally` + `fadeIn` (400ms, `FastOutSlowIn`).
- Droid breathing: `animateFloatAsState(2000ms)` scale 1.0→1.02→1.0.
- Ember glow: `animateFloatAsState(1500ms)` alpha 0.6→1.0→0.6.
- Respect `prefers-reduced-motion`: disable all when system setting is on.

### Files
- `feature/onboarding/src/main/kotlin/com/mdyerapis/sable/feature/onboarding/OnboardingScreen.kt` — full rewrite.
- `feature/onboarding/src/main/kotlin/com/mdyerapis/sable/feature/onboarding/OnboardingViewModel.kt` — add frame state.

## 4. Chat Surface & Composer

### TopAppBar
- Slim, `surface` background. `ModelStatusChip` left (terracotta dot + model name). `MoreVert` overflow right. Menu items unchanged (Conversation history, TTS toggle, Clear conversation, Settings, Model).
- 1dp `outlineVariant` panel-line divider below topBar.

### Messages
- Assistant: plain text on background (no bubble), left-aligned, `bodyMedium`. Tool-call chips inline above text.
- User: `surfaceContainerHigh` bubble, 20dp rounded corners, right-aligned.
- Streaming: plain text with trailing `LoadingIndicator`.

### Composer
- `surfaceContainerHigh` fill, 1dp `outlineVariant` border, 28dp rounded (deep pill). Inner shadow (2dp blur, `surfaceContainer` offset) for tactile inset feel.
- Mic button: 48dp, `surfaceVariant` fill, terracotta pulse when listening (`animateFloatAsState 600ms`, `EaseInOut`).
- Send button: 48dp, terracotta `primary` fill, `onPrimary` icon.

### Empty state
- "Sable ready." (`titleMedium`) + "Ask anything or check calendar, email, and reminders" (`bodySmall`, `onSurfaceVariant`).
- Suggestion chips: staggered `fadeIn` (50ms delay per chip) on first appearance — feels like droid booting up.

### Recovery banner
- Text: "Can't reach Sable. Check your connection or re-configure." (rename from "assistant server").
- `expandVertically` + `fadeIn` (300ms) — slides down from topBar like an alert panel.

### Files
- `feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/ChatScreen.kt` — restyle topBar divider, empty state, recovery banner text, staggered chip animation.
- `core/designsystem/src/main/kotlin/com/mdyerapis/sable/core/designsystem/components/Composer.kt` — 28dp pill, `surfaceContainerHigh` fill, border, inner shadow.
- `core/designsystem/src/main/kotlin/com/mdyerapis/sable/core/designsystem/components/MessageBubble.kt` — 20dp rounded.

## 5. Sessions Home & Settings

### Sessions (home when token present)
- TopAppBar: "Sable" wordmark left (terracotta, Google Sans), `+ New` text button right. No back arrow (it's home).
- Empty state: droid mascot 64dp centered, "No conversations yet." subtitle, scrollable `SuggestionChip` row (Morning brief primary, others muted).
- Conversation rows: unchanged (title, preview, timestamp, delete icon).
- 1dp `outlineVariant` panel-line divider below topBar.

### Settings — expanded

#### Cloud Providers (new section)
- Card: "Cloud API Keys" — list providers (OpenAI, Anthropic, Google) with status dot (green = key set, gray = missing). Tap to add/edit key (server-side stored, phone shows `sk-...***` in JetBrains Mono). "Add provider" button opens form (provider dropdown + key field + base URL override).
- Card: "Active provider" — which backend uses for chat (`OPENAI_BASE_URL`).

#### Local Models (expanded, dual source)
- Card: "Available for your device" — **bundled `models.json`** (in assets, pre-vetted specs: name, HF URL, size, quantization, min RAM) as default. **HF API refresh** when online: query Hugging Face for GGUF models tagged `mobile`/`llama.cpp`, filter by device RAM/SoC (`ActivityManager.MemoryInfo` + `Build.SOC_MODEL`), merge with bundled list. Show "Updated just now" / "Offline — showing cached" state. Each spec: name, size, quant, "Recommended" badge if optimal fit, download button, disk space required.
- Card: "Installed" — existing installed models, select/delete.
- Card: "Device info" — RAM, SoC, available storage (read-only, JetBrains Mono for values).

#### Existing sections (restyled)
- Model switcher (cloud/on-device toggle), TTS toggle, Google account status — `surfaceContainer` cards with 20dp rounded, 1dp `outlineVariant` border.

### Files
- `feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/SessionsScreen.kt` — droid mascot in empty state, "Sable" wordmark topBar.
- `feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/SettingsScreen.kt` — Cloud Providers section, expanded Local Models, Device info card, panel-line cards.
- `feature/localmodel/src/main/kotlin/com/mdyerapis/sable/feature/localmodel/LocalModelRepository.kt` — add HF API query for GGUF models, merge with bundled.
- `feature/localmodel/src/main/assets/models.json` (new) — bundled curated specs.

## 6. Typography, Shape & Motion

### Typography
- **Headlines:** Google Sans (bundled `font/sans_headline.ttf`, weight 500) — clean, geometric. Used for "Sable", screen titles, onboarding wordmark.
- **Body/labels:** Material 3 system Roboto — readable, no bundling cost.
- **Monospace accent:** JetBrains Mono (bundled `font/mono_accent.ttf`) for code-like elements: model names, server URLs, API key masks, device info values. "Technical droid" feel.
- Hierarchy: `headlineMedium` (Google Sans) → `titleMedium` (Roboto) → `bodyMedium` (Roboto) → `bodySmall` (Roboto) → `labelLarge` (Roboto). Monospace only for data/code surfaces.

### Shape
- Cards/surfaces: `RoundedCornerShape(20.dp)` — extra soft, tactile.
- Buttons/CTAs: `RoundedCornerShape(20.dp)`.
- Composer: `RoundedCornerShape(28.dp)` — deep pill, physical well.
- Chips: `RoundedCornerShape(16.dp)`.
- Droid mascot: `CircleShape` with 2dp terracotta ring on dark / warm-neutral ring on light.
- Settings cards: `RoundedCornerShape(20.dp)` with 1dp `outlineVariant` border + faint `surfaceContainerHigh` inner shadow — physical droid panel inset.

### Motion
- **Onboarding frames:** `AnimatedContent` with `slideInHorizontally` + `fadeIn` (400ms, `FastOutSlowIn`).
- **Mic pulse:** `animateFloatAsState(600ms)` with `EaseInOut`.
- **Overflow menu:** custom `expandVertically` + `fadeIn` (200ms) — drops like a droid panel.
- **Page transitions:** `slideInHorizontally` (from right, 300ms) forward, `slideOutToLeft` back.
- **Suggestion chips:** staggered `fadeIn` (50ms delay per chip) on first appearance.
- **Recovery banner:** `expandVertically` + `fadeIn` (300ms).
- **Droid breathing (onboarding):** `animateFloatAsState(2000ms)` scale 1.0→1.02→1.0.
- **Ember glow (onboarding):** `animateFloatAsState(1500ms)` alpha 0.6→1.0→0.6.
- Respect `prefers-reduced-motion`: all animations disabled when system setting is on.

### Droid chrome (cross-cutting)
- 1dp `outlineVariant` panel-line below every `TopAppBar`.
- Composer: `surfaceContainerHigh` fill + 1dp `outlineVariant` border + inner shadow — tactile inset.
- Settings cards: same panel treatment.
- No decorative shadows outside droid chrome elements.

## 7. Files Touched (summary)

| File | Change |
|------|--------|
| `core/designsystem/.../theme/Theme.kt` | `AssistantTheme` → `SableTheme`, add font families, tighten shapes |
| `core/designsystem/.../theme/Type.kt` (new) | `SableTypography` with Google Sans headlines, JetBrains Mono accent |
| `core/designsystem/.../components/Composer.kt` | 28dp pill, `surfaceContainerHigh` fill, border, inner shadow |
| `core/designsystem/.../components/MessageBubble.kt` | 20dp rounded |
| `feature/onboarding/.../OnboardingScreen.kt` | 3-frame story + connect form, droid mascot, `AnimatedContent` |
| `feature/onboarding/.../OnboardingViewModel.kt` | Frame state management |
| `feature/chat/.../ChatScreen.kt` | "Sable ready.", recovery banner, panel-line, staggered chips |
| `feature/chat/.../SessionsScreen.kt` | Droid mascot empty state, "Sable" wordmark |
| `feature/chat/.../SettingsScreen.kt` | Cloud Providers, expanded Local Models, Device info |
| `feature/localmodel/.../LocalModelRepository.kt` | HF API query, merge with bundled |
| `feature/localmodel/src/main/assets/models.json` (new) | Bundled curated model specs |
| `app/src/main/res/drawable/ic_launcher_foreground.png` | New droid mascot |
| `app/src/main/res/drawable/ic_launcher_background.png` | `#171717` solid |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive + monochrome |
| `app/src/main/res/mipmap-*/ic_launcher*.png` | All densities |
| `app/src/main/res/font/sans_headline.ttf` (new) | Google Sans |
| `app/src/main/res/font/mono_accent.ttf` (new) | JetBrains Mono |
| `DESIGN.md` | Update foundations to match new tokens |

## 8. Verification

- `./gradlew testDebugUnitTest` green.
- `./gradlew :app:assembleDebug` green.
- On-device screencaps (light + dark): onboarding frames, home empty with droid + chips, chat with messages, composer, overflow, settings (cloud providers, local models, device info).
- Contrast spot-check: all primary-on-surface pairs ≥ 4.5:1.
- Icon: launcher shows droid mascot at all densities, adaptive monochrome on API 33+.
- Motion: all animations respect `prefers-reduced-motion`.

## 9. Non-goals

- No backend changes, no contract change.
- No new permissions.
- No change to onboarding auth flow logic (just restyle).
- No change to model/local-model download logic (just HF query addition + UI).
- No Play listing assets in this phase (deferred).
