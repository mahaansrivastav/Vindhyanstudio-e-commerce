import { useState } from 'react'
import { INDIAN_STATES } from '../lib/format'
import type { Address } from '../lib/types'

const EMPTY: Address = { name: '', phone: '', line1: '', line2: '', city: '', state: '', pincode: '', isDefault: false }

export function AddressForm({ initial, onSubmit, onCancel, submitLabel = 'Save address' }: {
  initial?: Address
  onSubmit: (a: Address) => Promise<void>
  onCancel?: () => void
  submitLabel?: string
}) {
  const [a, setA] = useState<Address>(initial ?? EMPTY)
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const set = (k: keyof Address) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setA({ ...a, [k]: e.target.type === 'checkbox' ? (e.target as HTMLInputElement).checked : e.target.value })

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErr(null)
    if (!/^[6-9][0-9]{9}$/.test(a.phone)) return setErr('Enter a 10-digit mobile number')
    if (!/^[1-9][0-9]{5}$/.test(a.pincode)) return setErr('Enter a valid 6-digit pincode')
    setBusy(true)
    try { await onSubmit(a) } catch (e) { setErr((e as Error).message) } finally { setBusy(false) }
  }

  return (
    <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
      <Field label="Full name"><input className="input" required value={a.name} onChange={set('name')} autoComplete="name" /></Field>
      <Field label="Mobile"><input className="input" required inputMode="numeric" maxLength={10} value={a.phone} onChange={set('phone')} autoComplete="tel-national" /></Field>
      <Field label="House / street" wide><input className="input" required value={a.line1} onChange={set('line1')} autoComplete="address-line1" /></Field>
      <Field label="Area / landmark (optional)" wide><input className="input" value={a.line2 ?? ''} onChange={set('line2')} autoComplete="address-line2" /></Field>
      <Field label="Pincode"><input className="input" required inputMode="numeric" maxLength={6} value={a.pincode} onChange={set('pincode')} autoComplete="postal-code" /></Field>
      <Field label="City"><input className="input" required value={a.city} onChange={set('city')} autoComplete="address-level2" /></Field>
      <Field label="State" wide>
        <select className="input" required value={a.state} onChange={set('state')}>
          <option value="">Select state</option>
          {INDIAN_STATES.map((s) => <option key={s}>{s}</option>)}
        </select>
      </Field>
      <label className="flex items-center gap-2 text-sm sm:col-span-2">
        <input type="checkbox" checked={a.isDefault} onChange={set('isDefault')} /> Make this my default address
      </label>
      {err && <p className="text-sm text-bad sm:col-span-2">{err}</p>}
      <div className="flex gap-2 sm:col-span-2">
        <button className="btn-primary" disabled={busy}>{busy ? 'Saving…' : submitLabel}</button>
        {onCancel && <button type="button" className="btn-ghost" onClick={onCancel}>Cancel</button>}
      </div>
    </form>
  )
}

function Field({ label, wide, children }: { label: string; wide?: boolean; children: React.ReactNode }) {
  return <label className={wide ? 'sm:col-span-2' : ''}><span className="label">{label}</span>{children}</label>
}
