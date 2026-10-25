# 原始来源包的本地备份

本目录保存固定来源版本的完整ZIP，独立于可清理的`prototype/dist`采集工作副本。ZIP包含源动作JSON、全部源图片、许可和Schema，供恢复缓存及后续素材匹配使用。

原包体积较大且尚未经过本项目逐图审核，因此仅将原包路径、大小、SHA-256及固定来源版本记录在`../sources/free-exercise-db/provenance.json`，ZIP被本目录`.gitignore`忽略，不直接进入应用或公开Git提交。

此处是当前工作区的本地长期副本，不是异地备份。迁移电脑或清理原始资料前，另行保留ZIP并按台账核对哈希。正式采用的原图及改作记录仍需进入对应生产目录，审核后输出发布内容包。

## 缺失时恢复

在仓库根目录运行 `./content/fitness/scripts/restore-source-archive.ps1`。脚本从固定的来源提交下载到被忽略的 `prototype/dist/fitness-acquisition`，按最初的 `git archive` 文件范围重建 ZIP，大小与 SHA-256 均匹配后才安装到本目录。已有且校验正确的 ZIP 会直接复用；已有损坏文件不会自动覆盖。

不能用 GitHub 页面默认的全仓 ZIP 替代，它与台账记录的选择性归档不同。此脚本是开发者显式执行的联网恢复步骤；Gradle 构建和 Android 应用本身仍使用本地素材。下载缓存和原包均保存在项目所在磁盘。
