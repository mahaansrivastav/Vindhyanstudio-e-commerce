// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import type { ReactNode } from 'react'
import { Link, NavLink, useParams } from 'react-router-dom'
import { Empty } from '../components/ui'
import type { PublicConfig } from '../lib/types'
import { useConfig } from '../state/AppState'

// Policy text supplied by the owner. Have it reviewed before major changes;
// payment gateways and the Consumer Protection (E-Commerce) Rules 2020 require these pages.

type Section = { h?: string; body: ReactNode }
type Page = { title: string; intro?: ReactNode; sections: Section[] }

const LAST_UPDATED = '18 October 2026'

function email(c: PublicConfig | null) {
  const e = c?.sellerEmail || 'vindhyanstudio@gmail.com'
  return <a className="font-semibold text-accent underline underline-offset-2" href={`mailto:${e}`}>{e}</a>
}

function phone(c: PublicConfig | null) {
  return c?.sellerPhone || '+91 94559 38254'
}

function pages(c: PublicConfig | null): Record<string, Page> {
  const fee = c?.shippingFlatFee ?? 60
  const free = c?.freeShippingAbove ?? 999
  return {
    terms: {
      title: 'Terms of use',
      intro: <>These terms govern your use of the Vindhyan Studio website and every purchase made on it. By browsing, creating an account or placing an order you agree to them. Last updated: {LAST_UPDATED}.</>,
      sections: [
        { h: '1. Eligibility & account', body: 'You must be at least 18 years old, or use the site under the supervision of a parent or guardian. You sign in with a one-time password (OTP) sent to your mobile number or with your email. Keep your device and OTPs private, because you are responsible for activity on your account. We may suspend accounts used for fraud, abuse or misuse.' },
        { h: '2. Products & customisation', body: 'Our products are 3D printed and hand finished, so small variations in colour, texture and layer lines are normal and not defects. Product photos are representative. For personalised items, you confirm that you own or have permission to use every photo, name, logo or design you upload, and that it is not unlawful, obscene or infringing. We may refuse any customisation at our discretion and refund you in full.' },
        { h: '3. Prices & payment', body: 'All prices are in Indian Rupees and include applicable GST. Shipping and cash-on-delivery fees are shown before you pay. Online payments are processed by Razorpay; we never see or store your card or UPI PIN. If a price is listed incorrectly because of an error, we may cancel the order and refund any amount paid.' },
        { h: '4. Orders', body: 'An order is confirmed when you receive an order ID. Made-to-order items enter production within 24 hours; after that they cannot be cancelled. Bulk and custom orders are governed by the written quote we share with you.' },
        { h: '5. Shipping, returns & refunds', body: <>See our <Link className="font-semibold text-accent underline underline-offset-2" to="/policies/shipping">shipping policy</Link> and <Link className="font-semibold text-accent underline underline-offset-2" to="/policies/refund">returns &amp; refunds policy</Link>, which form part of these terms.</> },
        { h: '6. Reviews & blog posts', body: 'Only customers who bought a product can rate it. When you submit a review or blog post you grant Vindhyan Studio a non-exclusive, royalty-free licence to display it on our website and social channels with your name and city. Posts are published only after approval; we may edit for length or decline any submission.' },
        { h: '7. Intellectual property', body: 'The Vindhyan Studio name, logo, designs, photographs and website content belong to us. You may not copy, resell or reproduce our designs or 3D models without written permission.' },
        { h: '8. Prohibited use', body: "You agree not to misuse the site, including attempting to access the admin area or other users' accounts, scraping, uploading malware, or placing fake orders." },
        { h: '9. Limitation of liability', body: 'To the extent permitted by law, our total liability for any order is limited to the amount you paid for it. We are not liable for indirect losses or delays caused by couriers, payment providers or events beyond our control.' },
        { h: '10. Governing law & grievances', body: <>These terms are governed by the laws of India, and courts at Gorakhpur, Uttar Pradesh have exclusive jurisdiction. In line with the Consumer Protection (E-Commerce) Rules, 2020, complaints can be raised with our grievance officer at {email(c)} or {phone(c)}. We acknowledge complaints within 48 hours and resolve them within one month.</> },
      ],
    },
    privacy: {
      title: 'Privacy policy',
      intro: 'We collect only what we need to make and deliver your order.',
      sections: [
        { h: 'What we collect', body: 'Your name, mobile number, email, delivery addresses, order history and the photos or text you upload for personalisation. Payment details are handled entirely by Razorpay.' },
        { h: 'How we use it', body: 'To produce and ship orders, send OTPs and order updates (SMS, WhatsApp or email), issue GST invoices, prevent fraud and answer your questions. We never sell your data.' },
        { h: 'Who we share it with', body: 'Only the services needed to fulfil your order: our payment gateway (Razorpay), shipping partner (Shiprocket and its couriers) and SMS/email providers.' },
        { h: 'Your photos', body: 'Uploaded photos are used only to make your product and are deleted 90 days after delivery unless you ask us to keep them for re-orders.' },
        { h: 'Your rights', body: <>You can view and edit your profile at any time, and ask us to delete your account and data by writing to {email(c)}. We keep invoices for as long as tax law requires.</> },
        { h: 'Security', body: 'All traffic is encrypted with HTTPS, OTPs expire after 5 minutes and are rate-limited, and our admin area is separate from customer accounts.' },
      ],
    },
    shipping: {
      title: 'Shipping policy',
      intro: "We ship across India through Shiprocket's courier partners (Delhivery, Bluedart, Ecom Express, DTDC and others).",
      sections: [
        { h: 'Production time', body: 'Ready designs: 1–3 working days. Personalised items: 3–5 working days after your photo/text is confirmed. Bulk orders: as per quote.' },
        { h: 'Delivery time', body: 'Usually 2–7 working days after dispatch depending on your pincode. You can check availability on every product page.' },
        { h: 'Charges', body: <>Free shipping on orders of ₹{free.toLocaleString('en-IN')} or more; otherwise ₹{fee}. The exact amount is always shown in your cart before you pay.</> },
        { h: 'Tracking', body: <>Once dispatched you get the courier name and tracking number by email, and tracking appears under <Link className="font-semibold text-accent underline underline-offset-2" to="/account/orders">My Orders</Link>.</> },
        { h: 'Damaged parcel', body: "Please record an unboxing video. If the package is damaged, contact us within 48 hours of delivery with photos and we'll reprint or refund." },
      ],
    },
    refund: {
      title: 'Returns & refunds',
      sections: [
        { h: 'Personalised items', body: "Because they are made only for you, personalised products cannot be returned for change of mind. If there is a printing defect or the item doesn't match the approved design, we reprint it free or refund in full." },
        { h: 'Ready designs', body: 'Unused items in original packaging can be returned within 7 days of delivery. Return shipping is deducted from the refund unless the item was defective.' },
        { h: 'How to request', body: <>WhatsApp us on {phone(c)} or email {email(c)} with your order ID and photos within 48 hours of delivery.</> },
        { h: 'Refund timelines', body: 'Approved refunds are issued to the original payment method within 5–7 working days. COD refunds are sent by UPI or bank transfer.' },
        { h: 'Cancellations', body: 'Orders can be cancelled for a full refund before production starts (within 24 hours of ordering). Bulk orders follow the terms in your quote.' },
      ],
    },
    contact: {
      title: 'Contact us',
      sections: [
        { h: 'Vindhyan Studio', body: <>{c?.legalName ? <>A brand of {c.legalName}<br /></> : null}{c?.sellerAddress}{c?.gstin ? <><br />GSTIN: {c.gstin}</> : null}</> },
        { h: 'Email', body: email(c) },
        { h: 'WhatsApp / phone', body: <>{phone(c)}{c?.whatsapp ? <> · <a className="font-semibold text-accent underline underline-offset-2" href={`https://wa.me/${c.whatsapp}`} target="_blank" rel="noopener">Chat on WhatsApp</a></> : null}</> },
        { h: 'Hours', body: 'Monday to Saturday, 10 am – 7 pm IST. We reply within one working day.' },
        { h: 'Grievance officer', body: <>As required by the Consumer Protection (E-Commerce) Rules, 2020: write to {email(c)} or call {phone(c)}. We acknowledge complaints within 48 hours and resolve them within one month.</> },
      ],
    },
  }
}

