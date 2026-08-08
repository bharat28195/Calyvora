# FOUNDER.md — Calyvora Founder Journal & Decision Log

> **Purpose:** The company's living journal. It records not just *what* we built but *why* we
> built it, so anyone joining in six months or two years understands the reasoning behind every
> major decision. Maintained continuously by the founder and the AI co-founder. When this
> journal and the code disagree, the journal explains the intent; the [/docs constitution](docs/README.md)
> holds the binding architecture.
>
> **Format conventions:** newest entries first within each log. Every decision carries a date
> (ISO `YYYY-MM-DD`) and a stable ID (`PD-##` product, `ADR-##` architecture) so we can
> cross-reference. Dates are absolute, never "last week."

**Last updated:** 2026-07-22 · **Product: Orbit (by Calyvora)** · **Branch:** `feature/orbit` · **Stage:** Phase-1 trio ✅ · Work OS depth ✅ · Foundation hardening (RLS + RS256) ✅ · Demo suite (seed/dashboard/⌘K search/AI assistant) ✅ · Theming ✅ · **Founder-feedback buckets A + B + C.1 ✅** (in progress, see [docs/Founder-Feedback-Backlog.md](docs/Founder-Feedback-Backlog.md)) — 74 backend tests, verified live

---

## 1. Company Vision

### Mission
Replace the 20–40 disconnected tools a company runs (HR, work, knowledge, CRM, finance,
service, meetings, analytics, automation) with **one AI-native Enterprise Operating System** —
one platform, one identity, one data fabric, one AI layer — where every application works
independently yet integrates natively.

### Long-term vision (10 years)
"Running your company on Calyvora" should mean what "running on the cloud" means today: the
default, assumed substrate. The end state is **an organization that thinks** — every system
shares context, every action is auditable, and a governed AI layer with a complete view of the
business acts as an always-on operational partner. Third parties build vertical solutions on
top the way apps are built on iOS. See [docs/01](docs/01-executive-vision.md).

### Product philosophy
Build **fewer things, more deeply, on an uncompromising Foundation, with AI and security woven
in from line one, validated by real customers, enforced by machines, and expanded only under
proven pull.** The 15 engineering principles (API-first, AI-first, multi-tenant, Zero-Trust,
event-driven, composable, boring-tech-by-default, etc.) live in [docs/02](docs/02-product-philosophy.md),
each with a *why* and an enforcement mechanism, and a tie-breaker priority order:
**Security & Isolation → Correctness → UX/DX → Scalability → Cost/Velocity.**

### Core values
1. **Own it.** Think like a founding partner, not a feature factory.
2. **No shortcuts that mortgage the platform.** Speed never buys down the Foundation.
3. **Customer value over feature count.** We win love, referrals, and revenue — not a spec sheet.
4. **Enforce, don't exhort.** A rule enforced by a machine is a rule; by hope, a wish.
5. **Honesty in the record.** Decisions are documented with their trade-offs, superseded not deleted.
6. **Integration is the multiplier, not the substitute** — each app must also be individually excellent.

### Success metrics (north stars)
- **Primary:** number of customers running **≥3 connected OS-apps** on one login (proves the
  platform thesis, not just single-app adoption).
- **Product:** universal-assistant weekly active usage; cross-app action rate (an action in
  app A triggered from app B or the assistant).
