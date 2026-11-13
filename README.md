# Toolbox

[中文说明](README_CN.md)

Toolbox is a lightweight Android toolbox. It reads device, location, network, and usage data on demand, and it keeps a local ledger, exchange rates, and TOTP tokens on the device. There is no sign-in and no fixed server. Optional pieces (the floating monitor, WebDAV sync, mock location, and others) run only after you turn them on.

Current version **0.1** (`minSdk 26`, `targetSdk 36`). 

## Features

- **Device**: model, system, SoC/CPU, memory, storage, display, battery, and a basic GPU summary. Information rows can be copied.
- **Live monitor**: per-core CPU frequency and usage, plus GPU, memory, battery, and thermal status. An optional translucent floating chart runs as a foreground service and does not start at boot. When the system does not expose real usage, the value is shown as unavailable, or marked with `≈` when it is a frequency-ratio estimate. FPS is the display refresh rate, not a cross-app frame rate. While the floating window is open, the monitor page reuses that sample instead of polling again. The window position is saved when a drag ends.
- **Location**: on-demand fix, place name, decimal degrees and DMS, accuracy, a GNSS sky plot, and a manual terrain-elevation lookup. **Mock location** (Location → Configure mock location) injects developer-selected test-provider coordinates for 15, 30, or 60 minutes with a visible notification. Select Toolbox in Developer options first. **For learning and lawful testing only.**
- **Network**: local connection details; public IP / GeoIP / proxy hints only after a tap; a cancellable LAN scan with heuristic camera-port hints, not a complete discovery.
- **App usage**: needs Usage Access. Foreground time and traffic for today, yesterday, 7 days, and 30 days.
- **Astronomy**: a night-sky timeline for the chosen coordinates, with image export. Without a location fix it uses a default coordinate (Beijing) and says so on screen.
- **Ledger and cost amortization**: on-device SQLite ledger with multiple accounts, tags, budgets, subscription amortization, and transfers. Cost cards show a compact summary; tap to expand details and actions, or long-press to copy. Transactions include searchable multi-select tag filters and a direct year/month picker. Encrypted JSON backup/import, plaintext CSV export, manual HTTPS WebDAV sync (files live in an automatically created `Toolbox/ledger/` subfolder), and a home-screen widget. **Notification quick entry is temporarily disabled** (its menu entry is hidden and the notification listener is off). The code remains so it can be turned back on. Notification bodies are not stored in plaintext, and any body text already saved is cleared on startup.
- **Exchange rates**: reference rates fetched on demand (Frankfurter → European Central Bank → the community currency-api). Results show the date and source and can be used from the local cache.
- **TOTP**: RFC 6238 tokens (camera or gallery QR, or manual entry). Stored with Android Keystore, and the screen blocks screenshots. Encrypted backups use a passphrase you choose, locally or in the `Toolbox/totp/` subfolder of an HTTPS WebDAV folder you configure. If the local vault cannot be decrypted, it is left unchanged and sync will not overwrite it.
- **Privacy**: shortcuts to Google and system settings that some ROMs hide (Google settings, location accuracy, ads, usage diagnostics, phone-number verification, and a few system pages). Only exported activities are opened.
- **Appearance**: system/dark/light themes, dynamic, Everforest, sage or a custom accent, plus an optional background image with a strength slider; the accent can be sampled from that image. Tap its preview to open a full-screen editor: drag to reposition, pinch to zoom, then Save or Cancel. Zoom uses gestures rather than a slider. The image stays on the device and needs no permission.

Monthly net in Home, Ledger month summaries and the widget is income minus everyday spending and recorded monthly subscriptions (including a custom cycle of exactly one calendar month). Yearly, weekly and other cycle payments count only in the month they are paid, as one-time outlays: they stay in actual payments, budgets and account balances, and they do not enter monthly net. Daily holding cost amortizes every live asset and every subscription cycle; the UI shows daily and yearly equivalent only, and does not treat amortized monthly cost as this month's cash spending. Renewals still require confirmation; no payment is invented for an unrecorded month. Actual-payment budgets, account balances, transaction rows and cash-flow charts keep all recorded amounts. Renewal classification uses its parent, including a soft-deleted parent; an orphan with an unknown cycle stays in actual outlays only. Cost cards show up to two tags when collapsed and all live tags when expanded, with different header colours for assets, subscriptions and recurring income.

Home has no title bar. Swipe sideways between Home and Settings, like Android launcher pages. A screen-reader action opens Settings. About is a Settings submenu, alongside Appearance, Home, and Backup. Custom blank-row counts stay intact; if the grid only slightly exceeds the viewport, blank-row heights and gaps compress before scrolling is enabled. Tool cards and the status summary retain their measured size, including at larger font settings. Long-press editing disables pager swipes and shows a floating Done button.

## Backup and recovery

Configure one or more HTTPS WebDAV base folders in Settings, then bind services separately to Ledger and TOTP. Explicit sync creates `Toolbox/` and the tool's `ledger/` or `totp/` child. If the new main file is missing, it reads the nearest legacy file (shared `Toolbox/`, then the configured base). Legacy files are never deleted or rewritten, so existing plaintext ledger backups remain plaintext until you remove them yourself.

