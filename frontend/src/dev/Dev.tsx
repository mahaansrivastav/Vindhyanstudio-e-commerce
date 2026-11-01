// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useMemo, useState } from 'react'
import { Link, NavLink, Route, Routes } from 'react-router-dom'
import { DownloadIcon, range } from '../admin/Dashboard'
import { Empty, ErrorBox, ProductImage, Spinner } from '../components/ui'
import { api, downloadFile } from '../lib/api'
import { rupees } from '../lib/format'
import type { Count, LoginEvent, UsageOverview, VisitorRow, VisitRow } from '../lib/types'
import { useAsync } from '../lib/useAsync'
import { useAuth } from '../state/AppState'

type Preset = 'today' | '7d' | '30d' | 'month' | 'quarter' | 'custom'
const PRESETS: [Preset, string][] = [['today', 'Today'], ['7d', '7 days'], ['30d', '30 days'], ['month', 'This month'], ['quarter', 'This quarter'], ['custom', 'Custom']]

/**
 * Platform usage console. Developers (DEVELOPER_IDENTITIES) see everything, including logins with IPs;
 * analytics viewers (ANALYTICS_IDENTITIES) see only the aggregate overview. Nobody else can open it.
 */
export function Dev() {
  const { profile, signOut } = useAuth()
  const [preset, setPreset] = useState<Preset>('30d')
  const [[from, to], setRange] = useState<[string, string]>(range('30d'))
  if (!profile?.developer && !profile?.analytics) return <Empty title="Page not found"><Link to="/" className="underline">Go home</Link></Empty>
  const dev = !!profile.developer

  const nav: [string, string, boolean?][] = [['/dev', 'Overview', true], ...(dev ? [['/dev/logins', 'Logins'], ['/dev/visitors', 'Visitors'], ['/dev/visits', 'Page views']] as [string, string][] : [])]
  const item = ({ isActive }: { isActive: boolean }) => `shrink-0 rounded-lg px-3 py-2 text-[14px] transition ${isActive ? 'bg-ok/10 font-medium text-ok' : 'text-ink/70 hover:bg-surface hover:text-ink'}`
  const period = (
    <div className="space-y-2">
      <div className="scrollbar-none -mx-4 flex gap-2 overflow-x-auto px-4 sm:mx-0 sm:px-0">
        {PRESETS.map(([p, label]) => <button key={p} onClick={() => { setPreset(p); if (p !== 'custom') setRange(range(p)) }} className={`chip shrink-0 ${preset === p ? 'chip-on' : ''}`}>{label}</button>)}
      </div>
      {preset === 'custom' && (
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <input type="date" className="input w-auto" value={from} max={to} onChange={(e) => e.target.value && setRange([e.target.value, to])} aria-label="From" />
          <span className="text-muted">to</span>
          <input type="date" className="input w-auto" value={to} min={from} onChange={(e) => e.target.value && setRange([from, e.target.value])} aria-label="To" />
        </div>
      )}
    </div>
  )

  return (
    <div className="admin min-h-screen lg:grid lg:grid-cols-[220px_minmax(0,1fr)]">
      <aside className="sticky top-0 z-30 border-b border-line bg-white lg:h-screen lg:border-b-0 lg:border-r">
        <div className="flex items-center gap-2.5 px-5 py-4">
          <span className="flex h-7 w-7 items-center justify-center rounded-md bg-ink text-xs font-semibold text-white">{'</>'}</span>
          <div className="leading-tight">
            <p className="text-[15px] font-semibold tracking-tight">{dev ? 'Developer' : 'Analytics'}</p>
            <p className="text-xs text-muted">Platform usage</p>
          </div>
          <Link to="/" className="ml-auto text-xs text-muted hover:text-ink lg:hidden">Store ↗</Link>
        </div>
        <nav className="scrollbar-none flex gap-1 overflow-x-auto px-3 pb-3 lg:flex-col">
          {nav.map(([to, label, end]) => <NavLink key={to} to={to} end={end} className={item}>{label}</NavLink>)}
        </nav>
        <div className="absolute inset-x-0 bottom-0 hidden border-t border-line p-4 text-sm lg:block">
          <Link to="/" className="flex justify-between rounded-lg px-2 py-1.5 text-ink/70 hover:bg-surface">View store <span>↗</span></Link>
          {profile.admin && <Link to="/admin" className="flex justify-between rounded-lg px-2 py-1.5 text-ink/70 hover:bg-surface">Admin <span>↗</span></Link>}
          <p className="mt-3 truncate px-2 text-xs text-muted">{profile.email ?? profile.phone}</p>
          <button onClick={signOut} className="px-2 text-xs text-muted underline">Sign out</button>
        </div>
      </aside>
      <main className="min-w-0 px-4 py-6 sm:px-8 lg:py-8">
        <div className="mx-auto max-w-[1240px] space-y-6">
          <Routes>
            <Route index element={<Overview from={from} to={to} period={period} dev={dev} />} />
            {dev && <Route path="logins" element={<Logins from={from} to={to} period={period} />} />}
            {dev && <Route path="visitors" element={<Visitors from={from} to={to} period={period} />} />}
            {dev && <Route path="visits" element={<Visits from={from} to={to} period={period} />} />}
          </Routes>
        </div>
      </main>
    </div>
  )
}

