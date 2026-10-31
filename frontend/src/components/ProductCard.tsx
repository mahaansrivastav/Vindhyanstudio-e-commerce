import { Link } from 'react-router-dom'
import type { ProductCard as Card } from '../lib/types'
import { useWishlist } from '../state/AppState'
import { ProductImage, StockBadge } from './ui'

export function ProductCard({ p }: { p: Card }) {
  const wish = useWishlist()
  const saved = wish.has(p.sku)
  return (
    <div className="group relative">
      <Link to={`/p/${p.slug}`} className="block">
        <div className="relative overflow-hidden rounded-xl bg-surface">
          <ProductImage src={p.image} alt={p.name} className="aspect-square w-full transition duration-500 group-hover:scale-[1.03]" />
          <div className="absolute left-2.5 top-2.5 flex flex-wrap gap-1">
            {p.stock > 0 && p.customisable && <span className="rounded-md bg-white px-2 py-0.5 text-[11px] font-medium text-ink">Personalise</span>}
            <StockBadge stock={p.stock} />
          </div>
        </div>
        <div className="mt-3">
          <h3 className="line-clamp-2 text-[15px] font-normal leading-snug text-ink">{p.name}</h3>
          <div className="mt-1 flex flex-wrap items-baseline gap-x-2 text-[15px]">
            <span className="font-semibold">₹{p.sellingPrice.toLocaleString('en-IN')}</span>
            {p.mrp > p.sellingPrice && <span className="text-sm text-muted line-through">₹{p.mrp.toLocaleString('en-IN')}</span>}
            {p.offerPct > 0 && p.stock > 0 && <span className="text-sm text-accent">{p.offerPct}% off</span>}
          </div>
        </div>
      </Link>
      <button
        onClick={() => wish.toggle({ sku: p.sku, slug: p.slug, name: p.name, image: p.image, price: p.sellingPrice })}
        className="absolute right-2.5 top-2.5 rounded-full bg-white p-1.5 text-ink/70 transition hover:text-ink"
        aria-label={saved ? 'Remove from wishlist' : 'Add to wishlist'}
        aria-pressed={saved}
      >
        <Heart filled={saved} />
      </button>
    </div>
  )
}

export function Heart({ filled }: { filled: boolean }) {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill={filled ? '#e0245e' : 'none'} stroke={filled ? '#e0245e' : 'currentColor'} strokeWidth="2" aria-hidden>
      <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z" />
    </svg>
  )
}
