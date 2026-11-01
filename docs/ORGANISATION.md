# Vindhyan Studio: organisation and workflows

How the business is meant to be organised as it grows. Today one or two people cover several roles;
the point is that every responsibility has a clear owner. Backlog items in [ROADMAP.md](ROADMAP.md) name these departments as owners.

## Departments

| Department | Responsible for |
|---|---|
| **Leadership** | Strategy, budget, pricing, partnerships, final approvals |
| **Production (Manufacturing)** | 3D printing, finishing, quality check, printer maintenance, material stock (filament, glue, paint) |
| **Design / R&D** | New product designs, licensing of 3D models, personalisation templates, product photos and size references |
| **Catalogue** | Product listings: names, prices, MRP, stock, categories, mandatory details (manufacturer, quantity, origin) |
| **Content** | Product descriptions, website text, email wording, policy wording (with Legal) |
| **Marketing** | Instagram / WhatsApp / Google presence, campaigns, offers, SEO, influencer and B2B gifting outreach |
| **Sales (B2B / bulk)** | Corporate and bulk orders, quotes, GST invoices for businesses |
| **Operations (Fulfilment)** | Packing, courier booking (Shiprocket), dispatch, delivery issues, returns (RTO) |
| **Customer Support** | WhatsApp / email queries, order issues, returns and refunds, grievance officer duties |
| **Finance & Accounts** | Payments, refunds, GST filing, invoices, reconciliation, payment-gateway KYC |
| **Legal & Compliance** | Policies, consumer and data-protection rules, contracts, IP, model licences |
| **Technology** | Website, admin, integrations, data, backups, security, hosting |

## Core workflows

**1. Order to delivery**
Order placed → payment confirmed → *Production* prints and checks quality → *Operations* packs, marks "Packed" (courier booked) → hands to courier ("Shipped") → delivered → *Support* handles any issue within 48 hours.

**2. New product launch**
*Design* creates or licenses the model and shoots photos → *Catalogue* adds it (Excel upload or admin) with price, stock and mandatory details → *Content* writes the description → *Legal* checks the model licence → *Marketing* announces it.

**3. Price or stock update**
*Catalogue* downloads the product list → edits in Excel → uploads → reviews every change → approves. *Leadership* approves price changes above an agreed limit.

**4. Complaint / grievance**
*Support* acknowledges within 48 hours → resolves (replace, refund, explain) within one month → logs it in the grievance register → *Finance* processes refunds to the original payment method.

**5. Cancellation and refund**
Customer or *Support* cancels → online payments refund automatically → *Finance* checks the refund in the gateway dashboard → *Operations* cancels the courier booking if needed.

**6. Month-end review**
*Finance* downloads the Excel business report (sales, GST summary, products, stock) → shares with the CA → *Leadership* reviews sales, best sellers and stock to plan production and marketing.

**7. Website change**
Anyone adds the request to `ROADMAP.md` → *Technology* estimates and builds → *Legal* checks if it touches policies, payments or personal data → released and logged in the Done log.

## Key documents
| Document | Where |
|---|---|
| Roadmap and compliance backlog | `docs/ROADMAP.md` |
| Deployment and configuration | `DEPLOYMENT.md` |
| Policies shown on the website | `frontend/src/pages/Policy.tsx` → `/policies/*` |
| Email previews | `docs/email-previews/` |
| Business reports | Admin → Dashboard → Download Excel report |
