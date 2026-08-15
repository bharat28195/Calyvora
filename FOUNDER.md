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

### L-1 · 2026-08-11 · A migration that passes every test can still fail the only database that matters
- **What happened:** V40 (PD-18) deleted the demo platform owner with a bare
  `delete from users where email = 'owner@priorityhr.app'`. Green across 264 tests, then the deploy
  died on `refresh_tokens_user_id_fkey`. The backend never started; Render kept the previous container
  running, so the app stayed up and the only symptom was that the new owner login didn't work.
- **Why the tests could not have caught it:** every test database is built from empty by Flyway and
  then used. A destructive migration therefore always runs against data *it* created. The deployment's
  database had an account that had been **used** — signed in, so a refresh token pointed at it. There
  was no test in the suite that migrated *over* pre-existing data, so this class of bug had no way to
  surface.
- **Fix:** clear the session artefacts first (right regardless — it revokes the old credential), then
  delete inside a `do $$ … exception when foreign_key_violation` block that retires the account
  instead when something still references it. Plus `V40LegacyOwnerRemovalTest`, which cleans to V39,
  plants realistic rows, and only then applies V40.
- **The rule to keep:** *any migration that deletes or rewrites existing rows gets a test that plants
  the data first.* Adding a column does not need one; touching rows a customer created does.
- **Second lesson, cheaper but real:** a failed deploy on Render is silent from outside — health stays
  UP because the old container keeps serving. "The site works" is not evidence the deploy landed.
  Check that something *from the new build* answers.

### PD-20 · 2026-08-10 · The letterpad, and letters that raise themselves
- **Context:** Documents already had templates, merge fields and a paper-like preview, but every
  letter came out on blank paper — no logo, no address, no colour, one typeface — so nobody would
  actually send one to a candidate. And the two moments that produce letters, somebody joining and
  somebody leaving, were entirely manual: HR had to remember the sequence, find the template, fill
  it in, and separately remember to chase the laptop back. The ask was to take that load off a human.
- **Decision, three parts.**
  1. **A letterpad per company** (`letterheads`, one row keyed by company id): logo, heading, address
     block, footer strip, brand colour, one of three typefaces, and a rule. Set once; every letter
     prints on it. Per-template opt-out for memos that carry their own heading.
  2. **Offer and hire raise their own letters.** "Make an offer" moves the candidate to OFFER and
     issues the offer letter from the candidate's own details — no employee record required, because
     the merge engine already takes overrides. "Hire" invites them, marks them HIRED, and issues the
     joining letter.
  3. **Exit formalities as a checklist a manager works.** Starting an exit records the last working
     day, moves the employee to a new `NOTICE` status and raises a ten-item clearance list. Completing
     it issues the relieving letter and the experience certificate.
- **Editor: a toolbar over the existing text format, not a WYSIWYG.** Considered storing HTML and
  editing on the page as in Word — closer to what was asked for. Rejected for now: it means
  sanitising untrusted HTML before every render, migrating every existing template, and roughly
  double the build, to buy formatting nobody had asked for beyond bold, headings and lists. What is
  saved stays plain text a person can read and repair, and `dangerouslySetInnerHTML` never sees
  anything a user wrote. Revisit if tables in letters turn out to matter.
- **The constraint that shaped the hire flow:** an employee row needs a `user_id`, and the user does
  not exist until the invitation is accepted — while acceptance is a public call with no tenant bound,
  so it cannot write to `employees` under RLS at all. So the agreed job title, start date and
  department ride on the invitation row and are applied by `EmployeeService.provision` the first time
  the profile is created, which also seeds the joining checklist. Both ends of that are real
  constraints, not preference.
- **Refusing to complete an exit while clearance is open** is the one hard rule. A relieving letter
  certifies that company property came back and dues were settled; issuing it before that is true is
  a statement the company cannot stand behind. Overridable with `force=true`, because reality has
  exceptions — but never by accident.
- **Who may tick what:** onboarding items belong to the joiner (or an admin); exit items belong to the
  leaver's **manager**, HR or an admin, and explicitly not to the leaver, who would otherwise be
  signing off that they returned their own laptop.
- **Also:** exits are a top-level nav item rather than a child of People, which is HR-only — a manager
  would never have found a page nested under a section their role cannot open.
