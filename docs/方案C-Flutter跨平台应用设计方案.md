# 方案 C：多功能小工具 Flutter 应用设计方案

> 文档状态：推荐候选方案  
> 编写日期：2026-09-10  
> 当前阶段：产品与技术设计、开发环境验证，尚未开始 Flutter 功能开发  
> 首发平台：Android；Web 仅作为开发调试入口；架构预留 iOS

## 1. 方案结论

本方案使用 Flutter 构建“随身工具箱”，日常在 VS Code 中开发，在 Chrome 中快速调试布局和普通交互，在安卓真机上验证数据库、文件导入导出和应用生命周期，最终输出 APK。

项目从第一天采用真正的多包工作区，而不是把所有代码放进一个 `lib` 目录：

- `apps/toolbox_app` 是唯一应用壳，只负责启动、首页、全局主题和功能组装。
- `packages/feature_*` 中的每个工具都是独立 Flutter Package。
- `packages/core_*` 提供经过筛选的公共能力与工具接入契约。
- 应用壳可以依赖所有功能包；功能包不能依赖应用壳，也不能直接依赖其他功能包。
- 每个工具自行拥有页面、计算逻辑、历史记录、趋势和数据库。

这与原生 Android 方案的 `app + core:* + feature:*` 结构含义一致，只是将 Gradle Module 换成 Dart/Flutter Package，并使用 Pub Workspace 统一管理依赖。Dart 官方的 Pub Workspace 会为多包仓库提供统一的依赖解析和锁文件，适合本项目从一开始建立模块边界。

## 2. 项目定位与边界

### 2.1 产品目标

- 个人使用，不提供注册、登录和账号体系。
- 业务数据默认只保存在当前设备中。
- 不依赖服务器、域名或云数据库。
- 首页仅展示可用工具，不展示跨工具的记录或趋势。
- 每个工具对自己的计算、记录、趋势和设置负责。
- 首个工具是“电费分摊”。
- 首版发布为 Android APK，代码结构预留 iOS。
- Web 版本主要用于开发调试，不承诺与 Android 共用数据。

### 2.2 明确不做

- 不做动态下载插件或运行第三方代码。
- 不做跨设备自动同步。
- 不接入广告、埋点、登录或远程配置 SDK。
- 不为了“架构完整”而让每个简单操作都经过 Use Case。
- 不让首页理解任何电费账单字段。
- 不允许不同工具直接访问彼此数据库。

## 3. 产品信息架构

```text
随身工具箱
├─ 工具箱首页
│  ├─ 电费分摊
│  ├─ 未来工具 A
│  └─ 未来工具 B
├─ 电费分摊
│  ├─ 新建计算
│  ├─ 历史记录
│  ├─ 我的费用趋势
│  └─ 工具设置（后续）
└─ 应用设置
   ├─ 外观
   ├─ 数据备份与恢复
   └─ 关于
```

外层导航保持稳定。新增一个具有历史记录的工具时，它把历史页放在自己的功能包中，不向首页增加“全局记录”入口，也不要求所有工具采用相同的数据模型。

## 4. 技术选型

| 范围 | 推荐选择 | 说明 |
|---|---|---|
| SDK | Flutter stable + 随附 Dart SDK | 初始化时锁定并记录实际版本 |
| IDE | VS Code + Flutter/Dart 扩展 | 日常编码、断点与热重载 |
| UI | Flutter Material 3 | Android 首发，同时可适配 iOS |
| 架构 | Feature-first 多包工作区 + MVVM | 对齐 Flutter 官方的 View、ViewModel、Repository、Service 分层思想 |
| 包管理 | Pub Workspace | 单一依赖解析和根锁文件 |
| 路由 | `go_router` | 声明式路由，兼容 Android 返回和浏览器历史 |
| 状态管理 | 首版使用 `ChangeNotifier` + 不可变 UI State | 降低第三方依赖；通过构造函数注入依赖 |
| 结构化存储 | 推荐 Drift/SQLite，开发前做 Android 与 Web 小型验证 | 同一模型可覆盖移动端和 Web 调试；每个工具独立数据库 |
| 轻量设置 | `shared_preferences` | 主题、上次户数等非关键偏好；账单不能存这里 |
| 文件导入导出 | 系统文件选择器插件 + JSON | 由用户主动选择位置，不自动上传 |
| 图表 | Flutter Canvas 自绘首版折线图 | 需求简单，避免引入重量级图表依赖 |
| 金额 | 以“分”为单位的 Dart `int` | 避免浮点金额误差 |
| 测试 | `flutter_test` + integration_test | 覆盖纯计算、仓库、ViewModel 和关键页面流程 |
| APK 构建 | Flutter CLI + Android Gradle Wrapper | 无需全局安装 Gradle，也不使用 Maven 替代 |

