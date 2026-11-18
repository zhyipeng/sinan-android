# 司南 · Sinan

Android 悬浮式应用启动工具：按需唤出面板，通过内置 T9 九宫格搜索，以应用网格快速启动目标 App。

## 当前状态

2026-11-16 已完成全应用 UI 视觉重构（`0.6.0-dev`）：保持纯原生 View 与本地极简架构，建立浅色/深色语义分层、顶部抓手条与系统安全区适配、T9 卡片按键（含矮屏/横屏自动并排与触感反馈）、数字胶囊一键清空按钮、设置分组卡片、自适应深浅色预览舞台及统一弹窗样式；真实应用网格、内置九宫格、离线拼音/英文/数字搜索、点击启动、按需悬浮面板、权限引导与降级、快捷设置磁贴与悬浮球五款内置外观均正常运行。Android 15（API 35）模拟器已验证核心流程与 UI 适配；Android 10（API 29）历史验证已记录，最低版本、API 36、TalkBack 完整走查和厂商真机兼容性仍待验证。

仅显示当前用户资料的启动入口。未输入时按司南内启动请求的近期频率、最近时间和时段习惯排序，无记录时按名称排列；输入后优先匹配等级，同等级使用会话固定的常用分数，默认最多显示 24 项。支持简拼、全拼、混合拼音、英文和原始数字；引擎支持别名，别名编辑 UI 尚未提供。本地小时汇总保留 30 天，提供清空入口；不记录搜索词或逐次日志，不申请系统使用情况权限。可选悬浮球支持点击唤出、拖动贴边、位置记忆、前台通知及关闭入口；默认关闭。新增经典、游标、北辰、墨玉、朱砂五款样式及 20%–100% 透明度，支持实时预览和保存。置顶尚未实现。

已确定的产品方向：

- 默认隐藏面板，按需唤出；悬浮球为可选入口。
- 搜索结果使用 App 图标网格，内置九宫格，不依赖系统输入法。
- 支持拼音 T9 搜索和应用启动；未输入时按近期使用与频率智能排序。

2026-11-17 新增面板宽高、背景透明度、悬浮球大小、应用图标大小、网格列数及键盘大小设置，支持即时生效与自动保存；详见 [布局与大小](docs/layout-settings.md)。

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
| [面板简化验证](docs/validation-panel.md) | 图标按键、数字栏位置、横屏和大字号 |
| [悬浮球](docs/floating-ball.md) | 开关、权限、通知、位置与生命周期 |
| [悬浮球验证](docs/validation-ball.md) | 模拟器回归、截图与兼容性边界 |
| [面板内设置验证](docs/validation-settings.md) | 页面返回、查询保留与权限弹窗恢复 |
| [悬浮球外观](docs/ball-appearance.md) | 内置样式、面板与球图片、透明度与保存规则 |
| [外观验证](docs/validation-appearance.md) | 预览、真实窗口透明度、保存与截图 |
| [UI 视觉重构验证](docs/validation-ui.md) | 全界面设计规范、交互微调与 API 35 实测截图 |
| [拼音依赖](docs/dependencies.md) | TinyPinyin 来源、许可与词组范围 |
| [布局与大小](docs/layout-settings.md) | 设置范围、屏幕适配、持久化与专项复验 |
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

正式包用打包脚本一步产出（Gradle 构建 → zipalign → apksigner 签名 → 校验 → 输出 `dist/sinan-<版本>-release.apk`）。默认使用本机 debug keystore 签名，可直接安装但不可上架；传入 `--keystore`、`--ks-alias`、`--ks-pass-file` 可改用正式 keystore：

