# Room schema snapshots

首次在具备 Android SDK 的电脑上运行 KSP/Gradle 后，Room 会把版本化 JSON schema 生成到此目录。

这些 schema 文件应随源码保存，后续将用于编写和验证数据库迁移；不要用破坏式迁移代替正式迁移。
