// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { Suspense, useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../lib/api'
import type { CategoryDto } from '../lib/types'
import { useAuth, useCart, useConfig, useWishlist } from '../state/AppState'
import { Avatar } from './Avatar'
import { Spinner } from './ui'
import { IconBox, IconCart, IconHeart, IconPencil, IconSearch, IconShield, IconTruck, IconUser } from './Icons'

export function Layout() {
  const { profile } = useAuth()
  const cart = useCart()
  const wish = useWishlist()
  const config = useConfig()
  const nav = useNavigate()
  const loc = useLocation()
  const [params] = useSearchParams()
  const [q, setQ] = useState(params.get('q') ?? '')
  const [cats, setCats] = useState<CategoryDto[]>([])

  useEffect(() => { api<CategoryDto[]>('/api/catalogue/categories').then(setCats).catch(() => {}) }, [])
  useEffect(() => { window.scrollTo(0, 0) }, [loc.pathname])

  const search = (e: React.FormEvent) => {
    e.preventDefault()
    nav(q.trim() ? `/shop?q=${encodeURIComponent(q.trim())}` : '/shop')
  }
  const activeCat = loc.pathname === '/shop' ? params.get('category') : null
  const hideFab = /^\/(p|cart|checkout|pay)(\/|$)/.test(loc.pathname)
  // Biggest categories go in the menu bar; the long tail sits under "More".
  const ranked = [...cats].sort((a, b) => b.productCount - a.productCount)
  const main = ranked.slice(0, 7)
  const more = ranked.slice(7).sort((a, b) => a.name.localeCompare(b.name))

  return (
    <div className="flex min-h-screen flex-col">
      <div className="bg-brand px-4 py-1.5 text-center text-[12px] text-white/90">
        {config?.paymentProvider === 'mock'
          ? 'Test mode: payments, shipping and messages are simulated.'
          : <>Free shipping on orders over ₹{config?.freeShippingAbove ?? 999}</>}
      </div>

      <header className="sticky top-0 z-30 border-b border-line bg-white/85 backdrop-blur-md">
        <div className="mx-auto flex max-w-7xl items-center gap-3 px-4 py-2.5 sm:gap-8 sm:py-3">
          <Link to="/" className="flex shrink-0 items-center gap-2" aria-label="Vindhyan Studio home">
            <img src="/brand/logo-mark.png" alt="" className="h-7 w-auto sm:h-8" />
            <span className="leading-none">
              <span className="block text-[15px] font-semibold tracking-[0.14em] sm:text-[17px]">VINDHYAN</span>
              <span className="mt-0.5 block text-[8px] font-medium tracking-[0.5em] text-muted sm:text-[9px]">STUDIO</span>
            </span>
          </Link>
          {/* Desktop: search in the bar. Phones get a full-width search row below. */}
          <SearchBox q={q} setQ={setQ} onSubmit={search} className="mx-auto hidden max-w-xl md:block" />
          <nav className="ml-auto flex shrink-0 items-center gap-0.5 text-sm font-semibold sm:gap-1 md:ml-0">
            <HeaderLink to={profile ? '/account/orders' : '/login?next=/account/orders'} label="Track order" icon={<IconTruck />} className="hidden lg:flex" />
            <HeaderLink to={profile ? '/account/wishlist' : '/wishlist'} label="Wishlist" icon={<IconHeart />} count={wish.items.length} />
            <HeaderLink to="/cart" label="Cart" icon={<IconCart />} count={cart.count} />
            {profile ? <ProfileMenu /> : <HeaderLink to="/login" label="Sign in" icon={<IconUser />} />}
          </nav>
        </div>
        <div className="px-4 pb-2.5 md:hidden">
          <SearchBox q={q} setQ={setQ} onSubmit={search} />
        </div>
        {cats.length > 0 && (
          // Phones show the category row only on the home page (the shop page has its own filter chips).
          <div className={`border-t border-line ${loc.pathname === '/' ? '' : 'hidden md:block'}`}>
            <nav className="scrollbar-none mx-auto flex max-w-7xl items-center gap-6 overflow-x-auto px-4 text-sm lg:overflow-visible" aria-label="Categories">
              <NavItem to="/shop" active={loc.pathname === '/shop' && !activeCat}>All products</NavItem>
              {main.map((c) => <NavItem key={c.slug} to={`/shop?category=${c.slug}`} active={activeCat === c.slug}>{c.name}</NavItem>)}
              {more.length > 0 && <MoreMenu cats={more} active={more.some((c) => c.slug === activeCat)} />}
            </nav>
          </div>
        )}
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:py-8">
        <Suspense fallback={<Spinner label="" />}><Outlet /></Suspense>
      </main>

      {config?.whatsapp && (
        // Hidden on phones where a sticky buy/checkout bar sits at the bottom.
        <a data-fab={hideFab ? 'hide' : undefined} href={`https://wa.me/${config.whatsapp}?text=${encodeURIComponent('Hi Vindhyan Studio, I have a question about an order.')}`}
           target="_blank" rel="noopener" aria-label="Chat with us on WhatsApp"
           className="fixed bottom-4 right-4 z-40 flex items-center gap-2 rounded-full bg-[#25D366] p-3 data-[fab=hide]:hidden sm:data-[fab=hide]:flex text-sm font-medium text-white shadow-[0_4px_14px_rgba(0,0,0,0.15)] transition hover:bg-[#1fbd5a] sm:px-4 sm:py-2.5">
          <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-hidden><path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2zm0 18.2c-1.5 0-3-.4-4.3-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1 1 12 20.2zm4.5-6.1c-.2-.1-1.5-.7-1.7-.8-.2-.1-.4-.1-.6.1l-.8 1c-.1.2-.3.2-.5.1a6.7 6.7 0 0 1-3.3-2.9c-.2-.4.2-.4.7-1.3.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.6-.4h-.5a1 1 0 0 0-.7.3 3 3 0 0 0-.9 2.2 5.2 5.2 0 0 0 1.1 2.8 11.9 11.9 0 0 0 4.6 4c1.7.7 2.4.8 3.2.7.5-.1 1.5-.6 1.8-1.2.2-.6.2-1.1.1-1.2l-.4-.2z" /></svg>
          <span className="hidden sm:inline">Chat with us</span>
        </a>
      )}

      <footer className="mt-16">
        {/* Trust strip */}
        <div className="border-t border-line">
          <ul className="mx-auto grid max-w-7xl grid-cols-2 gap-x-4 gap-y-3 px-4 py-5 text-sm sm:grid-cols-4">
            {[
              [<IconPencil key="a" className="h-5 w-5" />, 'Made to order in India'],
              [<IconTruck key="b" className="h-5 w-5" />, `Free shipping above ₹${config?.freeShippingAbove ?? 999}`],
              [<IconBox key="c" className="h-5 w-5" />, 'Quality checked by hand'],
              [<IconShield key="d" className="h-5 w-5" />, 'Secure payments'],
            ].map(([icon, text]) => (
              <li key={text as string} className="flex items-center gap-2.5 text-ink/80">
                <span className="text-brand">{icon}</span>{text}
              </li>
            ))}
          </ul>
        </div>

        <div className="bg-[#1f1416] text-stone-300">
          <div className="mx-auto grid max-w-7xl gap-8 px-4 py-12 lg:grid-cols-[1.3fr_1fr_1fr_1fr]">
            <div>
              <img src="/brand/logo-light.png" alt="Vindhyan Studio, your idea, our creation" className="h-20 w-auto" />
              <p className="mt-4 max-w-xs text-sm leading-relaxed text-stone-400">Personalised 3D printed keychains, idols, décor and gifts, printed to order and shipped across India.</p>
              <div className="mt-5 space-y-1.5 text-sm">
                {config?.sellerPhone && <a className="block hover:text-white" href={config.whatsapp ? `https://wa.me/${config.whatsapp}` : `tel:${config.sellerPhone}`} target="_blank" rel="noopener">WhatsApp · {config.sellerPhone}</a>}
                {config?.sellerEmail && <a className="block hover:text-white" href={`mailto:${config.sellerEmail}`}>{config.sellerEmail}</a>}
              </div>
            </div>
            <FooterCol title="Shop" links={[['All products', '/shop'], ...cats.slice(0, 5).map((c) => [c.name, `/shop?category=${c.slug}`] as [string, string])]} />
            <FooterCol title="Help" links={[['Track your order', '/account/orders'], ['Contact us', '/policies/contact'], ['Shipping policy', '/policies/shipping'], ['Returns & refunds', '/policies/refund']]} />
            <FooterCol title="Legal" links={[['Terms of use', '/policies/terms'], ['Privacy policy', '/policies/privacy'], ['Grievance officer', '/policies/contact']]} />
          </div>
          <div className="border-t border-white/10">
            <div className="mx-auto flex max-w-7xl flex-col gap-1.5 px-4 pb-24 pt-5 text-xs text-stone-400 sm:flex-row sm:items-center sm:justify-between sm:pb-20">
              <span>© {new Date().getFullYear()} Vindhyan Studio. All rights reserved.{config?.legalName && <> Powered by <span className="font-semibold text-stone-200">{config.legalName}</span>.</>}</span>
              <span>{config?.gstin && <>GSTIN {config.gstin} · </>}Gorakhpur, Uttar Pradesh</span>
            </div>
          </div>
        </div>
      </footer>
    </div>
  )
}

