// Mirrors the backend DTOs.

export interface PublicConfig {
  storeName: string
  sellerAddress: string
  legalName: string | null
  gstin: string | null
  sellerEmail: string
  sellerPhone: string
  whatsapp: string
  shippingFlatFee: number
  freeShippingAbove: number
  codEnabled: boolean
  codMaxOrderValue: number
  paymentProvider: 'mock' | 'razorpay' | string
  devOtp: boolean
  phoneLogin: boolean
}

export interface CategoryDto { name: string; slug: string; productCount: number; coverImage: string | null }

export interface ProductCard {
  sku: string; name: string; slug: string; category: string; categorySlug: string
  mrp: number; sellingPrice: number; offerPct: number; stock: number; image: string | null; customisable: boolean
}

export interface CustomisationSpec {
  minPhotos: number; maxPhotos: number; text: boolean; textRequired: boolean
  colour: boolean; size: boolean; notes: boolean; sizeLabel: string
}

export interface ProductDetail {
  sku: string; name: string; slug: string; category: string; categorySlug: string; description: string
  mrp: number; sellingPrice: number; offerPct: number; stock: number; images: string[]
  customisation: CustomisationSpec; colours: string[]; sizes: string[]; leadTimeDays: number; returnable: boolean
}

export interface Customisation {
  text?: string; colour?: string; size?: string; notes?: string; photos?: string[]
}

export interface QuoteLine {
  sku: string; name: string; slug: string | null; image: string | null; unitPrice: number; mrp: number
  qty: number; lineTotal: number; stock: number; problem: string | null
}

export interface Quote {
  lines: QuoteLine[]; subtotal: number; shippingFee: number; total: number; codAvailable: boolean; valid: boolean
}

export interface Profile { id: number; name: string | null; email: string | null; phone: string | null; admin: boolean }

export interface Address {
  id?: number; name: string; phone: string; line1: string; line2?: string | null
  city: string; state: string; pincode: string; isDefault: boolean
}

export type OrderStatus =
  | 'PAYMENT_PENDING' | 'CONFIRMED' | 'PRINTING' | 'READY' | 'DISPATCHED' | 'DELIVERED' | 'CANCELLED' | 'RTO'

export interface OrderSummary {
  id: number; orderNo: string; status: OrderStatus; paymentMode: 'ONLINE' | 'COD'; total: number; createdAt: string
  itemCount: number; firstItem: string | null; customerName: string | null; city: string; customised: boolean
}

export interface OrderView {
  id: number; orderNo: string; status: OrderStatus; paymentMode: 'ONLINE' | 'COD'; createdAt: string
  address: Address
  items: { sku: string; name: string; slug: string | null; image: string | null; unitPrice: number; mrp: number; qty: number; lineTotal: number; customisation: Customisation | null }[]
  subtotal: number; shippingFee: number; discount: number; total: number
  events: { status: OrderStatus; note: string | null; actor: string | null; at: string }[]
  shipment: { status: string; courier: string | null; awb: string | null; trackingUrl: string | null; labelUrl: string | null; error: string | null } | null
  invoiceNo: string | null; canPay: boolean
  customer: { name: string | null; email: string | null; phone: string | null } | null
  nextStatuses: OrderStatus[] | null
}

export interface PaymentInit {
  provider: string; keyId: string | null; mode: string | null; sessionToken: string | null; providerOrderId: string; amountPaise: number; currency: string
  orderId: number; orderNo: string; name: string | null; email: string | null; phone: string | null
}

export interface SyncRun {
  id: number; startedAt: string; finishedAt: string; rowsOk: number; rowsFailed: number
  issues: { severity: 'ERROR' | 'WARN' | 'INFO'; row: number; sku: string | null; message: string }[]
}

export interface AdminProduct {
  sku: string; name: string; slug: string; categoryId: number; category: string; description: string | null
  mrp: number; sellingPrice: number; offerPct: number; stock: number; active: boolean; source: 'EXCEL' | 'ADMIN'
  customisation: string | null; categoryCustomisation: string | null
  images: { id: number; url: string }[]
}

export interface AdminCategory {
  id: number; name: string; slug: string; hsnCode: string | null; gstRate: number; weightG: number
  lengthCm: number; widthCm: number; heightCm: number; leadTimeDays: number
  customisation: string | null; colours: string | null; sizes: string | null; returnable: boolean; productCount: number
}

export interface Kpis {
  revenue: number; orders: number; avgOrderValue: number; itemsSold: number; customers: number; newCustomers: number
  cancelled: number; returned: number; awaitingPayment: number; codOrders: number; codRevenue: number; onlineOrders: number; onlineRevenue: number
}

export interface Dashboard {
  from: string; to: string; granularity: 'day' | 'month'; kpis: Kpis; previous: Kpis
  series: { label: string; date: string; orders: number; revenue: number }[]
  topProducts: { sku: string; name: string; category: string; image: string | null; qty: number; revenue: number }[]
  categories: { name: string; orders: number; qty: number; revenue: number }[]
  states: { name: string; orders: number; qty: number; revenue: number }[]
  attention: {
    toPrint: number; readyToShip: number; awaitingPayment: number
    lowStock: { sku: string; name: string; image: string | null; stock: number }[]
    outOfStock: { sku: string; name: string; image: string | null; stock: number }[]
    liveProducts: number; hiddenProducts: number; noPhoto: number; madeToOrder: number
  }
}

export interface ImportRow {
  row: number; sku: string; name: string; image: string | null; status: 'NEW' | 'UPDATE' | 'SAME' | 'ERROR'
  changes: { field: string; label: string; from: string | null; to: string | null }[]
  errors: string[]; warnings: string[]
}

export interface ImportPreview {
  newCount: number; updateCount: number; sameCount: number; errorCount: number; newCategories: string[]; rows: ImportRow[]
}
