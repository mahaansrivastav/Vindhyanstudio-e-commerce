import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { IconBox, IconPencil, IconPrinter, IconShield, IconTruck } from '../components/Icons'
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

  return (
    <div className="space-y-14">
      {/* Hero */}
      <section className="grid items-center gap-8 overflow-hidden rounded-3xl bg-brand-soft p-6 sm:p-10 lg:grid-cols-2">
        <div>
          <p className="inline-flex rounded-full bg-white px-3 py-1 text-xs font-bold text-brand shadow-sm">Your idea, our creation · 3D printed in India</p>
          <h1 className="mt-4 text-4xl leading-tight sm:text-5xl">Your name, printed in 3D.</h1>
          <p className="mt-4 max-w-md text-lg text-muted">Design a custom keychain in seconds: pick a style and colour, type a name, see it live and order. Printed and shipped to your door.</p>
          <div className="mt-7 flex flex-wrap gap-3">
            <Link to={keyring ? `/p/${keyring.slug}` : '/shop?category=keychain'} className="btn-primary px-6 py-3 text-base">Design your keychain</Link>
            <Link to="/shop" className="btn-outline px-6 py-3 text-base">Browse all products</Link>
          </div>
          <div className="mt-6 flex flex-wrap gap-x-5 gap-y-2 text-sm text-muted">
            <span>✓ Live preview</span><span>✓ Cash on delivery</span><span>✓ Tracked shipping</span>
          </div>
        </div>
        <KeyringPreview text={DEMO_NAMES[i]} colour={DEMO_COLOURS[i]} style={DEMO_STYLES[i]} className="aspect-[4/3] rounded-2xl bg-white shadow-sm" />
      </section>

      {/* Categories */}
      <section>
        <SectionHead title="Shop by category" link="/shop" />
        {cats.error && <ErrorBox message={cats.error} onRetry={cats.reload} />}
        <div className="mt-5 grid grid-cols-2 gap-4 sm:grid-cols-4 lg:grid-cols-8">
          {cats.data?.map((c) => (
            <Link key={c.slug} to={`/shop?category=${c.slug}`} className="group text-center">
              <div className="mx-auto aspect-square w-full max-w-36 overflow-hidden rounded-full bg-surface ring-1 ring-line transition group-hover:ring-2 group-hover:ring-brand">
                <ProductImage src={c.coverImage} alt="" className="h-full w-full transition duration-300 group-hover:scale-105" />
              </div>
              <p className="mt-2 text-sm font-semibold group-hover:text-brand">{c.name}</p>
            </Link>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section className="rounded-3xl border border-line p-6 sm:p-10">
        <h2 className="text-center text-2xl">How it works</h2>
        <div className="mt-8 grid gap-6 sm:grid-cols-3">
          {[
            [<IconPencil key="a" />, '1. Personalise', 'Choose a product, pick the style and colour, add a name or upload a photo.'],
            [<IconPrinter key="b" />, '2. We print it', 'Every piece is 3D printed fresh for you and checked by hand.'],
            [<IconBox key="c" />, '3. Delivered to you', 'Packed with care and shipped with tracking across India.'],
          ].map(([icon, t, d]) => (
            <div key={t as string} className="text-center">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-soft text-brand">{icon}</div>
              <p className="mt-3 font-bold">{t}</p>
              <p className="mx-auto mt-1 max-w-xs text-sm text-muted">{d}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Products */}
      <section>
        <SectionHead title="Bestsellers" link="/shop" />
        {products.loading && <Spinner />}
        {products.error && <ErrorBox message={products.error} onRetry={products.reload} />}
        <div className="mt-5 grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
          {products.data?.slice(0, 8).map((p) => <ProductCard key={p.sku} p={p} />)}
        </div>
      </section>

      {/* Trust */}
      <section className="grid gap-4 sm:grid-cols-3">
        {[
          [<IconShield key="a" />, 'Secure payments', 'UPI, cards, net banking, wallets or cash on delivery.'],
          [<IconTruck key="b" />, 'Free shipping', 'On orders above ₹999, with live order tracking.'],
          [<IconPencil key="c" />, 'Made for you', 'Personalised items printed fresh after you order.'],
        ].map(([icon, t, d]) => (
          <div key={t as string} className="flex gap-4 rounded-2xl bg-surface p-5">
            <div className="text-brand">{icon}</div>
            <div><p className="font-bold">{t}</p><p className="mt-0.5 text-sm text-muted">{d}</p></div>
          </div>
        ))}
      </section>
    </div>
  )
}

function SectionHead({ title, link }: { title: string; link: string }) {
  return (
    <div className="flex items-end justify-between">
      <h2 className="text-2xl">{title}</h2>
      <Link to={link} className="text-sm font-bold text-brand hover:underline">View all →</Link>
    </div>
  )
}
