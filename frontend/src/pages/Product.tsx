import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { PincodeCheck } from '../components/PincodeCheck'
import { Heart } from '../components/ProductCard'
import { ErrorBox, Price, ProductImage, Spinner, StockBadge } from '../components/ui'
import { api } from '../lib/api'
import type { ProductDetail } from '../lib/types'
import { useAsync } from '../lib/useAsync'
import { useAuth, useCart, useWishlist } from '../state/AppState'

interface Upload { key: string; preview: string }

export function Product() {
  const { slug } = useParams()
  const { data: p, error, loading, reload } = useAsync(() => api<ProductDetail>(`/api/catalogue/products/${slug}`), [slug])
  const [img, setImg] = useState(0)
  const [qty, setQty] = useState(1)
  const [text, setText] = useState('')
  const [colour, setColour] = useState('')
  const [size, setSize] = useState('')
  const [notes, setNotes] = useState('')
  const [photos, setPhotos] = useState<Upload[]>([])
  const [uploading, setUploading] = useState(false)
  const [formErr, setFormErr] = useState<string | null>(null)
  const [added, setAdded] = useState(false)
  const cart = useCart()
  const wish = useWishlist()
  const { profile } = useAuth()
  const nav = useNavigate()
  const loc = useLocation()

  useEffect(() => {
    if (!p) return
    setImg(0); setColour(p.colours[0] ?? ''); setSize(p.sizes[0] ?? ''); setText(''); setNotes(''); setPhotos([]); setAdded(false)
  }, [p])

  if (loading) return <Spinner />
  if (error || !p) return <ErrorBox message={error ?? 'Not found'} onRetry={reload} />

  const spec = p.customisation
  const madeToOrder = spec.maxPhotos > 0 || spec.text || spec.notes

  const upload = async (files: FileList | null) => {
    if (!files) return
    setFormErr(null)
    const room = spec.maxPhotos - photos.length
    const list = Array.from(files).slice(0, room)
    for (const f of list) {
      if (!['image/jpeg', 'image/png'].includes(f.type)) return setFormErr('Please upload JPG or PNG photos')
      if (f.size > 10 * 1024 * 1024) return setFormErr('Each photo must be under 10 MB')
    }
    setUploading(true)
    try {
      for (const f of list) {
        const form = new FormData()
        form.append('file', f)
        const { key } = await api<{ key: string }>('/api/uploads', { form })
        setPhotos((ps) => [...ps, { key, preview: URL.createObjectURL(f) }])
      }
    } catch (e) {
      setFormErr((e as Error).message)
    } finally {
      setUploading(false)
    }
  }

  const addToCart = (goToCart: boolean) => {
    setFormErr(null)
    if (spec.textRequired && !text.trim()) return setFormErr('Please enter the name/text to print')
    if (photos.length < spec.minPhotos) return setFormErr(`Please upload ${spec.minPhotos === spec.maxPhotos ? spec.minPhotos : `at least ${spec.minPhotos}`} photo${spec.maxPhotos > 1 ? 's' : ''}`)
    const customisation = {
      ...(spec.text && text.trim() ? { text: text.trim() } : {}),
      ...(spec.colour && colour ? { colour } : {}),
      ...(spec.size && size ? { size } : {}),
      ...(spec.notes && notes.trim() ? { notes: notes.trim() } : {}),
      ...(photos.length ? { photos: photos.map((x) => x.key) } : {}),
    }
    cart.add({
      sku: p.sku, slug: p.slug, name: p.name, image: p.images[0] ?? null, price: p.sellingPrice, qty,
      leadTimeDays: p.leadTimeDays, customisation: Object.keys(customisation).length ? customisation : undefined,
    })
    if (goToCart) nav('/cart')
    else setAdded(true)
  }

  const saved = wish.has(p.sku)

  return (
    <div className="grid gap-8 md:grid-cols-2">
      <div className="space-y-3">
        <ProductImage src={p.images[img] ?? null} alt={p.name} className="aspect-square w-full rounded-3xl" />
        {p.images.length > 1 && (
          <div className="flex gap-2 overflow-x-auto">
            {p.images.map((src, i) => (
              <button key={src} onClick={() => setImg(i)} className={`shrink-0 overflow-hidden rounded-xl ring-2 ${i === img ? 'ring-brand' : 'ring-transparent'}`} aria-label={`Photo ${i + 1}`}>
                <img src={src} alt="" className="h-16 w-16 object-cover" />
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="space-y-5">
        <div>
          <Link to={`/shop?category=${p.categorySlug}`} className="text-xs font-semibold uppercase tracking-wide text-brand">{p.category}</Link>
          <h1 className="mt-1 text-3xl font-bold">{p.name}</h1>
          <div className="mt-3 flex items-center gap-3"><Price price={p.sellingPrice} mrp={p.mrp} offer={p.offerPct} size="lg" /><StockBadge stock={p.stock} /></div>
          <p className="mt-1 text-xs text-muted">Inclusive of all taxes</p>
        </div>

        {madeToOrder && (
          <p className="rounded-xl bg-brand-soft px-3 py-2 text-sm text-brand-dark">
            Made to order: printed after you order, ready to ship in about <b>{p.leadTimeDays} days</b>.
            {!p.returnable && ' Personalised items can’t be returned unless damaged or defective.'}
          </p>
        )}

        {spec.colour && p.colours.length > 0 && (
          <fieldset>
            <legend className="label">Colour</legend>
            <div className="flex flex-wrap gap-2">
              {p.colours.map((c) => <button type="button" key={c} onClick={() => setColour(c)} className={`chip ${colour === c ? 'chip-on' : ''}`}>{c}</button>)}
            </div>
          </fieldset>
        )}
        {spec.size && p.sizes.length > 0 && (
          <fieldset>
            <legend className="label">Size / variant</legend>
            <div className="flex flex-wrap gap-2">
              {p.sizes.map((s) => <button type="button" key={s} onClick={() => setSize(s)} className={`chip ${size === s ? 'chip-on' : ''}`}>{s}</button>)}
            </div>
          </fieldset>
        )}
        {spec.text && (
          <label className="block">
            <span className="label">Name / text to print {spec.textRequired ? '' : '(optional)'}</span>
            <input className="input" maxLength={60} value={text} onChange={(e) => { setText(e.target.value); setFormErr(null) }} placeholder="e.g. AARAV" />
            <span className="mt-1 block text-right text-xs text-muted">{text.length}/60</span>
          </label>
        )}
        {spec.maxPhotos > 0 && (
          <div>
            <span className="label">Your photo{spec.maxPhotos > 1 ? 's' : ''} ({spec.minPhotos === spec.maxPhotos ? spec.minPhotos : `${spec.minPhotos}–${spec.maxPhotos}`}) · JPG/PNG up to 10 MB</span>
            {!profile ? (
              <Link to={`/login?next=${encodeURIComponent(loc.pathname)}`} className="btn-outline w-full">Log in to upload photos</Link>
            ) : (
              <div className="flex flex-wrap gap-2">
                {photos.map((ph) => (
                  <div key={ph.key} className="relative">
                    <img src={ph.preview} alt="Your upload" className="h-20 w-20 rounded-xl object-cover" />
                    <button type="button" onClick={() => setPhotos(photos.filter((x) => x.key !== ph.key))}
                            className="absolute -right-1.5 -top-1.5 h-6 w-6 rounded-full bg-ink text-xs text-white" aria-label="Remove photo">×</button>
                  </div>
                ))}
                {photos.length < spec.maxPhotos && (
                  <label className="flex h-20 w-20 cursor-pointer items-center justify-center rounded-xl border-2 border-dashed border-line text-xs text-muted hover:border-brand">
                    {uploading ? '…' : '+ Add'}
                    <input type="file" accept="image/jpeg,image/png" multiple={spec.maxPhotos > 1} className="sr-only" disabled={uploading}
                           onChange={(e) => { upload(e.target.files); e.target.value = '' }} />
                  </label>
                )}
              </div>
            )}
            <p className="mt-2 text-xs text-muted">Use a sharp, well-lit photo. We check every photo before printing and will contact you if it won’t print well. Photos are used only for your order and deleted after delivery.</p>
          </div>
        )}
        {spec.notes && (
          <label className="block">
            <span className="label">Notes for the maker (optional)</span>
            <textarea className="input" rows={3} maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Pose, outfit colours, anything we should know" />
          </label>
        )}

        {p.stock > 0 ? (
          <div className="space-y-3">
            <div className="flex items-center gap-3">
              <span className="label mb-0">Qty</span>
              <div className="flex items-center rounded-xl border border-line">
                <button className="px-3 py-2" onClick={() => setQty(Math.max(1, qty - 1))} aria-label="Decrease">−</button>
                <span className="w-8 text-center text-sm font-semibold">{qty}</span>
                <button className="px-3 py-2" onClick={() => setQty(Math.min(Math.min(20, p.stock), qty + 1))} aria-label="Increase">+</button>
              </div>
            </div>
            {formErr && <p className="text-sm text-bad" role="alert">{formErr}</p>}
            <div className="flex gap-2">
              <button className="btn-primary flex-1" onClick={() => addToCart(true)} disabled={uploading}>Buy now</button>
              <button className="btn-outline flex-1" onClick={() => addToCart(false)} disabled={uploading}>Add to cart</button>
              <button className="btn-outline px-3" onClick={() => wish.toggle({ sku: p.sku, slug: p.slug, name: p.name, image: p.images[0] ?? null, price: p.sellingPrice })}
                      aria-label={saved ? 'Remove from wishlist' : 'Add to wishlist'} aria-pressed={saved}><Heart filled={saved} /></button>
            </div>
            {added && <p className="text-sm text-ok">Added to cart. <Link to="/cart" className="font-semibold underline">View cart</Link></p>}
          </div>
        ) : (
          <button className="btn-outline w-full" disabled>Out of stock</button>
        )}

        <div className="card p-4"><PincodeCheck leadDays={madeToOrder ? p.leadTimeDays : 1} /></div>

        <div>
          <h2 className="font-sans text-sm font-semibold">About this product</h2>
          <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-stone-700">{p.description}</p>
          <p className="mt-3 text-xs text-muted">SKU {p.sku}</p>
        </div>
      </div>
    </div>
  )
}
