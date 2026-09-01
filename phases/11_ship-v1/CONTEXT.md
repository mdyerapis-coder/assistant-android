# Phase 11 — Ship v1.0

Gate: Mason chooses real `applicationId` + display name — replaces `com.mdyerapis.sable` everywhere. Phase cannot start without this. Companion: `assistant-backend/phases/07_ship-v1`.

## Does

- **Decision gate — identity**: Replace placeholder `applicationId com.mdyerapis.sable` (`app/build.gradle.kts`, `AndroidManifest.xml`, `strings.xml` `app_name`, all imports) with chosen name. Single commit, no aliases.
- **Release build**: `signingConfigs.release` (keystore not committed; creds via `local.properties`/`~/.gradle/gradle.properties` env) + `buildTypes.release` `isMinifyEnabled=true` `isShrinkResources=true` R8 (`proguard-android-optimize.txt` + `proguard-rules.pro`). Verifies with `assembleRelease`.
- **Distribution decision (flagged)**: Mason decides — Play Console internal testing track **or** self-distributed signed APK. Record choice in REPORT.md; do not do both.
- **Crash reporting**: Integrate Crashlytics (or Sentry) — uncaught + ANR, release-only, no PII. Verify forced crash appears in console.
- **Polish for ship**: App icon/label final, versionCode/versionName set, `android:exported` audit.

## Verification

- `./gradlew :app:assembleRelease` green; `apksigner verify` on output; install release APK on fresh device/emulator.
- Fresh-install E2E: onboarding → chat (SSE stream) → schedule reminder → receive push → calendar/gmail read → voice input/output. No debug build in path.
- Crash: trigger test crash on release build, appears in Crashlytics/Sentry dashboard.
- `./gradlew testDebugUnitTest` green (no behavior change beyond signing/crash).

## Non-goals

- Play Store listing assets, screenshots, privacy policy, or production rollout beyond internal track.
- New features, migrations, or backend changes — companion phase owns backend ship.
- Self-hosted distribution infrastructure if Play track chosen (and vice versa).