function HeaderLink({ to, label, icon, count, className = '' }: { to: string; label: string; icon: React.ReactNode; count?: number; className?: string }) {
  return (
    <NavLink to={to} className={`relative flex flex-col items-center gap-0.5 rounded-lg px-2 py-1 text-ink hover:text-brand sm:px-2.5 ${className}`} aria-label={label}>
      {icon}
      <span className="hidden text-[11px] md:block">{label}</span>
      {!!count && <span className="absolute -top-0.5 right-0.5 min-w-4 rounded-full bg-accent px-1 text-center text-[10px] font-semibold leading-4 text-white">{count}</span>}
    </NavLink>
  )
}

function FooterCol({ title, links }: { title: string; links: [string, string][] }) {
  const list = (
    <ul className="mt-3 space-y-2 text-sm text-stone-400">
      {links.map(([label, to]) => <li key={label}><Link to={to} className="hover:text-white">{label}</Link></li>)}
    </ul>
  )
  return (
    <div>
      {/* Mobile: tap to expand. Desktop: always open. */}
      <details className="group border-b border-white/10 pb-3 lg:hidden">
        <summary className="flex cursor-pointer list-none items-center justify-between text-sm font-medium text-white">
          {title}<span className="text-stone-400 transition group-open:rotate-45">+</span>
        </summary>
        {list}
      </details>
      <div className="hidden lg:block">
        <p className="text-sm font-medium text-white">{title}</p>
        {list}
      </div>
    </div>
  )
}

