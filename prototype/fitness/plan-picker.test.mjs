import test from 'node:test';
import assert from 'node:assert/strict';
import * as P from './plan-picker.mjs';
import * as M from './model.mjs';
const find=id=>M.CATALOG.find(exercise=>exercise.id===id);
const day=()=>({date:'2026-10-24',kind:'training',parts:['chest','shoulder'],items:[]});
const owned=['floor','dumbbellPair','longBand'];

test('80条候选按20条分批，搜索覆盖尚未加载的动作及别名',()=>{
  const exercises=Array.from({length:80},(_,index)=>({...find('EX-CHE-001'),id:`fixture-${index}`,name:`模拟动作${index}`,aliases:[`别名${index}`]}));
  const first=P.candidates(exercises,owned,{scope:'part:chest'});assert.equal(first.total,80);assert.equal(first.items.length,20);assert.equal(first.hasMore,true);
  assert.equal(P.candidates(exercises,owned,{scope:'part:chest',limit:40}).items.length,40);
  const searched=P.candidates(exercises,owned,{scope:'part:chest',query:'别名79'});assert.equal(searched.total,1);assert.equal(searched.items[0].id,'fixture-79');
  assert.equal(P.candidates(exercises,owned,{scope:'part:chest',limit:80}).hasMore,false);
});
test('30条候选末批及主部位筛选，不把卧推辅助肩部当肩部动作',()=>{
  const exercises=Array.from({length:30},(_,index)=>({...find('EX-CHE-005'),id:`fixture-${index}`}));
  assert.equal(P.candidates(exercises,owned,{scope:'part:chest',limit:40}).items.length,30);
  assert.equal(P.candidates(exercises,owned,{scope:'part:shoulder'}).total,0);
});
test('哑铃筛选仍检查训练凳，单哑铃可由双哑铃匹配，徒手排除器械动作',()=>{
  const exercises=M.CATALOG,scope='part:chest';
  assert.deepEqual(P.candidates(exercises,owned,{scope,gear:'dumbbell'}).items.map(exercise=>exercise.id),['EX-CHE-005']);
  assert.equal(P.candidates(exercises,owned,{scope,gear:'dumbbell',mine:false}).total,2);
  assert.equal(P.candidates(exercises,owned,{scope,gear:'floor'}).total,1);
  assert.equal(P.candidates(exercises,owned,{scope:'part:forearm'}).total,1);
});
test('分类、筛选只查询候选，不改变跨部位已选及另一日',()=>{
  let chosen=P.toggleItem(day(),find('EX-CHE-001'));chosen=P.toggleItem(chosen,find('EX-SHO-005'));
  const before=M.clone(chosen),other=day();P.candidates(M.CATALOG,owned,{scope:'part:chest',query:'不存在'});
  assert.deepEqual(chosen,before);assert.equal(P.activeItems(chosen).length,2);assert.equal(P.activeItems(other).length,0);
  assert.deepEqual(P.groups(chosen).map(group=>[group.key,group.items.length]),[['part:chest',1],['part:shoulder',1]]);
});
test('取消末个动作保留部位意图，重加复用身份和已填目标',()=>{
  let chosen=P.toggleItem(day(),find('EX-CHE-001'));chosen.items[0].targetSets=4;chosen.items[0].targetValue=12;const id=chosen.items[0].id;
  chosen=P.toggleItem(chosen,find('EX-CHE-001'));assert.deepEqual(chosen.parts,['chest','shoulder']);assert.equal(chosen.kind,'training');
  chosen=P.toggleItem(chosen,find('EX-CHE-001'));assert.equal(chosen.items.length,1);assert.equal(chosen.items[0].id,id);assert.equal(chosen.items[0].targetValue,12);
});
test('纯活动日不增加力量部位，保留单侧秒数计量',()=>{
  let chosen={...day(),parts:[]};chosen=P.toggleItem(chosen,find('EX-MOB-003'));
  assert.deepEqual(chosen.parts,[]);assert.equal(chosen.items[0].unit,'seconds');assert.equal(chosen.items[0].side,'each');assert.ok(P.scopes(chosen).includes('purpose:cardio'));
});
test('添加活动不会改变快捷入口顺序',()=>{
  const before=day(),after=P.toggleItem(before,find('EX-MOB-003'));
  assert.deepEqual(P.scopes(after),P.scopes(before));
});
test('批量目标默认只填空值，分别填写次数和秒数，可按部位覆盖',()=>{
  let chosen=P.toggleItem(day(),find('EX-CHE-001'));chosen=P.toggleItem(chosen,find('EX-COR-009'));chosen.items[0].targetSets=4;chosen.items[0].targetValue=12;
  const next=P.batchTargets(chosen,{sets:'3',reps:'20',seconds:'30'});assert.equal(next.items[0].targetValue,12);assert.equal(next.items[0].targetSets,4);assert.equal(next.items[1].targetValue,30);
  const overwritten=P.batchTargets(next,{scope:'part:chest',overwrite:true,reps:18});assert.equal(overwritten.items[0].targetValue,18);assert.equal(overwritten.items[1].targetValue,30);assert.equal(chosen.items[1].targetValue,'');
});
test('批量覆盖拒绝少于有效记录的组数，失败不改变草稿',()=>{
  const chosen=P.toggleItem(day(),find('EX-CHE-001')),before=M.clone(chosen),events=Array.from({length:3},()=>({status:'valid',itemId:chosen.items[0].id}));
  assert.throws(()=>P.batchTargets(chosen,{sets:2,overwrite:true},events),/已完成/);assert.deepEqual(chosen,before);assert.throws(()=>P.batchTargets(chosen,{}),/至少/);
});
test('分组内排序不交换其他部位动作，不改变记录身份',()=>{
  let chosen=P.toggleItem(day(),find('EX-CHE-001'));chosen=P.toggleItem(chosen,find('EX-SHO-005'));chosen=P.toggleItem(chosen,find('EX-CHE-005'));
  const ids=chosen.items.map(item=>item.id),next=P.reorder(chosen,ids[0],1);assert.equal(next.items[1].id,ids[1]);assert.equal(next.items[2].id,ids[0]);assert.deepEqual(P.reorder(next,ids[0],1),next);
});
