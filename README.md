# Toolbox

[中文说明](README_CN.md)

Toolbox is a lightweight Android utility app that reads information on demand, with optional notification-based quick ledger entry after explicit setup. The local ledger uses SQLite and optional, manually triggered WebDAV sync; the app has no sign-in account or fixed server. The live monitor starts only after the user requests it and uses a visible foreground service.

Read [AGENTS.md](AGENTS.md) before development. See [docs/PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md) for the canonical directory layout and rules for adding future features.

## Current scope

- `Device`: device identity, Android/API, kernel, SoC/CPU, ABI, core count, memory, storage, display, battery, and a basic GPU capability summary.
- `Live monitor`: an optional compact, translucent floating line-chart overlay for CPU, GPU, memory, battery, and an FPS reference. The visible metrics are configurable. The monitor page shows current frequency for each CPU core, GPU/display details, and thermal details; the overlay can switch between per-core frequencies and CPU usage weighted by peak frequency. CPU/GPU usage is preferred; when Android exposes only frequency, the overlay marks a frequency-ratio estimate with `≈`, and restricted sources are shown as unavailable.
- The monitor page reports battery temperature, Android thermal status, thermal headroom, current display refresh rate, and supported display modes when the public APIs provide them. The entire application and overlay support the Everforest theme palette in dark and light modes, plus a dynamic wallpaper-based scheme (Android 12+) and a custom accent color, with automatic contrast adaptation for text and charts. The overlay position can be locked; in locked mode it is touch-through and its last position is saved when it is moved.
- `Location`: on-demand current location with decimal/DMS coordinates, accuracy, provider, GPS provider altitude, timestamp, GNSS constellations observed during an on-demand satellite scan, available/enabled providers, and on-demand terrain elevation (DEM) query. Foreground location permission is requested only after the user starts a location read.
- `Network`: on-demand connection details including transport, interface, local addresses, DNS, gateways, and metered state. Public IP, approximate location, and ISP lookup is a separate explicit action.
- `Ledger & Cost Amortization`: bookkeeping organized into Overview (month totals, expense categories, recent entries), Transactions (per-day history with a month switcher), and Costs (asset amortization and subscription burn rate) tabs, with built-in expense/income categories and an optional average-cost tracker for one-time long-term assets (e.g. phones, laptops with daily/monthly depreciated cost) and periodic subscriptions (weekly, monthly, quarterly, semi-annual, yearly, or a custom every-N days/weeks/months/years cycle). Persisted locally via Android SDK `SQLiteOpenHelper` with `uuid`, millisecond timestamps, and soft-delete tombstones for Last-Write-Wins merge; the app has no dependency on any fixed server. Before each schema upgrade, the previous database file is copied to app-private storage that is never backed up (the newest 3 copies are kept), so the pre-upgrade data survives a faulty migration; there is no restore button yet. A home-screen widget shows the daily cost and this month's spending with a quick add button; it needs no extra permissions, refreshes whenever entries change and about every 3 hours via the system widget scheduler, and runs no background service. Local backup uses the system file picker — JSON export writes a full re-importable backup (deletions included), CSV export produces a spreadsheet — and imports offer Merge or Replace, all without a storage permission. Manual WebDAV backup/sync is also available from the ledger overflow menu: it runs only on an explicit tap, uses HTTPS only, and writes solely to a WebDAV folder you create yourself (`ledger.json`, dated `ledger-backup-*.json` snapshots — the newest 10 are kept, and a snapshot can be restored by downloading it and importing it with Replace — and a `ledger-backups.json` index). The password is stored locally, encrypted with Android Keystore, and the feature adds no permission, dependency, or background work. Because Android auto-backup is disabled for the ledger, the ledger shows a reminder when the data has changed and the last JSON export or WebDAV sync (or, without one, the oldest data) is at least 7 days old; it offers JSON export or WebDAV sync, and Later hides it for 7 days. Entries carry custom multi-tags (the 12 built-in categories become editable tags only where old entries referenced them; new installs start tagless). Tags can be renamed, recolored, emoji-picked from presets, reordered, merged, and bulk-applied to selected entries; a per-tag detail screen shows counts and totals. Transactions and Costs can be filtered by tags in Any/All mode, and the Overview breaks spending down by tag. Periodic subscriptions and recurring fixed income are confirmed per cycle: a pending card lists due renewals on their calendar dates, each recorded renewal becomes its own transaction locking that day's rate (skipped renewals stay skipped), and stopping a subscription on a date means a renewal on that exact date is not charged. An optional scheduled end date, which must fall after the entry date, works for both cost modes: the item stays live through that day — a renewal falling on or after it is never generated, and a one-time asset amortizes across the days up to it in place of the lifespan-days setting — and afterwards the item counts as ended (assets show as Amortized) and leaves the burn-rate totals. One-time assets can be ended by selling or scrapping on a chosen date — selling records the proceeds as a linked income entry and the final cost is price minus sale; sold or scrapped assets leave the active burn rate, and the disposal can be undone. Entries can be recorded in CNY, JPY, USD, or EUR: the CNY-per-unit rate is locked into each foreign entry when it is saved, so every total, summary, widget figure, and cost computation stays in CNY and never drifts. Rates come from an explicit on-demand fallback chain — Frankfurter (api.frankfurter.dev), then the ECB daily/history feed (www.ecb.europa.eu), then the community currency-api mirror (cdn.jsdelivr.net, then *.currency-api.pages.dev) — each request carries only a date or date range, and the source used is recorded on the entry (or "manual" for a typed rate); if every source fails you can pick the nearest cached rate or enter one manually. Every entry posts to a ledger account — multiple accounts (name, currency, opening balance and date, color, emoji) are managed in the Accounts tab, which shows each balance and a CNY-converted total-assets figure (foreign balances use the cached rate table; accounts missing a rate are excluded and flagged). Entries can be expenses, income, or account-to-account transfers; transfers are excluded from spending/income totals, and a reconcile action in an account's detail writes a signed balance-adjustment entry when the computed balance differs from the real one (adjustments appear only in that account's own entry list, never in Transactions or income/expense totals). Accounts ride the same LWW backup/WebDAV payload as entries and tags.
- `Notification quick entry`: from the Ledger menu, optionally enable Android notification access and choose source apps. New notifications from those apps are scanned locally for numbers (including `¥`/`￥` and `元` forms); up to 30 recent suggestions are kept for seven days. Tap an amount to prefill the existing ledger editor and confirm the entry. Notification bodies are not saved, and turning the feature off clears suggestions. The system grants notification access broadly; the app filters to selected sources after receipt. No network request or persistent foreground service is started by this feature.
- `Currency converter`: EUR-base reference rates with a source fallback chain and no API key — Frankfurter (api.frankfurter.dev), then the ECB eurofxref feed (www.ecb.europa.eu), then the community currency-api (cdn.jsdelivr.net, then *.currency-api.pages.dev); the converter labels the source actually used and flags community-source values as potentially differing slightly from the ECB. Enter an amount, pick a source currency (the four ledger currencies up front, plus a "More" list of every published currency with localized names; starring a currency there pins it next to the ledger currencies, both as a source chip and in the result list), and read conversions with each currency's own fraction digits. Rates are fetched only when you tap refresh or the first-use "Get rates" button, are cached locally (latest + recent dated rates) for offline use, and always display their rate date and source; the only data ever sent to these hosts is the requested date.
- Every information row across the app can be copied.

