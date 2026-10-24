import test from 'node:test';
import assert from 'node:assert/strict';
import * as M from './model.mjs';
import * as U from './experience.mjs';
const today='2026-10-23';
test('继续入口先返回今天，定位未完成动作并忽略已经记满项',()=>{
  let s=M.createDemo();assert.equal(U.todayAction(s,'2026-10-22').kind,'today');
  const first=M.schedule(s,today).items[0];s=M.complete(s,{planId:s.plans[0].id,day:today,itemId:first.id});s=M.complete(s,{planId:s.plans[0].id,day:today,itemId:first.id});
  assert.notEqual(U.todayAction(s,today,first.id).row.item.id,first.id);
});
test('空白、休息、全部结束以及只有历史的首页主操作有明确含义',()=>{
  let s=M.emptyState();assert.equal(U.todayAction(s).kind,'arrange');s.plans=[{id:'p',status:'active',days:[{date:today,kind:'rest',parts:[],items:[]}]}];assert.equal(U.todayAction(s).kind,'calendar');
  s=M.createDemo();s.plans[0].days.find(day=>day.date===today).items.forEach(item=>item.status='stopped');assert.equal(U.todayAction(s).kind,'records');
});
test('同一编辑草稿可恢复，单日安排和其他计划不能静默覆盖',()=>{
  const draft={editing:true,plan:{id:'original',start:today,end:today}};
  assert.equal(U.draftRoute(draft,{planId:'original'}),'resume');assert.equal(U.draftRoute(draft,{source:'day',date:today}),'protect');assert.equal(U.draftRoute(draft,{planId:'another'}),'protect');assert.equal(U.draftRoute(null,{}),'new');
});
test('备份导出预检和恢复共享UTF8字节限制，超限资料拒绝而非假成功',()=>{
  const s=M.createDemo(),backup=U.prepareBackup(s);assert.equal(U.inspectBackup(backup.text).events,s.events.length);assert.equal(U.byteLength('胸部'),6);
  assert.throws(()=>U.inspectBackup(' '.repeat(U.MAX_BACKUP_BYTES+1)),/20 MiB/);
  const large=M.emptyState();large.extra='x'.repeat(U.MAX_BACKUP_BYTES);assert.throws(()=>U.prepareBackup(large),/20 MiB/);assert.throws(()=>U.checkStorageState(large),/本次变更未保存/);
});
test('检查清单同时指出日期、缺部位、动作目标，不改变草稿',()=>{
  const s=M.emptyState(),item=M.itemFor(M.CATALOG[0],'',''),plan={id:'p',name:'待补',start:today,end:today,status:'draft',days:[{date:today,kind:'training',parts:['chest','shoulder'],items:[item]}]},before=M.clone(plan),issues=U.planIssues(plan,s);
  assert.ok(issues.some(issue=>issue.scope==='part:shoulder'));assert.ok(issues.some(issue=>issue.itemId===item.id&&issue.field==='value'));assert.ok(issues.every(issue=>issue.day===today));assert.deepEqual(plan,before);
});
test('复制到多日生成新身份，目标日任一历史使整次失败',()=>{
  const s=M.createDemo(),p=s.plans[0],source=p.days.find(day=>day.date===today),targets=[M.dayAdd(today,1),M.dayAdd(today,2)],next=U.copyDraftDays(p,today,targets,s);
  assert.notEqual(next.days.find(day=>day.date===targets[0]).items[0].id,source.items[0].id);assert.notEqual(next.days.find(day=>day.date===targets[0]).items[0].id,next.days.find(day=>day.date===targets[1]).items[0].id);
  assert.throws(()=>U.copyDraftDays(p,today,[M.dayAdd(today,-1),...targets],s),/历史/);assert.equal(p.days.find(day=>day.date===targets[0]).kind,'rest');
});
test('批量部位默认保护已有安排，历史不能通过覆盖开关绕过',()=>{
  const s=M.createDemo(),p=s.plans[0];assert.throws(()=>U.batchDayParts(p,[today],{kind:'training',parts:['chest'],overwrite:true},s),/历史/);
  assert.throws(()=>U.batchDayParts(p,[M.dayAdd(today,1)],{kind:'training',parts:['chest']},s),/已有安排/);
  const next=U.batchDayParts(p,[M.dayAdd(today,2)],{kind:'training',parts:['chest']},s);assert.deepEqual(next.days.find(day=>day.date===M.dayAdd(today,2)).parts,['chest']);
});
test('补记多组一次生成，目标/幂等/空实际值以及整批撤销保持一致',()=>{
  let s=M.createDemo();const p=s.plans[0],day=M.dayAdd(today,-1),item=p.days.find(date=>date.date===day).items[0];s.events=s.events.filter(event=>event.itemId!==item.id);
  const args={planId:p.id,day,itemId:item.id,count:3,batchId:'backfill-test'},next=U.backfillBatch(s,args);assert.equal(M.itemEvents(next,item.id).length,3);assert.ok(M.itemEvents(next,item.id).every(event=>event.timeSource==='dateOnly'&&event.actualAt===null&&event.actual.total===null));assert.equal(U.backfillBatch(next,args).events.length,next.events.length);
  assert.throws(()=>U.backfillBatch(next,{...args,count:2,batchId:'other'}),/剩余目标/);const revoked=U.revokeBatch(next,'backfill-test');assert.equal(M.itemEvents(revoked,item.id).length,0);assert.equal(U.revokeBatch(revoked,'backfill-test').events.filter(event=>event.status==='revoked').length,3);
});
test('归档可作为复制来源，目标必须生效且不能覆盖历史',()=>{
  const s=M.createDemo(),source=s.plans[0];source.status='archived';const target={id:'target',name:'目标',status:'active',start:today,end:today,days:[{date:today,kind:'unplanned',parts:[],items:[]}]};s.plans.push(target);
  const next=U.copyIntoPlan(s,source.id,today,target.id,[today]);assert.equal(next.plans[1].days[0].items.length,2);assert.equal(next.events.length,s.events.length);assert.notEqual(next.plans[1].days[0].items[0].id,source.days.find(day=>day.date===today).items[0].id);
});
test('模板只复制目标，新身份无记录，历史日拒绝应用',()=>{
  const s=M.createDemo(),p=s.plans[0],day=p.days.find(day=>day.date===today),withTemplate=U.saveTemplate(s,day,'核心与腿'),template=withTemplate.templates[0],to=M.dayAdd(today,2),next=U.applyTemplate(p,to,template,s);
  assert.notEqual(next.days.find(day=>day.date===to).items[0].id,day.items[0].id);assert.equal(M.activeEvents(withTemplate).length,M.activeEvents(s).length);assert.throws(()=>U.applyTemplate(p,today,template,s),/历史/);
});
test('恢复事实按计划部位汇集，最近练过根据真实记录且不产生记录',()=>{
  const s=M.createDemo(),facts=U.recoveryFacts(s,['chest','legs']);assert.equal(facts[0].event.day,M.dayAdd(today,-1));assert.ok(U.recentExercises(s).length);assert.equal(U.recoveryFacts(M.emptyState(),['chest'])[0].event,null);
});
test('收藏、模板兼容旧备份，损坏的模板和自建身份被拒绝',()=>{
  assert.doesNotThrow(()=>U.prepareBackup(M.createDemo()));const s=M.createDemo();s.favorites=[null];assert.throws(()=>U.prepareBackup(s),/收藏/);delete s.favorites;s.templates=[{id:'bad',name:'损坏'}];assert.throws(()=>U.prepareBackup(s),/模板/);
  delete s.templates;s.customExercises=[{...M.clone(M.CATALOG[0]),custom:true,image:''}];assert.throws(()=>U.prepareBackup(s),/身份/);
});
test('草稿中正在修改的空日期和目标能够备份恢复，生效计划仍严格校验',()=>{
  const s=M.emptyState();s.editorDraft={date:today,step:0,plan:{id:'draft',name:'',start:'',end:'',days:[{date:today,kind:'training',parts:['chest'],items:[M.itemFor(M.CATALOG[0],'','')]}]}};
  assert.equal(U.inspectBackup(U.prepareBackup(s).text).state.editorDraft.plan.start,'');
  const damaged=M.createDemo();damaged.plans[0].days[0].items[0].targetSets=0;assert.throws(()=>U.prepareBackup(damaged));
});
test('归档中已停止的原动作也能显式复制，新的目标恢复有效且不复制历史',()=>{
  const s=M.createDemo(),source=s.plans[0],day=source.days.find(day=>day.date===today);source.status='archived';day.items.forEach(item=>item.status='stopped');day.kind='unplanned';day.parts=[];
  s.plans.push({id:'new',name:'新计划',status:'active',start:today,end:today,days:[{date:today,kind:'unplanned',parts:[],items:[]}]});const next=U.copyIntoPlan(s,source.id,today,'new',[today]);assert.equal(next.plans[1].days[0].items.length,2);assert.ok(next.plans[1].days[0].items.every(item=>item.status==='active'));assert.equal(next.events.length,s.events.length);
});
test('恢复与图片选择遵守同一单图容量边界',()=>{
  const s=M.emptyState();s.customExercises=[{...M.clone(M.CATALOG[0]),id:'private-photo',custom:true,image:'data:image/png;base64,'+'A'.repeat((U.MAX_IMAGE_BYTES+3)/3*4)}];assert.throws(()=>U.prepareBackup(s),/单张/);
});