所有第三方包的具体版本在工程初始化当天选择兼容的稳定版本并写入根锁文件，不在设计阶段硬编码易过时的版本号。

## 5. 多包工作区结构

建议创建一个新的 Flutter 工程目录，保留当前 HTML 原型作为视觉参考：

```text
price_calculator/
├─ docs/
├─ prototype/                         现有 HTML 交互原型，只作设计参考
└─ flutter_app/
   ├─ pubspec.yaml                    Pub Workspace 根配置
   ├─ pubspec.lock                    整个工作区唯一锁文件
   ├─ analysis_options.yaml           全局静态检查规则
   ├─ README.md
   ├─ apps/
   │  └─ toolbox_app/
   │     ├─ pubspec.yaml
   │     ├─ android/
   │     ├─ ios/                      Windows 上保留，Mac 上构建
   │     ├─ web/
   │     ├─ lib/
   │     │  ├─ main.dart
   │     │  └─ src/
   │     │     ├─ app.dart
   │     │     ├─ app_bootstrap.dart
   │     │     ├─ app_router.dart
   │     │     ├─ tool_registry.dart
   │     │     └─ home/
   │     ├─ test/
   │     └─ integration_test/
   └─ packages/
      ├─ core_common/
      ├─ core_design_system/
      ├─ core_navigation/
      ├─ core_storage/
      ├─ core_backup/
      └─ feature_electricity/
```

### 5.1 与 Kotlin 模块的对应关系

| Kotlin 原生设计 | Flutter 对应包 |
|---|---|
| `app` | `apps/toolbox_app` |
| `core:common` | `packages/core_common` |
| `core:designsystem` | `packages/core_design_system` |
| `core:navigation` | `packages/core_navigation` |
| `core:backup` | `packages/core_backup` |
| `feature:electricity` | `packages/feature_electricity` |
| `feature:future-tool` | `packages/feature_future_tool` |

### 5.2 Pub Workspace

根 `pubspec.yaml` 只管理工作区：

```yaml
name: toolbox_workspace
publish_to: none

environment:
  sdk: '>=3.6.0 <4.0.0'

workspace:
  - apps/toolbox_app
  - packages/core_common
  - packages/core_design_system
  - packages/core_navigation
  - packages/core_storage
  - packages/core_backup
  - packages/feature_electricity
```

每个成员包拥有自己的 `pubspec.yaml` 并声明：

```yaml
resolution: workspace
```

工程初始化时以实际 Flutter 所带 Dart 版本调整 SDK 下限。使用工作区后，在根目录执行一次 `dart pub get` 或 `flutter pub get` 即可统一解析依赖。

## 6. 模块职责与依赖约束

### 6.1 `apps/toolbox_app`

负责：

- `main()` 与全局错误处理。
- 初始化已注册工具。
- 创建全局主题和根路由。
- 渲染工具箱首页。
- 组装备份贡献者。
- 保存真正属于整个应用的设置。

禁止：

- 定义电费表结构或执行电费计算。
- 直接查询某个功能包的数据库。
- 根据 `tool.id == 'electricity'` 写业务分支。

### 6.2 `core_common`

仅放稳定、无业务含义的代码，例如：

- 金额 `Money` 类型。
- 日期/月份 `YearMonth` 类型。
- `Result`、失败类型和通用校验结果。
- JSON 版本与基础序列化约定。

“因为两个页面长得相似”不能作为放进 Core 的理由。只有两个以上 Feature 确实需要、且语义稳定时才提取。