function NavItem({ to, active, children }: { to: string; active: boolean; children: React.ReactNode }) {
  return (
    <Link to={to} className={`shrink-0 py-3 ${active ? 'font-medium text-brand shadow-[inset_0_-2px_0_var(--color-brand)]' : 'text-ink/65 hover:text-brand'}`}>{children}</Link>
  )
}

function MoreMenu({ cats, active }: { cats: CategoryDto[]; active: boolean }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const loc = useLocation()
  useEffect(() => { setOpen(false) }, [loc.pathname, loc.search])
  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])
  return (
    <div ref={ref} className="shrink-0 lg:relative">
      <button type="button" onClick={() => setOpen(!open)} aria-expanded={open}
              className={`flex items-center gap-1 py-3 ${active ? 'font-medium text-ink' : 'text-ink/65 hover:text-ink'}`}>
        More
        <svg viewBox="0 0 24 24" className={`h-4 w-4 transition ${open ? 'rotate-180' : ''}`} fill="none" stroke="currentColor" strokeWidth="2" aria-hidden><path d="m6 9 6 6 6-6" /></svg>
      </button>
      {open && (
        <div className="absolute left-4 right-4 z-40 mt-px rounded-xl border border-line bg-white p-2 shadow-[0_12px_32px_rgba(0,0,0,0.08)] lg:left-auto lg:right-0 lg:w-[420px]">
          <ul className="grid grid-cols-2 gap-0.5">
            {cats.map((c) => (
              <li key={c.slug}>
                <Link to={`/shop?category=${c.slug}`} className="flex items-center justify-between rounded-lg px-3 py-2 text-sm text-ink/80 hover:bg-surface hover:text-ink">
                  {c.name}<span className="text-xs text-muted">{c.productCount}</span>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function ProfileMenu() {
  const { profile, signOut } = useAuth()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const loc = useLocation()
  const nav = useNavigate()
  useEffect(() => { setOpen(false) }, [loc.pathname])
  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false) }
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc) }
  }, [open])
  if (!profile) return null
  const links: [string, string][] = [['/account/orders', 'My orders'], ['/account/wishlist', 'Wishlist'], ['/account/addresses', 'Addresses'], ['/account', 'Account settings']]
  return (
    <div ref={ref} className="relative">
      <button type="button" onClick={() => setOpen(!open)} aria-expanded={open} aria-haspopup="menu" aria-label="Account menu"
              className="flex flex-col items-center gap-0.5 rounded-lg px-2 py-1 text-ink hover:text-brand sm:px-2.5">
        <Avatar name={profile.name} size="sm" />
        <span className="hidden text-[11px] md:block">Account</span>
      </button>
      {open && (
        <div role="menu" className="absolute right-0 z-40 mt-2 w-64 overflow-hidden rounded-xl border border-line bg-white shadow-[0_16px_40px_rgba(0,0,0,0.12)]">
          <div className="flex items-center gap-3 border-b border-line p-4">
            <Avatar name={profile.name} />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{profile.name || 'Welcome'}</p>
              <p className="truncate text-xs text-muted">{profile.email ?? (profile.phone ? `+91 ${profile.phone}` : '')}</p>
            </div>
          </div>
          <div className="p-1.5">
            {links.map(([to, label]) => (
              <Link key={to} to={to} role="menuitem" className="block rounded-lg px-3 py-2 text-sm text-ink/80 hover:bg-surface hover:text-ink">{label}</Link>
            ))}
            {profile.admin && <Link to="/admin" role="menuitem" className="block rounded-lg px-3 py-2 text-sm text-ink/80 hover:bg-surface hover:text-ink">Admin console</Link>}
            {(profile.developer || profile.analytics) && <Link to="/dev" role="menuitem" className="block rounded-lg px-3 py-2 text-sm text-ink/80 hover:bg-surface hover:text-ink">{profile.developer ? 'Developer console' : 'Analytics'}</Link>}
          </div>
          <div className="border-t border-line p-1.5">
            <button role="menuitem" onClick={() => { signOut(); setOpen(false); nav('/') }} className="w-full rounded-lg px-3 py-2 text-left text-sm text-ink/80 hover:bg-surface hover:text-bad">Sign out</button>
          </div>
        </div>
      )}
    </div>
  )
}

function SearchBox({ q, setQ, onSubmit, className = '' }: { q: string; setQ: (v: string) => void; onSubmit: (e: React.FormEvent) => void; className?: string }) {
  return (
    <form onSubmit={onSubmit} className={`relative w-full ${className}`} role="search">
      <IconSearch className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
      <input type="search" enterKeyHint="search" className="input bg-white py-2.5 pl-10 text-[16px] sm:py-2 sm:text-sm" placeholder="Search keychains, idols, lamps" value={q}
             onChange={(e) => setQ(e.target.value)} aria-label="Search products" />
    </form>
  )
}
