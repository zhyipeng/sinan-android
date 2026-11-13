# M1 系统闭环验证

日期：2026-11-13。版本：`0.2.0-dev`。仅验证当前应用网格与悬浮入口，不代表完整 MVP 验收。

## 构建

```bash
./gradlew :app:assembleDebug :app:lintDebug :core:search:build --offline --console=plain
```

Debug APK 与模块构建通过；最终离线 Android Lint 为 0 错误、0 警告。搜索模块仍为模型代码，测试任务 `NO-SOURCE`，本次不宣称有搜索算法覆盖率。

## 模拟器回归

```bash
python3 tools/smoke_android.py --serial <序列号> --adb <adb可执行路径>
```

脚本仅允许 `ro.kernel.qemu=1` 的模拟器。需要已构建 APK 和英文名称的 Clock 应用；脚本安装更新、不清除数据，临时修改自身悬浮权限和测试磁贴，并在 finally 中恢复原模式。截图保存在忽略的构建目录。`--serial`、`--adb` 和 `--apk` 可指定测试目标及产物。

| 检查 | Android 10 / API 29 | Android 15 / API 35 |
| --- | --- | --- |
| 无悬浮权限显示可用应用网格 | 通过 | 通过 |
| 点击授权按钮进入设置，返回可继续使用 | 通过 | 通过 |
| 普通网格点击 Clock，确认目标 Activity 在前台 | 通过 | 通过 |
| 授权后真实 APPLICATION_OVERLAY 窗口 | 通过 | 通过 |
| 连续三次唤出后仅一个可见悬浮窗口 | 通过 | 通过 |
| 返回、外部点击、Home 后窗口释放 | 通过 | 通过 |
| 悬浮网格启动 Clock 后收起 | 通过 | 通过 |
| 系统快捷设置磁贴唤出 | 通过（旧 Intent 路径） | 通过（PendingIntent 路径） |
| 面板设置入口回到普通网格 | 通过 | 通过 |
| 撤销权限后再次打开降级 | 通过 | 通过 |

API 35 另做旋转、深色和 1.5 倍字号组合切换：仍保留一个可见面板，标题、操作和首行应用正常显示。横屏面板宽度受透明宿主的可用显示区域约束，后续键盘布局需单独适配。

| 截图 | 说明 |
| --- | --- |
| [API 35 普通网格](assets/validation/m1-api35-fallback.png) | 无权限仍能启动 |
| [API 35 悬浮网格](assets/validation/m1-api35-overlay.png) | 位于 Clock 上方 |
| [API 29 横屏面板](assets/validation/m1-api29-overlay.png) | 旧系统悬浮窗口 |
| [API 35 横屏、深色、大字号](assets/validation/m1-api35-landscape-dark-large-font.png) | 配置切换后窗口仍可用 |

## 未覆盖

- 最低 API 26、API 36、厂商真机、折叠屏、工作资料和锁屏专项流程。
- 应用卸载/禁用、名称/语言变化的完整专项矩阵；当前有失效启动反馈和包变化失效机制，未以此代替专项测试。
- T9、使用习惯、隐私数据持久化、悬浮球和性能预算，均不属于本轮实现。

本轮仅执行本地 Git 提交，没有远程推送或发布。
