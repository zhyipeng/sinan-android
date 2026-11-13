# 可选悬浮球（0.5.0-dev）

用户于 2026-11-13 确认：默认关闭，设置手动开启；点击打开面板，面板展开时隐藏球、收起后恢复；支持拖动贴边并记住位置；运行期间有通知和关闭入口。当前不引入开机启动、定时保活或自动恢复任务。

## 开启与关闭

面板左下角设置图标进入普通页面，再点该页键盘的设置图标，菜单最后一项为「开启悬浮球」。开启前展示用途说明，只在用户操作后请求必要权限。已有悬浮权限但不启用球时，继续使用原有桌面/磁贴面板，不启动常驻服务。

悬浮球需要悬浮显示权限和可用通知。Android 13+ 首次开启按需请求通知权限，拒绝时不启动球或服务；可再次开启或转到系统通知设置。通知权限/频道关闭时先引导恢复通知，避免启动缺少可见关闭入口的功能。授权悬浮权限后需返回设置再次开启，不从权限页面或后台直接拉起服务。

可通过普通页面设置菜单或运行通知的「关闭悬浮球」停止。通知正文点击打开搜索面板，通知「设置」按钮进入普通页面。关闭时删除球窗口、停止前台通知、注销屏幕/通知广播、权限观察及显示监听。

## 服务与平台边界

`FloatingBallService` 为不导出的 `specialUse` 前台服务，Manifest 声明 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE` 和用途说明。Android 34+ 传入对应服务类型，旧版本使用兼容的前台服务调用；不冒用数据同步、媒体播放或短服务类型。

服务只从前台设置中的明确启用动作启动。点击可见球时，在保有悬浮显示权限的情况下直接启动既有 `PanelActivity`；面板实际显示后再移除球，避免把不可见窗口当作启动条件。通知使用系统直接发送的不可变 Activity PendingIntent，关闭动作发送给不导出的服务，没有通知 trampoline。

开启状态只保存在当前进程，服务返回 `START_NOT_STICKY`。进程被杀或设备重启后需手动重新开启；不承诺后台永不被杀，也不通过 alarm、JobScheduler 或开机广播恢复。贴边位置独立保留，不代表服务会自动开启。移除最近任务不主动停止服务，仍可用通知关闭。

使用 `AppOpsManager` 监听悬浮权限变化，撤销后停止服务；屏幕关闭/锁定期间移除球，解锁后恢复。通知禁用和频道阻止广播触发重新检查。锁屏不尝试启动面板、唤醒设备或绕过解锁。系统设置等敏感页面可由操作系统主动隐藏第三方窗口，服务遵守该限制，不尝试强制显示。

## 窗口、拖动与位置

球为 48dp 的非聚焦 Overlay，使用现有司南图标和品牌蓝背景；触摸区域与窗口大小相同，不用全屏透明遮罩。点击触发 `performClick`，保留无障碍描述。移动超过系统 touch slop 才视为拖动；拖动、取消触摸和多指干扰不会触发打开面板。

松手按物理屏幕左右位置选择最近侧，边缘保留 4dp；不随 RTL 语言翻转左右。高度保存为系统栏/刘海安全区域内的比例，而非绝对像素。旋转和显示变化时重新计算边界并约束到屏幕内；位置偏好读写在单独后台线程串行执行。

应用私有 SharedPreferences 仅保存 `right`、`fraction` 两项位置，无逐次拖动日志。不增加使用行为采集；「清空使用记录」仅清除排序统计，不清除球位置。应用自动备份仍禁用。

`FloatingBallController` 维护启用状态和已显示面板的宿主集合；新旧 Activity 重建分别移除自己的标记，避免旧宿主销毁时误覆盖新面板状态。球存在时仍由既有面板管理搜索、启动及窗口释放。

## 官方依据与验证边界

实现时核查：

- [前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)：specialUse 权限、类型及用途属性；Google Play 分发时用途需要另行审核，当前仅为本地开发实现。
- [后台启动前台服务限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)：从前台设置启动，避免依赖后台例外；Android 15+ 的悬浮权限例外还要求可见 Overlay。
- [后台 Activity 启动](https://developer.android.com/guide/components/activities/background-starts)：用户可见球的点击与已授予悬浮权限，通知使用系统触发的 Activity PendingIntent。
- [通知运行时权限](https://developer.android.com/develop/ui/views/notifications/notification-permission)：系统允许拒绝通知时运行部分前台服务，本产品主动要求通知可用，以提供状态与关闭入口。

实测记录见 [悬浮球验证](validation-ball.md)。厂商后台策略、最低 API 26、API 36、密码/生物识别锁屏及 TalkBack 专项仍需真机验证，模拟器结果不能替代。
