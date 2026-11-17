# 悬浮球验证（0.5.0-dev）

以下为 2026-11-13 的历史验证，NOT_STICKY 与通知拒绝不启用的结论已被文末 2026-11-17 策略替代。

日期：2026-11-13。功能及平台依据见 [悬浮球](floating-ball.md)。未接入系统使用情况统计、网络、分析 SDK 或无障碍服务。

## 构建与回归

```bash
./gradlew :core:search:build :app:assembleDebug :app:lintDebug --offline --console=plain
python3 tools/smoke_ball_android.py --serial emulator-5554
python3 tools/smoke_ball_android.py --serial emulator-5556
python3 tools/smoke_android.py --serial emulator-5554
python3 tools/smoke_android.py --serial emulator-5556
```

构建通过，Lint 0 项，纯 JVM 模块共 24 项测试，失败/错误为 0；两版本悬浮球专项和原有入口完整回归均通过。

设备为 Pixel 7 API 35 和 android10test API 29。所有脚本只接受模拟器，不在用户真机上操作。

## 悬浮球专项

两版本通过：

- 默认关闭，普通启动没有球服务；前台设置手动开启后只有一个球，跨应用保留，服务状态为 foreground 并注册通知 ID 1001。
- 点击球打开一个面板，球移除；返回关闭面板后球恢复。
- 超过 touch slop 的拖动不触发打开，松手贴边；设置关闭后服务/窗口释放，再开启保留位置。
- 进程结束后窗口和服务清理，重新打开司南不擅自开启；手动再次开启仍保留贴边方向和高度。
- 屏幕休眠/非安全锁屏时球隐藏、服务保留；唤醒并解锁后恢复。
- 横屏后球约束在显示边界内；测试返回竖屏再检查通知操作，避免通知操作区域被横屏下的系统通知挤出屏幕。
- 通知「关闭悬浮球」可停止球与服务。
- 撤销悬浮权限后清理窗口/服务，恢复权限后仍需手动开启。

API 35 另通过通知运行时权限的拒绝与再次授权：拒绝不启动球/服务，允许后正常启动。真实进程终止使用应用调试身份发送 SIGKILL，等待后无自动拉起；API 29 的调试域拒绝该命令，改用 `am force-stop`，不声称该镜像验证了相同的 SIGKILL 路径。

原有 `smoke_android.py` 用于检查搜索、九宫格、常用排序持久化/清空/损坏恢复、重复面板、返回/外点/Home、快捷设置磁贴和权限降级。专项结束后再执行，检查默认未启用球时原入口仍独立工作。

## 测试环境处理

专项脚本将原球位置文件备份在应用私有目录；不读取使用汇总，也不把位置内容输出到电脑。结束时关闭球，恢复原位置、悬浮权限、通知授权及 user-set/user-fixed 标记、旋转配置。遗留备份目录时拒绝覆盖，需要先检查并恢复后再运行。

Android 15 系统设置中的部分页面主动隐藏第三方 Overlay；这不是球服务退出。旋转在司南普通页面进行，不绕过系统的安全隐藏行为。Android 10 用窗口管理器旋转命令同步锁定方向，避免直接写设置后尚未完成旋转就断言横屏坐标。

## 截图

API 35：桌面上的球、点击后的面板（球隐藏）、通知关闭入口。

![悬浮球](assets/validation/ball-api35-ball.png)
![点击后的面板](assets/validation/ball-api35-panel.png)
![运行通知](assets/validation/ball-api35-notification.png)

API 29 横屏位置：

![横屏悬浮球](assets/validation/ball-api29-landscape.png)

## 边界

最低 API 26、API 36、厂商真机、密码/生物识别锁屏、TalkBack、折叠屏、长时间待机及分发审核尚未完成。频道被阻止的广播处理已实现，专项主要验证通知初次拒绝和通知关闭动作，未模拟全部厂商的通知设置页面。

NOT_STICKY 不等于平台永不创建新进程；本应用没有重新开启功能的后台调度。应用/设备被强制结束后需用户手动开启；位置记忆不代表自动恢复服务。

## 后台恢复策略验证（2026-11-17）

设备：现有 Android 15 / API 35 模拟器 `emulator-5554`，targetSdk 36；未操作真机，也未读取使用统计。

```bash
./gradlew :app:assembleDebug :app:lintDebug :core:search:build
python3 tools/smoke_ball_android.py --serial emulator-5554 --panel-settings --reboot
```

构建、Lint 与搜索模块 build 通过；Lint 0 错误，4 条固定工具链有更新版本的提示，未变更依赖。已实测系统 SIGKILL 后 START_STICKY 恢复、跨进程开关/位置保留、通知初次拒绝及运行中撤销后继续显示球、真实重启后的 BOOT_COMPLETED 恢复、主动关闭后真实重启不恢复、通知关闭动作、撤销悬浮权限后的清理及恢复权限不自启、锁屏隐藏/解锁恢复和横屏约束。

脚本增加独立开关偏好的备份/恢复、进程暂时不存在的容错轮询及可选 `--reboot`。开机验证使用真实模拟器重启，没有伪造受保护广播。首次轮询因 pidof 的非零返回中断，修正后已通过 SIGKILL；开机恢复后曾在查找通知时失败，已增加通知立即展示、恢复授权后刷新已有通知、脚本授权落盘等待与 UI 等待；最终通知关闭与重启关闭保持均通过。

最低 API 26、API 29/36、本轮厂商真机自启动/后台清理和长时间待机尚未复验；历史 API 29 结果不代表新策略已覆盖。强行停止不是 SIGKILL，不承诺绕过用户或系统的停止限制。

本轮另通过 ADB/UI 专项检查：普通降级页后台设置菜单显示电池优化状态，能打开系统电池优化列表、厂商说明后的应用详情及通知设置；开启球后使用 `am stack remove <应用根任务ID>` 移除司南任务，前台服务和球窗口仍保留，再从设置主动关闭成功。该操作验证任务移除回调路径，不代表厂商一键清理/强行停止可被绕过。专项在模拟器执行，原开关文件、通知授权与标记已恢复，结束时球关闭。
