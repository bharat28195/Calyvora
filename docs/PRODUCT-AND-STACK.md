# Calyvora — Product & Tech Stack

What the product does, what its parts are built from, and how it is run. Written 2026-10-01,
after an end-to-end functional test of the live deployment as a company owner.

---

## 1. What it is

A multi-tenant HR + workplace platform for Indian companies, sold by a vendor (Calyvora) to
companies, optionally through agencies. One database serves every tenant; a company sees only its
own data, enforced in the database itself (Postgres Row-Level Security), not only in code.

**Three kinds of account:**
- **Platform owner (vendor):** runs the whole platform, sees every company, sets pricing and plans.
- **Agency:** resells to companies and sees summaries of the companies it owns (never their pay data).
- **Company:** the customer. Roles inside a company: OWNER, ADMIN, HR, MANAGER, MEMBER.

Access inside a company is granted by the **reporting tree**, not the job title: whoever has people
reporting to them leads a team, whatever their role is called (PD-32).

---

## 2. Functionality — what works

Verified against the live deployment on 2026-10-01: **30 of 30 modules working** through real
create / update / delete, as the company owner, over the network. Every screen renders for every
role, and role boundaries are enforced (a manager sees their team, not the company; no pay data
leaks to anyone who should not see it).

### People & core HR
- **People directory** — employees, profiles, departments, designations (the company's own ladder).
- **Org chart** — the reporting tree; visibility flows from it.
- **Attendance** — check-in / check-out, monthly view, percentage counted from the 1st of the month,
  in the employee's own timezone (not UTC).
- **Leave** — request → approve → balance, with per-company leave **policies** (vacation, sick,
  personal, unpaid, comp-off) rather than a hard-coded allowance.
- **Holidays** — company calendar and upcoming holidays.
- **Regularizations & comp-off** — fix a missed punch, bank a worked holiday.
- **Shifts** — shift patterns and the roster.
- **Onboarding & exits** — onboarding checklist, exit checklist, full employee lifecycle.
- **Letters & letterpad** — company stationery, offer/hire letters, document templates with merge
  fields (PD-20).

### Money
- **Payroll** — salary structure, payroll runs, payslips, and the **bank file** for the last mile
  (problems flagged before download, not by the bank).
- **Statutory payroll (PF)** — provident fund with the ₹15,000 wage ceiling, on/off per company.
- **Income tax (TDS)** — Indian FY 2026-27 old & new regime, deduction declarations, a computation
  page and projected monthly tax, and TDS applied on payslips. Off by default per company.
- **Expenses** — claim → approve → reimburse.
- **Compensation** — pay records, CTC, hikes (visible only to those allowed).

### Work & knowledge
- **Work** — projects, tasks, sprints, boards, tickets.
- **Knowledge base** — spaces, pages, full-text search.
- **Helpdesk** — internal tickets and replies.
- **Company feed** — announcements, posts, comments, reactions, pinning.

### Talent
- **Recruitment** — job openings, candidate pipeline, stage moves, offer letters.
- **Performance** — review cycles, self and manager reviews, goals, hikes.

### Platform, agency & commerce
- **Plans & features** — each module is a feature flag; plans decide what a company gets.
- **Subscription & billing** — seat-based, per-currency price lists, subscription lifecycle.
- **Agency console** — an agency sees its companies and their spend (summaries only).
- **Platform console** — the vendor's view of every company, agency, trial request and price list.
- **Trial requests** — public "request a trial" (self-serve signup is deliberately closed, PD-21).

### Cross-cutting
- **AI assistant** — answers questions from the company's own data.
- **Insights / analytics** — headcount, attrition, cost.
- **Notifications**, **global search**, **dashboards** (per role).
- **Localization** — currency and timezone per company and per employee.

### Feature catalogue (toggleable modules)
`PAYROLL, STATUTORY_PAYROLL, INCOME_TAX, RECRUITMENT, PERFORMANCE, WORK, KNOWLEDGE, HELPDESK,
EXPENSES, SHIFTS, CLIENTS, FEED, ASSISTANT, ANALYTICS` — each on/off per company via its plan.

---

## 3. Tech stack

### Backend
- **Java 21**, **Spring Boot 3.3.4** (web, security, data-jpa, validation, actuator, mail).
- **Hibernate / JPA** over **PostgreSQL**, schema managed by **Flyway** (57 migrations).
- **Multi-tenancy:** Postgres **Row-Level Security** — the tenant id is bound to a database session
  variable and every table's policy filters on it, so isolation holds even if application code has a
  bug. Bindings can be made transaction-scoped for a future connection pooler (4.2).
- **Auth:** JWT with **RS256** asymmetric signing + a **JWKS** endpoint and key rotation; refresh
  tokens are rotating and single-use, with theft detection (reuse revokes the family).
- **Email:** pluggable — **Resend** (HTTPS API, survives hosts that block SMTP) or SMTP, with a
  console fallback for local dev.
- **API docs:** OpenAPI / Swagger (springdoc).
- **Tests:** JUnit + an embedded Postgres, run under a non-superuser role so RLS is actually
  exercised; ~500+ tests.

### Frontend
- **Next.js 14** (App Router) + **React 18** + **TypeScript 5**.
- **Tailwind CSS** for styling; **lucide-react** icons; **zod** for validation.
- **Two backends behind one API layer:** a faithful **mock backend** (for local dev, demos and e2e,
  no server needed) and the **live** API, switched by `NEXT_PUBLIC_API_MODE`.
- Access token in memory only; refresh via an httpOnly cookie; a middleware guards app routes.
- **Tests:** Vitest (unit) + **Playwright** (e2e, opens every screen against the mock).

### Delivery
- **Docker** images for both services.
- **CI:** GitHub Actions (build, unit, e2e).
- Branch `product/hr-platform` is what production deploys from.

---

## 4. How it is hosted (current)

| Piece | Where | Plan | Cost |
|---|---|---|---|
| Backend (Spring Boot) | Render (Docker web service) | Free instance | $0 |
| Frontend (Next.js) | Render (Docker web service) | Free instance | $0 |
| Database (Postgres) | **Neon** (not Render's — Render's free DB is deleted after 30 days) | Free | $0 |
| Email | Resend | Free tier | $0 |
| CDN / TLS / DNS | Cloudflare (in front) | Free | $0 |

Workspace is Render **Hobby ($0)**. **Total ≈ $0/mo today.**

**Known trade-offs at this tier:**
- Free instances **spin down after 15 min idle**; the backend's cold start is **~4½ minutes** (JVM +
  Spring boot). The frontend answers instantly, so a visitor sees the login page then a dead submit
  button — the worst possible first impression.
- The live deployment currently runs the **`staging` Spring profile**, which exposes developer-only
  endpoints publicly. The account-takeover vector (public mailbox) is closed (PD-48); the public
  **seed endpoints** are still open and are the likely source of stray demo payroll rows. Closing
  them means running the `prod` profile, which requires splitting the demo from production.

---

## 5. Verdict & what to add before selling

**Core product: yes, sellable.** The functionality is broad and it works. The gaps are operational,
not functional.

**Before the first paying customer:**
1. Kill the cold start (free UptimeRobot keep-alive now; $7 Render Starter instance at first
   customer).
2. Run the `prod` profile — split the demo from production so the `/dev` surface closes.
3. Set the platform-owner password (still on the built-in default).
4. Fix `/billing` rendering blank on a 403 for non-owners (minor; unhandled empty state).

**Premium/polish worth having:**
- Automated Postgres backups + a tested restore (Neon has PITR on paid tiers).
- Uptime & error monitoring (UptimeRobot free; Sentry free tier).
- A public, read-only pricing endpoint so the marketing page can show live, geo-aware prices.
- Data-residency clarity for Indian customers (where the DB physically sits).
