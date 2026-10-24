# Android实施与静态验证记录

日期：2026-10-24。仓库`multi-tools`，起始分支`main`，起始提交`6e93713`，工作区干净。用户授权按现有风格开始实现，本机以静态开发验证为主，迁移后的电脑负责Android构建和设备验证。

## 执行步骤

- [x] 核对工具注册、依赖注入、domain/data/presentation、Room及UI样式。
- [x] 核对项目Java要求：各Android模块source/target为17，core/common与core/backup为jvmToolchain(17)，现有构建记录使用JDK17。终端PATH中的Java8不作为项目要求。
- [x] 定义健身领域模型、校验、计划/记组/撤销/补记和统计规则，完成独立JVM检查。
- [x] 编写独立fitness Room结构与Repository，限制读取范围，事务内保证幂等与历史保护；Room运行出口待验证。
- [x] 接入模块、内容读取与已有主题，编写今日/计划/动作库/统计/管理页面；原生运行与视觉验收待验证。
- [x] 编写个人媒体与完整备份恢复、媒体代次切换和失败保护；Android解码、提交失败及中断待设备验证。
- [x] 增加领域/ZIP单元测试、Room/媒体设备测试源码、SQL与内容检查，执行本机可行检查。
- [x] 整理迁移与下一阶段检查单。
- [ ] 执行项目版本的Gradle/KSP/Compose编译、Lint、全部单元测试和设备测试；生成首份Room schema。
- [ ] 执行ViewModel生命周期、页面操作与完整设备验收矩阵。
- [ ] 完成876条中文/器械/计量/媒体映射、缺项补齐、专业教学与解剖审核、发布包体及性能验收。

## 开发约束

复用ToolModule、PocketToolboxApplication依赖创建和应用组合根；新增`feature/fitness`，不把业务塞入app，不修改电费/饮食数据格式。不升级现有AGP/Kotlin/Room/Compose，不降低Java17以适配当前PATH。

本机发现IntelliJ自带JBR21及Kotlin2.2.20编译器，可用于独立JVM规则验证或Kotlin语法分析；该工具与项目锁定的Kotlin2.3.21/Java17构建环境不同。任何这样的检查仅按其真实范围记录，不冒充Android Gradle、KSP、Compose或APK编译通过。不安装Android SDK，不改系统JAVA_HOME。

## 已写入的工程

模块：[feature/fitness](../../android_app/feature/fitness/README.md)。应用组合根仅增加依赖创建、ToolModule注册和模块依赖，业务仍分为domain/data/presentation。复用MaterialTheme、ToolboxColors、项目现有卡片、表单、导航及状态通知习惯。没有改电费/饮食的数据库、备份格式或依赖版本。

| 工作包 | 当前实现 | 仍需验证 |
| --- | --- | --- |
| 领域与存储 | 稳定身份、快照、四用途、次/秒/每侧；9张Room表；唯一请求、唯一同日动作；独立fitness.db | KSP映射、版本1 schema、Android实际SQLite、事务和设备并发 |
| 动作库 | 类别首页→分类列表；搜索/主练与辅助/器械/来源/收藏/最近；分批显示；双图列表；滑动教学、放大、重试；876来源资料离线入口 | 三主题、小屏/大字体/横屏、离线浏览、返回位置、原生解码内存 |
| 计划 | 四步、单日捷径、单份自动暂存草稿及替换保护、独立选择器；目标、缺项跳转、调整排序；多日复制/部位批量、模板、编辑、归档/删除 | 进程结束恢复、暂存失败、输入焦点、IME、长日期范围和各入口操作 |
| 训练 | 大按钮记组、当前动作聚焦、手动下一动作、撤销与重记、逐组实际值、历史批量补记/批量撤销、停止重加、未来/归档限制 | 快速点击与实际数据库一致；后台、跨日、时区变化、失败后的页面反馈 |
| 今日与统计 | 自然日时间轴、未安排/休息/训练、连续天数、今日部位、恢复依据；7/30天与自定义、计划/用途/部位/动作、组成记录 | 数据刷新、历史跳转、长周期查询、索引和性能实测 |
| 个人与备份 | 自建/编辑/停用、器械偏好；SAF图片导入和ZIP；manifest+JSON+个人媒体；严格预检后整工具替换 | SAF权限/取消/写入失败、原生图片类型、磁盘不足、恢复各提交边界和中断 |

