import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth, useCart, useConfig, useWishlist } from '../state/AppState'

export function Layout() {
  const { profile } = useAuth()
  const cart = useCart()
  const wish = useWishlist()
  const config = useConfig()
  const nav = useNavigate()
  const [params] = useSearchParams()
  const [q, setQ] = useState(params.get('q') ?? '')

  const search = (e: React.FormEvent) => {
    e.preventDefault()
    nav(q.trim() ? `/shop?q=${encodeURIComponent(q.trim())}` : '/shop')
  }

  return (
    <div className="flex min-h-screen flex-col">
      {config?.paymentProvider === 'mock' && (
        <div className="bg-amber-100 px-4 py-1.5 text-center text-xs font-medium text-amber-900">
          Test mode: payments, shipping and messages are simulated.
        </div>
      )}
      <header className="sticky top-0 z-30 border-b border-line bg-paper/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3">
          <Link to="/" className="flex shrink-0 items-center gap-2">
            <img src="/favicon.svg" alt="" className="h-8 w-8" />
            <span className="hidden font-display text-xl font-bold sm:inline">Vindhyan Studio</span>
          </Link>
          <form onSubmit={search} className="mx-auto w-full max-w-md" role="search">
            <input className="input py-2" placeholder="Search lithophanes, keyrings, lamps…" value={q}
                   onChange={(e) => setQ(e.target.value)} aria-label="Search products" />
          </form>
          <nav className="flex shrink-0 items-center gap-1 text-sm font-medium">
            <NavLink to="/wishlist" className="btn-ghost px-2.5" aria-label="Wishlist">
              ♡<span className="hidden md:inline">Wishlist</span>{wish.items.length > 0 && <Count n={wish.items.length} />}
            </NavLink>
            <NavLink to="/cart" className="btn-ghost px-2.5" aria-label="Cart">
              🛍<span className="hidden md:inline">Cart</span>{cart.count > 0 && <Count n={cart.count} />}
            </NavLink>
            {profile ? (
              <NavLink to="/account" className="btn-ghost px-2.5">
                <span aria-hidden>👤</span><span className="hidden md:inline">Account</span>
              </NavLink>
            ) : (
              <NavLink to="/login" className="btn-ghost px-2.5">Login</NavLink>
            )}
            {profile?.admin && <NavLink to="/admin" className="btn-outline px-2.5 py-1.5">Admin</NavLink>}
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:py-8">
        <Outlet />
      </main>

      <footer className="border-t border-line bg-card">
        <div className="mx-auto grid max-w-6xl gap-6 px-4 py-8 text-sm sm:grid-cols-3">
          <div>
            <p className="font-display text-lg font-bold">Vindhyan Studio</p>
            <p className="mt-1 text-muted">Custom 3D printed gifts and décor, made to order in India.</p>
          </div>
          <div className="flex flex-col gap-1.5">
            <Link to="/shop">Shop all</Link>
            <Link to="/account/orders">Track order</Link>
            <Link to="/policies/contact">Contact us</Link>
          </div>
          <div className="flex flex-col gap-1.5">
            <Link to="/policies/shipping">Shipping policy</Link>
            <Link to="/policies/refund">Returns & refunds</Link>
            <Link to="/policies/privacy">Privacy policy</Link>
            <Link to="/policies/terms">Terms of service</Link>
          </div>
        </div>
      </footer>
    </div>
  )
}

function Count({ n }: { n: number }) {
  return <span className="ml-0.5 rounded-full bg-brand px-1.5 text-[11px] font-bold leading-5 text-white">{n}</span>
}