### 6.3 `core_design_system`

负责：

- Material 3 主题、颜色、字号、圆角和间距 Token。
- 通用按钮、输入框、空状态、错误提示、卡片外观。
- 响应式页面外壳。

不负责：

- `ElectricityMeterCard` 等业务组件。
- 某个工具专用的文案和业务状态。

### 6.4 `core_navigation`

定义工具接入契约以及通用路由类型，不持有具体工具列表：

```dart
abstract interface class ToolModule {
  ToolDescriptor get descriptor;
  List<RouteBase> get routes;
  Future<void> initialize();
}

final class ToolDescriptor {
  const ToolDescriptor({
    required this.id,
    required this.title,
    required this.description,
    required this.entryPath,
    required this.iconKey,
  });

  final String id;
  final String title;
  final String description;
  final String entryPath;
  final String iconKey;
}
```

契约只暴露首页和路由需要的信息，不暴露账单、读数等内部模型。

### 6.5 `core_storage`

负责跨平台存储的基础设施：

- 数据库目录和连接工厂。
- Android/iOS/Web 的平台适配入口。
- 事务、迁移日志和数据库关闭约定。

它不定义任何工具的表。电费表只能存在于 `feature_electricity`。

### 6.6 `core_backup`

定义备份接口、应用级清单和文件选择流程：

```dart
abstract interface class BackupContributor {
  String get toolId;
  int get schemaVersion;
  Future<Map<String, Object?>> exportData();
  Future<void> validateImport(Map<String, Object?> data);
  Future<void> importData(Map<String, Object?> data);
}
```

备份协调层只知道工具 ID、版本和 JSON 数据，不理解各工具字段。

### 6.7 `feature_electricity`

独立拥有：

- 电费计算与尾差分配规则。
- 电费页面和内部导航。
- 电费账单数据库与迁移。
- 历史记录和个人费用趋势。
- 电费数据备份实现。
- 本功能的单元、Widget 和仓库测试。

### 6.8 固定依赖方向

```text
apps/toolbox_app
      │
      ├──────────────→ feature_electricity
      │                         │
      │                         └────────→ core_*
      └──────────────────────────────────→ core_*

feature_*  ✕  feature_*
core_*     ✕  feature_*
```

强制规则：

- `app → feature → core`。
- `core` 永远不能依赖 `feature`。
- `feature` 之间永远不能互相导入。
- 工具之间需要交换数据时，先定义用户可理解的跨工具能力，再由应用壳协调，不能偷读数据库。
- 每个包只通过 `lib/<package_name>.dart` 暴露公共 API；其他包禁止导入其 `lib/src`。

## 7. 功能包内部结构

`feature_electricity` 使用 Feature 内分层：

```text
packages/feature_electricity/
├─ pubspec.yaml
├─ lib/
│  ├─ feature_electricity.dart             唯一公共出口
│  └─ src/
│     ├─ electricity_module.dart            ToolModule 实现
│     ├─ domain/
│     │  ├─ models/
│     │  │  ├─ electricity_bill.dart
│     │  │  └─ meter_share.dart
│     │  ├─ services/
│     │  │  └─ allocation_calculator.dart
│     │  └─ failures/
│     ├─ data/
│     │  ├─ database/
│     │  │  ├─ electricity_database.dart
│     │  │  ├─ tables.dart
│     │  │  └─ migrations.dart
│     │  ├─ repositories/
│     │  │  └─ electricity_repository_impl.dart
│     │  └─ backup/
│     │     └─ electricity_backup_contributor.dart
│     └─ presentation/
│        ├─ calculator/
│        │  ├─ calculator_view.dart
│        │  ├─ calculator_view_model.dart
│        │  └─ calculator_ui_state.dart
│        ├─ history/
│        ├─ trend/
│        └─ widgets/
└─ test/
   ├─ domain/
   ├─ data/
   └─ presentation/
```

分层职责：

```text
View → ViewModel → Repository → Database/Service
                    ↓
            可选的复杂 Domain Service
```

