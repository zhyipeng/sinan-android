# 可选悬浮球

用户于 2026-11-13 确认：默认关闭，设置手动开启；点击打开面板，面板展开时隐藏球、收起后恢复；支持拖动贴边并记住位置；运行期间有通知和关闭入口。2026-11-17 用户确认调整为保存开启意愿、系统粘性恢复与解锁后的开机恢复；不引入定时保活任务。

## 开启与关闭

面板左下角设置图标直接切换到内置设置页，点击「开启悬浮球」；普通降级页仍可通过原设置菜单开启。开启前展示用途说明，只在用户操作后请求必要权限。已有悬浮权限但不启用球时，继续使用原有桌面/磁贴面板，不启动常驻服务。

悬浮球需要悬浮显示权限。Android 13+ 首次开启按需请求通知权限，拒绝或关闭通知频道仍可运行，并保留司南设置中的关闭入口；服务始终调用 startForeground 提交合法通知，不另建频道绕过用户选择。授权悬浮权限后需返回设置再次开启，不从权限页面或后台直接拉起服务。

可通过面板内设置页、普通降级页设置菜单或运行通知的「关闭悬浮球」停止。通知正文点击打开搜索面板，通知「设置」按钮直接进入悬浮面板的设置页。关闭时删除球窗口、停止前台通知、注销屏幕/通知广播、权限观察及显示监听。

## 服务与平台边界

`FloatingBallService` 为不导出的 `specialUse` 前台服务，Manifest 声明 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE` 和用途说明。Android 34+ 传入对应服务类型，旧版本使用兼容的前台服务调用；不冒用数据同步、媒体播放或短服务类型。

服务从前台设置明确启用，或在前台重新打开应用时恢复已保存的开启意愿；开机广播也可尝试恢复。点击可见球时，在保有悬浮显示权限的情况下直接启动既有 `PanelActivity`；面板实际显示后再移除球，避免把不可见窗口当作启动条件。通知使用系统直接发送的不可变 Activity PendingIntent，关闭动作发送给不导出的服务，没有通知 trampoline。

开启意愿保存在独立的 `floating-ball-state` SharedPreferences，后台串行读取及 commit；明确开启先落盘再启动服务，冷启动等待读取完成，不将默认 false 当成已关闭。服务返回 `START_STICKY`，空 Intent 重建时依据保存的意愿恢复；onDestroy 不清除意愿。主动关闭、通知关闭动作（不是禁用通知）或撤销悬浮权限会清除意愿并停止服务。

只接收解锁后的 `BOOT_COMPLETED`，恢复已开启且有悬浮权限的球；不处理锁定启动，不主动弹出 Activity。移除最近任务不主动停止服务，保留 `stopWithTask=false`；不添加 onTaskRemoved 闹钟、JobScheduler、WorkManager 或精确闹钟权限。强行停止不等同于进程被杀，系统可能阻止恢复直至用户重新打开应用；系统任务管理器停止和厂商清理也不保证可恢复。

使用 `AppOpsManager` 监听悬浮权限变化，撤销后停止服务；屏幕关闭/锁定期间移除球，解锁后恢复。通知禁用和频道阻止广播触发重新检查。锁屏不尝试启动面板、唤醒设备或绕过解锁。系统设置等敏感页面可由操作系统主动隐藏第三方窗口，服务遵守该限制，不尝试强制显示。

## 窗口、拖动与位置

球为 48dp 的非聚焦 Overlay，默认使用现有司南图标和品牌蓝背景；0.6 起可选其他样式和透明度，见 [外观设置](ball-appearance.md)。触摸区域与窗口大小相同，不用全屏透明遮罩。点击触发 `performClick`，保留无障碍描述。移动超过系统 touch slop 才视为拖动；拖动、取消触摸和多指干扰不会触发打开面板。

松手按物理屏幕左右位置选择最近侧，边缘保留 4dp；不随 RTL 语言翻转左右。高度保存为系统栏/刘海安全区域内的比例，而非绝对像素。旋转和显示变化时重新计算边界并约束到屏幕内；位置偏好读写在单独后台线程串行执行。

位置 SharedPreferences 保存 `right`、`fraction`；独立状态偏好只增加 `enabled` 开关，无逐次拖动日志。不增加使用行为采集；「清空使用记录」仅清除排序统计，不清除球位置。应用自动备份仍禁用。

`FloatingBallController` 维护启用状态和已显示面板的宿主集合；新旧 Activity 重建分别移除自己的标记，避免旧宿主销毁时误覆盖新面板状态。球存在时仍由既有面板管理搜索、启动及窗口释放。

## 官方依据与验证边界

实现时核查：

- [前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)：specialUse 权限、类型及用途属性；Google Play 分发时用途需要另行审核，当前仅为本地开发实现。
- [后台启动前台服务限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)：前台用户操作启动，开机广播使用官方后台启动例外；Android 15+ 单靠悬浮权限的例外还要求可见 Overlay。
- [后台 Activity 启动](https://developer.android.com/guide/components/activities/background-starts)：用户可见球的点击与已授予悬浮权限，通知使用系统触发的 Activity PendingIntent。
- [通知运行时权限](https://developer.android.com/develop/ui/views/notifications/notification-permission)：拒绝通知后前台服务仍需提交通知，通知栏可能不显示，保留司南设置中的关闭入口。

实测记录见 [悬浮球验证](validation-ball.md)。厂商后台策略、最低 API 26、API 36、密码/生物识别锁屏及 TalkBack 专项仍需真机验证，模拟器结果不能替代。

## 后台运行设置（2026-11-17）

面板设置页底部及普通降级页菜单提供「悬浮球后台运行设置」。动态查询 `isIgnoringBatteryOptimizations` 显示当前电池策略，打开系统电池优化列表由用户选择；不声明 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 或直接索要豁免。应用详情入口解释小米 / HyperOS 等系统的后台运行、不限制和自启动设置，不硬编码厂商私有 Activity；入口与选项因系统而异。

官方依据：[Doze 与电池优化](https://developer.android.com/training/monitoring-device-state/doze-standby)、[前台服务后台启动例外](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)。电池豁免不等于厂商自启动许可，也不保证服务永久运行。
