import * as M from './model.mjs';
import * as P from './plan-picker.mjs';

export const MAX_BACKUP_BYTES=20*1024*1024;
export const MAX_IMAGE_BYTES=3*1024*1024;
export const byteLength=value=>new TextEncoder().encode(value).byteLength;
export const defaultName=(start,end=start)=>`${start.slice(5).replace('-','.')} ${start===end?'单日训练':`— ${end.slice(5).replace('-','.')} 训练计划`}`;

export function remaining(s,date=s.demoToday){
  const counts=new Map();for(const event of M.activeEvents(s,{start:date,end:date}))counts.set(event.itemId,(counts.get(event.itemId)??0)+1);
  return M.schedule(s,date).plans.flatMap(({plan,day})=>day.kind==='training'?day.items.filter(item=>item.status==='active').map(item=>({planId:plan.id,planName:plan.name,date,item,count:counts.get(item.id)??0})):[]).filter(row=>row.count<row.item.targetSets);
}
export function todayAction(s,selected=s.demoToday,preferred=''){
  if(selected!==s.demoToday)return {kind:'today',label:'回到今日训练'};
  const pending=remaining(s),row=pending.find(row=>row.item.id===preferred)||pending.find(row=>row.item.id===s.preferences.lastTrainingItemId)||pending[0];
  if(row)return {kind:'continue',label:`继续：${row.item.name} ${row.count}/${row.item.targetSets}${row.item.purpose==='strength'?'组':'段'}`,row};
  const current=M.schedule(s,s.demoToday);
  if(current.actual.length||current.items.length)return {kind:'records',label:'查看今日记录'};
  return current.kind==='rest'?{kind:'calendar',label:'查看日程'}:{kind:'arrange',label:'安排今天'};
}
export function draftRoute(draft,request){
  if(!draft)return 'new';
  if(request.planId&&draft.editing&&draft.plan.id===request.planId)return 'resume';
  if(!request.planId&&!draft.editing&&request.source==='day'&&draft.entrySource==='day'&&draft.plan.start===request.date&&draft.plan.end===request.date)return 'resume';
  return 'protect';
}
export function inspectBackup(text){
  if(byteLength(text)>MAX_BACKUP_BYTES)throw Error('备份超过20 MiB，超出当前原型的完整恢复范围；可导出原始资料另行保留');
  const result=M.inspectBackup(text),state=result.state;
  checkStorageState(state);
  if(!M.calendarDay(state.demoToday))throw Error('备份中的演示日期无效');
  if(state.favorites!==undefined&&!Array.isArray(state.favorites)||state.templates!==undefined&&!Array.isArray(state.templates))throw Error('收藏或模板结构损坏');
  if((state.favorites??[]).some(id=>typeof id!=='string'))throw Error('收藏身份损坏');
  const customIds=new Set(M.CATALOG.map(exercise=>exercise.id));
  for(const exercise of state.customExercises){if(typeof exercise.id!=='string'||customIds.has(exercise.id)||typeof exercise.name!=='string'||!exercise.name.trim()||!Array.isArray(exercise.aliases)||!Array.isArray(exercise.secondaryParts)||!Array.isArray(exercise.steps)||!Array.isArray(exercise.cues)||!Array.isArray(exercise.references)||!Array.isArray(exercise.equipment)||!exercise.equipment.length||exercise.equipment.some(group=>!Array.isArray(group)||!group.length)||!['strength','cardio','warmup','stretch'].includes(exercise.purpose)||!['reps','seconds'].includes(exercise.unit)||!['total','each'].includes(exercise.side))throw Error('自建动作结构或身份损坏');customIds.add(exercise.id);if(exercise.image){const body=exercise.image.split(',')[1]??'',padding=body.endsWith('==')?2:body.endsWith('=')?1:0;if(body.length*3/4-padding>MAX_IMAGE_BYTES)throw Error('单张个人图片超过3 MiB，请缩小图片后恢复')}}
  const templateIds=new Set();for(const template of state.templates??[]){if(typeof template.id!=='string'||templateIds.has(template.id)||typeof template.name!=='string'||!template.name.trim()||!Array.isArray(template.parts)||!Array.isArray(template.items)||!template.items.length)throw Error('个人模板结构损坏');templateIds.add(template.id);for(const item of template.items){if(!['reps','seconds'].includes(item.unit)||!['total','each'].includes(item.side))throw Error('模板计量损坏');M.integer(item.targetSets,99);M.integer(item.targetValue,item.unit==='seconds'?3600:999)}}
  const itemIds=new Set();for(const plan of state.plans){M.validatePlan(plan,state);for(const day of plan.days){if(!M.calendarDay(day.date)||!Array.isArray(day.parts))throw Error('计划日期或部位结构损坏');for(const item of day.items){if(itemIds.has(item.id))throw Error('动作安排身份重复');itemIds.add(item.id)}}}
  // 草稿允许空名称、未填目标及正在修改的日期；已生成的日程仍须结构完整。
  if(state.editorDraft){const editor=state.editorDraft,p=editor.plan;if(!p||typeof p.name!=='string'||typeof p.start!=='string'||typeof p.end!=='string'||!Array.isArray(p.days)||!p.days.some(day=>day.date===editor.date))throw Error('计划草稿结构损坏');for(const day of p.days)if(!M.calendarDay(day.date)||!Array.isArray(day.parts)||!Array.isArray(day.items))throw Error('计划草稿日程损坏')}
  return {...result,bytes:byteLength(text)};
}
export function prepareBackup(s){const text=JSON.stringify({format:'fitness-prototype-json',version:M.VERSION,exportedAt:new Date().toISOString(),state:s}),inspection=inspectBackup(text);return {text,...inspection}}
export function checkStorageState(s){const text=JSON.stringify(s);if(byteLength(text)+1024>MAX_BACKUP_BYTES)throw Error('资料已接近20 MiB备份限额，请先备份并减少个人图片；本次变更未保存');return text}

