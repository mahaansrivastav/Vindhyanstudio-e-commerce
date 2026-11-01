// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { KeyringPreview } from '../components/KeyringPreview'
import { ProductCard } from '../components/ProductCard'
import { ErrorBox, ProductImage, Spinner } from '../components/ui'
import { api } from '../lib/api'
import type { CategoryDto, ProductCard as Card } from '../lib/types'
import { useAsync } from '../lib/useAsync'

const DEMO_NAMES = ['Aarav', 'Priya', 'Rohan', 'Ananya', 'Kabir']
const DEMO_COLOURS = ['Blue', 'Red', 'Black', 'Pink', 'Yellow']
const DEMO_STYLES = ['Classic', 'Script', 'Bold', 'Rounded', 'Script']

export function Home() {
  const cats = useAsync(() => api<CategoryDto[]>('/api/catalogue/categories'), [])
  const products = useAsync(() => api<Card[]>('/api/catalogue/products'), [])
  const [i, setI] = useState(0)
  useEffect(() => {
    const t = setInterval(() => setI((n) => (n + 1) % DEMO_NAMES.length), 2200)
    return () => clearInterval(t)
  }, [])
  const keyring = products.data?.find((p) => p.categorySlug.includes('key') && p.customisable)

  const topCats = [...(cats.data ?? [])].sort((a, b) => b.productCount - a.productCount).slice(0, 8)

  return (
    <div className="space-y-16 sm:space-y-20">
      {/* Hero */}
      <section className="grid items-center gap-8 pt-2 lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)] lg:gap-14 lg:pt-6">
        <div>
          <h1 className="text-[2.6rem] leading-[1.04] sm:text-6xl">Your name.<br />Printed in 3D.</h1>
          <p className="mt-5 max-w-md text-lg leading-relaxed text-muted">Personalised keychains, idols and décor, made to order.</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to={keyring ? `/p/${keyring.slug}` : '/shop?category=keychain'} className="btn-primary w-full px-6 py-3 text-[15px] sm:w-auto">Design your keychain</Link>
            <Link to="/shop" className="btn-outline w-full px-6 py-3 text-[15px] sm:w-auto">Shop all products</Link>
          </div>
        </div>
        <KeyringPreview text={DEMO_NAMES[i]} colour={DEMO_COLOURS[i]} style={DEMO_STYLES[i]} className="aspect-[4/3] rounded-2xl ring-1 ring-line" />
      </section>

      {/* Categories */}
      <section>
        <SectionHead title="Shop by category" link="/shop" linkLabel="All categories" />
        {cats.error && <ErrorBox message={cats.error} onRetry={cats.reload} />}
        <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {topCats.map((c) => (
            <Link key={c.slug} to={`/shop?category=${c.slug}`}
                  className="group flex items-center gap-3 rounded-xl bg-card p-2 pr-4 shadow-[0_1px_2px_rgba(0,0,0,0.04)] ring-1 ring-line transition hover:ring-ink/20">
              <div className="h-14 w-14 shrink-0 overflow-hidden rounded-lg bg-surface">
                <ProductImage src={c.coverImage} alt="" className="h-full w-full transition duration-300 group-hover:scale-105" />
              </div>
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{c.name}</p>
                <p className="text-xs text-muted">{c.productCount} item{c.productCount === 1 ? '' : 's'}</p>
              </div>
            </Link>
          ))}
        </div>
      </section>

      {/* Products */}
      <section>
        <SectionHead title="Bestsellers" link="/shop" linkLabel="View all" />
        {products.loading && <Spinner />}
        {products.error && <ErrorBox message={products.error} onRetry={products.reload} />}
        <div className="mt-6 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4 lg:gap-x-6">
          {products.data?.slice(0, 8).map((p) => <ProductCard key={p.sku} p={p} />)}
        </div>
      </section>

    </div>
  )
}

function SectionHead({ title, link, linkLabel }: { title: string; link: string; linkLabel: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <h2 className="text-2xl sm:text-3xl">{title}</h2>
      <Link to={link} className="shrink-0 text-sm font-medium text-ink/70 hover:text-ink">{linkLabel} ›</Link>
    </div>
  )
}
