# Go-live checklist

Everything, in order, to take Calyvora from the current free setup to a production environment with
a real customer on it — plus the optional demo environment. Tick top to bottom; nothing here needs
a code change, it is all Render / Neon / Hostinger dashboard work.

**The model:** one platform OWNER (you, the vendor) above unlimited companies, all on one instance
and one database. Each company is a tenant, isolated from the others by Postgres row-level security.
Adding a customer = creating a company under your owner account. No new instance per customer.

---

## Phase 0 — Prerequisites (secrets that must be set before anything else)

These live only in the Render dashboard (`sync: false` — never in the repo). Set them on the
**production** backend `calyvora-backend-prod` once it exists (Phase 1). The existing/demo backend
keeps its own values.

- [ ] **Platform-owner password.** `calyvora-backend-prod` → Environment → set
      `PLATFORM_OWNER_PASSWORD`, then restart. You log in as OWNER (`bharat28195@calyvora.in`) to
      create companies, and that account can read every customer — it must not be on the default.
- [ ] **JWT signing keys present:** `JWT_KID`, `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` on
      `calyvora-backend-prod`. Without them the app makes a throwaway key at each boot and every
      restart logs everyone out.
- [ ] **Email:** `RESEND_API_KEY` on `calyvora-backend-prod` (production sends real mail via Resend
      on 443; Render blocks SMTP, so Resend is the transport).

