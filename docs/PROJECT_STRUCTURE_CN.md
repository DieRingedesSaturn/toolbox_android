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
├── ToolboxApplication.kt
├── monitor/
│   ├── MonitorOverlayService.kt
│   ├── MonitorOverlayTheme.kt
│   ├── MonitorOverlayView.kt
│   ├── MonitorReader.kt
│   ├── MonitorSession.kt
│   └── MonitorSample.kt
├── location/
│   ├── CoordinateFormatter.kt
│   ├── LocationInfo.kt
│   ├── LocationInfoReader.kt
│   ├── PlaceNameReader.kt
│   ├── MockLocationConfig.kt
│   ├── MockLocationController.kt
│   ├── MockLocationSession.kt
│   └── MockLocationService.kt
├── astronomy/
│   ├── AstronomyCalculator.kt
│   ├── AstronomyImageExporter.kt
│   └── AstronomyInfo.kt
├── network/
│   ├── NetworkInfo.kt
│   ├── NetworkInfoReader.kt
│   ├── LanScan.kt
│   └── LanScanner.kt
├── privacy/
│   ├── PrivacyShortcut.kt
│   ├── PrivacyShortcutCatalog.kt
│   └── PrivacyShortcutResolver.kt
├── usage/
│   ├── AppCategory.kt
│   ├── AppCategoryResolver.kt
│   ├── AppUsageInfo.kt
│   └── AppUsageReader.kt
├── ledger/
│   ├── LedgerModels.kt
│   ├── LedgerCategory.kt
│   ├── LedgerTag.kt
│   ├── LedgerAccount.kt
│   ├── LedgerCalculator.kt
│   ├── LedgerQuickPicks.kt
│   ├── SubscriptionRenewals.kt
│   ├── LedgerBackupVault.kt
│   ├── BackupRecoveryStore.kt
│   ├── WebDavHistory.kt
│   ├── LedgerBackup.kt
│   ├── LedgerBackupReminder.kt
│   ├── LedgerBudgets.kt
│   ├── LedgerCsv.kt
│   ├── LedgerSyncContract.kt
│   ├── LedgerStore.kt
│   ├── LedgerDb.kt
│   ├── LedgerMapping.kt
│   ├── LedgerEntryDraft.kt
│   ├── LedgerDbSnapshots.kt
│   ├── LedgerWebDavConfig.kt
│   ├── LedgerWebDavClient.kt
│   ├── LedgerWebDavSync.kt
│   ├── LastWriteWins.kt
│   ├── WholeFileWebDavSync.kt
│   ├── NotificationLedger.kt
│   ├── NotificationLedgerDefaults.kt
│   ├── PaymentNotificationParser.kt
│   ├── NotificationAutoBooker.kt
│   ├── LedgerDuplicateMatcher.kt
│   ├── LedgerNotificationListener.kt
│   └── LedgerWidget.kt
├── totp/
│   ├── TotpModels.kt
│   ├── TotpVaultCrypto.kt
│   ├── TotpStore.kt
│   └── TotpWebDavSync.kt
├── fx/
│   ├── CurrencySearch.kt
│   ├── FxPinnedCurrencies.kt
│   ├── FxRates.kt
│   ├── FxRateReader.kt
│   └── FxRateStore.kt
├── ui/
│   ├── ToolboxApp.kt
│   ├── ToolboxTheme.kt
│   ├── AccentScheme.kt
│   ├── BackupDialogs.kt
│   ├── Components.kt
│   ├── WindowSize.kt
│   ├── AppStrings.kt
│   ├── *Strings.kt              # 按功能分组的 ToolboxStrings 扩展
│   ├── AppPreferences.kt
│   ├── AppBackground.kt
│   ├── BackgroundImageEditor.kt
│   ├── AppSettingsBackup.kt
│   ├── SettingsBackupSection.kt
│   ├── HomeScreen.kt
│   ├── HomePager.kt
│   ├── HomeGridLayout.kt
│   ├── HomeGridGeometry.kt
│   ├── HomeGridRows.kt
│   ├── HomeModuleGrid.kt
│   ├── MonitorScreen.kt
│   ├── MonitorMetrics.kt
│   ├── MonitorCpuCards.kt
│   ├── MonitorOverlaySettings.kt
│   ├── DeviceScreen.kt
│   ├── LocationScreen.kt
│   ├── MockLocationCard.kt
│   ├── MockLocationMapPicker.kt
│   ├── AstronomyScreen.kt
│   ├── NetworkScreen.kt
│   ├── PrivacyScreen.kt
│   ├── AppUsageScreen.kt
│   ├── LedgerScreen.kt
│   ├── LedgerBackupSession.kt
│   ├── LedgerCostSession.kt
│   ├── LedgerEditorSession.kt
│   ├── LedgerManagementSession.kt
│   ├── LedgerAccountsTab.kt
│   ├── LedgerAccountDetail.kt
│   ├── LedgerOverviewTab.kt
│   ├── LedgerOverviewOrder.kt
│   ├── LedgerOverviewOrderDialog.kt
│   ├── LedgerTransactionsTab.kt
│   ├── LedgerCostTab.kt
│   ├── LedgerEntryEditor.kt
│   ├── LedgerCostFields.kt
│   ├── LedgerEntryFields.kt
│   ├── LedgerRenewals.kt
│   ├── LedgerDisposalDialog.kt
│   ├── LedgerTagsDialog.kt
│   ├── LedgerDatePicker.kt
│   ├── LedgerBackupDialogs.kt
│   ├── LedgerBackupReminderCard.kt
│   ├── LedgerBudgetDialog.kt
│   ├── WebDavSettings.kt
│   ├── TotpScreenDialogs.kt
│   ├── NotificationLedgerDialog.kt
│   ├── NotificationLedgerDefaultsDialog.kt
│   ├── NotificationLedgerDuplicateDialog.kt
│   ├── FxScreen.kt
│   ├── TotpScreen.kt
│   ├── SettingsScreen.kt
│   └── AboutScreen.kt
└── device/
    ├── HomeStatusReader.kt
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
| `ui/AppStrings.kt`、`ui/*Strings.kt` | 文案接收者、枚举及按功能分组的双语扩展 | Android 系统值解析 |
| `device/` | Device 数据模型和读取逻辑、`HomeStatusReader` 首页摘要读取 | Compose UI |
| `monitor/` | 性能采样、CPU 拓扑计算、悬浮窗服务与渲染视图 | 无关的页面设置或设备摘要 |
| `usage/` | 应用与流量统计读取、离线分类解析模型 | 直接 Compose UI 渲染 |
| `astronomy/` | 日月升落计算、天文模型算法 | Android 系统服务调用 |
| `ledger/` | 记账模型与自定义多标签、一次性大件摊销/周期订阅计算、按日历日期的续费调度、原生 SQLite 存储（标签表、账户表与父级关联/处置/账户字段）、共享的备份/WebDAV 交换格式、文件备份（JSON/CSV）逻辑、手动 WebDAV 备份同步客户端、来源感知的微信/支付宝支付通知解析与可选自动入账（含撤销；通知快速记账暂时关闭：监听组件默认禁用，通知正文不再明文写入偏好设置）、通知监听与记录存储，以及 RemoteViews 小组件提供器 | 直接 Compose UI 渲染 |
| `fx/` | 汇率模型与阻塞式读取器（Frankfurter → 欧洲央行官网 → 社区镜像的回退链），以及 SharedPreferences 缓存（最新汇率 + 最多 120 个历史日期；仅在用户主动操作时联网） | 直接 Compose UI 渲染 |
| `privacy/` | 打开已导出的 Google 与系统设置的数据列表，以及 PackageManager 解析 | 直接 Compose UI 渲染 |


