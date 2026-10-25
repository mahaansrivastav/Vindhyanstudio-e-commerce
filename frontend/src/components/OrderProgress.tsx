import type { OrderStatus } from '../lib/types'

const STEPS: { key: OrderStatus; label: string }[] = [
  { key: 'CONFIRMED', label: 'Confirmed' },
  { key: 'PRINTING', label: 'Printing' },
  { key: 'READY', label: 'Packed' },
  { key: 'DISPATCHED', label: 'Shipped' },
  { key: 'DELIVERED', label: 'Delivered' },
]

export function OrderProgress({ status }: { status: OrderStatus }) {
  if (status === 'PAYMENT_PENDING' || status === 'CANCELLED' || status === 'RTO') return null
  const idx = STEPS.findIndex((s) => s.key === status)
  return (
    <ol className="grid grid-cols-5 gap-1" aria-label="Order progress">
      {STEPS.map((s, i) => (
        <li key={s.key} className="text-center">
          <div className={`h-1.5 rounded-full ${i <= idx ? 'bg-brand' : 'bg-line'}`} />
          <span className={`mt-2 block text-[11px] sm:text-xs ${i <= idx ? 'font-semibold text-ink' : 'text-muted'}`}>{s.label}</span>
        </li>
      ))}
    </ol>
  )
}
