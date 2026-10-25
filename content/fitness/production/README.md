# 全量动作生产

用户于2026-10-24确认交互原型验收完成，先确认250个候选全部建设，随后要求将源库876条全部纳入系统。原250项保留为中文覆盖基线，与源库映射后形成统一目录，不将两组数量直接相加。

- `baseline.json`：从03候选清单生成的完整250项初始基线，保留稳定ID、场景、器械原文、用途、计量、制作批次及16项样稿引用。
- `source-intake.json`：876条来源的全量入库队列，逐条保留来源身份、图片引用、缺项、中文及本项目ID待匹配状态；不是可发布目录。
- `progress.json`：后续实际制作时新增的人工进度文件，按ID记录来源匹配、媒体、审核、问题和发布版本；不由基线生成脚本覆盖。
- 后续完整中文教学内容另输出到`content/fitness/release/<内容版本>/`，逐项审核后才能标注专业审校通过。v1.2.0按用户确认先发布保留英文原文的来源库，产品发布与专业审校状态分别保存，见[发布记录](../../../docs/居家健身工具/21-v1.2.0正式发布.md)。

生成基线：

```powershell
node content/fitness/scripts/build-production-ledger.mjs
node content/fitness/scripts/build-source-intake.mjs
```

本目录不是已发布动作库，不直接改变原型中的16条待审样稿。来源快照另放`content/fitness/sources`，采集缓存放已忽略的`prototype/dist/fitness-acquisition`；缓存位于构建目录，不作为唯一长期备份。
