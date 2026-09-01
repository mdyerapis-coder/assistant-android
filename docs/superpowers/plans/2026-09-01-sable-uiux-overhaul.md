# Sable UI/UX Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the app from a generic Material template into a distinctive Sable identity — terracotta-warm, dark-first, droid-inspired chrome with a sable-animal droid mascot.

**Architecture:** Single-app Compose overhaul touching `core/designsystem` (theme, typography, components), `feature/onboarding` (story frames), `feature/chat` (sessions, settings, chat surface), `feature/localmodel` (HF API + bundled catalog), and `app/src/main/res` (icon, fonts). No backend changes, no new permissions, no contract changes.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.11.00), Material 3, Hilt, Room, OkHttp, kotlinx.coroutines, Hugging Face API (REST).

**Spec:** `docs/superpowers/specs/2026-09-01-sable-uiux-design.md`

## Global Constraints

- All color pairs ≥ 4.5:1 contrast ratio (WCAG AA).
- No hardcoded hex in components — read `MaterialTheme.colorScheme` exclusively.
- `AssistantTheme` → `SableTheme` rename; `dynamicColor` opt-in, default is brand.
- Respect `prefers-reduced-motion`: disable all custom animations when system setting is on.
- `./gradlew testDebugUnitTest` and `./gradlew :app:assembleDebug` green after every task.
- minSdk 26, compileSdk 35.
- No new permissions, no backend changes, no `docs/CONTRACT.md` change.

---

### Task 1: Theme & Typography Foundation

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/mdyerapis/sable/core/designsystem/theme/Theme.kt`
- Create: `core/designsystem/src/main/kotlin/com/mdyerapis/sable/core/designsystem/theme/Type.kt`
- Create: `app/src/main/res/font/sans_headline.ttf` (Google Sans, weight 500)
- Create: `app/src/main/res/font/mono_accent.ttf` (JetBrains Mono)

**Interfaces:**
- Produces: `SableTheme` composable (replaces `AssistantTheme`), `SableTypography` with Google Sans headlines + JetBrains Mono accent, `SableShapes` with 20dp/28dp/16dp rounded corners.

- [ ] **Step 1: Add font files**

Download Google Sans (weight 500) and JetBrains Mono (regular) `.ttf` files. Place in `app/src/main/res/font/`. If licensing prevents bundling Google Sans, use the closest open alternative (Inter or Manrope) — same geometric feel.

- [ ] **Step 2: Create Type.kt**

```kotlin
package com.mdyerapis.sable.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mdyerapis.sable.R

private val SansHeadline = FontFamily(
    Font(R.font.sans_headline, FontWeight.Medium)
)

private val MonoAccent = FontFamily(
    Font(R.font.mono_accent, FontWeight.Normal)
)

val SableTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = SansHeadline,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SansHeadline,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SansHeadline,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SansHeadline,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SansHeadline,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

val MonoFontFamily = MonoAccent
```

- [ ] **Step 3: Update Theme.kt**

Rename `AssistantTheme` → `SableTheme`. Add `SableTypography` and `SableShapes`. Keep `BrandLight`/`BrandDark` palettes unchanged (already correct from spec §1).

```kotlin
// In Theme.kt, replace the composable:
@Composable
fun SableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> BrandDark
        else -> BrandLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SableTypography,
        shapes = SableShapes,
        content = content
    )
}
```

Add `SableShapes`:
```kotlin
private val SableShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
```

- [ ] **Step 4: Update all call sites**

Find-replace `AssistantTheme` → `SableTheme` across all files. Run:
```bash
grep -rn "AssistantTheme" --include="*.kt" . | grep -v build
```

- [ ] **Step 5: Verify builds**

Run: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q`
Expected: both green.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: SableTheme + SableTypography + SableShapes foundation"
```

---

### Task 2: App Icon — Droid Mascot

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.png`
- Modify: `app/src/main/res/drawable/ic_launcher_background.png`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Modify: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
- Modify: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.png`

**Interfaces:**
- Produces: droid mascot icon at all densities, adaptive XML with monochrome layer.

- [ ] **Step 1: Create droid mascot vector**

Design a sable-animal droid hybrid: dark fur body (`#2A2A2A`), warm ember eyes (`#E08A6B`), compact rounded limbs, no text. Export as PNG at 432×432 (xxxhdpi base). Create monochrome variant (single-tone `#E08A6B` silhouette).

