---
name: assistant-android-workspace
description: Project contract for the Android companion app — what it is, how it relates to assistant-backend, where the source of truth lives.
---

# What this is

A native Android app (Kotlin, Jetpack Compose, Hilt) that's the phone-side half of a personal-assistant system. It does not think — it streams a chat UI over SSE from `assistant-backend`, renders tool calls as they happen (reminders, calendar, email — all backend-executed), and stores nothing sensitive except a bearer token in the Android Keystore. See `docs/plan.md` §2 for the full module layout and streamed-chat pattern.

## The repeating unit

A **phase** (`phases/00_...` through `phases/03_...`), same as `assistant-backend`. Each phase adds one vertical slice (project skeleton → onboarding+chat → tool-call UI → push delivery → Google OAuth), builds/tests locally, then gets a human check (sideload the debug APK, actually use it) before the next phase starts.

## Universes

- **Live:** whatever has a `phases/*/REPORT.md`.
- **Planned, not started:** phase folders with only a `CONTEXT.md`.
- **Deferred:** SMS and finances (mentioned in the original ask, explicitly out of scope for all phases currently planned — would be a new phase, not a retrofit).

## Source of truth

`docs/plan.md` for design intent and rationale. `docs/CONTRACT.md` for the exact wire shape — if the two ever conflict on the API shape, `docs/CONTRACT.md` wins (it's copied directly from the backend's owning source).

## Dependency boundary

This repo depends on exactly one thing from `assistant-backend`: `docs/CONTRACT.md` (copied in, not symlinked — re-copy by hand if the backend's contract changes, don't drift). It does not depend on `assistant-backend`'s Python, its database schema, its tool registry, or anything else. A cold agent building this repo should never need to open the other one.

## Toolchain

JDK 17, Gradle 8.14, Kotlin 2.0.21, AGP 8.7.3, KSP 2.0.21-1.0.28, Hilt 2.52, Compose BOM 2024.11.00, OkHttp 4.12.0, kotlinx.serialization 1.7.3, kotlinx.coroutines 1.9.0, Room 2.7.2, compileSdk 35, minSdk 26 — see `docs/plan.md` §2 for the full pinned list and `docs/adr/004-no-jvm-toolchain-note.md` for why there's no per-module `jvmToolchain` override. `compileSdk 35` needs `sdkmanager "platforms;android-35"` — as of the plan being written, only android-34 was confirmed installed; check before assuming it's there.
