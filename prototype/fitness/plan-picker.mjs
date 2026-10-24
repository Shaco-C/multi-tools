import {clone,addItemToDay,equipmentMatches,integer} from './model.mjs';

export const PAGE_SIZE=20;
export const groupKey=item=>item.purpose==='strength'?`part:${item.primaryPart}`:`purpose:${item.purpose}`;
export const activeItems=day=>day.items.filter(item=>item.status==='active');
export function groups(day){
  const items=activeItems(day),keys=[...new Set([...day.parts.map(part=>`part:${part}`),...items.map(groupKey)])];
  return keys.map(key=>({key,items:items.filter(item=>groupKey(item)===key)}));
}
export function scopes(day){
  return [...groups(day).map(group=>group.key).filter(key=>key.startsWith('part:')),'purpose:warmup','purpose:cardio','purpose:stretch'];
}
function gearMatches(exercise,gear){
  if(gear==='all')return true;
  const requirements=exercise.equipment;
  if(gear==='floor')return requirements.some(group=>group.length&&group.every(key=>key==='floor'));
  const keys=gear==='dumbbell'?['dumbbellPair','dumbbellSingle']:['longBand','loopBand'];
  return requirements.some(group=>group.some(key=>keys.includes(key)));
}
export function candidates(exercises,owned,{scope,query='',gear='all',mine=true,source='all',limit=PAGE_SIZE}){
  const search=query.trim().toLocaleLowerCase(),ids=new Set();
  const matched=exercises.filter(exercise=>{
    if(exercise.archived||groupKey(exercise)!==scope||ids.has(exercise.id))return false;
    if(mine&&!equipmentMatches(exercise,owned))return false;
    if(!gearMatches(exercise,gear))return false;
    if(source==='custom'&&!exercise.custom||source==='builtin'&&exercise.custom)return false;
    if(!`${exercise.name} ${exercise.english??''} ${(exercise.aliases??[]).join(' ')}`.toLocaleLowerCase().includes(search))return false;
    ids.add(exercise.id);return true;
  });
  const take=Math.max(PAGE_SIZE,Number(limit)||PAGE_SIZE);
  return {total:matched.length,items:matched.slice(0,take),hasMore:matched.length>take};
}
export function toggleItem(day,exercise){
  const next=clone(day),existing=next.items.find(item=>item.exerciseId===exercise.id&&item.status==='active');
  if(existing)existing.status='stopped';else addItemToDay(next,exercise,'','');
  // 部位是用户的安排意图，取消最后一个动作也保留部位和训练日。
  return next;
}
export function batchTargets(day,{scope='all',overwrite=false,sets='',reps='',seconds=''},events=[]){
  const values={sets,reps,seconds};
  for(const [key,value] of Object.entries(values))values[key]=String(value??'').trim()===''?null:integer(value,key==='sets'?99:key==='seconds'?3600:999,key==='sets'?'组/段数':'每组目标');
  if(Object.values(values).every(value=>value===null))throw Error('请至少填写一项目标');
  const next=clone(day),items=activeItems(next).filter(item=>scope==='all'||groupKey(item)===scope);
  if(!items.length)throw Error('当前范围还没有已选动作');
  for(const item of items){
    if(values.sets!==null&&(overwrite||String(item.targetSets??'').trim()==='')){
      if(values.sets<events.filter(event=>event.status==='valid'&&event.itemId===item.id).length)throw Error('组数不能小于已完成组数');
      item.targetSets=values.sets;
    }
    if(values[item.unit]!==null&&(overwrite||String(item.targetValue??'').trim()===''))item.targetValue=values[item.unit];
  }
  return next;
}
export function reorder(day,id,direction){
  const next=clone(day),item=next.items.find(item=>item.id===id);
  if(!item||item.status!=='active')return next;
  const indices=next.items.map((entry,index)=>entry.status==='active'&&groupKey(entry)===groupKey(item)?index:-1).filter(index=>index>=0);
  const from=indices.indexOf(next.items.indexOf(item)),to=from+Number(direction);
  if(to>=0&&to<indices.length)[next.items[indices[from]],next.items[indices[to]]]=[next.items[indices[to]],next.items[indices[from]]];
  return next;
}
