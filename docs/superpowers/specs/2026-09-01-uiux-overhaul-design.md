# UI/UX Overhaul — Quiet, Content-First Redesign

**Date:** 2026-09-01
**Status:** Approved design (terracotta accent, dark-first)
**Scope:** `assistant-android` visual identity + chat surface structure. No backend changes, no `docs/CONTRACT.md` change.

## Goal

Make the app feel like a quiet, content-first assistant (ChatGPT/Claude family) rather than a Material-template utility. The conversation is the star; chrome recedes; a single warm accent provides identity.

## 1. Palette

Replace the violet/cyan brand scheme with a neutral, low-chroma scheme and a single terracotta accent.

- **Accent (primary):** terracotta `#D97757` (light) / `#E08A6B` (dark, slightly lifted for contrast on dark surfaces).
- **Dark surfaces (default):** near-black neutrals — `background`/`surface` `#171717`, `surfaceContainer` `#212121`, `surfaceContainerHigh` `#2A2A2A`, `surfaceContainerHighest` `#333333`. Text `#ECECEC` on background, `#A3A3A3` on-surface-variant.
- **Light surfaces:** warm off-white `#F5F4EF` (Claude-style) background/surface, `#FFFFFF` surfaceContainer, near-black text `#1A1A1A`.
- **Secondary/tertiary:** muted warm grays (no second saturated hue). `secondary` = warm gray `#8A8A8A`; `tertiary` = muted sand `#C9A227` used only for status accents if ever needed.
- **Error:** keep Material red tokens (unchanged semantics).
- **Dark-first:** `AssistantTheme` defaults to dark regardless of the system setting; light is available via a manual theme toggle in the overflow menu (persisted). `dynamicColor` stays opt-in but is no longer the default path.

All pairs meet 4.5:1. Components read `MaterialTheme.colorScheme` exclusively — no hardcoded hex in components (existing rule, unchanged).


The current `TopAppBar` hosts five controls (model chip, sessions, clear, TTS, settings). Collapse to a slim header:

- **Left:** app title or nothing (conversation list is the home, so a title is optional).
- **Right:** a single overflow menu (⋮) containing: model switcher, TTS toggle, settings. Sessions/clear move to the home screen and message-list long-press respectively.
- The message list and composer get the reclaimed vertical space.

## 3. Home screen (structural change)

On launch (token present), land on a **conversation list** instead of an empty chat:

- Reuse `SessionsScreen` as the home destination (it already lists server-synced threads).
- Header: "New chat" primary action + the overflow menu.
- Below the list (or above it when empty): suggested prompts (reuse the existing suggestion chips) so a fresh install has an obvious first action.
- Tapping a thread opens the chat; "New chat" opens an empty chat.
- The existing `chat` destination remains for the active conversation; `sessions` becomes the start destination.

## 4. Message presentation

- **Assistant messages:** plain text on the background (no bubble), left-aligned, `bodyMedium`. Tool-call chips render inline above the text they precede.
- **User messages:** subtle bubble (`surfaceContainerHigh`), right-aligned, asymmetric radius (existing `MessageBubble` pattern, restyled).
- Streaming: the in-progress assistant text renders as plain text with a trailing cursor/`LoadingIndicator` (existing behavior, restyled).

## 5. Composer

Keep the rounded composer + mic + send. Restyle to the neutral palette: `surfaceContainer` fill, `outlineVariant` border, terracotta send button. Mic button keeps its listening pulse (Phase 09), recolored to the neutral scheme.

## 6. Files touched

- `core/designsystem/.../theme/Theme.kt` — new `BrandLight`/`BrandDark` palettes, dark-first default.
- `core/designsystem/.../components/MessageBubble.kt` — assistant plain-text variant, user bubble restyle.
- `core/designsystem/.../components/Composer.kt` — neutral restyle (already has mic).
- `feature/chat/.../ChatScreen.kt` — slim header + overflow menu, message presentation, home wiring.
- `feature/chat/.../SessionsScreen.kt` — home-screen treatment (new-chat action, suggested prompts).
- `app/.../nav/AppNavHost.kt` — `sessions` as start destination.
- `DESIGN.md` — update §0 research log + §1 foundations to the new palette.

## 7. Non-goals

- No backend changes, no contract change.
- No new permissions.
- No animation beyond existing Material defaults (respects reduced-motion).
- No change to onboarding flow, settings content, or model/local-model logic — only their visual restyle.

## 8. Verification

- `./gradlew testDebugUnitTest` green; `./gradlew :app:assembleDebug` green.
- On-device screencaps (light + dark): home list, empty chat with suggestions, active chat (assistant plain text + user bubble), composer, overflow menu.
- Contrast spot-check: terracotta on dark surface ≥ 4.5:1.
