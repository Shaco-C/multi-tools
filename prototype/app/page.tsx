"use client";

import { useEffect, useMemo, useState } from "react";
import { ArrowLeft, BarChart3, Bolt, Check, ChevronRight, CircleHelp, History, Lightbulb, Minus, MoreHorizontal, Plus, ReceiptText, Settings, Sparkles, Trash2, WalletCards, Wrench } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";

type Screen = "home" | "calculator" | "history" | "trend";
type Meter = { id: number; label: string; previous: string; current: string };

const historyData = [
  { month: "2026 年 9 月", total: 100, mine: 50, usage: 200 },
  { month: "2026 年 8 月", total: 132.5, mine: 61.2, usage: 248 },
  { month: "2026 年 7 月", total: 118.8, mine: 56.4, usage: 226 },
  { month: "2026 年 6 月", total: 96.2, mine: 43.8, usage: 188 },
];
const trendData = [38, 42, 46, 41, 45, 43.8, 56.4, 61.2, 50];
const money = (value: number) => value.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export default function HomePage() {
  const [screen, setScreen] = useState<Screen>("home");
  const [totalBill, setTotalBill] = useState("100");
  const [month, setMonth] = useState("2026-09");
  const [meters, setMeters] = useState<Meter[]>([
    { id: 1, label: "我的电表", previous: "100", current: "200" },
    { id: 2, label: "第 2 户", previous: "50", current: "150" },
  ]);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    const context = (document as Document & {
      modelContext?: {
        registerTool: (tool: Record<string, unknown>, options?: { signal?: AbortSignal }) => void | Promise<void>;
      };
    }).modelContext;
    if (!context?.registerTool) return;
    const lifecycle = new AbortController();
    void Promise.resolve(context.registerTool({
      name: "configure_electricity_split",
      title: "填写电费分摊",
      description: "打开电费分摊工具，并一次填写总电费与各户上下期读数。只更新当前表单，不保存记录。",
      inputSchema: {
        type: "object",
        properties: {
          totalAmount: { type: "number", minimum: 0 },
          meters: {
            type: "array", minItems: 1, maxItems: 6,
            items: { type: "object", properties: { label: { type: "string" }, previous: { type: "number", minimum: 0 }, current: { type: "number", minimum: 0 } }, required: ["label", "previous", "current"], additionalProperties: false },
          },
        },
        required: ["totalAmount", "meters"], additionalProperties: false,
      },
      annotations: { readOnlyHint: false, untrustedContentHint: false },
      execute(input: unknown) {
        const value = input as { totalAmount?: number; meters?: Array<{ label?: string; previous?: number; current?: number }> };
        if (typeof value.totalAmount !== "number" || !Array.isArray(value.meters) || value.meters.length < 1 || value.meters.length > 6) throw new Error("请输入有效的总金额和 1 至 6 户电表数据");
        if (value.meters.some(item => typeof item.label !== "string" || typeof item.previous !== "number" || typeof item.current !== "number" || item.current < item.previous)) throw new Error("每户名称和读数必须有效，且本期读数不能小于上期读数");
        setTotalBill(String(value.totalAmount));
        setMeters(value.meters.map((item, index) => ({ id: index + 1, label: index === 0 ? "我的电表" : item.label!, previous: String(item.previous), current: String(item.current) })));
        setSaved(false);
        setScreen("calculator");
        return { status: "configured", householdCount: value.meters.length, totalAmount: value.totalAmount };
      },
    }, { signal: lifecycle.signal })).catch(() => undefined);
    return () => lifecycle.abort();
  }, []);

  const result = useMemo(() => {
    const usages = meters.map(({ previous, current }) => {
      const p = Number(previous), c = Number(current);
      return Number.isFinite(p) && Number.isFinite(c) ? Math.max(0, c - p) : 0;
    });
    const totalUsage = usages.reduce((sum, item) => sum + item, 0);
    const parsedBill = Number(totalBill);
    const billCents = Number.isFinite(parsedBill) && parsedBill >= 0 ? Math.round(parsedBill * 100) : 0;
    // 与 Android 正式实现保持一致：先向下取整，再按小数余数补分，保证分摊合计精确等于总金额。
    const exactCents = usages.map(usage => totalUsage > 0 ? (billCents * usage) / totalUsage : 0);
    const floorCents = exactCents.map(value => Math.floor(value));
    const missingCents = Math.max(0, Math.min(
      usages.length,
      billCents - floorCents.reduce((sum, value) => sum + value, 0),
    ));
    const extraCentIndexes = new Set(
      exactCents
        .map((value, index) => ({ index, remainder: value - floorCents[index] }))
        .sort((left, right) => right.remainder - left.remainder || left.index - right.index)
        .slice(0, missingCents)
        .map(item => item.index),
    );
    const shares = usages.map((usage, index) => {
      const cents = floorCents[index] + (extraCentIndexes.has(index) ? 1 : 0);
      return { usage, percent: totalUsage > 0 ? usage / totalUsage * 100 : 0, amount: cents / 100 };
    });
    return { totalUsage, shares };
  }, [meters, totalBill]);

  const updateMeter = (id: number, field: keyof Meter, value: string) => {
    setSaved(false);
    setMeters(items => items.map(item => item.id === id ? { ...item, [field]: value } : item));
  };
  const addMeter = () => {
    if (meters.length >= 6) return;
    const id = Math.max(...meters.map(item => item.id)) + 1;
    setMeters(items => [...items, { id, label: `第 ${items.length + 1} 户`, previous: "", current: "" }]);
    setSaved(false);
  };
  const removeMeter = (id: number) => {
    if (id === 1 || meters.length === 1) return;
    setMeters(items => items.filter(item => item.id !== id));
    setSaved(false);
  };
  return (
    <main className="app-shell">
      <section className="content-shell">
        {screen === "home" && <HomeScreen onOpen={() => setScreen("calculator")} />}
        {screen === "calculator" && <CalculatorScreen month={month} setMonth={setMonth} totalBill={totalBill} setTotalBill={value => { setTotalBill(value); setSaved(false); }} meters={meters} result={result} updateMeter={updateMeter} addMeter={addMeter} removeMeter={removeMeter} saved={saved} onSave={() => setSaved(true)} onBack={() => setScreen("home")} onNavigate={setScreen} />}
        {screen === "history" && <HistoryScreen onBack={() => setScreen("home")} onNavigate={setScreen} />}
        {screen === "trend" && <TrendScreen onBack={() => setScreen("home")} onNavigate={setScreen} />}
      </section>
    </main>
  );
}

