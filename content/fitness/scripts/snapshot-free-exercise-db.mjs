import {readFile, writeFile, mkdir, stat, rename} from 'node:fs/promises';
import {createReadStream} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {dirname, resolve} from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const cacheRelative = 'prototype/dist/fitness-acquisition/free-exercise-db';
const cache = resolve(root, cacheRelative);
const output = resolve(root, 'content/fitness/sources/free-exercise-db');
const repository = 'https://github.com/yuhonas/free-exercise-db';
const git = args => execFileSync('git', ['-C', cache, ...args], {encoding: 'utf8', maxBuffer: 16 * 1024 * 1024});
const sha256 = body => createHash('sha256').update(body).digest('hex');
const revision = git(['rev-parse', 'HEAD']).trim();
if (!/^[a-f0-9]{40}$/.test(revision)) throw Error('来源版本无效');
if (git(['remote', 'get-url', 'origin']).trim() !== repository + '.git') throw Error('采集目录来源不符');
const jsonText = git(['show', `${revision}:dist/exercises.json`]);
const license = git(['show', `${revision}:LICENSE.md`]);
const schema = git(['show', `${revision}:schema.json`]);
const exercises = JSON.parse(jsonText);
const identifiers = new Set();
const images = new Set();
const sourceIssues = [];
for (const exercise of exercises) {
  if (!exercise.id || identifiers.has(exercise.id) || !exercise.name || !Array.isArray(exercise.instructions) || !Array.isArray(exercise.images)) throw Error(`来源动作结构异常：${exercise.id}`);
  identifiers.add(exercise.id);
  if (!exercise.instructions.length) sourceIssues.push({sourceId: exercise.id, issue: 'missing-instructions'});
  if (!exercise.images.length) sourceIssues.push({sourceId: exercise.id, issue: 'missing-images'});
  for (const image of exercise.images) {
    if (typeof image !== 'string' || !/^[A-Za-z0-9_().+/-]+\.(jpg|jpeg|png|webp)$/i.test(image) || image.split('/').includes('..') || image.startsWith('/')) throw Error('来源图片路径异常');
    images.add(image);
  }
}
const tree = new Map(git(['ls-tree', '-r', '-z', revision, 'exercises']).split('\0').filter(Boolean).map(line => {
  const [header, file] = line.split('\t');
  return [file, header.split(' ')[2]];
}));
const inventory = [];
for (const image of images) {
  const relative = 'exercises/' + image;
  const bytes = await readFile(resolve(cache, relative));
  const blob = createHash('sha1').update(`blob ${bytes.length}\0`).update(bytes).digest('hex');
  if (blob !== tree.get(relative)) throw Error(`图片与固定来源版本不符：${image}`);
  const jpeg = bytes[0] === 0xff && bytes[1] === 0xd8;
  const png = bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
  const webp = bytes.subarray(0, 4).toString() === 'RIFF' && bytes.subarray(8, 12).toString() === 'WEBP';
  if (!jpeg && !png && !webp) throw Error(`图片文件签名异常：${image}`);
  inventory.push({path: image, bytes: bytes.length, sha256: sha256(bytes), gitBlob: blob,
    url: `https://raw.githubusercontent.com/yuhonas/free-exercise-db/${revision}/${relative}`,
    cache: `${cacheRelative}/${relative}`, inspection: 'signature-and-source-hash-only'});
}
await mkdir(output, {recursive: true});
let previous;
try { previous = JSON.parse(await readFile(resolve(output, 'provenance.json'), 'utf8')); }
catch (error) { if (error.code !== 'ENOENT') throw error; }
if (previous && previous.revision !== revision) throw Error('已有另一版本快照；请单独建立版本目录，不覆盖旧来源台账');
const archiveRelative = `content/fitness/source-archives/free-exercise-db-${revision}.zip`;
const archive = resolve(root, archiveRelative);
await mkdir(dirname(archive), {recursive: true});
try {
  await stat(archive);
  if (!previous?.sourceArchive) throw Error('发现未登记的同名原包，请核实后另行存放；不覆盖或直接采用');
}
catch (error) {
  if (error.code !== 'ENOENT') throw error;
  const partial = archive + '.partial';
  execFileSync('git', ['-C', cache, 'archive', '--format=zip', `--prefix=free-exercise-db-${revision}/`, `--output=${partial}`, revision, 'dist/exercises.json', 'exercises', 'LICENSE.md', 'schema.json'], {stdio: 'pipe'});
  await rename(partial, archive);
}
const archiveDigest = createHash('sha256');
for await (const chunk of createReadStream(archive)) archiveDigest.update(chunk);
const archiveHash = archiveDigest.digest('hex');
if (previous?.sourceArchive && previous.sourceArchive.sha256 !== archiveHash) throw Error('本地原包备份与已记录哈希不符，不覆盖原台账');
const provenance = {
  source: 'free-exercise-db', repository, revision,
  acquiredAt: previous?.acquiredAt ?? new Date().toISOString(),
  licenseDeclaredBySource: 'Unlicense', licenseFile: 'LICENSE.source.md',
  licenseUrl: `${repository}/blob/${revision}/LICENSE.md`,
  exerciseCount: exercises.length, imageReferences: exercises.reduce((count, exercise) => count + exercise.images.length, 0),
  uniqueImagesDownloaded: inventory.length, imageBytes: inventory.reduce((count, image) => count + image.bytes, 0),
  sourceIssues,
  jsonSha256: sha256(jsonText), licenseSha256: sha256(license),
  localSourceCache: cacheRelative,
  sourceArchive: {path: archiveRelative, bytes: (await stat(archive)).size, sha256: archiveHash, retention: 'local-backup-outside-build-cache-git-ignored'},
  status: 'source-acquired-not-matched-or-approved',
  note: '来源876条已全部进入处理范围，原250项覆盖仍需映射；图片校验只确认文件签名和固定来源完整性，不代表可解码、教学/解剖或逐图权利审核通过。完整原包另存source-archives，正式采用前另存对应原图至内容生产目录。',
};
await writeFile(resolve(output, 'exercises.json'), jsonText);
await writeFile(resolve(output, 'schema.source.json'), schema);
await writeFile(resolve(output, 'LICENSE.source.md'), license);
await writeFile(resolve(output, 'images.json'), JSON.stringify(inventory, null, 2) + '\n');
await writeFile(resolve(output, 'provenance.json'), JSON.stringify(provenance, null, 2) + '\n');
console.log(JSON.stringify({revision, exercises: exercises.length, images: inventory.length, imageBytes: provenance.imageBytes}));