- View 只渲染状态并转发用户动作。
- ViewModel 管理页面状态、校验流程和异步操作。
- Repository 是账单数据的唯一可信入口。
- Database/Service 封装实际平台存储和文件接口。
- 纯计算规则放在 Domain Service 中，不依赖 Flutter Widget 和数据库。
- 简单查询允许 ViewModel 直接调用 Repository；只有复用或复杂业务才增加 Use Case。

## 8. 工具注册与新增工具流程

首版使用显式注册，不引入代码生成式插件发现：

```dart
final toolModules = <ToolModule>[
  ElectricityModule(dependencies: electricityDependencies),
];
```

首页读取 `descriptor` 生成卡片，根路由收集所有 `routes`。

以后新增工具时：

1. 创建 `packages/feature_xxx` Flutter Package。
2. 在包内完成自己的 Domain、Data 和Presentation。
3. 实现 `ToolModule`。
4. 在根 Workspace 中登记包路径。
5. 在 `toolbox_app` 添加一个注册项。
6. 添加依赖边界和功能测试。

除注册表外，不修改已有工具代码。首版不做运行时插件下载，因为个人应用不需要这类复杂度和安全风险。

## 9. 电费分摊业务设计

### 9.1 核心模型

```dart
final class ElectricityBill {
  const ElectricityBill({
    required this.id,
    required this.billingMonth,
    required this.totalAmountCents,
    required this.totalUsage,
    required this.shares,
    required this.createdAt,
    required this.updatedAt,
    this.note,
  });
}

final class MeterShare {
  const MeterShare({
    required this.id,
    required this.label,
    required this.isOwner,
    required this.position,
    required this.previousReading,
    required this.currentReading,
    required this.usage,
    required this.allocatedAmountCents,
  });
}
```

第一条记录固定代表本人，但逻辑使用稳定的 `isOwner` 字段识别，不依赖排序位置。一个账单必须且只能有一条 `isOwner == true`。

### 9.2 计算规则

```text
第 i 户用量     Uᵢ = 本期读数 Cᵢ - 上期读数 Pᵢ
所有户总用量   U  = ΣUᵢ
第 i 户占比        = Uᵢ / U
第 i 户原始金额    = 总电费 × Uᵢ / U
```

示例：

```text
总电费：100.00 元
我的电表：200 - 100 = 100 度
第 2 户：150 - 50 = 100 度
总用量：200 度
结果：各占 50%，各支付 50.00 元
```

金额以分存储并使用最大余数法分配尾差，保证所有分摊金额之和严格等于总金额。读数使用定点十进制或规范化字符串，不使用 `double` 作为持久化精确值。

### 9.3 校验规则

- 户数至少 1，首版最多 20。
- 总电费不得为负，输入最多两位小数。
- 读数不得为空或为负。
- 本期读数必须大于或等于上期读数。
- 总用量为 0 时不能按比例分摊。
- 单户用量为 0 是合法数据，其金额为 0。
- 删除已有输入的户时必须确认。
- 同一月份首版只保留一条正式记录，覆盖前确认。
- 保存账单和所有明细必须在一个事务中完成。

### 9.4 页面导航

```text
/                           工具箱首页
/tools/electricity          电费工具默认页
/tools/electricity/calc     新建计算
/tools/electricity/history  历史记录
/tools/electricity/trend    我的费用趋势
```

电费工具内部展示“计算 / 记录 / 趋势”，首页不展示这些入口。采用 `go_router` 是为了让 Android 返回栈和 Flutter Web 浏览器历史保持明确一致。

## 10. 本地数据设计

电费工具使用独立数据库，例如 `electricity.db`，主要包含：

### 10.1 `electricity_bill`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | integer | 主键 |
| `billing_month` | text | `YYYY-MM` |
| `total_amount_cents` | integer | 总电费，单位分 |
| `total_usage` | text | 精确十进制规范值 |
| `created_at` | integer | 时间戳 |
| `updated_at` | integer | 时间戳 |
| `note` | text? | 可选备注 |

