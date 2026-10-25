/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
}

interface Window {
  Razorpay?: new (options: Record<string, unknown>) => { open(): void; on(event: string, cb: (r: unknown) => void): void }
}
