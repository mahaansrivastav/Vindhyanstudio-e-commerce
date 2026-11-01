// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useState } from 'react'
import { Link, NavLink, Route, Routes } from 'react-router-dom'
import { AddressForm } from '../components/AddressForm'
import { Avatar } from '../components/Avatar'
import { Empty, ErrorBox, Spinner, StatusBadge } from '../components/ui'
import { api } from '../lib/api'
import { date, rupees } from '../lib/format'
import type { Address, OrderSummary } from '../lib/types'
import { useAsync } from '../lib/useAsync'
import { useAuth } from '../state/AppState'
import { OtpForm } from './Login'
import { WishlistGrid } from './Wishlist'

const NAV: [string, string, string, boolean?][] = [
  ['/account/orders', 'My orders', 'M6 3h12l2 4v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7zM4 7h16M9 11h6'],
  ['/account/wishlist', 'Wishlist', 'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z'],
  ['/account/addresses', 'Addresses', 'M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21zm0-9a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z'],
  ['/account', 'Account settings', 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm7.4-3a7.4 7.4 0 0 0-.1-1.2l2-1.6-2-3.4-2.4 1a7.6 7.6 0 0 0-2-1.2L14.5 3h-4l-.4 2.6a7.6 7.6 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.6a7.4 7.4 0 0 0 0 2.4l-2 1.6 2 3.4 2.4-1a7.6 7.6 0 0 0 2 1.2l.4 2.6h4l.4-2.6a7.6 7.6 0 0 0 2-1.2l2.4 1 2-3.4-2-1.6c.1-.4.1-.8.1-1.2z', true],
]

/** Customer area: one place for orders, wishlist, addresses and profile, with a side menu on desktop. */
export function Account() {
  const { profile } = useAuth()
  const item = ({ isActive }: { isActive: boolean }) =>
    `flex shrink-0 items-center gap-3 rounded-lg px-3 py-2 text-[14px] transition ${isActive ? 'bg-surface font-medium text-ink' : 'text-ink/65 hover:bg-surface hover:text-ink'}`
  return (
    <div className="grid grid-cols-1 gap-6 pt-2 lg:grid-cols-[250px_minmax(0,1fr)] lg:gap-10">
      <aside className="min-w-0 space-y-4 lg:sticky lg:top-36 lg:self-start">
        <div className="flex items-center gap-3 rounded-xl bg-card p-4 ring-1 ring-line">
          <Avatar name={profile?.name} />
          <div className="min-w-0">
            <p className="truncate font-medium">{profile?.name || 'Hello!'}</p>
            <p className="truncate text-xs text-muted">{profile?.email ?? (profile?.phone ? `+91 ${profile.phone}` : '')}</p>
          </div>
        </div>
        <nav className="scrollbar-none -mx-4 flex gap-1 overflow-x-auto px-4 lg:mx-0 lg:flex-col lg:px-0" aria-label="Account">
          {NAV.map(([to, label, d, end]) => (
            <NavLink key={to} to={to} end={end} className={item}>
              <svg viewBox="0 0 24 24" className="h-[18px] w-[18px] shrink-0" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d={d} /></svg>
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="min-w-0">
        <Routes>
          <Route index element={<ProfileTab />} />
          <Route path="orders" element={<OrdersTab />} />
          <Route path="wishlist" element={<Section title="Wishlist"><WishlistGrid /></Section>} />
          <Route path="addresses" element={<AddressesTab />} />
        </Routes>
      </div>
    </div>
  )
}

function Section({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl sm:text-3xl">{title}</h1>
        {action}
      </div>
      {children}
    </section>
  )
}

function ProfileTab() {
  const { profile, refresh, signIn } = useAuth()
  const [editing, setEditing] = useState(false)
  const [name, setName] = useState(profile?.name ?? '')
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState<string | null>(null)
  const [linking, setLinking] = useState<'phone' | 'email' | null>(null)
  if (!profile) return null

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true)
    try {
      await api('/api/me', { method: 'PUT', body: { name: name.trim() } })
      await refresh()
      setEditing(false)
      setMsg('Name updated')
    } catch (err) { setMsg((err as Error).message) } finally { setBusy(false) }
  }

  return (
    <Section title="Account settings">
      <div className="divide-y divide-line rounded-xl bg-card ring-1 ring-line">
        <div className="p-5">
          <p className="text-sm text-muted">Name</p>
          {editing ? (
            <form onSubmit={save} className="mt-2 flex flex-wrap gap-2">
              <input className="input max-w-sm" autoFocus value={name} onChange={(e) => setName(e.target.value)} maxLength={120} placeholder="Your full name" />
              <button className="btn-primary" disabled={busy || !name.trim()}>{busy ? 'Saving…' : 'Save'}</button>
              <button type="button" className="btn-ghost" onClick={() => { setEditing(false); setName(profile.name ?? '') }}>Cancel</button>
            </form>
          ) : (
            <div className="mt-1 flex items-center justify-between gap-3">
              <p className="font-medium">{profile.name || <span className="text-muted">Not added</span>}</p>
              <button className="text-sm font-medium underline underline-offset-2" onClick={() => { setEditing(true); setMsg(null) }}>{profile.name ? 'Edit' : 'Add'}</button>
            </div>
          )}
          {msg && !editing && <p className="mt-2 text-sm text-ok">{msg}</p>}
        </div>
        <Row label="Email" value={profile.email} onAdd={() => setLinking('email')} />
        <Row label="Mobile" value={profile.phone ? `+91 ${profile.phone}` : null} onAdd={() => setLinking('phone')} />
        {linking && (
          <div className="p-5">
            <p className="mb-3 text-sm text-muted">Verify it with a one-time code. You'll be able to sign in with either one.</p>
            <OtpForm mode={linking} linkMode onDone={async (t) => { await signIn(t); setLinking(null) }} />
            <button className="mt-2 text-sm text-muted underline" onClick={() => setLinking(null)}>Cancel</button>
          </div>
        )}
      </div>
      <p className="text-xs text-muted">Payments are handled securely by our payment partner; we never store your card details.</p>
    </Section>
  )
}

function Row({ label, value, onAdd }: { label: string; value: string | null | undefined; onAdd: () => void }) {
  return (
    <div className="flex items-center justify-between gap-3 p-5">
      <div>
        <p className="text-sm text-muted">{label}</p>
        <p className="mt-1 font-medium">{value || <span className="font-normal text-muted">Not added</span>}</p>
      </div>
      {!value && <button className="text-sm font-medium underline underline-offset-2" onClick={onAdd}>Add</button>}
    </div>
  )
}

function OrdersTab() {
  const { data, error, loading, reload } = useAsync(() => api<OrderSummary[]>('/api/orders'), [])
  return (
    <Section title="My orders">
      {loading && <Spinner />}
      {error && <ErrorBox message={error} onRetry={reload} />}
      {data && data.length === 0 && <Empty title="No orders yet"><Link to="/shop" className="btn-primary mt-2">Start shopping</Link></Empty>}
      {data && data.length > 0 && (
        <ul className="divide-y divide-line overflow-hidden rounded-xl bg-card ring-1 ring-line">
          {data.map((o) => (
            <li key={o.id}>
              <Link to={`/account/orders/${o.id}`} className="flex flex-wrap items-center gap-x-4 gap-y-2 p-4 transition hover:bg-surface/60">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{o.firstItem}{o.itemCount > 1 ? <span className="font-normal text-muted"> + {o.itemCount - 1} more</span> : ''}</p>
                  <p className="text-sm text-muted">{o.orderNo} · {date(o.createdAt)}</p>
                </div>
                <StatusBadge status={o.status} />
                <span className="w-20 text-right font-medium tabular-nums">{rupees(o.total)}</span>
                <span className="text-muted">›</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Section>
  )
}

function AddressesTab() {
  const { data, error, loading, reload } = useAsync(() => api<Address[]>('/api/me/addresses'), [])
  const [editing, setEditing] = useState<Address | 'new' | null>(null)
  if (loading && !data) return <Spinner />
  if (error) return <ErrorBox message={error} onRetry={reload} />

  return (
    <Section title="Addresses" action={editing !== 'new' && <button className="btn-outline" onClick={() => setEditing('new')}>+ Add address</button>}>
    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
      {[...(data ?? [])].sort((x, y) => Number(y.isDefault) - Number(x.isDefault)).map((a) => (
        editing !== 'new' && editing?.id === a.id ? (
          <div key={a.id} className="card p-5 md:col-span-2">
            <AddressForm initial={a} onCancel={() => setEditing(null)}
                         onSubmit={async (v) => { await api(`/api/me/addresses/${a.id}`, { method: 'PUT', body: v }); setEditing(null); reload() }} />
          </div>
        ) : (
          <div key={a.id} className="card flex flex-col justify-between gap-4 p-5 text-sm">
            <div>
              <p className="font-medium">{a.name} {a.isDefault && <span className="ml-1 rounded-md bg-surface px-1.5 py-0.5 text-[11px] font-medium text-muted">Default</span>}</p>
              <p className="mt-1 leading-relaxed text-muted">{a.line1}{a.line2 ? `, ${a.line2}` : ''}<br />{a.city}, {a.state} – {a.pincode}<br />+91 {a.phone}</p>
            </div>
            <div className="flex flex-wrap gap-x-4 gap-y-2 border-t border-line pt-3">
              <button className="font-medium underline underline-offset-2" onClick={() => setEditing(a)}>Edit</button>
              {!a.isDefault && (
                <button className="text-ink/70 hover:text-ink" onClick={async () => { await api(`/api/me/addresses/${a.id}`, { method: 'PUT', body: { ...a, isDefault: true } }); reload() }}>
                  Set as default
                </button>
              )}
              <button className="text-muted hover:text-bad" onClick={async () => { if (confirm('Delete this address?')) { await api(`/api/me/addresses/${a.id}`, { method: 'DELETE' }); reload() } }}>Delete</button>
            </div>
          </div>
        )
      ))}
      {editing === 'new' && (
        <div className="card p-5 md:col-span-2">
          <AddressForm onCancel={() => setEditing(null)} onSubmit={async (v) => { await api('/api/me/addresses', { body: v }); setEditing(null); reload() }} />
        </div>
      )}
      {data?.length === 0 && editing !== 'new' && <p className="text-sm text-muted md:col-span-2">No saved addresses yet.</p>}
    </div>
    </Section>
  )
}
