const BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const TOKEN_KEY = 'vs_token'

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function safeGet(key: string) {
  try { return localStorage.getItem(key) } catch { return null }
}

let token: string | null = safeGet(TOKEN_KEY)

export function getToken() {
  return token
}

export function setToken(t: string | null) {
  token = t
  try {
    if (t) localStorage.setItem(TOKEN_KEY, t)
    else localStorage.removeItem(TOKEN_KEY)
  } catch { /* storage blocked: session-only login */ }
}

type Options = { method?: string; body?: unknown; form?: FormData; signal?: AbortSignal }

async function request(path: string, opts: Options = {}): Promise<Response> {
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  let body: BodyInit | undefined
  if (opts.form) body = opts.form
  else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(opts.body)
  }
  let res: Response
  try {
    res = await fetch(BASE + path, { method: opts.method ?? (body ? 'POST' : 'GET'), headers, body, signal: opts.signal })
  } catch (e) {
    if ((e as Error).name === 'AbortError') throw e
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.')
  }
  if (!res.ok) {
    if (res.status === 401 && token) {
      setToken(null)
      window.dispatchEvent(new Event('vs:logout'))
    }
    let message = res.status === 403 ? 'You do not have access to this.' : 'Something went wrong. Please try again.'
    try {
      const data = await res.json()
      if (data?.message) message = data.message
    } catch { /* non-JSON error */ }
    throw new ApiError(res.status, message)
  }
  return res
}

export async function api<T>(path: string, opts: Options = {}): Promise<T> {
  const res = await request(path, opts)
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** For authenticated binary downloads (invoices, private photos). */
export async function apiBlob(path: string): Promise<Blob> {
  const res = await request(path)
  return res.blob()
}

export async function openBlob(path: string) {
  const win = window.open('', '_blank')
  try {
    const url = URL.createObjectURL(await apiBlob(path))
    if (win) win.location.href = url
    else window.location.href = url
  } catch (e) {
    win?.close()
    throw e
  }
}
