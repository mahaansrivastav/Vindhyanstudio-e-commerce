import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { PincodeCheck } from '../components/PincodeCheck'
import { Empty, ErrorBox, ProductImage } from '../components/ui'
import { api } from '../lib/api'
import { describeCustomisation, rupees } from '../lib/format'
import type { Quote } from '../lib/types'
import { useCart, useConfig } from '../state/AppState'

export function useQuote() {
  const cart = useCart()
  const [quote, setQuote] = useState<Quote | null>(null)
  const [error, setError] = useState<string | null>(null)
  const key = JSON.stringify(cart.lines.map((l) => [l.sku, l.qty]))
  useEffect(() => {
    if (cart.lines.length === 0) { setQuote(null); return }
    const ctrl = new AbortController()
    api<Quote>('/api/cart/quote', { body: { items: cart.lines.map((l) => ({ sku: l.sku, qty: l.qty })) }, signal: ctrl.signal })
      .then((q) => { setQuote(q); setError(null) })
      .catch((e: Error) => e.name !== 'AbortError' && setError(e.message))
    return () => ctrl.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
  return { quote, error }
}

export function Cart() {
  const cart = useCart()
  const config = useConfig()
  const { quote, error } = useQuote()
  const nav = useNavigate()

  if (cart.lines.length === 0) {
    return <Empty title="Your cart is empty"><Link to="/shop" className="btn-primary mt-2">Start shopping</Link></Empty>
  }

  const lead = Math.max(1, ...cart.lines.map((l) => (l.customisation ? l.leadTimeDays : 1)))
  const remaining = config && quote && quote.shippingFee > 0 ? config.freeShippingAbove - quote.subtotal : 0

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_360px]">
      <div>
        <h1 className="text-3xl font-semibold">Cart</h1>
        <ul className="mt-6 divide-y divide-line">
          {cart.lines.map((l, i) => {
            const q = quote?.lines[i]
            return (
              <li key={l.id} className="flex gap-4 py-4">
                <Link to={`/p/${l.slug}`} className="shrink-0"><ProductImage src={l.image} alt={l.name} className="h-24 w-24 rounded-xl" /></Link>
                <div className="min-w-0 flex-1">
                  <div className="flex justify-between gap-3">
                    <Link to={`/p/${l.slug}`} className="font-semibold">{l.name}</Link>
                    <span className="font-semibold">{rupees(q?.lineTotal ?? l.price * l.qty)}</span>
                  </div>
                  {describeCustomisation(l.customisation).map((d) => <p key={d} className="text-xs text-muted">{d}</p>)}
                  {q?.problem && <p className="mt-1 text-sm font-medium text-bad">{q.problem}</p>}
                  <div className="mt-2 flex items-center gap-3">
                    <div className="flex items-center rounded-lg border border-line text-sm">
                      <button className="px-2.5 py-1" onClick={() => cart.setQty(l.id, l.qty - 1)} aria-label="Decrease">−</button>
                      <span className="w-6 text-center">{l.qty}</span>
                      <button className="px-2.5 py-1" onClick={() => cart.setQty(l.id, l.qty + 1)} aria-label="Increase">+</button>
                    </div>
                    <button className="text-sm text-muted underline" onClick={() => cart.remove(l.id)}>Remove</button>
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      </div>

      <aside className="space-y-4 lg:sticky lg:top-24 lg:self-start">
        <div className="card space-y-3 p-5">
          {error && <ErrorBox message={error} />}
          <Row label="Subtotal" value={quote ? rupees(quote.subtotal) : '…'} />
          <Row label="Shipping" value={quote ? (quote.shippingFee === 0 ? 'Free' : rupees(quote.shippingFee)) : '…'} />
          {remaining > 0 && <p className="text-xs text-ok">Add {rupees(remaining)} more for free shipping</p>}
          <div className="border-t border-line pt-3"><Row label="Total" value={quote ? rupees(quote.total) : '…'} bold /></div>
          <button className="btn-primary w-full" disabled={!quote?.valid} onClick={() => nav('/checkout')}>Checkout</button>
          {quote && !quote.valid && <p className="text-xs text-bad">Fix the items marked above to continue.</p>}
        </div>
        <div className="card p-5"><PincodeCheck leadDays={lead} /></div>
      </aside>
    </div>
  )
}

export function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <div className={`flex justify-between text-sm ${bold ? 'text-base font-semibold' : ''}`}>
      <span className={bold ? '' : 'text-muted'}>{label}</span><span>{value}</span>
    </div>
  )
}
