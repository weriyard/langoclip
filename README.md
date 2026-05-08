# Floating Clipboard

Android app z pływającą ikoną (chathead/bubble) zawsze na wierzchu. Po kliknięciu otwiera Activity z polem tekstowym, do którego automatycznie wklejana jest zawartość schowka.

## Stack
- Kotlin + Compose + Material 3
- minSdk 26, targetSdk 35
- AGP 8.7.3, Kotlin 2.0.21

## Struktura

```
app/src/main/
├── AndroidManifest.xml
├── kotlin/com/floatingclipboard/
│   ├── MainActivity.kt          # UI + odczyt schowka w onResume
│   ├── BubbleService.kt         # Foreground service trzymający bubble
│   ├── BubbleTouchListener.kt   # Drag vs click
│   └── ui/theme/Theme.kt
└── res/
    ├── layout/bubble.xml
    ├── drawable/                # ic_bubble, bubble_bg, launcher
    ├── mipmap-anydpi-v26/       # adaptive icon
    └── values/                  # strings, themes
```

## Jak uruchomić

1. Otwórz katalog w Android Studio (Hedgehog albo nowszy) — IDE samo wygeneruje gradle wrapper i zsynchronizuje.
   - Alternatywnie z CLI: `gradle wrapper --gradle-version 8.10 && ./gradlew :app:installDebug`
2. Po pierwszym uruchomieniu kliknij **"Włącz pływającą ikonę"** — system poprosi o uprawnienie *Display over other apps* (i o notyfikacje na Androidzie 13+).
3. Po wróceniu do aplikacji ikona pojawi się i będzie pływać nad innymi appkami.
4. Klik w ikonę → otwiera się MainActivity z aktualną zawartością schowka w polu.
5. Drag → przesuwasz ikonę.

## Co warto wiedzieć

- **Schowek** czytany jest w `onResume` Activity (po Androidzie 10 nie da się go odczytać z tła — Service tego nie zrobi). Przy każdym kliknięciu w bubble Activity wraca do foregroundu i ponownie wklej.
- **Foreground Service** wymaga stałej notyfikacji — to systemowy wymóg, nie da się tego ukryć od Androida 8.
- **Producenci** (Xiaomi/Oppo/Vivo) mogą wymagać dodatkowego *"display popup while running in background"* w ustawieniach — bez tego MIUI/ColorOS będzie killować service.
- **Battery optimization** może ubić service po dłuższym czasie. Dla niezawodności możesz dodać request o `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Od Androida 12 system wyświetla toast *"App pasted from your clipboard"* — tego nie da się wyłączyć i nie powinieneś próbować.

## Dalsze kroki, jeśli chcesz rozbudować

- Snap to edge — po puszczeniu palca ikona "lipnie" do najbliższej krawędzi ekranu.
- Persystencja pozycji bubble w `SharedPreferences` / DataStore.
- Edge cases klawiatury: gdy soft input wjeżdża, layout params bubble nie zmieniają się — zwykle działa OK, ale przy `FLAG_LAYOUT_NO_LIMITS` trzeba uważać.
- Wariant z auto-paste w docelowej aplikacji (zewnętrznej) — to wymaga AccessibilityService, znacznie trudniejszy temat.
