# Toolbox

[中文说明](README_CN.md)

Toolbox is a lightweight Android toolbox. It reads device, location, network, and usage data on demand, and it keeps a local ledger, exchange rates, and TOTP tokens on the device. There is no sign-in and no fixed server. Optional pieces (the floating monitor, WebDAV sync, mock location, and others) run only after you turn them on.

Current version **0.1** (`minSdk 26`, `targetSdk 36`). This version has no speed test, Shizuku/root integration, process manager, or ads.

## Features

- **Device**: model, system, SoC/CPU, memory, storage, display, battery, and a basic GPU summary. Information rows can be copied.
- **Live monitor**: per-core CPU frequency and usage, plus GPU, memory, battery, and thermal status. An optional translucent floating chart runs as a foreground service and does not start at boot. When the system does not expose real usage, the value is shown as unavailable, or marked with `≈` when it is a frequency-ratio estimate. FPS is the display refresh rate, not a cross-app frame rate. While the floating window is open, the monitor page reuses that sample instead of polling again. The window position is saved when a drag ends.
- **Location**: on-demand fix, place name, decimal degrees and DMS, accuracy, a GNSS sky plot, and a manual terrain-elevation lookup. **Mock location** (Location → Configure mock location) injects developer-selected test-provider coordinates for 15, 30, or 60 minutes with a visible notification. Select Toolbox in Developer options first. **For learning and lawful testing only.**
- **Network**: local connection details; public IP / GeoIP / proxy hints only after a tap; a cancellable LAN scan with heuristic camera-port hints, not a complete discovery.
- **App usage**: needs Usage Access. Foreground time and traffic for today, yesterday, 7 days, and 30 days.
- **Astronomy**: a night-sky timeline for the chosen coordinates, with image export. Without a location fix it uses a default coordinate (Beijing) and says so on screen.
- **Ledger and cost amortization**: on-device SQLite ledger with multiple accounts, tags, budgets, subscription amortization, and transfers. JSON and CSV export/import, manual HTTPS WebDAV sync, and a home-screen widget. **Notification quick entry is temporarily disabled** (its menu entry is hidden and the notification listener is off). The code remains so it can be turned back on. Notification bodies are not stored in plaintext, and any body text already saved is cleared on startup.
- **Exchange rates**: reference rates fetched on demand (Frankfurter → European Central Bank → the community currency-api). Results show the date and source and can be used from the local cache.
- **TOTP**: RFC 6238 tokens (camera or gallery QR, or manual entry). Stored with Android Keystore, and the screen blocks screenshots. Encrypted backups use a passphrase you choose, locally or on an HTTPS WebDAV folder you configure. If the local vault cannot be decrypted, it is left unchanged and sync will not overwrite it.
- **Privacy**: shortcuts to Google and system settings that some ROMs hide (Google settings, location accuracy, ads, usage diagnostics, phone-number verification, and a few system pages). Only exported activities are opened.

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
