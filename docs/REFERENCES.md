# Development references

These projects are used as problem-solving and UI references. Toolbox does
not copy their source code. Before reusing any implementation, check the
original repository and its license.

## Device and monitor

- [CPU Info](https://github.com/kamgurgul/cpu-info) — device and hardware information; Apache-2.0.
- [DeviceInfo](https://github.com/ahmmedrejowan/DeviceInfo) — device information, monitoring, and hardware-test organization; Apache-2.0.
- [RvSystem Monitor](https://github.com/Rve27/RvSystem-Monitor) — `/proc`/`/sys`, thermal, and low-level monitoring ideas; GPL-3.0.
- [SysInfo](https://github.com/kl3jvi/sysinfo_app) — additional device-information coverage.

## Location and sensors

- [Android LocationManager test providers](https://developer.android.com/reference/android/location/LocationManager) — developer-selected mock-location access, detectable mock locations, and provider removal.
- [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use) — declared special-use sessions that inject test data without reading real GPS.
- [ipwho.is documentation](https://ipwhois.io/documentation) — approximate WGS84 IP coordinates; used only after an explicit request.
- [MapLibre GL JS](https://github.com/maplibre/maplibre-gl-js) — bundled 4.7.1 WebGL vector-tile client for the mock-location map picker; BSD-3-Clause. See `app/src/main/assets/mockmap/LICENSE-maplibre.txt`.
- [OpenFreeMap](https://openfreemap.org/) — style JSON and vector tiles at `tiles.openfreemap.org`, loaded only while the picker is open; no API key.
- [OSMF Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/) — apps must not use `tile.openstreetmap.org` as a default tile source.
- [FakeTraveler](https://github.com/mcastillof/FakeTraveler) — WebView + static HTML/JS-bridge architecture reference only; GPL-3.0, source is not copied.

- [My Location](https://github.com/mirfatif/MyLocation) — GNSS and location presentation ideas.
- [Trail Sense](https://github.com/kylecorry31/Trail-Sense) — local sensor tools and utility UI; MIT.
- [phyphox](https://github.com/phyphox/phyphox-android) — sensor sampling and experiment data flow; GPL-3.0.

## Network and larger applications

- [LibreSpeed Android](https://github.com/librespeed/speedtest-android) — future speed-test reference; LGPL-3.0.
- [LibreSpeed Server](https://github.com/librespeed/speedtest) — future self-hosted server reference; LGPL-3.0.
- [PlainApp](https://github.com/plainhub/plain-app) — larger Compose project and release-process reference; AGPL-3.0.

The current monitor uses Android foreground-service and overlay APIs plus
best-effort system-file reads. It does not add Rust, JNI, a chart library, or
third-party monitoring code.
