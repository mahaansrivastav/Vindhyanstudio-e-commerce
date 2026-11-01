// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { Link, Navigate } from 'react-router-dom'
import { Empty, ProductImage } from '../components/ui'
import { rupees } from '../lib/format'
import { useAuth, useWishlist } from '../state/AppState'

/** Guests see the wishlist here; signed-in customers see it inside their account. */
export function Wishlist() {
  const { profile } = useAuth()
  if (profile) return <Navigate to="/account/wishlist" replace />
  return (
    <div className="space-y-5 pt-2">
      <h1 className="text-3xl">Wishlist</h1>
      <WishlistGrid />
    </div>
  )
}

export function WishlistGrid() {
  const wish = useWishlist()
  if (!wish.items.length) return <Empty title="Your wishlist is empty"><p>Tap ♡ on any product to save it here.</p><Link to="/shop" className="btn-primary mt-4">Browse products</Link></Empty>
  return (
    <div className="grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3">
      {wish.items.map((w) => (
        <div key={w.sku} className="group">
          <Link to={`/p/${w.slug}`} className="block overflow-hidden rounded-xl bg-surface">
            <ProductImage src={w.image} alt={w.name} className="aspect-square w-full transition duration-500 group-hover:scale-[1.03]" />
          </Link>
          <p className="mt-3 line-clamp-2 text-[15px] leading-snug">{w.name}</p>
          <p className="mt-1 font-semibold">{rupees(w.price)}</p>
          <div className="mt-2 flex gap-2">
            <Link to={`/p/${w.slug}`} className="btn-primary flex-1 py-2">View</Link>
            <button className="btn-outline py-2" onClick={() => wish.toggle(w)} aria-label={`Remove ${w.name}`}>Remove</button>
          </div>
        </div>
      ))}
    </div>
  )
}