```bash
./tools/package_release.sh
./tools/package_release.sh --keystore /path/release.jks --ks-alias <别名> --ks-pass-file /path/ks-pass.txt
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。安装命令需要连接已授权的设备或模拟器。搜索模块可通过 `./gradlew :core:search:test` 运行固定样例和边界测试；构建成功不等于完成 Android 搜索交互和性能验收。

AGP 与 Kotlin 的兼容依据见 [Android 官方说明](https://developer.android.com/build/releases/agp-8-13-0-release-notes)和 [Kotlin 官方兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)。AGP 8.13.2 补充 Kotlin 2.3 的 R8 支持；具体工具链以实际构建结果为准，不自动追随最新版本。

## GitHub 自动构建

推送 `main` 或提交面向 `main` 的 PR 时，[Android 构建](https://github.com/zhyipeng/sinan-android/actions/workflows/android.yml)会运行 Debug 构建、Lint 和搜索模块验证。通过后可在运行详情的 Artifacts 下载 `sinan-debug`，保留 14 天。

## 来源与约定

方案提炼自 [《设计安卓效率工具》原会话](chatgpt-conversation://6af58939-b008-83e8-a513-9f40fc13c38b)。用户明确表达的需求、原助手建议、本次工程默认值分别记录，原会话中的性能数字均为目标而非实测。

产品名已确认是「司南」；应用 ID `dev.sinan.launcher` 为开发阶段默认值，发布前需确认。源码托管在 [GitHub](https://github.com/zhyipeng/sinan-android)，暂未选择开源许可证。

## 使用当前版本

1. 打开司南即可看到应用网格，无需使用情况访问权限。
2. 点击「允许悬浮显示」进入系统设置；拒绝授权也可继续使用网格。
3. 授权后点击「打开悬浮面板」。之后从桌面启动司南会默认打开面板。
4. 可在系统快捷设置的编辑页面添加「司南」磁贴；点击磁贴按相同规则打开面板或降级页。
5. 点击应用会启动目标并收起；返回、点击面板外或 Home 均释放窗口。键盘左下角的设置图标直接切换到面板内设置页；返回图标或系统返回键回到原搜索。
6. 使用内置九宫格输入，例如微信 `99`、支付宝 `932`、混合输入 `9443226`、Clock `25625`。设备需已安装对应应用；0/1 保留原始数字，短按退格删一位，长按退格清空全部输入；底行为设置图标 / 0 / 退格图标。

7. 普通页面右上角「使用记录」可查看保留说明并清空，随后按名称排列并重新学习；悬浮面板可直接在内置设置页查看和清空记录；普通降级页保留原设置菜单。

8. 面板内设置页可开启/关闭悬浮球，普通降级页也保留该入口；仅开启时按需申请通知权限并运行前台服务；拒绝通知也可使用。球点击打开面板、拖动贴边，锁屏与面板展开时隐藏。可通过运行通知关闭球；关闭通知权限不会关闭球。开关与位置会保留，系统终止进程或设备重启后尝试恢复；主动关闭后不再恢复。

9. 在面板设置页的「悬浮球外观」中选择样式、拖动透明度滑块。修改即时预览并自动保存，关闭球时也可预先设置；不会自动开启服务。

10. 在设置页底部「布局与大小」调节面板宽高和背景透明度、球大小、图标大小、网格列数与键盘大小。普通降级页的设置菜单也提供该入口；列数与图标会按可用空间适配。

11. 在设置页末尾的「面板背景图」可选择、更换图片或恢复默认背景。「悬浮球外观」的内置样式下方可用手机图片替换球图案；普通降级页可从「设置 → 自定义外观」配置两处图片。

悬浮面板保留应用网格、数字胶囊栏、九宫格和顶部轻量指示条，没有顶部标题、设置/关闭文字按钮、输入提示或应用数量。数字栏置于键盘上方，未输入时留空，有输入时右侧出现一键清空按钮；横屏时随键盘位于右侧。

键盘不打开系统输入法；竖屏位于底部，较宽的可用区域与结果区并排。无结果时保留键盘，便于纠错。

模拟器回归（需要已构建 APK、已启动模拟器、`adb` 在 PATH，镜像内有英文名称的 Clock 应用）：

```bash
python3 tools/smoke_android.py --serial emulator-5554
```

脚本仅操作模拟器，会在应用私有目录备份原使用汇总，验证学习、重启、清空和损坏恢复后还原；同时临时切换悬浮权限和添加测试磁贴，结束时恢复。若上次异常中断留下备份目录，脚本拒绝覆盖，需先检查并恢复原汇总；截图输出在 `app/build/smoke-api*/`。

悬浮球专项回归（仅模拟器，开始前需关闭球）：

```bash
python3 tools/smoke_ball_android.py --serial emulator-5554
```

专项脚本临时操作悬浮/通知权限、锁屏与旋转，在应用私有目录备份并恢复位置、外观和开关。使用 `--reboot` 可额外验证模拟器真实重启后的恢复与关闭后不恢复。结束时球关闭，原权限和屏幕设置恢复，不读取或清空使用统计。

外观专项（包含完整悬浮球回归，备份/恢复原位置与外观）：

```bash
python3 tools/smoke_ball_android.py --appearance --serial emulator-5554
```

2026-11-17 调整悬浮球后台策略：持久化开关、系统粘性恢复、开机恢复及通知拒绝降级，设置页底部新增「悬浮球后台运行设置」。不添加定时拉起，强行停止及厂商限制可能阻止恢复，详见 [悬浮球](docs/floating-ball.md)。
