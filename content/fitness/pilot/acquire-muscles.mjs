import {mkdir,writeFile} from 'node:fs/promises';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
const root=dirname(fileURLToPath(import.meta.url));
const headers={'User-Agent':'multi-tools-fitness-pilot'};
async function read(url,json=false){let last;for(let n=0;n<3;n++){try{const r=await fetch(url,{headers,signal:AbortSignal.timeout(25000)});if(!r.ok)throw Error(`${r.status}: ${url}`);return await (json?r.json():r.text())}catch(e){last=e}}throw last}
const commit=await read('https://api.github.com/repos/wger-project/wger/commits/master',true);
const base=`https://raw.githubusercontent.com/wger-project/wger/${commit.sha}/wger/core/static/images/muscles/`;
const dictionary=await read('https://wger.de/api/v2/muscle/?limit=100',true);
await mkdir(join(root,'muscles'),{recursive:true});
await writeFile(join(root,'muscles','dictionary.json'),JSON.stringify(dictionary,null,2));
const assets=[];
for(const [name,path] of [['front.svg','muscular_system_front.svg'],['back.svg','muscular_system_back.svg'],...dictionary.results.map(m=>[`main-${m.id}.svg`,`main/muscle-${m.id}.svg`])]){
  const api=`https://api.github.com/repos/wger-project/wger/contents/wger/core/static/images/muscles/${path}?ref=${commit.sha}`;
  const source=await read(api,true);const body=Buffer.from(source.content,'base64').toString('utf8');if(!body.includes('<svg')||/<script|<foreignObject|onload\s*=/i.test(body))throw Error('Unexpected SVG content');
  await writeFile(join(root,'muscles',name),body);assets.push({file:`muscles/${name}`,source:base+path,sha256:createHash('sha256').update(body).digest('hex'),bytes:Buffer.byteLength(body)});
}
await writeFile(join(root,'muscles','provenance.json'),JSON.stringify({retrievedAt:new Date().toISOString(),sourceCommit:commit.sha,sourcesFile:base+'SOURCES',originals:['https://commons.wikimedia.org/wiki/File:Muscular_system.svg','https://commons.wikimedia.org/wiki/File:Muscular_system-back.svg'],attribution:'Original anatomy by Termininja; wger project adapted SVG layers. Candidate anatomy assets; attribution and derivative chain require final release review.',license:'CC BY-SA 3.0 original anatomy; preserve source references and share-alike derivative obligations.',licenseUrl:'https://creativecommons.org/licenses/by-sa/3.0/',publicationStatus:'pilot-only-pending-review',assets},null,2));
console.log(JSON.stringify({commit:commit.sha,files:assets.length,bytes:assets.reduce((n,a)=>n+a.bytes,0),muscles:dictionary.count}));
