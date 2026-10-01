# Go-live checklist

Everything, in order, to take Calyvora from the current free setup to a production environment with
a real customer on it — plus the optional demo environment. Tick top to bottom; nothing here needs
a code change, it is all Render / Neon / Hostinger dashboard work.

**The model:** one platform OWNER (you, the vendor) above unlimited companies, all on one instance
and one database. Each company is a tenant, isolated from the others by Postgres row-level security.
Adding a customer = creating a company under your owner account. No new instance per customer.

---

## Phase 0 — Prerequisites (secrets that must be set before anything else)

These live only in the Render dashboard (`sync: false` — never in the repo).

- [ ] **Platform-owner password.** Backend service → Environment → set `PLATFORM_OWNER_PASSWORD`,
      then restart. You log in as OWNER (`bharat28195@calyvora.in`) to create companies, and that
      account can read every customer — it must not be on the built-in default.
- [ ] **JWT signing keys present:** `JWT_KID`, `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` on the backend.
      Without them the app makes a throwaway key at each boot and every restart logs everyone out.
- [ ] **Email:** `RESEND_API_KEY` set on the backend (production sends real mail via Resend on 443;
      Render blocks SMTP, so Resend is the transport).

---

## Phase 1 — Production cutover (this is "deploy main to prod")

- [ ] **Point the Render Blueprint at the repo**, reading from the **`main`** branch (`main` is now
      the production release branch — it has the full product and the two-environment `render.yaml`).
- [ ] **Blueprints → Sync.** This:
      - repoints `calyvora-backend` to `main`, the `prod` profile, and the **Starter** plan ($7 — your paid instance),
      - repoints `calyvora-frontend` to `main`,
      - creates the two new demo services (they stay unprovisioned until Phase 3 — harmless).
- [ ] **Production database.** Create a **fresh Neon database** for production so it starts clean
      (no demo/scale leftovers). Set `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` on `calyvora-backend`.
      - Use Neon's **direct** hostname, **not** the `-pooler` one (the app binds its tenant on a
        session-scoped setting; a transaction pooler can split that across connections).
      - Pick the Neon region matching the service (**Singapore / ap-southeast-1**).
- [ ] **Verify the database role enforces isolation.** In Neon's SQL editor:
      `select current_user, rolsuper, rolbypassrls from pg_roles where rolname = current_user;`
      Both `rolsuper` and `rolbypassrls` must be **false** — otherwise row-level security is silently
      ignored and tenants could see each other.
- [ ] **Confirm the backend is up and really in prod mode:**
      - `https://calyvora-backend.onrender.com/actuator/health` → `200` `{"status":"UP"}`
      - `https://calyvora-backend.onrender.com/api/v1/dev/mailbox` → **`404`** (under `prod` the dev
        endpoints do not exist — a 404 here is proof the prod profile is active).
- [ ] **Confirm the site loads:** `https://orbit.calyvora.in` → login page.

---

## Phase 2 — Keep the free parts warm

- [ ] In GitHub → **Actions**, enable workflows if prompted, then run **Keep-alive** once
      (Run workflow) and confirm it goes green. It pings every 10 minutes afterwards.
      (Once the backend is on Starter it never sleeps anyway; this mainly protects the free frontend
      and the demo.)

---

## Phase 3 — Demo environment (optional — production works without it)

Only needed if you want a public, self-serve demo separate from real customers.

- [ ] **Second Neon database** for the demo (or reuse the *old* production database — it already has
      demo data seeded). Never point the demo at the production database.
- [ ] **DNS:** at Hostinger add a **CNAME**: `demo` → `calyvora-frontend-demo.onrender.com`.
- [ ] On `calyvora-backend-demo`, set `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` (the demo DB) and its
      own `JWT_KID` / `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY`.
      - **Leave `RESEND_API_KEY` unset** on the demo. With no provider, verification/invite links are
        captured in the in-app dev mailbox (`demo.calyvora.in/dev/mailbox`) instead of being emailed —
        which is how a demo completes signup flows without a real inbox, and is safe because nothing
        is actually delivered.
- [ ] Seed the demo: open `demo.calyvora.in` and use the "Explore the demo" flow, or hit the demo
      backend's seed. Verify the Northwind demo company appears.

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