### 10.2 `electricity_share`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | integer | 主键 |
| `bill_id` | integer | 账单外键，级联删除 |
| `position` | integer | 当时的显示顺序 |
| `label` | text | 户名历史快照 |
| `is_owner` | integer/bool | 是否本人 |
| `previous_reading` | text | 上期读数 |
| `current_reading` | text | 本期读数 |
| `usage` | text | 用量快照 |
| `allocated_amount_cents` | integer | 分摊金额，单位分 |

数据库从版本 1 开始维护明确迁移。正式发布后禁止使用破坏式迁移清空用户数据。

### 10.3 Android 与 Web 的存储策略

业务层只依赖 `ElectricityRepository`：

```dart
abstract interface class ElectricityRepository {
  Stream<List<ElectricityBill>> watchBills();
  Future<ElectricityBill?> findByMonth(YearMonth month);
  Future<void> saveBill(ElectricityBill bill);
  Future<void> deleteBill(String id);
}
```

推荐在工程初始化阶段验证 Drift 的 Android SQLite 与 Web/WASM 存储，并尽量让两端使用同一套表定义：

```text
ElectricityRepository
├─ Android/iOS → 本机 SQLite 数据库
└─ Web          → 浏览器本地数据库适配器
```

需要明确：Chrome 和手机是两个独立运行环境，数据不会自动同步。浏览器数据只是调试数据。

如果 Drift Web 验证增加了不必要的配置成本，允许降级为：

- Android/iOS：`sqflite` + SQLite，作为正式数据实现。
- Web：内存仓库或仅供调试的浏览器存储实现。

这个降级只替换 Data 层，不修改页面、ViewModel 和计算规则。

## 11. 备份、恢复与隐私

- 首版不声明 `INTERNET` 权限；如果某个构建目标的框架配置需要网络能力，应单独审查最终 Manifest。
- 数据仅写入应用私有目录。
- 卸载应用会删除私有数据，设置页必须明确提醒。
- 用户主动选择导出位置，应用不自动上传。
- 应用级备份文件包含格式版本和各工具独立数据段：

```json
{
  "formatVersion": 1,
  "exportedAt": "2026-09-10T12:00:00Z",
  "tools": {
    "electricity": {
      "schemaVersion": 1,
      "data": {}
    }
  }
}
```

- 导入分为“解析 → 全量校验 → 展示摘要 → 用户确认 → 事务写入”。
- 单个 Feature 负责校验和解释自己的数据，`core_backup` 只负责编排。
- 备份字段只能向后兼容演进，未知工具数据默认保留并提示，而不是静默丢弃。

## 12. 状态管理与依赖注入

首版不急于引入大型依赖注入框架：

- ViewModel 使用 `ChangeNotifier` 或 `ValueNotifier` 暴露不可变 UI State。
- Repository 和 Service 通过构造函数传入。
- `app_bootstrap.dart` 是唯一组合根，创建数据库、Repository、Feature Module 和路由。
- Widget 不通过全局单例直接查找数据库。
- 数据库对象在应用生命周期内复用，并在需要时统一关闭。

示意：

```dart
final database = ElectricityDatabase(connectionFactory.open('electricity'));
final repository = ElectricityRepositoryImpl(database);
final module = ElectricityModule(repository: repository);
```

当状态共享显著增加时，再评估 Riverpod 等方案；迁移不能改变 Domain 和 Repository 接口。

## 13. 浏览器、真机与 APK 工作流

### 13.1 Chrome 快速调试

```powershell
flutter run -d chrome
```

适合验证：

- 手机宽度下的布局。
- 表单输入和动态增减户数。
- 计算结果与趋势图。
- 路由和普通交互。
- ViewModel 与仓库接口联动。

F12 手机模式只是调整浏览器视口，不等于 Android 设备。不能仅凭 Web 调试确认 Android 文件权限、系统返回键、SQLite 文件路径或应用后台恢复。

### 13.2 安卓真机调试

```powershell
flutter devices
flutter run -d <device-id>
```

真机必须验证：

- 数据库写入、升级和重启后读取。
- 页面退后台后的草稿状态。
- Android 返回手势。
- 文件导出与导入。
- APK升级安装后历史数据保留。

首版优先用真机，暂不要求安装大型模拟器系统镜像。