- [ ] **Step 2: Generate rasters**

Resize to: mdpi 108×108, hdpi 162×162, xhdpi 216×216, xxhdpi 324×324, xxxhdpi 432×432. Place in respective `mipmap-*` directories.

- [ ] **Step 3: Update adaptive XML**

```xml
<!-- mipmap-anydpi-v26/ic_launcher.xml -->
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

Same for `ic_launcher_round.xml`.

- [ ] **Step 4: Set background to #171717**

Replace `ic_launcher_background.png` with solid `#171717` at all densities.

- [ ] **Step 5: Verify builds**

Run: `./gradlew :app:assembleDebug --no-daemon -q`
Expected: green. Check icon in launcher on device.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: droid mascot launcher icon at all densities"
```

---

### Task 3: Composer Restyle

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/mdyerapis/sable/core/designsystem/components/Composer.kt`

**Interfaces:**
- Consumes: `SableShapes` (28dp extraLarge), `SableTheme` color tokens.
- Produces: pill-shaped composer with `surfaceContainerHigh` fill, 1dp `outlineVariant` border, inner shadow, terracotta send button.

- [ ] **Step 1: Update Composer shape and fill**

Replace `RoundedCornerShape` with `MaterialTheme.shapes.extraLarge` (28dp). Change `Surface` color to `MaterialTheme.colorScheme.surfaceContainerHigh`. Add 1dp `outlineVariant` border via `Modifier.border`.

- [ ] **Step 2: Add inner shadow**

Use `Modifier.drawBehind` with a subtle radial gradient from `surfaceContainer` at top to transparent at bottom (2dp blur effect). This gives the tactile inset feel.

- [ ] **Step 3: Update send button**

Change `containerColor` to `MaterialTheme.colorScheme.primary`, `contentColor` to `MaterialTheme.colorScheme.onPrimary`.

- [ ] **Step 4: Update mic button pulse**

Change `animateFloatAsState` duration to `600ms` with `EaseInOut` easing.

- [ ] **Step 5: Verify builds**

Run: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q`
Expected: both green.

- [ ] **Step 6: Commit**

```bash
git add core/designsystem/src/main/kotlin/com/mdyerapis/sable/core/designsystem/components/Composer.kt
git commit -m "feat: Composer restyle — 28dp pill, tactile inset, terracotta send"
```

---

### Task 4: MessageBubble Restyle

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/mdyerapis/sable/core/designsystem/components/MessageBubble.kt`

**Interfaces:**
- Consumes: `SableShapes`, `SableTheme` color tokens.
- Produces: user bubble with 20dp rounded corners, `surfaceContainerHigh` fill.

- [ ] **Step 1: Update bubble shape**

Change `RoundedCornerShape` to `MaterialTheme.shapes.large` (20dp).

- [ ] **Step 2: Update user bubble fill**

Change `containerColor` to `MaterialTheme.colorScheme.surfaceContainerHigh`.

- [ ] **Step 3: Verify builds**

