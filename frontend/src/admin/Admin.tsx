// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useState } from 'react'
import { Link, Navigate, NavLink, Route, Routes, useParams, useSearchParams } from 'react-router-dom'
import { ErrorBox, PrivateImage, ProductImage, Spinner, StatusBadge } from '../components/ui'
import { api, openBlob } from '../lib/api'
import { dateTime, describeCustomisation, rupees, STATUS_LABEL } from '../lib/format'
import type { OrderStatus, OrderSummary, OrderView } from '../lib/types'
import { useAsync } from '../lib/useAsync'
import { useAuth } from '../state/AppState'
import { Dashboard } from './Dashboard'
import { Import } from './Import'
import { Categories, ProductEditor, ProductList } from './Products'

const ACTION_LABEL: Partial<Record<OrderStatus, string>> = {
  PRINTING: 'Start printing',
  READY: 'Packed — book courier',
  DISPATCHED: 'Handed to courier',
  DELIVERED: 'Mark delivered',
  RTO: 'Mark returned (RTO)',
  CANCELLED: 'Cancel order',
}

const NAV: [string, string, React.ReactNode, boolean?][] = [
  ['/admin', 'Dashboard', <path key="d" d="M4 13h6V4H4zm10 7h6v-9h-6zM4 20h6v-4H4zm10-11h6V4h-6z" />, true],
  ['/admin/orders', 'Orders', <path key="o" d="M6 3h12l2 4v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7zM4 7h16M9 11h6" />],
  ['/admin/products', 'Products & stock', <path key="p" d="M3 7.5 12 3l9 4.5v9L12 21l-9-4.5zM3 7.5l9 4.5 9-4.5M12 12v9" />],
  ['/admin/categories', 'Categories', <path key="c" d="M4 4h7v7H4zm9 0h7v7h-7zM4 13h7v7H4zm9 3.5a3.5 3.5 0 1 0 7 0 3.5 3.5 0 0 0-7 0" />],
  ['/admin/import', 'Update from Excel', <path key="e" d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8zM14 3v5h5M9 13l2 2 4-4" />],
]

/** Owner console: its own full-screen layout (sidebar on desktop, tabs on mobile), separate from the shop. */
export function Admin() {
  const { profile, signOut } = useAuth()
  const item = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-3 rounded-lg px-3 py-2 text-[14px] transition ${isActive ? 'bg-ok/10 font-medium text-ok' : 'text-ink/70 hover:bg-surface hover:text-ink'}`
  return (
    <div className="admin min-h-screen lg:grid lg:grid-cols-[240px_1fr]">
      <aside className="sticky top-0 z-30 border-b border-line bg-white lg:h-screen lg:border-b-0 lg:border-r">
        <div className="flex items-center gap-2.5 px-5 py-4">
          <img src="/brand/logo-mark.png" alt="" className="h-7 w-auto" />
          <div className="leading-tight">
            <p className="text-[15px] font-semibold tracking-tight">Vindhyan Studio</p>
            <p className="text-xs text-muted">Admin</p>
          </div>
          <Link to="/" className="ml-auto text-xs text-muted hover:text-ink lg:hidden">Store ↗</Link>
        </div>
        <nav className="scrollbar-none flex gap-1 overflow-x-auto px-3 pb-3 lg:flex-col lg:overflow-visible lg:pb-0">
          {NAV.map(([to, label, icon, end]) => (
            <NavLink key={to} to={to} end={end} className={(a) => `${item(a)} shrink-0`}>
              <svg viewBox="0 0 24 24" className="h-[18px] w-[18px] shrink-0" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>{icon}</svg>
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="absolute inset-x-0 bottom-0 hidden border-t border-line p-4 text-sm lg:block">
          <Link to="/" className="flex items-center justify-between rounded-lg px-2 py-1.5 text-ink/70 hover:bg-surface hover:text-ink">View store <span>↗</span></Link>
          <p className="mt-3 truncate px-2 text-xs text-muted">{profile?.email ?? profile?.phone}</p>
          <button onClick={signOut} className="px-2 text-xs text-muted underline hover:text-ink">Sign out</button>
        </div>
      </aside>
      <main className="min-w-0 px-4 py-6 sm:px-8 lg:py-8">
        <div className="mx-auto max-w-[1240px]">
          <Routes>
            <Route index element={<Dashboard />} />
            <Route path="orders" element={<Orders />} />
            <Route path="orders/:id" element={<AdminOrder />} />
            <Route path="products" element={<ProductList />} />
            <Route path="products/new" element={<ProductEditor />} />
            <Route path="products/:sku" element={<ProductEditor />} />
            <Route path="categories" element={<Categories />} />
            <Route path="import" element={<Import />} />
            <Route path="catalogue" element={<Navigate to="/admin/import" replace />} />
          </Routes>
        </div>
      </main>
    </div>
  )
}

const FILTERS: (OrderStatus | '')[] = ['', 'CONFIRMED', 'PRINTING', 'READY', 'DISPATCHED', 'DELIVERED', 'PAYMENT_PENDING', 'CANCELLED', 'RTO']

function Orders() {
  const [params, setParams] = useSearchParams()
  const status = (params.get('status') ?? '') as OrderStatus | ''
  const summary = useAsync(() => api<{ byStatus: Record<OrderStatus, number>; ordersToday: number; revenueToday: number }>('/api/admin/summary'), [])
  const orders = useAsync(() => api<OrderSummary[]>(`/api/admin/orders${status ? `?status=${status}` : ''}`), [status])
  const s = summary.data

  return (
    <div className="space-y-5">
      <h2 className="text-2xl">Orders</h2>
      {s && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Stat label="To print" value={s.byStatus.CONFIRMED + s.byStatus.PRINTING} highlight />
          <Stat label="Ready to hand over" value={s.byStatus.READY} />
          <Stat label="Orders today" value={s.ordersToday} />
          <Stat label="Revenue today" value={rupees(s.revenueToday)} />
        </div>
      )}
      <div className="-mx-4 flex gap-2 overflow-x-auto px-4">
        {FILTERS.map((f) => (
          <button key={f} onClick={() => setParams(f ? { status: f } : {})} className={`chip shrink-0 ${status === f ? 'chip-on' : ''}`}>
            {f ? STATUS_LABEL[f] : 'All'}{f && s ? ` (${s.byStatus[f]})` : ''}
          </button>
        ))}
      </div>
      {orders.loading && <Spinner />}
      {orders.error && <ErrorBox message={orders.error} onRetry={orders.reload} />}
      {orders.data && (
        <div className="card overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-line bg-surface text-xs text-muted">
              <tr><th className="p-3">Order</th><th className="p-3">Customer</th><th className="p-3">Items</th><th className="p-3">Total</th><th className="p-3">Status</th></tr>
            </thead>
            <tbody className="divide-y divide-line">
              {orders.data.length === 0 && <tr><td colSpan={5} className="p-6 text-center text-muted">No orders here.</td></tr>}
              {orders.data.map((o) => (
                <tr key={o.id} className="hover:bg-surface/60">
                  <td className="p-3"><Link to={`/admin/orders/${o.id}`} className="font-medium text-ink hover:underline">{o.orderNo}</Link><br /><span className="text-xs text-muted">{dateTime(o.createdAt)}</span></td>
                  <td className="p-3">{o.customerName ?? '—'}<br /><span className="text-xs text-muted">{o.city}</span></td>
                  <td className="p-3">{o.firstItem}{o.itemCount > 1 ? ` +${o.itemCount - 1}` : ''}{o.customised && <span className="ml-1 rounded bg-brand-soft px-1.5 text-xs text-brand">custom</span>}</td>
                  <td className="p-3">{rupees(o.total)}<br /><span className="text-xs text-muted">{o.paymentMode}</span></td>
                  <td className="p-3"><StatusBadge status={o.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function Stat({ label, value, highlight }: { label: string; value: string | number; highlight?: boolean }) {
  return (
    <div className="card p-4">
      <p className="flex items-center gap-1.5 text-sm text-muted">{highlight && Number(value) > 0 && <span className="h-1.5 w-1.5 rounded-full bg-ok" />}{label}</p>
      <p className="mt-1 text-2xl font-semibold tracking-tight tabular-nums">{value}</p>
    </div>
  )
}

function AdminOrder() {
  const { id } = useParams()
  const { data: o, error, loading, reload, setData } = useAsync(() => api<OrderView>(`/api/admin/orders/${id}`), [id])
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  if (loading && !o) return <Spinner />
  if (error || !o) return <ErrorBox message={error ?? 'Not found'} onRetry={reload} />

  const act = async (status: OrderStatus) => {
    if (status === 'CANCELLED' && !confirm('Cancel this order? If the customer paid online, the money is refunded automatically.')) return
    setErr(null)
    setBusy(true)
    try { setData(await api<OrderView>(`/api/admin/orders/${o.id}/status`, { body: { status } })) } catch (e) { setErr((e as Error).message) } finally { setBusy(false) }
  }
  const retryShipment = async () => {
    setBusy(true)
    try { setData(await api<OrderView>(`/api/admin/orders/${o.id}/shipment/retry`, { method: 'POST' })) } catch (e) { setErr((e as Error).message) } finally { setBusy(false) }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <Link to="/admin/orders" className="text-sm text-muted">← Orders</Link>
          <h2 className="text-2xl font-semibold">{o.orderNo}</h2>
          <StatusBadge status={o.status} />
        </div>
        <section className="card divide-y divide-line">
          {o.items.map((i, idx) => (
            <div key={idx} className="flex gap-4 p-4 text-sm">
              <ProductImage src={i.image} alt={i.name} className="h-16 w-16 shrink-0 rounded-lg" />
              <div className="flex-1">
                <p className="font-semibold">{i.name} <span className="font-normal text-muted">({i.sku})</span> × {i.qty}</p>
                {describeCustomisation(i.customisation).map((d) => <p key={d} className="font-medium text-brand-dark">{d}</p>)}
                {i.customisation?.photos?.length ? (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {i.customisation.photos.map((k) => <PrivateImage key={k} storageKey={k} className="h-24 w-24 rounded-lg object-cover" />)}
                  </div>
                ) : null}
              </div>
              <span className="font-semibold">{rupees(i.lineTotal)}</span>
            </div>
          ))}
          <div className="flex justify-between p-4 text-sm"><span className="text-muted">Total ({o.paymentMode}{o.paymentMode === 'COD' ? ' — collect on delivery' : ''})</span><b>{rupees(o.total)}</b></div>
        </section>
        <section className="card p-4">
          <h3 className="label">History</h3>
          <ol className="space-y-2 text-sm">
            {o.events.map((e, i) => (
              <li key={i}><b>{STATUS_LABEL[e.status]}</b> <span className="text-muted">· {dateTime(e.at)} · {e.actor}</span>{e.note && <p className="text-muted">{e.note}</p>}</li>
            ))}
          </ol>
        </section>
      </div>

      <aside className="space-y-4">
        <section className="card space-y-2 p-4">
          <h3 className="label">Next step</h3>
          {o.nextStatuses?.length ? o.nextStatuses.map((s) => (
            <button key={s} disabled={busy} onClick={() => act(s)} className={s === 'CANCELLED' || s === 'RTO' ? 'btn-ghost w-full text-bad' : 'btn-primary w-full'}>
              {ACTION_LABEL[s] ?? s}
            </button>
          )) : <p className="text-sm text-muted">Nothing left to do.</p>}
          {o.status === 'PAYMENT_PENDING' && <p className="text-xs text-muted">Waiting for the customer to pay.</p>}
          {err && <p className="text-sm text-bad">{err}</p>}
        </section>
        <section className="card space-y-1 p-4 text-sm">
          <h3 className="label">Customer</h3>
          <p className="font-semibold">{o.address.name}</p>
          <p><a className="underline" href={`tel:+91${o.address.phone}`}>+91 {o.address.phone}</a>{o.customer?.email && <> · {o.customer.email}</>}</p>
          <p className="text-muted">{o.address.line1}{o.address.line2 ? `, ${o.address.line2}` : ''}, {o.address.city}, {o.address.state} – {o.address.pincode}</p>
        </section>
        <section className="card space-y-2 p-4 text-sm">
          <h3 className="label">Shipment</h3>
          {o.shipment ? (
            <>
              <p>Status: <b>{o.shipment.status}</b></p>
              {o.shipment.awb && <p>{o.shipment.courier} · AWB <span className="font-mono">{o.shipment.awb}</span></p>}
              {o.shipment.labelUrl && <a href={o.shipment.labelUrl} target="_blank" rel="noreferrer" className="btn-outline w-full">Print shipping label ↗</a>}
              {o.shipment.trackingUrl && <a href={o.shipment.trackingUrl} target="_blank" rel="noreferrer" className="block text-brand underline">Tracking page ↗</a>}
              {o.shipment.error && <p className="text-bad">{o.shipment.error}</p>}
              {o.shipment.status === 'CREATE_FAILED' && <button className="btn-outline w-full" onClick={retryShipment} disabled={busy}>Retry creating shipment</button>}
            </>
          ) : <p className="text-muted">Created automatically after payment.</p>}
        </section>
        <section className="card p-4 text-sm">
          <h3 className="label">Invoice</h3>
          {o.invoiceNo ? <button className="btn-outline w-full" onClick={() => openBlob(`/api/orders/${o.id}/invoice`).catch((e) => alert(e.message))}>Open {o.invoiceNo}</button> : <p className="text-muted">Not generated yet.</p>}
        </section>
      </aside>
    </div>
  )
}
