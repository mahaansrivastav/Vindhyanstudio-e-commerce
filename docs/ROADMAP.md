# Vindhyan Studio: roadmap and compliance backlog

Living document. Every planned change to the website and business processes goes here, grouped by area.
Update the **Status** column when work starts or finishes, and move finished items to the log at the bottom.

**Status:** ✅ done · 🟡 partly done · ⬜ not started
**Priority:** P1 = before scaling / legal risk · P2 = next quarter · P3 = nice to have
**Owner:** the department responsible (see [ORGANISATION.md](ORGANISATION.md))

Last reviewed: November 2026

---

## 1. Legal and compliance

### 1.1 Consumer Protection (E-Commerce) Rules, 2020
| ID | Item | Status | Priority | Owner | Notes |
|---|---|---|---|---|---|
| C-01 | Legal name, address, GSTIN, contact details on the site | ✅ | — | Legal | Footer + Contact page |
| C-02 | **Grievance officer's name** + contact; acknowledge in 48 h, resolve within 1 month | 🟡 | P1 | Legal / Support | Email and phone shown; **name missing** on Contact and Terms pages |
| C-03 | Return, refund, exchange, shipping and cancellation policies | ✅ | — | Legal | `/policies/*` |
| C-04 | Full price breakdown (items, shipping, taxes) before payment | ✅ | — | Tech | Cart, checkout, payment page |
| C-05 | Country of origin on products | ✅ | — | Catalogue | Product page footer |
| C-06 | Explicit agreement to terms at checkout, no pre-ticked boxes | ✅ | — | Tech | Line above "Place order" and "Pay" buttons |
| C-07 | Refund to the original payment method in reasonable time | ✅ | — | Finance | Automatic refund on cancel (Razorpay) |
| C-08 | Grievance tracking: log every complaint, time to acknowledge and resolve | ⬜ | P2 | Support | Start with a shared sheet; later a "Help" ticket screen |

### 1.2 Legal Metrology (Packaged Commodities) Rules: product listing details
| ID | Item | Status | Priority | Owner | Notes |
|---|---|---|---|---|---|
| L-01 | MRP inclusive of all taxes | ✅ | — | Catalogue | |
| L-02 | **Manufacturer / packer name and address** on every product | ⬜ | P1 | Catalogue / Tech | "Manufactured by MORVIN INTEGRATED SOLUTIONS, Gorakhpur" |
| L-03 | **Net quantity / dimensions** (e.g. "1 piece · approx. 6 × 3 cm") | ⬜ | P1 | Catalogue / Tech | Add a column in the product Excel + show on product page |
| L-04 | Generic product name (what it is) | 🟡 | P2 | Content | Some names are model names only ("Lion", "Hindu God") |

### 1.3 Digital Personal Data Protection Act, 2023
| ID | Item | Status | Priority | Owner | Notes |
|---|---|---|---|---|---|
| D-01 | Privacy notice: what is collected, why, retention, how to withdraw consent | 🟡 | P1 | Legal | Exists; reword to DPDP terms |
| D-02 | **Delete my account** (customer self-service) | ⬜ | P1 | Tech | Keep invoices as tax law requires; anonymise the rest |
| D-03 | **Download my data** | ⬜ | P2 | Tech | Profile, addresses, orders as a file |
| D-04 | **Auto-delete customer photos 90 days after delivery** | ⬜ | P1 | Tech | Promised in the privacy policy, **not built**. Build it or change the policy |
| D-05 | Named contact for data requests | ⬜ | P1 | Legal | Can be the grievance officer |
| D-06 | Personal-data breach response plan (who informs whom, within what time) | ⬜ | P2 | Tech / Legal | One-page runbook |
| D-07 | Marketing messages only with separate opt-in | ⬜ | P2 | Marketing / Tech | No marketing is sent today |

### 1.4 Consumer Protection (Dark Patterns) Guidelines, 2023
| ID | Item | Status | Priority | Owner | Notes |
|---|---|---|---|---|---|
| P-01 | No false urgency, hidden charges, pre-added items or forced sign-ups | ✅ | — | Tech / Marketing | "Only N left" shows only real stock |
| P-02 | Review every new campaign or banner against the guidelines | ⬜ | P2 | Marketing | Add to the campaign checklist |

### 1.5 Policy text that doesn't match the site (fix before the payment-gateway review)
| ID | Item | Status | Priority | Owner | Notes |
|---|---|---|---|---|---|
| T-01 | COD fee in policy (₹79) vs site (₹60); COD now de-emphasised | ⬜ | P1 | Legal | Update Shipping and Terms pages |
| T-02 | Terms mention **reviews and blog**, which don't exist | ⬜ | P1 | Legal | Remove until built (see S-01) |
| T-03 | 90-day photo deletion promised (see D-04) | ⬜ | P1 | Legal / Tech | |

### 1.6 Tax, payments, IP
| ID | Item | Status | Priority | Owner | Notes |
|---|---|---|---|---|---|
| F-01 | GST invoices (CGST+SGST / IGST), sequential numbering | ✅ | — | Finance | |
| F-02 | CA to confirm HSN code and GST rate per category | ⬜ | P1 | Finance | Default 3926 @ 18% today |
| F-03 | Card data never stored by us (gateway tokenisation) | ✅ | — | Tech | Razorpay |
| F-04 | Razorpay live mode: KYC, live keys, webhook secret | ⬜ | P1 | Finance / Tech | Test mode today |
| F-05 | Check licences of 3D models (many are non-commercial) | ⬜ | P1 | Legal / Design | Before listing any downloaded design |
| F-06 | Rewrite product descriptions copied from model sites | ⬜ | P1 | Content | Removes designer text like "share a Make" |

