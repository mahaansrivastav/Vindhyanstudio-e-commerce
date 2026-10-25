# Vindhyan Studio — Build Plan (v2, aligned to BRD v1.0)

> **Status (17 Oct 2026):** First working version built; see README.md. Done and tested locally with mocks: Excel/photo catalogue sync with error report, storefront, personalisation + photo upload, cart, pincode check, OTP login (mobile/email, account linking), addresses, checkout (online + COD), Razorpay gateway, auto GST invoice PDF, Shiprocket order + courier booking, tracking webhook, email/SMS notifications, stock write-back, admin orders + catalogue screens.
> **Change from this plan:** login uses the backend's own OTP + JWT instead of Supabase Auth. It's simpler to run in every environment and costs nothing; Supabase Auth can still be swapped in via the JwtDecoder bean.
> **Next:** Google Drive catalogue source (for hosted), MSG91 SMS after DLT, Razorpay/Shiprocket test-account runs, credit notes, real policy text.

Source of requirements: [docs/VindhyanStudio_BRD.pdf](docs/VindhyanStudio_BRD.pdf) (11 Oct 2026). Requirement IDs (FR-xx, S-x) below refer to it.

Goal: **customer personalises and pays → invoice and Shiprocket order are created automatically → owner prints, packs, and hands it to the courier.** The owner manages the catalogue only through Excel plus a photo folder.

---

## 0. What changed after reading the BRD

| Topic | Plan v1 assumed | BRD says → plan v2 |
|---|---|---|
| Products | Keyrings only | **8 categories**: lithophanes, keyrings, 3D person models, statues, mechanical calendars, bathroom kits, flower pots, lamps |
| Catalogue management | Admin CRUD screens | **Excel sheet + `photos/<SKU_id>/` folders**, auto-synced, with an error report (FR-07, FR-26) |
| Customisation | One text field | Per category: **photo upload (1–3 photos)**, name/text, colour, size, notes (FR-12/13) |
| Login | Google + guest checkout | **Mobile OTP or email (password or OTP)**, one account for both. **Login is required for checkout** (FR-01/02/15) |
| Payment | Online only | Online **and COD** (COD can be switched on/off) |
| Invoice | Not planned | **Automatic GST tax-invoice PDF** + credit notes (FR-18/19/20) |
| Shipping | Create order + label | Also a **pincode serviceability check** + tracking webhooks (FR-21/24) |
| Messages | Email, then WhatsApp | **Email + SMS** (WhatsApp is not mentioned) |
| Prototype | Starting from scratch | **A prototype already exists** (BRD §7). We need to decide whether to reuse or rebuild (see §10) |

---

## 1. Tech stack (free tiers)

