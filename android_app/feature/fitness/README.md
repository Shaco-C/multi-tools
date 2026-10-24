# 居家健身 Android模块

Java源码/字节码兼容级别为17，遵循本工程配置，与本机PATH中的Java8无关。领域、存储、页面分别位于domain/data/presentation；应用壳通过ToolModule和Application依赖容器接入。本工具使用独立fitness.db，不改其他工具资料。

原生源码包含今日时间轴与计组、计划四步及独立选择器、滑动图教学、统计、自建动作和带图片的ZIP备份。完整实现范围、已执行检查和待验出口见[实施记录18](../../../docs/居家健身工具/18-Android实施与静态验证记录.md)。当前未进行Android Gradle/KSP/Compose编译和设备验收。

## 内容

Debug使用[开发内容包](../../../content/fitness/android/README.md)：876条来源资料/1746张源图离线浏览，以及16份中文待审样稿；不等同于876个已经完成中文教学与计量定义的正式动作。0个专业审核通过。Release默认不显示健身、不携带开发媒体。

迁移需另带被Git忽略的`content/fitness/source-archives/`中的固定版本ZIP。构建时校验SHA-256并从已有本地文件生成assets；不需要网络下载内容。新增SVG渲染依赖由正常Gradle仓库获取。

## 验证

- `validation/run-domain-check.ps1`：明确传入JavaExe、KotlinHome和序列化/JUnit/Hamcrest RuntimeJars，以目标17编译独立领域及ZIP测试。输出到被忽略的prototype/dist，结果不代表Android编译。
- `validation/KotlinSyntaxCheck.kt`：Kotlin PSI语法检查器，本机2.2.20编译器使用；不做Android符号解析。
- `validation/check-sql.py`：Python标准库SQLite准备实际DAO查询并核对统计/过滤/级联样例；不是Room/KSP/Android SQLite验证。
- `validation/check-content.py`：Python3.11及以上标准库检查目录、原包SHA、876/1746及样稿引用。
- `src/androidTest/`：10项真实Room、并发、媒体解码及事务失败测试源码，待完整Android环境执行。

本工具备份需要媒体文件，未将仅JSON的公共BackupContributor误用为完整恢复。ZIP内分别保存manifest、业务快照与个人媒体；内置内容随应用分发。恢复先检查关联/容量/哈希/图片，再准备新媒体代次；Room事务提交新指针后清理旧目录。