---

## 2. Customer account
| ID | Item | Status | Priority | Owner | Notes |
|---|---|---|---|---|---|
| A-01 | Edit name; add email / mobile with OTP | ✅ | — | Tech | |
| A-02 | Saved addresses: add, edit, delete, **set as default** | ✅ | — | Tech | Default shown first and preselected at checkout |
| A-03 | Profile menu in header; account area with side menu | ✅ | — | Tech | |
| A-04 | **GSTIN for business invoices** | ⬜ | P2 | Tech / Finance | Printed on invoice; enables B2B gifting |
| A-05 | Change email / mobile (verify new one by OTP) | ⬜ | P2 | Tech | |
| A-06 | Notification preferences (email / WhatsApp / SMS; marketing on/off) | ⬜ | P2 | Tech | Needed with D-07 |
| A-07 | Sign out of all devices | ⬜ | P3 | Tech | |
| A-08 | Delete account / download data | ⬜ | P1 | Tech | See D-02, D-03 |

## 3. Shopping experience
| ID | Item | Status | Priority | Owner | Notes |
|---|---|---|---|---|---|
| S-01 | **Reviews with photos** (verified buyers only, moderated) | ⬜ | P2 | Tech / Support | Biggest trust builder |
| S-02 | Sort and filter (price, newest, personalisable, in stock) | ⬜ | P2 | Tech | |
| S-03 | Search suggestions while typing | ⬜ | P3 | Tech | |
| S-04 | Size reference photo on every product (in hand / next to a coin) | ⬜ | P2 | Design / Content | 3D prints are hard to judge |
| S-05 | Share on WhatsApp + link preview images (OG tags) | ⬜ | P2 | Tech / Marketing | |
| S-06 | Fast pages, lazy loading, mobile layout | ✅ | — | Tech | |

## 4. After purchase
| ID | Item | Status | Priority | Owner | Notes |
|---|---|---|---|---|---|
| O-01 | Tracking timeline, GST invoice download, status emails | ✅ | — | Tech | |
| O-02 | **Customer cancellation** within the policy window (24 h / before production) | ⬜ | P1 | Tech | Policy promises it; only admin can cancel today |
| O-03 | **Return / replacement request** with photo upload | ⬜ | P1 | Tech / Support | Policy promises 48-hour damage claims |
| O-04 | Buy again | ⬜ | P3 | Tech | |
| O-05 | WhatsApp order updates | ⬜ | P2 | Tech | Needs a WhatsApp Business API provider |

## 5. Admin and operations
| ID | Item | Status | Priority | Owner | Notes |
|---|---|---|---|---|---|
| M-01 | Dashboard, Excel reports, stock table, Excel upload with review | ✅ | — | Tech | |
| M-02 | Shiprocket integration switched on (auto courier booking, tracking) | ⬜ | P1 | Operations / Tech | Manual shipping today |
| M-03 | Owner-editable settings (shipping fee, COD limit, banner, holiday mode) | ⬜ | P2 | Tech | No redeploy needed for business changes |
| M-04 | Staff roles (packer, support) with limited admin access | ⬜ | P3 | Tech | |
| M-05 | Production queue view (what to print today, by material/colour) | ⬜ | P2 | Tech / Production | |

## 6. Marketing and growth
| ID | Item | Status | Priority | Owner | Notes |
|---|---|---|---|---|---|
| G-01 | SEO: titles, descriptions, `sitemap.xml`, Google Search Console | 🟡 | P2 | Marketing / Tech | |
| G-02 | Google Business Profile + Instagram shop | ⬜ | P2 | Marketing | |
| G-03 | Coupon codes / first-order discount | ⬜ | P3 | Tech / Marketing | |
| G-04 | Analytics (privacy-friendly) for visits → orders | ⬜ | P2 | Marketing / Tech | |

## 7. Technology and infrastructure
| ID | Item | Status | Priority | Owner | Notes |
|---|---|---|---|---|---|
| I-01 | Database in Mumbai | ✅ | — | Tech | Switch Render to the Mumbai env group if not done |
| I-02 | Backend always-on, closer to India (Render Starter, Singapore) | ⬜ | P1 | Tech | Free plan sleeps |
| I-03 | **Automated daily database backups** | ⬜ | P1 | Tech | Supabase Free has no downloadable backups |
| I-04 | `api.vindhyanstudio.in` custom domain for the backend | ⬜ | P2 | Tech | Backend moves without rebuilding the site |
| I-05 | Rotate secrets that were ever shared in chat / screenshots | ⬜ | P1 | Tech | DB passwords, service keys, Razorpay test secret |
| I-06 | Uptime monitoring + error alerts | ⬜ | P2 | Tech | |
| I-07 | Branded email templates | ✅ | — | Tech | Login code, confirmed, shipped, delivered, cancelled |

---

## Done log
| Date | ID | Change |
|---|---|---|
| Nov 2026 | A-02 | "Set as default" for saved addresses |
| Nov 2026 | C-06 | Clear agreement line (Terms, Privacy, Returns) above the checkout button |
| Nov 2026 | A-03 | Account area with side menu; profile menu in header |
| Nov 2026 | I-01 | Data copied to the Mumbai database |
| Nov 2026 | I-07 | HTML email templates via Brevo |
| Oct 2026 | M-01 | Owner dashboard, reports, stock table, Excel upload |

> Compliance notes are a working checklist, not legal advice. Have a CA / lawyer review before scaling.
