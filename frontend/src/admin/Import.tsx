import { useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { ErrorBox, ProductImage } from '../components/ui'
import { api, downloadFile } from '../lib/api'
import { dateTime } from '../lib/format'
import type { ImportPreview, ImportRow, SyncRun } from '../lib/types'
import { useAsync } from '../lib/useAsync'
import { DownloadIcon } from './Dashboard'

type Filter = 'changes' | 'NEW' | 'UPDATE' | 'ERROR' | 'SAME'

/** Owner workflow: download the list → edit in Excel → upload → review every change → apply. */
export function Import() {
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [filter, setFilter] = useState<Filter>('changes')
  const [busy, setBusy] = useState<'download' | 'check' | 'apply' | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [done, setDone] = useState<{ created: number; updated: number; skipped: number } | null>(null)
  const [drag, setDrag] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const history = useAsync(() => api<SyncRun[]>('/api/admin/products/import/history'), [])

  const download = async () => {
    setBusy('download'); setErr(null)
    try { await downloadFile('/api/admin/products/export', `vindhyan-products-${new Date().toISOString().slice(0, 10)}.xlsx`) }
    catch (e) { setErr((e as Error).message) } finally { setBusy(null) }
  }

  const check = async (f: File) => {
    setErr(null); setDone(null); setPreview(null)
    if (!f.name.toLowerCase().endsWith('.xlsx')) return setErr('Please choose an Excel file (.xlsx).')
    setFile(f); setBusy('check')
    try {
      const form = new FormData()
      form.append('file', f)
      const p = await api<ImportPreview>('/api/admin/products/import/preview', { form })
      setPreview(p)
      setSelected(new Set(p.rows.filter((r) => r.status === 'NEW' || r.status === 'UPDATE').map((r) => r.sku)))
      setFilter(p.errorCount > 0 ? 'ERROR' : 'changes')
    } catch (e) { setErr((e as Error).message); setFile(null) } finally { setBusy(null) }
  }

  const apply = async () => {
    if (!file || selected.size === 0) return
    if (!confirm(`Update ${selected.size} product${selected.size === 1 ? '' : 's'} on the website now?`)) return
    setBusy('apply'); setErr(null)
    try {
      const form = new FormData()
      form.append('file', file)
      form.append('skus', [...selected].join(','))
      setDone(await api('/api/admin/products/import/apply', { form }))
      setPreview(null); setFile(null)
      history.reload()
    } catch (e) { setErr((e as Error).message) } finally { setBusy(null) }
  }

  const reset = () => { setPreview(null); setFile(null); setDone(null); setErr(null) }

  return (
    <div className="max-w-5xl space-y-6">
      <div>
        <h2 className="text-2xl">Update from Excel</h2>
        <p className="mt-1 text-sm text-muted">Change many prices, stock or products at once. Nothing changes on the website until you review and approve.</p>
      </div>

      {done && (
        <div className="rounded-xl border border-green-200 bg-green-50 p-4 text-sm text-green-900">
          <b>Website updated.</b> {done.created} added · {done.updated} updated{done.skipped ? ` · ${done.skipped} not changed` : ''}.{' '}
          {done.created > 0 && <>New products are hidden until they have photos: <Link to="/admin/products?filter=hidden" className="font-medium underline">add photos</Link>.</>}
        </div>
      )}
      {err && <ErrorBox message={err} />}

      {!preview && (
        <ol className="grid gap-4 md:grid-cols-2">
          <li className="card p-5">
            <p className="text-sm text-muted">Step 1</p>
            <h3 className="mt-1 text-lg">Download your product list</h3>
            <p className="mt-1 text-sm text-muted">An Excel file with every product: price, stock and whether it's on the website. Edit it in Excel or Google Sheets.</p>
            <button className="btn-outline mt-4" onClick={download} disabled={busy === 'download'}><DownloadIcon /> {busy === 'download' ? 'Preparing…' : 'Download product list'}</button>
            <ul className="mt-4 space-y-1 text-xs text-muted">
              <li>• Stock: a number, or “Made to order”</li>
              <li>• Show on website: Yes or No</li>
              <li>• New product: add a row with a new SKU</li>
              <li>• Empty cell = keep what's on the website</li>
            </ul>
          </li>
          <li className="card p-5">
            <p className="text-sm text-muted">Step 2</p>
            <h3 className="mt-1 text-lg">Upload the edited file</h3>
            <div
              onDragOver={(e) => { e.preventDefault(); setDrag(true) }}
              onDragLeave={() => setDrag(false)}
              onDrop={(e) => { e.preventDefault(); setDrag(false); if (e.dataTransfer.files[0]) check(e.dataTransfer.files[0]) }}
              onClick={() => input.current?.click()}
              className={`mt-4 flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed px-4 py-10 text-center text-sm transition ${drag ? 'border-ink bg-surface' : 'border-line hover:border-ink/40'}`}
            >
              {busy === 'check' ? <p>Checking your file…</p> : <>
                <p className="font-medium">Drop the Excel file here</p>
                <p className="mt-1 text-muted">or click to choose (.xlsx)</p>
              </>}
              <input ref={input} type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" className="sr-only"
                     onChange={(e) => { if (e.target.files?.[0]) check(e.target.files[0]); e.target.value = '' }} />
            </div>
            <p className="mt-3 text-xs text-muted">Next you'll see every change. Nothing goes live until you approve it.</p>
          </li>
        </ol>
      )}

      {preview && file && (
        <Review preview={preview} fileName={file.name} selected={selected} setSelected={setSelected} filter={filter} setFilter={setFilter}
                busy={busy === 'apply'} onApply={apply} onCancel={reset} />
      )}

      {!preview && history.data && history.data.some(isUpload) && (
        <section>
          <h3 className="text-base">Recent uploads</h3>
          <ul className="mt-3 divide-y divide-line rounded-xl border border-line text-sm">
            {history.data.filter(isUpload).map((r) => (
              <li key={r.id}>
                <details className="group">
                  <summary className="flex cursor-pointer list-none items-center gap-3 px-4 py-3">
                    <span className="flex-1">{dateTime(r.startedAt)} <span className="text-muted">· {r.issues[0].message.replace('Excel upload by ', 'by ')}</span></span>
                    <span className="text-muted">{r.rowsOk} product{r.rowsOk === 1 ? '' : 's'} updated</span>
                    <span className="text-muted transition group-open:rotate-90">›</span>
                  </summary>
                  <ul className="space-y-1 px-4 pb-3 text-xs text-muted">
                    {r.issues.slice(1).map((i, n) => <li key={n}><span className="font-mono text-ink/70">{i.sku}</span> {i.message}</li>)}
                  </ul>
                </details>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

function isUpload(r: SyncRun) {
  return r.issues[0]?.message.startsWith('Excel upload by')
}

function Review({ preview: p, fileName, selected, setSelected, filter, setFilter, busy, onApply, onCancel }: {
  preview: ImportPreview; fileName: string; selected: Set<string>; setSelected: (s: Set<string>) => void
  filter: Filter; setFilter: (f: Filter) => void; busy: boolean; onApply: () => void; onCancel: () => void
}) {
  const rows = useMemo(() => p.rows.filter((r) => filter === 'changes' ? r.status === 'NEW' || r.status === 'UPDATE' : r.status === filter), [p, filter])
  const selectable = p.rows.filter((r) => r.status === 'NEW' || r.status === 'UPDATE')
  const toggle = (sku: string) => {
    const next = new Set(selected)
    if (next.has(sku)) next.delete(sku)
    else next.add(sku)
    setSelected(next)
  }
  const tabs: [Filter, string, number][] = [
    ['changes', 'All changes', p.newCount + p.updateCount], ['NEW', 'New products', p.newCount], ['UPDATE', 'Changed', p.updateCount],
    ['ERROR', 'Problems', p.errorCount], ['SAME', 'No change', p.sameCount],
  ]

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <div className="mr-auto">
          <p className="text-sm text-muted">Step 3 · Review</p>
          <h3 className="text-lg">{fileName}</h3>
        </div>
        <button className="btn-ghost" onClick={onCancel}>Choose another file</button>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Count n={p.newCount} label="New products" />
        <Count n={p.updateCount} label="Products with changes" />
        <Count n={p.errorCount} label="Rows with problems" tone={p.errorCount ? 'bad' : undefined} />
        <Count n={p.sameCount} label="No change" />
      </div>
      {p.newCategories.length > 0 && <p className="text-sm text-muted">New categories will be created: <b className="text-ink">{p.newCategories.join(', ')}</b></p>}
      {p.errorCount > 0 && <p className="text-sm text-bad">Rows with problems are skipped. Fix them in Excel and upload again, or apply the rest now.</p>}

      <div className="scrollbar-none -mx-4 flex gap-2 overflow-x-auto px-4">
        {tabs.map(([f, label, n]) => <button key={f} onClick={() => setFilter(f)} className={`chip shrink-0 ${filter === f ? 'chip-on' : ''}`}>{label} ({n})</button>)}
      </div>

      <div className="overflow-hidden rounded-xl border border-line">
        {(filter === 'changes' || filter === 'NEW' || filter === 'UPDATE') && rows.length > 0 && (
          <div className="flex items-center gap-3 border-b border-line bg-surface px-4 py-2 text-xs">
            <button className="font-medium underline" onClick={() => setSelected(new Set([...selected, ...rows.map((r) => r.sku)]))}>Select all</button>
            <button className="font-medium underline" onClick={() => { const n = new Set(selected); rows.forEach((r) => n.delete(r.sku)); setSelected(n) }}>Select none</button>
            <span className="ml-auto text-muted">{selected.size} of {selectable.length} selected</span>
          </div>
        )}
        {rows.length === 0 && <p className="p-6 text-center text-sm text-muted">Nothing here.</p>}
        <ul className="divide-y divide-line">
          {rows.map((r) => <RowItem key={r.sku + r.row} r={r} checked={selected.has(r.sku)} onToggle={() => toggle(r.sku)} />)}
        </ul>
      </div>

      <div className="sticky bottom-0 -mx-4 flex flex-wrap items-center gap-3 border-t border-line bg-white px-4 py-3">
        <p className="mr-auto text-sm text-muted">{selected.size === 0 ? 'Nothing selected' : `${selected.size} product${selected.size === 1 ? '' : 's'} will be updated`}</p>
        <button className="btn-ghost" onClick={onCancel}>Cancel</button>
        <button className="btn-primary" onClick={onApply} disabled={busy || selected.size === 0}>{busy ? 'Updating…' : 'Approve and update website'}</button>
      </div>
    </div>
  )
}

function RowItem({ r, checked, onToggle }: { r: ImportRow; checked: boolean; onToggle: () => void }) {
  const selectable = r.status === 'NEW' || r.status === 'UPDATE'
  return (
    <li className={`flex gap-3 px-4 py-3 text-sm ${selectable && !checked ? 'opacity-60' : ''}`}>
      <div className="w-5 shrink-0 pt-2">
        {selectable && <input type="checkbox" checked={checked} onChange={onToggle} aria-label={`Include ${r.name}`} className="h-4 w-4 accent-black" />}
      </div>
      <ProductImage src={r.image} alt="" className="h-12 w-12 shrink-0 rounded-lg" />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline gap-x-2">
          <span className="font-medium">{r.name || '(no name)'}</span>
          <span className="font-mono text-xs text-muted">{r.sku}</span>
          <span className="text-xs text-muted">· row {r.row}</span>
          <Tag status={r.status} />
        </div>
        {r.changes.length > 0 && (
          <ul className="mt-1 flex flex-wrap gap-x-4 gap-y-0.5 text-[13px]">
            {r.changes.map((c) => (
              <li key={c.field}>
                <span className="text-muted">{c.label}: </span>
                {c.from !== null && c.field !== 'description' && <><span className="text-muted line-through">{c.from}</span> → </>}
                <span className="font-medium">{c.field === 'description' ? 'updated' : c.to}</span>
              </li>
            ))}
          </ul>
        )}
        {r.errors.map((e) => <p key={e} className="mt-1 text-[13px] text-bad">✕ {e}</p>)}
        {r.warnings.map((w) => <p key={w} className="mt-1 text-[13px] text-warn">! {w}</p>)}
      </div>
    </li>
  )
}

function Tag({ status }: { status: ImportRow['status'] }) {
  const map = { NEW: ['New', 'bg-blue-50 text-blue-800'], UPDATE: ['Changed', 'bg-surface text-ink'], ERROR: ['Problem', 'bg-red-50 text-bad'], SAME: ['No change', 'bg-surface text-muted'] } as const
  const [label, cls] = map[status]
  return <span className={`rounded-md px-1.5 py-0.5 text-[11px] font-medium ${cls}`}>{label}</span>
}

function Count({ n, label, tone }: { n: number; label: string; tone?: 'bad' }) {
  return (
    <div className="card p-4">
      <p className={`text-2xl font-semibold tracking-tight ${tone === 'bad' ? 'text-bad' : n === 0 ? 'text-muted' : ''}`}>{n}</p>
      <p className="text-sm text-muted">{label}</p>
    </div>
  )
}
