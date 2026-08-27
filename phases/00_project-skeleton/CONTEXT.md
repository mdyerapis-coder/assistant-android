# Phase 00 — project skeleton (module layout, no real logic yet)

**Reads:** `docs/plan.md` §2 (the full module tree), `docs/adr/001-pure-modules-where-possible.md`, `docs/adr/004-no-jvm-toolchain-note.md`, `../CONTEXT.md` (toolchain versions).

**Does:** create the Gradle project with the module layout from the plan — `app`, `core:model`, `core:network`, `core:security`, `core:database`, `core:designsystem`, `backend-client`, `feature:onboarding`, `feature:chat` — as empty-but-compiling modules (a package, a build.gradle.kts, nothing real inside yet beyond what's needed to compile: e.g. `App.kt` with `@HiltAndroidApp`, `MainActivity.kt`, an empty nav host). Pin the toolchain versions exactly as listed in `../CONTEXT.md`. `core:model`, `core:network`, `backend-client` must have zero Android imports (ADR-001) — verify by checking their `build.gradle.kts` applies `kotlin("jvm")` / a pure-Kotlin plugin, not `com.android.library`.

**Writes:** `settings.gradle.kts`, `gradle/libs.versions.toml`, one `build.gradle.kts` per module, `app/src/main/.../App.kt`, `app/src/main/.../MainActivity.kt`, `app/src/main/.../nav/AppNavHost.kt` (empty/placeholder screen is fine), `app/src/main/AndroidManifest.xml`.

**Human check:** `./gradlew build` succeeds from a clean checkout. Confirm `compileSdk 35` actually resolves (run `sdkmanager --list_installed` first if unsure) — install `platforms;android-35` via `sdkmanager` if it's missing rather than silently downgrading `compileSdk`.

**When done:** write `REPORT.md` recording the build result and the exact toolchain versions actually used (in case any had to deviate from the plan's pinned list).
