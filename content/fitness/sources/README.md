# 动作来源快照

`free-exercise-db/`保存公开源库固定版本的完整动作JSON、原始Schema、许可和逐图哈希台账。来源的动作数不等于本项目动作数，必须经过250项稳定ID映射后再编辑和入包。

完整原图采集工作副本位于已被Git忽略的`prototype/dist/fitness-acquisition/free-exercise-db/exercises`。这是可重建缓存：版本、远程路径及SHA-256另存在本目录。**不要将缓存作为采用素材的唯一长期存储**；逐项匹配后，将实际采用的原图及署名另存到`content/fitness/production/media/<动作ID>/source/`，再生成发布尺寸文件。缓存清理前应保全已采用素材；不把整个外部源库直接打入APK。

本轮同时在`content/fitness/source-archives/`建立完整ZIP本地备份，位于构建目录之外；原包路径、大小和SHA-256见provenance，ZIP被忽略。它不能替代异地备份，迁移电脑时需要另行携带。

本轮使用Git读取公开源文件，没有运行来源库代码。首次采集可执行：

```powershell
git clone --depth 1 --no-checkout https://github.com/yuhonas/free-exercise-db.git prototype/dist/fitness-acquisition/free-exercise-db
git -C prototype/dist/fitness-acquisition/free-exercise-db checkout HEAD -- dist/exercises.json exercises LICENSE.md schema.json
node content/fitness/scripts/snapshot-free-exercise-db.mjs
```

本次固定版本见`free-exercise-db/provenance.json`。复现该版本时应先检出对应SHA，不随远端main变化覆盖现有台账；脚本拒绝覆盖不同版本的来源记录。不要在已有采集目录上重新运行clone或使用reset清除其他内容。

来源仓库声明Unlicense，保留其原始许可与链接。源库声明、图片来源链、动作变式匹配、中文编辑和教学审校分别记录；没有把仓库许可标签写成所有照片权利链或教学均已审查通过。没有使用RepDB受限图片作生成参考，也没有购买或采用收费动图库。
