# 居家健身 Android模块

Java源码/字节码兼容级别为17，遵循本工程配置，与本机PATH中的Java8无关。领域、存储、页面分别位于domain/data/presentation；应用壳通过ToolModule和Application依赖容器接入。本工具使用独立fitness.db，不改其他工具资料。

原生源码包含今日时间轴与自由顺序计组、计划四步及独立选择器、滑动图教学、统一坐标图统计、自建动作和带图片的ZIP备份。首轮源码记录见[18](../../../docs/居家健身工具/18-Android实施与静态验证记录.md)，初次原型对照见[19](../../../docs/居家健身工具/19-原型对照与本机验证记录.md)，体验修订见[20](../../../docs/居家健身工具/20-统计图与全量动作及自由记组.md)，发布结果见[21](../../../docs/居家健身工具/21-v1.2.0正式发布.md)。106项健身Debug主机测试及7项Release专项测试已通过；真实设备验收仍待执行。

## 内容

Debug和Release使用同一[动作内容包](../../../content/fitness/android/README.md)：892个内置可选条目，由876条来源动作与16份中文待审样稿组成。来源动作可分类、搜索、收藏和直接加入计划，保留英文原名、原说明及1746张离线源图。首次记录前可调整单位与每侧/合计口径，有完成或撤销历史后锁定。0个专业审核通过。v1.2.0起正式开放健身入口，发布开关和专业审校状态分别保存，见[21](../../../docs/居家健身工具/21-v1.2.0正式发布.md)。

应用Debug包名为`com.pockettoolbox.debug`，名称为“随身工具箱·测试版”，可与正式版同机安装，资料独立保存。正式版仍使用`com.pockettoolbox`。

迁移需另带被Git忽略的`content/fitness/source-archives/`中的固定版本ZIP；缺失时可在仓库根目录显式运行`content/fitness/scripts/restore-source-archive.ps1`联网重建并校验。构建时校验SHA-256并从已有本地文件生成assets；不需要网络下载内容。新增SVG渲染依赖由正常Gradle仓库获取。

## 验证

- `validation/run-domain-check.ps1`：明确传入JavaExe、KotlinHome和序列化/JUnit/Hamcrest RuntimeJars，以目标17编译独立领域及ZIP测试。输出到被忽略的prototype/dist，结果不代表Android编译。
- `validation/KotlinSyntaxCheck.kt`：Kotlin PSI语法检查器，本机2.2.20编译器使用；不做Android符号解析。
- `validation/check-sql.py`：Python标准库SQLite准备实际DAO查询并核对统计/过滤/级联样例；不是Room/KSP/Android SQLite验证。
- `validation/check-content.py`：Python3.11及以上标准库检查目录、原包SHA、876/1746及样稿引用。
- `validation/check-apk.py`：验证最终Debug/Release APK中所有图片路径、1746张源图哈希、肌肉图及授权文件；Release按发布配置核对是否携带健身素材。
- `src/androidTest/`：17项Room、并发、媒体解码及事务失败测试；同一份存储测试也由本地Robolectric执行。手机验证仍需运行`connectedDebugAndroidTest`。
- `src/test/`：领域/ZIP规则、ViewModel草稿和错误恢复、全量来源动作映射及备份往返、真实生成素材解码、三主题页面导航与状态恢复、交替记组、坐标图选择与分页测试。Robolectric缓存及临时文件跟随`GRADLE_USER_HOME`，本机位于D盘。

本工具备份需要媒体文件，未将仅JSON的公共BackupContributor误用为完整恢复。ZIP内分别保存manifest、业务快照与个人媒体；内置内容随应用分发。恢复先检查关联/容量/哈希/图片，再准备新媒体代次；Room事务提交新指针后清理旧目录。
