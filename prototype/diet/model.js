export const KEY = 'pocket-toolbox-diet-prototype-v2';
export const uid = () => crypto.randomUUID();
export const localDate = (offset = 0) => {
  const date = new Date();
  date.setDate(date.getDate() - offset);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
};
export const datetimeInput = at => {
  const date = new Date(at);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
};
export function seed() {
  const at = (days, time) => new Date(`${localDate(days)}T${time}:00`).toISOString();
  return {
    version: 2, active: 'm1', defaultType: 't1',
    brands: [{id:'b1',name:'麦当劳'},{id:'b2',name:'肯德基'},{id:'b3',name:'华莱士'}],
    foods: [{id:'f1',brandId:'b1',name:'辣翅'},{id:'f2',brandId:'b1',name:'薯条'},{id:'f3',brandId:'b2',name:'香辣鸡腿堡'},{id:'f4',brandId:'b3',name:'香辣鸡翅'}],
    types: [{id:'t1',name:'腹泻'},{id:'t2',name:'胃痛'},{id:'t3',name:'恶心'}],
    meals: [{id:'m1',brandId:'b1',foodId:'f1',date:localDate(1),note:'十翅一桶 · 晚上吃的'},{id:'m2',brandId:'b1',foodId:'f1',date:localDate(12),note:'又忘记了上次的感受'},{id:'m4',brandId:'b2',foodId:'f3',date:localDate(8),note:'午餐'},{id:'m5',brandId:'b3',foodId:'f4',date:localDate(20),note:'晚餐'}],
    events: [{id:'e1',mealId:'m1',typeId:'t1',at:at(0,'08:15')},{id:'e2',mealId:'m1',typeId:'t1',at:at(0,'09:20')},{id:'e3',mealId:'m2',typeId:'t1',at:at(11,'07:50')},{id:'e4',mealId:'m2',typeId:'t1',at:at(11,'09:10')},{id:'e5',mealId:'m2',typeId:'t1',at:at(11,'11:35')},{id:'e6',mealId:'m4',typeId:'t1',at:at(7,'08:30')},{id:'e7',mealId:'m4',typeId:'t2',at:at(7,'10:10')},{id:'e8',mealId:'m5',typeId:'t1',at:at(19,'06:40')}],
  };
}
export function migrateLegacy(old) {
  if (!old || !Array.isArray(old.meals) || !Array.isArray(old.events)) return seed();
  const fresh = seed();
  fresh.meals = [];
  fresh.events = [];
  for (const meal of old.meals) {
    let brand = fresh.brands.find(b => b.name === meal.brand);
    if (!brand) { brand = {id:uid(),name:meal.brand}; fresh.brands.push(brand); }
    let food = fresh.foods.find(f => f.brandId === brand.id && f.name === meal.food);
    if (!food) { food = {id:uid(),brandId:brand.id,name:meal.food}; fresh.foods.push(food); }
    // 未关联不适的旧进食仍保留为食品选项，不进入不适历史。
    const related = old.events.filter(e => e.mealId === meal.id);
    if (!related.length) continue;
    fresh.meals.push({id:meal.id,brandId:brand.id,foodId:food.id,date:meal.date,note:meal.note || ''});
    fresh.events.push(...related.map(e => ({id:e.id,mealId:meal.id,typeId:'t1',at:e.at})));
  }
  fresh.active = fresh.meals.some(m => m.id === old.active) ? old.active : fresh.meals[0]?.id || null;
  return fresh;
}
export const getMeal = (data, id) => data.meals.find(m => m.id === id);
export const eventsFor = (data, id) => data.events.filter(e => e.mealId === id);
export const recordedMeals = data => data.meals.filter(m => eventsFor(data, m.id).length);
export const nameOf = (data, collection, id) => data[collection].find(item => item.id === id)?.name || '未命名';
export function filteredEvents(data, range = 'all', typeId = 'all') {
  const start = new Date();
  start.setHours(0,0,0,0);
  if (range !== 'all') start.setDate(start.getDate() - (Number(range) - 1));
  return data.events.filter(e => (typeId === 'all' || e.typeId === typeId) && (range === 'all' || new Date(e.at) >= start));
}
export function brandStats(data, events = data.events, metric = 'events') {
  return data.brands.map(b => {
    const meals = data.meals.filter(m => m.brandId === b.id);
    const ids = new Set(meals.map(m => m.id));
    const rows = events.filter(e => ids.has(e.mealId));
    const recorded = meals.filter(m => rows.some(e => e.mealId === m.id));
    return {id:b.id,name:b.name,events:rows.length,meals:new Set(rows.map(e => e.mealId)).size,foods:new Set(recorded.map(m => m.foodId)).size};
  }).filter(b => b.events > 0).sort((a,b) => b[metric] - a[metric] || a.name.localeCompare(b.name));
}
export function addEvent(data, mealId, typeId, at = new Date().toISOString()) {
  if (!getMeal(data, mealId)) throw new Error('请先选择关联饮食。');
  const type = data.types.find(t => t.id === typeId && !t.archived);
  if (!type) throw new Error('请先在管理页启用一个不适类型。');
  const event = {id:uid(),mealId,typeId,at};
  data.events.push(event);
  data.active = mealId;
  data.defaultType = typeId;
  return event;
}
export function addIncident(data, values, at = new Date().toISOString()) {
  const brand = data.brands.find(b => b.id === values.brandId && !b.archived);
  const food = data.foods.find(f => f.id === values.foodId && f.brandId === values.brandId && !f.archived);
  if (!brand || !food) throw new Error('请先选择已维护的品牌和食品。');
  if (!data.types.some(t => t.id === values.typeId && !t.archived)) throw new Error('请选择已启用的不适类型。');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(values.date) || values.date > datetimeInput(at).slice(0,10)) throw new Error('进食日期不能晚于不适发生日期。');
  const meal = {id:uid(),brandId:brand.id,foodId:food.id,date:values.date,note:values.note || ''};
  data.meals.unshift(meal);
  const event = addEvent(data, meal.id, values.typeId, at);
  return {meal,event};
}
export function saveCatalog(data, kind, id, name, brandId = null) {
  name = name.trim();
  if (!name || name.length > 40) throw new Error('请输入 1 至 40 个字的名称。');
  const list = data[kind];
  if (!['brands','foods','types'].includes(kind)) throw new Error('无效的分类。');
  if (list.some(item => item.id !== id && item.name.toLocaleLowerCase() === name.toLocaleLowerCase() && (kind !== 'foods' || item.brandId === brandId))) throw new Error('这个名称已存在，停用的项目可以直接重新启用。');
  if (kind === 'foods' && !data.brands.some(b => b.id === brandId)) throw new Error('请先选择品牌。');
  let item = list.find(item => item.id === id);
  if (id && !item) throw new Error('这个项目已不存在。');
  if (item) item.name = name;
  else {item = {id:uid(),name,...(kind === 'foods' ? {brandId} : {})};list.push(item);}
  return item;
}
export function toggleCatalog(data, kind, id) {
  const item = data[kind].find(item => item.id === id);
  if (!item) return;
  if (kind === 'types' && !item.archived && data.types.filter(t => !t.archived).length === 1) throw new Error('请至少保留一个可用的不适类型。');
  item.archived = !item.archived;
  if (kind === 'types' && data.defaultType === id && item.archived) data.defaultType = data.types.find(t => !t.archived).id;
}
