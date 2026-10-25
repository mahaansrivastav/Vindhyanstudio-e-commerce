import { Link, useSearchParams } from 'react-router-dom'
import { ProductCard } from '../components/ProductCard'
import { Empty, ErrorBox, Spinner } from '../components/ui'
import { api } from '../lib/api'
import type { CategoryDto, ProductCard as Card } from '../lib/types'
import { useAsync } from '../lib/useAsync'

export function Shop() {
  const [params] = useSearchParams()
  const category = params.get('category') ?? ''
  const q = params.get('q') ?? ''
  const cats = useAsync(() => api<CategoryDto[]>('/api/catalogue/categories'), [])
  const qs = new URLSearchParams()
  if (category) qs.set('category', category)
  if (q) qs.set('q', q)
  const products = useAsync(() => api<Card[]>(`/api/catalogue/products?${qs}`), [category, q])
  const current = cats.data?.find((c) => c.slug === category)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold">{q ? `Results for “${q}”` : current?.name ?? 'All products'}</h1>
        {products.data && <p className="mt-1 text-sm text-muted">{products.data.length} item{products.data.length === 1 ? '' : 's'}</p>}
      </div>
      <div className="-mx-4 flex gap-2 overflow-x-auto px-4 pb-1">
        <Link to="/shop" className={`chip shrink-0 ${!category ? 'chip-on' : ''}`}>All</Link>
        {cats.data?.map((c) => (
          <Link key={c.slug} to={`/shop?category=${c.slug}`} className={`chip shrink-0 ${category === c.slug ? 'chip-on' : ''}`}>{c.name}</Link>
        ))}
      </div>
      {products.loading && <Spinner />}
      {products.error && <ErrorBox message={products.error} onRetry={products.reload} />}
      {products.data?.length === 0 && <Empty title="Nothing found">Try a different search or <Link className="underline" to="/shop">browse everything</Link>.</Empty>}
      <div className="grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
        {products.data?.map((p) => <ProductCard key={p.sku} p={p} />)}
      </div>
    </div>
  )
}
