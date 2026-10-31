import { useState } from 'react'
import { Link, NavLink, Route, Routes, useParams, useSearchParams } from 'react-router-dom'
import { ErrorBox, PrivateImage, ProductImage, Spinner, StatusBadge } from '../components/ui'
import { api, openBlob } from '../lib/api'
import { dateTime, describeCustomisation, rupees, STATUS_LABEL } from '../lib/format'
import type { OrderStatus, OrderSummary, OrderView, SyncRun } from '../lib/types'
import { useAsync } from '../lib/useAsync'
import { Categories, ProductEditor, ProductList } from './Products'

const ACTION_LABEL: Partial<Record<OrderStatus, string>> = {
  PRINTING: 'Start printing',
  READY: 'Packed — book courier',
  DISPATCHED: 'Handed to courier',
  DELIVERED: 'Mark delivered',
  RTO: 'Mark returned (RTO)',
  CANCELLED: 'Cancel order',
}

export function Admin() {
  const tab = 'rounded-lg px-3 py-2 text-sm font-semibold'
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-semibold">Studio admin</h1>
        <nav className="ml-auto flex gap-1">
          <NavLink to="/admin" end className={({ isActive }) => `${tab} ${isActive ? 'bg-ink text-white' : 'text-muted'}`}>Orders</NavLink>
          <NavLink to="/admin/products" className={({ isActive }) => `${tab} ${isActive ? 'bg-ink text-white' : 'text-muted'}`}>Products</NavLink>
          <NavLink to="/admin/catalogue" className={({ isActive }) => `${tab} ${isActive ? 'bg-ink text-white' : 'text-muted'}`}>Excel sync</NavLink>
        </nav>
      </div>
      <Routes>
        <Route index element={<Orders />} />
        <Route path="orders/:id" element={<AdminOrder />} />
        <Route path="products" element={<ProductList />} />
        <Route path="products/new" element={<ProductEditor />} />
        <Route path="products/:sku" element={<ProductEditor />} />
        <Route path="categories" element={<Categories />} />
        <Route path="catalogue" element={<Catalogue />} />
      </Routes>
      <p className="border-t border-line pt-4 text-center text-xs text-muted">
        Website designed &amp; developed by{' '}
        <a href="https://github.com/mahaansrivastav" target="_blank" rel="noopener" className="font-semibold text-ink underline underline-offset-2">Mahan Srivastava</a>
        {' '}· For changes or support, contact your developer.
      </p>
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
            <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
              <tr><th className="p-3">Order</th><th className="p-3">Customer</th><th className="p-3">Items</th><th className="p-3">Total</th><th className="p-3">Status</th></tr>
            </thead>
            <tbody className="divide-y divide-line">
              {orders.data.length === 0 && <tr><td colSpan={5} className="p-6 text-center text-muted">No orders here.</td></tr>}
              {orders.data.map((o) => (
                <tr key={o.id} className="hover:bg-stone-50">
                  <td className="p-3"><Link to={`/admin/orders/${o.id}`} className="font-semibold text-brand">{o.orderNo}</Link><br /><span className="text-xs text-muted">{dateTime(o.createdAt)}</span></td>
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
    <div className={`card p-4 ${highlight ? 'border-brand bg-brand-soft' : ''}`}>
      <p className="text-xs font-semibold uppercase tracking-wide text-muted">{label}</p>
      <p className="mt-1 text-2xl font-semibold">{value}</p>
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
    if (status === 'CANCELLED' && !confirm('Cancel this order? Refund any online payment from the Razorpay dashboard.')) return
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
          <Link to="/admin" className="text-sm text-muted">← Orders</Link>
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

function Catalogue() {
  const runs = useAsync(() => api<SyncRun[]>('/api/admin/catalogue/sync-runs'), [])
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const last = runs.data?.[0]

  const sync = async () => {
    setBusy(true)
    setErr(null)
    try { await api('/api/admin/catalogue/sync', { method: 'POST' }); runs.reload() } catch (e) { setErr((e as Error).message) } finally { setBusy(false) }
  }

  return (
    <div className="max-w-4xl space-y-4">
      <div className="card flex flex-wrap items-center justify-between gap-4 p-5">
        <div className="text-sm">
          <p className="font-semibold">Products come from the Excel sheet and photos folder.</p>
          <p className="text-muted">Edit <code>catalogue.xlsx</code> or add <code>photos/&lt;SKU_id&gt;/</code>, then sync. It also syncs automatically every 10 minutes.</p>
        </div>
        <button className="btn-primary" onClick={sync} disabled={busy}>{busy ? 'Syncing…' : 'Sync now'}</button>
      </div>
      {err && <ErrorBox message={err} />}
      {runs.loading && <Spinner />}
      {last && (
        <div className="card p-5">
          <p className="text-sm">Last sync {dateTime(last.startedAt)}: <b className="text-ok">{last.rowsOk} live</b>, <b className={last.rowsFailed ? 'text-bad' : ''}>{last.rowsFailed} skipped</b></p>
          {last.issues.length > 0 ? (
            <table className="mt-4 w-full text-left text-sm">
              <thead className="text-xs uppercase tracking-wide text-muted"><tr><th className="py-2">Row</th><th>SKU</th><th>Problem</th></tr></thead>
              <tbody className="divide-y divide-line">
                {last.issues.map((i, n) => (
                  <tr key={n}>
                    <td className="py-2 pr-3">{i.row || '—'}</td>
                    <td className="pr-3 font-mono">{i.sku ?? '—'}</td>
                    <td className={i.severity === 'ERROR' ? 'text-bad' : 'text-warn'}>{i.severity === 'WARN' ? '⚠ ' : ''}{i.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : <p className="mt-2 text-sm text-ok">No problems found.</p>}
        </div>
      )}
    </div>
  )
}
