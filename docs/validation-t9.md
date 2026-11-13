# T9 搜索与键盘验证

日期：2026-11-13。版本：`0.3.0-dev`。范围：离线索引、T9 匹配、内置九宫格和搜索到启动闭环。

## 构建与核心测试

```bash
./gradlew :app:assembleDebug :app:lintDebug :core:search:test --offline --console=plain
```

Debug APK 构建通过；最终 Android Lint 为 0 错误、0 警告。JUnit 测试 12 组通过，测试组内部包含多条固定样例和边界断言：

- 微信、支付宝、抖音、淘宝、高德地图、网易云音乐、重庆银行、绿色、QQ 音乐和 Alipay。
- 简拼、全拼、混合路径和音节边界子串；`94432` 是 `zhi + f + b` 的完整消费，不误判为普通前缀。
- 原始数字及 0/1、英文大小写、声调、ü/u:/v、土耳其语言环境和未知符号边界。
- 去重、多拼音变体、别名优先、不同资料 ID、稳定平局以及使用分不能跨匹配等级。
- 空查询、非法/超长查询、返回数量限制、索引输入拷贝、按键期间不再调用拼音提供器。
- 128 音节名称及高歧义路径，避免穷举全部简拼/全拼组合。

## JVM 基准

300 个合成应用，8 条查询交替，预热 100 次、采样 400 次。JBR 21 本机一次观测：索引构建约 12.0ms，搜索 P50 约 0.21ms、P95 约 0.32ms。

这是桌面 JVM 的合成数据，不含 Android 应用扫描、图标、窗口和绘制耗时，不能作为 Android 端 P95、内存预算或首帧指标的验收证据。完整原始输出位于忽略的 `core/search/build/test-results/test/`。

## 模拟器流程

```bash
python3 tools/smoke_android.py --serial <序列号> --adb <adb可执行路径>
```

在 Android 10 / API 29 和 Android 15 / API 35 上执行扩展后的回归脚本：

| 项目 | API 29 | API 35 |
| --- | --- | --- |
| 普通页 T9 输入 `25625` 筛出 Clock，Camera 被过滤 | 通过 | 通过 |
| `10` 保留原样、短按删除到 `1`、长按清空 | 通过 | 通过 |
| 无结果反馈、清空恢复、快速改输后只保留最新查询 | 通过 | 通过 |
| 悬浮面板 T9 过滤后启动 Clock 并收起 | 通过 | 通过 |
| 权限降级/撤销、授权设置返回、磁贴、重复唤出、返回/外部点击/Home 关闭 | 通过 | 通过 |

中文样例在固定 JVM 测试中验证；没有在模拟器安装真实微信/支付宝，也不读取个人应用数据作为算法测试期望。搜索过程中不保存查询或启动历史。

API 35 另行检查深色模式、1.5 倍字号及面板打开期间的横竖屏切换：12 个按键均在可见区域，按键第二行字母完整，结果与键盘横屏并排。检查后恢复显示设置。

## 证据与边界

| 截图 | 内容 |
| --- | --- |
| [API 35 搜索结果](assets/validation/t9-api35-query.png) | 悬浮面板输入 `25625` |
| [API 35 无结果](assets/validation/t9-api35-no-results.png) | 保留键盘供纠错 |
| [API 29 横屏搜索](assets/validation/t9-api29-query.png) | 旧平台左右布局 |
| [API 35 大字号竖屏](assets/validation/t9-api35-large-font-portrait.png) | 1.5 倍字体与深色 |
| [API 35 大字号横屏](assets/validation/t9-api35-large-font-landscape.png) | 面板打开期间旋转后仍能搜索 |

完整回归自动截图保留在 `app/build/smoke-api*/`，不纳入 Git。

尚未覆盖 API 26 / 36、厂商真机、TalkBack 完整操作、工作资料、折叠屏和 Android 端性能基准。使用情况采集、智能推荐、置顶、别名编辑和悬浮球不属于本轮实现。
