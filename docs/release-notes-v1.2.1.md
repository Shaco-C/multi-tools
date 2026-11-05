# v1.2.1 · 训练计划复用与电费账单确认

- 修复查看动作详情再返回时列表位置丢失，保留加载数量和筛选状态。
- 新增整份历史训练计划复制：选择新起始日期，保留每日安排和目标；生成独立草稿，不复制完成记录，保护已有草稿。
- 电费录入结果及历史账单展示各户计算算式，区分比例金额与分币后的最终应付。
- 保存电费前显示完整账单确认，支持取消修改、失败重试、多户滚动及大字体。

正式包：`com.pockettoolbox`，版本1.2.1（versionCode 4），Android 8.0及以上。沿用正式签名，可覆盖升级已有正式版；升级前建议导出各工具备份。测试版使用独立包名，不与正式版共享数据。

验证：204项主机测试通过，包含Compose/Room模拟；正式包构建、Lint、签名及离线素材校验。实体触摸、输入法和设备流畅度仍以真机体验为准。

## English

Preserves workout list positions after viewing details, adds independent copies of entire workout plans, and explains electricity allocations with formulas and a confirmation preview before saving. Existing workout history is not copied. Supports upgrades from previous stable versions with the same application ID and signing certificate. Android 8.0+ required.
