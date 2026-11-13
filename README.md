# 司南 · Sinan

Android 悬浮式应用启动工具：按需唤出面板，通过内置 T9 九宫格搜索，以应用网格快速启动目标 App。

## 当前状态

2026-11-13 已接入本地智能排序（`0.4.0-dev`）：真实应用网格、内置九宫格、离线拼音/英文/数字搜索、点击启动、按需悬浮面板、权限引导与降级、快捷设置磁贴。Android 10 / 15 模拟器已验证主要流程；最低版本、Android 16 和厂商真机兼容性仍待验证。

仅显示当前用户资料的启动入口。未输入时按司南内启动请求的近期频率、最近时间和时段习惯排序，无记录时按名称排列；输入后优先匹配等级，同等级使用会话固定的常用分数，默认最多显示 24 项。支持简拼、全拼、混合拼音、英文和原始数字；引擎支持别名，别名编辑 UI 尚未提供。本地小时汇总保留 30 天，提供清空入口；不记录搜索词或逐次日志，不申请系统使用情况权限。置顶与悬浮球尚未实现。

已确定的产品方向：

- 默认隐藏面板，按需唤出；悬浮球为可选入口。
- 搜索结果使用 App 图标网格，内置九宫格，不依赖系统输入法。
- 支持拼音 T9 搜索和应用启动；未输入时按近期使用与频率智能排序。

## 文档入口

| 文档 | 内容 |
| --- | --- |
| [产品方案](docs/product.md) | 需求来源、MVP 边界、交互、权限降级与验收 |
| [技术架构](docs/architecture.md) | 模块职责、窗口生命周期、数据流与 Android 验证项 |
| [T9 与排序引擎](docs/search-engine.md) | 索引、混合匹配、双模式排序、稳定性与测试样例 |
| [实施路线](docs/roadmap.md) | 里程碑、任务清单和完成标准 |
| [决策记录](docs/decisions.md) | 已确认需求、工程默认值、待确认事项 |
| [验证记录](docs/validation.md) | 工具链、构建结果与验证边界 |
| [M1 验证记录](docs/validation-m1.md) | 双版本模拟器流程验证、截图与复验命令 |
| [T9 验证记录](docs/validation-t9.md) | 引擎用例、JVM 基准、键盘和搜索到启动验证 |
| [本地智能排序](docs/usage-ranking.md) | 汇总字段、保留范围、评分与异常处理 |
| [排序验证记录](docs/validation-usage.md) | 固定时钟测试、双版本设备回归与截图 |
| [拼音依赖](docs/dependencies.md) | TinyPinyin 来源、许可与词组范围 |
| [协作规范](AGENTS.md) | 后续开发约束与验证命令 |
| [图标来源](docs/assets/branding/README.md) | 桌面版司南图标原稿与 Android 自适应资源 |

## 工程结构

```text
app/                  原生应用网格、应用扫描/启动、悬浮面板和磁贴入口
core/search/          纯 Kotlin/JVM 拼音索引、T9 匹配、小时汇总、习惯排序与测试
docs/                 产品、架构、算法与实施文档
gradle/               版本目录和 Gradle Wrapper
```

## 构建与运行

- JDK 17 或 21（JVM 字节码目标为 17）。
- Android SDK Platform 36；默认 Build Tools 35.0.0，缺少时由 SDK 工具安装。
- Gradle 8.13、AGP 8.13.2、Kotlin 2.3.0，版本固定在 Wrapper 和版本目录。
- 最低 Android 8.0（API 26）；`compileSdk` / `targetSdk` 为 36。

在 Android Studio 中打开仓库根目录并同步 Gradle。通过 IDE 配置 SDK，或在不纳入版本控制的 `local.properties` 写入本机 `sdk.dir`；也可配置 `ANDROID_HOME`。首次构建需要联网下载依赖。

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug :core:search:build
./gradlew :app:installDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。安装命令需要连接已授权的设备或模拟器。搜索模块可通过 `./gradlew :core:search:test` 运行固定样例和边界测试；构建成功不等于完成 Android 搜索交互和性能验收。

AGP 与 Kotlin 的兼容依据见 [Android 官方说明](https://developer.android.com/build/releases/agp-8-13-0-release-notes)和 [Kotlin 官方兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)。AGP 8.13.2 补充 Kotlin 2.3 的 R8 支持；具体工具链以实际构建结果为准，不自动追随最新版本。

## 来源与约定

方案提炼自 [《设计安卓效率工具》原会话](chatgpt-conversation://6af58939-b008-83e8-a513-9f40fc13c38b)。用户明确表达的需求、原助手建议、本次工程默认值分别记录，原会话中的性能数字均为目标而非实测。

产品名已确认是「司南」；应用 ID `dev.sinan.launcher` 为开发阶段默认值，发布前需确认。当前未选择开源许可证、配置远程仓库或发布签名。

## 使用当前版本

1. 打开司南即可看到应用网格，无需使用情况访问权限。
2. 点击「允许悬浮显示」进入系统设置；拒绝授权也可继续使用网格。
3. 授权后点击「打开悬浮面板」。之后从桌面启动司南会默认打开面板。
4. 可在系统快捷设置的编辑页面添加「司南」磁贴；点击磁贴按相同规则打开面板或降级页。
5. 点击应用会启动目标并收起；返回、点击面板外、关闭或 Home 均释放窗口。面板「设置」可进入权限状态与普通网格页面。
6. 使用内置九宫格输入，例如微信 `99`、支付宝 `932`、混合输入 `9443226`、Clock `25625`。设备需已安装对应应用；0/1 保留原始数字，短按退格删一位，长按退格或点击清空删除全部输入。

7. 普通页面右上角「使用记录」可查看保留说明并清空，随后按名称排列并重新学习；悬浮面板先点击「设置」进入普通页面。

键盘不打开系统输入法；竖屏位于底部，较宽的可用区域与结果区并排。无结果时保留键盘，便于纠错。

模拟器回归（需要已构建 APK、已启动模拟器、`adb` 在 PATH，镜像内有英文名称的 Clock 应用）：

```bash
python3 tools/smoke_android.py --serial emulator-5554
```

脚本仅操作模拟器，会在应用私有目录备份原使用汇总，验证学习、重启、清空和损坏恢复后还原；同时临时切换悬浮权限和添加测试磁贴，结束时恢复。若上次异常中断留下备份目录，脚本拒绝覆盖，需先检查并恢复原汇总；截图输出在 `app/build/smoke-api*/`。
