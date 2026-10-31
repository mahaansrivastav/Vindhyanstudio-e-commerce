// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useState } from 'react'
import { Link, NavLink, Route, Routes } from 'react-router-dom'
import { AddressForm } from '../components/AddressForm'
import { Empty, ErrorBox, Spinner, StatusBadge } from '../components/ui'
import { api } from '../lib/api'
import { date, rupees } from '../lib/format'
import type { Address, OrderSummary } from '../lib/types'
import { useAsync } from '../lib/useAsync'
import { useAuth } from '../state/AppState'
import { OtpForm } from './Login'

export function Account() {
  const { signOut } = useAuth()
  const tab = 'rounded-lg px-3 py-2 text-sm font-semibold'
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-semibold">My account</h1>
        <button className="btn-ghost text-sm" onClick={signOut}>Log out</button>
      </div>
      <nav className="flex gap-1 overflow-x-auto border-b border-line pb-2">
        <NavLink end to="/account" className={({ isActive }) => `${tab} ${isActive ? 'bg-ink text-white' : 'text-muted'}`}>Profile</NavLink>
        <NavLink to="/account/orders" className={({ isActive }) => `${tab} ${isActive ? 'bg-ink text-white' : 'text-muted'}`}>Orders</NavLink>
        <NavLink to="/account/addresses" className={({ isActive }) => `${tab} ${isActive ? 'bg-ink text-white' : 'text-muted'}`}>Addresses</NavLink>
        <NavLink to="/wishlist" className={`${tab} text-muted`}>Wishlist</NavLink>
      </nav>
      <Routes>
        <Route index element={<ProfileTab />} />
        <Route path="orders" element={<OrdersTab />} />
        <Route path="addresses" element={<AddressesTab />} />
      </Routes>
    </div>
  )
}

function ProfileTab() {
  const { profile, refresh, signIn } = useAuth()
  const [name, setName] = useState(profile?.name ?? '')
  const [saved, setSaved] = useState(false)
  const [linking, setLinking] = useState<'phone' | 'email' | null>(null)
  if (!profile) return null

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    await api('/api/me', { method: 'PUT', body: { name } })
    await refresh()
    setSaved(true)
  }

  return (
    <div className="grid max-w-3xl gap-4 sm:grid-cols-2">
      <form onSubmit={save} className="card space-y-3 p-5">
        <label className="block"><span className="label">Name</span>
          <input className="input" value={name} onChange={(e) => { setName(e.target.value); setSaved(false) }} maxLength={120} />
        </label>
        <button className="btn-primary">Save</button>
        {saved && <span className="ml-3 text-sm text-ok">Saved</span>}
      </form>
      <div className="card space-y-4 p-5 text-sm">
        <div>
          <p className="label">Mobile</p>
          {profile.phone ? <p className="font-semibold">+91 {profile.phone}</p> : <button className="font-semibold text-brand" onClick={() => setLinking('phone')}>+ Add mobile number</button>}
        </div>
        <div>
          <p className="label">Email</p>
          {profile.email ? <p className="font-semibold">{profile.email}</p> : <button className="font-semibold text-brand" onClick={() => setLinking('email')}>+ Add email</button>}
        </div>
        {linking && (
          <div className="rounded-xl border border-line p-4">
            <p className="mb-3 text-muted">You’ll be able to log in with either one.</p>
            <OtpForm mode={linking} linkMode onDone={async (t) => { await signIn(t); setLinking(null) }} />
          </div>
        )}
      </div>
    </div>
  )
}

function OrdersTab() {
  const { data, error, loading, reload } = useAsync(() => api<OrderSummary[]>('/api/orders'), [])
  if (loading) return <Spinner />
  if (error) return <ErrorBox message={error} onRetry={reload} />
  if (!data?.length) return <Empty title="No orders yet"><Link to="/shop" className="btn-primary mt-2">Start shopping</Link></Empty>
  return (
    <ul className="space-y-3">
      {data.map((o) => (
        <li key={o.id}>
          <Link to={`/account/orders/${o.id}`} className="card flex flex-wrap items-center justify-between gap-3 p-4 hover:border-ink">
            <div>
              <p className="font-semibold">{o.orderNo} <span className="font-normal text-muted">· {date(o.createdAt)}</span></p>
              <p className="text-sm text-muted">{o.firstItem}{o.itemCount > 1 ? ` + ${o.itemCount - 1} more` : ''}</p>
            </div>
            <div className="flex items-center gap-3"><StatusBadge status={o.status} /><span className="font-semibold">{rupees(o.total)}</span></div>
          </Link>
        </li>
      ))}
    </ul>
  )
}

function AddressesTab() {
  const { data, error, loading, reload } = useAsync(() => api<Address[]>('/api/me/addresses'), [])
  const [editing, setEditing] = useState<Address | 'new' | null>(null)
  if (loading && !data) return <Spinner />
  if (error) return <ErrorBox message={error} onRetry={reload} />

  return (
    <div className="max-w-3xl space-y-3">
      {data?.map((a) => (
        editing !== 'new' && editing?.id === a.id ? (
          <div key={a.id} className="card p-5">
            <AddressForm initial={a} onCancel={() => setEditing(null)}
                         onSubmit={async (v) => { await api(`/api/me/addresses/${a.id}`, { method: 'PUT', body: v }); setEditing(null); reload() }} />
          </div>
        ) : (
          <div key={a.id} className="card flex flex-wrap items-start justify-between gap-3 p-4 text-sm">
            <div>
              <p className="font-semibold">{a.name} · {a.phone} {a.isDefault && <span className="ml-1 rounded bg-stone-100 px-1.5 py-0.5 text-xs">Default</span>}</p>
              <p className="text-muted">{a.line1}{a.line2 ? `, ${a.line2}` : ''}, {a.city}, {a.state} – {a.pincode}</p>
            </div>
            <div className="flex gap-3">
              <button className="font-semibold text-brand" onClick={() => setEditing(a)}>Edit</button>
              <button className="text-muted" onClick={async () => { if (confirm('Delete this address?')) { await api(`/api/me/addresses/${a.id}`, { method: 'DELETE' }); reload() } }}>Delete</button>
            </div>
          </div>
        )
      ))}
      {editing === 'new' ? (
        <div className="card p-5">
          <AddressForm onCancel={() => setEditing(null)} onSubmit={async (v) => { await api('/api/me/addresses', { body: v }); setEditing(null); reload() }} />
        </div>
      ) : (
        <button className="btn-outline" onClick={() => setEditing('new')}>+ Add address</button>
      )}
    </div>
  )
}
