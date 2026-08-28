# Project structure

[中文版本](PROJECT_STRUCTURE_CN.md)

Toolbox uses one Gradle module with packages organized by capability. The v0.1 scope does not justify multiple modules, Clean Architecture, or fixed `data/domain/presentation` layers.

## Repository layout

```text
toolbox_android/
├── AGENTS.md                    # Repository-wide development rules
├── README.md                    # English project entry point
├── README_CN.md                 # Chinese project entry point
├── docs/
│   ├── PROJECT_STRUCTURE.md     # This file
│   ├── PROJECT_STRUCTURE_CN.md  # Chinese translation
│   └── REFERENCES.md            # External project references and licenses
├── app/
│   ├── build.gradle.kts         # Android app configuration and dependencies
│   ├── proguard-rules.pro       # Release shrinker rules
│   └── src/
│       ├── main/                # Production code and resources
│       ├── test/                # Device-independent JVM tests; create on demand
│       └── androidTest/         # Android/device tests; create on demand
├── gradle/wrapper/              # Pinned Gradle wrapper
├── build.gradle.kts             # Root plugin versions
├── settings.gradle.kts          # Repositories and module declaration
├── gradle.properties            # Shared, committable Gradle settings
└── local.properties             # Local SDK path; never commit
```

Generated directories such as `.gradle/`, `.kotlin/`, `build/`, and `app/build/` are not source code.

## Kotlin package layout

Current structure:

```text
app/src/main/java/com/example/toolbox/
├── MainActivity.kt
├── monitor/
│   ├── MonitorOverlayService.kt
│   ├── MonitorOverlayView.kt
│   ├── MonitorReader.kt
│   └── MonitorSample.kt
├── location/
│   ├── CoordinateFormatter.kt
│   ├── LocationInfo.kt
│   └── LocationInfoReader.kt
├── network/
│   ├── NetworkInfo.kt
│   └── NetworkInfoReader.kt
├── ui/
│   ├── ToolboxApp.kt
│   ├── ToolboxTheme.kt
│   ├── Components.kt
│   ├── AppStrings.kt
│   ├── AppPreferences.kt
│   ├── HomeScreen.kt
│   ├── MonitorScreen.kt
│   ├── DeviceScreen.kt
│   ├── PlaceholderScreen.kt
│   ├── SettingsScreen.kt
│   └── AboutScreen.kt
└── device/
    ├── DeviceInfo.kt
    └── DeviceInfoReader.kt
```

Responsibility boundaries:

| Location | Contains | Must not contain |
| --- | --- | --- |
| `MainActivity.kt` | Activity lifecycle, window setup, Compose startup | Screen business logic or data reads |
| `ui/ToolboxApp.kt` | Top-level state, simple screen switching, feature coordination | Low-level system field parsing |
| `ui/*Screen.kt` | Layout, state presentation, user events | File reads, HTTP implementations, long-running loops |
| `ui/Components.kt` | Shared `InfoCard` and `InfoRow` components | Components private to one screen |
| `ui/ToolboxTheme.kt` | Material theme, colors, typography | Screen state |
| `ui/AppStrings.kt` | Centralized user-facing strings | Android system value parsing |
| `device/` | Device models and reader logic | Compose UI |
| `monitor/` | Explicitly requested sampling, overlay service, and chart view | UI-only settings or unrelated device summaries |

## Incremental v0.1 layout

The location and network packages are now active capabilities:

```text
com/example/toolbox/
├── location/
│   ├── LocationInfo.kt
│   ├── LocationInfoReader.kt
│   └── CoordinateFormatter.kt
├── network/
│   ├── NetworkInfo.kt
│   └── NetworkInfoReader.kt
└── ui/
    ├── LocationScreen.kt
    └── NetworkScreen.kt
```

- `CoordinateFormatter` is pure logic with JVM tests for decimal degrees, DMS, hemispheres, and boundary values.
- `LocationInfoReader` requests only a foreground, user-triggered location and does not request background location.
- `NetworkInfoReader` reads local connection information and performs the explicit public-IP request with a short timeout.
- Screens remain in `ui/`; models and Android API reads remain in their capability packages.

## When to split further

Prefer splitting files before adding layers or Gradle modules. Reconsider the structure only when one of these conditions is real:

- A file mixes UI, Android API reads, and formatting logic.
- A feature has multiple data sources that require replaceable implementations or offline caching.
- Multiple screens share the same non-UI business rules.
- Build time, independent delivery, or team ownership genuinely requires modules.

Until then, do not add Repository/UseCase interfaces, a DI container, a database, or pass-through wrappers.

## File naming

- Screen: `<Feature>Screen.kt`
- Data snapshot: `<Feature>Info.kt`
- Android read entry point: `<Feature>InfoReader.kt`
- Pure formatting logic: `<Value>Formatter.kt`
- Shared Compose component: use the component name; keep a small set in `Components.kt`
- JVM test: `<ClassName>Test.kt`
- Android test: `<Behavior>Test.kt`

Test packages mirror the code under test. For example:

```text
app/src/test/java/com/example/toolbox/location/CoordinateFormatterTest.kt
```

## Release evolution boundaries

- v0.1: Home, Device, Location, Network, and the explicit opt-in Live Monitor.
- v0.2: Compass, Sensors, Screen/Touch test, and Flashlight; each implemented capability gets its own package.
- v0.3: network tools; speed testing requires a separate review of servers, traffic, algorithms, and licenses.
- v0.4: advanced device fields; keep these separate from the normal summary rather than dumping `/proc` or `/sys` into Device.

Every version continues to prohibit background services by default; the Live Monitor is the explicit user-requested exception. Analytics, advertisements, accounts, and automatic network polling remain prohibited.
