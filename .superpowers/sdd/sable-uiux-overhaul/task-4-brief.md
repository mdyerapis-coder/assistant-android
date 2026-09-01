# Task 4: MessageBubble Restyle

**Files:**
- Modify: `core/designsystem/src/main/kotlin/com/mdyerapis/sable/core/designsystem/components/MessageBubble.kt`

**Interfaces:**
- Consumes: `SableShapes` (large = 20dp), `SableTheme` color tokens.
- Produces: user bubble with 20dp rounded corners, `surfaceContainerHigh` fill.

**Steps:**

1. Update bubble shape to `MaterialTheme.shapes.large` (20dp rounded).

2. Change user bubble `containerColor` to `MaterialTheme.colorScheme.surfaceContainerHigh`.

3. Verify: `./gradlew testDebugUnitTest --no-daemon -q && ./gradlew :app:assembleDebug --no-daemon -q` both green.

4. Commit: `feat: MessageBubble restyle — 20dp rounded, surfaceContainerHigh`

**Global Constraints:**
- No hardcoded hex — read `MaterialTheme.colorScheme`.
- Assistant messages remain plain text (no bubble) — only user bubble changes.
