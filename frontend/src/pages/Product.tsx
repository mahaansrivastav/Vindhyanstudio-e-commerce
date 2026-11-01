// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { IconShield, IconTruck } from '../components/Icons'
import { colourHex, KeyringPreview } from '../components/KeyringPreview'
import { PincodeCheck } from '../components/PincodeCheck'
import { Heart } from '../components/ProductCard'
import { ErrorBox, Price, ProductImage, Spinner, StockBadge } from '../components/ui'
import { api } from '../lib/api'
import type { ProductDetail } from '../lib/types'
import { useAsync } from '../lib/useAsync'
import { useAuth, useCart, useConfig, useWishlist } from '../state/AppState'

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
  const config = useConfig()
  const nav = useNavigate()
  const loc = useLocation()

  useEffect(() => {
    if (!p) return
    setImg(0); setColour(p.colours[0] ?? ''); setSize(p.sizes[0] ?? ''); setText(''); setNotes(''); setPhotos([]); setAdded(false); setQty(1)
  }, [p])

  if (loading) return <Spinner />
  if (error || !p) return <ErrorBox message={error ?? 'Not found'} onRetry={reload} />

  const spec = p.customisation
  const madeToOrder = spec.maxPhotos > 0 || spec.text || spec.notes
  const customisable = madeToOrder || spec.colour || spec.size
  // Name keyrings get the live designer preview as the first gallery slide.
  const hasPreview = spec.text && p.categorySlug.includes('key')
  const slides = hasPreview ? p.images.length + 1 : p.images.length
  const saved = wish.has(p.sku)
  let step = 0

  const upload = async (files: FileList | null) => {
    if (!files) return
    setFormErr(null)
    const list = Array.from(files).slice(0, spec.maxPhotos - photos.length)
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

  return (
    <div className="space-y-6">
      <nav className="truncate text-sm text-muted" aria-label="Breadcrumb">
        <Link to="/" className="hover:text-ink">Home</Link> <span className="mx-1.5 text-line">/</span>
        <Link to={`/shop?category=${p.categorySlug}`} className="hover:text-ink">{p.category}</Link> <span className="mx-1.5 text-line">/</span>
        <span className="text-ink/80">{p.name}</span>
      </nav>

      <div className="grid gap-8 lg:grid-cols-[1.1fr_1fr] lg:gap-12">
        {/* Gallery */}
        <div className="space-y-3 lg:sticky lg:top-36 lg:self-start">
          <div className="overflow-hidden rounded-xl bg-surface">
            {hasPreview && img === 0
              ? <KeyringPreview text={text} colour={colour} style={size} className="aspect-square w-full" />
              : <ProductImage src={p.images[hasPreview ? img - 1 : img] ?? null} alt={p.name} className="aspect-square w-full" />}
          </div>
          {slides > 1 && (
            <div className="flex gap-2 overflow-x-auto p-0.5">
              {hasPreview && (
                <button onClick={() => setImg(0)} className={`flex h-16 w-16 shrink-0 items-center justify-center rounded-lg bg-surface text-[11px] font-medium ${img === 0 ? 'ring-2 ring-ink' : 'ring-1 ring-line'}`}>
                  Your design
                </button>
              )}
              {p.images.map((src, i) => {
                const idx = hasPreview ? i + 1 : i
                return (
                  <button key={src} onClick={() => setImg(idx)} className={`shrink-0 overflow-hidden rounded-lg ${idx === img ? 'ring-2 ring-ink' : 'ring-1 ring-line opacity-80 hover:opacity-100'}`} aria-label={`Photo ${i + 1}`}>
                    <img src={src} alt="" className="h-16 w-16 object-cover" />
                  </button>
                )
              })}
            </div>
          )}
        </div>

        {/* Details + designer */}
        <div className="space-y-6">
          <div>
            <h1 className="text-3xl leading-tight sm:text-4xl">{p.name}</h1>
            <div className="mt-3 flex flex-wrap items-center gap-3"><Price price={p.sellingPrice} mrp={p.mrp} offer={p.offerPct} size="lg" /><StockBadge stock={p.stock} /></div>
            <p className="mt-1.5 text-sm text-muted">Inclusive of all taxes · {madeToOrder ? `Ready to ship in ~${p.leadTimeDays} days` : 'Ships in 1–2 days'}</p>
          </div>

          {customisable && (
            <div className="card divide-y divide-line">
              <div className="px-5 py-4">
                <p className="font-medium">Personalise it</p>
                <p className="text-sm text-muted">{hasPreview ? 'Your choices update the preview instantly.' : 'Tell us how you want it made.'}</p>
              </div>

              {spec.size && p.sizes.length > 0 && (
                <Step n={++step} title={`Choose ${spec.sizeLabel.toLowerCase()}`}>
                  <div className="flex flex-wrap gap-2">
                    {p.sizes.map((s) => (
                      <button type="button" key={s} onClick={() => { setSize(s); if (hasPreview) setImg(0) }} className={`chip ${size === s ? 'chip-on' : ''}`}
                              style={hasPreview ? { fontFamily: styleFont(s) } : undefined}>{s}</button>
                    ))}
                  </div>
                </Step>
              )}

              {spec.colour && p.colours.length > 0 && (
                <Step n={++step} title="Choose colour" aside={colour}>
                  <div className="flex flex-wrap gap-2.5">
                    {p.colours.map((c) => (
                      <button type="button" key={c} onClick={() => { setColour(c); if (hasPreview) setImg(0) }} title={c} aria-label={c} aria-pressed={colour === c}
                              className={`h-9 w-9 rounded-full border border-black/10 ring-offset-2 transition ${colour === c ? 'ring-2 ring-brand' : 'hover:ring-2 hover:ring-line'}`}
                              style={{ background: knownColour(c) ? colourHex(c) : undefined }}>
                        {!knownColour(c) && <span className="text-[10px] font-semibold">{c.slice(0, 3)}</span>}
                      </button>
                    ))}
                  </div>
                </Step>
              )}

              {spec.text && (
                <Step n={++step} title={`Enter name / text${spec.textRequired ? '' : ' (optional)'}`}>
                  <input className="input text-base" maxLength={hasPreview ? 14 : 60} value={text} placeholder="e.g. AARAV"
                         onChange={(e) => { setText(e.target.value); setFormErr(null); if (hasPreview) setImg(0) }} />
                  <span className="mt-1 block text-right text-xs text-muted">{text.length}/{hasPreview ? 14 : 60}</span>
                </Step>
              )}

              {spec.maxPhotos > 0 && (
                <Step n={++step} title={`Upload photo${spec.maxPhotos > 1 ? 's' : ''} (${spec.minPhotos === spec.maxPhotos ? spec.minPhotos : `${spec.minPhotos}–${spec.maxPhotos}`})`}>
                  {!profile ? (
                    <Link to={`/login?next=${encodeURIComponent(loc.pathname)}`} className="btn-outline w-full">Sign in to upload photos</Link>
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
                        <label className="flex h-20 w-20 cursor-pointer items-center justify-center rounded-xl border-2 border-dashed border-line text-xs font-semibold text-muted hover:border-brand hover:text-brand">
                          {uploading ? '…' : '+ Add'}
                          <input type="file" accept="image/jpeg,image/png" multiple={spec.maxPhotos > 1} className="sr-only" disabled={uploading}
                                 onChange={(e) => { upload(e.target.files); e.target.value = '' }} />
                        </label>
                      )}
                    </div>
                  )}
                  <p className="mt-2 text-xs text-muted">JPG/PNG, up to 10 MB. Use a sharp, well-lit photo. We check every photo before printing.</p>
                </Step>
              )}

              {spec.notes && (
                <Step n={++step} title="Notes for the maker (optional)">
                  <textarea className="input" rows={3} maxLength={500} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Pose, outfit colours, anything we should know" />
                </Step>
              )}
            </div>
          )}

          {p.stock > 0 ? (
            <div className="space-y-3">
              <div className="flex items-center gap-3">
                <span className="text-sm text-muted">Quantity</span>
                <div className="flex items-center rounded-lg border border-line">
                  <button className="px-3 py-2 text-lg" onClick={() => setQty(Math.max(1, qty - 1))} aria-label="Decrease">−</button>
                  <span className="w-8 text-center text-sm font-semibold">{qty}</span>
                  <button className="px-3 py-2 text-lg" onClick={() => setQty(Math.min(Math.min(20, p.stock), qty + 1))} aria-label="Increase">+</button>
                </div>
                <span className="ml-auto text-lg font-semibold tracking-tight">₹{(p.sellingPrice * qty).toLocaleString('en-IN')}</span>
              </div>
              {formErr && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-bad" role="alert">{formErr}</p>}
              <div className="flex gap-2">
                <button className="btn-primary flex-1 py-3 text-[15px]" onClick={() => addToCart(true)} disabled={uploading}>Buy now</button>
                <button className="btn-outline flex-1 py-3 text-[15px]" onClick={() => addToCart(false)} disabled={uploading}>Add to cart</button>
                <button className="btn-outline px-3.5" onClick={() => wish.toggle({ sku: p.sku, slug: p.slug, name: p.name, image: p.images[0] ?? null, price: p.sellingPrice })}
                        aria-label={saved ? 'Remove from wishlist' : 'Add to wishlist'} aria-pressed={saved}><Heart filled={saved} /></button>
              </div>
              {added && <p className="text-sm text-ok">✓ Added to cart. <Link to="/cart" className="font-semibold underline">View cart</Link></p>}
            </div>
          ) : (
            <button className="btn-outline w-full py-3.5" disabled>Out of stock</button>
          )}

          <div className="divide-y divide-line rounded-xl border border-line">
            <div className="p-4"><PincodeCheck leadDays={madeToOrder ? p.leadTimeDays : 1} /></div>
            <InfoRow icon={<IconTruck className="h-5 w-5" />}>Free shipping above ₹{config?.freeShippingAbove ?? 999}, with tracking on every order</InfoRow>
            <InfoRow icon={<IconShield className="h-5 w-5" />}>Pay securely by UPI, card or net banking</InfoRow>
            {config?.whatsapp && (
              <InfoRow icon={<IconChat />}>
                Need a custom size or colour?{' '}
                <a href={`https://wa.me/${config.whatsapp}?text=${encodeURIComponent(`Hi! I have a question about ${p.name} (${p.sku}).`)}`} target="_blank" rel="noopener"
                   className="font-medium text-ink underline underline-offset-2">Ask on WhatsApp</a>
              </InfoRow>
            )}
          </div>
          {madeToOrder && !p.returnable && <p className="text-xs text-muted">Personalised items are made just for you and can’t be returned unless damaged or defective. <Link to="/policies/refund" className="underline">Returns policy</Link></p>}

          <div>
            <h2 className="text-lg">Product details</h2>
            <p className="mt-2 whitespace-pre-line text-[15px] leading-relaxed text-ink/75">{p.description}</p>
            <p className="mt-3 text-xs text-muted">SKU {p.sku} · Country of origin: India</p>
          </div>
        </div>
      </div>
    </div>
  )
}

function Step({ n, title, aside, children }: { n: number; title: string; aside?: string; children: React.ReactNode }) {
  return (
    <div className="px-5 py-4">
      <p className="mb-3 flex items-center gap-2 text-sm font-medium">
        <span className="flex h-5 w-5 items-center justify-center rounded-full bg-surface text-[11px] text-muted">{n}</span>
        {title}
        {aside && <span className="font-normal text-muted">· {aside}</span>}
      </p>
      {children}
    </div>
  )
}

function InfoRow({ icon, children }: { icon: React.ReactNode; children: React.ReactNode }) {
  return <div className="flex items-start gap-3 px-4 py-3 text-sm text-ink/80"><span className="mt-px shrink-0 text-ink/50">{icon}</span><span>{children}</span></div>
}

function IconChat() {
  return <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z" /></svg>
}

function knownColour(name: string) {
  return colourHex(name) !== '#1f2328' || name.toLowerCase() === 'black'
}

function styleFont(style: string) {
  return ({ script: 'Pacifico, cursive', bold: '"Archivo Black", sans-serif', rounded: 'Fredoka, sans-serif' } as Record<string, string>)[style.toLowerCase()]
}
