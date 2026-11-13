# AGENTS.md

This file applies to the entire repository. Its purpose is to help developers and automated agents maintain Toolbox through small, runnable, and verifiable changes.

## 1. Project scope

Toolbox is a lightweight Android toolbox, not an always-on telemetry daemon. The home screen shows a quick status summary and a user-reorderable grid of compact title cards for the tool modules. Current capabilities include:

- Device: summaries of the device, CPU/GPU, memory, storage, display, battery, and thermals.
- Location & Astronomy: on-demand coordinate retrieval, formatting, a platform-geocoder place name after a successful read, and solar/lunar ephemeris calculations. Location → Configure mock location injects developer-selected Android test-provider coordinates for an explicit 15/30/60-minute visible `specialUse` session (manual WGS84, a tap-to-pick map using bundled MapLibre GL JS and OpenFreeMap tiles while the picker is open, or an explicit ipwho.is → ipapi.co exit-IP lookup). The mock card shows a place name from the platform Geocoder once coordinates are set. It is for learning and lawful testing only, keeps Android's detectable mock flag, never reads real GPS, and does not start at boot.
- Network: local connection information and public IP lookup after an explicit user refresh, plus a user-triggered LAN device scan (ARP sweep, short TCP port probe, mDNS/SSDP/ONVIF WS-Discovery announcements) that flags camera-typical services/ports/OUI vendors as honest heuristic hints only.
- App & Traffic Usage: on-demand foreground app screen time and network traffic (Wi-Fi vs. Mobile) statistics, including tethering/P2P traffic recorded under the system tethering UID in totals and as a separate unattributable shared-network value, multi-category offline classification, and time range filtering (Today, Yesterday, 7 Days, 30 Days).
- Live Performance Monitor & Overlay: on-demand foreground sampling, weighted CPU core topology visualizer matrix, and customizable floating window overlay.
- Ledger & Cost Amortization: local bookkeeping (CNY/JPY/USD/EUR entries; foreign entries lock the CNY-per-unit rate — fetched per entry date via the Frankfurter → ECB → currency-api fallback chain, or typed manually — at save so all totals stay in CNY, and the source used is recorded per entry) with custom multi-tags, asset disposal (sell/scrap records a linked income entry), calendar-accurate subscription and recurring-income renewals recorded as real child transactions after user confirmation, and optional cost amortization for one-time long-term purchases (daily/monthly depreciated cost) and periodic subscriptions, persisted via Android SDK `SQLiteOpenHelper` (`uuid`, timestamps, soft-delete tombstones merged Last-Write-Wins via the shared `LedgerSyncPayload` backup/WebDAV file format; schema 3 adds tags, ledger accounts with balances/transfers/reconciliation adjustments, and parent/link/disposal/FX-source fields; schema 4 retires unused untouched category-derived tags — fresh installs start tagless and seeds only stay for referenced categories; schema 5 adds an optional cost end date (`cost_ends_at`) that caps renewals and sets the amortization horizon; every schema upgrade first copies the old database file into `noBackupFilesDir/ledger-db-snapshots/`, keeping the newest 3). Includes local file backup through the system picker (passphrase-encrypted JSON full backup incl. deletions, plaintext CSV spreadsheet export; Merge or Replace import; no storage permission), manual HTTPS WebDAV backup/sync to a user-created folder (explicit tap only, newest-10 unique dated snapshots selectable in the in-app history dialog; encrypted local checkpoints before restores; restores generate new revisions and local-only tombstones), and a RemoteViews home-screen widget (no extra permissions, no background service; refreshes on ledger writes and via the ~3-hour system widget scheduler). The app must not depend on any fixed server.
- Notification Quick Entry: temporarily disabled (`NotificationBookkeeping.ENABLED` is false). The listener component ships disabled, the ledger menu entry is hidden, and notification body text is not stored in SharedPreferences; already-stored bodies are cleared on startup. The parser, listener, and dialogs stay in the tree so the feature can be turned back on. When enabled it is optional source-filtered local parsing of payment notifications into ledger suggestions, with per-source defaults, confident auto-book plus undo, duplicate hints, and 7-day local records; verification-code notices keep no title or text. Makes no network request.
- General settings backup: explicit encrypted backup to `Toolbox/settings/` on the first configured WebDAV service, with ten versions, date-grouped history and a local checkpoint before restore. Only allowlisted `AppPreferences` fields travel; never include WebDAV credentials/configuration, ledger data, TOTP secrets or Android permission grants. Restoring appearance updates Compose state immediately; other screens reload preferences when reopened. Changing a passphrase input does not re-encrypt existing backups.
- Appearance & background image: system/dark/light theme modes, a dynamic, Everforest, sage or custom accent, plus an optional user-picked background image shown behind the app with a 0–70% strength slider. `ui/AppBackground.kt` copies the picked image into app-internal storage, decodes it at a bounded size for display, and samples a dominant seed colour; the `Background` accent derives the palette from that seed. Picking uses the system photo picker, so no storage permission and no network access are needed. Tapping the preview opens a full-screen drag/pinch editor with a local draft; only Save publishes scale and offsets in one preferences edit, while Cancel keeps the existing transform. There is no zoom slider. The image file is never part of WebDAV settings backups; only allowlisted appearance preferences travel.
- Currency converter: EUR-base reference rates from the Frankfurter → ECB → currency-api fallback chain, fetched only on explicit actions, cached locally, no API key.
- Privacy: a home-screen tool that opens exported Google and system settings activities some ROMs hide (Google settings, location accuracy, ads, usage diagnostics, phone-number verification, account sync, and a short list of system pages). It does not start non-exported components.
- TOTP Authenticator: RFC 6238 dynamic token generator (`SHA-1`/`SHA-256`/`SHA-512`, 6/8 digits, 15/30/60s period) supporting live Camera2 QR scanning, gallery QR screenshot decoding (`com.google.zxing:core` Apache-2.0), Base32 secrets, and `otpauth://totp/...` URIs (auto-extracting service issuer and account name), persisted locally via an Android Keystore AES-256-GCM key in `noBackupFilesDir`, protected with `FLAG_SECURE` while visible, and backed up via user-passphrase E2EE (`PBKDF2-HMAC-SHA256` 600,000 iterations + `AES-256-GCM`) to local JSON files or one or more user-selected HTTPS WebDAV services configured in Settings.

