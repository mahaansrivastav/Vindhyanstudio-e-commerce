// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ErrorBox, ProductImage, Spinner } from '../components/ui'
import { api, downloadFile } from '../lib/api'
import { rupees } from '../lib/format'
import type { Dashboard as Data } from '../lib/types'
import { useAsync } from '../lib/useAsync'

type Preset = 'today' | '7d' | '30d' | 'month' | 'lastMonth' | 'quarter' | 'fy' | 'custom'

const PRESETS: [Preset, string][] = [
  ['today', 'Today'], ['7d', 'Last 7 days'], ['30d', 'Last 30 days'], ['month', 'This month'],
  ['lastMonth', 'Last month'], ['quarter', 'This quarter'], ['fy', 'This financial year'], ['custom', 'Custom'],
]

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

/** Date range for a preset. Quarters and the financial year follow the Indian FY (April–March). */
export function range(p: Preset): [string, string] {
  const t = new Date()
  const y = t.getFullYear(), m = t.getMonth()
  switch (p) {
    case 'today': return [iso(t), iso(t)]
    case '7d': return [iso(new Date(y, m, t.getDate() - 6)), iso(t)]
    case '30d': return [iso(new Date(y, m, t.getDate() - 29)), iso(t)]
    case 'month': return [iso(new Date(y, m, 1)), iso(t)]
    case 'lastMonth': return [iso(new Date(y, m - 1, 1)), iso(new Date(y, m, 0))]
    // FY quarters (Apr–Jun, Jul–Sep, Oct–Dec, Jan–Mar) start on the same months as calendar quarters.
    case 'quarter': return [iso(new Date(y, m - (m % 3), 1)), iso(t)]
    case 'fy': return [iso(new Date(m >= 3 ? y : y - 1, 3, 1)), iso(t)]
    default: return [iso(new Date(y, m, t.getDate() - 29)), iso(t)]
  }
}

export function Dashboard() {
  const [preset, setPreset] = useState<Preset>('30d')
  const [[from, to], setRange] = useState<[string, string]>(range('30d'))
  const { data, error, loading, reload } = useAsync(() => api<Data>(`/api/admin/dashboard?from=${from}&to=${to}`), [from, to])
  const [downloading, setDownloading] = useState(false)

  const choose = (p: Preset) => {
    setPreset(p)
    if (p !== 'custom') setRange(range(p))
  }
  const download = async () => {
    setDownloading(true)
    try { await downloadFile(`/api/admin/reports/sales?from=${from}&to=${to}`, `vindhyan-report-${from}-to-${to}.xlsx`) }
    catch (e) { alert((e as Error).message) }
    finally { setDownloading(false) }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end gap-3">
        <div className="mr-auto">
          <h2 className="text-2xl">Dashboard</h2>
          <p className="text-sm text-muted">{fmtDate(from)} – {fmtDate(to)}</p>
        </div>
        <button className="btn-outline" onClick={download} disabled={downloading}>
          <DownloadIcon /> {downloading ? 'Preparing…' : 'Download Excel report'}
        </button>
      </div>

      <div className="space-y-3">
        <div className="scrollbar-none -mx-4 flex gap-2 overflow-x-auto px-4">
          {PRESETS.map(([p, label]) => <button key={p} onClick={() => choose(p)} className={`chip shrink-0 ${preset === p ? 'chip-on' : ''}`}>{label}</button>)}
        </div>
        {preset === 'custom' && (
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <input type="date" className="input w-auto" value={from} max={to} onChange={(e) => e.target.value && setRange([e.target.value, to])} aria-label="From date" />
            <span className="text-muted">to</span>
            <input type="date" className="input w-auto" value={to} min={from} onChange={(e) => e.target.value && setRange([from, e.target.value])} aria-label="To date" />
          </div>
        )}
      </div>

      {error && <ErrorBox message={error} onRetry={reload} />}
      {loading && !data && <Spinner />}
      {data && <Body d={data} loading={loading} />}
    </div>
  )
}