- **Trade-offs / debt:** the letterpad is applied at render time, so an old letter re-renders on
  today's stationery (the *words* stay frozen, which is what was signed — but a company that rebrands
  will see its old letters change). Output is browser print / Save-as-PDF; no server-rendered PDF and
  no "email this letter to the candidate" yet. There is no offboarding equivalent of the checklist
  templates — the ten items are a constant, not per-company configuration.

### PD-21 · 2026-08-12 · "Start free trial" asks; it does not admit
- **Context:** the trial button on the marketing site pointed at `/register`, which created a company
  and an ADMIN who could sign in that second. Nobody sold anything, nobody was told, and nobody
  approved it — anyone who found the URL had a live workspace. The founder's instruction was plain:
  nobody gets in until I give permission, and I want an email when someone asks.
- **Decision:** the public surface stores an **enquiry**, not an account. `POST /api/v1/trial-requests`
  is open to anyone, takes company, name, email and three optional fields, and creates a row in
  `trial_requests`. There is no password on the form because there is nothing to set one on. The
  vendor is emailed, the asker is acknowledged, and the request sits in the platform console until the
  owner approves it — at which point the workspace is provisioned through
  `PlatformService.provision`, the same path "New company" already used.
- **Self-signup is closed by config, not deleted.** `calyvora.security.registration.open` defaults to
  false and `/auth/register` answers 403 with an explanation. Kept as a flag because it is a
  commercial decision, not an architectural one — if we ever want open signup back it is one
  environment variable, and because deleting the endpoint would have turned every old link into a
  mystery instead of a message. `/register` in the app redirects to `/request-trial` for the same
  reason: that address was the site's call to action for months.
- **Why the password is typed by the owner, not emailed.** Approval asks for a starting password,
  seats and trial length. The customer's email says the workspace is ready and where to sign in; the
  credential is handed over by the person who sold the trial. Mailing a password would have been less
  work and worse. The alternative — a set-your-own-password token — needs machinery this codebase
  does not have yet (there is still no password-reset flow), and inventing it here would have made
  this change twice the size. **That is the debt to pay next**, and it pays for forgotten passwords
  too.
- **One open request per address**, enforced by a partial unique index rather than application logic.
  Someone who clicks twice because nothing visibly happened gets the same quiet 202, and the vendor's
  queue stays one row per person. Partial, so a customer turned down in March may ask again in
  September.
- **Trade-offs / debt:** the endpoint has no rate limit or captcha, so the queue can be spammed — the
  duplicate index blunts it but does not stop a script with a thousand addresses. Nothing here is
  RLS-protected (it cannot be: the caller has no tenant), so the vendor's sales queue is guarded by
  the platform console's role check alone. Declining sends no email, deliberately: turning someone
  down is a conversation, not an automated brush-off.
- **A note on the test suite.** Closing signup broke the way nearly every integration test conjures a
  tenant. Rather than rewrite ~250 tests to provision through the owner console — which would have
  made them all tests of the console — `IntegrationTestBase` keeps registration switched on and
  `TrialRequestFlowTest` re-declares `@SpringBootTest` without the override, so the shipped default is
  asserted directly. Worth knowing: the suite is not, by default, exercising the production auth
  posture. One class is.

### PD-22 · 2026-08-13 · Preparing a demo is a deliberate act, not a button on the login screen
- **Context:** "Explore the live demo" sat on `/login` under the sign-in form. One click seeded a
  populated company and dropped an anonymous visitor straight into its dashboard as the demo owner.
  It was a sales affordance placed on the one door real customers use, and it fused two different
  actions — *build the data* and *become someone* — into a single click.
- **Decision:** the button is gone. Demo data is now prepared at **`orbit.calyvora.in/demo/seed`**, a
  page you open on purpose before anyone is watching. It seeds on arrival, then lists every login with
  a copy button — because what you actually need thirty seconds before a demo is the credentials, not
  a confirmation message. It signs nobody in; whoever is running the demo chooses the identity to show.
- **A GET that writes,** deliberately. Seeding is idempotent and purely additive — it only fills gaps,
  never overwrites or deletes — so a stray prefetch or a double-click can do no harm, and making it
  POST would mean it could not be reached from the address bar, which is the whole point. Still
  `@Profile("!prod")`: it must never exist on a deployment holding real customer data.