### 13.3 构建 APK

```powershell
flutter test
flutter analyze
flutter build apk --release
```

若只给自己的现代 ARM64 手机使用，可以在确认设备架构后构建拆分 APK：

```powershell
flutter build apk --split-per-abi
```

Flutter 的 Android 构建仍通过项目内的 Gradle Wrapper 完成。无需全局安装 Gradle，但必须具备 Android SDK、Build-Tools、Platform-Tools 和兼容 JDK；Maven不能替代 Android Gradle Plugin 构建 APK。

## 14. 测试策略

### 14.1 包级单元测试

`core_common`：

- 金额解析与格式化。
- 月份排序与序列化。

`feature_electricity/domain`：

- 两户各 100 度、总费 100 元，各 50 元。
- 三户金额无法整除时的尾差。
- 0 用量户。
- 总用量为 0。
- 小数读数、超大读数和非法读数。
- 分摊金额总和始终等于总金额。

`feature_electricity/data`：

- 保存账单及明细的事务完整性。
- 按月份倒序查询。
- 本人趋势只选择 `isOwner` 数据。
- 数据库迁移不丢记录。
- 备份导出和恢复往返一致。

### 14.2 Widget 测试

- 户数变化正确增加和删除卡片。
- 我的电表不能删除。
- 非法输入显示明确错误。
- 未完整输入时不能保存。
- 深色模式和窄屏无溢出。
- 历史为空和趋势数据不足时展示空状态。

### 14.3 集成测试

- 新建账单 → 保存 → 历史查看 → 趋势显示。
- 编辑或覆盖同月账单。
- 导出 → 清空测试数据 → 导入 → 数据恢复。
- 旧版数据库升级到新版。

Web 测试主要验证 UI 和业务层；Android 集成测试是本地持久化与文件流程的最终依据。

## 15. 开发阶段规划

### 阶段 0：工具链与工作区

- 安装 Flutter stable、VS Code Flutter 扩展。
- 安装 Android Studio及必要 Android SDK组件。
- 配置 JDK 17、许可证和安卓真机。
- 创建 Pub Workspace 与所有初始 Core/Feature 包。
- 设置静态检查、依赖边界约定和基础测试。
- 验证同一个示例页面可以在 Chrome 和安卓真机运行。
- 验证选定数据库方案在 Android/Web 的最小读写与迁移。

### 阶段 1：工具箱壳与电费计算 MVP

- 实现全局主题和工具箱首页。
- 实现 `ToolModule` 注册与路由聚合。
- 按现有 HTML 原型还原电费页面。
- 实现户数增减、输入校验、精确计算和尾差分配。
- 完成纯 Dart 计算测试和 Widget 测试。

### 阶段 2：历史记录

- 建立电费独立数据库。
- 保存、查看、覆盖和删除账单。
- 自动将上期的本期读数带入下期。
- 验证重启、升级安装和迁移。

### 阶段 3：趋势与备份

- 实现本人最近 12 个月费用趋势。
- 实现应用级 JSON 导出与恢复。
- 补充恢复前校验和覆盖确认。

### 阶段 4：APK交付

- 真机回归测试。
- 创建并离线保存签名密钥。
- 构建 Release APK。
- 保存版本号、构建环境和 SHA-256 校验值。
- 验证从旧 APK覆盖安装不会丢失数据。

### 阶段 5：第二个工具验证架构

- 创建第二个 `feature_*` 包。
- 只通过 `ToolModule` 注册入口和路由。
- 验证不修改电费功能包即可接入。
- 根据真实重复代码决定是否继续提取 Core。

## 16. 当前 Windows 开发环境验证

2026-09-10 实际检查结果：

