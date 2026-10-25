import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, getToken, setToken } from '../lib/api'
import { load, save } from '../lib/storage'
import type { Customisation, Profile, PublicConfig } from '../lib/types'

// ---------- Config ----------
const ConfigCtx = createContext<PublicConfig | null>(null)
export const useConfig = () => useContext(ConfigCtx)

// ---------- Auth ----------
interface AuthState {
  profile: Profile | null
  ready: boolean
  signIn: (token: string) => Promise<void>
  signOut: () => void
  refresh: () => Promise<void>
}
const AuthCtx = createContext<AuthState>(null as unknown as AuthState)
export const useAuth = () => useContext(AuthCtx)

// ---------- Cart ----------
export interface CartLine {
  id: string
  sku: string
  slug: string
  name: string
  image: string | null
  price: number
  qty: number
  leadTimeDays: number
  customisation?: Customisation
}
interface CartState {
  lines: CartLine[]
  count: number
  add: (line: Omit<CartLine, 'id'>) => void
  setQty: (id: string, qty: number) => void
  remove: (id: string) => void
  clear: () => void
}
const CartCtx = createContext<CartState>(null as unknown as CartState)
export const useCart = () => useContext(CartCtx)

// ---------- Wishlist ----------
export interface WishItem { sku: string; slug: string; name: string; image: string | null; price: number }
interface WishState { items: WishItem[]; has: (sku: string) => boolean; toggle: (item: WishItem) => void }
const WishCtx = createContext<WishState>(null as unknown as WishState)
export const useWishlist = () => useContext(WishCtx)

function lineId(sku: string, c?: Customisation) {
  return sku + '|' + JSON.stringify(c ?? {})
}

export function AppStateProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<PublicConfig | null>(null)
  const [profile, setProfile] = useState<Profile | null>(null)
  const [ready, setReady] = useState(false)
  const [lines, setLines] = useState<CartLine[]>(() => load('vs_cart', []))
  const [wish, setWish] = useState<WishItem[]>(() => load('vs_wishlist', []))

  useEffect(() => { api<PublicConfig>('/api/config').then(setConfig).catch(() => {}) }, [])
  useEffect(() => save('vs_cart', lines), [lines])
  useEffect(() => save('vs_wishlist', wish), [wish])

  const refresh = useCallback(async () => {
    if (!getToken()) { setProfile(null); return }
    try { setProfile(await api<Profile>('/api/me')) } catch { setProfile(null) }
  }, [])

  useEffect(() => {
    refresh().finally(() => setReady(true))
    const onLogout = () => setProfile(null)
    window.addEventListener('vs:logout', onLogout)
    return () => window.removeEventListener('vs:logout', onLogout)
  }, [refresh])

  const auth = useMemo<AuthState>(() => ({
    profile, ready, refresh,
    signIn: async (token) => { setToken(token); await refresh() },
    signOut: () => { setToken(null); setProfile(null) },
  }), [profile, ready, refresh])

  const cart = useMemo<CartState>(() => ({
    lines,
    count: lines.reduce((n, l) => n + l.qty, 0),
    add: (line) => setLines((ls) => {
      const id = lineId(line.sku, line.customisation)
      const existing = ls.find((l) => l.id === id)
      if (existing) return ls.map((l) => (l.id === id ? { ...l, qty: Math.min(20, l.qty + line.qty) } : l))
      return [...ls, { ...line, id }]
    }),
    setQty: (id, qty) => setLines((ls) => ls.map((l) => (l.id === id ? { ...l, qty: Math.max(1, Math.min(20, qty)) } : l))),
    remove: (id) => setLines((ls) => ls.filter((l) => l.id !== id)),
    clear: () => setLines([]),
  }), [lines])

  const wishlist = useMemo<WishState>(() => ({
    items: wish,
    has: (sku) => wish.some((w) => w.sku === sku),
    toggle: (item) => setWish((ws) => (ws.some((w) => w.sku === item.sku) ? ws.filter((w) => w.sku !== item.sku) : [...ws, item])),
  }), [wish])

  return (
    <ConfigCtx.Provider value={config}>
      <AuthCtx.Provider value={auth}>
        <CartCtx.Provider value={cart}>
          <WishCtx.Provider value={wishlist}>{children}</WishCtx.Provider>
        </CartCtx.Provider>
      </AuthCtx.Provider>
    </ConfigCtx.Provider>
  )
}
