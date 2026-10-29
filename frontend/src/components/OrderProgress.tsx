import type { OrderStatus } from '../lib/types'
import { IconCheck } from './Icons'

const STEPS: { key: OrderStatus; label: string; hint: string }[] = [
  { key: 'CONFIRMED', label: 'Order placed', hint: 'We’ve received your order' },
  { key: 'PRINTING', label: 'Printing', hint: 'Your item is being made' },
  { key: 'READY', label: 'Packed', hint: 'Handed to the courier soon' },
  { key: 'DISPATCHED', label: 'Shipped', hint: 'On the way to you' },
  { key: 'DELIVERED', label: 'Delivered', hint: 'Enjoy!' },
]

/** Tracking status bar. Uses the order timeline to show the date each step was reached. */
export function OrderProgress({ status, events = [] }: { status: OrderStatus; events?: { status: OrderStatus; at: string }[] }) {
  if (status === 'PAYMENT_PENDING' || status === 'CANCELLED' || status === 'RTO') {
    const text = status === 'PAYMENT_PENDING' ? 'Waiting for payment' : status === 'CANCELLED' ? 'This order was cancelled' : 'Returned to sender'
    return <div className={`rounded-2xl p-4 text-sm font-semibold ${status === 'PAYMENT_PENDING' ? 'bg-amber-50 text-amber-900' : 'bg-stone-100 text-stone-700'}`}>{text}</div>
  }
  const idx = STEPS.findIndex((s) => s.key === status)
  const when = (k: OrderStatus) => {
    const e = events.find((ev) => ev.status === k)
    return e ? new Date(e.at).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) : null
  }

  return (
    <div className="card p-5 sm:p-6">
      <ol className="relative grid grid-cols-5" aria-label="Order tracking">
        <div className="absolute left-[10%] right-[10%] top-4 h-1 rounded-full bg-line" aria-hidden />
        <div className="absolute left-[10%] top-4 h-1 rounded-full bg-ok transition-all" style={{ width: `${(Math.max(idx, 0) / (STEPS.length - 1)) * 80}%` }} aria-hidden />
        {STEPS.map((s, i) => {
          const done = i <= idx
          const current = i === idx
          return (
            <li key={s.key} className="relative flex flex-col items-center text-center" aria-current={current ? 'step' : undefined}>
              <span className={`z-10 flex h-9 w-9 items-center justify-center rounded-full border-2 text-xs font-bold ${done ? 'border-ok bg-ok text-white' : 'border-line bg-white text-muted'} ${current ? 'ring-4 ring-green-100' : ''}`}>
                {done ? <IconCheck /> : i + 1}
              </span>
              <span className={`mt-2 text-[11px] font-bold sm:text-sm ${done ? 'text-ink' : 'text-muted'}`}>{s.label}</span>
              <span className="hidden text-xs text-muted sm:block">{done ? (when(s.key) ?? '') : ''}</span>
            </li>
          )
        })}
      </ol>
      {idx >= 0 && <p className="mt-5 text-center text-sm text-muted"><b className="text-ink">{STEPS[idx].label}:</b> {STEPS[idx].hint}</p>}
    </div>
  )
}
