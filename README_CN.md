# Toolbox

[English](README.md)

Toolbox 是一个按需读取信息的轻量 Android 工具箱。当前是 v0.1：无账户、无数据库；实时监控是用户主动启动后才运行的例外功能，并使用用户可见的前台服务。

开发前请阅读英文开发规范 [AGENTS.md](AGENTS.md)。完整目录约定和后续功能的落位规则见 [docs/PROJECT_STRUCTURE_CN.md](docs/PROJECT_STRUCTURE_CN.md)。

## 当前范围

- `Device`：手机身份、Android/API、内核、SoC/CPU、ABI、核心数、内存、存储、显示、电池，以及基础 GPU 能力摘要。
- `Live monitor`：可选的紧凑半透明悬浮窗折线图，显示 CPU、GPU、内存、电量和 FPS 参考值，并可自定义显示项目。监控页按核心显示 CPU 当前频率，同时显示 GPU/显示详情和热状态详情；悬浮窗可在逐核心频率和按峰值频率加权的 CPU 占比之间切换。CPU/GPU 优先读取真实占用率；Android 只提供频率时用 `≈` 标记频率占比估算，受系统限制的源明确显示不可用。
- 监控页还会在公开 API 提供时显示电池温度、Android 系统热状态、热余量、当前显示刷新率和支持的显示模式。悬浮窗与整个软件均支持 Everforest 自然森绿深浅主题配色（包含深色、浅色、动态色、蓝色、绿色等），自适应文字与图表高对比度；悬浮窗支持锁定位置与触控穿透，移动后会保存上次位置。
- `Location`：按需获取当前位置，显示十进制度/DMS 坐标、精度、提供方、GPS 提供方高度、时间、手机支持的卫星定位系统（GPS、北斗、GLONASS、Galileo、QZSS 等）及可用/已启用定位源，并支持按需查询地表地形海拔（DEM 模型）。只有用户开始定位后才请求前台位置权限。
- `Network`：按需显示连接方式、接口、本地地址、DNS、网关和按流量计费状态；公网 IP、IP 大致地理位置与运营商查询是单独的手动操作。
- Device 页与各模块所有信息行点击即可复制。

v0.1 暂不实现测速、Shizuku/root、进程管理、数据库、账户系统和云同步。基于 Shizuku 的高级权限数据读取属于后续计划，不是当前依赖。

## 实时监控权限

启动实时监控需要 `SYSTEM_ALERT_WINDOW`，用户需在系统设置中允许悬浮窗；同时声明前台服务权限，并在 Android 13+ 请求 `POST_NOTIFICATIONS`，以显示服务状态和停止操作。监控每秒采样一次，可从监控页或通知停止，不会开机自启。普通应用可能无法读取系统级 CPU/GPU 占用文件，界面会如实说明限制，不伪造数据。电池温度、系统热状态和热余量使用 Android 公开 API；精确 CPU/GPU 温度及厂商专用 GPU 占用率在没有受信任系统权限时可能不可用。FPS 项目当前以显示刷新率作为尽力而为的参考值；跨应用真实渲染 FPS 需要 SurfaceFlinger 特权访问，留给后续 Shizuku 计划。

位置模块仅在用户主动开始定位时申请 `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` 前台权限，不申请后台定位；地表地形海拔（DEM）仅在用户手动点击后单次查询。网络状态使用系统自动授予的 `ACCESS_NETWORK_STATE` 和 `INTERNET` 权限；公网 IP 与地理位置操作只在手动触发时向公开地址服务发起一次请求，不会自动轮询。

## 开发环境

- Android Studio Quail 3 (2026.1.3 Patch 1)
- Android SDK Platform 36、Build Tools 36.0.0、Platform Tools 37.0.1
- JDK 17
- Kotlin + Jetpack Compose
- minSdk 26，targetSdk 36

使用 Android Studio 打开仓库并让 Gradle 同步配置的依赖。命令行流程为：

```bash
./gradlew assembleDebug
./gradlew installDebug
adb logcat
```

如果当前 shell 尚未配置 JDK 17 和 Android SDK，请将 `JAVA_HOME` 与 `ANDROID_SDK_ROOT` 指向本机安装位置。SDK 路径保存在不提交的 `local.properties` 中。

## 包结构

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
├── device/
│   ├── DeviceInfo.kt
│   └── DeviceInfoReader.kt
└── ui/
    ├── AboutScreen.kt
    ├── AppPreferences.kt
    ├── AppStrings.kt
    ├── Components.kt
    ├── DeviceScreen.kt
    ├── HomeScreen.kt
    ├── LocationScreen.kt
    ├── MonitorScreen.kt
    ├── NetworkScreen.kt
    ├── PlaceholderScreen.kt
    ├── SettingsScreen.kt
    ├── ToolboxApp.kt
    └── ToolboxTheme.kt
```

v0.1 继续保持单一 `app` 模块：页面放在 `ui/`，设备、监控、定位、网络的数据模型与 Android API 读取分别放在能力包中。只有功能开始实现时才创建新目录，不预建空的架构层。

参考项目清单见 [docs/REFERENCES.md](docs/REFERENCES.md)。
