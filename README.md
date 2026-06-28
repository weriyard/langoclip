# LangoClip

Android app with a floating icon (chathead/bubble) always on top. Tapping it opens an Activity with a text field into which the clipboard contents are automatically pasted.

## Stack
- Kotlin + Compose + Material 3
- minSdk 26, targetSdk 35
- AGP 8.7.3, Kotlin 2.0.21

## Structure

```
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/langoclip/app/
│   ├── MainActivity.kt          # UI + clipboard read in onResume
│   ├── BubbleService.kt         # Foreground service holding the bubble
│   ├── BubbleTouchListener.kt   # Drag vs click
│   └── ui/theme/Theme.kt
└── res/
    ├── layout/bubble.xml
    ├── drawable/                # ic_bubble, bubble_bg, launcher
    ├── mipmap-anydpi-v26/       # adaptive icon
    └── values/                  # strings, themes
```

## How to run

1. Open the directory in Android Studio (Hedgehog or newer) — the IDE will generate the gradle wrapper and sync on its own.
   - Alternatively from the CLI: `gradle wrapper --gradle-version 8.10 && ./gradlew :app:installDebug`
2. On first launch, tap **"Enable floating icon"** — the system will ask for the *Display over other apps* permission (and for notifications on Android 13+).
3. After returning to the app, the icon will appear and float over other apps.
4. Tap the icon → MainActivity opens with the current clipboard contents in the field.
5. Drag → you move the icon.

## Things worth knowing

- **The clipboard** is read in the Activity's `onResume` (after Android 10 it can't be read from the background — a Service won't do it). Every time you tap the bubble, the Activity returns to the foreground and pastes again.
- **A Foreground Service** requires a persistent notification — this is a system requirement and can't be hidden as of Android 8.
- **Manufacturers** (Xiaomi/Oppo/Vivo) may require an additional *"display popup while running in background"* setting — without it MIUI/ColorOS will kill the service.
- **Battery optimization** may kill the service after a while. For reliability you can add a `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` request.
- As of Android 12 the system shows a *"App pasted from your clipboard"* toast — this can't be disabled and you shouldn't try to.

## Next steps if you want to extend it

- Snap to edge — when you release your finger, the icon "snaps" to the nearest screen edge.
- Persisting the bubble position in `SharedPreferences` / DataStore.
- Keyboard edge cases: when the soft input slides in, the bubble's layout params don't change — usually works fine, but you have to be careful with `FLAG_LAYOUT_NO_LIMITS`.
- A variant with auto-paste into the target (external) app — this requires an AccessibilityService, a considerably harder topic.