function HomeScreen({ onOpen }: { onOpen: () => void }) {
  return <div className="page home-page">
    <header className="home-header"><div><p className="eyebrow">星期四 · 9 月 10 日</p><h1>下午好</h1><p className="subtle">今天想用哪个小工具？</p></div><button className="avatar-button"><Settings /></button></header>
    <section className="featured-card" onClick={onOpen} onKeyDown={e => e.key === "Enter" && onOpen()} role="button" tabIndex={0}>
      <div className="featured-topline"><span className="tool-icon"><Bolt /></span><Badge className="month-badge">本月待记录</Badge></div>
      <div><p className="eyebrow light">常用工具</p><h2>电费分摊</h2><p>按每户实际用量，快速算清本月电费。</p></div>
      <div className="featured-bottom"><div><strong>¥ 50.00</strong><span>上月我的费用</span></div><button className="circle-arrow"><ChevronRight /></button></div>
    </section>
    <section className="section-block"><div className="section-title"><h2>全部工具</h2><span>1 个可用</span></div><div className="tool-grid">
      <button className="tool-tile" onClick={onOpen}><span className="tile-icon cyan"><Lightbulb /></span><span className="tile-copy"><strong>电费分摊</strong><small>用量占比计算</small></span><ChevronRight /></button>
      <div className="tool-tile coming-soon"><span className="tile-icon"><Wrench /></span><span className="tile-copy"><strong>下一个工具</strong><small>等待你的灵感</small></span><Plus /></div>
    </div></section>
    <section className="insight-card"><div className="insight-icon"><Sparkles /></div><div><span>小提示</span><p>保存本月账单后，下月会自动带入本期读数。</p></div></section>
  </div>;
}

