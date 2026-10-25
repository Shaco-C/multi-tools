# 随身工具箱 v1.2.0 · 居家健身

居家健身正式加入工具箱，与电费分摊和饮食不适记录一起使用。支持离线安排训练、查看动作、记录完成组数和回顾统计。

## 本版功能

- 单日、多日训练计划，主练部位、动作目标、休息日、模板与草稿暂存。
- 每个动作独立记组，支持A、B、A交替完成、撤销、组明细、实际值和历史补记。
- 统一柱状坐标图，显示数字；按部位或动作比较天次、组段数，支持日期/计划筛选、点选明细及共用刻度的分页。
- 892个内置可选条目：876个来源动作＋16个中文样稿，支持分类、搜索、收藏和直接加入计划。
- 1746张来源原图离线使用，来源动作保留英文原名与原说明；首次记录前可调整次数/秒数和合计/每侧口径。
- 自建动作、个人图片、器械偏好，以及包含计划、有效/撤销记录、模板、收藏、图片和草稿的健身ZIP备份恢复。
- 修复草稿恢复、归档状态、训练日期和媒体处理中的边界问题。
- 中英文README及安装、备份、构建与素材来源说明。

## 安装与迁移

- Android 8.0（API 26）及以上，正式包名`com.pockettoolbox`，versionName `1.2.0`，versionCode `3`。
- 沿用既有正式签名，可覆盖升级v1.0.0 / v1.1.0；电费与饮食使用原数据库，健身使用独立数据库。
- 独立测试版包名为`com.pockettoolbox.debug`，可与正式版共存。测试资料如需迁移，请在测试版导出对应工具备份，再在正式版手动恢复；健身使用ZIP，电费与饮食使用JSON。
- 恢复会替换当前工具资料，请先备份。系统自动备份已关闭，卸载前务必导出。备份文件未加密。
- 正式安装包为`multi-tools-v1.2.0.apk`，提供同名`.sha256`校验文件。应用不声明联网权限。

## 内容状态与验证

来源动作保留英文原文，中文教学与专业审校尚未完成，专业审校数量仍为0。892是可选条目数，部分中文样稿与来源动作存在候选对应；应用内显示内容状态。来源固定为free-exercise-db版本`f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5`，出处和授权文件随APK分发。

191项Debug主机测试及7项Release专项测试通过，Debug / Release构建成功，两种Lint均为0错误、17警告。最终APK图片哈希、素材路径、授权文件及正式签名检查通过。详细验证见[本机发布记录](https://github.com/Shaco-C/multi-tools/blob/v1.2.0/docs/居家健身工具/21-v1.2.0正式发布.md)。系统文件选择器、触摸、键盘和流畅度仍需真机验收。

## English

Home workout tracking is now available in the release app: single-day and multi-day plans, flexible set recording and undo, a shared coordinate chart, personal exercises, and fitness ZIP backups with images. The library offers 876 source exercises plus 16 Chinese drafts with offline photos. Source names and instructions remain in English; professional teaching review is incomplete.

Android 8.0+; application ID `com.pockettoolbox`; version `1.2.0` / code `3`; same release signing certificate for upgrades. The separate test app uses `com.pockettoolbox.debug`. Export and restore backups manually to migrate between the two apps. Full English project documentation: [README.en.md](https://github.com/Shaco-C/multi-tools/blob/v1.2.0/README.en.md).