Run: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q`
Expected: both green.

- [ ] **Step 4: Commit**

```bash
git add core/designsystem/src/main/kotlin/com/mdyerapis/sable/core/designsystem/components/MessageBubble.kt
git commit -m "feat: MessageBubble restyle — 20dp rounded, surfaceContainerHigh"
```

---

### Task 5: ChatScreen Restyle

**Files:**
- Modify: `feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/ChatScreen.kt`

**Interfaces:**
- Consumes: `SableTheme` tokens, `SableShapes`, `MonoFontFamily`.
- Produces: panel-line divider, "Sable ready." empty state, staggered chip animation, recovery banner rename.

- [ ] **Step 1: Add panel-line divider below TopAppBar**

After `TopAppBar`, add:
```kotlin
HorizontalDivider(
    color = MaterialTheme.colorScheme.outlineVariant,
    thickness = 1.dp
)
```

- [ ] **Step 2: Update empty state text**

Change "Assistant Ready" → "Sable ready." and subtitle to "Ask anything or check calendar, email, and reminders".

- [ ] **Step 3: Add staggered chip animation**

Wrap each `SuggestionChip` in `AnimatedVisibility` with `fadeIn` + staggered `initialDelay`:
```kotlin
suggestions.forEachIndexed { index, (label, prompt) ->
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(300, delayMillis = index * 50))
    ) {
        SuggestionChip(...)
    }
}
```

- [ ] **Step 4: Update recovery banner text**

Change "Can't reach your assistant server." → "Can't reach Sable. Check your connection or re-configure."

- [ ] **Step 5: Add banner animation**

Wrap banner card in `AnimatedVisibility` with `expandVertically` + `fadeIn` (300ms).

- [ ] **Step 6: Verify builds**

Run: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q`
Expected: both green.

- [ ] **Step 7: Commit**

```bash
git add feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/ChatScreen.kt
git commit -m "feat: ChatScreen restyle — panel-line, Sable ready, staggered chips"
```

---

### Task 6: SessionsScreen Restyle

**Files:**
- Modify: `feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/SessionsScreen.kt`

**Interfaces:**
- Consumes: `SableTheme` tokens, droid mascot drawable.
- Produces: "Sable" wordmark topBar, droid mascot in empty state.

- [ ] **Step 1: Update TopAppBar title**

Change `Text("Conversations")` → `Text("Sable")` with `MaterialTheme.typography.headlineMedium` and `MaterialTheme.colorScheme.primary`.

- [ ] **Step 2: Add droid mascot to empty state**

Add `Image(painterResource(R.drawable.ic_launcher_foreground), ...)` at 64dp above "No conversations yet." text.

- [ ] **Step 3: Add panel-line divider**

Same as ChatScreen: `HorizontalDivider` below `TopAppBar`.

- [ ] **Step 4: Verify builds**

Run: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q`
Expected: both green.

- [ ] **Step 5: Commit**

```bash
git add feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/SessionsScreen.kt
git commit -m "feat: SessionsScreen restyle — Sable wordmark, droid mascot empty"
```

---

### Task 7: SettingsScreen — Cloud Providers & Expanded Local Models

**Files:**
- Modify: `feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/SettingsScreen.kt`
- Create: `feature/localmodel/src/main/assets/models.json`

**Interfaces:**
- Consumes: `SableTheme` tokens, `MonoFontFamily`, `LocalModelRepository`.
- Produces: Cloud Providers section, expanded Local Models with device info, bundled model catalog.

- [ ] **Step 1: Create bundled models.json**

```json
[
  {
    "id": "qwen2.5-1.5b-instruct-q4",
    "name": "Qwen2.5 1.5B Instruct Q4",
    "hfUrl": "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF",
    "sizeBytes": 1000000000,
    "quantization": "Q4_K_M",
    "minRamBytes": 3000000000,
    "recommended": true
  },
  {
    "id": "phi-3-mini-4k-q4",
    "name": "Phi-3 Mini 4K Q4",
    "hfUrl": "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf",
    "sizeBytes": 2200000000,
    "quantization": "Q4_K_M",
    "minRamBytes": 5000000000,
    "recommended": false
  }
]
```

- [ ] **Step 2: Add Cloud Providers section to SettingsScreen**

Add a new `Card` section above Local Models:
```kotlin
Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = Modifier.fillMaxWidth()
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Cloud API Keys", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        // List providers with status dots
        ProviderRow("OpenAI", hasKey = true) // wire to backend status
        ProviderRow("Anthropic", hasKey = false)
        ProviderRow("Google", hasKey = true)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { /* open add provider form */ }) {
            Text("Add provider")
        }
    }
}
```

- [ ] **Step 3: Add Device info card**

```kotlin
Card(...) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Device info", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        InfoRow("RAM", "${totalRamGb} GB", MonoFontFamily)
        InfoRow("SoC", Build.SOC_MODEL, MonoFontFamily)
        InfoRow("Available", "${availableStorageGb} GB", MonoFontFamily)
    }
}
```

- [ ] **Step 4: Add panel-line dividers between sections**

Same `HorizontalDivider` pattern as ChatScreen.

- [ ] **Step 5: Verify builds**

Run: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q`
Expected: both green.

