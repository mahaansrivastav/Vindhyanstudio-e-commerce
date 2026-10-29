import { Link } from 'react-router-dom'
import type { ProductCard as Card } from '../lib/types'
import { useWishlist } from '../state/AppState'
import { Price, ProductImage, StockBadge } from './ui'

export function ProductCard({ p }: { p: Card }) {
  const wish = useWishlist()
  const saved = wish.has(p.sku)
  return (
    <div className="group relative">
      <Link to={`/p/${p.slug}`} className="block">
        <div className="relative overflow-hidden rounded-2xl bg-surface">
          <ProductImage src={p.image} alt={p.name} className="aspect-square w-full transition duration-300 group-hover:scale-[1.04]" />
          <div className="absolute left-2 top-2 flex flex-wrap gap-1">
            {p.offerPct > 0 && p.stock > 0 && <span className="rounded-full bg-accent px-2 py-0.5 text-xs font-bold text-white">{p.offerPct}% off</span>}
            <StockBadge stock={p.stock} />
          </div>
        </div>
        <div className="mt-3 space-y-1">
          <h3 className="text-sm font-bold leading-snug group-hover:text-brand">{p.name}</h3>
          <Price price={p.sellingPrice} mrp={p.mrp} offer={0} />
          {p.customisable && p.stock > 0 && <p className="text-xs font-semibold text-brand">Personalise it →</p>}
        </div>
      </Link>
      <button
        onClick={() => wish.toggle({ sku: p.sku, slug: p.slug, name: p.name, image: p.image, price: p.sellingPrice })}
        className="absolute right-2 top-2 rounded-full bg-white/95 p-2 shadow-sm transition hover:scale-110"
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
    <svg width="18" height="18" viewBox="0 0 24 24" fill={filled ? '#e0245e' : 'none'} stroke={filled ? '#e0245e' : 'currentColor'} strokeWidth="2" aria-hidden>
      <path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z" />
    </svg>
  )
}
