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

“随身工具箱”把常用的小工具集中在一个应用中。当前正式版首先提供电费分摊：输入总金额和各户电表读数，即可得到精确到分的分摊结果，并在本机管理历史账单、趋势与备份。

## 下载

前往 [GitHub Releases](https://github.com/Shaco-C/multi-tools/releases/latest) 下载最新正式版 APK。应用最低支持 Android 8.0（API 26）。

## 已有功能

- **精确电费分摊**：按实际用量计算，处理分币余数，确保各户金额之和等于总账单。
- **历史账单管理**：保存、查看、修改和删除每月账单。
- **自动带入**：创建新账单时带入上一期电表结构和读数。
- **本人费用趋势**：展示折线、每个节点的金额、平均费用和最高费用。
- **本地导入导出**：支持 JSON 备份恢复和适合表格软件查看的 CSV 导出。
- **背景主题**：内置清爽、暖白、纯白三种高对比度浅色主题，并记住选择。
- **离线与隐私**：Manifest 不声明联网权限，业务数据仅保存在当前设备。

## 技术架构

- Kotlin + Jetpack Compose + Material 3
- 单 Activity、Navigation Compose
- Room 本地数据库
- 多模块结构：`app → feature:electricity → core:*`
- JDK 17、Gradle 9.6.0、AGP 9.4.0
- `compileSdk 37`、`targetSdk 36`、`minSdk 26`

## 本地开发

Windows 环境建议从不含中文的入口路径打开工程，例如本机使用的 `D:\Program\multi-tools\android_app`。

```powershell
cd android_app
.\gradlew.bat :core:common:test :feature:electricity:testDebugUnitTest :app:assembleDebug
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

账单数据默认只存在应用的 Room 数据库中。卸载应用前如需保留数据，请先在应用内导出 JSON 备份。仓库不会提交签名密钥、设备数据库、导出账单、SDK 路径或构建产物。
