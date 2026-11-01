// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useEffect } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { OrderProgress } from '../components/OrderProgress'
import { ErrorBox, PrivateImage, ProductImage, Spinner, StatusBadge } from '../components/ui'
import { api, openBlob } from '../lib/api'
import { dateTime, describeCustomisation, rupees, STATUS_LABEL } from '../lib/format'
import type { OrderView } from '../lib/types'
import { useAsync } from '../lib/useAsync'
import { useCart } from '../state/AppState'
import { Row } from './Cart'

export function OrderDetail() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const { data: o, error, loading, reload } = useAsync(() => api<OrderView>(`/api/orders/${id}`), [id])
  const payErr = params.get('payError')
  const cart = useCart()
  const placed = params.get('placed') === '1'

  // The invoice is generated a moment after confirmation; refresh once to pick it up.
  useEffect(() => {
    if (o && o.status !== 'PAYMENT_PENDING' && !o.invoiceNo) {
      const t = setTimeout(reload, 2000)
      return () => clearTimeout(t)
    }
  }, [o, reload])

  if (loading && !o) return <Spinner />
  if (error || !o) return <ErrorBox message={error ?? 'Order not found'} onRetry={reload} />

  const reorder = () => {
    o.items.forEach((i) => i.slug && cart.add({
      sku: i.sku, slug: i.slug, name: i.name, image: i.image, price: i.unitPrice, qty: i.qty, leadTimeDays: 3,
      customisation: i.customisation ? { ...i.customisation, photos: undefined } : undefined,
    }))
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      {placed && o.status !== 'PAYMENT_PENDING' && (
        <div className="rounded-xl bg-green-50 p-5 text-green-900">
          <p className="text-lg font-semibold">Thank you! Your order is confirmed.</p>
          <p className="text-sm">We’ve sent the details to you. We’ll message you again when it ships.</p>
        </div>
      )}
      {o.status === 'PAYMENT_PENDING' && (
        <div className="rounded-xl bg-amber-50 p-5 text-amber-900">
          <p className="font-semibold">Payment pending</p>
          <p className="text-sm">Your order is saved. Complete the payment to confirm it. Nothing is charged twice.</p>
          {payErr && <p className="mt-2 text-sm text-bad">{payErr}</p>}
          {o.canPay && <Link to={`/pay/${o.id}`} className="btn-primary mt-3">Pay {rupees(o.total)}</Link>}
        </div>
      )}

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to="/account/orders" className="text-sm text-muted">← My orders</Link>
          <h1 className="mt-1 text-3xl font-semibold">Order {o.orderNo}</h1>
          <p className="text-sm text-muted">Placed {dateTime(o.createdAt)} · {o.paymentMode === 'COD' ? 'Cash on delivery' : 'Paid online'}</p>
        </div>
        <StatusBadge status={o.status} />
      </div>

      <OrderProgress status={o.status} events={o.events} />

      {o.shipment?.awb && (
        <div className="card flex flex-wrap items-center justify-between gap-3 p-4 text-sm">
          <span>Shipped with <b>{o.shipment.courier ?? 'courier'}</b> · AWB <span className="font-mono">{o.shipment.awb}</span></span>
          {o.shipment.trackingUrl && <a href={o.shipment.trackingUrl} target="_blank" rel="noreferrer" className="btn-outline py-1.5">Track parcel ↗</a>}
        </div>
      )}

      <section className="card divide-y divide-line">
        {o.items.map((i) => (
          <div key={i.sku + JSON.stringify(i.customisation)} className="flex gap-4 p-4">
            <ProductImage src={i.image} alt={i.name} className="h-20 w-20 shrink-0 rounded-xl" />
            <div className="min-w-0 flex-1 text-sm">
              <div className="flex justify-between gap-2">
                {i.slug ? <Link to={`/p/${i.slug}`} className="font-semibold">{i.name}</Link> : <span className="font-semibold">{i.name}</span>}
                <span className="font-semibold">{rupees(i.lineTotal)}</span>
              </div>
              <p className="text-muted">Qty {i.qty} × {rupees(i.unitPrice)}</p>
              {describeCustomisation(i.customisation).map((d) => <p key={d} className="text-xs text-muted">{d}</p>)}
              {i.customisation?.photos?.length ? (
                <div className="mt-2 flex gap-2">{i.customisation.photos.map((k) => <PrivateImage key={k} storageKey={k} className="h-12 w-12 rounded-lg object-cover" />)}</div>
              ) : null}
            </div>
          </div>
        ))}
        <div className="space-y-2 p-4">
          <Row label="Subtotal" value={rupees(o.subtotal)} />
          <Row label="Shipping" value={o.shippingFee === 0 ? 'Free' : rupees(o.shippingFee)} />
          <Row label="Total" value={rupees(o.total)} bold />
        </div>
      </section>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <section className="card p-4 text-sm">
          <h2 className="label">Delivering to</h2>
          <p className="font-semibold">{o.address.name} · {o.address.phone}</p>
          <p className="text-muted">{o.address.line1}{o.address.line2 ? `, ${o.address.line2}` : ''}, {o.address.city}, {o.address.state} – {o.address.pincode}</p>
        </section>
        <section className="card space-y-2 p-4 text-sm">
          <h2 className="label">Invoice</h2>
          {o.invoiceNo ? (
            <button className="btn-outline w-full" onClick={() => openBlob(`/api/orders/${o.id}/invoice`).catch((e) => alert(e.message))}>
              Download invoice {o.invoiceNo}
            </button>
          ) : (
            <p className="text-muted">{o.status === 'PAYMENT_PENDING' ? 'Available after payment.' : 'Being generated…'}</p>
          )}
          <Link to="/cart" onClick={reorder} className="btn-ghost w-full">Reorder these items</Link>
        </section>
      </div>

      <section className="card p-4">
        <h2 className="label">History</h2>
        <ol className="space-y-3 border-l-2 border-line pl-4 text-sm">
          {[...o.events].reverse().map((e, i) => (
            <li key={i}>
              <p className="font-semibold">{STATUS_LABEL[e.status]}</p>
              {e.note && <p className="text-muted">{e.note}</p>}
              <p className="text-xs text-muted">{dateTime(e.at)}</p>
            </li>
          ))}
        </ol>
      </section>
    </div>
  )
}
