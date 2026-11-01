// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useState } from 'react'
import { api } from '../lib/api'
import { load, save } from '../lib/storage'

interface Result { pincode: string; serviceable: boolean; codAvailable: boolean; etaDays?: number; expectedBy?: string }

/** Delivery availability + expected date (BRD FR-21). leadDays adds production time for made-to-order items. */
export function PincodeCheck({ leadDays = 0 }: { leadDays?: number }) {
  const [pin, setPin] = useState<string>(() => load('vs_pincode', ''))
  const [res, setRes] = useState<Result | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const check = async (e: React.FormEvent) => {
    e.preventDefault()
    setErr(null)
    setRes(null)
    if (!/^[1-9][0-9]{5}$/.test(pin)) { setErr('Enter a valid 6-digit pincode'); return }
    setBusy(true)
    try {
      setRes(await api<Result>(`/api/shipping/serviceability?pincode=${pin}&leadDays=${leadDays}`))
      save('vs_pincode', pin)
    } catch (e) {
      setErr((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={check} className="space-y-2">
      <label className="text-sm font-medium" htmlFor="pincode">Delivery</label>
      <div className="flex gap-2">
        <input id="pincode" inputMode="numeric" maxLength={6} className="input" placeholder="Enter pincode" value={pin}
               onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))} />
        <button className="btn-outline shrink-0" disabled={busy}>{busy ? 'Checking…' : 'Check'}</button>
      </div>
      {err && <p className="text-sm text-bad">{err}</p>}
      {res && (res.serviceable ? (
        <p className="text-sm text-ok">
          ✓ Delivers to {res.pincode}
          {res.expectedBy && <> by <b>{new Date(res.expectedBy).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })}</b></>}
        </p>
      ) : (
        <p className="text-sm text-bad">Sorry, we don't deliver to {res.pincode} yet.</p>
      ))}
    </form>
  )
}