export function planIssues(plan,s){
  const issues=[],add=(message,day='',itemId='',field='',scope='')=>issues.push({message,day,itemId,field,scope});
  if(!plan.name?.trim()||plan.name.trim().length>60)add('填写1—60字符的计划名称','','','name');
  try{M.dates(plan.start,plan.end)}catch(error){add(error.message,'','','dates');return issues}
  let count=0;
  for(const day of plan.days){
    if(day.kind!=='training')continue;
    const items=P.activeItems(day);count+=items.length;
    if(!items.length&&!day.parts.length)add(`${day.date}：选择动作，或将这天改为休息/未安排`,day.date,'','day');
    for(const part of day.parts)if(!items.some(item=>item.purpose==='strength'&&item.primaryPart===part))add(`${day.date}：${M.PARTS[part]}尚未选择主动作`,day.date,'','pick',`part:${part}`);
    for(const item of items){
      try{M.integer(item.targetSets,99)}catch{add(`${day.date} · ${item.name}：填写有效的组/段数`,day.date,item.id,'sets')}
      try{M.integer(item.targetValue,item.unit==='seconds'?3600:999)}catch{add(`${day.date} · ${item.name}：填写${item.side==='each'?'每侧':''}${item.unit==='seconds'?'秒数':'次数'}`,day.date,item.id,'value')}
      if(Number(item.targetSets)<M.itemEvents(s,item.id).length)add(`${day.date} · ${item.name}：组数不能少于已完成记录`,day.date,item.id,'sets');
    }
  }
  if(!count&&!s.events.some(event=>event.planId===plan.id)&&!issues.length)add('至少安排一个有完整目标的训练日','','','day');
  return issues;
}