- **The owner account moved** to `bharat28195@calyvora.in` / `Bharat@28195#`. **That password is in
  the source tree, so it is not a secret** — anyone who can read the repo can sign in as the account
  that sees every customer. It exists so a fresh deployment works immediately; `PLATFORM_OWNER_PASSWORD`
  must be set on anything holding real data, and the app warns at every startup until it is.
- **Renaming, not recreating.** Changing the configured owner email on a deployment that already has
  an owner would have left *two* accounts able to read every customer — the old one still live, still
  holding its old password, and invisible in a console that lists customer companies. The bootstrap
  now moves the existing account instead. An UPDATE, not a DELETE-and-create: the owner row is
  referenced from refresh tokens and a dozen `created_by` columns, which is exactly what broke the
  V40 deploy (L-1). The password is reset during the move, because an address that just changed has no
  meaningful old password and there is still no reset flow to recover with.
- **Tested where it actually runs.** Every other test starts from an empty database, so the bootstrap
  always takes its "create" branch; the rename only ever executes on the live deployment. That is the
  precise shape of the V40 incident, so `PlatformOwnerRenameTest` builds the deployment's state
  first — including a company ADMIN sitting in the platform company, to prove the reconciliation
  matches on `OWNER` and not merely on "first user found".
- **Trade-offs / debt:** the seed endpoint is unauthenticated on staging, so anyone who finds the URL
  can populate demo data there. Acceptable for a demo deployment, unacceptable the day staging holds
  anything real — at which point the profile must change to `prod` and the endpoint disappears.

### PD-23 · 2026-08-15 · A way back in, and the app moves to `orbit.calyvora.in`
- **Context:** there was no password reset at all. A forgotten password meant asking an administrator
  to set a new one, and the platform owner had nobody to ask. Every earlier change that touched
  credentials — approving a trial, moving the owner account — had to hand passwords over out of band
  precisely because of this hole. It has been on the debt list since PD-21.
- **Asked for: OTP to a phone. Built: OTP to email.** Two facts decided it. No account has a phone
  number — `users` holds email only, and the employee profile that does hold one is behind RLS, so it
  is unreadable to someone who is not logged in; a phone-only reset would have locked out every
  existing user including the founder. And Indian transactional SMS needs DLT registration (sender ID
  and every template registered with a TRAI-approved platform) before a gateway delivers anything,
  then costs per message. Email is already wired through Resend and free at this volume. The founder
  chose email once the cost was clear.
- **A six-digit code, not a link.** It can be read off one device and typed into another, which is
  what people actually do — and it is exactly what an SMS would carry, so switching channel later
  needs a sender, not a redesign. No `users.phone` column was added: schema for a feature nobody uses
  is awkward to unwind (L-1), and it can be added against a real requirement on the day.
- **The bug the tests caught, which I would otherwise have shipped.** The attempt cap did not work.
  The obvious implementation increments the counter and then throws to reject the guess — and the
  throw rolls the increment back. Every wrong guess was therefore the first wrong guess, and a
  six-digit code, whose entire safety rests on the number of tries being small, was brute-forceable
  at leisure. Fixed with a `REQUIRES_NEW` recorder, the same shape and the same reasoning as
  `RefreshTokenRevoker`. **Worth remembering: any counter incremented on a path that then throws is
  wrong by default.**
- **What it refuses to do,** each pinned by a test: reveal whether an address has an account (unknown
  addresses get the same answer, and a wrong code fails with the same words as a missing account);
  accept a code twice; leave a previous code alive once a new one is asked for; allow more than five
  requests an hour for one account; accept a weak password (this is a second front door, and the
  weakest route decides what the password rules actually are); revive a DISABLED account; or leave
  old sessions valid — resetting revokes every refresh token, because the likeliest reason to reset
  is that a session is somewhere it should not be.
- **The app moved to `orbit.calyvora.in`.** Which uncovered the real find: **`app.calyvora.in` never
  existed.** `FRONTEND_BASE_URL` had pointed at it for weeks, so every invitation, verification and
  trial-approval link sent in that time went to a hostname returning NXDOMAIN. The app was healthy,
  every page loaded, the suite was green — and nobody could act on an email. It is the one setting
  whose breakage is completely invisible from inside the system, and nothing in the app can detect
  it. **Resolve it after changing it; do not trust it because the site loads.**
