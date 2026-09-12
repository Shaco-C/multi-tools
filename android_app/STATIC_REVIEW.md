# 静态开发检查记录

> 检查日期：2026-09-12  
> 检查范围：不下载 Android SDK、不执行 Gradle 配置或编译

## 第二轮复核与修复

本轮针对隐藏逻辑错误、误操作、返回行为、输入规模和 Compose 重组范围进行了复核，并完成以下修改：

- 工具内部切换“计算 / 记录 / 趋势”时清理旧的子页面栈，避免连续切换后需要反复按返回键。
- 记录页和趋势页返回计算页；计算页退出工具时统一回到首页。
- 编辑过但未保存的计算页同时拦截顶部返回键和 Android 系统返回手势，退出前要求确认。
- 删除任意非本人电表前显示确认对话框，已有读数时明确提示输入会丢失。
- 删除中间户后再新增，默认名称使用单调递增编号，避免出现两个“第 3 户”。
- 账单月份修改后立即重新校验，修复结果仍沿用旧月份状态的问题。
- 正式页面不再预填原型演示金额和读数，也不显示假的“上月费用”。
- 移除没有行为的设置和更多按钮，避免用户点击后无反馈。
- 电表列表改为带稳定 ID 的 `LazyColumn`，最多 20 户时只组合屏幕附近的卡片。
- 为软键盘启用 `adjustResize` 和 IME Insets，降低底部输入框被键盘遮挡的风险。
- 限制名称和数值输入长度，并拒绝科学计数法，防止异常粘贴造成超大 `BigDecimal` 运算。
- 为工具标签加入 Tab 选择语义，改善无障碍焦点和状态播报。
- 补充 Money、ViewModel、删除确认、月份校验、零金额和重复 ID 测试。
- 增加 Room 2.8.5、KSP 2.3.12、电费账单与分摊明细表、事务 DAO、级联删除和写入一致性校验。
- 将计算结果通过 Repository 和协程接入 Room，加入保存中锁定、成功、失败及重复月份处理。
- 增加 Room `Flow` 历史摘要、按月倒序列表、加载/空/错误重试状态及对应测试。
- 增加账单详情、编辑回填、事务更新、重复月份保护、删除确认与级联删除。
- 修复设备系统时间回拨时修改时间可能倒退的问题，保证 `updatedAt` 单调不回退。
- 增加仅统计本人费用的月度趋势、环比、平均/最高费用、缺月处理、月份明细和错误重试。
- 增加上月读数精确查询、整户结构回填、编辑态隔离、异步竞态保护和手动输入防覆盖。
- 增加工具级 JSON 备份契约、SAF 文件读写、格式/大小/业务校验、恢复预览确认和 Room 原子替换。
- 增加 UTF-8 BOM CSV 明细导出、Windows 换行/引号兼容、精确金额格式和公式注入防护。
- 当前共有 56 个 JVM 测试方法和 16 个 Android 数据库测试方法；均需在笔记本完成动态执行。

## 最终交付复核

- 在金额、分摊算法、Room 事务、持久化校验、自动带入竞态、JSON 校验、CSV 安全和工具扩展点补充中文维护注释。
- 复核全部模块依赖方向、路由注入、数据库事务边界、备份恢复范围和 CSV 导出链路，未发现新的静态阻断问题。
- 前端交互原型通过 `npm run lint` 和 `npm run build`；同时将原型分摊算法改为与 Android 一致的按余数补分算法。
- Android 动态结果仍须以笔记本上的 JDK 17、SDK 37、Gradle 测试和真机构建为准。

## 已完成

- 建立 `app`、`core:common`、`core:designsystem`、`core:navigation`、`core:backup`、`feature:electricity` 六个模块。
- 使用 `app → feature → core` 依赖方向。
- 使用 AGP 9.4 内置 Kotlin；Android 模块未应用已不兼容的 `kotlin-android` 插件。
- 使用 Compose BOM 管理 Compose 库版本。
- Room 数据库归 `feature:electricity` 独立拥有，数据库版本从 1 开始并导出 schema；未启用破坏式迁移。
- 加入 Gradle 9.6 Wrapper 启动脚本、配置和官方 Wrapper JAR。
- Android Manifest 未声明 `INTERNET`，并关闭系统云备份 `allowBackup`。
- 实现工具注册表、工具箱首页、电费内部导航和计算页。
- 实现纯 Kotlin 精确分摊算法与五组单元测试。
- 历史页、详情页、修改页、删除流程、趋势页、自动带入、JSON 恢复和 CSV 导出均已连接 Room 数据。

## 已执行的静态检查

- Manifest 与样式 XML 可以被 XML 解析器读取。
- `settings.gradle.kts` 中每个模块均存在 `build.gradle.kts`。
- Core 没有导入 Feature。
- Feature 没有导入 App。
- 未发现联网权限声明。
- 未发现旧 `org.jetbrains.kotlin.android` 插件引用。

## 必须在笔记本执行的动态验证

- Gradle 9.6 / AGP 9.4 / Android SDK 37 同步。
- Kotlin 与 Compose 编译。
- `:feature:electricity:test` 单元测试。
- `:feature:electricity:connectedDebugAndroidTest` Room 创建、事务读写和外键级联测试。
- `:app:assembleDebug` APK 构建。
- Compose 页面在真机上的尺寸、键盘、返回手势与深色模式。
- 使用 Layout Inspector、Profiler 或 Macrobenchmark 检查实际帧耗时；静态检查不能证明真机始终无掉帧。
- Room schema 生成、数据库迁移与 APK 升级安装。

在以上验证完成前，本记录不能替代真实编译结果。
