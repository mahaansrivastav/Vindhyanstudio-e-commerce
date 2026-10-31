// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useEffect, useState, type ReactNode } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { apiBlob } from '../lib/api'
import { rupees, STATUS_LABEL, STATUS_TONE } from '../lib/format'
import type { OrderStatus } from '../lib/types'
import { useAuth } from '../state/AppState'

export function Price({ price, mrp, offer, size = 'md' }: { price: number; mrp: number; offer: number; size?: 'md' | 'lg' }) {
  return (
    <div className="flex flex-wrap items-baseline gap-x-2">
      <span className={size === 'lg' ? 'text-[28px] font-semibold tracking-tight' : 'font-semibold'}>{rupees(price)}</span>
      {mrp > price && <span className="text-sm text-muted line-through">{rupees(mrp)}</span>}
      {offer > 0 && <span className="text-sm font-medium text-accent">{offer}% off</span>}
    </div>
  )
}

export function StockBadge({ stock }: { stock: number }) {
  if (stock <= 0) return <span className="rounded-md bg-white px-2 py-0.5 text-[11px] font-medium text-muted">Out of stock</span>
  if (stock <= 3) return <span className="rounded-md bg-white px-2 py-0.5 text-[11px] font-medium text-warn">Only {stock} left</span>
  return null
}

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${STATUS_TONE[status]}`}>{STATUS_LABEL[status]}</span>
}

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-16 text-sm text-muted" role="status">
      <span className="h-4 w-4 animate-spin rounded-full border-2 border-line border-t-ink" />
      {label}
    </div>
  )
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-bad" role="alert">
      {message}
      {onRetry && <button className="ml-3 font-semibold underline" onClick={onRetry}>Retry</button>}
    </div>
  )
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="card px-6 py-14 text-center">
      <h2 className="text-xl">{title}</h2>
      {children && <div className="mt-3 text-sm text-muted">{children}</div>}
    </div>
  )
}

export function RequireAuth({ children, admin = false }: { children: ReactNode; admin?: boolean }) {
  const { profile, ready } = useAuth()
  const loc = useLocation()
  if (!ready) return <Spinner />
  if (!profile) return <Navigate to={`/login?next=${encodeURIComponent(loc.pathname + loc.search)}`} replace />
  if (admin && !profile.admin) return <Empty title="Admins only"><Link className="underline" to="/">Back to shop</Link></Empty>
  return <>{children}</>
}

/** Shows an image that needs the auth header (customer uploads). */
export function PrivateImage({ storageKey, className }: { storageKey: string; className?: string }) {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    let revoke: string | null = null
    apiBlob(`/api/files?key=${encodeURIComponent(storageKey)}`)
      .then((b) => { revoke = URL.createObjectURL(b); setUrl(revoke) })
      .catch(() => setUrl(null))
    return () => { if (revoke) URL.revokeObjectURL(revoke) }
  }, [storageKey])
  if (!url) return <div className={`animate-pulse bg-stone-200 ${className ?? ''}`} />
  return <a href={url} target="_blank" rel="noreferrer"><img src={url} alt="Customer upload" className={className} /></a>
}

export function ProductImage({ src, alt, className }: { src: string | null; alt: string; className?: string }) {
  if (!src) return <div className={`flex items-center justify-center bg-stone-100 text-xs text-muted ${className ?? ''}`}>No photo</div>
  return <img src={src} alt={alt} loading="lazy" className={`bg-stone-100 object-cover ${className ?? ''}`} />
}