- [ ] **Step 6: Commit**

```bash
git add feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/SettingsScreen.kt feature/localmodel/src/main/assets/models.json
git commit -m "feat: SettingsScreen — Cloud Providers, Device info, bundled models.json"
```

---

### Task 8: HF API Model Refresh

**Files:**
- Modify: `feature/localmodel/src/main/kotlin/com/mdyerapis/sable/feature/localmodel/LocalModelRepository.kt`

**Interfaces:**
- Consumes: bundled `models.json`, device RAM/SoC info.
- Produces: `refreshAvailableModels()` method that queries HF API, merges with bundled, filters by device capability.

- [ ] **Step 1: Add HF API query method**

```kotlin
suspend fun refreshAvailableModels(): List<LocalModelSpec> {
    val bundled = loadBundledModels()
    return try {
        val hfModels = fetchHfModels()
        val merged = (bundled + hfModels).distinctBy { it.id }
        merged.filter { it.minRamBytes <= deviceRamBytes }
    } catch (e: Exception) {
        bundled.filter { it.minRamBytes <= deviceRamBytes }
    }
}

private suspend fun fetchHfModels(): List<LocalModelSpec> {
    // GET https://huggingface.co/api/models?filter=gguf&sort=downloads&limit=20
    // Parse response, filter for mobile/llama.cpp compatible, map to LocalModelSpec
}
```

- [ ] **Step 2: Add device RAM detection**

```kotlin
private val deviceRamBytes: Long
    get() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem
    }
```

- [ ] **Step 3: Update SettingsScreen to show refresh state**

Add "Updated just now" / "Offline — showing cached" text below "Available for your device" header.

- [ ] **Step 4: Verify builds**

Run: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q`
Expected: both green.

- [ ] **Step 5: Commit**

```bash
git add feature/localmodel/src/main/kotlin/com/mdyerapis/sable/feature/localmodel/LocalModelRepository.kt
git commit -m "feat: HF API model refresh with device RAM filtering"
```

---

### Task 9: Onboarding Screen — Sable Story

**Files:**
- Modify: `feature/onboarding/src/main/kotlin/com/mdyerapis/sable/feature/onboarding/OnboardingScreen.kt`
- Modify: `feature/onboarding/src/main/kotlin/com/mdyerapis/sable/feature/onboarding/OnboardingViewModel.kt`

**Interfaces:**
- Consumes: `SableTheme` tokens, `SableTypography`, droid mascot drawable.
- Produces: 4-frame onboarding story with `AnimatedContent` transitions, connect form.

- [ ] **Step 1: Add frame state to OnboardingViewModel**

```kotlin
data class OnboardingUiState(
    val currentFrame: Int = 0, // 0-3
    val baseUrl: String = "",
    val token: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDone: Boolean = false,
)

fun nextFrame() {
    _uiState.value = _uiState.value.copy(currentFrame = (_uiState.value.currentFrame + 1).coerceAtMost(3))
}

fun skipToConnect() {
    _uiState.value = _uiState.value.copy(currentFrame = 3)
}
```

- [ ] **Step 2: Rewrite OnboardingScreen with AnimatedContent**

```kotlin
@Composable
fun OnboardingScreen(...) {
    val uiState by viewModel.uiState.collectAsState()

    Box(...) {
        AnimatedContent(
            targetState = uiState.currentFrame,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            }
        ) { frame ->
            when (frame) {
                0 -> StoryFrame("Sable stays quiet.", R.drawable.ic_launcher_foreground)
                1 -> StoryFrame("Sable remembers.", R.drawable.ic_launcher_foreground) // with ember
                2 -> StoryFrame("Sable acts when you ask.", R.drawable.ic_launcher_foreground) // with paw
                3 -> ConnectForm(uiState, viewModel)
            }
        }

        // Skip button (top-right, frames 0-2 only)
        if (uiState.currentFrame < 3) {
            TextButton(
                onClick = { viewModel.skipToConnect() },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text("Skip")
            }
        }
    }
}

