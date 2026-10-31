// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { ErrorBox, ProductImage, Spinner } from '../components/ui'
import { api } from '../lib/api'
import { describeCustomisation, rupees } from '../lib/format'
import type { OrderView, PaymentInit } from '../lib/types'
import { useAsync } from '../lib/useAsync'
import { useConfig } from '../state/AppState'
import { usePay, type PayMethod } from '../state/Payments'
import { Row } from './Cart'

type Choice = PayMethod | 'cod'

const METHODS: { id: PayMethod; title: string; sub: string; badges: string[] }[] = [
  { id: 'upi', title: 'UPI', sub: 'Google Pay, PhonePe, Paytm, BHIM or any UPI app', badges: ['GPay', 'PhonePe', 'Paytm', 'BHIM'] },
  { id: 'card', title: 'Credit / Debit card', sub: 'All major Indian and international cards', badges: ['Visa', 'Mastercard', 'RuPay', 'Amex'] },
  { id: 'netbanking', title: 'Net banking', sub: 'SBI, HDFC, ICICI, Axis, Kotak and 50+ banks', badges: [] },
  { id: 'wallet', title: 'Wallets', sub: 'Paytm, PhonePe, Amazon Pay, Mobikwik', badges: [] },
]

/** Dedicated payment step (Cart → Address → Payment). Opens the gateway with the chosen method preselected. */
export function Pay() {
  const { id } = useParams()
  const { data: o, error, loading, reload } = useAsync(() => api<OrderView>(`/api/orders/${id}`), [id])
  const config = useConfig()
  const pay = usePay()
  const nav = useNavigate()
  const [choice, setChoice] = useState<Choice>('upi')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  if (loading && !o) return <Spinner />
  if (error || !o) return <ErrorBox message={error ?? 'Order not found'} onRetry={reload} />
  if (o.status !== 'PAYMENT_PENDING') return <Navigate to={`/account/orders/${o.id}`} replace />

  const codOk = !!config?.codEnabled && o.total <= (config?.codMaxOrderValue ?? 0)
  const testMode = config?.paymentProvider === 'mock'

  const submit = async () => {
    setErr(null)
    setBusy(true)
    try {
      if (choice === 'cod') {
        await api(`/api/orders/${o.id}/cod`, { method: 'POST' })
        nav(`/account/orders/${o.id}?placed=1`)
        return
      }
      const init = await api<PaymentInit>(`/api/orders/${o.id}/pay`, { method: 'POST' })
      if (await pay(init, choice)) nav(`/account/orders/${o.id}?placed=1`)
      else setErr('Payment was not completed. You can try again. You won’t be charged twice.')
    } catch (e) {
      setErr((e as Error).message)
      reload()
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <Steps />
      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <section className="space-y-4">
          <div className="card flex flex-wrap items-end justify-between gap-3 p-5">
            <div>
              <p className="label">Amount to pay</p>
              <p className="text-3xl font-semibold">{rupees(o.total, true)}</p>
              <p className="text-sm text-muted">Order {o.orderNo} · inclusive of all taxes</p>
            </div>
            {testMode && <span className="rounded-full bg-amber-100 px-3 py-1 text-xs font-semibold text-amber-900">Test mode · no real money</span>}
          </div>

          <div className="card p-5">
            <h1 className="font-sans text-base font-semibold">Choose a payment method</h1>
            <div className="mt-4 space-y-2" role="radiogroup" aria-label="Payment method">
              {METHODS.map((m) => (
                <MethodTile key={m.id} selected={choice === m.id} onSelect={() => setChoice(m.id)} title={m.title} sub={m.sub} badges={m.badges} />
              ))}
              {codOk && (
                <MethodTile selected={choice === 'cod'} onSelect={() => setChoice('cod')} title="Cash on delivery"
                            sub="Pay in cash or UPI when the parcel arrives" badges={[]} />
              )}
            </div>

            {err && <p className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-bad" role="alert">{err}</p>}

            <button className="btn-primary mt-5 w-full py-3.5 text-base" onClick={submit} disabled={busy}>
              {busy ? 'Please wait…' : choice === 'cod' ? 'Confirm order · pay on delivery' : `Pay ${rupees(o.total, true)} securely`}
            </button>

            <div className="mt-4 flex items-start gap-3 rounded-xl bg-stone-50 p-3 text-xs text-muted">
              <Lock />
              <p>
                Payments are processed by our payment partner over an encrypted (SSL) connection and are PCI-DSS compliant.
                Vindhyan Studio never sees or stores your card number, CVV or UPI PIN.
              </p>
            </div>
          </div>

          <p className="text-center text-xs text-muted">
            By paying you agree to our <Link to="/policies/terms" className="underline">Terms</Link>,{' '}
            <Link to="/policies/refund" className="underline">Refund</Link> and{' '}
            <Link to="/policies/shipping" className="underline">Shipping</Link> policies.
          </p>
        </section>

        <aside className="space-y-4">
          <section className="card space-y-3 p-5">
            <h2 className="font-sans text-base font-semibold">Order summary</h2>
            <ul className="space-y-3">
              {o.items.map((i, n) => (
                <li key={n} className="flex gap-3 text-sm">
                  <ProductImage src={i.image} alt={i.name} className="h-14 w-14 shrink-0 rounded-lg" />
                  <div className="min-w-0 flex-1">
                    <p className="font-medium">{i.name} × {i.qty}</p>
                    {describeCustomisation(i.customisation).map((d) => <p key={d} className="truncate text-xs text-muted">{d}</p>)}
                  </div>
                  <span className="font-medium">{rupees(i.lineTotal)}</span>
                </li>
              ))}
            </ul>
            <div className="space-y-2 border-t border-line pt-3">
              <Row label="Subtotal" value={rupees(o.subtotal)} />
              <Row label="Shipping" value={o.shippingFee === 0 ? 'Free' : rupees(o.shippingFee)} />
              <Row label="Total" value={rupees(o.total, true)} bold />
            </div>
          </section>
          <section className="card p-5 text-sm">
            <h2 className="label">Delivering to</h2>
            <p className="font-semibold">{o.address.name} · {o.address.phone}</p>
            <p className="text-muted">{o.address.line1}{o.address.line2 ? `, ${o.address.line2}` : ''}, {o.address.city}, {o.address.state} – {o.address.pincode}</p>
          </section>
          <section className="card p-5 text-sm">
            <h2 className="label">Need help?</h2>
            <p className="font-semibold">{config?.storeName ?? 'Vindhyan Studio'}</p>
            {config?.sellerEmail && <p><a className="underline" href={`mailto:${config.sellerEmail}`}>{config.sellerEmail}</a></p>}
            {config?.sellerPhone && <p><a className="underline" href={`tel:${config.sellerPhone}`}>{config.sellerPhone}</a></p>}
            <Link to="/policies/contact" className="mt-1 inline-block text-brand">Contact details →</Link>
          </section>
        </aside>
      </div>
    </div>
  )
}

function MethodTile({ selected, onSelect, title, sub, badges }: { selected: boolean; onSelect: () => void; title: string; sub: string; badges: string[] }) {
  return (
    <button type="button" role="radio" aria-checked={selected} onClick={onSelect}
            className={`flex w-full items-center gap-3 rounded-xl border p-4 text-left transition ${selected ? 'border-brand bg-brand-soft ring-1 ring-brand' : 'border-line hover:border-ink'}`}>
      <span className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 ${selected ? 'border-brand' : 'border-stone-300'}`}>
        {selected && <span className="h-2.5 w-2.5 rounded-full bg-brand" />}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold">{title}</span>
        <span className="block text-xs text-muted">{sub}</span>
      </span>
      <span className="hidden flex-wrap justify-end gap-1 sm:flex">
        {badges.map((b) => <span key={b} className="rounded border border-line bg-card px-1.5 py-0.5 text-[10px] font-semibold text-stone-600">{b}</span>)}
      </span>
    </button>
  )
}

export function Steps({ current = 3 }: { current?: 1 | 2 | 3 }) {
  const steps = ['Cart', 'Address', 'Payment']
  return (
    <ol className="flex items-center justify-center gap-2 text-xs font-semibold sm:text-sm" aria-label="Checkout steps">
      {steps.map((s, i) => (
        <li key={s} className="flex items-center gap-2">
          <span className={`flex h-6 w-6 items-center justify-center rounded-full ${i + 1 < current ? 'bg-ok text-white' : i + 1 === current ? 'bg-brand text-white' : 'bg-stone-200 text-muted'}`}>
            {i + 1 < current ? '✓' : i + 1}
          </span>
          <span className={i + 1 === current ? 'text-ink' : 'text-muted'}>{s}</span>
          {i < steps.length - 1 && <span className="mx-1 h-px w-6 bg-line sm:w-12" />}
        </li>
      ))}
    </ol>
  )
}

function Lock() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="mt-0.5 shrink-0 text-ok" aria-hidden>
      <rect x="4" y="11" width="16" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  )
}
