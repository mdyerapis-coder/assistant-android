# Task 3: Composer Restyle

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/mdyerapis/sable/core/designsystem/components/Composer.kt`

**Interfaces:**
- Consumes: `SableShapes` (extraLarge = 28dp), `SableTheme` color tokens from Theme.kt.
- Produces: pill-shaped composer with `surfaceContainerHigh` fill, 1dp `outlineVariant` border, inner shadow, terracotta send button, 600ms mic pulse.

**Steps:**

1. Update composer shape to `MaterialTheme.shapes.extraLarge` (28dp pill).

2. Change `Surface` color to `MaterialTheme.colorScheme.surfaceContainerHigh`.

3. Add 1dp `outlineVariant` border via `Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge)`.

4. Add inner shadow effect using `Modifier.drawBehind` with a subtle radial gradient from `surfaceContainer` at top to transparent at bottom (2dp blur).

5. Update send button: `containerColor = MaterialTheme.colorScheme.primary`, `contentColor = MaterialTheme.colorScheme.onPrimary`.

6. Update mic pulse: `animateFloatAsState` with `tween(600)` and `EaseInOut` easing.

7. Verify: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q` both green.

8. Commit: `feat: Composer restyle — 28dp pill, tactile inset, terracotta send`

**Global Constraints:**
- No hardcoded hex — read `MaterialTheme.colorScheme`.
- Respect `prefers-reduced-motion`.