一般恢复文案已重新核对[Mayo Clinic力量训练说明](https://www.mayoclinic.org/healthy-lifestyle/fitness/in-depth/strength-training/art-20046670)，建议同一肌群力量训练之间留出一个完整休息日。48小时仅作为本产品筛查近期重复安排的窗口，不表示用户已恢复。仅日期补记不推算小时，设备时刻异常降级为日期说明，辅助参与不重复累计主练组数。本次打开06中的旧ACSM PDF链接返回404；当前新增文案不依赖该失效链接。

## 内容接入边界

[Android开发内容包](../../content/fitness/android/README.md)由脚本生成：**876条来源资料、1746张原图、16份中文待审定义、17个共享肌肉SVG**。16份使用现有双帧PNG分栏显示，不额外生成一套重复文件。11个样稿/来源候选对应单独存映射，仍待复核。

当前正式审核通过为0；其余860条来源尚未形成完整中文计量定义。不能将原始category或equipment直接猜为计量、数量和稳定固定条件。原始来源可离线浏览与复制为个人动作，个人动作由用户明确选择用途/单位/每侧/器械。全量正式内容建设仍按15—17推进。

Debug构建从固定SHA的本地ZIP同步来源图到`feature/fitness/build/generated/fitnessAssets`，构建和运行均不下载。`releaseApproved=false`时Release不注册健身入口、不纳入开发媒体，现有工具仍沿用原发布流程。新增AndroidSVG 1.4用于复用SVG，坐标与API见[官方说明](https://bigbadaboom.github.io/androidsvg/)，使用Apache 2.0许可证；其他版本未调整。

## 本机已执行检查

| 检查 | 结果 | 能证明与不能证明 |
| --- | --- | --- |
| 独立JVM规则/统计/ZIP | 54项通过；编译目标17，JBR21运行，编译器2.2.20，JUnit4.13.2 | 覆盖日历范围、目标、同日去重/恢复、历史冻结、撤销序位、左右实际值、统计、恢复降级、ZIP关联/哈希/容量/路径及失败清理；不代表项目Kotlin2.3.21插件编译 |
| Kotlin PSI语法分析 | 36份相关kt/kts，0个语法错误 | 已规范化CRLF后解析；不解析Android/Compose符号或Gradle DSL，不代替类型检查 |
| SQLite SQL检查 | Python SQLite3.51.0；从9个Entity结构准备41条实际DAO SQL；聚合/过滤/最近名称/级联样例通过 | 核对SQL字段和语法、主练不被辅助关系放大、实际值缺失不伪造；不是Android实际SQLite/Room/KSP运行或生产性能证明 |
| 内容与本地原包 | 876定义、1746源图引用、16中文样稿、17SVG、ZIP SHA-256一致 | 证明引用与采集一致；不代表教学、中文、解剖与许可审核通过 |
| 工作区检查 | `git diff --check`；验证输出和原包均被Git忽略 | 辅助输出位于`prototype/dist/fitness-native-check`，未加入业务文件 |

可复用检查工具保存在模块`validation/`，路径通过参数或脚本位置解析，不写死本机IDE路径。独立编译器语法工具使用K1分析API，仅用于本机可用编译器；迁移后以项目Gradle编译为准。

已增加**10项Android设备测试源码**：请求并发幂等/目标上限、批次撤销、非法恢复不覆盖、历史条目保护、实际值统计、级联、替换草稿清理、带图恢复与提交失败保留旧代次。尚未执行，不计入54项已通过测试。ViewModel自动化和Compose交互测试仍待补充并运行。

备份当前上限：业务JSON128MiB、单张图片3MiB且最多1600万像素、清单512KiB、ZIP与展开合计各512MiB、最多10000个条目。这些是防止异常文件的实现上限，**不是已经实测可用的手机容量承诺**；导出也核对相同边界。大备份的对象内存和文件空间需设备专项测试。

## 迁移到下一台电脑

1. 携带完整仓库、正常项目配置，以及Git忽略的`content/fitness/source-archives/free-exercise-db-f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5.zip`。原包99,702,843字节，SHA-256为`3fad20f601ee50376781ecc41fcff9674ccaaff3427a4c397f472496e23a4982`。只拉Git没有完整原图包。
2. 按项目既定Java17/SDK37环境同步；不要依据终端默认Java8降级编译选项。继续使用锁定的AGP9.4.0、Kotlin2.3.21、KSP2.3.12、Room2.8.5及Compose BOM版本。
3. 在仓库根目录执行内容生成与检查；进入`android_app`执行模块与应用构建：

```powershell
node content/fitness/scripts/build-native-content.mjs
python android_app/feature/fitness/validation/check-content.py
python android_app/feature/fitness/validation/check-sql.py
cd android_app
.\gradlew.bat :feature:fitness:testDebugUnitTest :feature:fitness:lintDebug :app:assembleDebug
.\gradlew.bat :feature:fitness:connectedDebugAndroidTest
```

4. 首次KSP成功后检查并提交`feature/fitness/schemas/`产生的真实版本1 schema，不手造文件或使用破坏性迁移兜底。执行全项目现有测试，回归电费/饮食与Release入口、资源和合并Manifest。
5. 用飞行模式完成“建计划→选动作→逐组记→误点撤销→补记→统计→自建带图→导出→恢复”，并走大字体、长列表、旋转、后台、进程结束、跨日、时区、磁盘不足及恢复中断矩阵。测量Debug原图包、优化媒体、APK与安装大小和解码峰值。
6. 完成876条正式内容映射与审核，再考虑开启Release健身内容。**目前结论是首轮原生实现与静态检查已落地，尚未达到原生运行验收或正式发布出口。**
