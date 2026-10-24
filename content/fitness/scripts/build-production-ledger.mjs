import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {dirname, resolve} from 'node:path';
import {CATALOG} from '../pilot/catalog.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const sourceFile = 'docs/居家健身工具/03-首批动作候选清单.md';
const output = resolve(root, 'content/fitness/production');
const categories = {
  CHE: ['chest', '胸部'], BAC: ['back', '背部'], SHO: ['shoulder', '肩部'],
  BIC: ['biceps', '肱二头肌'], TRI: ['triceps', '肱三头肌'], FOR: ['forearm', '前臂与握力'],
  COR: ['core', '腹部与核心'], GLU: ['glutes', '臀部'], LEG: ['legs', '腿部'],
  CAL: ['calves', '小腿与胫骨前侧'], AUX: ['supplement', '全身与既有补充'],
  MOB: ['mobility', '活动与拉伸'], AER: ['cardio', '有氧与体能'],
};
const purposeNames = {strength: '力量/稳定性', warmup: '活动/热身', stretch: '拉伸', cardio: '有氧/体能'};

function defaultPurpose(code, number) {
  if (code === 'AER') return 'cardio';
  if (code === 'MOB') return [1, 2, 7, 15, 16].includes(number) ? 'warmup' : 'stretch';
  if (code === 'AUX') return [5, 12, 13].includes(number) ? 'strength' : number >= 6 && number <= 10 ? 'warmup' : 'cardio';
  return 'strength';
}

const source = await readFile(resolve(root, sourceFile), 'utf8');
const exercises = source.split(/\r?\n/).filter(line => /^\| EX-/.test(line)).map(line => {
  const [, id, name, scene, equipmentText, measurement] = line.split('|').map(value => value.trim());
  const [, code, ordinal] = /^EX-([A-Z]{3})-(\d{3})$/.exec(id) ?? [];
  if (!categories[code] || !['H', 'A', 'G'].includes(scene) || !['次', '秒', '每侧次', '每侧秒'].includes(measurement)) {
    throw Error(`候选字段无效：${id}`);
  }
  const sample = CATALOG.find(exercise => exercise.id === id);
  return {
    id, name, navigationCategory: categories[code][0], scene, equipmentText,
    purpose: defaultPurpose(code, Number(ordinal)),
    unit: measurement.includes('秒') ? 'seconds' : 'reps',
    side: measurement.startsWith('每侧') ? 'each' : 'total',
    proposedPrimaryPart: code === 'AUX' || code === 'MOB' || code === 'AER' ? null : categories[code][0],
    primaryPartReview: 'pending',
    batch: `CONTENT-${code}`,
    pilotReference: sample ? {catalog: 'content/fitness/pilot/catalog.mjs', image: `content/fitness/pilot/media/${id}.png`} : null,
    sourceMapping: {status: 'unmapped', sourceId: null, evidence: null},
    progress: {
      identity: 'candidate', chineseText: sample ? 'pilot_draft' : 'pending',
      poseMedia: sample ? 'pilot_draft' : 'pending', muscleMapping: 'pending',
      equipmentReview: 'pending', measurementReview: 'pending',
      rightsReview: 'pending', teachingReview: 'pending', packaging: 'pending',
    },
    publishable: false,
  };
});

if (exercises.length !== 250 || new Set(exercises.map(exercise => exercise.id)).size !== 250 || new Set(exercises.map(exercise => exercise.name)).size !== 250) {
  throw Error('全量基线应为250个不同ID和名称，不允许静默增减');
}
const totals = field => Object.fromEntries([...new Set(exercises.map(exercise => exercise[field]))].map(value => [value, exercises.filter(exercise => exercise[field] === value).length]));
const expectedPurposes = {strength: 210, warmup: 10, stretch: 11, cardio: 19};
for (const [purpose, count] of Object.entries(expectedPurposes)) if (totals('purpose')[purpose] !== count) throw Error(`用途基线不一致：${purpose}`);
const ledger = {
  schemaVersion: 1, scopeConfirmedOn: '2026-10-24', coverageBaselineCount: 250, sourceFile,
  role: 'original-chinese-coverage-baseline', stage: 'full-production-planned', interactionAcceptance: 'accepted-by-user',
  counts: {candidates: exercises.length, pilotDrafts: exercises.filter(exercise => exercise.pilotReference).length, professionallyReviewed: 0, publishable: 0, byScene: totals('scene'), byPurpose: totals('purpose')},
  exercises,
};
await mkdir(output, {recursive: true});
// 初始基线保留为生成文件；实际制作进度另存progress.json，避免重建时覆盖人工审核结果。
await writeFile(resolve(output, 'baseline.json'), JSON.stringify(ledger, null, 2) + '\n');
const rows = exercises.map(exercise => `| ${exercise.id} | ${exercise.name} | ${exercise.scene} | ${purposeNames[exercise.purpose]} | ${exercise.side === 'each' ? '每侧' : ''}${exercise.unit === 'seconds' ? '秒' : '次'} | ${exercise.pilotReference ? '已有待审样稿' : '待制作'} | 待审 |`);
const report = `# 250项动作覆盖与生产基线\n\n用户于2026-10-24确认原型验收完成，先确认250项全量建设，随后进一步要求将公开源库876条全部纳入系统。此表保留原250项中文覆盖基线；当前全量范围见[17-源库全量采集与入库台账](17-源库全量采集与入库台账.md)。250与876需要映射去重，不能直接相加或将此表当成最终上限。\n\n由\`content/fitness/scripts/build-production-ledger.mjs\`从03生成；人工进度应记录到\`content/fitness/production/progress.json\`，不直接改生成文件。开发步骤见[15-全量动作建设与Android开发计划](15-全量动作建设与Android开发计划.md)。\n\n## 分批清单\n\n批次是制作顺序。保留全部250项覆盖意图，最终与876条源记录统一映射。\n\n| 批次 | 分类 | 数量 | 已有待审样稿 | 专业审核通过 |\n| --- | --- | ---: | ---: | ---: |\n${Object.entries(categories).map(([code, [category, label]]) => `| CONTENT-${code} | ${label} | ${exercises.filter(exercise => exercise.navigationCategory === category).length} | ${exercises.filter(exercise => exercise.navigationCategory === category && exercise.pilotReference).length} | 0 |`).join('\n')}\n\n## 逐项基线\n\n| ID | 动作 | 场景 | 默认用途 | 计量 | 现有内容 | 发布审核 |\n| --- | --- | --- | --- | --- | --- | --- |\n${rows.join('\n')}\n\n合计250项；H122/A61/G67；力量210/活动热身10/拉伸11/有氧19。16项已有中文与生成姿势样稿，专业审核通过0项。250项均需完成身份、器械、计量、教学、肌群、许可与离线检查。\n`;
await writeFile(resolve(root, 'docs/居家健身工具/16-250项动作全量生产基线.md'), report);
console.log(JSON.stringify(ledger.counts));