- **Business:** net revenue retention (land-and-expand is our moat), logo retention, NPS.
- See detailed SaaS metrics in [§9](#9-startup-metrics) (tracked once live).

---

## 2. Founder Notes

> Running log of the founder's and co-founder's thinking — ideas, observed problems, competitor
> inspiration, opportunities, and open questions. Newest first.

**2026-07-22 (The product gets a name — "Orbit" — and the founder's feedback becomes the roadmap)**
- **Named the product: Orbit; Calyvora is the parent company.** The founder decided the OS itself needs
  its own brand distinct from the company. We shortlisted Orbit / Nexus / Cortex / Meridian and the
  founder chose **Orbit** — everything revolves around one platform. Wired it as a one-line switch
  (`frontend/src/lib/brand.ts`) so the whole UI reads "Orbit by Calyvora". This also unlocks the
  packaging idea below (a named product a client can buy in whole or in part).
- **Turned 8 pages of handwritten notes into a tracked, living backlog.** The founder handed over
  detailed product feedback; rather than cherry-pick, we transcribed every item into
  [docs/Founder-Feedback-Backlog.md](docs/Founder-Feedback-Backlog.md) with per-item status, so no idea
  is lost and any session can resume. Sequenced into buckets A (quick wins), B (role dashboards +
  attendance), C (People OS depth), D (new modules). **Discipline over speed: capture everything, ship
  in reviewable slices.**
- **Shipped A + B + C.1 already, each tested and live.** Fixed the real bug (members dropdown → a
  searchable picker that scales to ~1k), moved navigation to a left sidebar, made attendance *derived
  from leave* first (a phased, low-regret call the founder approved) before committing to a full daily
  attendance model, and built the most-emphasized item — **salary, yearly hikes, and payslips** — as
  Owner/Admin-only compensation with real hike-% history.
- **A phasing decision worth remembering (attendance).** The founder asked for present/on-leave and a
  leave calendar. Rather than model daily attendance up front, we derive it from approved leave now and
  deferred the full daily-attendance record to Bucket C — the founder explicitly chose "both, phased."

**2026-07-21 (Foundation debt cleared — RLS + RS256 before more features)**
- **The database now enforces tenant isolation itself (SD-2).** Until today, one tenant not seeing
  another's data rested entirely on every query remembering its `company_id` filter — one forgotten
  `where` clause, or one injection, and it's a breach. We added Postgres Row-Level Security on all 11
  tenant-owned tables (V12): each request binds its tenant to the connection as a session GUC, and the
  DB refuses to read or write any other tenant's rows. It's *defense in depth* — the app-layer checks
  stay; RLS is the backstop. Deny-by-default too: a connection with no bound tenant sees nothing.
- **Named the sharp edge instead of hiding it.** RLS is bypassed by Postgres superusers, and our
  embedded dev DB connects as one — so I made the test drop to a NOSUPERUSER role via `SET ROLE` (which
  IS subject to RLS) to prove the policies actually bite, and wrote down loudly that the production DB
  role must be NOSUPERUSER. Better an honest boundary in the record than a green test that proves nothing.

- **Stopped the RS256 follow-up from slipping again.** Access tokens were still HS256 (a shared secret)
  since Sprint 1, deferred twice. Before building any more app depth we cut over to **RS256 asymmetric
  signing**: signers hold the private key, verifiers hold only the public key — there is no longer a
  shared secret whose leak would let a verifier forge tokens. Fulfils the deferred half of SD-5.
- **Built rotation in from day one, not as a later retrofit.** Keys carry a `kid`; one key is active for
  signing while every configured key stays trusted for verification, so we can rotate with zero downtime
  (publish new → flip active → retire old once its last token expires). Public keys are discoverable at
  `/.well-known/jwks.json` (RFC 7517) so the frontend or a future gateway verifies tokens without us
  hand-delivering keys. Decisions logged as SD-5a/SD-23/SD-24.
- **Kept dev zero-config without shipping a secret.** No keys configured → an ephemeral keypair is
  generated at boot with a loud warning, rather than committing a dev private key to the repo. 7 new
  tests (60 total), full suite green. Next foundation item: Postgres RLS (SD-2).

**2026-07-20 (Work OS deepened — first real "depth" investment after the trio)**
- **From a board to a workspace.** Work OS now has a left-pane workspace — Board · Backlog · Sprints ·
  Tickets — with real agile sprints (create → start → complete, ≤1 active per project, unfinished work
  carries back to the backlog) and a lightweight support-tickets type. `com.calyvora.work` grew Flyway
  V10/V11; 5 new integration tests (58 total), verified live: a "Sprint 1" running with two tasks on the
  board, one task left in the backlog, and tickets PLT-T1/T2 with a People-employee assignee.
- **Restraint held where it mattered (SD-22b).** The founder asked for support tickets *inside* Work. I
  built a thin version but logged it as deliberate debt: tickets' true system of record is **Service OS**
  (Phase 2), with customers/SLAs Work doesn't model. We took the shortcut knowingly, in writing, rather
  than quietly letting Work become a CRM — protecting the "one system of record per entity" principle.
- **The cross-app graph keeps paying off.** Both task assignees and ticket assignees are People
  employees — the same org graph, now feeding a third surface. No new glue.

**2026-07-20 (Knowledge OS shipped — the Phase-1 trio is complete)**
- **Third full app on the platform; the depth-first bet (PD-02) is delivered.** Knowledge OS — spaces,
  a Markdown page tree with drafts/publish, tenant-wide search, and "my pages" — built on the foundation:
  `com.calyvora.knowledge`, Flyway V8/V9, 6 integration tests (48 total, each with a cross-tenant check).
  **People / Work / Knowledge now all run on one login, one identity, one data fabric.**
- **The graph closed into a triangle (PD-06).** A page's **author is a People `Employee`** and a page can
  **link a Work `Task`** — so a single doc ties a *person* to a *task* to *knowledge* with zero glue.
  Verified live end-to-end: a "Deploy runbook" page came back authored by "Milton Waddams" and linked to
  `PLT-1`, and full-text search found it by body. This is the "integrated by construction" moat in one
  screen — the thing no bundle of point tools can copy.
- **Leverage compounding again.** Knowledge OS reused the Sprint-1 spine *and read both prior apps*
  (People for authorship, Work for the task link) without touching either. I added exactly one shared
  seam — `EmployeeService.ensureEmployeeId` — so authorship provisions a People profile without Knowledge
  knowing People's internals. Each app keeps making the next cheaper and the platform more valuable.
- **Restraint holds.** With the trio done, the pull now is *depth* (versioning, richer editor, comments)
  and the deferred **foundation debt — Postgres RLS (SD-2) and RS256 (SD-5) — which must not keep slipping.**
  Deliberately *not* starting a fourth app yet.

**2026-07-10 (Work OS shipped — the cross-app thesis is real)**
- **Second full app on the platform.** Work OS (projects, Kanban task board, My Work) built on the
  foundation: `com.calyvora.work`, Flyway V6/V7, 5 integration tests (42 total, each with a
  cross-tenant check). Verified live — a `Platform` project with tasks moving across To do → In
  progress → Done, priorities, and per-project refs (PLT-1…).
- **The moat, demonstrated (PD-05):** a Work OS task's **assignee is a People OS `Employee`** — the two
  apps share one org graph with zero glue. "Bob Stone" assigned in Work shows up in his People profile
  and in *My Work*. This is "integrated by construction," not an integration project — exactly the thing
  no single-app competitor can copy cheaply.
- **Leverage compounding:** People OS reused the Sprint-1 spine; Work OS reused the spine *and* read
  People OS. Each app makes the next cheaper and more valuable. Restraint still matters — three Phase-1
  apps, then depth — but the platform bet is paying off on schedule.

**2026-07-10 (People OS shipped — first full app complete)**
- **People OS is built, tested, and verified live.** All five vertical slices on the foundation:
  employee directory & profiles (P1), departments + org chart (P2), onboarding checklists (P3),
  time-off with approvals + balances (P4), and self-service (P5). **18 People OS integration tests**
  (37 total) pass on real embedded Postgres — every slice includes an adversarial cross-tenant check.
  Verified end-to-end in the browser against the live backend: directory, org tree (CTO → Engineer),
  onboarding checklist, and a vacation request approved down to a 20-day remaining balance.
- **Design note:** `Employee` is a 1:1 auto-provisioned extension of the platform `User`, so identity
  (auth) and HR data stay cleanly separated and every company member appears in the directory without
  the auth flow knowing anything about People OS. This is the org graph every future app will read (PD-04).
- **Proof the platform thesis works:** People OS reused the Sprint-1 spine wholesale — tenancy, JWT,
  RBAC, error envelope, TenantContext — and added a full business module without touching the
  foundation. That "integrated by construction" leverage is exactly the moat we're building toward.

**2026-07-10 (Sprint 1 complete — foundation shipped & verified)**
- **Backend built and proven.** JDK 21 arrived on the dev machine; I bootstrapped Maven (no global
  install) and implemented the entire Sprint-1 backend on the foundation: auth/registration/verification,
  JWT + rotating refresh with **reuse detection**, dashboard, company/settings, and the full invitation
  lifecycle. **19 tests pass on a real embedded Postgres** (no Docker) — including the adversarial
  **cross-tenant isolation** suite that is the Sprint-1 merge gate (SD-2).
- **Whole app runs locally, verified in the browser.** With the `embedded` profile the backend boots a
  throwaway Postgres and prints email links to the console; the frontend (`API_MODE=live`) drove the full
  golden path against the real API — register → verify → login → dashboard — with a secure httpOnly
  refresh cookie. Sprint 1's Definition of Done is met end-to-end.
- **New implementation decisions:** SD-10 (Zonky embedded Postgres, no-Docker), SD-11 (reuse revocation
  in a REQUIRES_NEW tx so a stolen family is actually burned before the 401), SD-12 (console email +
  resilient mail). Logged in [DECISIONS.md](DECISIONS.md).
- **Working agreement:** founder wants the **full app built locally first, no PRs** for now. Resume state
  lives in [CONTEXT.md](CONTEXT.md).
- **Next:** Sprint 2 = **People OS**, our beachhead app (PD-02) — the first real business module on the
  foundation. Plan: [docs/Sprint2-PeopleOS.md](docs/Sprint2-PeopleOS.md).

**2026-07-09 (Sprint 1 build begins)**
- **Approved to build.** Founder gave the go-ahead to start Sprint 1. Order followed per plan §15:
  Feature 0 scaffolding + foundation → Feature 1 landing.
- **Build tool: Gradle → Maven (SD-9, supersedes the Gradle half of SD-7).** Founder directive: the
  backend is a **Maven** Java project. Reason: team/Spring-Boot tooling familiarity. Trade-off: none
  material for our needs; Spring Boot's Maven support is first-class. Logged in [DECISIONS.md](DECISIONS.md).
- **Toolchain reality (co-founder flag):** this workstation has **Node** but **no JDK 21, no Maven,
  no Docker**. Consequence: the Next.js frontend is built *and verified locally*; the Spring Boot
  backend + Docker Compose are written to spec but **cannot be compiled/run here** — the verification
  gate for backend code is **CI (GitHub Actions)** or a local `mvn`/Docker install. I will not claim a
  backend "works" that I couldn't execute. Recommend installing **Temurin JDK 21 + Docker Desktop**
  to unlock local backend runs and the Testcontainers integration suite.
- **Shipped this session:** monorepo (`/backend` Maven+Spring Boot, `/frontend` Next.js, `/infra`
  compose, CI); backend foundation (TenantContext, error envelope, stateless-JWT SecurityConfig,
  Flyway V1 baseline, OpenAPI, correlation-id); Feature 1 landing page (verified rendering, no console
  errors). The pre-existing static `web/` marketing site is kept separate (deploy target for calyvora.in).
- **Decision — frontend-first (given the toolchain gap):** founder chose to build and verify the
  *entire* Sprint-1 UI now, deferring the Java backend until a JDK/Docker toolchain exists. To make
  this real (not mocked screenshots), the frontend runs against an **in-browser mock backend**
  (`frontend/src/lib/mock/backend.ts`) that mirrors the §7 API contract; `lib/api.ts` flips to the
  real backend with `NEXT_PUBLIC_API_MODE=live`. Trade-off: backend correctness/tenant-isolation
  tests are still outstanding — the security-critical half. **Do not treat Sprint 1 as done until the
  Spring Boot backend is implemented and its adversarial cross-tenant + auth tests pass.**
- **Frontend milestone reached:** all Sprint-1 screens built and **verified end-to-end in the browser**
  — the golden path register → verify-email → login → dashboard → invite → accept → (new active member),
  plus RBAC nav gating, logout, and company settings save. This is the demoable week-1 milestone,
  delivered on the UI side.

**2026-07-05 (Sprint 1 kickoff)**
- **Scope honesty (co-founder):** the "Sprint 1" feature list is really the *platform foundation* and
  is ~2–3 weeks of production-quality work, not 5 days. I recommend measuring success by a **demoable
  milestone at Feature 6** (register→verify→login→protected dashboard) in week 1, then invite/settings.
  Flagged so we don't mistake a foundation for a week-sized task or cut corners to fit a calendar.
- **Deliberate debt (co-founder):** Sprint 1 enforces tenant isolation at the app layer (`TenantContext`)
  and **defers Postgres RLS to Sprint 2**. This is a conscious, logged trade-off (SD-2) — acceptable for
  one sprint *only because* adversarial cross-tenant tests are a merge gate. RLS is the real backstop and
  must not slip past Sprint 2.
- **Still-open (unchanged, now urgent):** pricing model and the first design partner — both should be
  progressing in parallel with the build.

**2026-07-05**
- **New idea (co-founder):** the **transactional outbox + Debezium CDC** on the JVM isn't just
  plumbing — it's the mechanism that lets *every* future app subscribe to *every* other app's
  facts with zero coupling. This is the technical heart of "integrated by construction." Worth
  protecting fiercely; it's easy to erode with a "just this once" direct DB read.
- **Problem observed:** the biggest *likelihood* risk isn't technical — it's **spreading thin**.
  The vision lists 14+ apps; the temptation to start five at once is strong and fatal. Guardrail:
  the Phase-1 scope is exactly three apps (People, Work, Knowledge) and a written "NOT building
  yet" list.
- **Competitor inspiration:** Notion (delightful, composable UX), Linear (speed + opinionated
  defaults), Ramp/Rippling (land-and-expand across functions), ServiceNow (platform/workflow
  depth). We want Linear's craft with Rippling's cross-function expansion and a genuinely
  AI-native core none of them have.
- **Future opportunity:** the org **knowledge graph** could become a standalone durable asset —
  the institutional memory of a company, queryable and actionable. Possibly the deepest long-term moat.
- **Open questions (need answers):**
  1. Beachhead segment precision — digital-native SMB vs lower mid-market first? (Leaning SMB.)
  2. Which 3–5 **design partners** do we recruit *before* building Phase 1?
  3. Meeting OS video: build vs. partner in Phase 2 — confirm partner-first. (ADR pending.)
  4. Pricing model — per-seat vs. per-app suite vs. usage/AI-metered hybrid?
  5. Vector store graduation threshold (pgvector → dedicated) — define the metric that triggers it.

---

## 3. Product Decisions

> One entry per major product decision. Newest first.

### PD-12 · 2026-08-09 · "My Finances" — separate the pay record from the directory, mask it, split who owns it
- **Context:** founder shared Keka's My Finances screens and asked for the same: an employee should
  see how they're paid and what they're enrolled in, and the payslip should carry the company logo
  and identity instead of a bare table of numbers.
- **Decision (shape):** a separate `employee_finance` table rather than more columns on `employees`.
  The directory row is readable by every colleague by design; a bank account and a PAN never should
  be. Same entity would put them one careless `SELECT` away from each other.
- **Decision (visibility):** self-or-HR only. A manager can see a report's *rating* but not their bank
  details — being someone's manager is not a reason to see where their salary lands.
- **Decision (edit ownership is split):** the employee owns bank details and identity; HR owns
  PF/ESI/professional tax. Those are employer filings — letting people edit their own enrolment is a
  compliance problem wearing the costume of a self-service feature. The server rejects it rather than
  merely hiding the form.
- **Decision (masking is server-side):** the full account number and PAN never leave the backend, so
  a screenshot, a bug report or an open devtools tab can't expose them. A changed PAN drops its
  verified flag, because otherwise the tick survives onto a document nobody has checked.
- **Consequence:** the payslip becomes a real document (logo, legal name, address, employee number,
  designation, UAN/PF/PAN, net-in-words in lakh/crore grouping) — which is what makes it credible in
  a demo, and what an employee actually needs when a bank asks for one.

### PD-11 · 2026-07-27 · Ship a first-deploy path (Render blueprint) + the "Orbit by Calyvora" marketing site
- **Context:** founder wants to host the app for real testing ("think of it as Keka") — a first-ever
  deploy, no prior devops — and, separately, a very polished marketing site branded **Orbit by Calyvora**.
- **Decision (hosting):** one-file **Render.com blueprint** (`render.yaml`) provisions all three parts —
  managed **Postgres**, the **Spring Boot** backend (Docker), the **Next.js** frontend (native Node) —
  wired together automatically. Recommended because Render's Postgres role is **NOSUPERUSER** so tenant
  RLS holds with zero setup. Railway (all-in-one, no sleep) and Hostinger VPS documented as alternatives;
  Railway needs a one-time non-superuser app-role because its default `postgres` is a superuser.
- **Decision (test vs. prod profile):** deploy under a new **`staging`** profile — production-hardened
  (HTTPS/secure cookies, `forward-headers`, small Hikari pool, INFO logs) **but not named `prod`**, so the
  one-click `/api/v1/dev/**` seeding stays available for UAT. A separate **`prod`** profile turns seeding +
  Swagger off for real customers.
- **Decision (safety backstop):** `TenantIsolationVerifier` refuses to boot in `staging`/`prod` if the DB
  role can bypass RLS (superuser/BYPASSRLS) — a multi-tenant data-leak is worse than a failed boot.
  Overridable via `REQUIRE_TENANT_ISOLATION=false`.
- **Also:** datasource now assembles from `DB_HOST/DB_PORT/DB_NAME` when no full `DB_URL` (so a managed DB
  wires in with no JDBC-URL editing); Next config emits `standalone` + tolerates a scheme-less
  `BACKEND_ORIGIN`; beginner guide in `docs/DEPLOY.md`.
- **Decision (marketing site):** a standalone, self-contained `website/orbit/index.html` — dark premium
  aesthetic on the app's own palette (violet #7c5cff → aqua #22d3ee), feature story mapped to the **real**
  shipped modules (attendance→payroll LOP, regularization, helpdesk, platform console), per-seat pricing.
  Kept as a static file (deployable anywhere), distinct from the in-app brand tokens.
- **Status:** _shipped 2026-07-27_ — backend compiles, frontend production build green (standalone emitted).

### PD-10 · 2026-07-27 · Priority HR becomes a true multi-tenant SaaS with a platform-owner above the companies
- **Context:** founder feedback dump (8 points), on branch `product/hr-platform`. Decided jointly via a
  4-question clarification. This reshapes the ownership model of the whole product.
- **Decision (owner = platform vendor):** **OWNER is the seller (us), a platform super-admin sitting
  _above_ all companies — not an employee of any company.** Owner has **no "Me" self-service**. Owner
  gets a **Platform Console**: list every company (headcount, seats used/total, subscription end-date,
  status), **create a company + its first ADMIN**, control each subscription/seat count, and **end a
  subscription at any time → that company's app locks** with a "your subscription has ended" popup.
- **Decision (roles inside a company):** a **fixed role ladder — ADMIN · HR · MANAGER · MEMBER** — with
  preset permissions (custom roles deferred). Owner creates the first ADMIN; ADMIN creates HR/MANAGER/
  MEMBER accounts. MEMBER sees only "Me"; HR sees People/Payroll/Leave/Recruit; MANAGER sees their team +
  approvals; ADMIN sees the whole company (but **not** billing control — see below).
- **Decision (subscription = Netflix model):** a company subscribes for **N seats at ₹100/employee/mo**.
  ADMIN can **only see** the end-date (settings + a left-pane indicator) and gets a **notification as it
  nears expiry** — billing management is **removed from ADMIN**. To grow, ADMIN raises an **in-app
  "request more seats"** which appears in the owner console; **owner approves → seat limit bumps** (6→12)
  and it runs on. (Real email is mocked, so the request is an in-app object, not an email.)
- **Decision (localization):** Settings gets **currency + timezone now** (pick INR/USD… → money formats
  app-wide; pick a timezone → times render in it) and **language as a stored preference only** (English
  now; full translation deferred).
- **Also in scope:** fix **check-in/out** + a **day-wise in/out log** with Keka/Zoho-style visuals; fix
  **payslip printing**.
- **Why:** this is what makes the product actually _sellable_ — a vendor provisions and controls tenants,
  each tenant runs itself, and access is gated by a subscription the vendor owns. It's the SaaS shape the
  ₹100/emp/mo pricing (PD adjacent) always implied.
- **Architectural consequence (ADR to follow):** a **platform scope above the tenant** — an account that
  reads _across_ tenants, which today's row-level security forbids. Building **mock-first** (the product's
  default demo path has no RLS, so cross-company is trivial) then porting to the live backend.
- **Status:** _shipped & verified live 2026-07-27_ — all 8 points delivered on `product/hr-platform`:
  platform-owner console + create-company/end-subscription/seat-approval (V30), roles ADMIN/HR/MANAGER/
  MEMBER with per-role nav+guards, Netflix-style seats + app-lock + expiry banner + request-seats,
  check-in/out fix + daily log, currency/timezone app-wide (₹), payslip printing. 158 backend tests
  green. Demo: `owner@priorityhr.app` + 5 sample companies via `/dev/seed-platform`. Follow-up debt:
  self-registration still creates an OWNER (should be ADMIN); server-side lock is advisory (frontend
  overlay) — enforce at a filter later.

### PD-09 · 2026-07-22 · Documents are generated from templates and then frozen
- **Decision:** the Documents module (founder notes D2/D3) is a **per-company template library** plus
  **immutable generated letters**. Starter templates (offer · joining · relieving · experience ·
  promotion) are seeded on a company's first open and then belong to the company — we never overwrite an
  edited template. A generated letter's body is **rendered once and frozen**; editing the template
  afterwards cannot change a document that has already been issued.
- **Why frozen:** an issued letter is a record, not a view. If a template edit rewrote history, no
  employee could trust a letter we gave them, and we'd have no defensible answer to "what did you
  actually issue me in March?" The cost is duplicated text; the benefit is that the record is real.
- **Why a merge engine, not a document editor:** `{{named.substitution}}` with no expressions or logic.
  The value comes from the data already in People (profile, manager, dates, salary) filling itself in —
  not from another rich-text editor. A field with no value renders as `—` and the generate screen
  **names the empty fields before you issue**, so a letter never goes out with visible plumbing in it.
- **Owner/Admin-only:** these letters carry salary and exit details, so the whole surface is role-gated
  rather than per-endpoint.
- **Open:** e-signature, letterhead/branding upload, and DOCX export are deliberately not built yet;
  print-to-PDF covers the demo and most real use.

### PD-08 · 2026-07-22 · Product named "Orbit"; Calyvora becomes the parent company
- **Decision:** the Enterprise OS product is branded **Orbit**; **Calyvora** is the parent company. UI
  reads "Orbit by Calyvora". Implemented as a single switch in `frontend/src/lib/brand.ts`.
- **Why:** the founder wants a product identity distinct from the company, and a named product a client
  can license in whole or in part (the modular-packaging idea, BR3 in the feedback backlog).
- **Alternatives considered:** Nexus, Cortex, Meridian (shortlist offered); founder chose Orbit.
- **Related open item:** BR3 modular packaging / per-tenant module entitlements — not yet built.

### PD-06 · 2026-07-20 · Knowledge OS is the third app; it closes the task↔doc↔person graph
- **Decision:** Build **Knowledge OS** (spaces + Markdown pages + search) as the third Phase-1 app,
  completing the trio (PD-02). A page's **author is a People `Employee`** and a page may **link one Work
  `Task`** — two real cross-app FKs, not strings. Plan: [docs/Sprint4-KnowledgeOS.md](docs/Sprint4-KnowledgeOS.md).
  Collaborative RBAC (any member writes; archive is OWNER/ADMIN); DRAFT/PUBLISHED; a `parent_id` page tree.
- **Reason:** Knowledge is a company's institutional memory (a long-term moat, §11), and linking docs to
  the people and tasks they're about turns three separate apps into one graph — the platform thesis, visible
  on a single page. Completing the trio is the milestone PD-02 has aimed at since kickoff.
- **Alternatives considered:** a standalone wiki integrated later via API (rejected — the integration tax we
  exist to remove); free-text authors / no task link (rejected — throws away the graph that is the whole point);
  a heavy block/rich-text model (rejected for MVP — Markdown is portable and zero-dependency, SD-15).
- **Trade-offs / debt:** no version history, rich editor, comments, or per-space permissions yet; search is
  `ILIKE`, not full-text. All logged as future work in the Sprint 4 plan §5.
- **Final outcome:** _Shipped & verified live 2026-07-20 (6 tests; a page authored by a People employee and
  linked to Work `PLT-1`, found by full-text search)._ The Phase-1 trio is complete.

### PD-07 · 2026-07-20 · Work OS depth — sprints, backlog & a workspace (tickets as logged debt)
- **Decision:** Deepen Work OS from a single Kanban board into a **project workspace** (Board · Backlog ·
  Sprints · Tickets). Add agile **sprints** (≤1 active/project; complete carries unfinished work to the
  backlog), a **backlog**, and a **lightweight support-tickets** type. Plan:
  [docs/Sprint5-WorkOS-Sprints.md](docs/Sprint5-WorkOS-Sprints.md).
- **Reason:** After completing the Phase-1 trio, the highest-value move is *depth* in the app teams use
  daily. Sprints/backlog are table-stakes for real work management; the founder also wanted tickets now.
- **Alternatives considered:** a standalone agile tool integrated later (rejected — integration tax);
  full **Service OS** for tickets now (rejected — Phase-2 scope; would sprawl). Tickets are therefore a
  **deliberate, logged shortcut** (SD-22b) that graduates to Service OS.
- **Trade-offs / debt:** no drag-and-drop, burndown/velocity, sub-tasks, or per-project roles yet;
  tickets lack customers/SLAs/comments (that's Service OS). All logged in the Sprint 5 plan §5.
- **Final outcome:** _Shipped & verified live 2026-07-20 (5 tests; active sprint on the board, backlog,
  and tickets with People-employee assignees)._

### PD-05 · 2026-07-10 · Work OS is the second app; tasks link to People employees
- **Decision:** Build **Work OS** (projects + Kanban tasks + My Work) as the second Phase-1 app, with a
  task's **assignee being a People OS `Employee`** (a real cross-app foreign key, not a name string).
  Plan: [docs/Sprint3-WorkOS.md](docs/Sprint3-WorkOS.md). Collaborative RBAC (any member creates/edits;
  archive is OWNER/ADMIN); per-project `KEY-N` task refs.
- **Reason:** Proves the platform thesis end-to-end — the second app reuses the foundation *and* reads
  the first app's graph with zero integration glue. This cross-app value is the moat (PD-01/PD-02).
- **Alternatives considered:** free-text assignees (rejected — throws away the org graph, the whole
  point); a separate task tool integrated later via API (rejected — that's the integration tax we exist
  to remove).
- **Trade-offs / debt:** no comments/subtasks/sprints/notifications yet; no per-project roles; no drag-
  and-drop (move via controls). All logged as future work.
- **Final outcome:** _Shipped & verified 2026-07-10 (5 tests, live board with cross-app assignees)._

### PD-04 · 2026-07-10 · People OS is the first app; it owns the org graph
- **Decision:** Build **People OS** as the first business module on the foundation (beachhead per PD-02),
  modeling employees as a 1:1 auto-provisioned extension of the platform `User`. Scope shipped: directory,
  departments/org chart, onboarding, time-off, self-service. Plan: [docs/Sprint2-PeopleOS.md](docs/Sprint2-PeopleOS.md).
- **Reason:** People/org is the identity backbone every other app (Work, Knowledge, CRM…) reads. Building
  it first produces that shared graph and proves the platform thesis (a full app with zero foundation changes).
- **Alternatives considered:** start with Work OS (rejected — depends on the people graph); a standalone HRIS
  with its own identity (rejected — duplicates auth, breaks the "one identity" promise).
- **Trade-offs / debt:** flat vacation allowance (no accrual engine yet); Sprint-1 roles reused (no per-
  department RBAC yet); contractors-without-login not modeled. All logged as future work.
- **Final outcome:** _Shipped & verified 2026-07-10 (18 tests, live)._ Next apps consume this graph.

### PD-03 · 2026-07-05 · Sprint 1 = Platform Foundation, not HRMS
- **Decision:** The first build sprint delivers the tenancy/identity/auth/RBAC/invite spine + app
  shell (13 demo capabilities), explicitly *not* any business module. Plan: [docs/Sprint1.md](docs/Sprint1.md).
- **Reason:** Every future module depends on this foundation; building it right once is the highest-
  leverage work we'll do. Matches the "Foundation before apps" sequencing in [docs/11](docs/11-roadmap.md).
- **Alternatives considered:** start with an HRMS vertical (rejected — would hard-code assumptions the
  platform must generalize); thinner auth-only slice (rejected — invites/settings prove the tenant
  lifecycle the demo needs).
- **Expected impact:** a secure, multi-tenant base that every Phase-1 app (People/Work/Knowledge)
  plugs into; a live demo of the platform thesis.
- **Risks:** scope is a **2–3 week** foundation, not a 5-day sprint (see Founder Note); tenant
  isolation is app-layer-only for Sprint 1 (SD-2, debt logged). Mitigated by vertical slices +
  adversarial isolation tests + a demoable milestone at Feature 6.
- **Sprint-1 implementation decisions:** SD-1…SD-8 recorded in [DECISIONS.md](DECISIONS.md).
- **Final outcome:** _Plan complete; awaiting founder approval to begin coding._

### PD-02 · 2026-07-05 · Depth-first phasing: launch with 3 apps, not the full suite
- **Decision:** Phase 1 ships **People OS + Work OS + Knowledge OS** on a Foundation built only
  as far as those three need. A written "not building yet" list guards scope.
- **Reason:** The #1-likelihood failure mode for a suite is "master of none." Depth-first proves
  the platform thesis and yields sellable standalone products fast.
- **Alternatives considered:** (a) build the full ecosystem in parallel — rejected, spreads thin;
  (b) single app only — rejected, doesn't prove the cross-app thesis that is our whole moat.
- **Expected impact:** faster time-to-revenue, credible each-app excellence, a real proof of
  "one platform" with the universal assistant across all three.
- **Risks:** individual apps must still beat focused best-of-breed tools; mitigated by depth-first
  investment and design-partner validation.
- **Final outcome:** _Open — in effect for Phase 1._ See [docs/11](docs/11-roadmap.md).

### PD-01 · 2026-07-05 · Product scope & decomposition = 14 OS-apps on a shared Foundation
- **Decision:** Define the Enterprise OS as a Foundation Platform + independent OS-apps
  (People, Work, Knowledge, CRM, Finance, Service, Meeting, Analytics, Automation) + AI Studio,
  Marketplace, Mobile, Admin. Apps decomposed by **business function with distinct primary users
  and a single System of Record**.
- **Reason:** Matches how customers buy and adopt, gives each app a clear data owner (avoiding
  "who owns this data" fights), and aligns to team ownership (Conway's Law working for us).
- **Alternatives considered:** decompose by technical layer (rejected — those are shared services,
  not products); one monolithic do-everything app (rejected — collapses ownership and UX).
- **Expected impact:** clean land-and-expand, coherent packaging, near-zero customer-side
  integration cost per added app.
- **Risks:** breadth vs. depth tension (see PD-02).
- **Final outcome:** _Adopted as the product map._ See [docs/03](docs/03-enterprise-os-overview.md),
  [docs/04](docs/04-product-map.md).

---

## 4. Architecture Decision Log

> Summaries here; binding detail in [docs/06](docs/06-architecture-principles.md) and (once
> established in Phase 1) formal ADRs under `docs/adr/`. Newest first.

### ADR-07 · 2026-07-05 · Backend language = Java (LTS) + Spring Boot (was .NET)
- **What:** Transactional core & all OS-apps in **Java + Spring Boot / Spring Modulith**; TS/Node
  for edge/BFF & real-time; Python for AI/ML pipelines.
- **Why:** JVM-native data/event ecosystem (Kafka, Debezium, OpenSearch); Spring Modulith fits the
  modular-monolith strategy; deepest enterprise talent pool (de-risks hiring); **the founder can
  contribute in Java** — the team building the platform must be able to build it; boring/proven.
- **Alternatives evaluated:** **.NET/C#** (originally penciled in) — equally excellent, rejected
  for ecosystem fit, broader cloud-agnostic talent pool, and team contribution fit. Node-only
  backend — rejected for transactional/enterprise-correctness workloads.
- **Pros:** first-class libraries for our substrate; huge hiring pool; mature reliability;
  contribution fit.
- **Cons:** JVM memory footprint vs. lighter runtimes; more ceremony than Node for simple I/O
  services (mitigated by using TS at the edge).
- **Future improvements:** consider GraalVM native images for cold-start-sensitive services;
  virtual threads (Project Loom) for high-concurrency I/O.
- **Detail:** [docs/06 §6.14](docs/06-architecture-principles.md#614-runtime--languages).

### ADR-06 · 2026-07-05 · Multi-tenancy = hybrid 3-tier isolation on one tenant-agnostic codebase
- **What:** Tier 1 pooled (shared DB + Postgres RLS, the default), Tier 2 schema-per-tenant,
  Tier 3 dedicated DB/cluster/region + BYOK. A Tenant Router resolves the physical target;
  app code is identical across tiers.
- **Why:** One codebase must profitably serve a 20-person startup and a 50k-person regulated bank.
  Isolation is a data-routing/infra concern, not an app concern.
- **Alternatives:** pure pooled (best economics, loses regulated segment); pure siloed (great
  isolation, destroys SMB margins & fleet ops).
- **Pros:** win the whole market; residency & compliance reachable; promote tenants across tiers
  without code changes. **Cons:** routing complexity; per-tier ops differences.
- **Detail:** [docs/07](docs/07-multi-tenant-strategy.md).

### ADR-05 · 2026-07-05 · AI is a Foundation primitive (shared AI Platform), not per-app
- **What:** One Model Gateway (Claude-default, provider-abstracted), RAG-first grounding,
  tenant-scoped knowledge graph, agents-as-principals whose tools are permissioned APIs, full AI
  audit + AI permissions.
- **Why:** Cross-domain, permissioned, auditable AI needs a unified data + permission model built
  in from the start — impossible to retrofit or to do per-silo.
- **Alternatives:** AI-per-app (rejected — siloed, inconsistent guardrails, no cross-domain reasoning).
- **Pros:** experiences no single-silo competitor can ship; governance as a selling point.
- **Cons:** the AI Platform is a critical shared dependency; cost governance required.
- **Detail:** [docs/09](docs/09-ai-strategy.md).

### ADR-04 · 2026-07-05 · Security = Zero-Trust, RBAC+ABAC, per-tenant keys, immutable audit
- **What:** OIDC/OAuth + SSO (Entra/Google/Okta) + SCIM; central live authZ (RBAC+ABAC hybrid);
  mTLS everywhere; per-tenant encryption keys + BYOK; append-only tamper-evident audit of humans
  **and agents**.
- **Why:** We hold customers' entire operational data — a breach is an extinction event. Security
  must be structural and blast-radius small.
- **Pros:** enterprise/regulated trust; the secure path is the default path. **Cons:** more upfront
  rigor. **Detail:** [docs/08](docs/08-security-architecture.md).

### ADR-03 · 2026-07-05 · Event-driven backbone = Kafka + CloudEvents + transactional outbox
- **What:** Domain events (past-tense facts) over Kafka; CloudEvents envelope; schema registry with
  backward-compat enforcement; transactional outbox; idempotent consumers.
- **Why:** The nervous system of "integrated by construction" — decouples producers/consumers,
  gives replayable history, and lets new capabilities subscribe without the producer knowing.
- **Alternatives:** RabbitMQ/SQS (great queues, poor event logs); point-to-point calls (coupling
  swamp). **Fallback:** Redpanda (Kafka-API compatible). **Detail:** [docs/06 §6.5](docs/06-architecture-principles.md#65-event-driven-architecture).

### ADR-02 · 2026-07-05 · Data stack = Postgres + Redis + Kafka + OpenSearch + object store + pgvector
- **What:** Postgres SoR (RLS tenancy), Redis (cache/ephemeral), Kafka (events), OpenSearch
  (search), S3-compatible object store (files), pgvector→dedicated vector store at scale.
- **Why:** Boring, proven, open-source-friendly; five datastores cover ~all needs; new datastores
  require an ADR. **Cons:** we operate more ourselves (accepted, aligned with no-lock-in).
  **Detail:** [docs/06 §6.8](docs/06-architecture-principles.md#68-storage).

### ADR-01 · 2026-07-05 · Topology = Modular Monolith → extract services under proven pressure
- **What:** Each OS-app & the Foundation are modular monoliths with CI-enforced module boundaries
  (separate schemas, module APIs, events); extract a service only under measured need.
- **Why:** Most microservice benefits (ownership, testability, extractability) without the
  distributed-systems tax before we can pay it. The classic startup-killer avoided.
- **Alternatives:** microservices-from-day-one (rejected — premature); single monolith (rejected —
  ball of mud). **Detail:** [docs/06 §6.1](docs/06-architecture-principles.md#61-topology).

### ADR-02 · 2026-08-09 · Transactional email = HTTPS API (Resend), not SMTP
- **What:** Outgoing mail goes through an `EmailSender` chosen per send from `EmailSettings`, with
  three transports — Resend (HTTPS :443), SMTP, and a console transport for local dev. Which one is
  used is resolved by an `EmailSettingsResolver`; `MAIL_PROVIDER` pins it, otherwise it's inferred
  from whichever credentials exist. A deployment with nothing configured now logs a loud warning at
  startup and reports its sends as *undelivered*, instead of silently pretending to work.
- **Why:** Found in live QA — every signup on the hosted deployment was unrecoverable. Render (like
  many hosts) blocks outbound SMTP on all ports, so `smtp.hostinger.com:587` timed out; the swallowed
  failure meant registration returned 201, the verification mail went nowhere, and the account could
  never be activated. Not a credential problem and not fixable with TLS settings — SMTP is simply not
  available. Port 443 always is.
- **Also:** sends now return an `EmailResult`, so `POST /auth/register` answers `{"emailSent": …}` and
  the signup screen offers a resend instead of claiming "check your email" for mail that never left.
  Failures still never roll back the signup — the account is real either way.
- **Alternatives:** stay on SMTP via a relay on 2525 (rejected — still a blocked-port gamble);
  fire-and-forget queue (rejected for now — the honest inline result is what the UI needs).
- **Consequence:** per-tenant sending ("customer's mail comes from *their* domain") is now a matter
  of implementing one resolver; no transport or caller knows tenants exist.

### Deployment baseline (cross-cutting)
Cloud-agnostic Kubernetes, service mesh (mTLS), GitOps, trunk-based dev + feature flags +
progressive delivery, OpenTelemetry observability. See [docs/06 §6.10–6.13](docs/06-architecture-principles.md).

---

## 5. Weekly Progress Log

> Auto-summarized at the end of each development week. Newest first.

### Week of 2026-07-20 → 2026-07-22 (founder-feedback week)
- **Features completed:** the founder's 8-page handwritten notes were transcribed into a living tracker
  ([docs/Founder-Feedback-Backlog.md](docs/Founder-Feedback-Backlog.md)) and worked in bucket order.
  Shipped: **Bucket A** (searchable member picker fixing the "dropdown not working" bug, always-on
  Knowledge search, sprint-length picker, **left sidebar nav**, wordmark), **branding → Orbit** (PD-08),
  **Bucket B** (role-aware dashboard + team overview, present vs on-leave, leave calendar),
  **Bucket C** (salary + hike history + payslips V13, richer profiles/skills/ratings V14, goals V15,
  assigned-work with overdue flags), **D1 Clients ⭐** (V16), and **D2+D3 Documents & templates** (V17).
  People and Documents both gained **left-pane sub-panes** at the founder's request.
- **Decisions made:** **PD-08** (product = Orbit, Calyvora = parent), **PD-09** (documents generated from
  templates then **frozen**; merge-substitution, not a document editor). Attendance settled as
  *"both, phased"* — derived from leave now, full daily records later (C.4).
- **In progress / next:** C.4 full daily attendance, C.7 fuller review cycle, D4 Notifications, D5 Inbox,
  BR3 modular packaging. C.9 ("kanban in the employee tab") and D6 ("Organization") need scope
  confirmation from the founder before building.
- **Blockers:** none technical. Business blockers unchanged (design partners, pricing).
- **Bugs discovered/fixed:** members dropdown unusable at scale (→ `MemberSelect`); `Goal` NPE on
  create (assigned-id insert defers `@PrePersist` — initialize timestamps in the constructor);
  schema-validation mismatch `smallint` vs `Integer` (V14 uses `integer`).
- **Technical debt created:** the frontend mock now mirrors the merge engine + starter templates
  (`frontend/src/lib/documents.ts`) — two copies to keep in sync, accepted so the mock demo doesn't lie.
- **Lessons learned:** shipping in the founder's stated bucket order — and keeping the tracker updated as
  each item lands — made "what's left?" answerable at any moment without re-reading the notes.
- **Customer feedback:** the notes themselves (the founder acting as first customer).

### Week of 2026-06-30 → 2026-07-05
- **Features completed:** none (pre-build). Foundational **architecture constitution** authored:
  18 docs under [/docs](docs/README.md). **Sprint 1 plan** authored: [docs/Sprint1.md](docs/Sprint1.md)
  (18-part production-quality plan) + [DECISIONS.md](DECISIONS.md) ledger.
- **Decisions made:** PD-01, PD-02, **PD-03 (Sprint 1 = Platform Foundation)**, ADR-01…ADR-07,
  **SD-1…SD-8** (Sprint-1 implementation choices). Notably: backend **.NET → Java + Spring Boot**.
- **In progress:** Sprint 1 planning complete; awaiting approval to start Feature 0 (scaffolding +
  foundation: Docker Compose, Spring Boot, Next.js, security skeleton, Flyway baseline, CI).
- **Blockers:** none technical. **Business blockers (carried):** recruit 3–5 design partners; decide
  pricing model — both should run parallel to the build.
- **Bugs discovered:** n/a. **Technical debt created (planned, logged):** SD-2 app-layer tenant
  isolation with **RLS deferred to Sprint 2**; SD-5 HS256 JWT (→ RS256 Sprint 2). **Resolved:** n/a.
- **Lessons learned:** documenting decisions *with trade-offs at the moment of decision* is far
  cheaper than reconstructing rationale later. Keeping foundational docs decision-neutral made the
  .NET→Java switch a one-line change. Sizing honesty up front (2–3 wks, not 5 days) prevents
  corner-cutting under a false deadline.
- **Customer feedback:** none yet (no design partners onboarded).
- **Important metrics:** none yet (pre-launch).

---

## 6. Customer Insights

> Logged whenever customer/design-partner feedback arrives. _No entries yet — pre-build._

_Template:_ Company · Industry · Feature requested · Problem experienced · Importance
(Critical/High/Med/Low) · Proposed solution · Current status.

---

## 7. Product Backlog

> Prioritized. Each item: business value · customer impact · effort · dependencies · timeline.
> This is a strategic backlog; execution tickets live in Work OS once it exists.

### Critical
- **Foundation spine (thin but real):** Tenancy/Org (Tier-1 + RLS), AuthN (OIDC+SSO), AuthZ
  (RBAC+core ABAC), service template/golden path, Kafka event backbone (+outbox+schema registry),
  OTel observability, CI/CD+GitOps, enforcement gates (isolation tests, module boundaries, ADRs).
  · *Value:* everything depends on it · *Impact:* enables all apps · *Effort:* High · *Deps:* none
  · *Timeline:* Phase 1 start.
- **Recruit 3–5 design partners** before building Phase 1. · *Value:* de-risks the #1 failure mode
  · *Impact:* directional · *Effort:* Med (founder-led) · *Deps:* none · *Timeline:* immediate.

### High
- **People OS (beachhead):** HRIS, org/directory, onboarding, leave, self-service, basic
  performance/goals, ATS basics. · Produces the identity+org graph every app consumes · Effort: High
  · Deps: Foundation spine · Phase 1.
- **Work OS + Knowledge OS:** proves cross-app value (task↔doc↔person). · Effort: High · Deps:
  Foundation, People OS · Phase 1.
- **AI Platform v1:** Model Gateway (Claude-default), RAG over docs, pgvector, prompt registry,
  AI audit, universal assistant across the 3 apps. · The AI-native promise, visible in v1 · Effort:
  High · Deps: Foundation, apps' events · Phase 1.
- **Admin Platform v1 + Billing v1.** · Control plane + get paid · Effort: Med · Deps: Identity ·
  Phase 1.

### Medium
- Workflow engine, Notification service, Comments primitive, Search service. · Deps: event backbone
  · Phase 1–2.
- Mobile Platform v1. · Phase 2.
- Decide & implement pricing model (open question, §2). · Deps: GTM validation.

### Low
- CRM OS, Service OS, Meeting OS, Automation OS. · Phase 2.

### Future Ideas
- Analytics OS, Finance OS, AI Studio, Marketplace. · Phase 3+.
- Knowledge graph as a standalone corporate-memory product. · Moonshot (see §11).
- On-prem/customer-cluster deployment for sovereign/regulated buyers. · Phase 3+.

---

## 8. Competitor Research

> Continuously updated. _Initial landscape scan — 2026-07-05._

| Competitor | Strengths | Weaknesses | Opportunity for us |
|-----------|-----------|------------|--------------------|
| **Workday / SAP SF** | Enterprise depth, trust, compliance | Dated UX, slow/expensive implementation, weak cross-domain AI, costly | AI-native + fast onboarding + modern UX for mid-market |
| **Rippling** | Strong land-and-expand across HR/IT/Finance | Still assembling breadth; AI shallow | Deeper AI-native core + knowledge graph |
| **Notion / Confluence** | Delightful, composable docs/UX | Not a system of record; weak workflow/permissions at enterprise scale | Knowledge OS with real SoR + governance + graph |
| **Jira / Linear / ClickUp / Monday / Asana** | Mature work management; Linear = craft/speed | Siloed from HR/CRM/finance; integration tax | Work OS natively linked to people/customers/knowledge |
| **Salesforce / HubSpot / Zoho** | CRM depth, ecosystem | Expensive, complex; disconnected from delivery/support/finance | True 360° via one platform |
| **ServiceNow / Freshservice** | Platform + workflow depth (ITSM) | Complex, costly, enterprise-only | Service OS for mid-market with native cross-app context |
| **Slack / Teams / Zoom** | Ubiquitous collaboration | Meeting outcomes are dead-ends; siloed | Meeting OS that feeds the graph & creates tasks |
| **Power BI / Tableau / Looker** | Powerful BI | Require ETL/warehouse integration projects | Analytics OS with no-ETL cross-domain queries |
| **Zapier / Power Automate / Workato** | Broad connectors | Brittle polling, external to the data | Automation OS on the native event backbone |

_To track over time:_ new releases, pricing changes, AI features shipped, UX improvements, and
resulting market gaps.

---

## 9. Startup Metrics

> _No live metrics yet (pre-build)._ Instrumentation is a Foundation requirement, not an
> afterthought — usage metering feeds Billing from day one.

| Metric | Definition | Current | Target (12mo post-launch) |
|--------|------------|---------|---------------------------|
| MAU / DAU | Monthly / daily active users | — | — |
| DAU/MAU stickiness | Ratio | — | ≥ 0.4 |
| **≥3-app customers** (north star) | Tenants on 3+ connected OS-apps | — | grow QoQ |
| CAC | Customer acquisition cost | — | — |
| LTV / LTV:CAC | Lifetime value / ratio | — | ≥ 3:1 |
| MRR / ARR | Recurring revenue | — | — |
| **NRR** | Net revenue retention (expansion moat) | — | ≥ 120% |
| Logo churn | Monthly/annual | — | < 1%/mo |
| Feature adoption | Universal-assistant WAU; cross-app action rate | — | — |
| NPS | Net promoter score | — | ≥ 40 |

---

## 10. Lessons Learned

> Knowledge base of mistakes, solutions, better approaches, and best practices. Newest first.

- **2026-07-10 — Refresh-token rotation is hostile to concurrent/duplicate refreshes.** A duplicate
  refresh (React StrictMode's dev double-invoke, or two browser tabs) re-presents the just-rotated
  cookie, which reuse-detection correctly reads as theft and revokes the whole family — logging the
  user out. Fix: fire the bootstrap refresh once per mount (client guard). Longer term, give the
  server a small same-family grace window. Lesson: security mechanisms (reuse detection) need an
  explicit concurrency story, or they fire on benign races.
- **2026-07-10 — No Docker? Use a real embedded engine, not a fake.** Zonky embedded Postgres runs the
  actual Postgres binary in tests and local runs, so partial indexes / `timestamptz` / real SQL are
  exercised — an H2 substitute would have hidden bugs the tenant-isolation gate must catch.
- **2026-07-05 — Keep foundational docs decision-neutral until a decision is actually made.** The
  runtime sections stayed language-agnostic, so switching .NET→Java touched one standards line + a
  new decision section rather than a rewrite. Defer commitments to the point of decision, then
  record them explicitly.
- **2026-07-05 — Decisions must be logged with trade-offs *at the moment of decision*.** Rationale
  is expensive to reconstruct later; this journal exists so we never have to.
- **2026-07-05 — Enforce principles with machines.** Standards that rely on memory decay at 100
  engineers. Every standard in [docs/14](docs/14-engineering-standards.md) has an enforcement
  mechanism (CI gate, lint, test).
- **2026-07-05 — The hard part is restraint, not architecture.** The most likely failure is
  building too much; guardrail is depth-first phasing + a written "not building yet" list.

**Best practices adopted:** API-first & event-first before UI; single System of Record per entity;
ambient/mandatory tenant context; trunk-based dev + feature flags; ADRs for foundational changes;
docs-as-code reviewed in PRs.

---

## 11. Future Vision

> Running list of big ideas, moonshots, AI opportunities, new modules, and expansion.

- **Big ideas:** the org **knowledge graph as a durable corporate asset** — institutional memory
  that's queryable and actionable; possibly our deepest long-term moat.
- **Moonshot features:** governed AI agents performing a meaningful share of routine operational
  work autonomously, humans supervising exceptions; agentic workflows as a primary work mode.
- **AI opportunities:** cross-domain natural-language business questions; AI-authored automations
  ("describe the process → we build the flow"); an AI/agent Marketplace; per-vertical expert agents.
- **New product modules (later phases):** Analytics OS, Finance OS (GL/payroll), AI Studio,
  Marketplace, deeper Meeting OS (native video), CMDB/change mgmt in Service OS, CPQ/marketing in
  CRM OS.
- **Expansion opportunities:** up-market to enterprise/regulated (Tier-3 + BYOK + residency);
  on-prem/sovereign deployments; a partner/ISV platform economy building verticals on top
  (healthcare, legal, manufacturing) — the "apps on iOS" end state.
- **Long-term roadmap:** Phases 1–5 in [docs/11](docs/11-roadmap.md).

---

## Appendix · How this journal is maintained
- **Who:** the founder and the AI co-founder, jointly. The AI co-founder updates it whenever a
  significant decision, feedback item, or lesson occurs — without being asked.
- **When:** at every material decision (add a PD/ADR entry), on customer feedback (§6), at week's
  end (§5), and whenever a lesson is learned (§10).
- **Relationship to /docs:** [/docs](docs/README.md) is the *binding constitution* (what the system
  is). FOUNDER.md is the *narrative and rationale* (why we chose it, what we're thinking, where we're
  going). They must not contradict; when a decision here changes an architecture doc, update both in
  the same change.