(These overlap with Phase 1's per-service steps — set them whenever you create `calyvora-backend-prod`.)

---

## Phase 1 — Build the production services (new, alongside the existing ones)

Option A: production is a NEW pair of services; the existing services keep running untouched and
become the demo. Nothing you rely on breaks while you build and verify prod.

- [ ] **Point the Render Blueprint at the repo**, reading from the **`main`** branch (`main` is the
      production release branch — full product + the two-environment `render.yaml`).
- [ ] **Blueprints → Sync.** This **creates** the new production services
      `calyvora-backend-prod` (Starter, `main`, `prod`) and `calyvora-frontend-prod` (`main`), and
      leaves your existing `calyvora-backend`/`calyvora-frontend` as the demo. It does **not** move
      any traffic — the new services come up on their own `onrender.com` urls.
- [ ] **Production database.** Create a **fresh Neon database** for production so it starts clean.
      Set `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` on **`calyvora-backend-prod`**.
      - Use Neon's **direct** hostname, **not** the `-pooler` one.
      - Region: **Singapore / ap-southeast-1**.
- [ ] **Production signing keys + email.** On `calyvora-backend-prod` set its own `JWT_KID` /
      `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` and `RESEND_API_KEY`.
- [ ] **Verify the database role enforces isolation.** In Neon's SQL editor:
      `select current_user, rolsuper, rolbypassrls from pg_roles where rolname = current_user;`
      Both `rolsuper` and `rolbypassrls` must be **false** — otherwise row-level security is silently
      ignored and tenants could see each other.
- [ ] **Verify prod on its onrender.com url (before touching any domain):**
      - `https://calyvora-backend-prod.onrender.com/actuator/health` → `200` `{"status":"UP"}`
      - `https://calyvora-backend-prod.onrender.com/api/v1/dev/mailbox` → **`404`** (proof the `prod`
        profile is active — dev endpoints do not exist).
      - `https://calyvora-frontend-prod.onrender.com` → login page loads.

---

## Phase 1b — Point orbit.calyvora.in at production (the cutover)

Do this only once prod is verified above. It moves your brand URL from the demo (old) frontend to
the new prod frontend. Reversible — if anything looks wrong, move the domain back.

- [ ] **Render — add the domain to prod:** `calyvora-frontend-prod` → Settings → Custom Domains →
      add `orbit.calyvora.in`. Render shows the DNS target (e.g. `calyvora-frontend-prod.onrender.com`).
- [ ] **Render — remove the domain from the old frontend:** `calyvora-frontend` → Custom Domains →
      remove `orbit.calyvora.in` (a domain can only live on one service).
- [ ] **Hostinger — repoint DNS:** edit the `orbit` **CNAME** for `calyvora.in` to point at
      `calyvora-frontend-prod.onrender.com` (the target Render showed). Save.
- [ ] **Wait for DNS + TLS** (usually minutes, up to ~an hour). Render marks the domain "Verified"
      and issues the certificate automatically.
- [ ] **Confirm:** `https://orbit.calyvora.in` now loads the production site, and
      `https://orbit.calyvora.in/api/v1/dev/mailbox` → **`404`** (you're on prod).

Your old `orbit` CNAME currently points at `calyvora-frontend.onrender.com`; you are changing it to
`calyvora-frontend-prod.onrender.com`. That is the whole DNS change.

---

## Phase 2 — Keep the free parts warm

- [ ] In GitHub → **Actions**, enable workflows if prompted, then run **Keep-alive** once
      (Run workflow) and confirm it goes green. It pings every 10 minutes afterwards.
      (Once the backend is on Starter it never sleeps anyway; this mainly protects the free frontend
      and the demo.)

---

## Phase 3 — Demo on its own domain (optional — production works without it)

In Option A the demo is your EXISTING services (`calyvora-backend` / `calyvora-frontend`), which keep
their current database (already seeded) and the `staging` profile. The only thing to do is give them
the `demo.calyvora.in` address — they already run.

- [ ] **Hostinger — add a `demo` CNAME** for `calyvora.in` pointing at
      `calyvora-frontend.onrender.com` (the existing frontend).
- [ ] **Render — add the domain:** `calyvora-frontend` → Custom Domains → add `demo.calyvora.in`.
      (After Phase 1b removed `orbit` from this service, `demo.calyvora.in` is its address.)
- [ ] **Leave `RESEND_API_KEY` unset** on the existing `calyvora-backend` so the demo captures
      verification/invite links in the in-app mailbox (`demo.calyvora.in/dev/mailbox`) instead of
      emailing them — safe, since nothing is delivered, and it lets a demo finish signup flows.
- [ ] Confirm `https://demo.calyvora.in` loads and the Northwind demo company is there (it already
      is, in the existing database). Re-seed via the "Explore the demo" flow if you want it fresh.

---

## Phase 4 — Onboard the trial customer (your 15-person prospect)

All in the platform console, logged in as OWNER, on **production**.

- [ ] **Create the company.** Platform console → Create company:
      - Company name: the customer's company
      - Admin: their main contact's first/last name + email + a temporary password you choose
      - **Seats: 15** (or 18 for headroom — you can change it anytime)
      - **Months: 1** (the trial window)
      - Currency: INR (or USD if they're international)
      The company comes out **ACTIVE** immediately, with an end date one month out — that *is* the
      free trial: full access now, auto-locks when the month ends unless you renew. You simply don't
      invoice them for the trial.
- [ ] (Optional) **Tune their modules.** Company → features: turn on what you want them to trial
      (e.g. People, Attendance, Leave, Payroll, Expenses) and leave niche ones off.
- [ ] **Send the customer their login:** `https://orbit.calyvora.in` + their admin email + the temp
      password. Tell them to change the password on first sign-in.
- [ ] **They add their team.** As ADMIN, the customer invites up to 15 people from inside the app
      (invited users become MEMBER, or MANAGER if they lead a team). You don't do this part.
- [ ] **Confirm isolation (do this once, for your own confidence):** log in as the customer's admin
      and check they see only their own company — no other customer, no demo data.

**The role to give the customer is `ADMIN` of their own company — never `OWNER`** (that is your
vendor account and reads every customer) and never `AGENCY_OWNER` (that is a reseller).

---

## Phase 5 — When they sign the contract

- [ ] Platform console → the company → **renew / extend the end date** (and set the price/seats to
      the agreed terms). Same company, same data — the trial becomes the paid account, nothing
      migrates, nobody re-enters anything.

---

## Adding the next customer (repeat forever)

Just Phase 4 again — one more company under your one owner account. No new instance, no new
deployment, no extra Render cost. Bump the instance size or database only when total usage across
all companies grows large, never per customer.

---

## Rollback / safety notes

- The `prod` profile removes **all** dev/seed/mailbox endpoints — there is nothing public to abuse on
  production. (On the demo they exist by design, which is why the demo has its own throwaway DB.)
- Real email failures never roll back a signup (by design), so always confirm a real send worked:
  `POST /api/v1/dev/test-email?to=you@…` on a non-prod deployment, or just watch the first real
  invite arrive.
- Keep `main` as production and `product/hr-platform` as where you build; release by merging the
  branch into `main`.