Version 0.1 does not include speed tests, Shizuku/root, process management, third-party ORM frameworks, or advertisements. Shizuku-based privileged telemetry is a future plan, not a current dependency.

## Monitor permissions

Starting the live monitor requires `SYSTEM_ALERT_WINDOW` so the user can
allow the overlay in system settings. The monitor also declares the
foreground-service permissions and requests `POST_NOTIFICATIONS` on Android
13+ so its running state and stop action remain visible. It samples once per
second, can be stopped from the monitor page or notification, and does not
auto-start after boot. Ordinary apps may be blocked from system-wide CPU/GPU
usage files; the UI reports that limitation instead of fabricating values.
Battery temperature and thermal status/headroom use public Android APIs. Exact
CPU/GPU temperatures and vendor-specific GPU utilization may remain unavailable
without trusted system access. The FPS metric is the current display refresh
rate as a best-effort reference; real cross-app rendered FPS requires privileged
SurfaceFlinger access and is reserved for the future Shizuku plan.

Location uses `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` only for a
foreground, user-triggered request. Terrain elevation (DEM) is queried only
after an explicit user action. Network status uses normal
`ACCESS_NETWORK_STATE` and `INTERNET` permissions; the public IP and GeoIP
action sends a single request to public address services upon explicit user tap
and is never polled automatically.

System automatic backup is disabled because the local ledger contains financial
records. Use the ledger's manual JSON export or WebDAV sync for backup. WebDAV
sync requires a server that supplies strong ETags for existing `ledger.json`
files and honors conditional writes; otherwise it stops before overwriting data.
App traffic totals count each UID once. Traffic from shared or unidentified UIDs
cannot be assigned to a single app or category, and unavailable traffic reads
are shown as unavailable. GNSS constellations are listed only after an actual
satellite observation.

## Development environment

- Android Studio Quail 3 (2026.1.3 Patch 1)
- Android SDK Platform 36, Build Tools 36.0.0, Platform Tools 37.0.1
- JDK 17
- Kotlin and Jetpack Compose
- minSdk 26, targetSdk 36

Open the repository in Android Studio and let Gradle synchronize the configured dependencies. The command-line workflow is:

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew installRelease
adb logcat
```

Debug builds run Compose without R8 and without the bundled baseline profile, so the UI is noticeably slower; install the release build for day-to-day use. When the untracked `signing/toolbox-debug.keystore` exists, debug and release builds are both signed with it, so either one updates an installed copy in place and keeps its data. Without that file, debug builds fall back to the default debug key and release builds stay unsigned. Release builds are not debuggable, so `adb shell run-as` cannot read the app's files; back up with the in-app JSON export or WebDAV instead.

If JDK 17 and the Android SDK are not already configured in the shell, set `JAVA_HOME` and `ANDROID_SDK_ROOT` to the local installations. Keep the SDK path in the untracked `local.properties` file.

## Package structure

See [docs/PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md) for the current package layout.

See [docs/REFERENCES.md](docs/REFERENCES.md) for project references and licenses.
