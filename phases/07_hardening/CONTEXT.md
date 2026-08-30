# Phase 07 — Hardening + Debt Payoff

Companion: `assistant-backend` `phases/04_hardening`. No Python read; contract is `docs/CONTRACT.md`.

## Does

- **05b debt — on-device re-test**: USB adb on `AJ4UVB4611033150`: send message, wait ~3s, pull `assistant_chat.db` with `-wal`/`-shm` (`run-as <pkg> cat databases/assistant_chat.db*`); `conversation_messages` row must exist. WAL-pull timing artifact resolved.
- **05b debt — instrumented test**: `./gradlew :core:database:connectedAndroidTest` green — `ConversationRepositoryTest` (Room in-memory, `src/androidTest`) passes on device.
- **06 debt — chips clipping**: suggestion chips become horizontal-scroll `Row` (no clip on narrow screens); retains plain-text labels, Material shape/tokens.
- **06 debt — empty-state CTA**: strengthen hierarchy (primary CTA prominent, secondary muted) per DESIGN.md §4 plain language.
- **CI**: GitHub Actions on every push: `./gradlew testDebugUnitTest` and `./gradlew :app:assembleDebug` green.

## Verification

- USB adb re-test: `conversation_messages` row present after WAL pull; `conversations` row survives force-stop + relaunch.
- `:core:database:connectedAndroidTest` green on `AJ4UVB4611033150`.
- Narrow-screen check: chips scroll, no clip; empty-state CTA hierarchy visible in light/dark screencaps.
- CI: workflow file present; `testDebugUnitTest` + `assembleDebug` pass on push.

## Non-goals

- No ViewModel/reducer/API/persistence behavior changes beyond listed fixes.
- No new features, schema bumps, or `AppDatabase` migration.
