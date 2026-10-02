import test from 'node:test';
import assert from 'node:assert/strict';
import { seed, localDate, addIncident, addEvent, saveCatalog, toggleCatalog, brandStats, filteredEvents, recordedMeals, migrateLegacy } from './model.js';

test('首次不适与追加事件分开计数，同一顿饮食只计算一次', () => {
  const data = seed();
  const before = brandStats(data).find(b => b.id === 'b1');
  const {meal,event} = addIncident(data,{brandId:'b1',foodId:'f1',date:localDate(1),typeId:'t1'});
  addEvent(data,meal.id,'t1');
  addEvent(data,meal.id,'t2');
  const after = brandStats(data).find(b => b.id === 'b1');
  assert.equal(after.events,before.events+3);
  assert.equal(after.meals,before.meals+1);
  assert.equal(event.typeId,'t1');
  assert.equal(data.active,meal.id);
});

test('仅维护食品不产生历史记录，改名保持统计关联', () => {
  const data = seed();
  const before = brandStats(data);
  const mealsBefore = recordedMeals(data).length;
  saveCatalog(data,'foods',null,'冰淇淋','b1');
  assert.equal(recordedMeals(data).length,mealsBefore);
  assert.deepEqual(brandStats(data),before);
  saveCatalog(data,'brands','b1','麦当劳（常去店）');
  saveCatalog(data,'foods','f1','辣鸡翅','b1');
  assert.equal(brandStats(data).find(b=>b.id==='b1').events,before[0].events);
  assert.equal(data.meals.find(m=>m.id==='m1').foodId,'f1');
});

test('停用不丢失历史，停用选项不能用于新增，类型至少保留一个', () => {
  const data = seed();
  const before = structuredClone(data.events);
  toggleCatalog(data,'brands','b1');
  assert.throws(()=>addIncident(data,{brandId:'b1',foodId:'f1',date:localDate(1),typeId:'t1'}));
  assert.deepEqual(data.events,before);
  toggleCatalog(data,'types','t1');
  assert.equal(data.defaultType,'t2');
  assert.throws(()=>addEvent(data,'m1','t1'));
  toggleCatalog(data,'types','t2');
  assert.throws(()=>toggleCatalog(data,'types','t3'));
  toggleCatalog(data,'brands','b1');
  assert.equal(brandStats(data).find(b=>b.id==='b1').events,5);
});

test('不能重复创建同品牌食品，不同品牌可以维护同名食品', () => {
  const data = seed();
  assert.throws(()=>saveCatalog(data,'foods',null,' 辣翅 ','b1'));
  assert.throws(()=>saveCatalog(data,'types',null,'腹泻'));
  assert.throws(()=>saveCatalog(data,'brands',null,'   '));
  assert.doesNotThrow(()=>saveCatalog(data,'foods',null,'辣翅','b2'));
});

test('无效首次记录不会产生孤立饮食或事件', () => {
  const data = seed();
  const snapshot = structuredClone(data);
  assert.throws(()=>addIncident(data,{brandId:'b1',foodId:'f3',date:localDate(1),typeId:'t1'}));
  assert.throws(()=>addIncident(data,{brandId:'b1',foodId:'f1',date:localDate(-1),typeId:'t1'}));
  assert.throws(()=>addIncident(data,{brandId:'b1',foodId:'f1',date:localDate(1),typeId:'missing'}));
  assert.deepEqual(data,snapshot);
});

test('类型与时间筛选依据不适日期，品牌合计等于筛选事件数', () => {
  const data = seed();
  const rows = filteredEvents(data,'30','t2');
  assert.equal(rows.length,1);
  assert.equal(rows[0].mealId,'m4');
  const recent = filteredEvents(data,'1','t1');
  assert.equal(recent.length,2);
  assert.equal(brandStats(data,recent).reduce((sum,b)=>sum+b.events,0),recent.length);
});

test('旧版迁移只带入不适历史，未出现不适的食品仍可维护', () => {
  const old = {active:'normal',meals:[{id:'normal',brand:'新品牌',food:'面条',date:localDate(3),outcome:'fine'},{id:'bad',brand:'新品牌',food:'鸡翅',date:localDate(1)}],events:[{id:'event',mealId:'bad',at:new Date().toISOString()}]};
  const data = migrateLegacy(old);
  assert.equal(data.active,'bad');
  assert.equal(data.meals.length,1);
  assert.equal(data.events.length,1);
  assert.ok(data.foods.some(f=>f.name==='面条'));
  assert.equal(data.events[0].typeId,'t1');
});