function ElectricityNav({ active, onNavigate }: { active: Exclude<Screen, "home">; onNavigate: (screen: Screen) => void }) {
  return <Tabs value={active} onValueChange={value => onNavigate(value as Screen)} className="tool-subnav"><TabsList><TabsTrigger value="calculator"><ReceiptText />计算</TabsTrigger><TabsTrigger value="history"><History />记录</TabsTrigger><TabsTrigger value="trend"><BarChart3 />趋势</TabsTrigger></TabsList></Tabs>;
}

type CalculatorProps = { month: string; setMonth: (v: string) => void; totalBill: string; setTotalBill: (v: string) => void; meters: Meter[]; result: { totalUsage: number; shares: Array<{ usage: number; percent: number; amount: number }> }; updateMeter: (id: number, f: keyof Meter, v: string) => void; addMeter: () => void; removeMeter: (id: number) => void; saved: boolean; onSave: () => void; onBack: () => void; onNavigate: (screen: Screen) => void };

function CalculatorScreen(p: CalculatorProps) {
  const parsedBill = Number(p.totalBill);
  const valid = p.result.totalUsage > 0 && Number.isFinite(parsedBill) && parsedBill >= 0;
  return <div className="page calculator-page">
    <header className="app-header"><Button variant="ghost" size="icon" onClick={p.onBack}><ArrowLeft /></Button><div><p>随身工具箱</p><h1>电费分摊</h1></div><Button variant="ghost" size="icon"><MoreHorizontal /></Button></header>
    <ElectricityNav active="calculator" onNavigate={p.onNavigate} />
    <section className="bill-overview">
      <div className="bill-field wide"><label htmlFor="total-bill">本期总电费</label><div className="money-input"><span>¥</span><Input id="total-bill" inputMode="decimal" value={p.totalBill} onChange={e => p.setTotalBill(e.target.value)} /></div></div>
      <div className="bill-field"><label htmlFor="bill-month">账单月份</label><Input id="bill-month" type="month" value={p.month} onChange={e => p.setMonth(e.target.value)} /></div>
      <div className="bill-field"><label>分摊户数</label><div className="stepper"><Button variant="ghost" size="icon-sm" onClick={() => p.removeMeter(p.meters.at(-1)?.id ?? 1)} disabled={p.meters.length <= 1}><Minus /></Button><strong>{p.meters.length}</strong><Button variant="ghost" size="icon-sm" onClick={p.addMeter} disabled={p.meters.length >= 6}><Plus /></Button></div></div>
    </section>
    <div className="meter-heading"><div><h2>电表读数</h2><p>系统会按实际用量自动计算</p></div><span>{p.result.totalUsage} 度</span></div>
    <section className="meter-list">{p.meters.map((meter, index) => { const share = p.result.shares[index]; const invalid = meter.current !== "" && Number(meter.current) < Number(meter.previous); return <Card key={meter.id} className={index === 0 ? "meter-card owner-card" : "meter-card"}>
      <div className="meter-card-head"><div className="meter-name"><span className="meter-index">{index + 1}</span><Input aria-label="电表名称" value={meter.label} onChange={e => p.updateMeter(meter.id, "label", e.target.value)} />{index === 0 && <Badge className="owner-badge">我的</Badge>}</div>{index > 0 && <Button variant="ghost" size="icon-sm" onClick={() => p.removeMeter(meter.id)}><Trash2 /></Button>}</div>
      <div className="reading-grid"><label><span>上期读数</span><Input inputMode="decimal" value={meter.previous} onChange={e => p.updateMeter(meter.id, "previous", e.target.value)} placeholder="0" /></label><span className="reading-arrow">→</span><label><span>本期读数</span><Input inputMode="decimal" aria-invalid={invalid} value={meter.current} onChange={e => p.updateMeter(meter.id, "current", e.target.value)} placeholder="0" /></label></div>
      {invalid ? <p className="error-text">本期读数不能小于上期读数</p> : <div className="meter-result"><span>用量 <strong>{share.usage}</strong> 度</span><span>{share.percent.toFixed(1)}%</span><strong>¥ {money(share.amount)}</strong></div>}
    </Card>; })}</section>
    <Button variant="outline" className="add-meter" onClick={p.addMeter} disabled={p.meters.length >= 6}><Plus /> 添加一户</Button>
    <section className="result-panel"><div><p>我的本期费用</p><strong>¥ {money(p.result.shares[0]?.amount ?? 0)}</strong></div><div className="result-meta"><span>总用量 {p.result.totalUsage} 度</span><span>占比 {p.result.shares[0]?.percent.toFixed(1) ?? "0.0"}%</span></div><Button size="lg" disabled={!valid} onClick={p.onSave} className={p.saved ? "save-button saved" : "save-button"}>{p.saved ? <><Check /> 已保存本期记录</> : <><ReceiptText /> 保存本期记录</>}</Button></section>
  </div>;
}

