import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { api } from '../lib/api'
import { useAuth, useConfig } from '../state/AppState'

type Step = 'identity' | 'code'

/** Mobile OTP or email OTP login (BRD FR-01, FR-03). Also used by Account to link a second method. */
export function OtpForm({ mode, onDone, linkMode = false }: { mode: 'phone' | 'email'; onDone: (token: string) => Promise<void>; linkMode?: boolean }) {
  const [step, setStep] = useState<Step>('identity')
  const [identity, setIdentity] = useState('')
  const [code, setCode] = useState('')
  const [devOtp, setDevOtp] = useState<string | null>(null)
  const [consent, setConsent] = useState(linkMode)
  const [cooldown, setCooldown] = useState(0)
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const base = linkMode ? '/api/me/link' : '/api/auth/otp'

  useEffect(() => {
    if (cooldown <= 0) return
    const t = setTimeout(() => setCooldown(cooldown - 1), 1000)
    return () => clearTimeout(t)
  }, [cooldown])

  const send = async (e?: React.FormEvent) => {
    e?.preventDefault()
    setErr(null)
    if (!consent) return setErr('Please accept the terms to continue')
    setBusy(true)
    try {
      const r = await api<{ devOtp?: string }>(`${base}/request`, { body: { identity } })
      setDevOtp(r.devOtp ?? null)
      setStep('code')
      setCooldown(30)
    } catch (e) {
      setErr((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const verify = async (e: React.FormEvent) => {
    e.preventDefault()
    setErr(null)
    setBusy(true)
    try {
      const r = await api<{ token: string }>(`${base}/verify`, { body: { identity, code } })
      await onDone(r.token)
    } catch (e) {
      setErr((e as Error).message)
      setBusy(false)
    }
  }

  if (step === 'identity') {
    return (
      <form onSubmit={send} className="space-y-4">
        <label className="block">
          <span className="label">{mode === 'phone' ? 'Mobile number' : 'Email address'}</span>
          {mode === 'phone' ? (
            <div className="flex">
              <span className="flex items-center rounded-l-xl border border-r-0 border-line bg-stone-50 px-3 text-sm text-muted">+91</span>
              <input className="input rounded-l-none" inputMode="numeric" maxLength={10} autoComplete="tel-national" required autoFocus
                     value={identity} onChange={(e) => setIdentity(e.target.value.replace(/\D/g, ''))} placeholder="98765 43210" />
            </div>
          ) : (
            <input className="input" type="email" autoComplete="email" required autoFocus value={identity}
                   onChange={(e) => setIdentity(e.target.value)} placeholder="you@example.com" />
          )}
        </label>
        {!linkMode && (
          <label className="flex items-start gap-2 text-sm text-muted">
            <input type="checkbox" className="mt-1" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
            <span>I agree to the <Link to="/policies/terms" className="underline">Terms</Link> and <Link to="/policies/privacy" className="underline">Privacy Policy</Link>.</span>
          </label>
        )}
        {err && <p className="text-sm text-bad" role="alert">{err}</p>}
        <button className="btn-primary w-full" disabled={busy}>{busy ? 'Sending…' : 'Send code'}</button>
      </form>
    )
  }

  return (
    <form onSubmit={verify} className="space-y-4">
      <p className="text-sm text-muted">
        We sent a 6-digit code to <b className="text-ink">{mode === 'phone' ? `+91 ${identity}` : identity}</b>.{' '}
        <button type="button" className="underline" onClick={() => { setStep('identity'); setCode('') }}>Change</button>
      </p>
      {devOtp && <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-900">Local dev code: <b className="font-mono">{devOtp}</b></p>}
      <input className="input text-center font-mono text-2xl tracking-[0.5em]" inputMode="numeric" autoComplete="one-time-code" maxLength={6} required autoFocus
             value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} aria-label="One-time code" />
      {err && <p className="text-sm text-bad" role="alert">{err}</p>}
      <button className="btn-primary w-full" disabled={busy || code.length !== 6}>{busy ? 'Verifying…' : 'Verify & continue'}</button>
      <button type="button" className="btn-ghost w-full" disabled={cooldown > 0 || busy} onClick={() => send()}>
        {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
      </button>
    </form>
  )
}

export function Login() {
  const config = useConfig()
  const phone = config?.phoneLogin ?? false
  const [picked, setMode] = useState<'phone' | 'email' | null>(null)
  const mode = picked ?? (phone ? 'phone' : 'email')
  const { profile, signIn } = useAuth()
  const [params] = useSearchParams()
  const nav = useNavigate()
  const next = params.get('next') || '/account'
  const safeNext = next.startsWith('/') && !next.startsWith('//') ? next : '/account'

  if (profile) return <Navigate to={safeNext} replace />

  return (
    <div className="mx-auto max-w-sm">
      <h1 className="text-3xl font-bold">Log in or sign up</h1>
      <p className="mt-1 text-sm text-muted">No password needed. We’ll send you a one-time code.</p>
      <div className={`mt-6 grid ${phone ? 'grid-cols-2' : 'grid-cols-1'} rounded-xl bg-stone-100 p-1 text-sm font-semibold`} role="tablist">
        {(phone ? (['phone', 'email'] as const) : (['email'] as const)).map((m) => (
          <button key={m} role="tab" aria-selected={mode === m} onClick={() => setMode(m)}
                  className={`rounded-lg py-2 ${mode === m ? 'bg-card shadow-sm' : 'text-muted'}`}>
            {m === 'phone' ? 'Mobile' : 'Email'}
          </button>
        ))}
      </div>
      <div className="card mt-4 p-5">
        <OtpForm key={mode} mode={mode} onDone={async (t) => { await signIn(t); nav(safeNext, { replace: true }) }} />
      </div>
    </div>
  )
}