| 项目 | 检查结果 | 结论 |
|---|---|---|
| Git | 已安装，位于 `E:\Programs\git\Git\cmd\git.exe` | 满足 Flutter SDK与项目管理需要 |
| VS Code | 已安装，命令位于 `E:\Programs\Microsoft VS Code\bin\code.cmd` | 可作为主要 IDE |
| Flutter | 命令不可用 | 需要安装 Flutter stable |
| Dart | 命令不可用 | 随 Flutter SDK提供，无需单独安装 |
| ADB | 命令不可用 | 需要 Android SDK Platform-Tools |
| Android Studio | 常见路径未发现 | 建议安装，用于管理 SDK和设备 |
| Android SDK | 未配置 | 生成 APK前必须安装 |
| 默认 Java | 当前命令行为 Java 8 | 不作为本项目构建 JDK |
| JDK 17 | `E:\Programs\jdk\jdk-17.0.18` 存在 | 可供 Android/Gradle使用 |

### 环境结论

当前电脑可以继续查看和开发现有 Web 原型，但尚不能运行 Flutter 或构建 APK。建议的最省心配置是：

```text
VS Code              日常开发与调试
Flutter SDK          Dart/Flutter 工具链
Chrome               快速 UI 调试
Android Studio       只用于 SDK Manager，需要时创建模拟器
安卓真机             正式数据与系统行为验证
JDK 17               Android Gradle 构建
```

不要求日常使用 Android Studio 写代码，也不要求全局安装 Gradle。

## 17. 初始化完成标准

只有满足以下条件，才进入正式业务开发：

- `flutter doctor -v` 中 Flutter、VS Code 和 Android Toolchain通过。
- Chrome 能运行工作区中的 `toolbox_app`。
- 安卓真机能安装并运行 Debug 版本。
- 根目录只产生一个工作区锁文件。
- `toolbox_app`、`core_*`、`feature_electricity` 能分别测试。
- 依赖方向符合 `app → feature → core`。
- 数据库小样在 Android上完成创建、写入、查询和升级。
- Web 数据适配器完成最小验证或明确采用内存替代。
- `flutter build apk --release` 能生成可安装 APK。

## 18. 方案风险与控制

| 风险 | 影响 | 控制方式 |
|---|---|---|
| 多包结构增加初始文件数量 | 首次配置稍慢 | 只创建必要的 5 个 Core和首个 Feature，不做更细碎拆包 |
| Web 与 Android存储实现不同 | 调试表现可能不完全一致 | Repository接口隔离，Android真机作为持久化验收环境 |
| 第三方数据库插件升级 | 可能影响构建 | 锁定版本、保留迁移测试、初始化时做技术验证 |
| Flutter仍依赖 Gradle生成 APK | 无法完全避开安卓工具链 | 使用 Wrapper和Android Studio SDK Manager，不全局安装 Gradle |
| iOS最终需要 Mac | Windows不能完成发布 | 只预留工程和跨平台代码，真正需要时再配置 Mac/Xcode |
| 过早抽象造成开发负担 | 功能开发变慢 | 模块边界从第一天固定，业务内部抽象按实际复杂度增加 |

## 19. 最终推荐

本项目采用以下基线：

```text
Flutter stable
+ VS Code
+ Pub Workspace 多包工程
+ Material 3
+ go_router
+ MVVM（View / ViewModel / Repository / Service）
+ 构造函数依赖注入
+ 每个工具独立数据库与备份贡献者
+ Chrome 快速调试
+ Android真机验收
+ Gradle Wrapper构建 APK
```

模块化程度与 Kotlin 原生方案保持同一层级，但避免动态插件系统和过度分层。这样首个电费工具不会因为架构而变得笨重，同时第二、第三个工具可以作为独立包接入。

## 20. 官方参考

- Flutter 应用架构指南：<https://docs.flutter.dev/app-architecture/guide>
- Flutter 架构建议：<https://docs.flutter.dev/app-architecture/recommendations>
- Dart Pub Workspace：<https://dart.dev/tools/pub/workspaces>
- Flutter 路由：<https://docs.flutter.dev/ui/navigation>
- Flutter SQLite持久化：<https://docs.flutter.dev/cookbook/persistence/sqlite>
- Flutter VS Code开发：<https://docs.flutter.dev/tools/vs-code>
- Flutter Android环境配置：<https://docs.flutter.dev/platform-integration/android/setup>
- Flutter Android构建与发布：<https://docs.flutter.dev/deployment/android>
- Flutter iOS环境配置：<https://docs.flutter.dev/platform-integration/ios/setup>
