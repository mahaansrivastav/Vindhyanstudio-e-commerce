import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Empty, ErrorBox, ProductImage, Spinner } from '../components/ui'
import { api } from '../lib/api'
import { rupees } from '../lib/format'
import type { AdminCategory, AdminProduct } from '../lib/types'
import { useAsync } from '../lib/useAsync'

// ---------------- Product list ----------------

export function ProductList() {
  const [q, setQ] = useState('')
  const [query, setQuery] = useState('')
  const { data, error, loading, reload } = useAsync(() => api<AdminProduct[]>(`/api/admin/products?q=${encodeURIComponent(query)}`), [query])

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <form onSubmit={(e) => { e.preventDefault(); setQuery(q) }} className="flex-1">
          <input className="input max-w-sm" placeholder="Search by name or SKU" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search products" />
        </form>
        <Link to="/admin/categories" className="btn-outline">Categories</Link>
        <Link to="/admin/products/new" className="btn-primary">+ Add product</Link>
      </div>
      {loading && !data && <Spinner />}
      {error && <ErrorBox message={error} onRetry={reload} />}
      {data?.length === 0 && <Empty title="No products yet"><Link to="/admin/products/new" className="btn-primary mt-2">Add your first product</Link></Empty>}
      {data && data.length > 0 && (
        <div className="card divide-y divide-line">
          {data.map((p) => (
            <Link key={p.sku} to={`/admin/products/${p.sku}`} className={`flex items-center gap-4 p-3 hover:bg-stone-50 ${p.active ? '' : 'opacity-60'}`}>
              <ProductImage src={p.images[0]?.url ?? null} alt={p.name} className="h-14 w-14 shrink-0 rounded-lg" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold">{p.name}</p>
                <p className="text-xs text-muted">{p.sku} · {p.category} · {p.images.length} photo{p.images.length === 1 ? '' : 's'}</p>
              </div>
              <div className="hidden text-right text-sm sm:block">
                <p className="font-semibold">{rupees(p.sellingPrice)}</p>
                <p className={`text-xs ${p.stock <= 0 ? 'text-bad' : p.stock <= 3 ? 'text-warn' : 'text-muted'}`}>{p.stock} in stock</p>
              </div>
              <div className="flex w-24 shrink-0 flex-col items-end gap-1">
                <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${p.active ? 'bg-green-50 text-green-800' : 'bg-stone-100 text-stone-600'}`}>{p.active ? 'Live' : 'Hidden'}</span>
                <span className="text-[11px] text-muted">{p.source === 'EXCEL' ? 'from Excel' : 'managed here'}</span>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}

// ---------------- Product editor ----------------

interface Form { sku: string; name: string; categoryId: string; description: string; mrp: string; sellingPrice: string; stock: string; active: boolean }
const EMPTY: Form = { sku: '', name: '', categoryId: '', description: '', mrp: '', sellingPrice: '', stock: '10', active: true }

export function ProductEditor() {
  const { sku } = useParams()
  const isNew = !sku
  const nav = useNavigate()
  const cats = useAsync(() => api<AdminCategory[]>('/api/admin/categories'), [])
  const [product, setProduct] = useState<AdminProduct | null>(null)
  const [form, setForm] = useState<Form>(EMPTY)
  const [queued, setQueued] = useState<File[]>([])
  const [err, setErr] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [loadErr, setLoadErr] = useState<string | null>(null)

  useEffect(() => {
    if (isNew) { setProduct(null); setForm(EMPTY); return }
    api<AdminProduct>(`/api/admin/products/${sku}`).then((p) => {
      setProduct(p)
      setForm({ sku: p.sku, name: p.name, categoryId: String(p.categoryId), description: p.description ?? '', mrp: String(p.mrp),
        sellingPrice: String(p.sellingPrice), stock: String(p.stock), active: p.active })
    }).catch((e: Error) => setLoadErr(e.message))
  }, [sku, isNew])

  if (loadErr) return <ErrorBox message={loadErr} />
  if (!isNew && !product) return <Spinner />

  const locked = product?.source === 'EXCEL'
  const set = (k: keyof Form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    setForm({ ...form, [k]: e.target.type === 'checkbox' ? (e.target as HTMLInputElement).checked : e.target.value })
    setMsg(null)
  }
  const mrp = Number(form.mrp), price = Number(form.sellingPrice)
  const offer = mrp > 0 && price > 0 && price <= mrp ? Math.round(((mrp - price) / mrp) * 100) : null

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    setErr(null); setMsg(null)
    if (!form.categoryId) return setErr('Choose a category')
    if (price > mrp) return setErr('Selling price can’t be higher than MRP')
    setBusy(true)
    try {
      const body = { sku: isNew ? form.sku.trim() : product!.sku, name: form.name, categoryId: Number(form.categoryId), description: form.description,
        mrp, sellingPrice: price, stock: Number(form.stock), active: form.active }
      let saved = isNew
        ? await api<AdminProduct>('/api/admin/products', { body })
        : await api<AdminProduct>(`/api/admin/products/${product!.sku}`, { method: 'PUT', body })
      if (queued.length) saved = await uploadFiles(saved.sku, queued)
      setQueued([])
      setProduct(saved)
      setMsg(isNew ? 'Product created and live on the site.' : 'Saved. Changes are live.')
      if (isNew) nav(`/admin/products/${saved.sku}`, { replace: true })
    } catch (e) {
      setErr((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const takeOver = async () => {
    setProduct(await api<AdminProduct>(`/api/admin/products/${product!.sku}/take-over`, { method: 'POST' }))
    setMsg('This product is now managed here. The Excel row for it will be ignored.')
  }

  return (
    <form onSubmit={save} className="grid gap-6 lg:grid-cols-[1fr_360px]">
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <Link to="/admin/products" className="text-sm text-muted">← Products</Link>
          <h2 className="text-2xl font-bold">{isNew ? 'Add product' : product!.name}</h2>
          {product && product.active && <a href={`/p/${product.slug}`} target="_blank" rel="noreferrer" className="text-sm font-semibold text-brand">View on site ↗</a>}
        </div>

        {locked && (
          <div className="rounded-xl bg-amber-50 p-4 text-sm text-amber-900">
            This product comes from the Excel sheet, so edits here would be overwritten by the next sync.
            <button type="button" onClick={takeOver} className="ml-2 font-semibold underline">Manage here instead</button>
          </div>
        )}

        <fieldset disabled={locked} className="card grid gap-4 p-5 sm:grid-cols-2">
          <label className="sm:col-span-2"><span className="label">Product name</span>
            <input className="input" required maxLength={200} value={form.name} onChange={set('name')} placeholder="e.g. Custom Name Keyring" />
          </label>
          <label><span className="label">Category</span>
            <select className="input" required value={form.categoryId} onChange={set('categoryId')}>
              <option value="">Choose…</option>
              {cats.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
            <Link to="/admin/categories" className="mt-1 inline-block text-xs text-brand">+ New category</Link>
          </label>
          <label><span className="label">SKU {isNew && '(leave blank to auto-create)'}</span>
            <input className="input font-mono uppercase" maxLength={50} value={form.sku} onChange={set('sku')} disabled={!isNew} placeholder="auto" />
          </label>
          <label><span className="label">MRP (₹)</span>
            <input className="input" required type="number" min={1} step="1" inputMode="numeric" value={form.mrp} onChange={set('mrp')} />
          </label>
          <label><span className="label">Selling price (₹)</span>
            <input className="input" required type="number" min={1} step="1" inputMode="numeric" value={form.sellingPrice} onChange={set('sellingPrice')} />
            {offer !== null && offer > 0 && <span className="mt-1 block text-xs text-ok">Shows as {offer}% off</span>}
          </label>
          <label><span className="label">Stock</span>
            <input className="input" required type="number" min={0} step="1" value={form.stock} onChange={set('stock')} />
          </label>
          <label className="flex items-center gap-2 self-end pb-2 text-sm">
            <input type="checkbox" checked={form.active} onChange={set('active')} /> Show on website
          </label>
          <label className="sm:col-span-2"><span className="label">Description</span>
            <textarea className="input" rows={5} maxLength={4000} value={form.description} onChange={set('description')}
                      placeholder="What it is, size, material, what the customer can personalise…" />
          </label>
        </fieldset>
      </div>

      <aside className="space-y-4">
        {product && <Personalisation product={product} onChange={setProduct} />}
        <Photos product={product} locked={locked} queued={queued} setQueued={setQueued} onChange={setProduct} onError={setErr} />
        <div className="card space-y-3 p-4 lg:sticky lg:top-24">
          {err && <p className="text-sm text-bad" role="alert">{err}</p>}
          {msg && <p className="text-sm text-ok" role="status">{msg}</p>}
          <button className="btn-primary w-full" disabled={busy || locked}>{busy ? 'Saving…' : isNew ? 'Create product' : 'Save changes'}</button>
        </div>
      </aside>
    </form>
  )
}

function Personalisation({ product, onChange }: { product: AdminProduct; onChange: (p: AdminProduct) => void }) {
  const [value, setValue] = useState(product.customisation ?? '')
  const [msg, setMsg] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const presets: [string, string][] = [['Name keychain', 'style, colour, text'], ['Name / text', 'text'], ['1 photo', 'photo:1, notes'], ['2–3 photos', 'photo:2-3, notes'], ['None', '']]
  const save = async (v: string) => {
    setErr(null); setMsg(null)
    try {
      onChange(await api<AdminProduct>(`/api/admin/products/${product.sku}/customisation`, { method: 'PUT', body: { customisation: v } }))
      setValue(v)
      setMsg('Saved')
    } catch (e) { setErr((e as Error).message) }
  }
  return (
    <section className="card space-y-3 p-4">
      <h3 className="label">Personalisation</h3>
      <p className="text-xs text-muted">What the customer fills in for this product. Empty = category default{product.categoryCustomisation ? ` (${product.categoryCustomisation})` : ' (none)'}.</p>
      <div className="flex flex-wrap gap-1.5">
        {presets.map(([label, v]) => <button key={label} type="button" onClick={() => save(v)} className={`chip py-1 text-xs ${value === v ? 'chip-on' : ''}`}>{label}</button>)}
      </div>
      <div className="flex gap-2">
        <input className="input py-2 text-xs" value={value} onChange={(e) => { setValue(e.target.value); setMsg(null) }} placeholder="e.g. style, colour, text" />
        <button type="button" className="btn-outline py-2 text-xs" onClick={() => save(value)}>Save</button>
      </div>
      {err && <p className="text-xs text-bad">{err}</p>}
      {msg && <p className="text-xs text-ok">{msg}</p>}
    </section>
  )
}

async function uploadFiles(sku: string, files: File[]) {
  const form = new FormData()
  files.forEach((f) => form.append('files', f))
  return api<AdminProduct>(`/api/admin/products/${sku}/images`, { form })
}

function Photos({ product, locked, queued, setQueued, onChange, onError }: {
  product: AdminProduct | null; locked: boolean; queued: File[]; setQueued: (f: File[]) => void
  onChange: (p: AdminProduct) => void; onError: (e: string | null) => void
}) {
  const [busy, setBusy] = useState(false)
  const [drag, setDrag] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const images = product?.images ?? []

  const add = async (list: FileList | null) => {
    if (!list || locked) return
    onError(null)
    const files = Array.from(list).filter((f) => ['image/jpeg', 'image/png', 'image/webp'].includes(f.type))
    if (files.length < list.length) onError('Only JPG, PNG or WebP photos are allowed')
    if (files.some((f) => f.size > 10 * 1024 * 1024)) return onError('Each photo must be under 10 MB')
    if (!product) { setQueued([...queued, ...files].slice(0, 10)); return }
    setBusy(true)
    try { onChange(await uploadFiles(product.sku, files)) } catch (e) { onError((e as Error).message) } finally { setBusy(false) }
  }
  const remove = async (id: number) => {
    if (!product || !confirm('Delete this photo?')) return
    onChange(await api<AdminProduct>(`/api/admin/products/${product.sku}/images/${id}`, { method: 'DELETE' }))
  }
  const move = async (idx: number, dir: -1 | 1) => {
    if (!product) return
    const ids = images.map((i) => i.id)
    const j = idx + dir
    if (j < 0 || j >= ids.length) return
    ;[ids[idx], ids[j]] = [ids[j], ids[idx]]
    onChange(await api<AdminProduct>(`/api/admin/products/${product.sku}/images/order`, { method: 'PUT', body: ids }))
  }

  return (
    <section className="card space-y-3 p-4">
      <h3 className="label">Photos (first one is the cover)</h3>
      <div className="grid grid-cols-3 gap-2">
        {images.map((img, i) => (
          <div key={img.id} className="group relative">
            <img src={img.url} alt="" className="aspect-square w-full rounded-lg object-cover" />
            {i === 0 && <span className="absolute left-1 top-1 rounded bg-ink/80 px-1.5 text-[10px] font-semibold text-white">Cover</span>}
            {!locked && (
              <div className="absolute inset-x-1 bottom-1 flex justify-between opacity-100 sm:opacity-0 sm:group-hover:opacity-100">
                <button type="button" onClick={() => move(i, -1)} disabled={i === 0} className="h-6 w-6 rounded bg-white/90 text-xs shadow disabled:opacity-30" aria-label="Move left">←</button>
                <button type="button" onClick={() => remove(img.id)} className="h-6 w-6 rounded bg-white/90 text-xs text-bad shadow" aria-label="Delete photo">✕</button>
                <button type="button" onClick={() => move(i, 1)} disabled={i === images.length - 1} className="h-6 w-6 rounded bg-white/90 text-xs shadow disabled:opacity-30" aria-label="Move right">→</button>
              </div>
            )}
          </div>
        ))}
        {queued.map((f, i) => (
          <div key={i} className="relative">
            <img src={URL.createObjectURL(f)} alt="" className="aspect-square w-full rounded-lg object-cover opacity-70" />
            <button type="button" onClick={() => setQueued(queued.filter((_, k) => k !== i))} className="absolute right-1 top-1 h-6 w-6 rounded bg-white/90 text-xs text-bad shadow" aria-label="Remove">✕</button>
          </div>
        ))}
      </div>
      {!locked && (
        <div
          onDragOver={(e) => { e.preventDefault(); setDrag(true) }}
          onDragLeave={() => setDrag(false)}
          onDrop={(e) => { e.preventDefault(); setDrag(false); add(e.dataTransfer.files) }}
          onClick={() => input.current?.click()}
          className={`cursor-pointer rounded-xl border-2 border-dashed p-5 text-center text-sm ${drag ? 'border-brand bg-brand-soft' : 'border-line text-muted hover:border-brand'}`}
        >
          {busy ? 'Uploading…' : <>Drop photos here or <b className="text-brand">browse</b><br /><span className="text-xs">JPG, PNG, WebP · up to 10 MB each · max 10</span></>}
          <input ref={input} type="file" accept="image/jpeg,image/png,image/webp" multiple className="sr-only" onChange={(e) => { add(e.target.files); e.target.value = '' }} />
        </div>
      )}
      {!product && queued.length > 0 && <p className="text-xs text-muted">Photos upload when you click “Create product”.</p>}
    </section>
  )
}

// ---------------- Categories ----------------

const CAT_EMPTY = { name: '', hsnCode: '', gstRate: '18', weightG: '100', lengthCm: '10', widthCm: '10', heightCm: '5', leadTimeDays: '3', customisation: '', colours: '', sizes: '', returnable: false }
type CatForm = typeof CAT_EMPTY

export function Categories() {
  const { data, error, loading, reload } = useAsync(() => api<AdminCategory[]>('/api/admin/categories'), [])
  const [editing, setEditing] = useState<AdminCategory | 'new' | null>(null)

  return (
    <div className="max-w-4xl space-y-4">
      <div className="flex items-center gap-3">
        <Link to="/admin/products" className="text-sm text-muted">← Products</Link>
        <h2 className="text-2xl font-bold">Categories</h2>
        <button className="btn-primary ml-auto" onClick={() => setEditing('new')}>+ Add category</button>
      </div>
      <p className="text-sm text-muted">Category settings apply to every product in it: GST, shipping size/weight, making time and what the customer can personalise. Categories that are also in the Excel “categories” sheet get overwritten by the next sync.</p>
      {editing && <CategoryEditor initial={editing === 'new' ? null : editing} onDone={() => { setEditing(null); reload() }} />}
      {loading && !data && <Spinner />}
      {error && <ErrorBox message={error} onRetry={reload} />}
      <div className="card divide-y divide-line">
        {data?.map((c) => (
          <button key={c.id} onClick={() => setEditing(c)} className="flex w-full items-center gap-3 p-3 text-left text-sm hover:bg-stone-50">
            <span className="flex-1"><b>{c.name}</b> <span className="text-muted">· {c.productCount} products · GST {c.gstRate}% · {c.leadTimeDays} days to make</span></span>
            <span className="text-xs text-muted">{c.customisation || 'no personalisation'}</span>
          </button>
        ))}
      </div>
    </div>
  )
}

function CategoryEditor({ initial, onDone }: { initial: AdminCategory | null; onDone: () => void }) {
  const [f, setF] = useState<CatForm>(initial ? {
    name: initial.name, hsnCode: initial.hsnCode ?? '', gstRate: String(initial.gstRate), weightG: String(initial.weightG),
    lengthCm: String(initial.lengthCm), widthCm: String(initial.widthCm), heightCm: String(initial.heightCm),
    leadTimeDays: String(initial.leadTimeDays), customisation: initial.customisation ?? '', colours: initial.colours ?? '',
    sizes: initial.sizes ?? '', returnable: initial.returnable,
  } : CAT_EMPTY)
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const set = (k: keyof CatForm) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  const save = async (e: React.FormEvent) => {
    e.preventDefault()
    setErr(null); setBusy(true)
    const body = { ...f, gstRate: Number(f.gstRate), weightG: Number(f.weightG), lengthCm: Number(f.lengthCm), widthCm: Number(f.widthCm),
      heightCm: Number(f.heightCm), leadTimeDays: Number(f.leadTimeDays) }
    try {
      if (initial) await api(`/api/admin/categories/${initial.id}`, { method: 'PUT', body })
      else await api('/api/admin/categories', { body })
      onDone()
    } catch (e) { setErr((e as Error).message) } finally { setBusy(false) }
  }

  const field = (k: keyof CatForm, label: string, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <label><span className="label">{label}</span><input className="input" value={f[k] as string} onChange={set(k)} {...props} /></label>
  )

  return (
    <form onSubmit={save} className="card grid gap-3 border-brand p-5 sm:grid-cols-4">
      <div className="sm:col-span-2">{field('name', 'Name', { required: true, maxLength: 100 })}</div>
      {field('hsnCode', 'HSN code', { maxLength: 20 })}
      {field('gstRate', 'GST %', { type: 'number', min: 0, max: 28, step: '0.01', required: true })}
      {field('weightG', 'Weight per item (g)', { type: 'number', min: 1, required: true })}
      {field('lengthCm', 'Length (cm)', { type: 'number', min: 1, step: '0.1', required: true })}
      {field('widthCm', 'Width (cm)', { type: 'number', min: 1, step: '0.1', required: true })}
      {field('heightCm', 'Height (cm)', { type: 'number', min: 1, step: '0.1', required: true })}
      {field('leadTimeDays', 'Days to make', { type: 'number', min: 0, max: 60, required: true })}
      <div className="sm:col-span-3">{field('customisation', 'Personalisation options', { placeholder: 'e.g. photo:1, text, colour, size' })}
        <p className="mt-1 text-xs text-muted">Options: <code>photo:1</code> or <code>photo:2-3</code>, <code>text</code> (required) or <code>text?</code> (optional), <code>colour</code>, <code>size</code>, <code>notes</code>. Leave blank for ready-stock items.</p>
      </div>
      <div className="sm:col-span-2">{field('colours', 'Colours (comma-separated)', { placeholder: 'Black, White, Red' })}</div>
      <div className="sm:col-span-2">{field('sizes', 'Sizes / variants (comma-separated)', { placeholder: 'Small, Medium, Large' })}</div>
      <label className="flex items-center gap-2 text-sm sm:col-span-4"><input type="checkbox" checked={f.returnable} onChange={set('returnable')} /> Returnable</label>
      {err && <p className="text-sm text-bad sm:col-span-4">{err}</p>}
      <div className="flex gap-2 sm:col-span-4">
        <button className="btn-primary" disabled={busy}>{busy ? 'Saving…' : initial ? 'Save category' : 'Create category'}</button>
        <button type="button" className="btn-ghost" onClick={onDone}>Cancel</button>
      </div>
    </form>
  )
}
