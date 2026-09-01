# Task 1: Theme & Typography Foundation

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/mdyerapis/sable/core/designsystem/theme/Theme.kt`
- Create: `core/designsystem/src/main/kotlin/com/mdyerapis/sable/core/designsystem/theme/Type.kt`
- Create: `app/src/main/res/font/sans_headline.ttf` (Google Sans or Inter as open alternative, weight 500)
- Create: `app/src/main/res/font/mono_accent.ttf` (JetBrains Mono)

**Interfaces:**
- Produces: `SableTheme` composable (replaces `AssistantTheme`), `SableTypography` with Google Sans headlines + JetBrains Mono accent, `SableShapes` with 20dp/28dp/16dp rounded corners, `MonoFontFamily` public val.

**Steps:**

1. Add font files to `app/src/main/res/font/`. If Google Sans licensing prevents bundling, use Inter (open alternative, same geometric feel).

2. Create `Type.kt` with `SableTypography` (Google Sans for headlines/titleMedium, Roboto default for body/labels) and `MonoFontFamily` (JetBrains Mono for data/code surfaces).

3. Update `Theme.kt`: rename `AssistantTheme` → `SableTheme`, add `SableTypography` and `SableShapes` to `MaterialTheme`. Keep `BrandLight`/`BrandDark` palettes unchanged.

4. Find-replace `AssistantTheme` → `SableTheme` across all `.kt` files (excluding `build/`).

5. Verify: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q` both green.

6. Commit: `feat: SableTheme + SableTypography + SableShapes foundation`

**Global Constraints:**
- All color pairs ≥ 4.5:1 contrast ratio.
- No hardcoded hex in components — read `MaterialTheme.colorScheme` exclusively.
- `dynamicColor` stays opt-in, default is brand.
- Respect `prefers-reduced-motion`.
- minSdk 26, compileSdk 35.
