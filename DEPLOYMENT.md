# Deploying and configuring Vindhyan Studio

Everything below is **configuration only**: environment variables, dashboards and DNS. No code changes are needed to move hosts or to switch payment gateways or shipping on and off.

```
 Customer ─► https://<domain>          Frontend: Vercel / Netlify / Cloudflare Pages (stays put)
                  │ calls
                  ▼
           https://api.<domain>        Backend: Render (or Railway, Fly, any Docker host)
                  │
        ┌─────────┴──────────┐
        ▼                    ▼
  Postgres (Supabase)   File storage (Supabase)      + Brevo email, payment gateway, Shiprocket
```

**Why the backend is portable:** the frontend only knows `https://api.<domain>`. To move the backend, deploy the same code on the new host with the same environment variables, then point the `api` DNS record at it. The frontend is never rebuilt. The database moves the same way: change `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

Templates: `backend/.env.production.example` (all backend variables), `frontend/.env.production.example`, `render.yaml` (Render blueprint).

---

## 1. Supabase (database + file storage)

1. supabase.com → **New project** → region **Mumbai** → set a database password (save it).
2. Project → **Connect** → **Session pooler** → JDBC:
   - `DB_URL=jdbc:postgresql://<host>.pooler.supabase.com:5432/postgres?sslmode=require`
   - `DB_USERNAME=postgres.<project-ref>`, `DB_PASSWORD=<password>`
   - Use the **session** pooler (5432), not the transaction pooler (6543). Tables are created automatically on first start.
3. Storage → **New bucket** `catalogue` (**Public**) and `private` (**not** public).
4. Project Settings → API Keys → **service_role / secret** key → `SUPABASE_SERVICE_KEY` (backend only). `SUPABASE_URL=https://<project-ref>.supabase.co`.

## 2. Brevo (email; required, customers log in with an email code)

1. brevo.com → **Senders & IP → Domains** → add your domain and copy its DNS records to your registrar.
2. **SMTP & API → SMTP** → `SPRING_MAIL_USERNAME` (login) and `SPRING_MAIL_PASSWORD` (SMTP key).
3. `NOTIFY_FROM_EMAIL=Vindhyan Studio <orders@<domain>>`

## 3. Backend on Render

1. render.com → **New → Blueprint** → connect the GitHub repo → it reads `render.yaml` and creates `vindhyan-backend`. Or use **New → Web Service → Docker** with **Root directory** `backend` and **Health check path** `/actuator/health`.
2. Fill the variables it asks for (values from `backend/.env.production.example`). `JWT_SECRET` is generated for you.
3. Deploy → open `https://<service>.onrender.com/actuator/health` → it should show `{"status":"UP"}`.
4. **Settings → Custom domains → `api.<domain>`** → add the CNAME it shows at your registrar.
5. Plan: **Starter** (~$7/month) stays awake. **Free** sleeps after 15 minutes idle (the first visitor waits ~1 minute), so use it only for testing.

**Other hosts:** Railway (root directory `backend`, Dockerfile), Fly.io or any VPS:

```bash
docker build -t vindhyan-backend ./backend
```

Run that image with the same env file, or upload just the JAR (`java -jar app.jar`). The app reads `PORT` from the host.

## 4. Publish the catalogue (from your laptop)

```bash
cp backend/.env.production.example backend/.env.production.local
```

Fill in the same DB_* and SUPABASE_* values, then:

```bash
./scripts/publish-catalogue.sh
```

Re-run it whenever `catalogue/catalogue.xlsx` or its photos change. The owner can also manage products in **Admin → Products**.

## 5. Frontend (Vercel / Netlify / Cloudflare Pages)

- Root/base directory `frontend`, build `npm run build`, output `dist`.
- Env: `VITE_API_BASE_URL=https://api.<domain>`. It never changes when the backend moves.
- Add domains `<domain>` and `www.<domain>`. Routing files are already included (`vercel.json`, `public/_redirects` for Netlify/Cloudflare).

## 6. DNS at your registrar

| Type | Name | Points to |
|---|---|---|
| A / CNAME | `@`, `www` | frontend host (shown in its Domains page) |
| CNAME | `api` | backend host (Render custom-domain target) |
| TXT/CNAME | Brevo records | email authentication |

---

## 7. Payment gateway: what you do vs what's already built

