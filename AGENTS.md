# AGENTS.md

This file applies to the entire repository. Its purpose is to help developers and automated agents maintain Toolbox through small, runnable, and verifiable changes.

## 1. Project scope

Toolbox is a lightweight Android toolbox, not an always-on telemetry daemon. Current capabilities include:

- Device: summaries of the device, CPU/GPU, memory, storage, display, battery, and thermals.
- Location & Astronomy: on-demand coordinate retrieval, formatting, and solar/lunar ephemeris calculations.
- Network: local connection information and public IP lookup after an explicit user refresh.
- App & Traffic Usage: on-demand foreground app screen time and network traffic (Wi-Fi vs. Mobile) statistics, multi-category offline classification, and time range filtering (Today, Yesterday, 7 Days, 30 Days).
- Live Performance Monitor & Overlay: on-demand foreground sampling, weighted CPU core topology visualizer matrix, and customizable floating window overlay.

Unless explicitly requested, do not add speed tests, Shizuku/root integration, process management, databases, accounts, cloud synchronization, advertisements, analytics SDKs, Firebase, or persistent background services.

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
├── location/             # Location models, coordinate formatters, and readers
├── astronomy/            # Solar/lunar ephemeris calculators and models
├── network/              # Network models and information readers
├── usage/                # App usage/traffic readers, category resolver, and models
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
- App Usage statistics require `PACKAGE_USAGE_STATS`, requested on-demand only when the user navigates to the App Usage feature.
- Floating overlay requires `SYSTEM_ALERT_WINDOW`, requested on-demand only when the user enables the floating monitor.
- Network access must follow a clear user action, use timeouts, expose failure states, and avoid automatic high-frequency polling.
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
