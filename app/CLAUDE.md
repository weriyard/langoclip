# CLAUDE.md

Context for Claude Code working on this project. Concise — only what isn't obvious from the code itself.

## What this app is

Android app with a floating bubble (chathead) always on top. Tapping the bubble opens `MainActivity` with a text field that auto-populates with the current system clipboard contents. Drag moves the bubble.

This is **not** a home screen widget — it's an overlay window drawn by `WindowManager` on top of other apps.

## Stack and versions

- Kotlin 2.0.21, AGP 8.7.3
- Jetpack Compose (BOM 2024.12.01) + Material 3
- minSdk 26, targetSdk 35, compileSdk 35
- JDK 17 for Gradle
- Version catalog: `gradle/libs.versions.toml`
- Single-module: `:app`

## Architecture — critical decisions

### Main flow

```
User taps bubble → BubbleService.launchActivity()
  → MainActivity returns to foreground
  → ON_RESUME in PasteScreen
  → readClipboard() and populate the field
```

### Why Activity, not Service, reads the clipboard

Since Android 10 (API 29), `ClipboardManager.getPrimaryClip()` returns null when the app isn't in the foreground (unless it's the active IME or default assistant). A Service has no UX-level foreground — an Activity does. So we read the clipboard in `MainActivity`, and the bubble is just a trigger to bring the Activity forward.

**Don't try** to read the clipboard in `BubbleService` — it works locally on some ROMs (custom builds where the restriction is loosened), but it's not guaranteed and looks suspicious during review.

### specialUse foreground service

The bubble lives in `BubbleService` as a foreground service with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` (required on API 34+). The manifest declares a `<property>` with the subtype description — without it, install on API 34+ throws `SecurityException` on the first `startForeground` call. Not optional.

The notification is persistent (`setOngoing(true)`) and `IMPORTANCE_LOW` to stay quiet — but it **cannot be hidden** since Android 8, that's a system requirement for foreground services.

### Drag vs click in BubbleTouchListener

Distinguished by two thresholds: `scaledTouchSlop` (finger movement) and `getLongPressTimeout()` (duration). Click only fires if both are below threshold. `view.performClick()` is invoked for a11y compatibility (lint would complain otherwise).

To tune sensitivity: bump the multiplier on `touchSlop` in the `ACTION_UP` branch of `onTouch`.

## File layout

```
app/src/main/
├── AndroidManifest.xml          # 4 permissions + service with <property>
├── kotlin/com/langoclip/app/
│   ├── MainActivity.kt          # Compose UI, lifecycle observer for clipboard
│   ├── BubbleService.kt         # FGS + WindowManager overlay
│   ├── BubbleTouchListener.kt   # Drag/click detection
│   └── ui/theme/Theme.kt        # Material 3 + dynamic colors (API 31+)
└── res/
    ├── layout/bubble.xml         # FrameLayout with ImageView (56dp circle)
    ├── drawable/                 # ic_bubble, bubble_bg (oval), launcher icons
    ├── mipmap-anydpi-v26/        # adaptive icon
    └── values/                   # strings, themes
```

`sourceSets["main"].kotlin.srcDirs("src/main/kotlin")` in `app/build.gradle.kts` — we use `kotlin/`, not `java/`.

## Build and run

```bash
# First-time setup (if no gradle wrapper):
gradle wrapper --gradle-version 8.10

# Standard dev cycle:
./gradlew :app:installDebug      # build + install on connected device
./gradlew :app:assembleDebug     # APK at app/build/outputs/apk/debug/

