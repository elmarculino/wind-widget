# Wind Widget

A modern Android home screen widget that displays real-time wind data from your Ecowitt weather station.

![Widget Preview](docs/preview.png)

## Features

- **3-hour wind history chart** with smooth gradient fill
- **Real-time current readings** (speed, direction, gusts)
- **Wind direction arrows** overlaid on the chart
- **Beaufort scale** indicator
- **Auto-refresh** every 30 minutes via WorkManager
- **Tap to refresh** manually
- **Offline support** with cached data
- **Dark modern UI** optimized for AMOLED screens

## Widget Display

```
┌─────────────────────────────────────────────┐
│ São Miguel dos Milagres          Ter 20.1.  │
│                                             │
│     ↖  ↖  ↖  ↖  ↖  ↖  ↖  ↖  ↖             │
│   ~~~~~~~~~~~~~~~~~~~~~~~~~~~~    ▌7       │
│  /▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓\    ▌5  Bft  │
│ /▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓\   ▌3       │
│                                             │
│ 17:30  18:00  18:30  19:00  19:30  20:00   │
├─────────────┬─────────────┬─────────────────┤
│  ← ENE 74°  │  11.5 nós   │    max: 15      │
└─────────────┴─────────────┴─────────────────┘
```

## Requirements

- Android 8.0 (API 26) or higher
- Ecowitt weather station with cloud API access
- Ecowitt API credentials (Application Key + API Key)

## Installation

### Download APK

Get the latest release from the [Releases](../../releases) page.

### Build from Source

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/wind-widget.git
cd wind-widget

# Build debug APK
./gradlew assembleDebug

# APK location: app/build/outputs/apk/debug/app-debug.apk
```

## Configuration

### Getting Ecowitt API Credentials

1. Go to [ecowitt.net](https://www.ecowitt.net/)
2. Log in to your account
3. Navigate to **Settings** → **API**
4. Create a new Application Key
5. Note your **Application Key**, **API Key**, and device **MAC Address**

### Widget Setup

1. Long-press on your home screen
2. Select **Widgets**
3. Find **Wind Chart** and drag to home screen
4. Enter your Ecowitt credentials:
   - **Application Key**: From ecowitt.net
   - **API Key**: From ecowitt.net
   - **MAC Address**: Your device MAC (e.g., `AA:BB:CC:DD:EE:FF`)
   - **Location Name**: Display name for the widget header

## API Endpoints Used

| Endpoint | Purpose |
|----------|---------|
| `/api/v3/device/history` | Last 3 hours of wind data for the chart |
| `/api/v3/device/real_time` | Current wind speed/direction for bottom bar |

Both requests use `wind_speed_unitid=8` to receive data in **knots**.

## Project Structure

```
wind-widget/
├── app/src/main/
│   ├── java/com/windwidget/
│   │   ├── WindData.kt              # Data model
│   │   ├── WindDataFetcher.kt       # Ecowitt API client
│   │   ├── WindChartRenderer.kt     # Canvas-based chart rendering
│   │   ├── WindWidget.kt            # AppWidgetProvider
│   │   ├── WindWidgetConfigureActivity.kt
│   │   ├── WindUpdateScheduler.kt   # WorkManager scheduling
│   │   ├── MainActivity.kt
│   │   └── BootReceiver.kt
│   └── res/
│       ├── layout/
│       ├── drawable/
│       ├── values/
│       └── xml/
├── build.gradle.kts
└── settings.gradle.kts
```

## Dependencies

- **OkHttp** - HTTP client for API requests
- **Gson** - JSON parsing
- **WorkManager** - Background updates
- **Material Components** - UI components

## License

MIT License - see [LICENSE](LICENSE) for details.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/improvement`)
3. Commit your changes (`git commit -m 'Add feature'`)
4. Push to the branch (`git push origin feature/improvement`)
5. Open a Pull Request
