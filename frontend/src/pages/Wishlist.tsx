// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { Link } from 'react-router-dom'
import { Empty, ProductImage } from '../components/ui'
import { rupees } from '../lib/format'
import { useWishlist } from '../state/AppState'

export function Wishlist() {
  const wish = useWishlist()
  if (!wish.items.length) return <Empty title="Your wishlist is empty">Tap ♡ on any product to save it here.</Empty>
  return (
    <div>
      <h1 className="text-3xl font-semibold">Wishlist</h1>
      <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
        {wish.items.map((w) => (
          <div key={w.sku} className="space-y-2">
            <Link to={`/p/${w.slug}`}><ProductImage src={w.image} alt={w.name} className="aspect-square w-full rounded-xl" /></Link>
            <p className="text-sm font-semibold">{w.name}</p>
            <p className="text-sm">{rupees(w.price)}</p>
            <div className="flex gap-2">
              <Link to={`/p/${w.slug}`} className="btn-primary flex-1 py-1.5">View</Link>
              <button className="btn-ghost py-1.5" onClick={() => wish.toggle(w)}>Remove</button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