const NAV: [string, string][] = [['terms', 'Terms of use'], ['privacy', 'Privacy policy'], ['shipping', 'Shipping policy'], ['refund', 'Returns & refunds'], ['contact', 'Contact & grievances']]

export function Policy() {
  const { slug = '' } = useParams()
  const config = useConfig()
  const p = pages(config)[slug]
  if (!p) return <Empty title="Page not found"><Link to="/" className="underline">Go home</Link></Empty>

  return (
    <div className="space-y-6">
      <div className="border-b border-line pb-6 pt-2">
        <p className="text-sm text-muted">Policies</p>
        <h1 className="mt-1 text-3xl sm:text-4xl">{p.title}</h1>
      </div>
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-[220px_1fr] lg:gap-14">
        <aside className="min-w-0 space-y-4 lg:sticky lg:top-36 lg:self-start">
          <nav className="flex gap-1 overflow-x-auto scrollbar-none lg:flex-col" aria-label="Policies">
            {NAV.map(([s, label]) => (
              <NavLink key={s} to={`/policies/${s}`}
                       className={({ isActive }) => `shrink-0 rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-surface font-medium text-ink' : 'text-ink/70 hover:text-ink'}`}>
                {label}
              </NavLink>
            ))}
          </nav>
        </aside>
        <article className="min-w-0 max-w-3xl">
          {p.intro && <p className="text-base leading-relaxed text-ink/80">{p.intro}</p>}
          <div className="mt-2 space-y-6">
            {p.sections.map((s, i) => (
              <section key={i}>
                {s.h && <h2 className="text-lg">{s.h}</h2>}
                <p className="mt-1.5 leading-relaxed text-ink/80">{s.body}</p>
              </section>
            ))}
          </div>
        </article>
      </div>
    </div>
  )
}
