// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { getToken } from './api'

// First-party usage analytics: page views go to our own backend only (no third-party trackers).
const BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const SESSION_GAP = 30 * 60 * 1000

function id() {
  return (crypto.randomUUID?.() ?? `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`).replace(/-/g, '').slice(0, 32)
}

function stored(storage: () => Storage, key: string, make: () => string) {
  try {
    const s = storage()
    let v = s.getItem(key)
    if (!v) { v = make(); s.setItem(key, v) }
    return v
  } catch {
    return make()
  }
}

/** Session = visits with less than 30 minutes between page views. Returns [sessionId, isNewSession]. */
function session(): [string, boolean] {
  try {
    const raw = sessionStorage.getItem('vs_sess')
    const now = Date.now()
    const s = raw ? (JSON.parse(raw) as { id: string; last: number }) : null
    const fresh = !s || now - s.last > SESSION_GAP
    const next = { id: fresh ? id() : s!.id, last: now }
    sessionStorage.setItem('vs_sess', JSON.stringify(next))
    return [next.id, fresh]
  } catch {
    return [id(), true]
  }
}

export function sendPageView(path: string) {
  const [sid, isNew] = session()
  const params = new URLSearchParams(location.search)
  const body = {
    visitor: stored(() => localStorage, 'vs_vid', id),
    session: sid,
    path,
    referrer: isNew ? document.referrer || null : null,
    src: isNew ? params.get('utm_source') : null,
    tz: Intl.DateTimeFormat().resolvedOptions().timeZone,
    lang: navigator.language,
    w: window.screen?.width,
  }
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  fetch(`${BASE}/api/t/pv`, { method: 'POST', headers, body: JSON.stringify(body), keepalive: true }).catch(() => {})
}

/** Records one page view per route change. */
export function PageTracker() {
  const loc = useLocation()
  useEffect(() => { sendPageView(loc.pathname) }, [loc.pathname])
  return null
}