Unless explicitly requested, do not add speed tests, Shizuku/root integration, process management, third-party ORM frameworks, advertisements, analytics SDKs, Firebase, or persistent background services.

## 2. Core principles

1. Keep a single `app` module and a flat package structure. Do not introduce Clean Architecture, Repository, UseCase, or dependency-injection frameworks in advance.
2. Use minimal permissions, no persistent background work, and on-demand reads. The app must not request permissions on first launch.
3. Read static information once. Refresh dynamic information only while its screen is visible, and stop when the user leaves the screen.
4. Make every important user-facing value copyable. Show unavailable data explicitly as `Unknown` or `Not available`; never invent a default value.
5. Prefer the Android SDK and standard Kotlin/Compose capabilities. Before adding a third-party dependency, explain its purpose, size, permissions, network behavior, and license.
6. Each change should deliver one verifiable increment while keeping the Debug build installable.

## 3. File organization

See `docs/PROJECT_STRUCTURE.md` for the canonical structure. The current convention is:

```text
app/src/main/java/com/example/toolbox/
├── MainActivity.kt       # Android entry point; window setup and Compose startup only
├── ui/                   # App shell, screens, theme, strings, and shared Compose components
├── device/               # Device models and Android information readers
├── location/             # Location models, coordinate formatters, place-name reader, and readers
├── astronomy/            # Solar/lunar ephemeris calculators and models
├── network/              # Network models and information readers
├── usage/                # App usage/traffic readers, category resolver, and models
├── ledger/               # Bookkeeping models/categories, cost amortization math, SQLite store, backup/WebDAV file format, and widget
├── totp/                 # RFC 6238 TOTP generator, otpauth URI parser, Keystore local store, PBKDF2/AES-GCM vault, and WebDAV sync
├── fx/                   # Frankfurter/ECB exchange-rate models, reader, and local cache
└── monitor/              # Real-time sampling, overlay service, and views
```

