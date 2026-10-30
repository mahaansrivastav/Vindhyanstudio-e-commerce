import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../lib/api'
import type { CategoryDto } from '../lib/types'
import { useAuth, useCart, useConfig, useWishlist } from '../state/AppState'
import { IconCart, IconHeart, IconSearch, IconTruck, IconUser } from './Icons'

// Developer credit link. Change to the portfolio URL once it's live.
const DEVELOPER_URL = 'https://github.com/mahaansrivastav'

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

  return (
    <div className="flex min-h-screen flex-col">
      <div className="bg-ink px-4 py-2 text-center text-xs font-medium text-white">
        {config?.paymentProvider === 'mock'
          ? 'Test mode: payments, shipping and messages are simulated.'
          : <>Free shipping on orders above ₹{config?.freeShippingAbove ?? 999} · Made to order in India</>}
      </div>

      <header className="sticky top-0 z-30 border-b border-line bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center gap-3 px-4 py-3 sm:gap-6">
          <Link to="/" className="flex shrink-0 items-center gap-2" aria-label="Vindhyan Studio home">
            <img src="/brand/logo-mark.png" alt="" className="h-9 w-auto" />
            <span className="hidden leading-none sm:block">
              <span className="block text-lg font-extrabold tracking-[0.12em]">VINDHYAN</span>
              <span className="block text-[10px] font-medium tracking-[0.42em] text-muted">STUDIO</span>
            </span>
          </Link>
          <form onSubmit={search} className="relative mx-auto w-full max-w-xl" role="search">
            <IconSearch className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
            <input className="input rounded-full bg-surface py-2.5 pl-10" placeholder="Search keychains, idols, toys, lamps…" value={q}
                   onChange={(e) => setQ(e.target.value)} aria-label="Search products" />
          </form>
          <nav className="flex shrink-0 items-center gap-0.5 text-sm font-semibold sm:gap-1">
            <HeaderLink to={profile ? '/account/orders' : '/login?next=/account/orders'} label="Track order" icon={<IconTruck />} className="hidden lg:flex" />
            <HeaderLink to="/wishlist" label="Wishlist" icon={<IconHeart />} count={wish.items.length} />
            <HeaderLink to={profile ? '/account' : '/login'} label={profile ? 'Account' : 'Sign in'} icon={<IconUser />} />
            <HeaderLink to="/cart" label="Cart" icon={<IconCart />} count={cart.count} />
            {profile?.admin && <NavLink to="/admin" className="btn-outline ml-1 px-3 py-1.5 text-xs">Admin</NavLink>}
          </nav>
        </div>
        {cats.length > 0 && (
          <div className="border-t border-line">
            <nav className="scrollbar-none mx-auto flex max-w-7xl gap-1 overflow-x-auto px-4 text-sm font-medium" aria-label="Categories">
              <Link to="/shop" className={`shrink-0 border-b-2 px-3 py-2.5 ${loc.pathname === '/shop' && !activeCat ? 'border-brand text-brand' : 'border-transparent text-ink hover:text-brand'}`}>All products</Link>
              {cats.map((c) => (
                <Link key={c.slug} to={`/shop?category=${c.slug}`}
                      className={`shrink-0 border-b-2 px-3 py-2.5 ${activeCat === c.slug ? 'border-brand text-brand' : 'border-transparent text-ink hover:text-brand'}`}>{c.name}</Link>
              ))}
            </nav>
          </div>
        )}
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:py-8">
        <Outlet />
      </main>

      {config?.whatsapp && (
        <a href={`https://wa.me/${config.whatsapp}?text=${encodeURIComponent('Hi Vindhyan Studio, I have a question about an order.')}`}
           target="_blank" rel="noopener" aria-label="Chat with us on WhatsApp"
           className="fixed bottom-5 right-5 z-40 flex items-center gap-2 rounded-full bg-[#25D366] px-4 py-3 text-sm font-bold text-white shadow-lg transition hover:scale-105">
          <svg viewBox="0 0 24 24" className="h-5 w-5" fill="currentColor" aria-hidden><path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2zm0 18.2c-1.5 0-3-.4-4.3-1.2l-.3-.2-3 .8.8-2.9-.2-.3A8.2 8.2 0 1 1 12 20.2zm4.5-6.1c-.2-.1-1.5-.7-1.7-.8-.2-.1-.4-.1-.6.1l-.8 1c-.1.2-.3.2-.5.1a6.7 6.7 0 0 1-3.3-2.9c-.2-.4.2-.4.7-1.3.1-.2 0-.3 0-.4l-.8-1.8c-.2-.5-.4-.4-.6-.4h-.5a1 1 0 0 0-.7.3 3 3 0 0 0-.9 2.2 5.2 5.2 0 0 0 1.1 2.8 11.9 11.9 0 0 0 4.6 4c1.7.7 2.4.8 3.2.7.5-.1 1.5-.6 1.8-1.2.2-.6.2-1.1.1-1.2l-.4-.2z" /></svg>
          <span className="hidden sm:inline">Chat with us</span>
        </a>
      )}

      <footer className="mt-12 border-t border-line bg-surface">
        <div className="mx-auto grid max-w-7xl gap-8 px-4 py-10 text-sm sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <img src="/brand/logo-full.png" alt="Vindhyan Studio, your idea, our creation" className="h-24 w-auto" />
            <p className="mt-2 text-muted">Your idea, our creation. Personalised 3D printed keychains, gifts, décor and more, printed to order and shipped across India.</p>
            {config?.sellerPhone && <p className="mt-3 text-muted">WhatsApp / call: <a className="font-semibold text-ink hover:text-brand" href={`https://wa.me/${config.whatsapp}`} target="_blank" rel="noopener">{config.sellerPhone}</a></p>}
          </div>
          <FooterCol title="Shop" links={[['All products', '/shop'], ...cats.slice(0, 4).map((c) => [c.name, `/shop?category=${c.slug}`] as [string, string])]} />
          <FooterCol title="Help" links={[['Track your order', '/account/orders'], ['Contact us', '/policies/contact'], ['Shipping policy', '/policies/shipping'], ['Returns & refunds', '/policies/refund']]} />
          <FooterCol title="Legal" links={[['Privacy policy', '/policies/privacy'], ['Terms of service', '/policies/terms']]} />
        </div>
        <div className="border-t border-line">
          <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-2 px-4 py-4 text-xs text-muted">
            <span>
              © {new Date().getFullYear()} Vindhyan Studio{config?.legalName ? <>, a brand of {config.legalName}</> : null}. All rights reserved.
              {config?.gstin && <> · GSTIN {config.gstin}</>}
              {config?.sellerAddress && <span className="block sm:inline"> · {config.sellerAddress}</span>}
            </span>
            <a href={DEVELOPER_URL} target="_blank" rel="noopener" className="hover:text-ink">
              Powered by <b className="font-semibold text-ink underline decoration-brand/40 underline-offset-2">Mahaan Srivastav</b>
            </a>
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
      {!!count && <span className="absolute -top-0.5 right-0.5 min-w-4 rounded-full bg-accent px-1 text-center text-[10px] font-bold leading-4 text-white">{count}</span>}
    </NavLink>
  )
}

function FooterCol({ title, links }: { title: string; links: [string, string][] }) {
  return (
    <div>
      <p className="font-bold">{title}</p>
      <ul className="mt-3 space-y-2 text-muted">
        {links.map(([label, to]) => <li key={to}><Link to={to} className="hover:text-brand">{label}</Link></li>)}
      </ul>
    </div>
  )
}
