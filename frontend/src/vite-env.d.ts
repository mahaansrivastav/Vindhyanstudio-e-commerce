/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
}

interface Window {
  Cashfree?: (opts: { mode: string }) => { checkout(o: { paymentSessionId: string; redirectTarget: string }): Promise<{ error?: { message?: string }; paymentDetails?: unknown; redirect?: boolean }> }
  Razorpay?: new (options: Record<string, unknown>) => { open(): void; on(event: string, cb: (r: unknown) => void): void }
}