### 会话与共享逻辑

- `LedgerScreen` 保留分页、筛选、列表加载及导航；页面作用域内的 `LedgerBackupSession`、`LedgerCostSession`、`LedgerEditorSession` 和 `LedgerManagementSession` 分别承接备份、续费/处置、编辑保存、标签/账户/预算操作。它们使用页面传入的协程作用域，离开页面即取消；不创建常驻任务。弹窗 Host 与对应会话放在一起。
- `LedgerEntryDraft.resolve()` 负责纯表单到条目的转换；成本、账户与转账输入组件独立。`LedgerDb` 管建表、迁移及升级前快照，`LedgerMapping` 管行映射，`LedgerStore` 管 CRUD 和事务；数据库版本保持不变。
- `WholeFileWebDavSync` 共用旧路径回退、条件写入、重试和历史快照。记账/TOTP 仅提供编解码与合并函数。多目标同步并行读取全部目标，本地合并一次，再并行条件写入；412 会整组重新读取重试，最多三轮。同目录同凭据的重复配置合并为一个目标。读取或解密失败时不发布主文件；写入阶段可能部分成功，按目标报告结果，不能保证跨服务器原子性。
- `MonitorSession.state` 是页面和悬浮窗服务的共享状态：服务运行时由服务采样，否则仅可见页面采样。`MockLocationSession` 暴露服务的同一个状态流，页面不再镜像启动/停止状态；test provider 的所有权及清理仍归服务负责。
- `ToolboxStrings` 保持原有调用方式，文案实现按功能放在同级 `*Strings.kt` 扩展中。首页通过 `HomeStatusReader` 读取本机摘要，不直接查询账本；不会因此增加联网行为。

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
│   ├── NetworkInfoReader.kt
│   ├── LanScan.kt
│   └── LanScanner.kt
└── ui/
    ├── LocationScreen.kt
    └── NetworkScreen.kt
