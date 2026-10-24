import {readFile, writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {dirname, resolve} from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const folder = resolve(root, 'content/fitness/sources/free-exercise-db');
const readJson = async file => JSON.parse(await readFile(resolve(folder, file), 'utf8'));
const sourceText = await readFile(resolve(folder, 'exercises.json'), 'utf8');
const source = JSON.parse(sourceText);
const provenance = await readJson('provenance.json');
const images = await readJson('images.json');
if (createHash('sha256').update(sourceText).digest('hex') !== provenance.jsonSha256) throw Error('来源快照哈希不符');
if (source.length !== provenance.exerciseCount || new Set(source.map(exercise => exercise.id)).size !== source.length) throw Error('来源数量或身份异常');
const downloaded = new Set(images.map(image => image.path));
const exercises = source.map(exercise => {
  for (const image of exercise.images) if (!downloaded.has(image)) throw Error(`缺少已下载图片台账：${image}`);
  return {
    sourceRecordId: `free-exercise-db:${exercise.id}`,
    sourceId: exercise.id, sourceName: exercise.name, sourceRevision: provenance.revision,
    sourceCategory: exercise.category, sourceEquipment: exercise.equipment,
    instructionCount: exercise.instructions.length, images: exercise.images,
    issues: provenance.sourceIssues.filter(issue => issue.sourceId === exercise.id).map(issue => issue.issue),
    canonicalId: null, chineseName: null,
    production: {mapping: 'pending', chineseEditing: 'pending', measurement: 'pending', equipment: 'pending', muscleMap: 'pending', mediaReview: 'pending', rightsReview: 'pending', teachingReview: 'pending', packaging: 'pending'},
    publishable: false,
  };
});
const byCategory = {};
for (const exercise of exercises) byCategory[exercise.sourceCategory ?? 'unknown'] = (byCategory[exercise.sourceCategory ?? 'unknown'] ?? 0) + 1;
const result = {
  schemaVersion: 1, scopeConfirmedOn: '2026-10-24', scope: 'all-source-records-plus-original-coverage',
  sourceCount: exercises.length, requiredChineseCoverageBaselineCount: 250,
  finalCanonicalCount: null, sourceRevision: provenance.revision,
  counts: {imageReferences: provenance.imageReferences, imagesDownloaded: images.length, missingInstructions: exercises.filter(exercise => !exercise.instructionCount).length, missingImages: exercises.filter(exercise => !exercise.images.length).length, professionallyReviewed: 0, publishable: 0, bySourceCategory: byCategory},
  exercises,
};
await writeFile(resolve(root, 'content/fitness/production/source-intake.json'), JSON.stringify(result, null, 2) + '\n');
const report = `# 源库全量采集与入库台账\n\n日期：2026-10-24。用户已验收原型，并要求将源库全部876条纳入系统。此处“纳入”表示全部进入身份映射、中文编辑、教学素材和发布检查队列；源记录不是自动发布的正式动作。\n\n## 本轮实际取得\n\n| 内容 | 实测 |\n| --- | --- |\n| 来源 | [free-exercise-db](${provenance.repository}) |\n| 固定提交 | [${provenance.revision.slice(0, 12)}](${provenance.repository}/tree/${provenance.revision}) |\n| 完整动作JSON | ${source.length}条，保留源说明、肌群、器械、图片路径等字段 |\n| 原图 | ${images.length}张，合计${provenance.imageBytes.toLocaleString('en-US')}字节（约${(provenance.imageBytes / 1024 / 1024).toFixed(2)} MiB） |\n| 下载完整性 | 全部图片与固定版本Git blob一致，并保存SHA-256及文件签名检查；未以此代替解码/画面审核 |\n| 源说明缺项 | ${result.counts.missingInstructions}条 |\n| 源图片缺项 | ${result.counts.missingImages}条 |\n| 中文编辑/专业审校/正式入包 | 尚未完成；原16项仍为待审样稿 |\n\n## 存放位置\n\n- 完整JSON、许可、Schema与来源记录：\`content/fitness/sources/free-exercise-db/\`。\n- ${images.length}张原图：\`prototype/dist/fitness-acquisition/free-exercise-db/exercises/\`，已忽略的可重建采集缓存；按来源目录保存，图片位置由JSON给出。采用前另存对应原图到内容生产目录，缓存不能作为唯一长期保存位置。\n- ${source.length}条完整队列：\`content/fitness/production/source-intake.json\`。\n- 原250项中文覆盖和稳定ID：[16-250项动作全量生产基线](16-250项动作全量生产基线.md)。\n- 后续步骤：[15-全量动作建设与Android开发计划](15-全量动作建设与Android开发计划.md)。\n\n## 来源用途分布\n\n此表是来源原始category，不直接作为本项目用途/统计归属。\n\n| 来源category | 条数 |\n| --- | ---: |\n${Object.entries(byCategory).map(([category, count]) => `| ${category} | ${count} |`).join('\n')}\n\n## 已发现的源缺项\n\n| 来源ID | 缺项 | 处理 |\n| --- | --- | --- |\n${provenance.sourceIssues.map(issue => `| ${issue.sourceId} | ${issue.issue === 'missing-images' ? '无图片' : '无步骤说明'} | 另行补齐；保留源记录，不自动删除或标为完整 |`).join('\n')}\n\n## 统一入库规则\n\n1. 876条逐条进入队列，原250项逐条做覆盖映射；不能直接相加为1126个正式动作，也不自动删除源库没有的原需求动作。\n2. 已有EX-ID优先保留。来源ID只作外部身份；同变式合并别名与出处，不同器械、支撑或计量语义不能按相似名称硬合并。新增正式ID在主目录映射时统一分配，保留源ID映射关系。\n3. 中文名、简介、步骤、器械条件、次/秒/每侧、肌群和用途逐条确定；不把英文列表或机器直译当作中文完整教学。\n4. 缺步骤/缺图先补齐；原两帧不覆盖完整过程时补脚本和帧，静态保持按进入/保持/退出组织。没有GIF不阻止静态合格条目发布。\n5. 内部测试可使用明确标记的待审内容；正式包逐项通过检查。最终唯一动作数在映射去重后计算，任何合并/替换/缺口都记录，不通过重复或空白凑数量。\n6. 来源声明Unlicense，原许可随快照保留；逐图来源链与教学审核分开进行。本轮未采购商业库，也未将受限图片作AI生成参考。\n`;
const archiveNote = provenance.sourceArchive ? `\n\n## 原始资料本地备份\n\n已在构建缓存之外保存[完整来源ZIP](../../${provenance.sourceArchive.path})，${provenance.sourceArchive.bytes.toLocaleString('en-US')}字节；SHA-256：\`${provenance.sourceArchive.sha256}\`。ZIP被Git忽略，迁移电脑需另行携带；这是一份本地副本，不冒充异地备份。\n` : '';
await writeFile(resolve(root, 'docs/居家健身工具/17-源库全量采集与入库台账.md'), report + archiveNote);
console.log(JSON.stringify({sourceCount: result.sourceCount, ...result.counts}));
