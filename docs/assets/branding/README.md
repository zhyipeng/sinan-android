# 司南图标来源

2026-11-12 按用户要求复用相邻桌面项目 `../sinan` 的 App 图标。

- SVG 原稿：`../sinan/apps/desktop/assets/icon.svg`，原样复制为本目录的 `icon.svg`。
- PNG 原稿：`../sinan/apps/desktop/assets/icon.png`，原样复制为本目录的 `icon.png`，保留桌面版预览。
- Android 自适应图标：`mipmap-anydpi/ic_launcher.xml` 使用独立背景和前景 VectorDrawable；蓝色渐变、罗盘、搜索柄、刻度和指针来自 SVG。最低 API 26，无需额外的旧版位图回退。
- 自适应背景铺满系统遮罩，去除桌面版预绘制的圆角边框和投影；前景以中心缩放至 75%，给搜索柄和系统遮罩保留空间。
- Manifest 的普通图标与圆形图标均指向 `@mipmap/ic_launcher`。原绿色占位图标已移除。
- 单色图标复用前景的透明轮廓，供支持主题图标的系统使用；本次未单独开启系统主题图标验证。

本次没有修改桌面项目。后续品牌图形变化时，应从桌面 SVG 同步，并检查自适应遮罩效果。