Placement rules:

- `ui/*Screen.kt` owns presentation and user events, not complex Android API reads.
- Each capability package may contain its simple models and Reader/Provider. Do not split into `data/domain/presentation` at the current scale.
- Shared Compose components stay in `ui/Components.kt`; split only when the file becomes difficult to navigate or a distinct component family emerges.
- Test directories mirror production package paths.
- Do not create empty directories, placeholder architecture layers, or pass-through-only interfaces.
- If a file mixes responsibilities or a package grows continuously, split files within that feature before adding more hierarchy.

Do not commit `.gradle/`, `.kotlin/`, `build/`, `local.properties`, IDE state, APKs, signing files, or secrets.

## 4. Kotlin and Compose conventions

- Use the official Kotlin style, four-space indentation, and trailing commas.
- Name Composables with nouns, event callbacks with `on...`, and Android data readers with `...Reader`.
- Hoist state to the lowest common owner. Use `rememberSaveable` for recoverable UI-only state; do not save `Context`, system services, or large objects.
- Run blocking I/O on `Dispatchers.IO` and update UI state on the main thread. Never read files, perform network calls, or query system services directly during composition.
- Screen-level refresh work must be cancellable and lifecycle-aware. Do not create unbounded coroutines, permanent loops, or polling that continues after a screen is hidden.
- Reuse `InfoCard` and `InfoRow` so presentation and copy behavior remain consistent.
- Keep user-facing strings centralized; do not mix translated text into system data readers.
- Handle API-level differences with explicit `Build.VERSION.SDK_INT` branches. Scope deprecation suppressions narrowly instead of suppressing an entire file.

## 5. Android, permissions, and data boundaries

