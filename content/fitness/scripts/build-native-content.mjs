import {readFile, writeFile, mkdir} from 'node:fs/promises';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {CATALOG} from '../pilot/catalog.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const load = async file => JSON.parse(await readFile(resolve(root, file), 'utf8'));
const source = await load('content/fitness/sources/free-exercise-db/exercises.json');
const provenance = await load('content/fitness/sources/free-exercise-db/provenance.json');
const mappings = (await load('content/fitness/production/pilot-source-mapping.json')).mappings;
const sourcesById = new Map(source.map(exercise => [exercise.id, exercise]));
const parts = {chest:'Chest', back:'Back', shoulder:'Shoulder', biceps:'Biceps', triceps:'Triceps', forearm:'Forearm', core:'Core', glutes:'Glutes', legs:'Legs', calves:'Calves', full:'Full', neck:'Neck'};
const purposes = {strength:'Strength', cardio:'Cardio', warmup:'Warmup', stretch:'Stretch'};
const exercises = CATALOG.map(exercise => ({
  id:exercise.id, name:exercise.name, english:exercise.english, aliases:exercise.aliases,
  purpose:purposes[exercise.purpose], primaryPart:parts[exercise.primaryPart]??null,
  secondaryParts:exercise.secondaryParts.map(part => parts[part]), unit:exercise.unit==='seconds'?'Seconds':'Reps',
  side:exercise.side==='each'?'Each':'Total', equipment:exercise.equipment,
  introduction:exercise.intro, steps:exercise.steps, cues:exercise.cues,
  images:[0,1].map(panel => ({path:`media/${exercise.id}.png`,panel,panels:2})),
  contentRevision:exercise.contentRevision, references:exercise.references, personal:false, archived:false, reviewed:false,
}));
for (const [id, sourceId] of Object.entries(mappings)) {
  if (!exercises.some(exercise => exercise.id===id)||!sourcesById.has(sourceId)) throw Error(`动作映射失效：${id}/${sourceId}`);
}
const reverse = new Map(Object.entries(mappings).map(([id, sourceId]) => [sourceId, id]));
const sourceRecords = source.map(exercise => ({
  id:exercise.id, name:exercise.name, primaryMuscles:exercise.primaryMuscles, secondaryMuscles:exercise.secondaryMuscles,
  equipment:exercise.equipment, category:exercise.category, level:exercise.level,
  instructions:exercise.instructions, images:exercise.images.map(path=>({path:`source-media/${path}`,panel:0,panels:1})),
  linkedExerciseId:reverse.get(exercise.id)??null,
}));
const document = {version:`development-${provenance.revision.slice(0,12)}`, sourceCount:source.length,
  reviewedCount:0, sourceRevision:provenance.revision, exercises, sourceRecords};
const directory = resolve(root,'content/fitness/android');
await mkdir(directory,{recursive:true});
await writeFile(resolve(directory,'catalog.json'),JSON.stringify(document,null,2)+'\n');
await writeFile(resolve(directory,'content.properties'),[
  '# Generated development content; this file does not approve teaching content.',
  `version=${document.version}`, `sourceCount=${source.length}`, `exerciseCount=${exercises.length}`, 'reviewedCount=0',
  'sourceProcessedCount=0', 'releaseApproved=false', `sourceRevision=${provenance.revision}`,
  `sourceArchive=${provenance.sourceArchive.path}`, `sourceArchiveSha256=${provenance.sourceArchive.sha256}`, '',
].join('\n'));
console.log(JSON.stringify({exercises:exercises.length,sourceRecords:sourceRecords.length,proposedMappings:reverse.size,releaseApproved:false}));
