// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AddressForm } from '../components/AddressForm'
import { Empty, ErrorBox, ProductImage, Spinner } from '../components/ui'
import { api } from '../lib/api'
import { describeCustomisation, rupees } from '../lib/format'
import type { Address } from '../lib/types'
import { Steps } from './Pay'
import { useAsync } from '../lib/useAsync'
import { useCart, useConfig } from '../state/AppState'
import { Row, useQuote } from './Cart'

export function Checkout() {
  const cart = useCart()
  const { quote, error: quoteErr } = useQuote()
  const addresses = useAsync(() => api<Address[]>('/api/me/addresses'), [])
  const [addressId, setAddressId] = useState<number | null>(null)
  const [adding, setAdding] = useState(false)
  const [mode, setMode] = useState<'ONLINE' | 'COD'>('ONLINE')
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const nav = useNavigate()
  const config = useConfig()
  const onlineOff = config?.paymentProvider === 'disabled'

  useEffect(() => {
    const list = addresses.data
    if (!list) return
    if (list.length > 0 && (!addressId || !list.some((a) => a.id === addressId))) setAddressId((list.find((a) => a.isDefault) ?? list[0]).id!)
  }, [addresses.data, addressId])

  const showForm = adding || addresses.data?.length === 0

  useEffect(() => { if (quote && !quote.codAvailable && mode === 'COD') setMode('ONLINE') }, [quote, mode])
  useEffect(() => { if (onlineOff) setMode('COD') }, [onlineOff])

  if (cart.lines.length === 0) return <Empty title="Your cart is empty"><Link to="/shop" className="btn-primary mt-2">Shop now</Link></Empty>
  if (addresses.loading && !addresses.data) return <Spinner />

  const place = async () => {
    setErr(null)
    if (!addressId) return setErr('Choose a delivery address')
    setBusy(true)
    try {
      const res = await api<{ orderId: number; paymentRequired: boolean }>('/api/orders', {
        body: { addressId, paymentMode: mode, items: cart.lines.map((l) => ({ sku: l.sku, qty: l.qty, customisation: l.customisation })) },
      })
      cart.clear()
      nav(res.paymentRequired ? `/pay/${res.orderId}` : `/account/orders/${res.orderId}?placed=1`)
    } catch (e) {
      setErr((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_380px]">
      <div className="space-y-6">
        <Steps current={2} />
        <h1 className="text-3xl font-semibold">Checkout</h1>

        <section className="card p-5">
          <h2 className="font-sans text-base font-semibold">1. Delivery address</h2>
          {addresses.error && <div className="mt-3"><ErrorBox message={addresses.error} onRetry={addresses.reload} /></div>}
          <div className="mt-3 space-y-2">
            {addresses.data?.map((a) => (
              <label key={a.id} className={`flex cursor-pointer gap-3 rounded-xl border p-3 text-sm ${addressId === a.id ? 'border-brand bg-brand-soft' : 'border-line'}`}>
                <input type="radio" name="address" checked={addressId === a.id} onChange={() => setAddressId(a.id!)} className="mt-1" />
                <span><b>{a.name}</b> · {a.phone}<br /><span className="text-muted">{a.line1}{a.line2 ? `, ${a.line2}` : ''}, {a.city}, {a.state} – {a.pincode}</span></span>
              </label>
            ))}
          </div>
          {showForm ? (
            <div className="mt-4">
              <AddressForm
                submitLabel="Save & use this address"
                onCancel={addresses.data?.length ? () => setAdding(false) : undefined}
                onSubmit={async (a) => {
                  const saved = await api<Address>('/api/me/addresses', { body: a })
                  addresses.setData([...(addresses.data ?? []), saved])
                  setAddressId(saved.id!)
                  setAdding(false)
                }}
              />
            </div>
          ) : (
            <button className="mt-3 text-sm font-semibold text-brand" onClick={() => setAdding(true)}>+ Add a new address</button>
          )}
        </section>

        <section className="card p-5">
          <h2 className="font-sans text-base font-semibold">2. Payment</h2>
          <div className="mt-3 space-y-2 text-sm">
            <label className={`flex gap-3 rounded-xl border p-3 ${onlineOff ? 'opacity-50' : 'cursor-pointer'} ${mode === 'ONLINE' ? 'border-brand bg-brand-soft' : 'border-line'}`}>
              <input type="radio" name="mode" checked={mode === 'ONLINE'} disabled={onlineOff} onChange={() => setMode('ONLINE')} />
              <span><b>Pay online</b><br /><span className="text-muted">{onlineOff ? 'Coming soon' : 'UPI, cards, netbanking, wallets'}</span></span>
            </label>
            <label className={`flex gap-3 rounded-xl border p-3 ${quote?.codAvailable ? 'cursor-pointer' : 'opacity-50'} ${mode === 'COD' ? 'border-brand bg-brand-soft' : 'border-line'}`}>
              <input type="radio" name="mode" checked={mode === 'COD'} disabled={!quote?.codAvailable} onChange={() => setMode('COD')} />
              <span><b>Cash on delivery</b><br /><span className="text-muted">{quote?.codAvailable ? 'Pay when your parcel arrives' : 'Not available for this order'}</span></span>
            </label>
          </div>
        </section>
      </div>

      <aside className="card h-fit space-y-3 p-5 lg:sticky lg:top-24">
        <h2 className="font-sans text-base font-semibold">Order summary</h2>
        <ul className="space-y-3">
          {cart.lines.map((l) => (
            <li key={l.id} className="flex gap-3 text-sm">
              <ProductImage src={l.image} alt={l.name} className="h-14 w-14 shrink-0 rounded-lg" />
              <div className="min-w-0 flex-1">
                <p className="font-medium">{l.name} × {l.qty}</p>
                {describeCustomisation(l.customisation).map((d) => <p key={d} className="truncate text-xs text-muted">{d}</p>)}
              </div>
            </li>
          ))}
        </ul>
        {quoteErr && <ErrorBox message={quoteErr} />}
        <div className="space-y-2 border-t border-line pt-3">
          <Row label="Subtotal" value={quote ? rupees(quote.subtotal) : '…'} />
          <Row label="Shipping" value={quote ? (quote.shippingFee === 0 ? 'Free' : rupees(quote.shippingFee)) : '…'} />
          <Row label="Total" value={quote ? rupees(quote.total) : '…'} bold />
        </div>
        {err && <p className="text-sm text-bad" role="alert">{err}</p>}
        <button className="btn-primary w-full py-3" disabled={busy || !quote?.valid || !addressId || showForm} onClick={place}>
          {busy ? 'Placing order…' : mode === 'COD' ? 'Place order' : 'Continue to payment'}
        </button>
        <p className="text-center text-xs text-muted">By placing the order you agree to our <Link to="/policies/terms" className="underline">terms</Link>.</p>
      </aside>
    </div>
  )
}