- **Trade-offs / debt:** reset codes go nowhere until `RESEND_API_KEY` is set on Render — the second
  feature now blocked on that one key, after trial notifications. Throttling is per account, not per
  IP, so one attacker can still ask about many addresses; a rate limit at the edge is the fix if it
  ever matters.
- **Final outcome:** _Shipped with 15 integration tests covering the letterpad's PATCH semantics and
  tenant isolation, the exit lifecycle including the clearance guard, and the hire flow through to the
  profile and checklist appearing after acceptance._

### PD-19 · 2026-08-10 · Calyvora is the parent; Priority HR Services is the business inside it
- **Context:** the revenue today is Priority HR Services — real clients, real placements. The product
  is newer than the business that pays for it. The site said nothing about how the two relate, so a
  visitor could not tell whether Orbit and Priority HR Services were one company, a partnership, or a
  reseller arrangement, and existing service clients had no reason to trust a software brand.
- **Decision:** present Calyvora as the **parent company** with two arms under it — **Orbit** (the
  product) and **Priority HR Services** (the services business, and today's clients). Two directors
  with separate remits: **Khushboo, Director — Calyvora** (the company and the platform) and
  **Renu Rao, Director — HR Services** (all client delivery and hiring). A new `about.html` states
  this as an org tree rather than a paragraph, and every page's footer now carries "Priority HR
  Services is part of the Calyvora group."
- **Why this way round:** the services business is the credibility and the product is the leverage.
  Making the product company the parent lets Orbit be sold to companies that will never buy hiring —
  the whole point of becoming a product company — while the services arm keeps its own name and its
  own client relationships instead of being absorbed into a brand those clients never bought.
- **Alternatives considered:** one merged brand (rejected — throws away the name existing clients
  signed with); Priority HR Services as the parent with Orbit as its tool (rejected — a services
  company selling software to other services companies is a harder story, and it caps the product);
  saying nothing about the structure (rejected — that ambiguity is what the page exists to remove).
- **Open, and a real one:** this is currently a **presentational** group structure. Whether Priority
  HR Services becomes a legal subsidiary of a Calyvora entity is a registration question with tax and
  contract consequences that the website cannot settle. The copy is deliberately worded as "part of
  the Calyvora group" and "operates as part of" rather than naming a shareholding, so nothing on the
  site has to be retracted if the paperwork lands differently. **Confirm with an accountant before
  claiming a parent/subsidiary relationship in a contract or an invoice.**
- **Final outcome:** _Shipped — `website/orbit/about.html`, linked from the nav and footer of every
  page._

### PD-18 · 2026-08-10 · Three tiers: the vendor, the agency, the company — and only the vendor sells
- **Context:** the site sells "manage every company from one console" to agencies and groups, and the
  only thing that fitted was the **platform-owner console** — our own view, which reads every tenant
  on the system and can start and end subscriptions. Giving that to a customer who runs several
  companies would expose every other customer and let them switch on their own billing.
- **Decision:** a third tier between the two. The platform owner (`ownerorbit@calyvora.in`, one
  account, ours) creates an **agency**; the agency creates its own companies and asks for seats; the
  vendor alone activates billing. A company an agency creates is `PENDING` and therefore **locked**
  until we activate it — its admin can sign in and see why, and nothing else works. Selling directly
  to a company is unchanged and stays the common case: those simply have no agency and show as
  "Direct" in the owner console, so both ways of selling live in one list.
- **Alternatives considered:** giving agencies the platform console with a filter (rejected — one
  bug in the filter exposes every customer, and the console can end subscriptions); pooled seats
  across an agency (rejected — needs a new billing model to buy flexibility nobody has asked for);
  letting agencies self-sign-up (rejected — we would be approving billing for strangers).
- **Scope of what an agency can see: company-level summaries only** — headcount, seats, status, end
  date, cost. No employee, payroll or personal data. This is not a limitation we imposed reluctantly;
  it is what makes the tier safe to build at all. RLS binds one `company_id` per connection, so the
  agency console reads only `companies`/`users`/`subscriptions` — the three tables V12 deliberately
  leaves outside RLS — and a member company's HR data is unreachable *by construction*, because the
  agency's own tenant binding is its workspace. Deeper drill-down would mean a real cross-tenant read
  path, and that is a much larger security surface than the feature is worth today.
- **Shape:** an agency is a `Company` row flagged `is_agency`, its members holding `AGENCY_OWNER` —
  the same pattern V35 established for the platform company, where the console is granted by
  *membership*, not by the role alone. Member companies carry a nullable `agency_id`. Reusing that
  shape avoided a second identity model; the agency owner needs a home company for `users.company_id`
  and the tenant binding either way.
- **Also:** the platform owner moved out of the dev-only seeder into `PlatformOwnerBootstrap`, which
  runs in every profile — the one account that can see every customer should not depend on someone
  remembering to call a seeding endpoint, nor carry a demo password. `owner@priorityhr.app` is
  deleted by V40.
- **Trade-offs / debt:** an agency owner has no way to open one of its companies as that company's
  admin, which a real agency will eventually want. Company-scoped endpoints without an explicit role
  gate (the people directory) are reachable by an agency but bound to its own empty workspace, so
  they return nothing useful — untidy rather than unsafe, and worth tightening.
- **Final outcome:** _Shipped on `product/hr-platform` with 7 isolation tests: agency A cannot see
  agency B's companies by list or by id, cannot reach the platform console, cannot activate or end
  billing, and cannot read a member company's people._

### PD-16 · 2026-08-09 · Commercial rules bind on the server, not in the UI
- **Context:** a full QA pass over the deployed build (243 checks across 14 modules, all four roles, two
  tenants) came back with reads and tenant isolation completely clean — and the *commercial* model
  unenforced. Ending a company's subscription set a flag the frontend chose to respect while the API
  kept serving that tenant reads, writes and fresh logins; the seat limit was displayed everywhere and
  consulted nowhere, so a one-seat company could invite without limit.
- **Decision:** Every commercial term is enforced in the backend. A `SubscriptionLockFilter` rejects a
  locked tenant with `402 SUBSCRIPTION_INACTIVE` on everything except the surface the lock screen needs
  (sign in/out, who am I, read my subscription) — **login deliberately still succeeds**, because the
  product's answer to a lapsed subscription is an explanatory screen, which it cannot show if the
  credentials themselves start failing. Seats are consumed by active members *and* pending invitations,
  checked when the invitation is issued. Owner-console values are validated rather than silently
  clamped.
- **Alternatives considered:** blocking login outright for a locked tenant (rejected — the customer then
  sees "wrong password" instead of "your subscription ended", and support pays for it); checking seats
  only at accept time (rejected — the error arrives for the invitee, who can do nothing about it, long
  after the admin who could).
- **Trade-offs / debt:** the lock is evaluated per request against the subscription row — one indexed
  lookup on a hot path, un-cached for now. Payroll run is still O(employees) serial payslip builds
  (~10 s for six people) and needs batching before a real customer.
- **Also fixed in the same pass:** payslips printed **USD** on an INR company because the currency was
  read from the salary row (which defaults to USD) rather than company settings; `PATCH
  /company/settings` was a full replace that erased the legal name and address — which print on every
  payslip — when a client sent only the localisation fields; IFSC/PAN/UAN rejected ordinary input
  (lower case, spaced digits) instead of normalising it, and the UI showed a bare "Validation failed"
  while the API had been returning per-field messages all along; attendance stamped punches in server
  UTC rather than the company timezone, putting an early-morning IST punch on the previous day.
- **Final outcome:** _Fixed on `product/hr-platform` with regression tests covering each defect._
  Outstanding and **not** code: `RESEND_API_KEY` is unset on Render, so every invitation and
  verification email fails silently — invitations still work only because the API hands the join link
  back to the admin.

### PD-16 · 2026-08-09 · A ₹1,299 floor and two months free on annual — the shape of the offer
- **Context:** founder wanted pricing low enough for startups but not low enough to go broke, and asked
  for research against the market.
- **What the market does:** Keka ₹90–180/employee but with a **₹6,999/month minimum** and a 2% setup
  fee; Zoho People ₹50–230 but payroll is a separate ₹33 add-on (₹85–180 bundled); greytHR free to 25
  then a ₹2,495+ base.
- **What it actually costs us:** the app is multi-tenant, so one backend and one database serve every
  customer. Render is ~$20/month all in. **Break-even is ~35 employees across all customers — two
  small companies.** Infrastructure is not the risk; support time and having no floor are.
- **Decision:** keep ₹149/₹99 but add a **₹1,299 monthly minimum**. A four-person customer at ₹149
  pays ₹596 and will cost more than that in support. At ₹1,299 we are still 5× under Keka's floor,
  which is precisely the wedge — a 15-person startup pays us ₹2,235 against their ₹6,999.
- **Decision:** **annual prepay charges 10 months, not 12.** Cash upfront when it matters most, and a
  prepaid customer is much less likely to churn — worth more than the two months.
- **Decision:** quote **excluding GST**. B2B customers reclaim it; quoting inclusive would hand over
  18% of every rupee for nothing.
- **Considered, not done:** free tier up to 10 employees without payroll, to counter greytHR's free-25
  and make payroll the upgrade trigger. Worth revisiting once there are real signups to learn from.

### PD-15 · 2026-08-09 · Pricing is data the owner edits, and price changes are never retroactive
- **Context:** founder asked whether changing rates would always mean a full deploy, and said to do
  whatever is best for the product.
- **Decision:** the price list moves out of code into the database, edited on Platform → Pricing.
  Changing what you charge is a business decision that happens on a business timescale; making it
  wait on a build is how prices end up stale because changing them is a chore.
- **Decision (the important one):** price lists are **versioned by the date they take effect**, and
  every calculation asks for the list in force for the month it's pricing. A single editable rate
  would have been far simpler and quietly wrong — it would restate invoices already issued, so a
  customer querying last month's bill would be shown a number that never existed at the time.
  Billing that can't be checked isn't billing.
- **Consequence:** any tier shape works, so a future "₹199 under 25 people" or an enterprise band is a
  form fill, not a release. The graduated rule (PD-14) is enforced for whatever is configured.
- **Rejected:** env vars on Render. No history, a restart per change, and still retroactive.

### PD-14 · 2026-08-09 · Volume pricing is graduated, not a flat band
- **Context:** founder set pricing at ₹149 per employee up to 100 people and ₹99 beyond, and asked
  for the app to match the website.
- **Decision:** the cheaper rate applies **only to the employees above 100**, not to everyone once the
  threshold is crossed.
- **Why:** a flat band makes revenue fall as a customer grows. 100 employees at ₹149 is ₹14,900; 101
  at ₹99 would be ₹9,999 — the 101st hire would cost us ₹4,901 a month, and a 101-person customer
  would pay less in total than a 71-person one. Graduated keeps the bill monotonic (101 = ₹14,999)
  while still honouring the promise that bigger companies get the better rate.
- **Also decided:** the quoted rate shown to a customer is the **marginal** one ("you're on ₹99 now"),
  not a blended average — a blended figure appears on no price list and answers no question they have.
  And a company the owner has quoted a special rate is flagged `custom_price`, so future changes to
  the standard list can't silently rewrite what was agreed with an existing customer.
- **Open question for later:** whether crossing 100 should be sticky (a company that dips to 98 goes
  back to ₹149 today). Fine at current scale; worth revisiting before a customer notices.

### PD-13 · 2026-08-09 · A workspace is usable the moment it's created — and OWNER means the vendor, only
- **Context:** founder — "when I create a workspace for anyone they should be able to log in directly
  without verification, they'll be admin, and the owner page is only for me, to see how many companies
  there are and what I'm earning."
- **Decision (activation):** creating a workspace creates an **active company + active ADMIN** and
  signs them in. Email verification becomes a switch (`REQUIRE_EMAIL_VERIFICATION`, off by default),
  not a requirement — it can come back on once outgoing mail is proven.
- **Decision (roles):** `OWNER` is the platform vendor and **nothing else**. A company signup is an
  `ADMIN`. This was already the intent under PD-10, but registration still handed out `OWNER`.
- **What that uncovered:** because `/api/v1/platform/**` is guarded on the `OWNER` role and lists
  every company, **every self-registered user was a platform owner** who could read every customer's
  headcount, seats and billing. It had never fired only because verification was broken, so nobody
  could log in after signing up — the bug was holding the door shut on itself. Acting on the founder's
  request without noticing would have opened it.
- **Decision (defence in depth):** the console now needs the role **and** membership of the company
  flagged `is_platform`. A privileged role should never be the only thing standing between one
  customer and another's data — roles get handed out by code that changes, company identity doesn't.
- **Lesson:** a bug can mask a worse one. The email outage looked like a availability problem and was
  also, silently, the only access control on the platform console.

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
