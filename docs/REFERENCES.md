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
