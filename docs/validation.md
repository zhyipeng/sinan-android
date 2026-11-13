# 工程骨架验证记录

日期：2026-11-12。范围：M0 工程骨架，不代表 MVP 或系统兼容性验收完成。

## 环境与命令

- macOS；Android Studio 自带 JBR 21.0.10，JVM 目标 17。
- Android SDK Platform 36，Gradle 8.13，AGP 8.13.2，Kotlin 2.3.0。
- 首次联网构建下载工具链和依赖，随后通过项目自己的 Wrapper 完成离线复验。

```bash
./gradlew :app:assembleDebug :app:lintDebug :core:search:build --offline --console=plain
```

## 结果

| 检查 | 结果 |
| --- | --- |
| Debug APK 构建 | 通过，输出 `app/build/outputs/apk/debug/app-debug.apk` |
| Android Lint | 0 错误、4 警告；均为 Gradle / AGP / Kotlin 有新版本的提示 |
| 纯 Kotlin 模块构建 | 通过；当前测试任务为 `NO-SOURCE`，没有算法测试用例 |
| Gradle Wrapper | 用 8.13 生成整套脚本与 JAR，官方 JAR SHA-256 校验一致，分发包校验和固定 |
| 文档本地链接 / Android XML | 检查通过 |
| 新增文本空白 | 检查通过 |
| Git 忽略规则 | 本机 SDK 配置、环境文件、签名材料和构建输出均不纳入版本控制 |

Lint HTML 报告位于 `app/build/reports/lint-results-debug.html`，属于本机生成文件。固定工具链是当前有意选择，未为了消除更新提示而升级版本。

## Android 15 模拟器验证

2026-11-12 使用现有 `p0test` AVD（Pixel 7 配置、Android 15 / API 35、arm64、1080 × 2400）完成骨架冒烟验证，保留模拟器已有数据。

| 检查 | 结果与证据 |
| --- | --- |
| 安装与冷启动 | `adb install -r` 成功；`am start -W` 返回 `Status: ok`，单次 `TotalTime: 344ms` |
| 竖屏浅色 | 中文、说明文字、系统栏和手势区域显示正常；[截图](assets/validation/api35-portrait.png) |
| 横屏 | 旋转后内容重排，无文字截断或系统栏遮挡；[截图](assets/validation/api35-landscape.png) |
| 深色模式 | 跟随系统切换深色背景和浅色文字；[截图](assets/validation/api35-dark.png) |
| 1.5 倍字号 | 配置切换稳定后，文字正常换行，内容完整可见；[截图](assets/validation/api35-large-font.png) |
| 返回与重新打开 | 系统返回后再次启动成功，单次暖启动 `TotalTime: 89ms` |
| 崩溃检查 | 检查本次应用进程日志，未发现 `FATAL EXCEPTION` |
| 桌面版图标复用 | 替换后再次通过 APK 构建与最终离线 Lint（0 错误、0 警告）；更新安装后，Pixel Launcher 圆形遮罩下罗盘与搜索柄完整可见，点击可启动司南；[截图](assets/validation/api35-launcher-icon.png) |

启动耗时仅为模拟器单次观测，不是性能基准或 P95。测试完成后恢复原有旋转、字体与深浅色设置，模拟器继续运行，司南保留在前台。此次验证未修改业务代码。

## 尚未验证

未进行真机、最低支持版本或其他 Android 版本验证。悬浮权限、应用扫描、后台服务、目标应用启动、T9 与排序尚未实现，性能预算、搜索正确性、命中率和厂商兼容性均未验收。后续功能阶段需分别补齐，不能复用本记录作为相关能力的验收依据。

Git 已在 `main` 分支初始化；本次未创建提交或配置远程仓库。
