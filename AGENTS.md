# AGENTS.md — Wind Widget

Rules for every agent working here. The agent on this project is **Claude** (Claude Code).

## Context
- Goal and scope: PROJECT.md. Current state: STATUS.md. Past choices: DECISIONS.md.

## Project overview

Wind Widget is an Android app that displays real-time wind data from Ecowitt weather stations as
home screen widgets. It fetches wind speed, direction, and gust data from the Ecowitt API and
displays it in various widget styles.

### Tech stack
- Kotlin, min SDK 26 (Android 8.0), target SDK 34, Gradle Kotlin DSL
- OkHttp 4.12.0 (HTTP), Gson 2.10.1 (JSON), WorkManager 2.9.0 (background updates)
- Material Components 1.11.0, AndroidX AppCompat & ConstraintLayout
- Tests: JUnit 4, MockK, Robolectric 4.13 (native graphics, for widget previews)

### Structure
```
app/src/main/java/com/windwidget/
├── MainActivity.kt                # Main app screen - manages saved locations
├── WindWidgetConfigureActivity.kt # Widget configuration when adding widgets
├── SavedLocation.kt               # Data class + LocationManager for saved locations
├── WindData.kt                    # Data model for wind readings
├── WindDataFetcher.kt             # Ecowitt API client (class name: EcowittDataFetcher)
├── WindUpdateScheduler.kt         # WorkManager: periodic + on-demand updates, WindUpdateWorker
├── BootReceiver.kt                # Restarts updates after device reboot
├── RetryInterceptor.kt / SecurePrefs.kt
│
├── WindWidget.kt                  # Chart widget (4x2)      → WindChartRenderer.kt
├── WindWidgetHorizontal.kt        # Bar widget (4x1)        → WindBarRenderer.kt
├── WindWidgetClean.kt             # Material style (4x2)    → WindCleanRenderer.kt
├── WindWidgetCompact.kt           # Compact (2x2)           → WindCompactRenderer.kt
├── WindWidgetModern.kt            # Dark glass (4x2)        → WindModernRenderer.kt
└── WindWidgetTide.kt              # Wind + tide (4x2)       → WindTideRenderer.kt
    (WindguruFetcher, TideFetcher, WindTideLocation.kt, WindTideConfigureActivity)
```

### Ecowitt API
- `/api/v3/device/history` for the last 3 hours (5-min intervals); `/api/v3/device/real_time` for current readings
- Wind speed unit: 8 (knots). Requires Application Key, API Key, MAC address

### Data storage
Two SharedPreferences files (encrypted via `SecurePrefs`):
1. `wind_widget_prefs` — per-widget credentials (`widget_<id>_*`) and cached data
2. `wind_widget_locations` — saved locations list (JSON array)

Plain (not encrypted) `wind_tide_prefs`: wind+tide places, each widget's place, Windguru and tide caches.
No secrets: Windguru needs only a Referer, and tabuasdemare is a public page.

### Update mechanism
- Every update goes through `WindUpdateScheduler.enqueueUpdate(...)` → `WindUpdateWorker`
  (periodic 30 min, tap-to-refresh with `forceRefresh`, resize, boot, configure)
- 5-minute cache; on failure the last real reading is shown as `STALE`

### UI theme
- Dark background #0D0F12, card #1A1D21, accent #4ADE80 (green)
- Text: #FFFFFF primary, #B0B0B0 secondary, #666666 tertiary
- Modern widget: blue accent #3B82F6 / #60A5FA, Portuguese labels

### Widget previews
Picker previews are PNGs in `res/drawable-nodpi/widget_preview_<style>.png`, rendered by the real
renderers in `WidgetPreviewGenerator` (unit test). After changing a renderer, run
`./gradlew testDebugUnitTest --tests '*WidgetPreviewGenerator'` and copy
`app/build/widget-previews/*.png` into `res/drawable-nodpi/`.

## Rules
- Language: code/identifiers in English. Widget UI text may be Portuguese (Modern widget already is).
- Secrets: never in files. Ecowitt keys are entered in the app at runtime; the test keys live in Phase (key names only).
- Git: feature branch + PR; never commit to `master` unless asked. Plain-text commit messages, no ANSI
  escape or color codes. Commits carry the `Co-Authored-By: Claude ...` trailer.
- All widget updates go through `WindUpdateScheduler.enqueueUpdate(...)` (WorkManager). Never launch
  fire-and-forget coroutines from a provider/receiver (see DECISIONS 2026-10-03).
- Never cache or present demo data as real wind data. Demo is only for "no credentials" or
  "never fetched successfully", and it is always labelled `DEMO`.
- Bug fixes in `EcowittDataFetcher` come with a JVM unit test in `app/src/test`.
- Before finishing: update STATUS.md if state changed; add to DECISIONS.md for non-trivial choices.

## Commands
- JDK 17: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
- Build: `./gradlew assembleDebug`
- Test: `./gradlew testDebugUnitTest`
- CI: `.github/workflows/build.yml` (unit tests + debug APK)
- Deploy: TBD (sideload APK)
