import { Link, useParams } from 'react-router-dom'
import { Empty } from '../components/ui'
import { useConfig } from '../state/AppState'

// DRAFT text. The owner must review these before going live (Razorpay KYC requires all five pages).
const POLICIES: Record<string, { title: string; body: string[] }> = {
  shipping: {
    title: 'Shipping policy',
    body: [
      'We ship across India through Shiprocket’s courier partners.',
      'Ready-stock items are dispatched within 1–2 business days. Personalised items are made to order and dispatched after the production time shown on the product page.',
      'Shipping is free above the amount shown in your cart; a flat fee applies below it. You’ll receive the courier name and tracking link once your parcel leaves us.',
    ],
  },
  refund: {
    title: 'Returns & refunds',
    body: [
      'Personalised items (with your photo, name or text) are made only for you and can’t be returned, unless they arrive damaged or with a print defect.',
      'Non-personalised items can be returned within 7 days of delivery if unused and in original packaging.',
      'For damage or defects, contact us within 48 hours of delivery with photos of the item and packaging. Approved refunds are credited to the original payment method within 5–7 business days.',
    ],
  },
  privacy: {
    title: 'Privacy policy',
    body: [
      'We collect your name, contact details and delivery address to process and deliver your orders, and share them only with our payment and courier partners for that purpose.',
      'Photos you upload are used only to make your order and are deleted after delivery. Card and UPI details are handled by our payment gateway; we never see or store them.',
      'You can ask us to delete your account and data at any time by contacting us.',
    ],
  },
  terms: {
    title: 'Terms of service',
    body: [
      'By placing an order you confirm that you have the right to use any photos, names or logos you upload for personalisation.',
      'Colours and textures of 3D printed items can vary slightly from photos. Prices include applicable taxes.',
      'We may cancel and fully refund an order if an uploaded photo is unsuitable for printing or an item becomes unavailable.',
    ],
  },
  contact: {
    title: 'Contact us',
    body: ['Vindhyan Studio', 'Email: hello@vindhyanstudio.in (placeholder)', 'Phone / WhatsApp: +91 00000 00000 (placeholder)', 'Address: to be added by the owner'],
  },
}

export function Policy() {
  const { slug = '' } = useParams()
  const config = useConfig()
  let p = POLICIES[slug]
  if (slug === 'contact' && config) {
    p = { title: 'Contact us', body: [
      config.storeName,
      `Email: ${config.sellerEmail}`,
      ...(config.sellerPhone ? [`Phone / WhatsApp: ${config.sellerPhone}`] : []),
      `Address: ${config.sellerAddress}`,
      'We reply within one working day (Mon–Sat, 10 am – 7 pm IST).',
    ] }
  }
  if (!p) return <Empty title="Page not found"><Link to="/" className="underline">Go home</Link></Empty>
  return (
    <article className="mx-auto max-w-2xl space-y-4">
      <h1 className="text-3xl font-bold">{p.title}</h1>
      {p.body.map((t) => <p key={t} className="leading-relaxed text-stone-700">{t}</p>)}
    </article>
  )
}