```

- `CoordinateFormatter` 是纯逻辑，已用 JVM 单元测试验证十进制度、DMS、半球和边界值。
- `PlaceNameReader` 在定位读取或模拟坐标就绪后，通过系统 `Geocoder` 解析地点名称；地址拼装、有界取消和迟到结果丢弃有 JVM 测试；调用方按页面可见生命周期执行，并校验结果对应当前请求。
- `LocationInfoReader` 只申请用户主动触发的前台定位，不申请后台定位。
- `ui/MockLocationMapPicker.kt` 是模拟定位的点按选点对话框。内置资源在 `app/src/main/assets/mockmap/`（自写 `index.html`/`map.js`、MapLibre GL JS 4.7.1 及其 BSD-3-Clause 许可证）。仅在对话框打开时从 OpenFreeMap 拉取瓦片。
- `NetworkInfoReader` 读取本地连接信息，并用短超时执行用户明确触发的公网 IP 查询。
- `LanScan` 是局域网扫描的纯逻辑（子网枚举、摄像头端口/厂商表、可疑标记、SSDP/WS-Discovery 解析），有 JVM 测试；`LanScanner` 执行用户主动触发的 ARP 发现、TCP 端口探测和 mDNS/SSDP/ONVIF WS-Discovery 收听，组播锁只在扫描期间持有。
- 页面仍放在 `ui/`，数据模型和 Android API 读取放在能力包，保持规则统一。

## 何时拆分

优先拆文件，不急于拆层或拆 Gradle 模块。出现以下情况之一时再讨论结构升级：

- 一个文件混合了 UI、Android API 读取和格式化逻辑。
- 单个功能已有多种数据源，且需要可替换实现或离线缓存。
- 多个页面共享同一组非 UI 业务规则。
- 构建时间、独立发布或团队边界确实需要多模块。

在此之前，不添加 Repository/UseCase 接口、DI 容器、第三方 ORM 或仅作转发的包装层。

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

任何版本都继续遵守：默认无后台服务；Live Monitor 与限时模拟定位会话是用户明确要求的例外。仍然无分析、无广告、无账户、无自动网络轮询。