- Treat the Gradle files as the source of truth for `minSdk`, `targetSdk`, and dependency versions. When upgrading them, verify both the Debug build and physical-device behavior.
- Device must not require sensitive permissions.
- Location may request `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` only after the user opens the feature and starts location retrieval. Never request background location.
- Mock location starts only after an explicit tap, and only after the user has selected Toolbox as the mock-location app in Developer options. Debug and release builds declare `ACCESS_MOCK_LOCATION` so Toolbox appears in that picker; the permission is not granted at install and does not enable mocking by itself. Sessions last 15, 30, or 60 minutes, show a stoppable notification (on-demand `POST_NOTIFICATIONS` on Android 13+), and write test locations without requesting background location. Exit-IP coordinates are fetched only from that card's own button. The map picker loads OpenFreeMap styles and vector tiles from `tiles.openfreemap.org` only while that dialog is open; it has no search box and no API key. After coordinates are set (map, IP fill, or typed), the card resolves a place name through the platform Geocoder.
- App Usage statistics require `PACKAGE_USAGE_STATS`, requested on-demand only when the user navigates to the App Usage feature.
- Floating overlay requires `SYSTEM_ALERT_WINDOW`, requested on-demand only when the user enables the floating monitor.
- TOTP QR scanning requests `CAMERA` on-demand only when the user taps Scan QR Code inside the TOTP screen; picking a QR screenshot from the gallery requires no permission.
- Network access must follow a clear user action, use timeouts, expose failure states, and avoid automatic high-frequency polling. Public-IP/GeoIP lookup and terrain-elevation lookup (`api.open-meteo.com`, falling back to `api.open-elevation.com`) run only after their own explicit taps. Opening the mock-location map picker loads OpenFreeMap styles/tiles from `tiles.openfreemap.org` until the dialog is dismissed. Place-name lookup uses the platform Geocoder after a successful location read or after mock coordinates are set; it is not a map search box, and the platform Geocoder may use the network. Exchange-rate requests use Frankfurter (`api.frankfurter.dev`), the ECB eurofxref feed (`www.ecb.europa.eu`), and the community currency-api (`cdn.jsdelivr.net`, `*.currency-api.pages.dev`); they carry only the requested date or date range and run only on an explicit refresh/save/renewal action.
- WebDAV sync/backup (for Ledger, TOTP and general settings) runs only after an explicit user tap (never automatically or in the background), uses HTTPS only, and writes backup payloads solely to per-tool `Toolbox/ledger/`, `Toolbox/totp/` and `Toolbox/settings/` subfolders under the user's configured HTTPS folder(s) in Settings (where users can configure one or more WebDAV services and bind them per tool). The shared and tool subfolders are created with MKCOL when missing; when the new main file is absent, sync reads the nearest legacy file from the shared Toolbox folder or configured base, leaving legacy files untouched. Ledger, TOTP and settings payloads use user-passphrase PBKDF2/AES-GCM envelopes; file names and indexes are plaintext metadata. Ledger/TOTP sync reads all selected services before publishing merged data; settings backup uses only the first configured service. Main files and history indexes use strong ETag conditional writes. Never use replaceAll to apply TOTP sync results: merge atomically against current local data. Stored passwords are encrypted with Android Keystore, and credentials or URLs must never be logged.
- Exchange rates are fetched only after an explicit action (saving a foreign-currency entry without a cached rate for its date, confirming renewals, or the converter's refresh/get-rates button). Never fetch on screen open or in the background; send only the date path; cache every fetched rate locally.
- Keep GNSS altitude distinct from any future terrain elevation value. Never present one as the other.
- Android vendors may restrict `/proc`, `/sys`, and hardware fields via SELinux. Fall back gracefully (e.g. CPU frequency scaling estimation for restricted `/proc/stat`, and standard fallback when Android 16 `getGpuHeadroom()` HAL is unsupported).
- Do not log or upload precise locations, IP addresses, device identifiers, or other sensitive data. Avoid complete sensitive values in logs.

## 6. Build and verification

Common commands:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew installDebug
adb logcat
```

Verify in proportion to the change:

- Documentation or comments: check links, paths, commands, and the final diff.
- Kotlin/Compose: run at least `./gradlew assembleDebug`.
- Pure logic: add or update unit tests and run `testDebugUnitTest`.
- Manifest, permissions, system APIs, display, sensors, or network: in addition to building, verify on an API 26+ physical device. If no device is available, report that physical-device verification was not performed.
- Release configuration: verify R8/resource shrinking, APK size, installation, and startup. A Debug build alone is insufficient.

A successful build confirms the compilation path only. It does not prove permissions, hardware fields, location accuracy, network results, or vendor compatibility.

Any schema change must keep `LedgerStoreMigrationTest` (Robolectric, JVM-only) passing and should add a case for the new version; migration steps must read only the columns that exist at their own version, never through the current row mapper. Never run instrumented tests (`connectedAndroidTest`) against a device that holds real ledger data: they uninstall the app afterwards, which deletes the ledger.

## 7. Change discipline

Before editing:

1. Read `README.md`, this file, and `docs/PROJECT_STRUCTURE.md`.
2. Inspect the real directory tree, Gradle configuration, Manifest, and Git state. Do not infer current behavior from planning documents.
3. Confirm that the feature belongs to the current release scope. If it does not, propose the smallest useful slice.

Before finishing:

1. Inspect the diff for generated files, machine-specific paths, secrets, and unrelated formatting.
2. Run verification appropriate to the risk and report any unverified boundary accurately.
3. When adding a permission, dependency, network service, or background behavior, update the README and explain the user impact.
4. Do not push, rewrite user history, or delete user data. Commit only when requested, and keep each commit focused on one topic.

## 8. Definition of done

A feature is complete only when:

- It lives in the agreed location, has clear naming and responsibilities, and avoids unnecessary abstraction.
- Success, loading, unavailable, and failure states behave reasonably.
- Permission and network behavior follow the on-demand rules.
- Important values are copyable and clearly formatted.
- Appropriate builds/tests have run; physical-device behavior is verified or explicitly reported as unverified.
- Documentation matches the implementation.