# Cleanup:
./gradlew clean
```

In Android Studio: opening the project root (where `settings.gradle.kts` lives) is enough — sync pulls dependencies.

## Platform-specific gotchas

### OEM quirks
- **Xiaomi/MIUI, Oppo/ColorOS, Vivo**: extra permission *"display popup window while running in background"* in vendor settings. Without it the service gets killed or the overlay doesn't show despite `Display over other apps` being granted.
- **Battery optimization**: even on stock Android the service can die after hours. Consider `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` if reliability is critical.

### Samsung One UI (target devices: S25 Ultra + Tab S10+)
- **Sleeping apps / Deep sleeping apps**: One UI has its own sleep mechanism, more aggressive than stock Android. An app in "Deep sleep" won't even start a foreground service. Add LangoClip to *Settings → Battery → Background usage limits → Never sleeping apps*.
- **DeX mode (Tab S10+)**: overlays behave differently — they may not float over DeX windows (different window manager). We don't use `TYPE_APPLICATION_OVERLAY` in a DeX-compatible way here. If DeX support becomes a requirement, test and add conditional logic via `Configuration.uiMode`.
- **Game Booster**: blocks overlays per-game. User-side problem, not a code one — mention as a known limitation in README/onboarding.
- **Edge Panels**: Samsung's own floating UI on the right edge. Doesn't conflict, but for snap-to-edge it's worth avoiding that zone or starting the bubble elsewhere.
- **Split screen / Pop-up View**: bubble works over split screen; Pop-up View occasionally overlaps the overlay — verify before release.

### Tablet form factor (Tab S10+, 12.4")
- **Initial position bug**: `WindowManager.LayoutParams.x/y` are pixels, not dp. The hardcoded `x=50, y=300` in `BubbleService.addBubble()` resolves to different positions across DPIs. **TODO**: convert `dp → px` via `resources.displayMetrics.density` so the position is consistent between S25 Ultra (~505 ppi) and Tab S10+ (~266 ppi).
- **MainActivity layout**: on a 12.4" screen `OutlinedTextField` stretches across the full width. Readability would benefit from `Modifier.widthIn(max = 600.dp)` with centering, or a responsive layout via `WindowSizeClass`.
- **Orientation**: the bubble currently doesn't react to rotation (position stays in old-orientation pixels, can land off-screen). Add `onConfigurationChanged` in the Service and clamp to the new screen bounds.

### Android 12+
- Toast *"App pasted from your clipboard"* on every read. System-level, can't be disabled.

### Permission flow
- `SYSTEM_ALERT_WINDOW` doesn't go through the standard runtime permission dialog — must send `Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)` and handle the return via `ActivityResultContract`. See `MainActivity.overlayLauncher`.
- `POST_NOTIFICATIONS` (API 33+) follows the standard runtime flow.

## Implementation status

### Done
- Bubble with drag and click
- Foreground service with correct `specialUse` type
- Auto-paste clipboard into Activity
- Permission flow for overlay and notifications
- Material 3 theme with dynamic colors
- Adaptive launcher icon

### Not done (potential next steps)
- **Px → dp for bubble position**: urgent for cross-device consistency (see: Tablet form factor). `(dp * resources.displayMetrics.density).toInt()`.
- **Snap-to-edge**: animate bubble to nearest edge after `ACTION_UP`. `ValueAnimator` on `params.x` + `windowManager.updateViewLayout`.
- **Position persistence**: store `params.x/y` to DataStore on `ACTION_UP`, restore in `BubbleService.onCreate`.
- **Rotation handling**: `onConfigurationChanged` in Service, clamp position to new screen bounds.
- **Responsive Activity UI**: `WindowSizeClass` to distinguish phone/tablet, `widthIn(max = 600.dp)` for the text field on tablet.
- **Battery optimization request**: explanation dialog before sending `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **Long-press menu**: hold the bubble → menu with options (e.g. "disable", "copy history").
- **Clipboard history**: listen via `ClipboardManager.OnPrimaryClipChangedListener` and keep a local list. **Note**: the listener also falls under foreground restrictions since API 29.
- **Auto-paste into another app**: requires `AccessibilityService`. Significantly harder, and tread carefully with Play Store review (those permissions are heavily scrutinized).

## Code conventions

- Idiomatic Kotlin: `?.let`, `runCatching` instead of try/catch where the stack trace isn't needed.
- Compose: state hoisting, screens as top-level `@Composable` functions in the Activity files, theme in `ui/theme/`.
- KDoc for non-trivial classes; inline comments only where intent isn't obvious from the code.
- No Hilt / DI — the project is small, manual constructor injection is enough.
- No tests at the moment. If added: unit tests for `BubbleTouchListener` (pure logic, easy), instrumented tests for the permission flow.

## Don't change without a reason

- `foregroundServiceType="specialUse"` + `<property>` in the manifest — this isn't cargo-culting, API 34 requires it.
- `TYPE_APPLICATION_OVERLAY` — don't fall back to `TYPE_PHONE`/`TYPE_SYSTEM_ALERT`, both deprecated since Oreo.
- `FLAG_NOT_FOCUSABLE` in layout params — without it the bubble grabs focus and blocks input behind it.
- `Intent.FLAG_ACTIVITY_REORDER_TO_FRONT` when launching from the bubble — without it new entries spawn fresh tasks instead of returning to the existing one.
