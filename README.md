# multi-tools

> 2026-09-13 本机已完成首次 Android 构建：56 项 JVM 测试通过，debug APK 已生成。Windows 请从英文路径 `D:\Program\multi-tools\android_app` 打开。环境路径、已知限制和设备验收步骤见[本机开发准备](docs/本机Android开发准备-20260913.md)。下方旧交付状态以该记录更新为准。

随身工具箱：离线优先的原生 Android 多功能工具集合。正式路线为 Kotlin + Jetpack Compose，首个工具为电费分摊。

## 目录

- `android_app/`：正式开发入口，Android Studio 应打开此目录。
- `prototype/`：React 交互原型，用于界面和交互对照。
- `docs/方案A-原生安卓应用设计方案.md`：采用的架构方案；文档中的早期进度和旧电脑环境属于历史记录。
- `交付清单-20260912.md`、`另一台电脑使用说明.md`：原始交付记录。
- `docs/本机迁移检查-20260913.md`：本次迁移检查与待处理问题。

## Android

模块依赖：`app → feature:electricity → core:*`。已实现精确到分的电费分摊、Room 账单管理、本人费用趋势、上月读数带入、JSON 备份恢复与 CSV 导出。Android Manifest 不声明联网权限。

当前配置为 JDK 17 工具链、compileSdk 37、targetSdk 36、Gradle 9.6.0、AGP 9.4.0。这些是工程声明值，构建可用性仍需实际验证。

```powershell
cd android_app
.\gradlew.bat --version
.\gradlew.bat :core:common:test :feature:electricity:test :app:assembleDebug
# 连接真机或启动模拟器后
.\gradlew.bat :feature:electricity:connectedDebugAndroidTest
```

## 原型

要求 Node.js >=22.13.0，依赖通过 `npm ci` 安装，然后运行 `npm run dev`。当前交付包缺少 `prototype/build/sites-vite-plugin`，需补回或调整原型构建配置后才能启动；详见迁移检查。

## 版本管理

提交源码、文档、测试、Gradle Wrapper、依赖锁文件及 Room schema。忽略缓存、依赖目录、APK、本机 SDK 路径、密钥和设备数据库。个人导出文件放入根目录 `backups/` 或 `exports/`，不要混入源码。`.gitignore` 不能识别任意文件中的秘密，提交前仍应检查 `git diff --cached`。