function HistoryScreen({ onBack, onNavigate }: { onBack: () => void; onNavigate: (screen: Screen) => void }) {
  return <div className="page records-page"><header className="app-header"><Button variant="ghost" size="icon" onClick={onBack}><ArrowLeft /></Button><div><p>随身工具箱</p><h1>电费分摊</h1></div><Button variant="ghost" size="icon"><MoreHorizontal /></Button></header>
    <ElectricityNav active="history" onNavigate={onNavigate} />
    <div className="tool-section-heading"><div><p className="eyebrow">按月份保存</p><h2>历史账单</h2></div><Button onClick={() => onNavigate("calculator")}><Plus /> 新建账单</Button></div>
    <section className="summary-strip"><div><span>今年我的费用</span><strong>¥ 473.60</strong></div><div><span>月均费用</span><strong>¥ 52.62</strong></div><div><span>已记录</span><strong>9 个月</strong></div></section>
    <div className="record-list-title"><h2>最近账单</h2><span>共 9 条</span></div>
    <section className="history-list">{historyData.map(item => <button className="history-item" key={item.month}><span className="history-date"><span className="record-type"><Bolt /></span><span><strong>{item.month}</strong><small>{item.usage} 度 · 总额 ¥{money(item.total)}</small></span></span><span className="history-amount"><small>我的费用</small><strong>¥ {money(item.mine)}</strong></span><ChevronRight /></button>)}</section>
  </div>;
}

function TrendScreen({ onBack, onNavigate }: { onBack: () => void; onNavigate: (screen: Screen) => void }) {
  const max = Math.max(...trendData); const points = trendData.map((value, index) => `${24 + index * 42},${150 - value / max * 105}`).join(" ");
  return <div className="page trend-page"><header className="app-header"><Button variant="ghost" size="icon" onClick={onBack}><ArrowLeft /></Button><div><p>随身工具箱</p><h1>电费分摊</h1></div><Button variant="ghost" size="icon"><MoreHorizontal /></Button></header>
    <ElectricityNav active="trend" onNavigate={onNavigate} />
    <div className="tool-section-heading"><div><p className="eyebrow">仅统计我的费用</p><h2>费用趋势</h2></div></div>
    <section className="trend-hero"><div className="trend-copy"><span>本月费用</span><strong>¥ 50.00</strong><p><Badge>较上月 ↓ 18.3%</Badge></p></div><svg viewBox="0 0 380 180" role="img" aria-label="最近九个月费用趋势折线图"><defs><linearGradient id="area" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#20d9ca" stopOpacity=".4"/><stop offset="100%" stopColor="#20d9ca" stopOpacity="0"/></linearGradient></defs><line x1="20" y1="50" x2="370" y2="50" className="grid-line"/><line x1="20" y1="100" x2="370" y2="100" className="grid-line"/><line x1="20" y1="150" x2="370" y2="150" className="grid-line"/><polygon points={`24,150 ${points} 360,150`} fill="url(#area)"/><polyline points={points} fill="none" stroke="#16c7bc" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round"/><circle cx="360" cy={150 - trendData.at(-1)! / max * 105} r="7" fill="#071c2c" stroke="#7ef5eb" strokeWidth="4" /></svg><div className="chart-labels"><span>1 月</span><span>3 月</span><span>5 月</span><span>7 月</span><span>9 月</span></div></section>
    <section className="metric-grid"><Card className="metric-card"><WalletCards /><span>近 9 月合计</span><strong>¥ 423.40</strong></Card><Card className="metric-card"><BarChart3 /><span>月均费用</span><strong>¥ 47.04</strong></Card></section>
    <section className="insight-card wide-insight"><div className="insight-icon"><CircleHelp /></div><div><span>趋势解读</span><p>本月费用比上月减少 ¥11.20，用电量回落到近三个月的平均水平。</p></div></section>
  </div>;
}
