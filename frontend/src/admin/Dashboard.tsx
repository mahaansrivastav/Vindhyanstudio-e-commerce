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
        <Todo to="/admin/orders?status=CONFIRMED" n={a.toPrint} label="Orders to print" />
        <Todo to="/admin/orders?status=READY" n={a.readyToShip} label="Packed, waiting for courier" />
        <Todo to="/admin/orders?status=PAYMENT_PENDING" n={a.awaitingPayment} label="Waiting for payment" quiet />
        <Todo to="/admin/products?filter=low" n={lowCount} label="Low or out of stock" />
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
          <h3 className="text-base">Sales by {d.granularity}</h3>
          <p className="text-xs text-muted">Confirmed orders, including shipping</p>
        </div>
        <Bars points={d.series} />
      </section>

      <div className="grid gap-6 lg:grid-cols-[1.4fr_1fr]">
        <section className="card overflow-hidden">
          <h3 className="px-5 pt-5 text-base">Best-selling products</h3>
          {d.topProducts.length === 0 ? <p className="p-5 text-sm text-muted">No sales in this period yet.</p> : (
            <table className="mt-3 w-full text-sm">
              <thead className="border-y border-line bg-surface text-left text-xs text-muted">
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
    <Link to={to} className={`rounded-xl border p-4 transition hover:border-ink/40 ${hot ? 'border-ink/20 bg-surface' : 'border-line'}`}>
      <p className={`text-2xl font-semibold tracking-tight ${n === 0 ? 'text-muted' : ''}`}>{n}</p>
      <p className="mt-0.5 text-sm text-muted">{label} ›</p>
    </Link>
  )
}

function Kpi({ label, value, now, before, note }: { label: string; value: string; now: number; before: number; note?: string }) {
  const change = before > 0 ? ((now - before) / before) * 100 : null
  return (
    <div className="card p-4">
      <p className="text-sm text-muted">{label}</p>
      <p className="mt-1 text-2xl font-semibold tracking-tight tabular-nums">{value}</p>
      <p className="mt-1 text-xs text-muted">
        {change === null ? (now > 0 ? 'No sales in previous period' : '—') : (
          <><span className={change >= 0 ? 'text-ok' : 'text-bad'}>{change >= 0 ? '▲' : '▼'} {Math.abs(change).toFixed(0)}%</span> vs previous period</>
        )}
      </p>
      {note && <p className="text-xs text-muted">{note}</p>}
    </div>
  )
}

function Bars({ points }: { points: Data['series'] }) {
  const max = Math.max(1, ...points.map((p) => p.revenue))
  const every = Math.ceil(points.length / 12)
  if (points.every((p) => p.revenue === 0)) return <p className="py-10 text-center text-sm text-muted">No sales in this period yet.</p>
  return (
    <div className="mt-5">
      <div className="flex h-48 items-end gap-[3px]">
        {points.map((p) => (
          <div key={p.date} className="group relative flex h-full flex-1 items-end">
            <div className="w-full rounded-t-[3px] bg-ink/80 transition group-hover:bg-ink" style={{ height: `${Math.max(p.revenue > 0 ? 2 : 0, (p.revenue / max) * 100)}%` }} />
            <div className="pointer-events-none absolute bottom-full left-1/2 z-10 mb-1 hidden -translate-x-1/2 whitespace-nowrap rounded-md bg-ink px-2 py-1 text-xs text-white group-hover:block">
              {p.label}: {rupees(p.revenue)} · {p.orders} order{p.orders === 1 ? '' : 's'}
            </div>
          </div>
        ))}
      </div>
      <div className="mt-2 flex gap-[3px] border-t border-line pt-1.5 text-[10px] text-muted">
        {points.map((p, i) => <span key={p.date} className="flex-1 truncate text-center">{i % every === 0 ? p.label : ''}</span>)}
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
        {total > 0 && <><div className="bg-ink" style={{ width: `${pa}%` }} /><div className="bg-accent" style={{ width: `${100 - pa}%` }} /></>}
      </div>
      <div className="mt-3 grid grid-cols-2 gap-3 text-sm">
        {[a, b].map(([label, n, v], i) => (
          <div key={label}>
            <p className="flex items-center gap-1.5 text-muted"><span className={`h-2 w-2 rounded-full ${i === 0 ? 'bg-ink' : 'bg-accent'}`} />{label}</p>
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
            <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-surface"><div className="h-full rounded-full bg-ink/70" style={{ width: `${(value / max) * 100}%` }} /></div>
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