function Body({ d, loading }: { d: Data; loading: boolean }) {
  const k = d.kpis, p = d.previous, a = d.attention
  const lowCount = a.lowStock.length + a.outOfStock.length
  return (
    <div className={`space-y-6 transition ${loading ? 'opacity-60' : ''}`}>
      {/* Needs attention */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Todo to="/admin/orders?status=CONFIRMED" n={a.toPrint} label="To print" />
        <Todo to="/admin/orders?status=READY" n={a.readyToShip} label="Ready to ship" />
        <Todo to="/admin/orders?status=PAYMENT_PENDING" n={a.awaitingPayment} label="Awaiting payment" quiet />
        <Todo to="/admin/products?filter=low" n={lowCount} label="Low stock" />
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        <Kpi label="Sales" value={rupees(k.revenue)} now={k.revenue} before={p.revenue} />
        <Kpi label="Orders" value={String(k.orders)} now={k.orders} before={p.orders} />
        <Kpi label="Average order" value={rupees(k.avgOrderValue)} now={k.avgOrderValue} before={p.avgOrderValue} />
        <Kpi label="Items sold" value={String(k.itemsSold)} now={k.itemsSold} before={p.itemsSold} />
        <Kpi label="Customers" value={String(k.customers)} now={k.customers} before={p.customers} note={`${k.newCustomers} new sign-ups`} />
      </div>

      {/* Sales chart */}
      <section className="card p-5">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h3 className="text-base">Sales</h3>
          <p className="text-xs text-muted">By {d.granularity} · confirmed orders incl. shipping</p>
        </div>
        <Bars points={d.series} />
      </section>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)]">
        <section className="card overflow-hidden">
          <h3 className="px-5 pt-5 text-base">Best-selling products</h3>
          {d.topProducts.length === 0 ? <p className="p-5 text-sm text-muted">No sales in this period yet.</p> : (
            <table className="mt-3 w-full text-sm">
              <thead className="border-y border-line bg-surface/70 text-left text-xs text-muted">
                <tr><th className="px-5 py-2 font-medium">Product</th><th className="px-3 py-2 text-right font-medium">Qty</th><th className="px-5 py-2 text-right font-medium">Sales</th></tr>
              </thead>
              <tbody className="divide-y divide-line">
                {d.topProducts.map((t) => (
                  <tr key={t.sku}>
                    <td className="px-5 py-2.5">
                      <Link to={`/admin/products/${t.sku}`} className="flex items-center gap-3 hover:underline">
                        <ProductImage src={t.image} alt="" className="h-9 w-9 shrink-0 rounded-md" />
                        <span className="min-w-0"><span className="block truncate">{t.name}</span><span className="text-xs text-muted">{t.category}</span></span>
                      </Link>
                    </td>
                    <td className="px-3 py-2.5 text-right tabular-nums">{t.qty}</td>
                    <td className="px-5 py-2.5 text-right font-medium tabular-nums">{rupees(t.revenue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        <div className="space-y-6">
          <section className="card p-5">
            <h3 className="text-base">How customers paid</h3>
            <Split a={['Cash on delivery', k.codOrders, k.codRevenue]} b={['Online', k.onlineOrders, k.onlineRevenue]} />
            {(k.cancelled > 0 || k.returned > 0) && <p className="mt-3 text-xs text-muted">{k.cancelled} cancelled · {k.returned} returned (RTO) in this period</p>}
          </section>
          <section className="card p-5">
            <h3 className="text-base">Sales by category</h3>
            <Share rows={d.categories.slice(0, 6).map((c) => [c.name, c.revenue, `${c.qty} sold`])} />
          </section>
          <section className="card p-5">
            <h3 className="text-base">Top states</h3>
            <Share rows={d.states.map((s) => [s.name, s.revenue, `${s.orders} orders`])} />
          </section>
        </div>
      </div>

      {/* Stock + catalogue health */}
      <section className="card p-5">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h3 className="text-base">Stock alerts</h3>
          <Link to="/admin/products" className="text-sm text-ink/70 hover:text-ink">Manage stock ›</Link>
        </div>
        <p className="mt-1 text-sm text-muted">{a.liveProducts} products on the website · {a.hiddenProducts} hidden · {a.madeToOrder} made to order{a.noPhoto ? ` · ${a.noPhoto} without photos` : ''}</p>
        {lowCount === 0 ? <p className="mt-4 text-sm text-ok">All products on the website are in stock.</p> : (
          <ul className="mt-4 grid gap-2 sm:grid-cols-2">
            {[...a.outOfStock, ...a.lowStock].map((s) => (
              <li key={s.sku}>
                <Link to={`/admin/products?filter=low`} className="flex items-center gap-3 rounded-lg border border-line p-2 text-sm hover:border-ink/30">
                  <ProductImage src={s.image} alt="" className="h-9 w-9 shrink-0 rounded-md" />
                  <span className="min-w-0 flex-1 truncate">{s.name}</span>
                  <span className={`shrink-0 text-xs font-medium ${s.stock <= 0 ? 'text-bad' : 'text-warn'}`}>{s.stock <= 0 ? 'Out of stock' : `${s.stock} left`}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

function Todo({ to, n, label, quiet }: { to: string; n: number; label: string; quiet?: boolean }) {
  const hot = n > 0 && !quiet
  return (
    <Link to={to} className="card group flex items-center gap-3 p-4 transition hover:ring-1 hover:ring-ink/10">
      <span className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-base font-semibold tabular-nums ${hot ? 'bg-ok/10 text-ok' : 'bg-surface text-muted'}`}>{n}</span>
      <span className="min-w-0 flex-1 text-sm leading-snug text-ink/80">{label}</span>
      <span className="text-muted transition group-hover:translate-x-0.5">›</span>
    </Link>
  )
}

function Kpi({ label, value, now, before, note }: { label: string; value: string; now: number; before: number; note?: string }) {
  const change = before > 0 ? ((now - before) / before) * 100 : null
  return (
    <div className="card p-4">
      <p className="text-[13px] text-muted">{label}</p>
      <p className="mt-1.5 text-[26px] font-semibold leading-none tracking-tight tabular-nums">{value}</p>
      <div className="mt-2.5 flex flex-wrap items-center gap-1.5 text-xs">
        {change === null
          ? <span className="text-muted">{now > 0 ? 'New this period' : 'No data yet'}</span>
          : <>
              <span className={`rounded-md px-1.5 py-0.5 font-medium tabular-nums ${change >= 0 ? 'bg-ok/10 text-ok' : 'bg-bad/10 text-bad'}`}>
                {change >= 0 ? '+' : '−'}{Math.abs(change).toFixed(1)}%
              </span>
              <span className="text-muted">vs previous</span>
            </>}
      </div>
      {note && <p className="mt-1 text-xs text-muted">{note}</p>}
    </div>
  )
}

/** Smooth area chart of sales; hover (or tap) a point to see the day's numbers. */
function Bars({ points }: { points: Data['series'] }) {
  const [hover, setHover] = useState<number | null>(null)
  if (points.every((p) => p.revenue === 0)) return <p className="py-14 text-center text-sm text-muted">No sales in this period yet.</p>
  const W = 1000, H = 220, pad = 8
  const max = Math.max(...points.map((p) => p.revenue)) * 1.1
  const x = (i: number) => (points.length === 1 ? W / 2 : (i / (points.length - 1)) * W)
  const y = (v: number) => H - pad - (v / max) * (H - pad * 2)
  const line = points.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p.revenue).toFixed(1)}`).join(' ')
  const area = `${line} L${x(points.length - 1)},${H} L${x(0)},${H} Z`
  const every = Math.ceil(points.length / 8)
  const h = hover === null ? null : points[hover]
  const total = points.reduce((a, p) => a + p.revenue, 0)
  return (
    <div className="mt-4">
      <div className="mb-3 flex items-baseline gap-3">
        <p className="text-2xl font-semibold tracking-tight tabular-nums">{rupees(h ? h.revenue : total)}</p>
        <p className="text-sm text-muted">{h ? `${h.label} · ${h.orders} order${h.orders === 1 ? '' : 's'}` : 'Total in this period'}</p>
      </div>
      <div className="relative" onMouseLeave={() => setHover(null)}
           onMouseMove={(e) => { const r = e.currentTarget.getBoundingClientRect(); setHover(Math.max(0, Math.min(points.length - 1, Math.round(((e.clientX - r.left) / r.width) * (points.length - 1))))) }}>
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="h-52 w-full overflow-visible">
          <defs>
            <linearGradient id="sales-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--color-ok)" stopOpacity="0.22" />
              <stop offset="100%" stopColor="var(--color-ok)" stopOpacity="0" />
            </linearGradient>
          </defs>
          {[0.25, 0.5, 0.75].map((f) => <line key={f} x1="0" x2={W} y1={H * f} y2={H * f} stroke="var(--color-line)" strokeDasharray="4 6" vectorEffect="non-scaling-stroke" />)}
          <path d={area} fill="url(#sales-fill)" />
          <path d={line} fill="none" stroke="var(--color-ok)" strokeWidth="2" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
          {h && <line x1={x(hover!)} x2={x(hover!)} y1="0" y2={H} stroke="var(--color-ink)" strokeOpacity="0.25" vectorEffect="non-scaling-stroke" />}
        </svg>
        {h && <span className="pointer-events-none absolute h-2.5 w-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-ok shadow"
                    style={{ left: `${(x(hover!) / W) * 100}%`, top: `${(y(h.revenue) / H) * 100}%` }} />}
      </div>
      <div className="mt-2 flex justify-between text-[11px] text-muted">
        {points.map((p, i) => (i % every === 0 || i === points.length - 1) && <span key={p.date}>{p.label}</span>)}
      </div>
    </div>
  )
}

function Split({ a, b }: { a: [string, number, number]; b: [string, number, number] }) {
  const total = a[2] + b[2]
  const pa = total > 0 ? (a[2] / total) * 100 : 50
  return (
    <div className="mt-4">
      <div className="flex h-2.5 overflow-hidden rounded-full bg-surface">
        {total > 0 && <><div className="bg-ink/80" style={{ width: `${pa}%` }} /><div className="bg-ok" style={{ width: `${100 - pa}%` }} /></>}
      </div>
      <div className="mt-3 grid grid-cols-2 gap-3 text-sm">
        {[a, b].map(([label, n, v], i) => (
          <div key={label}>
            <p className="flex items-center gap-1.5 text-muted"><span className={`h-2 w-2 rounded-full ${i === 0 ? 'bg-ink/80' : 'bg-ok'}`} />{label}</p>
            <p className="font-medium tabular-nums">{rupees(v)} <span className="font-normal text-muted">· {n} orders</span></p>
          </div>
        ))}
      </div>
    </div>
  )
}

function Share({ rows }: { rows: [string, number, string][] }) {
  if (rows.length === 0) return <p className="mt-3 text-sm text-muted">No sales in this period yet.</p>
  const max = Math.max(...rows.map((r) => r[1]))
  return (
    <ul className="mt-4 space-y-3">
      {rows.map(([name, value, note]) => (
        <li key={name} className="text-sm">
          <div className="flex justify-between gap-3"><span className="truncate">{name}</span><span className="shrink-0 tabular-nums">{rupees(value)}</span></div>
          <div className="mt-1 flex items-center gap-2">
            <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface"><div className="h-full rounded-full bg-ok/70" style={{ width: `${(value / max) * 100}%` }} /></div>
            <span className="w-16 shrink-0 text-right text-xs text-muted">{note}</span>
          </div>
        </li>
      ))}
    </ul>
  )
}

function fmtDate(s: string) {
  return new Date(s + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function DownloadIcon() {
  return <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d="M12 3v12m0 0-4-4m4 4 4-4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" /></svg>
}
