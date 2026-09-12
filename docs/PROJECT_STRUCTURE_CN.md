# 项目结构

[English](PROJECT_STRUCTURE.md)

Toolbox 采用“单模块、按能力分包”的结构。v0.1 的规模不需要多模块、Clean Architecture 或固定的 `data/domain/presentation` 三层。

## 仓库目录

```text
toolbox_android/
├── AGENTS.md                    # 全仓库英文开发规范
├── README.md                    # 英文项目入口
├── README_CN.md                 # 中文项目入口
├── docs/
│   ├── PROJECT_STRUCTURE.md     # 英文结构说明
│   ├── PROJECT_STRUCTURE_CN.md  # 本文件
│   └── REFERENCES.md            # 外部项目参考与许可证记录
├── app/
│   ├── build.gradle.kts         # Android app 配置与依赖
│   ├── proguard-rules.pro       # Release 收缩规则
│   └── src/
│       ├── main/                # 生产代码与资源
│       ├── test/                # 不依赖设备的 JVM 单元测试（按需创建）
│       └── androidTest/         # 需要 Android/真机的测试（按需创建）
├── gradle/wrapper/              # 固定 Gradle 版本
├── build.gradle.kts             # 根插件版本
├── settings.gradle.kts          # 仓库和模块声明
├── gradle.properties            # 可提交的 Gradle 公共配置
└── local.properties             # 本机 SDK 路径，不提交
```

生成目录 `.gradle/`、`.kotlin/`、`build/` 和 `app/build/` 不属于源代码。

## Kotlin 包结构

当前结构：

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
├── astronomy/
│   ├── AstronomyCalculator.kt
│   └── AstronomyInfo.kt
├── network/
│   ├── NetworkInfo.kt
│   └── NetworkInfoReader.kt
├── usage/
│   ├── AppCategory.kt
│   ├── AppCategoryResolver.kt
│   ├── AppUsageInfo.kt
│   └── AppUsageReader.kt
├── ui/
│   ├── ToolboxApp.kt
│   ├── ToolboxTheme.kt
│   ├── Components.kt
│   ├── AppStrings.kt
│   ├── AppPreferences.kt
│   ├── HomeScreen.kt
│   ├── MonitorScreen.kt
│   ├── DeviceScreen.kt
│   ├── LocationScreen.kt
│   ├── AstronomyScreen.kt
│   ├── NetworkScreen.kt
│   ├── AppUsageScreen.kt
│   ├── SettingsScreen.kt
│   └── AboutScreen.kt
└── device/
    ├── DeviceInfo.kt
    └── DeviceInfoReader.kt
```

职责边界：

| 位置 | 放什么 | 不放什么 |
| --- | --- | --- |
| `MainActivity.kt` | Activity 生命周期、窗口设置、启动 Compose | 页面业务、数据读取 |
| `ui/ToolboxApp.kt` | 顶层状态、简单页面切换、功能协调 | 底层系统字段解析 |
| `ui/*Screen.kt` | 页面布局、状态呈现、用户事件 | 文件读取、HTTP 实现、长时间循环 |
| `ui/Components.kt` | 多页面复用的 `InfoCard`、`InfoRow` | 单一页面私有组件 |
| `ui/ToolboxTheme.kt` | Material 主题、颜色、排版 | 页面状态 |
| `ui/AppStrings.kt` | 集中的用户可见文案 | Android 系统值解析 |
| `device/` | Device 数据模型和读取逻辑 | Compose UI |
| `monitor/` | 性能采样、CPU 拓扑计算、悬浮窗服务与渲染视图 | 无关的页面设置或设备摘要 |
| `usage/` | 应用与流量统计读取、离线分类解析模型 | 直接 Compose UI 渲染 |
| `astronomy/` | 日月升落计算、天文模型算法 | Android 系统服务调用 |

## v0.1 的增量结构

位置和网络能力包已经开始实现：

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

- `CoordinateFormatter` 是纯逻辑，已用 JVM 单元测试验证十进制度、DMS、半球和边界值。
- `LocationInfoReader` 只申请用户主动触发的前台定位，不申请后台定位。
- `NetworkInfoReader` 读取本地连接信息，并用短超时执行用户明确触发的公网 IP 查询。
- 页面仍放在 `ui/`，数据模型和 Android API 读取放在能力包，保持规则统一。

## 何时拆分

优先拆文件，不急于拆层或拆 Gradle 模块。出现以下情况之一时再讨论结构升级：

- 一个文件混合了 UI、Android API 读取和格式化逻辑。
- 单个功能已有多种数据源，且需要可替换实现或离线缓存。
- 多个页面共享同一组非 UI 业务规则。
- 构建时间、独立发布或团队边界确实需要多模块。

在此之前，不添加 Repository/UseCase 接口、DI 容器、数据库或仅作转发的包装层。

## 文件命名

- 页面：`<Feature>Screen.kt`
- 数据快照：`<Feature>Info.kt`
- Android 读取入口：`<Feature>InfoReader.kt`
- 纯格式化逻辑：`<Value>Formatter.kt`
- 通用 Compose 组件：按组件命名；少量组件继续放在 `Components.kt`
- JVM 测试：`<ClassName>Test.kt`
- Android 测试：`<Behavior>Test.kt`

测试包应镜像被测代码。例如：

```text
app/src/test/java/com/example/toolbox/location/CoordinateFormatterTest.kt
```

## 版本演进边界

- v0.1：Home、Device、Location、Network，以及用户明确启动的 Live Monitor。
- v0.2：Compass、Sensors、Screen/Touch test、Flashlight；每个已实现能力使用自己的包。
- v0.3：网络工具；测速需要单独评估服务端、流量、算法和许可证。
- v0.4：高级设备字段；与普通摘要分开，不把 `/proc`、`/sys` 全量内容直接塞进 Device 页。

任何版本都继续遵守：默认无后台服务；Live Monitor 是用户明确要求的例外。仍然无分析、无广告、无账户、无自动网络轮询。
