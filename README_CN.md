# Toolbox

[English](README.md)

Toolbox 是一款按需读取的轻量 Android 工具箱：设备信息、实时监控、定位、网络、应用用量、天文星历，以及本机记账、汇率换算和 TOTP 令牌。账本和令牌保存在本机，无需登录，也不依赖固定服务器。悬浮窗监控、WebDAV 同步、模拟位置等可选功能都要你主动开启后才会运行。

当前版本 **0.1**（`minSdk 26`，`targetSdk 36`）。暂不提供测速、Shizuku/root、进程管理或广告。

## 功能

- **设备信息**：型号、系统、SoC/CPU、内存、存储、显示、电池和基础 GPU 摘要；各信息行可复制。
- **实时监控**：按核心查看 CPU 频率与占用，以及 GPU、内存、电池和热状态。可开启半透明悬浮窗折线图（前台服务，不会开机自启）。系统不提供真实占用时显示为不可用，或以 `≈` 标记频率占比估算。FPS 目前是显示刷新率参考，不是跨应用真实帧率。悬浮窗开着时，监控页复用它的采样，不再各读一份。悬浮窗位置在拖动结束时保存。
- **位置**：按需定位、地名、十进制度/DMS、精度、GNSS 星空图，以及手动查询地形海拔。另有「模拟位置」供开发者测试（需在开发者选项中指定本应用；会话限时 15/30/60 分钟，带常驻通知）。**仅供学习与合法测试。**
- **网络**：本机连接信息；点一下再查询公网 IP / GeoIP / 代理风险提示；可取消的局域网设备扫描（含摄像头端口启发式提示，并非完整发现）。
- **应用用量**：需「使用情况访问」权限。按今天、昨天、7 天、30 天查看前台时长和流量。
- **天文星历**：根据坐标生成夜间天体时间线，可导出图片。没有定位结果时使用默认坐标（北京），界面会标明这是默认参考坐标。
- **记账与成本摊销**：本机 SQLite 账本（多账户、标签、预算、订阅摊销、转账）。支持 JSON/CSV 导出导入、手动 HTTPS WebDAV 同步，以及主屏幕小组件。**通知快速记账暂时关闭**（菜单入口已隐藏，通知监听组件默认禁用）。相关代码保留，之后可以重新打开。通知正文不再以明文写入本机偏好设置，启动时会清掉已经存下的正文。
- **汇率换算**：按需拉取参考汇率（Frankfurter → 欧洲央行 → 社区 currency-api），结果带日期和来源，可离线使用缓存。
- **TOTP**：RFC 6238 令牌（相机或相册扫码、手动输入）。本机 Android Keystore 加密，页面开启防截屏；可用你设置的密码导出加密备份，或同步到你配置的 HTTPS WebDAV。本机库无法解密时保持原样，同步不会覆盖已有令牌。
- **隐私**：打开部分系统会藏起来的 Google 与系统设置（Google 设置、定位精确度、广告、使用情况与诊断、电话号码验证，以及若干系统页面）。只会启动已导出的界面。

## 截图

仓库里目前没有截图。

## 安装与构建

用 [Android Studio](https://developer.android.com/studio) 打开本仓库，或在命令行：

```bash
./gradlew assembleDebug
./gradlew installDebug      # 调试包，无 R8，界面较慢
./gradlew assembleRelease   # 日常使用建议装已签名的 release
./gradlew testDebugUnitTest
```

环境：JDK 21（应用字节码目标仍为 Java 17）、Android SDK Platform 36。把 SDK 路径写在不提交的 `local.properties` 里。

debug 包在项目根目录存在不提交的 `signing/toolbox-debug.keystore` 时用它签名，否则用默认调试密钥。release **不会**再用这把调试密钥签名。需要发布签名时，在不提交的 `keystore.properties` 或环境变量里配置：

| `keystore.properties` | 环境变量 |
| --- | --- |
| `storeFile` | `TOOLBOX_RELEASE_STORE_FILE` |
| `storePassword` | `TOOLBOX_RELEASE_STORE_PASSWORD` |
| `keyAlias` | `TOOLBOX_RELEASE_KEY_ALIAS` |
| `keyPassword` | `TOOLBOX_RELEASE_KEY_PASSWORD` |

`storeFile` 可以是绝对路径，或相对于项目根目录的路径。没有这些值，或文件不存在时，release 包不签名。

## 权限

下列权限都是使用对应功能时才申请，或只是安装期声明、本身不会打开功能。

| 权限 | 用途 |
| --- | --- |
| 精确/粗略位置 | 位置读取、天文定位；只有你开始定位时才申请，不申请后台位置 |
| 使用情况访问（`PACKAGE_USAGE_STATS`） | 应用用量与流量 |
| 查询全部应用（`QUERY_ALL_PACKAGES`） | 解析用量列表里的应用名。安装期声明；若上架 Play，属于受限权限 |
| 悬浮窗、前台服务、通知 | 实时监控悬浮窗；模拟位置会话的状态通知（Android 13+ 会申请通知权限） |
| 通知监听 | 为通知快速记账声明。该功能暂时关闭，监听组件默认禁用 |
| 相机 | TOTP 扫码，用时再申请 |
| 网络 / 网络状态 / 组播 | 公网 IP、汇率、海拔、地图瓦片、WebDAV、局域网扫描（扫描进行中才加入组播） |
| 模拟位置（`ACCESS_MOCK_LOCATION`） | 仅用于出现在开发者选项的模拟应用列表；声明本身不会开启模拟 |

系统自动备份已关闭（账本和令牌属于敏感数据）。请用应用内的 JSON 导出或 WebDAV 同步备份。

定位地名由系统地理编码器解析，可能会联网。公网 IP、海拔、汇率、地图瓦片和 WebDAV 都只在你主动操作时访问对应服务。

## 文档

- 开发约定：[AGENTS.md](AGENTS.md)
- 目录与包结构：[docs/PROJECT_STRUCTURE_CN.md](docs/PROJECT_STRUCTURE_CN.md)
- 参考项目与第三方许可：[docs/REFERENCES.md](docs/REFERENCES.md)

## 许可

本仓库目前没有 `LICENSE` 文件。公开发布前请选定许可证。内置的 MapLibre GL JS 为 BSD-3-Clause，见 `app/src/main/assets/mockmap/LICENSE-maplibre.txt`；ZXing 为 Apache-2.0。
