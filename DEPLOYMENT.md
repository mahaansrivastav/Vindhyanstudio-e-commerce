# Deploying Vindhyan Studio

```
 Customer ──► https://<your-domain>        Frontend (Vercel, free)
                     │  calls
                     ▼
              https://api.<your-domain>    Backend (Railway, Docker)
                     │
          ┌──────────┴───────────┐
          ▼                      ▼
   Supabase Postgres      Supabase Storage        (+ Brevo email, later Razorpay / Shiprocket)
   (products, orders)     (photos, invoices)
```

Do the steps in this order. Every value goes into the platform's **environment variables**, never into the code.
Templates: `backend/.env.production.example`, `frontend/.env.production.example`.

---

## 1. Supabase (database + file storage)

1. supabase.com → **New project** → region **Mumbai (ap-south-1)** → set a strong database password (save it).
2. **Database connection:** Project → **Connect** → **Session pooler** → JDBC. Turn it into the three variables:
   - `DB_URL=jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require` (use the host shown for your project)
   - `DB_USERNAME=postgres.<project-ref>`
   - `DB_PASSWORD=<the password from step 1>`

   Use the **session** pooler (port 5432), not the transaction pooler (6543), because migrations need it. Tables are created automatically on first start (Flyway).
3. **Storage:** Storage → **New bucket**
   - `catalogue`: tick **Public bucket** (product photos)
   - `private`: **not** public (customer photos, invoices)
4. **Keys:** Project Settings → API Keys → copy the **service_role / secret** key → `SUPABASE_SERVICE_KEY`. It goes **only** into the backend, never into the frontend.
   `SUPABASE_URL=https://<project-ref>.supabase.co`
5. Free projects pause after 7 days with no traffic. A real shop gets traffic; you can also add a free uptime ping (step 6).

## 2. Brevo (email; required, because customers log in with an email code)

1. brevo.com → sign up → **Senders & IP → Domains** → add your domain and add the DNS records it shows (SPF/DKIM) at your domain registrar.
2. **SMTP & API → SMTP** → copy the login and create an SMTP key → `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`.
3. `NOTIFY_FROM_EMAIL=Vindhyan Studio <orders@<your-domain>>`

## 3. Railway (backend)

1. railway.com → **New project → Deploy from GitHub repo** → pick `Vindhyanstudio-e-commerce`.
   (If the repo is private, allow Railway access to it when asked.)
2. Service → **Settings**:
   - **Root directory:** `backend` (Railway finds the `Dockerfile` there)
   - **Healthcheck path:** `/actuator/health`
3. Service → **Variables → Raw editor** → paste `backend/.env.production.example` with real values.
   Railway sets `PORT` itself; the app already uses it.
4. Deploy. In the logs, look for `Started BackendApplication`. Then **Settings → Networking → Generate domain** and open
   `https://<generated>.up.railway.app/actuator/health`. It should say `{"status":"UP"}`.
5. **Custom domain:** Settings → Networking → **Custom domain** → `api.<your-domain>` → Railway shows a **CNAME** record → add it at your domain registrar.

Cost: Railway has no permanent free tier (a small trial, then about $5/month on the Hobby plan, which covers this app).

## 4. Publish the catalogue (from your laptop)

The live server doesn't have the Excel folder. You publish the catalogue from your machine into the production database and storage:

```bash
cp backend/.env.production.example backend/.env.production.local   # then fill in the same real values
./scripts/publish-catalogue.sh
```

It prints `Catalogue published: N live, M skipped` plus any problems. Run it again whenever `catalogue/catalogue.xlsx` or `catalogue/asset_image/` changes. The owner can also add or edit products directly in **Admin → Products** on the live site.

## 5. Vercel (frontend)

1. vercel.com → **Add New → Project** → import the GitHub repo.
2. **Root directory:** `frontend`. Framework is detected as **Vite** (build `npm run build`, output `dist`).
3. **Environment variables:** `VITE_API_BASE_URL=https://api.<your-domain>`
4. Deploy, then **Settings → Domains** → add `<your-domain>` and `www.<your-domain>`. Vercel shows the DNS records to add at your registrar (an **A** record for the root domain, a **CNAME** for `www`).

Netlify works the same way (base directory `frontend`, publish directory `frontend/dist`, same variable). Add a `_redirects` file with `/* /index.html 200`.

## 6. DNS summary (at your domain registrar)

| Type | Name | Value |
|---|---|---|
| A | `@` | Vercel's IP (shown in Vercel → Domains) |
| CNAME | `www` | `cname.vercel-dns.com` |
| CNAME | `api` | the target Railway shows |
| TXT/CNAME | (from Brevo) | Brevo's SPF / DKIM records |

DNS can take from a few minutes to a few hours. HTTPS certificates are issued automatically by Vercel and Railway.

Optional: free uptime monitoring with UptimeRobot on `https://api.<your-domain>/actuator/health` every 5 minutes. It alerts you if the site goes down and keeps Supabase awake.

## 7. Go-live checklist

- [ ] `https://api.<your-domain>/actuator/health` shows UP
- [ ] Catalogue published; products and photos show on `https://<your-domain>`
- [ ] Log in with the owner's email (code arrives by email) → **Admin** button appears (`ADMIN_IDENTITIES`)
- [ ] Place a cash-on-delivery test order → order email received → Admin: Printing → Packed → Dispatched → Delivered → cancel the test order
- [ ] Real business details in `SELLER_*`, and the policy texts reviewed (`frontend/src/pages/Policy.tsx`)
- [ ] GitHub repo set to **Private**

## Switching integrations on later (no code change, just variables + redeploy)

| When | Set |
|---|---|
| Razorpay approved | `PAYMENT_PROVIDER=razorpay`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`. Webhook URL: `https://api.<your-domain>/api/webhooks/razorpay` |
| Shiprocket ready | `SHIPPING_PROVIDER=shiprocket` + `SHIPROCKET_*`. Webhook URL: `https://api.<your-domain>/api/webhooks/tracking` |
| SMS (after DLT) | needs an MSG91 sender (not built yet); mobile login appears once `NOTIFY_SMS` isn't `console` |

## Safety notes

- `PAYMENT_PROVIDER=mock` is for local testing only. On a public site it lets anyone "pay" without paying. Production uses `disabled` (COD only) or `razorpay`.
- Never commit `.env.production.local`, the database password or the Supabase service key. If one leaks, rotate it in Supabase or Railway.