| Layer | Choice | Notes |
|---|---|---|
| Frontend | React + Vite + TypeScript + Tailwind, mobile-first | Hosted on Vercel (free) |
| Backend | Spring Boot 3, Java 21, Maven | See hosting note below |
| Database | Supabase Postgres (500 MB free) | Flyway migrations |
| Auth | **Supabase Auth**: phone OTP, email+password, email OTP, identity linking | Backend verifies the Supabase JWT. OTP length, expiry and rate limits are configurable, which covers FR-03 |
| Files | Supabase Storage (1 GB free) | Product images (public bucket) + customer photos (**private** bucket, signed URLs) |
| Catalogue source | **Google Drive folder**: Google Sheet (or .xlsx) + `photos/<SKU_id>/` | Owner edits on a phone or laptop. Backend reads it through a service account |
| Payments | Razorpay (UPI, cards, netbanking, wallets) + COD | Behind a `PaymentGateway` interface, with a mock for development |
| Shipping | Shiprocket API | Behind a `ShippingProvider` interface, with a mock for development |
| Invoice PDF | OpenHTMLtoPDF + Thymeleaf template | Generated on the server and stored in Supabase Storage |
| Email | Brevo SMTP (300/day free) | Also used as Supabase Auth's SMTP, because the built-in mailer allows only a few emails per hour |
| SMS / OTP | MSG91 (connected through Supabase's "Send SMS" hook) | **Paid**, see §8 |
| Backups | GitHub Actions nightly `pg_dump` → private storage | The Supabase free tier has **no backups**, and the BRD asks for daily backups |

**Hosting the backend.** Render's free server sleeps after 15 minutes idle, which breaks the BRD's "pages under 3 s" target on the first request. Options:
- **(a) Oracle Cloud Always-Free VM** (recommended): always on, free, but a bit more setup.
- (b) Render free + an uptime ping every 10 minutes: easiest, but a little fragile.

---

## 2. Catalogue sync (Excel + photo folder)

```
Google Drive / VindhyanStudio/
├── catalogue  (Google Sheet or .xlsx)
│     ├── tab "products"    SKU_id | Product_Name | Product_category | MRP | Selling_Price | %offer | stock | descriptions   ← unchanged (BRD)
│     └── tab "categories"  NEW, one row per category (8 rows)
└── photos/
      ├── KR-001/ 1.jpg 2.jpg …
      └── LP-004/ …
```

**Why a new `categories` tab:** the BRD keeps the product columns unchanged, but the system also needs data that is the same for every product in a category:

`category | hsn_code | gst_rate | weight_g | length_cm | width_cm | height_cm | lead_time_days | customisation (e.g. photo:1,text,colour,size) | colours | sizes | returnable`

So the owner fills in 8 rows once, and the product sheet stays as it is.

**Sync job** (runs from a "Sync now" button in admin and every 10 minutes):
1. Read the sheet. Validate each row: missing or invalid price, duplicate SKU, unknown category, no photo folder, Selling_Price > MRP.
2. Valid rows → upsert into `products`. Bad rows are skipped and listed in the **error report** (admin screen + email to the owner) (FR-26).
3. Photos: new or changed files (checked by file checksum) are resized to WebP and copied to Supabase Storage, because hot-linking Drive images is slow and rate-limited.
4. SKUs missing from the sheet → marked `inactive` (not deleted), so old orders still show them.
5. **Stock (FR-28):** the server reduces stock when an order is paid and **writes the new figure back to the sheet** (this needs a Google Sheet; plain .xlsx can't be written back), so there is a single source of truth. If .xlsx is kept, admin offers a "download current stock" export instead.

> Assumption: `%offer` is shown for display only. The price charged is always the server's `Selling_Price` (FR-11).

---

## 3. Login and profile (FR-01 to FR-06)

- Login screen (S4) with two tabs: **Mobile + OTP** | **Email + password / email OTP**. A terms-consent checkbox appears on first sign-up.
- Supabase settings: 6-digit OTP, 5-minute expiry, 30-second resend cooldown, attempt limits (FR-03).
- A profile row (`customers`) is created on first login. **"Add mobile / Add email"** in the profile links the second method to the same account (FR-02).
- Profile (S5): details, profile photo, change password, addresses (add, edit, delete, set default), orders with tracking + **Reorder**, invoices, wishlist.
- Wishlist: kept in localStorage for guests and merged into the account on login (FR-10).

---

## 4. Order flow

```
Cart ─► pincode check (Shiprocket serviceability → "Delivers by 21 Oct" / not serviceable)
     ─► Login ─► Address ─► Checkout: summary + payment mode (Online / COD if enabled & under COD limit)
     ─► POST /api/orders   (server re-prices from DB, checks stock, computes shipping fee)  → PAYMENT_PENDING

Online:  Razorpay order → popup → POST /verify (signature) → PAID
         webhook payment.captured / failed / refunded = source of truth (late success, retries) (FR-16/17)
COD:     → COD_CONFIRMED directly

On PAID / COD_CONFIRMED (one transaction + async jobs with retry):
  ├─ reduce stock (+ write back to the sheet)
  ├─ generate invoice number + PDF            (FR-18)
  ├─ create Shiprocket order                  (FR-22)
  └─ email + SMS customer (with invoice link); email owner "New order #VS1023"

Owner admin (S10):
  AWAITING_APPROVAL ─► (approves customer photos, or asks for better ones)   only for orders with photos
  PRINTING ─► READY  → assign AWB + schedule pickup + label PDF
           ─► DISPATCHED / IN_TRANSIT / DELIVERED / RTO   ← Shiprocket webhook (FR-24), customer gets email + SMS
Cancel / refund → Razorpay refund + credit note PDF (FR-20)
```

**When the courier is booked:** the Shiprocket *order* is created at payment, but the AWB (the courier booking) is only assigned when the owner marks it **Ready**. Made-to-order items can take days, so booking the courier at payment would bring a courier before the item exists.

**Shipping charge (FR-25):** set in config, e.g. `free_above: 999`, `flat_fee: 79`. Courier rates from Shiprocket are not shown to the customer.

---

## 5. Customisation and photo uploads (FR-12 to FR-14)

- The product page renders the form fields from the category's `customisation` spec (photo × N, text with a character limit, colour from `colours`, size from `sizes`, notes).
- Photos are uploaded directly from the browser to the **private** Supabase bucket using a signed upload URL. Allowed: JPG/PNG, up to 10 MB, checked on the server too. Photo guidance text and a "made to order: ships in ~X days" note (lead_time_days) are shown.
- The customisation JSON + photo paths are saved on `order_items` and visible to the owner, who can download the originals.
- Privacy: a consent checkbox for storing photos, and customer photos are **auto-deleted 60 days after delivery**. This also keeps us under the 1 GB free storage.

---

## 6. GST invoice (FR-18 to FR-20)

- Numbering: `VS/2026-27/00001`. Gapless and reset each financial year, using a locked counter row in the database.
- Prices include tax, so taxable value = price ÷ (1 + rate). **CGST + SGST** if the ship-to state equals the seller's state, otherwise **IGST**. Shipping is taxed at the order's dominant rate (to be confirmed with the owner's CA).
- Contents follow FR-19: seller name, address and GSTIN; invoice number and date; customer and address; HSN, quantity, taxable value, GST split, shipping, discount, total, payment mode.
- COD orders: invoice created at confirmation (or at dispatch, depending on the owner's choice).
- ⚠️ **Without a GSTIN** a "tax invoice" can't legally be issued. The system would issue a plain "Invoice / Bill of Supply" until a GSTIN exists.

---

## 7. Data model (Postgres)

```
customers        id, auth_uid, name, email, phone, photo_url, role (CUSTOMER|ADMIN)
addresses        id, customer_id, name, phone, line1, line2, city, state, pincode, is_default
categories       id, name, slug, hsn, gst_rate, weight_g, l/w/h_cm, lead_time_days,
                 customisation_spec (jsonb), colours[], sizes[], returnable
products         sku (PK), category_id, name, slug, description, mrp, selling_price,
                 offer_pct, stock, active, synced_at
product_images   id, sku, url, sort_order, checksum
wishlist         customer_id, sku
orders           id, order_no, customer_id, address_snapshot (jsonb), subtotal, shipping_fee,
                 discount, total, payment_mode (ONLINE|COD), status, created_at
order_items      id, order_id, sku, name/price/hsn/gst snapshots, qty,
                 customisation (jsonb), upload_paths[]
payments         id, order_id, provider, provider_order_id, provider_payment_id, status, raw
invoices         id, order_id, invoice_no, type (INVOICE|CREDIT_NOTE), pdf_path, totals (jsonb)
shipments        id, order_id, sr_order_id, sr_shipment_id, awb, courier, label_url,
                 tracking_url, status, events (jsonb)
order_events     id, order_id, from_status, to_status, actor, at          ← status timeline (S9)
sync_runs        id, started_at, rows_ok, rows_failed, errors (jsonb)
notification_log id, order_id, channel (EMAIL|SMS), template, status, error
```

---

## 8. Running costs

| Item | Cost |
|---|---|
| Hosting, database, storage, email, auth | ₹0 (free tiers) |
| Domain (e.g. vindhyanstudio.in) | ~₹800/year |
| Razorpay | ~2% per online payment, no fixed fee |
| Shiprocket | Per shipment (courier rate). COD orders have an extra COD fee |
| **SMS OTP + order SMS** | **~₹0.20–0.30 per SMS** via MSG91. Indian rules also require **DLT registration** (sender ID + templates) before any SMS can be sent, which takes days to set up |

To keep SMS costs low: **email login works from day one at ₹0**, and mobile OTP is switched on once DLT is approved. The BRD itself lists "email login as fallback".

---

## 9. Phases (mapped to the BRD)

| Phase | Delivers | FRs | Accounts needed |
|---|---|---|---|
| **0. Scaffold** | Monorepo, Spring Boot + React running locally, Flyway schema, mock payment and shipping, CI | — | GitHub |
| **1. Catalogue & browse** | Drive sync + error report, home/search/category (S1), product page with gallery (S2), cart (S3), wishlist (S6) | 07–11, 26 | Supabase, Google Cloud service account |
| **2. Accounts** | Email login, profile, addresses (S4, S5) | 01–05 | Brevo |
| **3. Checkout & pay** | Customisation + uploads, checkout (S7), Razorpay test mode, COD, success page (S8), GST invoice | 12–19, 25 | Razorpay |
| **4. Shipping & admin** | Pincode check, Shiprocket order/AWB/label, webhooks, tracking (S9), owner admin (S10), stock write-back | 21–24, 27, 28 | Shiprocket |
| **5. Go live** | Policy pages, Razorpay KYC → live keys, domain, backups, mobile-speed check | Compliance | Domain |
| **6. After launch** | Mobile OTP + SMS (after DLT), Reorder, credit notes, profile photo | 01, 06, 20, 23 | MSG91 |

Every "Must" in the BRD is covered by phase 5, except mobile OTP and SMS, which depend on DLT approval rather than on code.

---

## 10. Decisions needed from the owner

1. **The existing prototype (BRD §7):** what is it built with, and can we see the code? Options: (a) rebuild in React + Spring Boot and reuse its logic and Excel format, or (b) continue the prototype as it is. This plan assumes (a).
2. **Where the Excel lives:** Google Sheet in Drive (recommended, allows stock write-back) or an .xlsx file uploaded through admin?
3. **Sizes with different prices** (e.g. a 4×6 vs 6×8 lithophane): one SKU per size (works with the current Excel), or one SKU with size-based prices (needs a new column)?
4. Seller **GSTIN, address, HSN codes and GST rates** per category.
5. **COD:** on or off at launch? Maximum order value for COD? Is COD allowed for customised items, which carry a high risk of being refused at delivery?
6. **Shipping rule:** flat fee and free-above amount.
7. **Production lead time** per category, and the **return/refund policy** for customised items.
8. **OTP/SMS provider and DLT:** does the business have a registered entity for DLT? If not, launch with email only.
9. **Reference websites** for the look and feel.