Ledger JSON exports and WebDAV payloads use a user passphrase with PBKDF2-HMAC-SHA256 (600,000 iterations) and AES-256-GCM, as TOTP backups do. The ledger passphrase is held only while its screen is open; use the same passphrase on devices sharing a backup. Keep it separately: it cannot be recovered. CSV exports and the local ledger SQLite database remain plaintext; snapshot names and indexes reveal timestamps, not record contents.

Each successful main-file sync attempts a unique snapshot, keeping the newest ten indexed versions. The **Backup history and restore** menu in Ledger and TOTP lists versions for a selected service, including indexed versions in legacy folders. Versions are grouped by creation date, newest first across current and legacy folders, with individual times down to milliseconds. Refresh and restore run only after a tap. Preview the active-record count and confirm a replacement; an encrypted local checkpoint is saved first and is available in that same menu. File Replace imports also checkpoint readable current data. Restoration creates new revisions and tombstones for omitted local records, so the next manual sync propagates those changes.

Settings → WebDAV includes a separate **App settings backup** action. It uses the first service in the configured list, independent of Ledger/TOTP bindings, and writes encrypted `settings.json` and ten indexed versions under `Toolbox/settings/`. Only general preferences (language, theme, ordering, monitor options, pinned currencies and mock accuracy) are included; WebDAV credentials/configuration, ledger data, TOTP tokens and Android permission grants are excluded. Restore previews the preference count and checkpoints current preferences first. Appearance updates immediately; reopen other screens to reload their preferences.

Changing an entered or remembered backup passphrase does **not** rotate existing backups. Older exports, snapshots and recovery checkpoints still need their original passphrase. A sync/backup against an existing remote vault first decrypts it, so a different passphrase fails before writing. New local exports use the newly entered passphrase. WebDAV login credentials are separate. There is no automatic password migration.

Main files and snapshot indexes require strong ETags and conditional writes. All selected services are read concurrently, merged once locally, then published concurrently; a 412 conflict retries the whole group up to three rounds. Read/decrypt failures prevent main-file publication. Duplicate destinations are coalesced, and publication failures are reported per service. This is manual whole-file Last-Write-Wins sync, not an atomic transaction across servers. A server or device changed concurrently may require another sync.

## Screenshots

This repository does not include screenshots yet.

## Build and install

Open the project in [Android Studio](https://developer.android.com/studio), or from the command line:

```bash
./gradlew assembleDebug
./gradlew installDebug      # debug build, no R8, slower UI
./gradlew assembleRelease   # day-to-day build; install it once it is signed
./gradlew testDebugUnitTest
```

Toolchain: JDK 21 (app bytecode still targets Java 17) and Android SDK Platform 36. Put the SDK path in the untracked `local.properties` file.

Debug builds are signed with the untracked `signing/toolbox-debug.keystore` when that file exists, and otherwise with the default debug key. Release builds use the same original keystore when no separate release key is configured. To sign a release with a dedicated key, set either the untracked `keystore.properties` or environment variables:

| `keystore.properties` | Environment variable |
| --- | --- |
| `storeFile` | `TOOLBOX_RELEASE_STORE_FILE` |
| `storePassword` | `TOOLBOX_RELEASE_STORE_PASSWORD` |
| `keyAlias` | `TOOLBOX_RELEASE_KEY_ALIAS` |
| `keyPassword` | `TOOLBOX_RELEASE_KEY_PASSWORD` |

`storeFile` may be absolute or relative to the project root. If no complete release configuration exists, the release build falls back to `signing/toolbox-debug.keystore` when present; without that file it is left unsigned.

## Permissions

All of these are requested when you use the feature, or are install-time declarations that do not turn the feature on by themselves.

| Permission | Use |
| --- | --- |
| Fine / coarse location | Location reads and astronomy. Requested only when you start a fix. No background location. |
| Usage access (`PACKAGE_USAGE_STATS`) | App usage and traffic |
| Query all packages (`QUERY_ALL_PACKAGES`) | Resolve app names in the usage list. Install-time; Play treats it as a restricted permission. |
| Overlay, foreground service, notifications | Live monitor window, and the mock-location status notification (Android 13+ asks for notification permission) |
| Notification listener | Declared for notification quick entry, which is temporarily disabled. The listener component is off. |
| Camera | TOTP QR scan, requested when you tap scan |
| Network / network state / multicast | Public IP, exchange rates, elevation, map tiles, WebDAV, and the LAN scan (multicast only while a scan is running) |
| Mock location (`ACCESS_MOCK_LOCATION`) | So Toolbox can appear in the Developer options mock-app list. Declaring it does not enable mocking. |

Automatic system backup is off, because the ledger and tokens are sensitive. Use in-app JSON export or WebDAV sync.

Place names come from the platform geocoder, which may use the network. Public IP, elevation, exchange rates, map tiles, and WebDAV run only after you ask, and only against that action's own hosts.

## Docs

- Development rules: [AGENTS.md](AGENTS.md)
- Layout: [docs/PROJECT_STRUCTURE.md](docs/PROJECT_STRUCTURE.md)
- References and third-party licenses: [docs/REFERENCES.md](docs/REFERENCES.md)

## License

This repository has no `LICENSE` file. Choose one before a public release. Bundled MapLibre GL JS is BSD-3-Clause (`app/src/main/assets/mockmap/LICENSE-maplibre.txt`). ZXing core is Apache-2.0.
