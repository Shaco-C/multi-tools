# 随身工具箱 · Android

> 2026-09-13 本机已完成首次 Android 构建：56 项 JVM 测试通过，debug APK 已生成。Windows 请从英文路径 `D:\Program\multi-tools\android_app` 打开。环境路径、已知限制和设备验收步骤见[本机开发准备](../docs/本机Android开发准备-20260913.md)。下方旧交付状态以该记录更新为准。

这是“随身工具箱”的原生 Android 多模块工程，界面以仓库中的 `prototype` 交互原型为标准。

## 当前进度

- 已建立 `app → feature → core` 的模块边界。
- 已实现工具注册契约、工具箱首页和统一 Material 3 主题。
- 已实现电费分摊纯 Kotlin 计算核心与单元测试。
- 已实现电费计算页的第一版 Compose 交互。
- 已完成电费工具独立 Room 数据库的第 1 版结构、事务 DAO、写入前一致性校验和数据库测试源码。
- 已将计算页接入 Room：可保存本月账单，显示保存中、成功和失败状态，并阻止重复月份覆盖。
- 已实现订阅 Room 实时数据的历史记录列表、加载状态、空状态和错误重试。
- 已实现历史账单详情、原数据回填修改、删除确认及 Room 事务更新/级联删除。
- 已实现仅统计“我的电表”费用的月度趋势、环比、平均值、最高值和月份明细。
- 已实现新账单自动带入上一个自然月的电表结构与本期读数，并保护手动输入不被覆盖。
- 已实现电费工具独立 JSON 导出、文件预检、二次确认和 Room 单事务恢复。
- 已实现适配 Windows Excel 的 UTF-8 CSV 明细导出，并防护表格公式注入。

本工程目前在没有 Android SDK 的公司电脑上进行静态开发，尚未执行 Gradle 同步或 Android 编译。首次转移到具备 Android Studio 的电脑后，应先完成下面的验证。

## 首次运行

1. 使用 Android Studio 打开本目录。
2. 将 Gradle JDK 设置为 JDK 17。
3. 安装 Android SDK 37，并确认 Build-Tools 和 Platform-Tools 可用。
4. 工程已经携带 Gradle Wrapper，无需全局安装 Gradle。
5. 执行：

```powershell
./gradlew.bat test
./gradlew.bat :app:assembleDebug
```

启动模拟器或连接真机后，再执行 Room 数据库测试：

```powershell
./gradlew.bat :feature:electricity:connectedDebugAndroidTest
```

应用不声明联网权限。正式发布前仍需检查合并后的 Manifest。
