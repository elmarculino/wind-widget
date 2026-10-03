# Claude Code Instructions

## Project Overview

Wind Widget is an Android app that displays real-time wind data from Ecowitt weather stations as home screen widgets. It fetches wind speed, direction, and gust data from the Ecowitt API and displays it in various widget styles.

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34
- **Build System**: Gradle with Kotlin DSL

### Key Dependencies
- OkHttp 4.12.0 - HTTP requests
- Gson 2.10.1 - JSON parsing
- WorkManager 2.9.0 - Periodic background updates
- Material Components 1.11.0 - UI components
- AndroidX AppCompat & ConstraintLayout

## Project Structure

```
app/src/main/java/com/windwidget/
├── MainActivity.kt              # Main app screen - manages saved locations
├── WindWidgetConfigureActivity.kt # Widget configuration when adding widgets
├── SavedLocation.kt             # Data class + LocationManager for saved locations
├── WindData.kt                  # Data model for wind readings
├── WindDataFetcher.kt           # Ecowitt API client (class name: EcowittDataFetcher)
├── WindUpdateScheduler.kt       # WorkManager for 30-min periodic updates
├── BootReceiver.kt              # Restarts updates after device reboot
│
├── WindWidget.kt                # Main chart widget (4x2)
├── WindWidgetHorizontal.kt      # Bar widget (4x1)
├── WindWidgetClean.kt           # Material Design style (4x2)
├── WindWidgetCompact.kt         # Compact widget (2x2)
├── WindWidgetModern.kt          # Dark glass style (4x2)
│
├── WindChartRenderer.kt         # Canvas rendering for chart widget
├── WindBarRenderer.kt           # Canvas rendering for bar widget
├── WindCleanRenderer.kt         # Canvas rendering for clean widget
├── WindCompactRenderer.kt       # Canvas rendering for compact widget
└── WindModernRenderer.kt        # Canvas rendering for modern widget
```

## Key Concepts

### Ecowitt API
- Uses `/api/v3/device/history` for last 3 hours of data (5-min intervals)
- Uses `/api/v3/device/real_time` for current readings
- Wind speed unit: 8 (knots)
- Requires: Application Key, API Key, MAC Address

### Data Storage
Two SharedPreferences files:
1. `wind_widget_prefs` - Active widget credentials and cached data
2. `wind_widget_locations` - Saved locations list (JSON array)

### Widget Types
All widgets share the same data fetcher and update mechanism:
1. **Wind Chart** (4x2) - Full chart with 3-hour history
2. **Wind Bar** (4x1) - Horizontal bar chart
3. **Wind Clean** (4x2) - Material Design version
4. **Wind Compact** (2x2) - Minimal current conditions
5. **Wind Modern** (4x2) - Dark glass UI with blue accents

### Update Mechanism
- WorkManager schedules updates every 30 minutes
- 5-minute cache duration for API responses
- Tap-to-refresh on widgets
- BootReceiver restarts scheduling after reboot

## UI Theme
- Dark background: #0D0F12
- Card background: #1A1D21
- Accent color: #4ADE80 (green)
- Text colors: #FFFFFF (primary), #B0B0B0 (secondary), #666666 (tertiary)

## Git Commits

- Do not use ANSI escape characters or color codes in git commit messages
- Keep commit messages plain text only
