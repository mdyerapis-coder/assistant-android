# Task 2: App Icon — Droid Mascot

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.png`
- Modify: `app/src/main/res/drawable/ic_launcher_background.png`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Modify: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
- Modify: `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.png`

**Interfaces:**
- Produces: droid mascot icon at all densities, adaptive XML with monochrome layer.
- Consumed by: Task 6 (SessionsScreen uses `ic_launcher_foreground` as droid mascot), Task 9 (Onboarding uses same).

**Steps:**

1. Create a sable-animal droid mascot: dark fur body (#2A2A2A), warm ember eyes (#E08A6B), compact rounded limbs, no text. Friendly, reads at 48dp. Export as PNG at 432×432 (xxxhdpi base). Create monochrome variant (single-tone #E08A6B silhouette).

2. Generate rasters: mdpi 108×108, hdpi 162×162, xhdpi 216×216, xxhdpi 324×324, xxxhdpi 432×432. Place in respective mipmap-* directories.

3. Update adaptive XML (`ic_launcher.xml` and `ic_launcher_round.xml`) to include monochrome layer:
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```

4. Replace `ic_launcher_background.png` with solid #171717 at all densities.

5. Verify: `./gradlew :app:assembleDebug --no-daemon -q` green.

6. Commit: `feat: droid mascot launcher icon at all densities`

**Note:** Since you cannot generate actual image files programmatically, create placeholder PNGs (solid color blocks with the right dimensions) and document in the report that the actual mascot artwork needs to be designed and dropped in. The adaptive XML and raster structure should be correct.

**Global Constraints:**
- Icon must read clearly at 48dp (smallest launcher size).
- Background #171717 (dark default).
- Monochrome layer uses #E08A6B.
