# Vindhyan Studio

E-commerce site for custom 3D printed products. The flow it automates: **customer personalises and pays → invoice + Shiprocket order are created automatically → owner prints, packs, clicks "Packed", and hands the parcel to the courier.**

| Part | Tech | Folder |
|---|---|---|
| Storefront + owner admin | React 19, Vite, TypeScript, Tailwind v4 | `frontend/` |
| API | Spring Boot 4.1, Java 21, Flyway, JPA | `backend/` |
| Catalogue (owner edits this) | `catalogue.xlsx` + `photos/<SKU_id>/` | `catalogue/` |
| Requirements / plan | BRD + build plan | `docs/`, `PLAN.md` |

## Run locally (no accounts or Docker needed)

Prerequisites: Java 21, Node 20+.

```bash
cd backend && ./mvnw spring-boot:run
```

```bash
cd frontend && npm install && npm run dev
```

Open http://localhost:5180. If port 8080 is busy, run the backend with `PORT=8081 APP_PUBLIC_BASE_URL=http://localhost:8081` and the frontend with `BACKEND_URL=http://localhost:8081 npm run dev`.

- **Local mode** uses a file-based H2 database (`backend/data/`). Payments, shipping, email and SMS are **mocked**: payments show a "Simulate payment" dialog, and messages are printed in the backend console.
- **Login:** on the login screen, the OTP is shown on the page ("Local dev code").
- **Admin:** log in with `admin@vindhyanstudio.local` (Email tab), then click **Admin**. Admins are set with `ADMIN_IDENTITIES`.
- **Reset everything:** stop the backend and delete `backend/data/`.
- **Mock shipping:** pincodes starting with 9 are treated as undeliverable, so you can test that path.

## Environments

The backend picks its environment from `SPRING_PROFILES_ACTIVE`:

| Profile | Database | Integrations | Use |
|---|---|---|---|
| `local` (default) | H2 file | all mocked | your laptop |
| `dev` | Postgres (Supabase) | mocked by default, switch each one on with env vars | hosted staging |
| `prod` | Postgres (Supabase) | Razorpay + Shiprocket + SMTP by default | live shop |

Every setting is an env var with a safe local default; see `backend/src/main/resources/application.yml`. The frontend only needs `VITE_API_BASE_URL` (see `frontend/.env.example`).

### Backend env vars

| Var | Example | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` / `prod` | |
| `DB_URL` | `jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require` | Supabase → Connect → **Session pooler** (works on IPv4 hosts like Render) |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres.<project-ref>` / … | |
| `JWT_SECRET` | 48+ random chars | **Must** be changed outside local |
| `APP_PUBLIC_BASE_URL` | `https://api.vindhyanstudio.in` | Backend's own URL (webhooks, file links) |
| `APP_FRONTEND_URL` | `https://vindhyanstudio.in` | Used in email/SMS links |
| `APP_CORS_ORIGINS` | `https://vindhyanstudio.in,https://dev.vindhyanstudio.in` | Comma-separated |
| `ADMIN_IDENTITIES` | `owner@gmail.com,9876543210` | Who gets the admin screens |
| `SELLER_NAME`, `SELLER_ADDRESS`, `SELLER_STATE`, `SELLER_GSTIN`, `SELLER_EMAIL` | | Printed on invoices. Without a GSTIN, a non-tax invoice is issued |
| `SHIPPING_FLAT_FEE`, `FREE_SHIPPING_ABOVE`, `COD_ENABLED`, `COD_MAX_ORDER_VALUE` | `79`, `999`, `true`, `3000` | BRD FR-25 |
| `STORAGE_PROVIDER` | `local` / `supabase` | Use `supabase` on hosts with ephemeral disks (Render) |

## Integrations: how to switch each one on

Every integration sits behind an interface with a mock, so you can switch them on one at a time:

| Integration | Interface | Implementations | Switch |
|---|---|---|---|
| Payments | `payment/PaymentGateway` | Mock (local), Disabled (COD only), Razorpay, Cashfree | `PAYMENT_PROVIDER` |
| Shipping | `shipping/ShippingProvider` | Mock (local), Manual, Shiprocket | `SHIPPING_PROVIDER` |
| Email / SMS | `notify/MessageSender` | console, `SmtpEmailSender` | `NOTIFY_EMAIL`, `NOTIFY_SMS` |
| File storage | `storage/StorageService` | `LocalStorageService`, `SupabaseStorageService` | `STORAGE_PROVIDER` |
| Catalogue | `catalogue/sync/CatalogueSource` | `LocalExcelCatalogueSource` | `CATALOGUE_SOURCE` |

To add another provider (e.g. Cashfree or MSG91), implement the interface, annotate it with `@ConditionalOnProperty(name = "app.payment.provider", havingValue = "cashfree")`, and set the env var.

### Razorpay (payments)
1. Sign up at razorpay.com. **Test mode** keys work immediately.
2. Set `PAYMENT_PROVIDER=razorpay`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`.
3. Dashboard → Webhooks → add `{APP_PUBLIC_BASE_URL}/api/webhooks/payments` with events `payment.captured` and `payment.failed`, then set the same secret in `RAZORPAY_WEBHOOK_SECRET`.
4. Live mode needs KYC. The site's policy pages (`/policies/*`) must have real content first; they're drafts now.

### Shiprocket (shipping)
1. Shiprocket → Settings → API → **Create API user** (a separate email/password).
2. Settings → Pickup addresses → add the workshop address. Its nickname goes in `SHIPROCKET_PICKUP_LOCATION`, and its pincode in `SHIPROCKET_PICKUP_PINCODE`.
3. Set `SHIPPING_PROVIDER=shiprocket`, `SHIPROCKET_EMAIL`, `SHIPROCKET_PASSWORD`.
4. Webhooks: URL `{APP_PUBLIC_BASE_URL}/api/webhooks/tracking`. Pick any token and set it as `SHIPROCKET_WEBHOOK_TOKEN`. Shiprocket rejects URLs containing "shiprocket", which is why the path is neutral.
5. Fill weight and size per category in the `categories` sheet; Shiprocket rates depend on them.

How it behaves: the Shiprocket order is created right after payment. The courier (AWB) is booked and the label generated when the owner clicks **Packed — book courier** in admin. The "Print shipping label" button appears there.

### Email (Brevo SMTP, free 300/day)
Set `NOTIFY_EMAIL=smtp`, `SPRING_MAIL_HOST=smtp-relay.brevo.com`, `SPRING_MAIL_PORT=587`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`, `NOTIFY_FROM_EMAIL="Vindhyan Studio <orders@yourdomain>"`. Verify the sender domain in Brevo.

### SMS / mobile OTP
Not wired yet; mobile OTPs go to the console. In India SMS needs DLT registration first. After that, add an `Msg91SmsSender implements MessageSender` and set `NOTIFY_SMS=msg91`. Until then, use email login in hosted environments.

### Supabase (database + storage)
1. Create a project (region: Mumbai). Flyway creates all tables on first start.
2. Storage: create bucket `catalogue` (**public**) and bucket `private` (**private**). Set `STORAGE_PROVIDER=supabase`, `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` (service_role key; never put it in the frontend).
3. Free projects pause after 7 days without traffic. Any real use (or an uptime ping on `/actuator/health`) keeps them awake.

## Managing products (Admin → Products)

Log in as an admin and open **Admin → Products**:

- **Add product:** name, category, MRP, selling price, stock and description. Drag photos in (JPG/PNG/WebP, up to 10). It's live on the site as soon as you save.
- **Edit:** change any field, add, delete or reorder photos (the first is the cover), or untick **Show on website** to hide a product.
- **Categories:** GST/HSN, shipping weight and size, days to make, and personalisation options (`photo:1`, `text`, `colour`…) for every product in the category.
- Products added here are marked **managed here** and the Excel sync never touches them. Products from Excel are read-only in admin until you click **Manage here instead**.

## Payment page

Online orders go **Cart → Address (checkout) → Payment** (`/pay/<orderId>`). The payment page shows the amount due, the order summary, the delivery address, payment-method choices (UPI, cards, net banking, wallets, and cash on delivery when allowed), security notes, seller contact and policy links. With `PAYMENT_PROVIDER=razorpay` the **Pay** button opens Razorpay with the chosen method preselected. If the customer closes it, the order stays "Payment pending" and they can pay later from the order page.

**Before applying for a payment gateway**, set real `SELLER_NAME`, `SELLER_ADDRESS`, `SELLER_EMAIL` and `SELLER_PHONE` (shown on the payment and Contact pages) and replace the draft policy text in `frontend/src/pages/Policy.tsx`. Gateways check for business contact details plus Terms, Privacy, Refund and Shipping policies.

## Catalogue (owner's Excel)

`catalogue/catalogue.xlsx` is the owner's own working sheet (originals in `docs/catalogue-source/`). The sync reads it as-is:

| Sheet column | Used as |
|---|---|
| `S no.` | SKU `VS-001`, `VS-002`… (keep numbers stable) |
| `Flag` | `0` = hidden from the website, blank/anything else = live |
| `Category` / `Sub-Category` | category / product name ("customisable" + Keychain → "Customisable Keychain") |
| `MRP (₹)…` / `Selling Price (₹)` | prices (formulas are evaluated) |
| `Description` + `Details` | product description (marketplace "Boost me" text is stripped) |
| `Image File` | main photo, e.g. `asset_image/3.png`, relative to `catalogue/` |

- Photos live in `catalogue/asset_image/`. Extra photos for a product can go in `catalogue/photos/<SKU>/`. The sync makes web-sized copies (max 1200 px), and the originals are never changed.
- There's no stock column, so products are made to order and never show "out of stock".
- Category settings (GST, weight/size, days to make, colours, styles) and **per-product personalisation** (e.g. `style, colour, text` for name keychains) are set in **Admin → Products / Categories** and are kept across syncs.
- The sync runs on startup, every 10 minutes, and from **Admin → Excel sync**. Rows with problems (e.g. a missing image) are skipped and listed there.
- Our older template layout (`SKU_id, Product_Name, Product_category, MRP, Selling_Price, stock…` plus a `categories` sheet) still works too.

## Deploying (free tiers)

1. **Database + storage:** Supabase (see above).
2. **Backend:** Render → New Web Service → this repo, root `backend`, Docker. Set the env vars and health check path `/actuator/health`. Note that the free tier sleeps after 15 minutes idle; an Oracle Cloud Always-Free VM avoids that.
3. **Frontend:** Vercel → import repo, root `frontend`, set `VITE_API_BASE_URL`. `vercel.json` handles SPA routing.
4. Create one Render service + one Vercel project per environment (dev, prod), each with its own env vars and Supabase project.

> **Hosted catalogue:** `LocalExcelCatalogueSource` reads a folder on the server, which suits local and VM hosting. For Render/Vercel the next step is `GoogleDriveCatalogueSource` (Google Sheet + Drive photos folder), as planned in `PLAN.md`.

## API overview

Public: `GET /api/config`, `GET /api/catalogue/{categories,products,products/{slug}}`, `POST /api/cart/quote`, `GET /api/shipping/serviceability`, `POST /api/auth/otp/{request,verify}`
Customer: `/api/me`, `/api/me/addresses`, `/api/me/link/*`, `POST /api/uploads`, `/api/orders`, `/api/orders/{id}/pay`, `POST /api/orders/{id}/cod`, `/api/orders/{id}/invoice`, `POST /api/payments/verify`
Admin: `/api/admin/summary`, `/api/admin/orders`, `POST /api/admin/orders/{id}/status`, `/api/admin/products` (+ `/images`, `/images/order`, `/take-over`), `/api/admin/categories`, `POST /api/admin/catalogue/sync`
Webhooks: `/api/webhooks/payments` (active gateway), `/api/webhooks/tracking` (Shiprocket)

> Full production setup (Render/Supabase/Vercel, payment gateway and Shiprocket onboarding): see **DEPLOYMENT.md**.
