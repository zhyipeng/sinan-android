# 拼音依赖

2026-11-13 为纯 Kotlin/JVM 搜索模块引入 TinyPinyin `2.0.3`，通过 `PinyinProvider` 接口隔离。只使用单字转换和司南的有限词组覆盖，不修改上游全局词典。

| 构件 | 用途 | 许可与来源 |
| --- | --- | --- |
| `com.github.promeg:tinypinyin:2.0.3` | 离线汉字转拼音 | [上游项目](https://github.com/promeG/TinyPinyin)，Apache 2.0 |
| `org.ahocorasick:ahocorasick:0.3.0` | TinyPinyin 运行时依赖 | [上游](https://github.com/robert-bor/aho-corasick)，[发布 POM](https://repo.maven.apache.org/maven2/org/ahocorasick/ahocorasick/0.3.0/ahocorasick-0.3.0.pom)，Apache 2.0 |
| `junit:junit:4.13.2` | 仅 JVM 测试，不随 APK 分发 | 固定样例、边界和基准测试 |

TinyPinyin 构件使用上游 README 指向的阿里云 Maven 仓库；`exclusiveContent` 只允许该单一模块，其余依赖继续走 Google / Maven Central。不增加 JCenter 或 JitPack。

运行时许可说明和 Apache 2.0 原文位于 `app/src/main/assets/licenses/`，随 APK 打包；没有给司南自身选择开源许可证。

词组覆盖目前包含重庆、银行、音乐、长安、长城、厦门、六安、重装；其他汉字使用库的默认读音。支持显式拼音变体，不穷举多音字组合，不等同于完整词组词典。
