<p align="center">
  <img src="docs/assets/app-icon-v1.png" width="140" alt="随身工具箱图标">
</p>

<h1 align="center">随身工具箱</h1>

<p align="center">离线优先、轻量可靠的原生 Android 日常工具集合</p>

<p align="center">
  <a href="https://github.com/Shaco-C/multi-tools/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/Shaco-C/multi-tools"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Offline" src="https://img.shields.io/badge/Data-Local%20Only-14B8A6">
</p>

“随身工具箱”把常用的小工具集中在一个应用中。当前代码包含电费分摊与饮食不适记录：既能精确分摊电费，也能记住出现过不适的品牌和食品，在下次点餐前回顾经历。各工具的数据、历史与备份分别保存在本机。

## 下载

前往 [GitHub Releases](https://github.com/Shaco-C/multi-tools/releases/latest) 下载最新正式版 APK。应用最低支持 Android 8.0（API 26）。

饮食不适工具为 v1.1.0 新增功能，发布前请以 Release 页面列出的版本和功能为准。

## 已有功能

- **精确电费分摊**：按实际用量计算，处理分币余数，确保各户金额之和等于总账单。
- **历史账单管理**：保存、查看、修改和删除每月账单。
- **自动带入**：创建新账单时带入上一期电表结构和读数。
- **本人费用趋势**：展示折线、每个节点的金额、平均费用和最高费用。
- **本地导入导出**：支持 JSON 备份恢复和适合表格软件查看的 CSV 导出。
- **背景主题**：内置清爽、暖白、纯白三种高对比度浅色主题，并记住选择。
- **饮食不适记录**：只登记出现不适的饮食，进食日期记到日；点击记录自动保存不适日期和时分，同一顿饮食可以关联多次不适。
- **常用选项管理**：统一维护品牌、品牌下的食品和不适类型，默认“腹泻”；支持改名、停用和重新启用，保留历史关联。
- **饮食明细与统计**：查看品牌及食品历史、带数字的品牌横向柱状图，切换“不适次数 / 关联饮食次数”，按类型和近 30 / 90 天筛选。
- **记录修正**：支持修改、删除与撤销，以及结束 / 恢复一顿饮食记录。饮食数据支持独立 JSON 备份恢复与 CSV 导出。
- **离线与隐私**：Manifest 不声明联网权限，业务数据仅保存在当前设备。

## 技术架构

- Kotlin + Jetpack Compose + Material 3
- 单 Activity、Navigation Compose
- Room 本地数据库
- 多模块结构：`app → feature:electricity / feature:diet → core:*`
- JDK 17、Gradle 9.6.0、AGP 9.4.0
- `compileSdk 37`、`targetSdk 36`、`minSdk 26`

## 换手机与备份

两个工具分别导出和恢复自己的数据：

| 工具 | 备份入口 | JSON 包含的数据 |
| --- | --- | --- |
| 电费分摊 | 历史记录 → 数据导出与恢复 | 全部月份账单、每户读数、分摊金额与时间 |
| 饮食不适记录 | 管理 → 数据导出与恢复 | 品牌、食品、不适类型、饮食、每次不适的时间、记录状态与默认设置 |

1. 在旧手机的两个工具中分别选择“导出 JSON 备份”，保存两个文件并转移到新手机。
2. 在新手机安装本应用，在各工具对应的备份页选择 JSON 文件。应用会先校验并显示数量，确认后恢复。
3. 恢复采用**替换当前工具全部数据**的方式，不进行合并；其他工具不受影响。新手机已有记录时，请先备份。
4. 检查新手机记录完整后，再处理旧手机上的数据。

CSV 仅用于 Excel 等表格软件查看明细，不能用于恢复。网页原型中的浏览器示例数据独立保存，不属于 Android 备份。JSON / CSV 均为明文文件。

## 本地开发

Windows 环境建议从不含中文的入口路径打开工程，例如本机使用的 `D:\Program\multi-tools\android_app`。

```powershell
cd android_app
.\gradlew.bat :core:common:test :feature:electricity:testDebugUnitTest :feature:diet:testDebugUnitTest :app:assembleDebug
```

正式包需要创建 `android_app/keystore.properties`。可复制 `keystore.properties.example` 后填写自己的密钥路径和密码；真实密钥与配置已由 `.gitignore` 排除。

```powershell
.\gradlew.bat :app:assembleRelease
```

本机迁移、SDK 路径和设备验收步骤见 [Android 开发准备](docs/本机Android开发准备-20260913.md)。采用的总体方案见 [原生安卓应用设计方案](docs/方案A-原生安卓应用设计方案.md)。

## 项目目录

- `android_app/`：正式 Android 工程。
- `prototype/`：早期交互原型，用于设计对照。
- `docs/`：架构、迁移、交付和版本说明。

## 数据与安全

账单与饮食记录分别保存在应用的 Room 数据库中。应用不声明联网权限，系统自动备份和迁移也已关闭；换机或卸载前，请分别导出各工具的 JSON 备份。导出的 JSON / CSV 是明文文件，请自行妥善保管。仓库不会提交签名密钥、设备数据库、个人导出数据、SDK 路径或构建产物。

饮食统计只描述已记录的餐后不适关联，不计算发生率、不判断原因，也不会将未记录的食品标记为安全。使用与验收步骤见 [饮食不适工具实施记录](docs/饮食不适工具实施记录-20261002.md)。
