// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import type { OrderStatus } from './types'

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })
const inr2 = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 })

export function rupees(v: number, exact = false) {
  return (exact ? inr2 : inr).format(v)
}

export function date(iso: string) {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function dateTime(iso: string) {
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
}

export const STATUS_LABEL: Record<OrderStatus, string> = {
  PAYMENT_PENDING: 'Payment pending',
  CONFIRMED: 'Confirmed',
  PRINTING: 'Printing',
  READY: 'Packed · courier booked',
  DISPATCHED: 'Dispatched',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  RTO: 'Returned to us',
}

export const STATUS_TONE: Record<OrderStatus, string> = {
  PAYMENT_PENDING: 'bg-amber-50 text-amber-800 ring-amber-200',
  CONFIRMED: 'bg-sky-50 text-sky-800 ring-sky-200',
  PRINTING: 'bg-violet-50 text-violet-800 ring-violet-200',
  READY: 'bg-indigo-50 text-indigo-800 ring-indigo-200',
  DISPATCHED: 'bg-teal-50 text-teal-800 ring-teal-200',
  DELIVERED: 'bg-green-50 text-green-800 ring-green-200',
  CANCELLED: 'bg-stone-100 text-stone-600 ring-stone-200',
  RTO: 'bg-red-50 text-red-800 ring-red-200',
}

export function describeCustomisation(c: { text?: string; colour?: string; size?: string; notes?: string; photos?: string[] } | null | undefined) {
  if (!c) return []
  const parts: string[] = []
  if (c.text) parts.push(`Text: “${c.text}”`)
  if (c.colour) parts.push(`Colour: ${c.colour}`)
  if (c.size) parts.push(`Size: ${c.size}`)
  if (c.photos?.length) parts.push(`${c.photos.length} photo${c.photos.length > 1 ? 's' : ''}`)
  if (c.notes) parts.push(`Notes: ${c.notes}`)
  return parts
}

export const INDIAN_STATES = [
  'Andaman and Nicobar Islands', 'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chandigarh', 'Chhattisgarh',
  'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Goa', 'Gujarat', 'Haryana', 'Himachal Pradesh', 'Jammu and Kashmir',
  'Jharkhand', 'Karnataka', 'Kerala', 'Ladakh', 'Lakshadweep', 'Madhya Pradesh', 'Maharashtra', 'Manipur', 'Meghalaya',
  'Mizoram', 'Nagaland', 'Odisha', 'Puducherry', 'Punjab', 'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura',
  'Uttar Pradesh', 'Uttarakhand', 'West Bengal',
]
