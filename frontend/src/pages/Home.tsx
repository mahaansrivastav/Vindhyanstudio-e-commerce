import { Link } from 'react-router-dom'
import { ProductCard } from '../components/ProductCard'
import { ErrorBox, ProductImage, Spinner } from '../components/ui'
import { api } from '../lib/api'
import type { CategoryDto, ProductCard as Card } from '../lib/types'
import { useAsync } from '../lib/useAsync'

export function Home() {
  const cats = useAsync(() => api<CategoryDto[]>('/api/catalogue/categories'), [])
  const products = useAsync(() => api<Card[]>('/api/catalogue/products'), [])

  return (
    <div className="space-y-12">
      <section className="overflow-hidden rounded-3xl bg-ink px-6 py-12 text-white sm:px-12 sm:py-16">
        <p className="text-sm font-semibold uppercase tracking-widest text-orange-300">Printed layer by layer</p>
        <h1 className="mt-3 max-w-xl text-4xl font-bold sm:text-5xl">Gifts made from your photos, names & ideas.</h1>
        <p className="mt-4 max-w-lg text-stone-300">Lithophanes, name keyrings, mini figurines, lamps and more, 3D printed to order and shipped across India.</p>
        <div className="mt-8 flex flex-wrap gap-3">
          <Link to="/shop" className="btn bg-white text-ink hover:bg-stone-200">Shop all</Link>
          <Link to="/shop?category=lithophane-photos" className="btn border border-white/30 hover:bg-white/10">Turn a photo into a lithophane</Link>
        </div>
      </section>

      <section>
        <h2 className="text-2xl font-bold">Shop by category</h2>
        {cats.error && <div className="mt-4"><ErrorBox message={cats.error} onRetry={cats.reload} /></div>}
        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {cats.data?.map((c) => (
            <Link key={c.slug} to={`/shop?category=${c.slug}`} className="group card overflow-hidden">
              <ProductImage src={c.coverImage} alt={c.name} className="aspect-[4/3] w-full transition group-hover:scale-[1.03]" />
              <div className="p-3">
                <p className="text-sm font-semibold">{c.name}</p>
                <p className="text-xs text-muted">{c.productCount} product{c.productCount === 1 ? '' : 's'}</p>
              </div>
            </Link>
          ))}
        </div>
      </section>

      <section>
        <div className="flex items-end justify-between">
          <h2 className="text-2xl font-bold">Popular right now</h2>
          <Link to="/shop" className="text-sm font-semibold text-brand">View all →</Link>
        </div>
        {products.loading && <Spinner />}
        {products.error && <div className="mt-4"><ErrorBox message={products.error} onRetry={products.reload} /></div>}
        <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
          {products.data?.slice(0, 8).map((p) => <ProductCard key={p.sku} p={p} />)}
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        {[
          ['Made for you', 'Every personalised piece is printed fresh after you order.'],
          ['Secure payment', 'UPI, cards, netbanking and wallets, or cash on delivery.'],
          ['Tracked shipping', 'Courier and tracking link sent as soon as your parcel leaves.'],
        ].map(([t, d]) => (
          <div key={t} className="card p-5">
            <p className="font-semibold">{t}</p>
            <p className="mt-1 text-sm text-muted">{d}</p>
          </div>
        ))}
      </section>
    </div>
  )
}
