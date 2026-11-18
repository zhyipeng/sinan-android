# GitHub 构建与分发

## 日常构建

推送 `main`、提交面向 `main` 的 PR 或手动运行 [Android 构建](https://github.com/zhyipeng/sinan-android/actions/workflows/android.yml)，会生成 Debug APK，并执行 Android Lint 和搜索模块构建及测试。成功后在运行详情的 Artifacts 下载 `sinan-debug`，保留 14 天。

## 发布正式 APK

1. 修改 `app/build.gradle.kts` 的 `versionName` 和 `versionCode`。`versionName` 使用三段数字，`versionCode` 必须大于所有已发布 APK 的版本号。
2. 将版本提交推送到 `main`，确认构建通过。
3. 为该提交创建并推送与版本名对应的标签，例如版本 `0.7.1` 对应 `v0.7.1`。每次推送一个发布标签，等待发布完成后再推送下一个。
4. [Android 发布](https://github.com/zhyipeng/sinan-android/actions/workflows/release.yml)验证标签提交位于 `main` 的历史、构建并检查 Release APK，使用 GitHub Actions 中配置的专用签名完成签名校验，最后在 [Releases](https://github.com/zhyipeng/sinan-android/releases) 发布 `sinan-<versionCode>.apk`。

后续覆盖升级依赖相同的应用 ID 和签名。调试签名与发布签名不同，调试版用户首次切换到正式渠道需要卸载旧包，旧安装的本地设置及记录会随之移除。正式签名材料应在仓库之外保留备份。

## App 检查更新

App 进入前台时最多每 24 小时自动尝试一次。面板设置页底部与普通页面的设置菜单都提供「检查更新」，可绕过冷却立即检查。检查只读取公开 GitHub 发布信息，使用 APK 文件名中的 `versionCode` 判定是否存在更新。用户确认下载后，打开浏览器下载的 APK，按系统提示确认安装。