@Composable
private fun StoryFrame(text: String, mascotRes: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Droid mascot with breathing animation
        val scale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000),
                repeatMode = RepeatMode.Reverse
            )
        )
        Image(
            painter = painterResource(mascotRes),
            contentDescription = "Sable",
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 3: Update connect form**

Replace "Personal Assistant" → "Sable", "Private self-learning AI assistant..." → "Quietly capable.", "Connect to Assistant" → "Connect". Use `SableTypography.headlineMedium` for wordmark.

- [ ] **Step 4: Add tap-to-advance**

Add `Modifier.clickable { viewModel.nextFrame() }` to `StoryFrame` (frames 0-2 only).

- [ ] **Step 5: Verify builds**

Run: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q`
Expected: both green.

- [ ] **Step 6: Commit**

```bash
git add feature/onboarding/src/main/kotlin/com/mdyerapis/sable/feature/onboarding/
git commit -m "feat: Onboarding — 4-frame Sable story with AnimatedContent"
```

---

### Task 10: Page Transitions & Motion Polish

**Files:**
- Modify: `app/src/main/kotlin/com/mdyerapis/sable/nav/AppNavHost.kt`
- Modify: `feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/ChatScreen.kt`

**Interfaces:**
- Consumes: `SableTheme`, `prefers-reduced-motion` check.
- Produces: `slideInHorizontally` forward nav, `slideOutToLeft` back nav, overflow `expandVertically`.

- [ ] **Step 1: Add page transitions to NavHost**

```kotlin
composable(
    "chat",
    enterTransition = { slideInHorizontally { it } },
    exitTransition = { slideOutHorizontally { -it } },
    popEnterTransition = { slideInHorizontally { -it } },
    popExitTransition = { slideOutHorizontally { it } }
) { ... }
```

Apply same pattern to `sessions`, `settings`.

- [ ] **Step 2: Add overflow menu animation**

Wrap `DropdownMenu` in `AnimatedVisibility` with `expandVertically` + `fadeIn` (200ms).

- [ ] **Step 3: Respect prefers-reduced-motion**

Check `LocalReducedMotion.current` (Compose accessibility) and disable all custom animations when true.

- [ ] **Step 4: Verify builds**

Run: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q`
Expected: both green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/mdyerapis/sable/nav/AppNavHost.kt feature/chat/src/main/kotlin/com/mdyerapis/sable/feature/chat/ChatScreen.kt
git commit -m "feat: page transitions + overflow animation + reduced-motion respect"
```

---

### Task 11: DESIGN.md Update & Final Verification

**Files:**
- Modify: `DESIGN.md`

**Interfaces:**
- Produces: updated DESIGN.md matching new tokens, on-device screencaps.

- [ ] **Step 1: Update DESIGN.md foundations**

Replace palette table, typography section, shape section, motion section with values from spec §1, §6.

- [ ] **Step 2: Run full test suite**

Run: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q`
Expected: both green.

- [ ] **Step 3: Push APK to device and screencap**

```bash
adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/sable-uiux.apk
adb shell pm install -r /data/local/tmp/sable-uiux.apk
adb shell am start -n com.mdyerapis.sable/.MainActivity
```

Screencap each surface (light + dark): onboarding frames, home empty with droid + chips, chat with messages, composer, overflow, settings.

- [ ] **Step 4: Contrast spot-check**

Verify all primary-on-surface pairs ≥ 4.5:1 using WebAIM contrast checker or equivalent.

- [ ] **Step 5: Commit**

```bash
git add DESIGN.md
git commit -m "docs: DESIGN.md updated for Sable UI/UX overhaul"
```