// ---------------- Overview ----------------

function Overview({ from, to, period, dev }: { from: string; to: string; period: React.ReactNode; dev: boolean }) {
  const [internal, setInternal] = useState(false)
  const { data: d, error, loading, reload } = useAsync(() => api<UsageOverview>(`/api/analytics/overview?from=${from}&to=${to}&internal=${internal}`), [from, to, internal])
  const [busy, setBusy] = useState(false)
  const exportXlsx = async () => {
    setBusy(true)
    try { await downloadFile(`/api/dev/export?from=${from}&to=${to}`, `usage-${from}-to-${to}.xlsx`) } catch (e) { alert((e as Error).message) } finally { setBusy(false) }
  }

  return (
    <>
      <div className="flex flex-wrap items-end gap-3">
        <div className="mr-auto">
          <h1 className="text-2xl">Usage overview</h1>
          <p className="text-sm text-muted">{from} → {to} · {internal ? 'all traffic' : 'shop visitors only'}</p>
        </div>
        {dev && (
          <label className="flex items-center gap-2 text-sm text-muted">
            <input type="checkbox" className="accent-black" checked={internal} onChange={(e) => setInternal(e.target.checked)} /> Include admin and developer pages
          </label>
        )}
        {dev && <button className="btn-outline" onClick={exportXlsx} disabled={busy}><DownloadIcon /> {busy ? 'Preparing…' : 'Export Excel'}</button>}
      </div>
      {period}
      {error && <ErrorBox message={error} onRetry={reload} />}
      {loading && !d && <Spinner />}
      {d && (
        <div className={`space-y-6 ${loading ? 'opacity-60' : ''}`}>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-7">
            <Kpi label="Visitors" now={d.kpis.visitors} before={d.previous.visitors} />
            <Kpi label="Page views" now={d.kpis.views} before={d.previous.views} />
            <Kpi label="Sessions" now={d.kpis.sessions} before={d.previous.sessions} />
            <Kpi label="Logins" now={d.kpis.logins} before={d.previous.logins} note={d.kpis.failedLogins ? `${d.kpis.failedLogins} failed` : undefined} />
            <Kpi label="New sign-ups" now={d.kpis.signups} before={d.previous.signups} />
            <Kpi label="Orders" now={d.kpis.orders} before={d.previous.orders} note={rupees(d.kpis.revenue)} />
            <Kpi label="Visitor → order" now={d.kpis.conversion} before={d.previous.conversion} suffix="%" />
          </div>

          <section className="card p-5">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h2 className="text-base">Traffic</h2>
              <p className="flex items-center gap-4 text-xs text-muted"><Legend className="bg-ok" label="Visitors" /><Legend className="bg-ink/25" label="Page views" /></p>
            </div>
            <Traffic points={d.series} />
          </section>

          <section className="card overflow-hidden">
            <h2 className="px-5 pt-5 text-base">Most viewed products</h2>
            {d.products.length === 0 ? <p className="p-5 text-sm text-muted">No product views yet.</p> : (
              <div className="overflow-x-auto">
                <table className="mt-3 w-full min-w-[560px] text-sm">
                  <thead className="border-y border-line bg-surface/70 text-left text-xs text-muted">
                    <tr><th className="px-5 py-2 font-medium">Product</th><th className="px-3 py-2 text-right font-medium">Views</th><th className="px-3 py-2 text-right font-medium">Visitors</th><th className="px-3 py-2 text-right font-medium">Ordered</th><th className="px-5 py-2 text-right font-medium">View → order</th></tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {d.products.map((p) => (
                      <tr key={p.sku}>
                        <td className="px-5 py-2.5"><span className="flex items-center gap-3"><ProductImage src={p.image} alt="" className="h-9 w-9 shrink-0 rounded-md" /><span className="min-w-0"><span className="block truncate">{p.name ?? p.sku}</span><span className="text-xs text-muted">{p.sku}</span></span></span></td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{p.views}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{p.visitors}</td>
                        <td className="px-3 py-2.5 text-right tabular-nums">{p.ordered}</td>
                        <td className="px-5 py-2.5 text-right tabular-nums">{p.visitors ? `${Math.min(100, Math.round((p.ordered / p.visitors) * 1000) / 10)}%` : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            <Bars title="Where visitors came from" rows={d.sources} unit="sessions" />
            <Bars title="Devices" rows={d.devices} unit="visitors" />
            <Bars title="Region (time zone)" rows={d.regions} unit="visitors" />
            <Bars title="Browsers" rows={d.browsers} unit="visitors" />
            <Bars title="Operating systems" rows={d.os} unit="visitors" />
            <Bars title="Languages" rows={d.languages} unit="visitors" />
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
            <section className="card overflow-hidden">
              <h2 className="px-5 pt-5 text-base">Top pages</h2>
              <ul className="mt-3 divide-y divide-line text-sm">
                {d.pages.map((p) => (
                  <li key={p.path} className="flex items-center gap-3 px-5 py-2">
                    <span className="min-w-0 flex-1 truncate font-mono text-xs">{p.path}</span>
                    <span className="w-14 text-right tabular-nums">{p.views}</span>
                    <span className="w-20 text-right text-xs text-muted tabular-nums">{p.visitors} visitors</span>
                  </li>
                ))}
                {d.pages.length === 0 && <li className="px-5 py-4 text-muted">No visits yet.</li>}
              </ul>
            </section>
            <section className="card p-5">
              <h2 className="text-base">Busiest hours (IST)</h2>
              <Hours hours={d.hours} />
              {dev && <p className="mt-4 text-xs text-muted">Traffic by area: {d.areas.map((a) => `${a.name.toLowerCase()} ${a.count}`).join(' · ') || '—'}</p>}
            </section>
          </div>
          <p className="text-xs text-muted">First-party analytics: collected by this site only, no third-party trackers. Raw visits and logins are deleted after the retention period.</p>
        </div>
      )}
    </>
  )
}

function Kpi({ label, now, before, note, suffix = '' }: { label: string; now: number; before: number; note?: string; suffix?: string }) {
  const change = before > 0 ? ((now - before) / before) * 100 : null
  return (
    <div className="card p-4">
      <p className="text-[13px] text-muted">{label}</p>
      <p className="mt-1.5 text-2xl font-semibold leading-none tracking-tight tabular-nums">{now.toLocaleString('en-IN')}{suffix}</p>
      <p className="mt-2 text-xs">
        {change === null ? <span className="text-muted">{note ?? '—'}</span> : (
          <><span className={`rounded-md px-1.5 py-0.5 font-medium ${change >= 0 ? 'bg-ok/10 text-ok' : 'bg-bad/10 text-bad'}`}>{change >= 0 ? '+' : '−'}{Math.abs(change).toFixed(0)}%</span>{note && <span className="ml-1.5 text-muted">{note}</span>}</>
        )}
      </p>
    </div>
  )
}

function Legend({ className, label }: { className: string; label: string }) {
  return <span className="flex items-center gap-1.5"><span className={`h-2 w-2 rounded-full ${className}`} />{label}</span>
}

function Traffic({ points }: { points: UsageOverview['series'] }) {
  const [hover, setHover] = useState<number | null>(null)
  if (points.every((p) => p.views === 0)) return <p className="py-12 text-center text-sm text-muted">No visits in this period yet.</p>
  const W = 1000, H = 200
  const max = Math.max(...points.map((p) => p.views)) * 1.1
  const x = (i: number) => (points.length === 1 ? W / 2 : (i / (points.length - 1)) * W)
  const y = (v: number) => H - (v / max) * (H - 8)
  const path = (k: 'views' | 'visitors') => points.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p[k]).toFixed(1)}`).join(' ')
  const h = hover === null ? null : points[hover]
  const every = Math.ceil(points.length / 8)
  return (
    <div className="mt-4">
      <p className="mb-2 h-5 text-sm text-muted">{h ? <><b className="text-ink">{h.label}</b> · {h.visitors} visitors · {h.views} page views</> : 'Hover the chart for daily numbers'}</p>
      <div className="relative" onMouseLeave={() => setHover(null)}
           onMouseMove={(e) => { const r = e.currentTarget.getBoundingClientRect(); setHover(Math.max(0, Math.min(points.length - 1, Math.round(((e.clientX - r.left) / r.width) * (points.length - 1))))) }}>
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="h-48 w-full overflow-visible">
          <defs><linearGradient id="vis" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="var(--color-ok)" stopOpacity="0.2" /><stop offset="100%" stopColor="var(--color-ok)" stopOpacity="0" /></linearGradient></defs>
          <path d={`${path('visitors')} L${x(points.length - 1)},${H} L${x(0)},${H} Z`} fill="url(#vis)" />
          <path d={path('views')} fill="none" stroke="var(--color-ink)" strokeOpacity="0.25" strokeWidth="1.5" strokeDasharray="5 5" vectorEffect="non-scaling-stroke" />
          <path d={path('visitors')} fill="none" stroke="var(--color-ok)" strokeWidth="2" vectorEffect="non-scaling-stroke" />
          {h && <line x1={x(hover!)} x2={x(hover!)} y1="0" y2={H} stroke="var(--color-ink)" strokeOpacity="0.2" vectorEffect="non-scaling-stroke" />}
        </svg>
      </div>
      <div className="mt-2 flex justify-between text-[11px] text-muted">
        {points.map((p, i) => (i % every === 0 || i === points.length - 1) && <span key={p.date}>{p.label}</span>)}
      </div>
    </div>
  )
}

function Bars({ title, rows, unit }: { title: string; rows: Count[]; unit: string }) {
  const max = Math.max(1, ...rows.map((r) => r.count))
  return (
    <section className="card p-5">
      <h2 className="text-base">{title}</h2>
      {rows.length === 0 ? <p className="mt-3 text-sm text-muted">No data yet.</p> : (
        <ul className="mt-4 space-y-2.5">
          {rows.slice(0, 8).map((r) => (
            <li key={r.name} className="text-sm">
              <div className="flex justify-between gap-3"><span className="truncate capitalize">{r.name}</span><span className="shrink-0 tabular-nums text-muted">{r.count} {unit}</span></div>
              <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-surface"><div className="h-full rounded-full bg-ok/70" style={{ width: `${(r.count / max) * 100}%` }} /></div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function Hours({ hours }: { hours: number[] }) {
  const max = Math.max(1, ...hours)
  return (
    <div className="mt-4">
      <div className="flex h-28 items-end gap-[3px]">
        {hours.map((n, i) => <div key={i} title={`${i}:00 – ${n} views`} className="flex-1 rounded-t-sm bg-ok/70" style={{ height: `${Math.max(n ? 4 : 0, (n / max) * 100)}%` }} />)}
      </div>
      <div className="mt-1.5 flex justify-between text-[10px] text-muted"><span>0</span><span>6</span><span>12</span><span>18</span><span>23</span></div>
    </div>
  )
}

// ---------------- Developer-only lists ----------------

function useFilter<T>(rows: T[] | undefined, fields: (r: T) => (string | null | undefined)[]) {
  const [q, setQ] = useState('')
  const out = useMemo(() => {
    const n = q.trim().toLowerCase()
    return (rows ?? []).filter((r) => !n || fields(r).some((f) => f?.toLowerCase().includes(n)))
  }, [rows, q, fields])
  return { q, setQ, out }
}

function Page({ title, hint, period, q, setQ, count, children }: { title: string; hint: string; period: React.ReactNode; q: string; setQ: (v: string) => void; count: number; children: React.ReactNode }) {
  return (
    <>
      <div><h1 className="text-2xl">{title}</h1><p className="text-sm text-muted">{hint}</p></div>
      {period}
      <div className="flex flex-wrap items-center gap-3">
        <input className="input max-w-sm" placeholder="Search email, IP, page…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search" />
        <span className="text-sm text-muted">{count} rows</span>
      </div>
      {children}
    </>
  )
}

function Table({ head, children }: { head: string[]; children: React.ReactNode }) {
  return (
    <div className="card overflow-x-auto">
      <table className="w-full min-w-[860px] text-left text-sm">
        <thead className="border-b border-line bg-surface/70 text-xs text-muted"><tr>{head.map((h) => <th key={h} className="px-3 py-2 font-medium">{h}</th>)}</tr></thead>
        <tbody className="divide-y divide-line">{children}</tbody>
      </table>
    </div>
  )
}

const loginFields = (r: LoginEvent) => [r.identity, r.name, r.ip, r.browser, r.reason]
function Logins({ from, to, period }: { from: string; to: string; period: React.ReactNode }) {
  const { data, error, loading } = useAsync(() => api<LoginEvent[]>(`/api/dev/logins?from=${from}&to=${to}`), [from, to])
  const f = useFilter(data, loginFields)
  return (
    <Page title="Logins" hint="Every sign-in attempt, newest first (max 1,000)." period={period} q={f.q} setQ={f.setQ} count={f.out.length}>
      {error && <ErrorBox message={error} />}
      {loading && !data ? <Spinner /> : (
        <Table head={['Time (IST)', 'Email / mobile', 'Name', 'Result', 'IP', 'Device', 'Browser / OS']}>
          {f.out.map((r, i) => (
            <tr key={i}>
              <td className="whitespace-nowrap px-3 py-2 tabular-nums">{r.at}</td>
              <td className="px-3 py-2">{r.identity}</td>
              <td className="px-3 py-2">{r.name ?? '—'}</td>
              <td className="px-3 py-2">{r.success ? <span className="text-ok">Signed in{r.newAccount ? ' · new' : ''}</span> : <span className="text-bad" title={r.reason ?? ''}>Failed</span>}</td>
              <td className="px-3 py-2 font-mono text-xs">{r.ip ?? '—'}</td>
              <td className="px-3 py-2 capitalize">{r.device}</td>
              <td className="px-3 py-2">{r.browser} / {r.os}</td>
            </tr>
          ))}
        </Table>
      )}
    </Page>
  )
}

const visitorFields = (r: VisitorRow) => [r.customer, r.ip, r.source, r.browser, r.timezone, r.visitor]
function Visitors({ from, to, period }: { from: string; to: string; period: React.ReactNode }) {
  const { data, error, loading } = useAsync(() => api<VisitorRow[]>(`/api/dev/visitors?from=${from}&to=${to}`), [from, to])
  const f = useFilter(data, visitorFields)
  return (
    <Page title="Visitors" hint="One row per browser (device), most recent first." period={period} q={f.q} setQ={f.setQ} count={f.out.length}>
      {error && <ErrorBox message={error} />}
      {loading && !data ? <Spinner /> : (
        <Table head={['Last seen', 'First seen', 'Customer', 'Views', 'Sessions', 'IP', 'Device', 'Source', 'Time zone']}>
          {f.out.map((r) => (
            <tr key={r.visitor}>
              <td className="whitespace-nowrap px-3 py-2 tabular-nums">{r.lastSeen}</td>
              <td className="whitespace-nowrap px-3 py-2 tabular-nums text-muted">{r.firstSeen}</td>
              <td className="px-3 py-2">{r.customer ?? <span className="text-muted">guest</span>}</td>
              <td className="px-3 py-2 tabular-nums">{r.views}</td>
              <td className="px-3 py-2 tabular-nums">{r.sessions}</td>
              <td className="px-3 py-2 font-mono text-xs">{r.ip ?? '—'}</td>
              <td className="px-3 py-2 capitalize">{r.device} · {r.browser}</td>
              <td className="px-3 py-2">{r.source ?? 'direct'}</td>
              <td className="px-3 py-2 text-muted">{r.timezone ?? '—'}</td>
            </tr>
          ))}
        </Table>
      )}
    </Page>
  )
}

const visitFields = (r: VisitRow) => [r.path, r.customer, r.ip, r.source, r.browser]
function Visits({ from, to, period }: { from: string; to: string; period: React.ReactNode }) {
  const { data, error, loading } = useAsync(() => api<VisitRow[]>(`/api/dev/visits?from=${from}&to=${to}`), [from, to])
  const f = useFilter(data, visitFields)
  return (
    <Page title="Page views" hint="Raw page views, newest first (max 1,000). Export Excel from Overview for more." period={period} q={f.q} setQ={f.setQ} count={f.out.length}>
      {error && <ErrorBox message={error} />}
      {loading && !data ? <Spinner /> : (
        <Table head={['Time (IST)', 'Page', 'Area', 'Customer', 'IP', 'Device', 'Source']}>
          {f.out.map((r, i) => (
            <tr key={i}>
              <td className="whitespace-nowrap px-3 py-2 tabular-nums">{r.at}</td>
              <td className="max-w-[260px] truncate px-3 py-2 font-mono text-xs">{r.path}</td>
              <td className="px-3 py-2 text-xs">{r.area.toLowerCase()}</td>
              <td className="px-3 py-2">{r.customer ?? <span className="text-muted">guest</span>}</td>
              <td className="px-3 py-2 font-mono text-xs">{r.ip ?? '—'}</td>
              <td className="px-3 py-2 capitalize">{r.device} · {r.browser}</td>
              <td className="px-3 py-2">{r.source ?? '—'}</td>
            </tr>
          ))}
        </Table>
      )}
    </Page>
  )
}
