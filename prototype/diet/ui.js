import { KEY, seed, migrateLegacy, localDate, datetimeInput, getMeal, eventsFor, recordedMeals, nameOf, filteredEvents, brandStats, addEvent, addIncident, saveCatalog, toggleCatalog } from './model.js';
import { icon } from './icons.js';

const $ = selector => document.querySelector(selector);
const esc = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const timeLabel = at => new Date(at).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',hour12:false});
const dateLabel = date => date === localDate() ? '今天' : date === localDate(1) ? '昨天' : date.replaceAll('-','.');
const fullDate = date => date.replace(/^(\d+)-(\d+)-(\d+)$/,'$1年$2月$3日');
let data = seed();
try {
  const stored = JSON.parse(localStorage.getItem(KEY));
  if (stored?.version === 2 && ['brands','foods','types','meals','events'].every(k => Array.isArray(stored[k]))) data = stored;
  else {
    const legacy = JSON.parse(localStorage.getItem('pocket-toolbox-diet-prototype-v1'));
    if (legacy) data = migrateLegacy(legacy);
  }
} catch { /* 浏览器禁止存储时仍可体验原型。 */ }
let tab = 'record', brand = null, food = null, search = '', range = 'all', metric = 'events', typeFilter = 'all';
let manageBrand = data.brands[0]?.id, fromStats = false, editingEvent = null, editingMeal = null, catalogEdit = null;
let draftAt = null, pendingDraft = null, undo = null, toastTimer;
const name = (kind,id) => nameOf(data,kind,id);
const rowsFor = id => eventsFor(data,id);
const selected = () => getMeal(data,data.active);
const scopedEvents = () => filteredEvents(data,range,typeFilter);
const activeTypes = () => data.types.filter(t => !t.archived);
const symbol = id => {const text = name('brands',id);return `<span class="tile ${text==='肯德基'?'rose':text==='麦当劳'?'':'teal'}">${esc(text.slice(0,1))}</span>`;};
const options = (items,value,placeholder) => `${placeholder?`<option value="">${esc(placeholder)}</option>`:''}${items.map(item=>`<option value="${item.id}" ${item.id===value?'selected':''}>${esc(item.name)}${item.archived?'（已停用）':''}</option>`).join('')}`;
function persist() {try {localStorage.setItem(KEY,JSON.stringify(data));} catch {notify('浏览器存储不可用，本次变更仍可体验。');}}
function notify(message,action) {
  clearTimeout(toastTimer); $('#toast').hidden=false;
  $('#toast').innerHTML=esc(message)+(action?'<button id="undo">撤销</button>':'');
  undo=action||null;toastTimer=setTimeout(()=>$('#toast').hidden=true,6500);
}
function fixActive() {
  if (!data.meals.some(m=>m.id===data.active && rowsFor(m.id).length)) data.active=recordedMeals(data)[0]?.id||null;
}
function mealRows(meals, scope = null) {
  return meals.map(m=>{
    const events=scope?scope.filter(e=>e.mealId===m.id):rowsFor(m.id);const symptoms=[...new Set(events.map(e=>name('types',e.typeId)))].join('、');
    return `<button class="meal-row ${m.id===data.active?'selected':''}" data-select="${m.id}">${symbol(m.brandId)}<span class="row-copy"><strong>${esc(name('brands',m.brandId))} · ${esc(name('foods',m.foodId))}</strong><small>${fullDate(m.date)}进食 · ${esc(symptoms)}</small></span><span class="badge">${scope?'筛选内 ':''}${events.length} 次不适</span><i class="row-arrow">${icon('arrow')}</i></button>`;
  }).join('');
}
function eventRows(events) {
  return events.length?events.slice().sort((a,b)=>b.at.localeCompare(a.at)).map(e=>{
    const m=getMeal(data,e.mealId);
    return `<div class="event"><span class="event-icon">${icon('clock')}</span><div class="event-body"><div class="event-time">${timeLabel(e.at)} <span class="record-tabs-title">${dateLabel(datetimeInput(e.at).slice(0,10))}</span></div><p class="event-copy">${esc(name('types',e.typeId))} · ${esc(name('brands',m.brandId))} / ${esc(name('foods',m.foodId))}</p><p class="event-meal-date">进食：${fullDate(m.date)}</p></div><div class="event-actions"><button class="icon-btn" data-edit="${e.id}" aria-label="修改${timeLabel(e.at)}的记录">${icon('edit')}</button><button class="icon-btn" data-delete="${e.id}" aria-label="删除${timeLabel(e.at)}的记录">${icon('trash')}</button></div></div>`;
  }).join(''):'<div class="empty">这里还没有不适记录。<br>出现不适时再记，不用记录正常饮食。</div>';
}
function recordPage() {
  const m=selected();const related=m?rowsFor(m.id):[];
  const today=related.filter(e=>datetimeInput(e.at).slice(0,10)===localDate());
  const allToday=data.events.filter(e=>datetimeInput(e.at).slice(0,10)===localDate());
  const last=related.slice().sort((a,b)=>b.at.localeCompare(a.at))[0];
  const recent=recordedMeals(data).slice().sort((a,b)=>b.date.localeCompare(a.date));
  const hero=m?`<div class="hero-top"><span class="label-pill"><span class="demo-dot" style="background:#65eee2"></span>继续记录这顿饮食</span><span class="brand-symbol">${icon('meal')}</span></div><p class="eyebrow">记住这一次的感受</p><h2>${esc(name('brands',m.brandId))} · ${esc(name('foods',m.foodId))}</h2><p class="sub">${fullDate(m.date)}进食 · ${dateLabel(m.date)}<br>${esc(m.note||'未添加备注')}</p><div class="hero-count"><strong>${today.length}</strong><span>次 / 今日不适</span></div><p class="sub">这顿饮食累计 ${related.length} 次不适${last?` · 最近 ${timeLabel(last.at)}`:''}</p><div class="hero-type"><label for="quickType">这次记录</label><select id="quickType">${options(activeTypes(),data.defaultType)}</select></div><button class="record-button" id="addEvent" ${activeTypes().length?'':'disabled'}>${icon('plus')}再记一次${esc(name('types',data.defaultType))}</button><div class="hero-foot"><span style="display:flex;align-items:center;gap:5px">${icon('clock')}点一下，自动记当前时分</span><button id="editMeal">修改饮食信息</button></div>`:`<p class="eyebrow">只记不舒服的那一餐</p><h2>出现不适时，再记下来</h2><p class="sub" style="margin-top:15px">选择品牌、食品和类型，时间自动记录。<br>正常饮食不用登记。</p><button class="record-button" id="emptyNew">${icon('plus')}记录一次不适</button>`;
  return `<div class="summary"><div class="stat"><span class="stat-label">${icon('clock')}今日不适</span><strong>${allToday.length}<small>次</small></strong></div><div class="stat"><span class="stat-label">${icon('meal')}关联饮食</span><strong>${recent.length}<small>顿</small></strong></div><div class="stat"><span class="stat-label">${icon('grid')}涉及品牌</span><strong>${brandStats(data).length}<small>个</small></strong></div></div><div class="layout"><div><section class="hero">${hero}</section><section class="section-space"><div class="section-head"><h2>出现过不适的饮食</h2><button class="link-button" id="addMealInline">新的一次 →</button></div><div class="meal-list">${mealRows(recent)||'<div class="empty">记录过的不适饮食会留在这里。</div>'}</div></section></div><div><section class="card"><div class="section-head"><h2>今天的时间线</h2><small>${allToday.length} 条记录</small></div><div class="timeline">${eventRows(allToday)}</div></section><section class="note-card"><i>${icon('spark')}</i><div><h3>点餐前，回看以前的经历</h3><p>只收集不适记录，不要求每天记饮食。品牌和食品的经历，可以随时查。</p><button class="link-button" data-tab="brands" style="padding-left:0;margin-top:8px">查看品牌与食品 →</button></div></section><section class="note-card"><i>${icon('settings')}</i><div><h3>维护一次，以后直接选</h3><p>品牌、品牌下的食品、不适类型都在“管理”里维护。</p><button class="link-button" data-tab="manage" style="padding-left:0;margin-top:8px">管理常用选项 →</button></div></section><p class="preview-tip">上方“记录一次不适”创建新的饮食经历；主卡片“再记一次”追加到当前这顿饮食。</p></div></div>`;
}
function brandsPage() {
  if(brand)return detailPage();
  const rows=brandStats(data).filter(b=>b.name.includes(search));
  return `<div class="layout"><section><div class="search">${icon('search')}<input id="brandSearch" placeholder="点餐前，搜搜这个品牌" aria-label="搜索品牌" value="${esc(search)}"></div>${rows.map(b=>`<button class="brand-row" data-brand="${b.id}">${symbol(b.id)}<span class="row-copy"><strong>${esc(b.name)}</strong><small>${b.foods} 种食品 · 关联 ${b.meals} 顿饮食</small></span><span class="row-right"><strong>${b.events} <small>次</small></strong><small>不适记录</small></span><i class="row-arrow">${icon('arrow')}</i></button>`).join('')||'<div class="empty">没有找到相关不适记录。<br>常用品牌可在“管理”中提前维护。</div>'}</section><div><section class="summary-panel"><p class="muted">已记录的不适</p><div class="total-big">${data.events.length} <span style="font-size:14px;font-weight:400">次</span></div><small>关联 ${recordedMeals(data).length} 顿饮食 · ${brandStats(data).length} 个品牌</small></section><section class="note-card"><i>${icon('meal')}</i><div><h3>没有记录，不等于没有问题</h3><p>这里只展示你记录过的不适经历。正常饮食不用记，也不会把没记录的食品标成“安全”。</p></div></section><button class="outline-button" data-tab="manage">${icon('settings')}维护品牌和食品</button></div></div>`;
}
function scopeLabel() {return `${range==='all'?'全部时间':`近${range}天`} · ${typeFilter==='all'?'全部类型':name('types',typeFilter)}`;}
function detailPage() {
  const events=fromStats?scopedEvents():data.events;
  const meals=data.meals.filter(m=>m.brandId===brand);
  const ids=new Set(meals.map(m=>m.id));const brandEvents=events.filter(e=>ids.has(e.mealId));
  const foods=data.foods.filter(f=>f.brandId===brand && brandEvents.some(e=>getMeal(data,e.mealId)?.foodId===f.id));
  if(!foods.some(f=>f.id===food))food=foods[0]?.id||null;
  const foodMeals=meals.filter(m=>m.foodId===food && events.some(e=>e.mealId===m.id));
  const foodIds=new Set(foodMeals.map(m=>m.id));const foodEvents=events.filter(e=>foodIds.has(e.mealId));
  const last=foodEvents.slice().sort((a,b)=>b.at.localeCompare(a.at))[0];
return `<button class="back-link" id="backBrands">${icon('back')}${fromStats?'返回统计':'全部品牌'}</button>${fromStats?`<div class="scope-pill">当前筛选：${esc(scopeLabel())}</div>`:''}<div class="detail-title">${symbol(brand)}<div><h2>${esc(name('brands',brand))}</h2><p>${brandEvents.length} 次不适 · 关联 ${new Set(brandEvents.map(e=>e.mealId)).size} 顿饮食</p></div></div><div class="layout"><div><div class="section-head"><h2>食品明细</h2><small>${foods.length} 种食品</small></div>${foods.map(f=>{const fids=new Set(meals.filter(m=>m.foodId===f.id).map(m=>m.id));const rows=events.filter(e=>fids.has(e.mealId));return `<button class="food-row ${f.id===food?'active':''}" data-food="${f.id}"><span class="tile teal">${icon('meal')}</span><span class="row-copy"><strong>${esc(f.name)}</strong><small>关联 ${new Set(rows.map(e=>e.mealId)).size} 顿饮食</small></span><span class="row-right"><strong>${rows.length} <small>次</small></strong><small>不适记录</small></span></button>`;}).join('')||'<div class="empty">当前筛选下没有记录。</div>'}<section class="note-card"><i>${icon('spark')}</i><div><h3>给下次点餐的提醒</h3><p>${last?`${esc(name('foods',food))}最近一次记录：${fullDate(datetimeInput(last.at).slice(0,10))} ${timeLabel(last.at)}。`:'这里保留你记录过的具体食品和发生时间。'}<br>记录的是餐后关联，不自动判断原因。</p></div></section></div><div><section class="card"><div class="section-head"><h2>${food?esc(name('foods',food)):'食品'}的记录</h2>${food?'<button class="link-button" id="repeatMeal">又吃后不适，记一次 →</button>':''}</div><div class="timeline scroll-list">${eventRows(foodEvents)}</div></section><section class="section-space"><div class="section-head"><h2>相关饮食</h2><small>${foodMeals.length} 顿</small></div><div class="meal-list">${mealRows(foodMeals,fromStats?events:null)}</div></section></div></div>`;
}
function statsPage() {
  const events=scopedEvents();const rows=brandStats(data,events,metric);const max=Math.max(1,...rows.map(b=>b[metric]));
  return `<div class="filter-row"><div class="segmented"><button data-metric="events" class="${metric==='events'?'active':''}">不适记录次数</button><button data-metric="meals" class="${metric==='meals'?'active':''}">关联饮食次数</button></div><div class="stats-selects"><select id="typeFilter" aria-label="筛选不适类型"><option value="all">全部不适类型</option>${options(data.types,typeFilter)}</select><select id="range" aria-label="统计时间范围"><option value="all" ${range==='all'?'selected':''}>全部时间</option><option value="30" ${range==='30'?'selected':''}>近30天</option><option value="90" ${range==='90'?'selected':''}>近90天</option></select></div></div><div class="summary"><div class="stat"><span class="stat-label">不适记录</span><strong>${events.length}<small>次</small></strong></div><div class="stat"><span class="stat-label">关联饮食</span><strong>${new Set(events.map(e=>e.mealId)).size}<small>顿</small></strong></div><div class="stat"><span class="stat-label">涉及品牌</span><strong>${rows.length}<small>个</small></strong></div></div><div class="layout"><section class="card chart-card"><div class="chart-head"><div><h2>品牌不适对比</h2><p>${esc(scopeLabel())}<br>${metric==='events'?'每次点击记录，单独计数':'同一顿饮食出现多次不适，只计一顿'}</p></div><div class="chart-legend"><span></span>${metric==='events'?'不适次数':'饮食次数'}</div></div>${rows.map(b=>`<button class="bar-row" data-brand="${b.id}" data-scoped="true" aria-label="${esc(b.name)} ${b[metric]}次，查看明细"><span class="bar-label">${esc(b.name)}</span><span class="bar-track"><span class="bar-fill" style="display:block;width:${b[metric]/max*100}%"></span></span><strong>${b[metric]}</strong></button>`).join('')||'<div class="empty">这个时间范围和类型下还没有记录。</div>'}${rows.length?`<div class="chart-axis"><span>0</span><span>${Math.ceil(max/2)}</span><span>${max} 次</span></div>`:''}<p class="chart-callout">点击柱子查看明细，保留当前时间与类型筛选。</p></section><div><section class="card"><div class="section-head"><h2>不适类型分布</h2><small>${events.length} 次</small></div>${data.types.map(t=>{const count=events.filter(e=>e.typeId===t.id).length;return count?`<div class="food-row"><span class="tile teal">${icon('clock')}</span><span class="row-copy"><strong>${esc(t.name)}</strong><small>每次不适单独记录</small></span><strong>${count} 次</strong></div>`:'';}).join('')||'<div class="empty">暂无记录</div>'}</section><section class="note-card"><i>${icon('chart')}</i><div><h3>次数，帮助你回想经历</h3><p>你只记录不适饮食，因此这里不计算“发生率”或食品安全排名。次数多，也可能是记录得更多。</p></div></section></div></div>`;
}
function catalogRow(item,kind) {
  return `<div class="catalog-row ${item.archived?'is-archived':''}"><div class="row-copy"><strong>${esc(item.name)}${kind==='types' && item.id===data.defaultType?'<span class="default-label">默认</span>':''}</strong><small>${item.archived?'已停用 · 历史记录保留':kind==='types'?'可在记录时直接选择':'可在记录时直接选择'}</small></div><div class="catalog-actions">${kind==='types'&&!item.archived&&item.id!==data.defaultType?`<button class="link-button" data-default="${item.id}">设默认</button>`:''}<button class="icon-btn" data-rename="${item.id}" data-kind="${kind}" aria-label="修改${esc(item.name)}名称">${icon('edit')}</button><button class="archive-button" data-archive="${item.id}" data-kind="${kind}">${item.archived?'启用':'停用'}</button></div></div>`;
}
function managePage() {
  if(!data.brands.some(b=>b.id===manageBrand))manageBrand=data.brands[0]?.id;
  const foods=data.foods.filter(f=>f.brandId===manageBrand);
  return `${pendingDraft?'<div class="resume-banner"><span>刚才的记录草稿已保留，维护完成后可继续。</span><button class="link-button" id="resumeRecording">继续记录 →</button></div>':''}<div class="manage-heading"><div><h2>常用选项，一次维护</h2><p class="muted">品牌、食品和不适类型集中放在这里，记录时直接选择。</p></div></div><div class="layout manage-layout"><section class="card"><div class="section-head"><h2>品牌与食品</h2><button class="link-button" data-add="brands">+ 新增品牌</button></div><div class="brand-picks">${data.brands.map(b=>`<div class="brand-pick ${b.id===manageBrand?'active':''} ${b.archived?'is-archived':''}"><button class="brand-pick-name" data-manage-brand="${b.id}">${symbol(b.id)}<span><strong>${esc(b.name)}</strong><small>${b.archived?'已停用':`${data.foods.filter(f=>f.brandId===b.id&&!f.archived).length} 个食品`}</small></span></button><div class="catalog-actions"><button class="icon-btn" data-rename="${b.id}" data-kind="brands" aria-label="修改${esc(b.name)}名称">${icon('edit')}</button><button class="archive-button" data-archive="${b.id}" data-kind="brands">${b.archived?'启用':'停用'}</button></div></div>`).join('')||'<div class="empty">先新增一个常用品牌。</div>'}</div>${manageBrand?`<div class="section-head food-manage-heading"><h3>${esc(name('brands',manageBrand))}的食品</h3><button class="link-button" data-add="foods">+ 新增食品</button></div>${foods.map(f=>catalogRow(f,'foods')).join('')||'<div class="empty">添加这个品牌的常点食品，之后记录时直接选。</div>'}`:''}</section><div><section class="card"><div class="section-head"><h2>不适类型</h2><button class="link-button" data-add="types">+ 新增类型</button></div>${data.types.map(t=>catalogRow(t,'types')).join('')}<p class="preview-tip">默认类型用于首页的一键记录，你仍可在记录前切换。</p></section><section class="note-card"><i>${icon('shield')}</i><div><h3>名称改了，历史也找得到</h3><p>修改名称会同步更新历史显示。“停用”只收起记录时的选项，已有明细和统计保留；需要时可以再启用。</p></div></section><section class="note-card"><i>${icon('meal')}</i><div><h3>食品选项不是饮食记录</h3><p>可以提前维护薯条等常点食品。只有真正记录不适后，才会进入历史和统计。</p></div></section></div></div>`;
}
function paintIcons() {document.querySelectorAll('[data-icon]').forEach(el=>el.innerHTML=icon(el.dataset.icon));}
function render() {
  fixActive();document.querySelectorAll('[data-tab]').forEach(b=>{b.classList.toggle('active',b.dataset.tab===tab);b.setAttribute('aria-current',b.dataset.tab===tab?'page':'false');});
  $('#view').innerHTML=tab==='record'?recordPage():tab==='brands'?brandsPage():tab==='stats'?statsPage():managePage();paintIcons();
}
function updateFoodOptions(chosen=null) {
  const f=$('#mealForm').elements;
  const foods=data.foods.filter(item=>item.brandId===f.brandId.value && (!item.archived||editingMeal?.foodId===item.id));
  f.foodId.innerHTML=options(foods,chosen||foods[0]?.id,foods.length?null:'请先在管理中添加食品');
  updateHistoryHint();
}
function updateHistoryHint() {
  const f=$('#mealForm').elements;const meals=data.meals.filter(m=>m.foodId===f.foodId.value);const ids=new Set(meals.map(m=>m.id));
  const rows=data.events.filter(e=>ids.has(e.mealId));const last=rows.slice().sort((a,b)=>b.at.localeCompare(a.at))[0];
  $('#mealHint').innerHTML=rows.length?`${icon('spark')}<span>这个食品曾记录 <strong>${rows.length} 次不适</strong>，关联 ${new Set(rows.map(e=>e.mealId)).size} 顿饮食。<br>最近：${fullDate(datetimeInput(last.at).slice(0,10))} · ${esc(name('types',last.typeId))}</span>`:'<span>这个食品还没有不适记录。只在出现不适时保存。</span>';
}
function openMeal(prefill=null,resume=false,edit=false) {
  const form=$('#mealForm');form.reset();editingMeal=edit?selected():null;
  if(!resume)pendingDraft=null;
  const brands=data.brands.filter(b=>!b.archived || editingMeal?.brandId===b.id);
  const values=resume?pendingDraft?.values:editingMeal||prefill||{};
  draftAt=resume?pendingDraft.at:new Date().toISOString();
  form.elements.brandId.innerHTML=options(brands,values.brandId||selected()?.brandId||brands[0]?.id,brands.length?null:'请先维护一个品牌');
  updateFoodOptions(values.foodId||selected()?.foodId);
  form.elements.date.value=values.date||localDate(1);form.elements.date.max=localDate();
  form.elements.note.value=values.note||'';
  form.elements.typeId.innerHTML=options(activeTypes(),values.typeId||data.defaultType,activeTypes().length?null:'请先维护不适类型');
  form.elements.typeId.required=!edit;
  $('#firstTypeLabel').hidden=edit;
  $('#mealTitle').textContent=edit?'修改这顿饮食':'记下这次不适';
  $('#mealSubmit').innerHTML=icon('check')+(edit?'保存修改':'保存这次不适');
  $('#capturedAt').textContent=edit?'修改饮食信息不会增加不适次数':`已自动记下发生时间：${fullDate(datetimeInput(draftAt).slice(0,10))} ${timeLabel(draftAt)}`;
  $('#mealError').textContent='';
  document.querySelectorAll('[data-date]').forEach(b=>b.classList.toggle('active',form.elements.date.value===localDate(Number(b.dataset.date))));
  paintIcons();$('#mealDialog').showModal();
}
function recordAgain() {
  try {
    const m=selected();const event=addEvent(data,m.id,data.defaultType);persist();render();
    notify(`已记录${name('types',event.typeId)} · ${timeLabel(event.at)}`,()=>{data.events=data.events.filter(e=>e.id!==event.id);persist();render();notify('已撤销这次记录');});
  } catch(error) {notify(error.message);}
}
function openCatalog(kind,id=null) {
  const item=id?data[kind].find(x=>x.id===id):null;
  catalogEdit={kind,id,brandId:kind==='foods'?(item?.brandId||manageBrand):null};
  const label=kind==='brands'?'品牌':kind==='foods'?'食品':'不适类型';
  $('#catalogTitle').textContent=`${id?'修改':'新增'}${label}`;$('#catalogLabel').textContent=`${label}名称`;
  $('#catalogHelp').textContent=id?'修改名称会同步更新历史显示，记录次数不变。':kind==='foods'?`添加到${name('brands',manageBrand)}，以后记录时直接选。`:'填写一次，以后直接选择。';
  $('#catalogForm').reset();$('#catalogForm').elements.name.value=item?.name||'';$('#catalogError').textContent='';paintIcons();$('#catalogDialog').showModal();
}
document.addEventListener('click',e=>{
  const b=e.target.closest('button');if(!b)return;
  if(b.dataset.tab){tab=b.dataset.tab;brand=null;food=null;fromStats=false;render();}
  else if(b.id==='addEvent')recordAgain();
  else if(['newMeal','addMealInline','emptyNew'].includes(b.id))openMeal();
  else if(b.id==='editMeal')openMeal(null,false,true);
  else if(b.id==='repeatMeal')openMeal({brandId:brand,foodId:food});
  else if(b.id==='resumeRecording')openMeal(null,true);
  else if(b.id==='manageFromForm'){
    manageBrand=$('#mealForm').elements.brandId.value||manageBrand;
    if(!editingMeal)pendingDraft={values:Object.fromEntries(new FormData($('#mealForm'))),at:draftAt};
    else pendingDraft=null;
    $('#mealDialog').close();editingMeal=null;tab='manage';render();
  }
  else if(b.dataset.close)document.getElementById(b.dataset.close).close();
  else if(b.dataset.date){$('#mealForm').elements.date.value=localDate(Number(b.dataset.date));document.querySelectorAll('[data-date]').forEach(x=>x.classList.toggle('active',x===b));}
  else if(b.dataset.select){data.active=b.dataset.select;tab='record';const last=rowsFor(data.active).slice().sort((a,b)=>b.at.localeCompare(a.at)).find(e=>activeTypes().some(t=>t.id===e.typeId));if(last)data.defaultType=last.typeId;persist();render();window.scrollTo({top:0,behavior:'smooth'});}
  else if(b.dataset.brand){brand=b.dataset.brand;food=null;tab='brands';fromStats=b.dataset.scoped==='true';render();window.scrollTo({top:0,behavior:'smooth'});}
  else if(b.dataset.food){food=b.dataset.food;render();}
  else if(b.id==='backBrands'){brand=null;food=null;tab=fromStats?'stats':'brands';fromStats=false;render();}
  else if(b.dataset.metric){metric=b.dataset.metric;render();}
  else if(b.dataset.delete){const event=data.events.find(x=>x.id===b.dataset.delete);data.events=data.events.filter(x=>x.id!==event.id);persist();render();notify('已删除这次记录',()=>{data.events.push(event);data.active=event.mealId;persist();render();notify('已恢复这次记录');});}
  else if(b.dataset.edit){editingEvent=b.dataset.edit;const event=data.events.find(x=>x.id===editingEvent);$('#eventForm').elements.at.value=datetimeInput(event.at);$('#eventForm').elements.typeId.innerHTML=options(data.types.filter(t=>!t.archived||t.id===event.typeId),event.typeId);$('#eventError').textContent='';$('#eventDialog').showModal();}
  else if(b.id==='undo'&&undo){const action=undo;undo=null;action();}
  else if(b.dataset.add)openCatalog(b.dataset.add);
  else if(b.dataset.rename)openCatalog(b.dataset.kind,b.dataset.rename);
  else if(b.dataset.manageBrand){manageBrand=b.dataset.manageBrand;render();}
  else if(b.dataset.archive){try{toggleCatalog(data,b.dataset.kind,b.dataset.archive);persist();render();notify('选项已更新，历史记录保留');}catch(error){notify(error.message);}}
  else if(b.dataset.default){data.defaultType=b.dataset.default;persist();render();notify(`默认不适类型已设为${name('types',data.defaultType)}`);}
  else if(b.id==='reset'){
    if(confirm('恢复示例数据？当前原型中的修改将被替换，可在提示中撤销。')){const previous=data;data=seed();brand=null;food=null;search='';pendingDraft=null;manageBrand=data.brands[0]?.id;persist();render();notify('已恢复示例数据',()=>{data=previous;persist();render();notify('已恢复重置前的数据');});}
  }
});
document.addEventListener('change',e=>{
  if(e.target.id==='range'){range=e.target.value;render();}
  else if(e.target.id==='typeFilter'){typeFilter=e.target.value;render();}
  else if(e.target.id==='quickType'){data.defaultType=e.target.value;persist();render();}
  else if(e.target.form?.id==='mealForm'){
    if(e.target.name==='brandId')updateFoodOptions();
    else if(e.target.name==='foodId')updateHistoryHint();
    else if(e.target.name==='date')document.querySelectorAll('[data-date]').forEach(b=>b.classList.toggle('active',e.target.value===localDate(Number(b.dataset.date))));
  }
});
document.addEventListener('input',e=>{if(e.target.id==='brandSearch'){search=e.target.value;const start=e.target.selectionStart;render();const input=$('#brandSearch');input.focus();input.setSelectionRange(start,start);}});
$('#mealForm').addEventListener('submit',e=>{
  e.preventDefault();const values=Object.fromEntries(new FormData(e.target));values.note=values.note.trim();
  try {
    if(editingMeal){
      const f=data.foods.find(f=>f.id===values.foodId && f.brandId===values.brandId);
      if(!f)throw new Error('请选择对应品牌下的食品。');
      const earliest=rowsFor(editingMeal.id).map(e=>datetimeInput(e.at).slice(0,10)).sort()[0];
      if(!values.date||values.date>localDate()||(earliest&&values.date>earliest))throw new Error('进食日期不能晚于不适发生日期。');
      Object.assign(editingMeal,{brandId:values.brandId,foodId:values.foodId,date:values.date,note:values.note});
      persist();$('#mealDialog').close();editingMeal=null;render();notify('饮食信息已修改，不适次数不变');
    }else{
      const previousActive=data.active;const previousDefault=data.defaultType;const {meal,event}=addIncident(data,values,draftAt);persist();pendingDraft=null;
      tab='record';brand=null;$('#mealDialog').close();render();window.scrollTo({top:0,behavior:'smooth'});
      notify(`已记录${name('types',event.typeId)} · ${timeLabel(event.at)}`,()=>{data.events=data.events.filter(e=>e.mealId!==meal.id);data.meals=data.meals.filter(m=>m.id!==meal.id);data.active=previousActive;data.defaultType=previousDefault;persist();render();notify('已撤销这顿饮食的不适记录');});
    }
  }catch(error){$('#mealError').textContent=error.message;}
});
$('#eventForm').addEventListener('submit',e=>{
  e.preventDefault();const at=new Date(e.target.elements.at.value);const event=data.events.find(x=>x.id===editingEvent);if(!event)return;
  if(!Number.isFinite(at.getTime())||at.getTime()>Date.now()+60000){$('#eventError').textContent='请选择有效的已发生时间。';return;}
  if(datetimeInput(at).slice(0,10)<getMeal(data,event.mealId).date){$('#eventError').textContent='不适日期不能早于关联饮食日期。';return;}
  event.at=at.toISOString();event.typeId=e.target.elements.typeId.value;persist();$('#eventDialog').close();render();notify('不适类型与时间已更新');
});
$('#catalogForm').addEventListener('submit',e=>{
  e.preventDefault();try{const item=saveCatalog(data,catalogEdit.kind,catalogEdit.id,e.target.elements.name.value,catalogEdit.brandId);if(catalogEdit.kind==='brands')manageBrand=item.id;persist();$('#catalogDialog').close();render();notify('已保存，以后记录时直接选择');}catch(error){$('#catalogError').textContent=error.message;}
});
document.querySelectorAll('dialog').forEach(d=>d.addEventListener('click',e=>{if(e.target===d){const r=d.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)d.close();}}));
render();