**Already built (code):** create order → payment page → gateway popup → server-side verification → order confirmed. The paid amount must match the order total. A signed webhook confirms payments if the customer closes the tab. A **reconciliation job every 5 minutes** catches missed webhooks. Cancelling a paid order in admin **refunds automatically**. Supports **Razorpay** and **Cashfree**; pick one with `PAYMENT_PROVIDER`.

**What you do (needs your login):**

### Razorpay
1. Sign up at razorpay.com → complete **KYC** (PAN, bank account, business details, website URL). They review the live website, so the policy pages, contact details and prices must be visible. This usually takes 2–5 working days.
2. **Test first:** Dashboard (Test mode) → Account & Settings → **API Keys** → generate → `RAZORPAY_KEY_ID=rzp_test_…`, `RAZORPAY_KEY_SECRET=…`
3. **Webhooks** → Add → URL `https://api.<domain>/api/webhooks/payments` → events **payment.captured, payment.failed, order.paid** → set a secret → `RAZORPAY_WEBHOOK_SECRET`.
4. **Payment capture** setting → **Automatic** (Account & Settings → Payment capture).
5. Set `PAYMENT_PROVIDER=razorpay` → redeploy → place a test order with Razorpay's test card/UPI.
6. After KYC approval: switch the dashboard to **Live mode**, generate live keys (`rzp_live_…`), create the same webhook in live mode, update the 3 variables → redeploy.

### Cashfree (alternative)
1. Sign up at cashfree.com (Payment Gateway) → KYC, same website requirements.
2. **Sandbox:** Developers → API Keys → `CASHFREE_APP_ID`, `CASHFREE_SECRET_KEY`, `CASHFREE_ENV=sandbox`.
3. Developers → **Webhooks** → URL `https://api.<domain>/api/webhooks/payments` → payment events (signed with your secret key; nothing else to copy).
4. In Cashfree, **whitelist your domain** (Developers → Whitelisting) so checkout opens on it.
5. `PAYMENT_PROVIDER=cashfree` → redeploy → test. When live: production keys + `CASHFREE_ENV=production`.

### Turning payments off again
`PAYMENT_PROVIDER=disabled` → the site takes cash-on-delivery orders only. `mock` is **local testing only**; on a public site it lets anyone "pay" without paying.

## 8. Shiprocket: what you do vs what's already built

**Already built:** after payment (or a COD order) a Shiprocket order is created with the address, items, weight and size. **Packed** in admin books the courier, assigns the AWB, requests pickup and gets the label. Tracking webhooks update the order (shipped, delivered, RTO) and message the customer. Cancelling in admin cancels it at Shiprocket.

**What you do:**
1. Sign up at shiprocket.in → complete KYC → recharge the wallet (shipping is prepaid from it).
2. Settings → **Pickup addresses** → add the workshop address. Its nickname goes in `SHIPROCKET_PICKUP_LOCATION`, its pincode in `SHIPROCKET_PICKUP_PINCODE`.
3. Settings → API → **Create API user** (a separate email and password; not your login) → `SHIPROCKET_EMAIL`, `SHIPROCKET_PASSWORD`.
4. Settings → API → **Webhooks** → URL `https://api.<domain>/api/webhooks/tracking`, token = any random string → same value in `SHIPROCKET_WEBHOOK_TOKEN`.
5. In admin, fill each category's **weight and size**; courier prices depend on them.
6. `SHIPPING_PROVIDER=shiprocket` → redeploy.

Options: `SHIPROCKET_AUTO_BOOK=true` books the courier right after payment (ready-stock items); `false` (default) waits for **Packed**, which suits made-to-order items. `SHIPROCKET_COURIER_ID` forces one courier (blank = Shiprocket's choice). `SHIPPING_PROVIDER=manual` = no integration; the owner books couriers himself.

## 9. Go-live checklist

- [ ] `https://api.<domain>/actuator/health` → UP
- [ ] Catalogue published; products and photos show on `https://<domain>`
- [ ] Owner logs in by email → **Admin** appears (`ADMIN_IDENTITIES`)
- [ ] COD test order → email received → Printing → Packed → Dispatched → Delivered
- [ ] Online test order (gateway test keys) → paid → cancel → refund visible in the gateway dashboard
- [ ] Real `SELLER_*` details; policy texts reviewed (`frontend/src/pages/Policy.tsx`)
- [ ] GitHub repo **private**; no `.env.production.local` committed

## Safety

- Secrets live only in the host's environment variables and your local `.env.production.local` (git-ignored). If one leaks, rotate it in that service's dashboard.
- Changing any variable needs a **redeploy/restart** of the backend to take effect.