export function copyDraftDays(plan,sourceDate,targets,s){
  const next=M.clone(plan),source=next.days.find(day=>day.date===sourceDate);
  if(!source||!P.activeItems(source).length)throw Error('来源日期没有可复制的动作');
  const dates=[...new Set(targets)];if(!dates.length)throw Error('请选择目标日期');
  for(const date of dates){if(date===sourceDate||!next.days.some(day=>day.date===date))throw Error('请选择计划内其他日期');if(s.events.some(event=>event.planId===plan.id&&event.day===date))throw Error(`${date}有完成或撤销历史，不能覆盖`)}
  for(const date of dates){const index=next.days.findIndex(day=>day.date===date);next.days[index]={...M.clone(source),date,items:P.activeItems(source).map(item=>({...M.clone(item),id:M.uid()}))}}
  return next;
}
export function batchDayParts(plan,targets,{kind,parts=[],overwrite=false},s){
  const next=M.clone(plan),dates=[...new Set(targets)];if(!dates.length)throw Error('请选择目标日期');if(!['training','rest','unplanned'].includes(kind))throw Error('请选择有效安排状态');
  for(const date of dates){const day=next.days.find(day=>day.date===date);if(!day)throw Error('日期不在计划内');if(s.events.some(event=>event.planId===plan.id&&event.day===date))throw Error(`${date}有历史记录，不能批量覆盖`);if(!overwrite&&(day.kind!=='unplanned'||P.activeItems(day).length))throw Error(`${date}已有安排；请取消该日或明确选择覆盖`)}
  for(const date of dates){const day=next.days.find(day=>day.date===date);day.kind=kind;day.parts=kind==='training'?[...new Set(parts)]:[];day.items.forEach(item=>item.status='stopped')}
  return next;
}
export function backfillBatch(s,{planId,day,itemId,count,batchId=M.uid()}){
  if(s.requests.includes(batchId))return M.clone(s);
  count=M.integer(count,99,'补记组/段数');if(day>=s.demoToday)throw Error('批量补记仅用于过去日期');
  const {plan,date,item}=M.locate(s,planId,day,itemId);if(plan.status!=='active'||date.kind!=='training'||item.status!=='active')throw Error('此安排不能补记');
  const existing=M.itemEvents(s,itemId).length;if(existing+count>item.targetSets)throw Error('补记数量超过剩余目标，请先明确调整目标');
  const next=M.clone(s),recordedAt=new Date().toISOString();
  for(let index=0;index<count;index++){const requestId=`${batchId}:${index}`;next.events.push({id:M.uid(),requestId,batchId,planId,day,itemId,exerciseId:item.exerciseId,ordinal:existing+index+1,status:'valid',recordedAt,timezone:Intl.DateTimeFormat().resolvedOptions().timeZone,utcOffsetMinutes:-new Date().getTimezoneOffset(),timeSource:'dateOnly',actualAt:null,actual:{total:null,left:null,right:null},snapshot:M.clone(item)});next.requests.push(requestId)}next.requests.push(batchId);return next;
}
export function revokeBatch(s,batchId){const next=M.clone(s),revokedAt=new Date().toISOString();for(const event of next.events)if(event.batchId===batchId&&event.status==='valid'){event.status='revoked';event.revokedAt=revokedAt}return next}
export const copyableItems=(plan,day)=>plan?.status==='archived'?day?.items??[]:day?P.activeItems(day):[];
export function copyIntoPlan(s,fromPlanId,fromDate,toPlanId,targets){
  const sourcePlan=s.plans.find(plan=>plan.id===fromPlanId),source=sourcePlan?.days.find(day=>day.date===fromDate),target=s.plans.find(plan=>plan.id===toPlanId),items=copyableItems(sourcePlan,source);
  if(!source||!items.length)throw Error('来源日期没有可复制的动作');if(!target||target.status!=='active')throw Error('请选择生效的目标计划');
  if(!targets.length)throw Error('请选择目标日期');const next=M.clone(target);for(const date of [...new Set(targets)]){if(!next.days.some(day=>day.date===date))throw Error('目标日期不在计划内');if(s.events.some(event=>event.planId===toPlanId&&event.day===date))throw Error('目标日有历史记录，不能覆盖');if(fromPlanId===toPlanId&&date===fromDate)throw Error('请选择其他日期');const index=next.days.findIndex(day=>day.date===date);next.days[index]={...M.clone(source),date,kind:'training',parts:[...new Set(items.filter(item=>item.purpose==='strength').map(item=>item.primaryPart))],items:items.map(item=>({...M.clone(item),id:M.uid(),status:'active'}))}}
  return M.savePlan(s,next);
}
export function saveTemplate(s,day,name){
  if(!name.trim()||name.trim().length>60)throw Error('模板名称须为1—60字符');if(!P.activeItems(day).length)throw Error('当前日没有可保存的动作');
  for(const item of P.activeItems(day)){M.integer(item.targetSets,99);M.integer(item.targetValue,item.unit==='seconds'?3600:999)}
  const next=M.clone(s);next.templates??=[];next.templates.push({id:M.uid(),name:name.trim(),parts:[...day.parts],items:P.activeItems(day).map(({id,...item})=>M.clone(item)),createdAt:new Date().toISOString()});return next;
}
export function applyTemplate(plan,date,template,s){
  if(!template?.items?.length)throw Error('模板没有动作');if(s.events.some(event=>event.planId===plan.id&&event.day===date))throw Error('当日有历史记录，不能覆盖');
  const next=M.clone(plan),day=next.days.find(day=>day.date===date);if(!day)throw Error('日期不在计划内');
  Object.assign(day,{kind:'training',parts:[...template.parts],items:template.items.map(item=>({...M.clone(item),id:M.uid(),status:'active'}))});return next;
}
export function recentExercises(s){return [...new Set(M.activeEvents(s).slice().sort((a,b)=>b.recordedAt.localeCompare(a.recordedAt)).map(event=>event.exerciseId))]}
export function recoveryFacts(s,parts){return parts.map(part=>{const rows=M.activeEvents(s,{purpose:'strength',end:s.demoToday}).filter(event=>event.snapshot.primaryPart===part||(event.snapshot.secondaryParts??[]).includes(part)).sort((a,b)=>b.day.localeCompare(a.day)||b.recordedAt.localeCompare(a.recordedAt));return {part,event:rows[0]??null}})}
