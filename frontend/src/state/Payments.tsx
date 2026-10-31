import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import { api } from '../lib/api'
import { rupees } from '../lib/format'
import type { PaymentInit } from '../lib/types'

/**
 * Opens the right payment UI for the configured provider and verifies the result on the server.
 * Resolves to true when the order is paid, false when the customer closed/cancelled.
 * Add new providers (Cashfree, PhonePe...) as another branch here.
 */
export type PayMethod = 'upi' | 'card' | 'netbanking' | 'wallet'
type PayFn = (init: PaymentInit, method?: PayMethod) => Promise<boolean>
const PayCtx = createContext<PayFn>(async () => false)
export const usePay = () => useContext(PayCtx)

type Result = { providerOrderId: string; providerPaymentId: string; signature: string } | null

function loadScript(src: string, ready: () => boolean): Promise<void> {
  if (ready()) return Promise.resolve()
  return new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = src
    s.onload = () => resolve()
    s.onerror = () => reject(new Error('Could not load the payment window. Check your connection.'))
    document.body.appendChild(s)
  })
}

export function PaymentProvider({ children }: { children: ReactNode }) {
  const [mock, setMock] = useState<PaymentInit | null>(null)
  const resolver = useRef<(r: Result) => void>(() => {})

  const collect = useCallback(async (init: PaymentInit, method?: PayMethod): Promise<Result> => {
    if (init.provider === 'cashfree') {
      await loadScript('https://sdk.cashfree.com/js/v3/cashfree.js', () => !!window.Cashfree)
      const cf = window.Cashfree!({ mode: init.mode ?? 'sandbox' })
      const result = await cf.checkout({ paymentSessionId: init.sessionToken!, redirectTarget: '_modal' })
      if (result.error && !result.paymentDetails) return null // closed or failed; server reconciles anyway
      // Cashfree has no client signature: the server confirms the order status with Cashfree.
      return { providerOrderId: init.providerOrderId, providerPaymentId: '', signature: '' }
    }
    if (init.provider === 'razorpay') {
      await loadScript('https://checkout.razorpay.com/v1/checkout.js', () => !!window.Razorpay)
      return new Promise<Result>((resolve) => {
        const rzp = new window.Razorpay!({
          key: init.keyId,
          order_id: init.providerOrderId,
          amount: init.amountPaise,
          currency: init.currency,
          name: 'Vindhyan Studio',
          description: `Order ${init.orderNo}`,
          prefill: { name: init.name ?? '', email: init.email ?? '', contact: init.phone ?? '', ...(method ? { method } : {}) },
          theme: { color: '#26282e' },
          handler: (r: { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }) =>
            resolve({ providerOrderId: r.razorpay_order_id, providerPaymentId: r.razorpay_payment_id, signature: r.razorpay_signature }),
          modal: { ondismiss: () => resolve(null) },
        })
        rzp.open()
      })
    }
    // mock (local / dev)
    return new Promise<Result>((resolve) => {
      resolver.current = resolve
      setMock(init)
    })
  }, [])

  const pay = useCallback<PayFn>(async (init, method) => {
    const r = await collect(init, method)
    if (!r) return false
    await api('/api/payments/verify', { body: r })
    return true
  }, [collect])

  const finishMock = (r: Result) => {
    setMock(null)
    resolver.current(r)
  }

  return (
    <PayCtx.Provider value={pay}>
      {children}
      {mock && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-4 sm:items-center" role="dialog" aria-modal="true">
          <div className="card w-full max-w-sm p-6 shadow-xl">
            <p className="text-xs font-semibold uppercase tracking-wide text-amber-700">Test payment · no real money</p>
            <h2 className="mt-1 text-2xl">Pay {rupees(mock.amountPaise / 100, true)}</h2>
            <p className="mt-1 text-sm text-muted">Order {mock.orderNo}. This dialog replaces Razorpay while PAYMENT_PROVIDER=mock.</p>
            <div className="mt-5 grid gap-2">
              <button className="btn-primary" onClick={() => finishMock({ providerOrderId: mock.providerOrderId, providerPaymentId: 'mock_pay_' + Date.now(), signature: 'ok' })}>
                Simulate successful payment
              </button>
              <button className="btn-outline" onClick={() => finishMock({ providerOrderId: mock.providerOrderId, providerPaymentId: 'mock_fail_' + Date.now(), signature: 'fail' })}>
                Simulate failed payment
              </button>
              <button className="btn-ghost" onClick={() => finishMock(null)}>Close (pay later)</button>
            </div>
          </div>
        </div>
      )}
    </PayCtx.Provider>
  )
}
