# Changelog

All notable changes to Calyvora. Newest first. Dates are absolute (ISO `YYYY-MM-DD`).

## [Unreleased]

### 2026-08-09 — In-app billing matches the published price list (₹149 / ₹99)
**The site advertised volume pricing while the app still charged a flat ₹100 per employee stored per
company. What a customer is billed should be what they were sold.**

- **Volume tiers, applied everywhere** (`VolumePricing`, Flyway V36): ₹149 per employee per month for
  the first 100 people, ₹99 for each one after. Used by the customer's billing page, their invoice
  history, and the platform owner's revenue view — one calculation, so the owner console can never
  quote a figure the customer isn't being asked to pay.
- **Graduated, not a flat band — deliberately.** Reading "₹99 once you pass 100" as a rate on *every*
  employee makes the bill **fall** as a company grows: 100 people at ₹149 is ₹14,900, but 101 at ₹99
  would be ₹9,999 — a third of the revenue lost at the moment a customer hires their 101st person,
  and a 101-person customer paying less in total than a 71-person one. Charging the cheaper rate only
  above the threshold keeps the bill rising (101 people = ₹14,999) while still giving larger
  customers the better marginal rate. A test asserts the bill never falls as headcount grows.
- **Negotiated rates still win.** `subscriptions.custom_price` marks a company the platform owner has
  quoted its own flat rate; the price list doesn't touch it. Existing rows on the old ₹100 default —
  which nobody chose — move to the standard list, while any row whose price had been deliberately
  changed keeps it.
- **The UI explains the bill.** Once a company crosses a tier the total is no longer headcount × one
  rate, so the billing page shows the ladder ("Employees 1–100 · ₹149 each") instead of arithmetic
  that doesn't add up. The headline rate is the **marginal** one — "you're on ₹99 now" — rather than a
  blended average, which appears on no price list and answers no question anyone has.
- **The mock backend carries the same tier table**, so the two modes can't quote different money.

### 2026-08-09 — Workspaces are usable immediately — and signup no longer grants the owner console
**Founder request: "when I create a workspace for anyone they should be able to log in directly
without verification… they will be admin, and the owner page is only for me." Acting on the second
half of that turned out to be a security fix, not a preference.**

- **`[CRITICAL]` Self-registration assigned `Role.OWNER`.** `/api/v1/platform/**` is guarded by
  `hasRole('OWNER')` and lists `companyRepository.findAll()` — every company on the platform with its
  headcount, seats, subscription and billing. So **anyone who signed up became a platform owner and
  could read every customer's data.**
  - **Why it never fired:** the broken email verification was accidentally the only lock on the door.
    No self-registered account could ever log in, so nobody ever reached the console. Removing that
    gate — exactly what was being asked for — would have opened it on the next signup.
  - **Fix, in two layers.** Registration now creates an **ADMIN** of the new company, which is what a
    company signup always should have produced. And the console requires **role `OWNER` *and*
    membership of the company explicitly flagged as the platform** (`companies.is_platform`, new in
    V35, checked by `PlatformAccess`) — so a stray OWNER row, however it appears, still can't reach
    another tenant's data.
  - **V35 repairs existing data:** every OWNER outside the platform company is demoted to ADMIN.
- **Creating a workspace signs you straight into it.** You've just set up a company and need to start
  adding people — waiting on an email helped nobody, least of all while mail was broken. The signup
  becomes an active ADMIN of an active company and the UI logs them in and lands on the dashboard.
- **Verification is a switch, not a deletion.** `REQUIRE_EMAIL_VERIFICATION=true` restores the old
  behaviour once outgoing mail is proven. The login gate is checked against the same flag, so the
  accounts created while mail was broken aren't left permanently locked out with no way to rescue
  them. The welcome email still sends when a mailbox is configured — it just never blocks anyone.
- **The app opens on the app.** `/` was a marketing landing page, so anyone opening the deployment —
  including a customer who just wants to work — had to get past a brochure first. It now redirects:
  dashboard if you have a session, log in if you don't. The product story still lives on the separate
  marketing site (`website/orbit`), which is where it belongs.
- **The mock backend mirrors all of it**, so the two modes can't drift on something this important.
- 6 integration tests: signup logs in immediately, signup is an ADMIN, signup **cannot** reach the
  platform console, a new admin can invite members straight away, the real platform owner still can
  reach the console, and one company's admin still can't see another's people.

### 2026-08-09 — My Finances + a payslip that reads like a real document
**Founder request, with Keka's "My Finances" screens as the reference: an employee should see how
they're paid and what they're enrolled in, and the payslip should carry the company's logo and
identity rather than a bare table of numbers.**

- **Employee finance record (Flyway V34, RLS).** New `employee_finance` — payment mode, bank
  (name / account / IFSC / account name / branch), PF (status, number, UAN, join date, account name),
  ESI (status, number), professional tax (state, registered location), and identity (PAN, verified
  flag, date of birth, parent's name). One row per employee, created on first view.
  - **Why its own table rather than more columns on `employees`:** the directory row is readable by
    every colleague by design. A bank account and a PAN never should be, and putting them in the same
    entity would be one careless `SELECT` away from a leak.
- **Two access rules, both tested.** Visibility is **self-or-HR** — not even a manager can read a
  report's bank details. Edit ownership is **split**: an employee maintains their own bank details and
  identity, while PF/ESI/professional-tax enrolment is HR's, because those are employer filings and
  self-service there is a compliance hole rather than a convenience. A self-edit of a statutory field
  is rejected with `403`.
- **Account number and PAN are masked server-side** (`XXXXXX456D`) — the full values never reach the
  browser at all, so there is nothing for a screenshot, a bug report or a devtools tab to expose.
  Changing a PAN clears its `verified` flag, so the tick can't carry over to an unchecked document.
- **Payslip now carries the whole document.** `PayslipResponse` gained the company block (legal name,
  address, **logo**), who it's for (employee number, date joined, department, designation), the
  statutory identifiers a payslip is expected to show (payment mode, UAN, PF number, masked PAN), and
  **net-in-words** — spelled with Indian lakh/crore grouping, the line a reader uses to catch a
  misplaced digit. Rendered as a real document rather than a two-column table.
- **`/me/finances`** — payroll summary strip, payment information, statutory information (read-only,
  marked as HR-maintained) and identity information with a verification badge, plus an edit form for
  the half the employee owns.
- **Branding already existed but never reached the payslip.** `logoUrl` / `legalName` / `address` have
  been in Settings since V32; the payslip only used two of them. It now uses all three, and the demo
  seeds a legal name + address so a demo payslip looks like a document out of the box.
- **Validation matches the real formats:** IFSC (`ABCD0123456`), UAN (12 digits), PAN
  (`ABCDE1234F`) — a malformed value is a `400`, not a silently stored typo.
- 8 integration tests covering masking, cross-employee refusal, the statutory-edit rule, PAN
  re-verification and the enriched payslip.

### 2026-08-09 — Live QA pass on the hosted deployment: fix the signup blocker + 5 bugs
**A full QA sweep against `calyvora-frontend.onrender.com` found the product working but the front
door jammed: nobody could complete a signup. This is what was wrong, and why each fix is shaped the
way it is — recorded in full because every one of these was invisible from the code alone.**

Scope of the sweep: 42 API endpoints exercised live as four roles, plus write journeys (leave
request → approval → balance, check-in, helpdesk ticket, AI assistant). 41/42 passed. Tenant
isolation, salary privacy, role gates and anonymous access were probed directly and all held.

#### 1. `[P0]` Verification email could never be delivered — signup was unrecoverable
- **Symptom:** `POST /auth/register` returned `201`, the UI said "Check your email", no mail ever
  arrived, and login then returned `403 Please verify your email`. The account was permanently stuck.
  There was no way out: `/dev/mailbox` is registered only under the `embedded` profile, so it does not
  exist on a deployed `staging` build.
- **Root cause:** **Render blocks outbound SMTP on every port.** `smtp.hostinger.com:587` failed with
  `SocketTimeoutException: Connect timed out`. Credentials were correct — the packets never left the
  host. `SmtpEmailService` swallowed the error by design (a mail outage must not roll back a completed
  signup), which is right, but it made a total mail failure indistinguishable from success.
- **Fix — the email layer is now transport-pluggable.** `EmailSender` implementations
  (`ResendSender` over HTTPS :443, `SmtpSender`, `ConsoleSender`) are selected per send from an
  `EmailSettings` resolved by an `EmailSettingsResolver`. `MAIL_PROVIDER` pins the transport;
  unset, it is inferred (Resend key → Resend, SMTP username → SMTP, neither → console).
  **Use Resend on any hosted deployment — port 443 is never blocked.**
- **A silent misconfiguration is no longer possible.** With nothing configured the app logs a loud
  warning at startup, and `POST /dev/test-email` reports `sent: false` with an explanation rather than
  claiming success for a message that was only written to a log.
- **Why not just try port 465:** worth a 2-minute test, but it is the same gamble on a blocked port.
  HTTPS removes the class of failure instead of moving it.
- **Per-tenant sending is now a one-resolver job.** Nothing else — no transport, no caller — knows
  tenants exist, so "each customer sends from their own domain" drops in without a rewrite.

#### 2. `[P0]` Signup claimed success for mail that was never sent
- **Symptom:** the "Check your email" screen appeared unconditionally, so the user waited for a
  message that did not exist.
- **Fix:** sends return an `EmailResult`; `POST /auth/register` answers `{"emailSent": true|false}`
  and the signup screen shows either "Check your email" or "Workspace created — but we couldn't send
  the email" with a **Resend** button. The account is still created either way: a mail outage must
  never lose a completed signup.

#### 3. `[P1]` Logged-out visitors saw ~25 app pages instead of the login screen
- **Symptom:** `/payroll`, `/billing`, `/platform`, `/expenses` and others returned `200` with an
  empty shell to a stranger; only `/dashboard`, `/people`, `/work`, `/members`, `/settings` redirected.
- **Root cause:** `middleware.ts` listed *protected* prefixes, so every route added since was
  unguarded by default. No data leaked — the API correctly returns `401` — but it looked broken.
- **Fix:** inverted to an allow-list of **public** paths. A new page is now protected the moment it
  exists rather than when someone remembers to add it.

#### 4. `[P2]` Any employee could read every colleague's performance rating
- **Symptom:** `GET /people/employees` returned `rating` on every record to any authenticated user.
  Salary was already protected (compensation is self-only, `403` for others), so this was an oversight
  rather than a decision.
- **Fix:** `RatingVisibility` strips the rating at the API boundary — visible to HR/leadership, to the
  person themselves, and to their manager. Applied to both the list and paged endpoints; internal
  callers (analytics, team overview) keep the full record. Four regression tests.

#### 5. `[P2]` Unknown API routes returned `500`, not `404`
- **Symptom:** `GET /api/v1/anything-misspelled` → `500 INTERNAL_ERROR "Something went wrong"`. A typo
  was indistinguishable from a server crash — this cost real time during the QA sweep itself.
- **Root cause:** `NoResourceFoundException` fell through to the catch-all `Exception` handler.
- **Fix:** explicit handlers for `NoHandlerFoundException` / `NoResourceFoundException` → `404`, and
  `HttpRequestMethodNotSupportedException` → `405`. (Anonymous callers still get `401` before routing
  — deliberate, so strangers can't enumerate which endpoints exist.)

#### 6. `[P2]` Production users were told to open the local-dev mailbox
- **Symptom:** the signup success screen hardcoded "open `/dev/mailbox`" — a development instruction
  shown to real customers, pointing at a page that cannot work on a deployment.
- **Fix:** shown only in mock/development builds.

#### 7. `[P2]` Documentation defects
- `docs/DEPLOY.md` listed the HR demo login as `leo.martin@northwind.demo`; the seeder creates
  `leo.martins@` (with an *s*) — the documented one returned `401`. Corrected.
- A stray `/m` typo in the `JWT_PRIVATE_KEY` table row. Removed.
- The email section is rewritten around Resend, with the SMTP-is-blocked warning first, a transport
  comparison table, and the real error strings each misconfiguration produces.

#### Still open — configuration, not code
- **JWT keys are not set on Render.** Tokens come back signed with `kid: dev-…`, the ephemeral-keypair
  prefix, so **every restart logs all users out** — and free-tier services restart whenever they wake
  from idle. Set `JWT_KID` / `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY`; the startup log should read
  `ephemeral=false`.
- **Cold start is ~81 seconds** on the free tier, during which the Next.js proxy gives up and returns
  a bare `500`. Wake the site before any demo, or move to the always-on starter plan.

### 2026-07-27 — Deployment (Render blueprint) + "Orbit by Calyvora" marketing site
**Make the app hostable for real testing, and give it a landing page.**

- **One-click hosting.** `render.yaml` blueprint provisions Postgres + backend (Docker) + frontend
  (Node) wired together; `docs/DEPLOY.md` is a first-timer walkthrough (Render / Railway / Hostinger VPS).
- **`staging` + `prod` profiles.** New `application-staging.yml` (hosted testing — hardened but seeding
  stays on) and `application-prod.yml` (real customers — seeding + Swagger off). Backend + frontend
  `Dockerfile`s; Next.js emits a `standalone` bundle.
- **Tenant-isolation guard.** `TenantIsolationVerifier` refuses to start in a hosted profile if the DB
  role can bypass Row-Level Security (superuser/BYPASSRLS) — prevents a silent cross-tenant data leak.
- **Flexible DB config.** Datasource assembles from `DB_HOST/DB_PORT/DB_NAME` when no full `DB_URL` is
  given, so managed databases wire in without hand-editing a JDBC URL.
- **Marketing site.** `website/orbit/index.html` — a polished, self-contained landing page for **Orbit
  by Calyvora** on the product palette, with the feature story mapped to the real shipped modules.

### 2026-07-27 — Payroll ↔ attendance, payroll run, payslip branding, regularization
**Make pay reflect real attendance, and give employees a way to fix missed punches.**

- **Attendance → payslip (LOP).** A month's payslip now factors unpaid absence: absent days (½ for a
  half-day) become a **loss-of-pay** deduction (per-day = monthly gross ÷ working days), reducing net;
  the payslip shows payable-vs-working days.
- **HR payroll run** (`GET /api/v1/payroll/run`, Owner/Admin/HR): every employee's gross/LOP/net for a
  month with totals + a "publish payslips" step (`/payroll/run`).
- **Payslip branding** (Flyway V32): a company legal name + address (Settings → Payslip branding) print
  on the payslip header.
- **Attendance regularization** (Flyway V33): an employee who forgot to clock in raises a fix-up for a
  past day (in/out + reason); their **manager** (or HR/admin) approves in a **Regularizations** queue,
  which writes the attendance record. Notifications both ways. Managers now have an approval surface.

### 2026-07-27 — HR Helpdesk (employee case management)
**Roadmap #2 — the employee-facing "raise a request to HR and track it" module (Keka helpdesk).**

- **HR Helpdesk (Flyway V31, RLS).** `com.calyvora.helpdesk`: employees raise tickets
  (category HR/Payroll/IT/Facilities/Other · priority · subject · description) and track them; HR
  agents (ADMIN/HR) run a **queue** with status filter, **assign** (incl. "assign to me"), move
  **OPEN → IN_PROGRESS → RESOLVED → CLOSED**, and reply in a **threaded conversation**. Notifications
  fire to agents on raise and to the raiser on reply/status change. A ticket is visible only to its
  raiser and to agents. New **Helpdesk** nav item for everyone; `/helpdesk` (raise + my tickets + HR
  queue) and `/helpdesk/[id]` (thread). Demo seeds three tickets across statuses with a reply and a
  resolution. Integration-tested.

### 2026-07-27 — Multi-tenant SaaS: platform owner, roles, subscriptions + demo fixes (PD-10)
**The founder's 8-point spec — Priority HR becomes a true SaaS with a vendor above the companies.**

- **Platform-owner console (Flyway V30).** A new `OWNER` = the vendor above all companies
  (`com.calyvora.platform`). `/api/v1/platform/**` (OWNER only): list every company with headcount,
  seats, subscription status and end date; **create a company + its first ADMIN**; **end a
  subscription** (locks that company's app) or reactivate/extend; set seats. Companies, users and
  subscriptions are not RLS-isolated, so the owner reads across tenants without any bypass;
  `subscriptions` + `company_settings` RLS is lifted (platform-managed / app-filtered).
- **Roles — ADMIN · HR · MANAGER · MEMBER** (OWNER is now platform-only). People-ops controllers admit
  HR; Members/Settings/Subscription stay ADMIN. Nav is gated per role; OWNER has no company app.
- **Subscriptions — Netflix-style seats.** Each company has a seat limit + end date. Admins get a
  **read-only** Subscription page (billing management removed) with an **expiry banner**, and can
  **request more seats** → the owner approves in the console → the limit bumps. When a subscription
  ends, the company app is covered by a **"your subscription has ended" lock**.
- **Check-in/out fixed + daily log** (the demo pre-filled today, disabling the button); **currency +
  timezone** app-wide (₹ default) with a Settings → Localization card; **payslip printing** fixed
  (prints a real black-on-white payslip instead of a blank page).
- **Demo:** `POST /api/v1/dev/seed-platform` provisions the platform owner + 5 varied sample companies
  (healthy, near-expiry, near seat-limit, a pending seat request, and one ended/locked). 158 backend
  tests green.

### 2026-07-27 — Shift scheduling / rostering
**Roadmap #1 from [docs/HR-Modules.md](docs/HR-Modules.md) — Keka/Zoho core for hourly & ops teams.**

- **Shift scheduling ⭐ (Flyway V28, RLS).** `com.calyvora.shift`: reusable **shift templates**
  (name + start/end time + colour) and a **weekly roster grid** (employees × 7 days). Assigning is an
  upsert — **at most one shift per employee per day**, so re-assigning a filled cell moves the person to
  the new shift rather than duplicating. New **Shifts** section with a templates manager and an
  inline-editable roster (colour-coded cells, week navigation). Owner/Admin-only; the demo seeds three
  shifts (Morning/Evening/Night) and rosters the support team across the current week. 3 integration
  tests (155 backend tests total).

### 2026-07-26 — Recruitment/ATS, directory pagination, and a marketing site
**Benchmarked against Keka / Zoho People / BambooHR (see [docs/HR-Modules.md](docs/HR-Modules.md)).**

- **Recruitment / ATS ⭐ (Flyway V27, RLS).** `com.calyvora.recruit`: **job openings** + a **candidate
  hiring pipeline** (applied → screening → interview → offer → hired / rejected). Each opening shows its
  candidate and hired counts; candidates carry rating, source and a résumé link. New **Recruitment**
  section with an openings list and a **pipeline board** (drag-free stage moves). Owner/Admin-only; the
  demo seeds two roles with candidates across the pipeline. This is the flagship module the big suites
  lead with, and the biggest gap we'd had.
- **Directory pagination & server-side search (scale to 1,000+).** `GET /people/employees/page` returns
  a paged, name/email-searched envelope; seat/page loads stay a cheap query instead of loading the whole
  company. The People screen now searches server-side with Prev/Next. (The full-list endpoint stays for
  pickers.)
- **`website/priority-hr-services/`** — a self-contained marketing site (hero, services, modules,
  pricing at ₹100/employee/month, contact). No build step; deploy the folder to any static host.
- **[docs/HR-Modules.md](docs/HR-Modules.md)** — a module inventory (what's shipped) and a prioritized
  roadmap (shifts, HR helpdesk, offboarding, PF/ESI compliance, assets, LMS…) drawn from the competitor
  benchmark.

### 2026-07-26 — `product/hr-platform`: sellable HR product (subscription billing + payslip template)
**One deployable HR-only branch. Full backend intact (non-HR just unlinked), so it deploys HR-only to any domain.**

- **Subscription billing — per active employee, per month (Flyway V26, RLS).** `com.calyvora.billing`:
  a company subscribes at **₹100/employee/month** (₹1,200/employee/year). The monthly charge is
  `price × active headcount`, **metered** — a company with 5 people in January and 20 in February is
  billed for 20 in February. `GET /billing` returns the plan, this month's charge, and a 6-month
  invoice history (each month priced on that month's headcount, derived from employee start dates);
  `POST /billing/activate` and `POST /billing/invoices/{month}/pay` move it through trial → active →
  paid. Seat counting is a `count` query, so it scales to thousands of employees. New **Billing** page.
- **Configurable payslip template (Flyway V25, RLS).** `com.calyvora.people` payslip components: each
  company defines its payslip as an ordered set of earnings/deductions (**% of gross**, **% of basic**,
  **fixed**, or **remainder**), seeded with a standard CTC breakdown. Payslip generation now reads the
  template instead of a hard-coded split. **Payroll → Payslip template** editor.
- **Payroll validation rules** (server + mirrored in the UI): percentages in 0–100, fixed amounts ≥ 0,
  exactly one *remainder* earning, one *basis* earning when a percent-of-basic deduction exists,
  earnings ≤ 100% of gross, and deductions that can't exceed gross (net pay ≥ 0).

### 2026-07-23 — `feature/hr-suite`: an HR-only product surface (demo branch)
**Branch only — presents Orbit as a focused HR suite. Adds self-service pay + a Payroll console.**

- **Curated to HR.** The left nav drops Work, Knowledge, Clients and Feed; what remains is the People
  side: Dashboard, Insights (People + Finance), Me, Inbox, People, Performance, **Payroll**, Expenses,
  Documents, Members, Settings. (The other modules' code and pages are untouched — just unlinked — so
  this stays a thin, reversible demo layer over `feature/orbit`.)
- **Salary & payslips, both sides.**
  - **Self-service (any employee):** `GET /people/me/compensation` and `/people/me/payslip` — new
    endpoints so a member sees *their own* salary, hike history and monthly payslip. New **Me → My pay**
    page (print-friendly).
  - **Admin Payroll console:** a `/payroll` page listing everyone's current salary with company totals,
    expanding per person to record a raise or pull a payslip (reuses the existing compensation panel).
- **HR-focused Dashboard & Insights.** The dashboard now leads with attendance, time off and your own
  day (not tasks/sprints/knowledge); Insights shows only the People and Finance charts.

### 2026-07-23 — Insights: a company analytics dashboard (charts across all three apps)
**2 new tests. Founder ask: "an analytics dashboard with charts/graphs — sprint burnouts and industry things."**

- **Analytics module (`com.calyvora.analytics`, Owner/Admin).** `GET /analytics/overview` returns
  chart-ready series reaching across **People, Work and Finance** — every figure computed from data we
  actually hold, so an empty company yields empty series rather than invented numbers:
  - **People:** headcount, **12-month headcount growth** (from employee start dates), headcount by
    department, **rating distribution**, approved **leave days by type**, goals (open/achieved/missed +
    avg progress), new joiners this year, average tenure, on-leave-today.
  - **Work:** tasks by status and by priority, tickets by status, the **active sprint** (committed /
    done / remaining / unestimated points), and **velocity** — completed story points per finished
    sprint (drawn from the tasks actually done in each).
  - **Finance:** expenses by category and the reimbursement pipeline (pending → approved → paid this year).
- **Charts, no library.** A small set of dependency-free, theme-aware **SVG** primitives
  (`components/charts`): donut, horizontal bar list, vertical bars (velocity), and an area/line trend
  (headcount). Keeps the strict-CSP artifact model and the bundle lean.
- **New "Insights" nav** (Owner/Admin) with a KPI strip + a grid of chart cards. Mock-backend parity so
  it works in offline mode too.

### 2026-07-23 — Performance review cycles (C.7) + Performance-tab fix
**4 new tests. Fixes the "something went wrong" the founder hit on the Performance tab.**

- **Bug fix — Performance tab 500.** The Me → Performance page called `GET /people/employees/me`,
  which the backend routed into `/employees/{id}` and tried to parse `"me"` as a UUID → 500
  "Something went wrong". The frontend now calls `GET /people/me`. (One-line path fix in `api.ts`.)
- **Managers can set goals for their downline.** `GoalService` previously let only an admin or the
  goal's owner edit goals; now a **reporting manager** can manage their direct reports' goals too —
  so a team lead who is a plain member can set goals for their people (founder request).
- **Review cycles (Flyway V24, RLS on `review_cycles` + `performance_reviews`).** An Owner/Admin opens
  a named cycle for a period; it **fans out one review per active employee**, snapshotting each
  person's manager at open time. The **member writes a self-assessment**; their **manager writes the
  review, a 1–5 rating, and a hike recommendation** (percent or a new salary); an **admin approves**,
  and approval **writes the raise straight into compensation history** — one auditable flow from
  "what they achieved" to the raise. Each review shows the person's **goals rollup** (achieved/total)
  and **current salary** as context for the rating.
  - Authorization is **by relationship**: a review is visible/editable by the employee (self side),
    their manager (manager side), or an admin — mirroring goals. Cycle admin (open/close/approve) is
    Owner/Admin-only.
  - New notification types (review started · self submitted · submitted for approval · approved) route
    to the right person at each step, reusing the D4/D5 inbox.
  - **UI:** **Me → Review** (self-assessment + your manager's verdict, plus a "My team's reviews"
    section for managers); a **Performance** hub for Owner/Admin (open a cycle, watch progress, expand
    to review and **approve & apply hike** per person). The demo seeds an in-flight cycle — one review
    awaiting the owner's approval, one already approved with the raise applied.

### 2026-07-23 — Company feed (E7) and sprint depth (E8)
**14 new tests (138 total).**

- **Feed (Flyway V22, RLS):** posts with **per-post visibility** — company-wide or one team — plus
  reactions (toggling: the same emoji twice removes it), comments, and Owner/Admin pinning. The
  visibility rule is enforced **on read**, not hidden in the UI: a team post is returned only to that
  team, its author, and admins. Visibility is stored **on the post**, not derived from the author's
  team, so moving department never retroactively changes who could see something. Post kinds
  (update · announcement · **celebration** · question) drive the icon; the demo seeds a pinned
  announcement, a birthday post, a question and a team-only update.
- **Sprint depth (Flyway V23):** **story points** on tasks, a **capacity** figure on sprints, and a
  **daily snapshot** table. `GET /work/sprints/{id}/report` returns commitment vs capacity (flagging
  over-commitment), completed/remaining, **how many tasks are unestimated** — a burndown lies if half
  the board has no numbers — the day-by-day burndown, and per-person load sorted heaviest-first.
  `GET /work/projects/{id}/velocity` averages **completed sprints only** and suggests the next
  commitment. New **Report** tab in the project workspace with an SVG burndown (actual vs ideal) and
  a velocity chart; points show on board cards.
- **Why snapshots:** a burndown computed from current state can only ever draw *today*. Remaining
  work is recorded once per sprint per day (and re-recorded on every board change), so the line
  reflects what actually happened.

### 2026-07-23 — Notifications & Inbox (D4/D5), holiday calendar, the **Me** hub, and expense claims
Four modules from the founder's 2026-07-22 session, built in one pass. **19 new tests (124 total).**

- **Notifications + Inbox (D4/D5, Flyway V19, RLS).** A leave request now routes to **the requester's
  manager** — or every Owner/Admin when nobody manages them, because a request with no approver would
  sit unseen. Decisions route back to the requester, and a goal set by a manager notifies the employee.
  Two rules: we never notify someone about **their own action**, and the text is **frozen at send time**
  so an entry still reads correctly after the thing it points at changes. Header **bell** with an unread
  badge (polls the cheap count endpoint, fetches the list only when opened) + a full `/inbox`.
- **Holiday calendar (Flyway V20, RLS).** Readable by everyone, editable by Owner/Admin, with a
  one-click starter calendar. A holiday **fills everyone's attendance day automatically** (optional
  holidays don't), which closes the "no holiday list" debt logged when attendance shipped. New
  **What's coming up** dashboard card merges upcoming holidays with your own leave.
- **The Me hub.** A left-pane **Me** section — Overview · Attendance · Time off · Performance ·
  Expenses — so anyone, whatever their role, has one place for their own stuff. The attendance and
  leave pieces are **shared components**, not copies: People → Attendance and Me → Attendance run the
  same code, and People → Time off is now the shared self-service view plus the approvals queue.
- **Expense claims (Flyway V21, RLS).** Submit a claim (travel, meals, supplies…), it routes to your
  manager, and **approval and payment are separate states** on purpose — "approved but not yet paid"
  is exactly what people chase, so it stays visible until money moves. A claim can only be edited while
  still awaiting a decision; changing the amount afterwards would make the decision a lie. Owner/Admin
  get an **Expenses** queue with running totals.
- **Fixed:** the team day sheet and check-in returned nothing/404 for a company whose employee profiles
  hadn't been lazily provisioned yet. `day()` also had to stop being `readOnly` — a read-only
  transaction silently swallowed the provisioning inserts without flushing them.

### 2026-07-22 — Founder feedback: C.4 / B6 phase 2 — **daily attendance**
Phase 1 inferred "present vs on leave" from approved leave. This is the real record: one row per
employee per day (`attendance_records`, Flyway **V18**, RLS, unique on employee+day). **10 new tests
(105 total).**

- **Two rules keep it from becoming double data entry:** approved leave **auto-fills** the day (so time
  off is never entered twice) and a **marked row always wins** over anything derived. Weekends resolve
  to week-off; a day nobody marked reports as *unmarked* rather than inventing a value.
- **Self-service:** `POST /people/attendance/me/check-in` (idempotent — clocking in twice doesn't move
  the time) and `/check-out`, plus `GET /people/attendance/me?month=`.
- **Owner/Admin:** `GET /people/attendance/day?date=` is the team sheet; `POST /people/attendance/mark`
  marks or corrects anyone's day (future dates rejected); `GET /people/attendance/employees/{id}?month=`.
- **Month summary** counts worked days (half days as 0.5) over *expected* days — holidays, week-offs and
  future dates excluded — so the attendance % means something.
- **UI:** People → **Attendance** sub-pane. Check in/out card, team day sheet with one-click marking,
  and a month grid. Counts are **clickable drill-downs** ("on leave" → exactly who), with a **per-team
  breakdown**. Demo seeds 14 days with a believable mix of WFH, a half day and an absence.
- Dashboard tiles now link into the day sheet and disclose how many of "present today" are merely
  unmarked — the number is honest about what's recorded vs assumed.

### 2026-07-22 — Clients restricted to Owner/Admin
The customer list, contacts and commercial asks were visible to every member. `ClientController` is now
role-gated, the sidebar tab is hidden for members, and **global search filters Clients and Documents for
non-admins** so the search box can't become a side door around the role gate. Test-covered.

### 2026-07-22 — Work & Knowledge get left-pane sub-panes
Matching the People pattern the founder asked for: **Work → Projects / My work** and **Knowledge →
Spaces / My pages** now live in the left pane, and the on-page "My work →" / "My pages →" links and
back-links were removed.

### 2026-07-22 — Founder feedback: Bucket D2 + D3 — **Documents & templates**
"Fill in a name → a proper document is generated." A new `com.calyvora.document` module: a per-company
**template library** and the letters generated from it. **12 new tests (95 total), all green.**

- **Schema** (Flyway **V17**, both RLS-protected): `document_templates` (name, kind, body with
  `{{merge.fields}}`, `built_in`) and `generated_documents` (frozen rendered body, kind, employee, issuer).
- **Starter library** seeded per company on first open — **offer · joining · relieving · experience ·
  promotion** — and fully editable afterwards, because a company's letters should read like theirs. We
  never overwrite an edited template.
- **Merge engine** (`MergeFields`, pure + unit-tested): named substitution only — no expressions. Values
  resolve from the People profile (name, employee ID, title, department, **manager**, location, dates,
  computed **tenure**) plus **compensation** and company/signatory context. A field with no value renders
  as `—`, never a leftover `{{token}}`.
- **Generation** — `POST /api/v1/documents/preview` is a dry run that reports **which fields came back
  empty** so gaps are fixed *before* a letter goes out; `POST /api/v1/documents` issues it. Caller
  `overrides` win over derived values, so the issuer can always correct what the profile got wrong.
- **Issued letters are frozen** at generation time: editing a template afterwards cannot rewrite a
  document someone already signed. (Covered by a test.)
- **API**: `/api/v1/documents/templates` CRUD, `/documents/fields` (merge-field catalogue),
  `/documents/preview`, `/documents` (issue/list, `?employeeId=`), `/documents/{id}` (get/delete).
  Whole surface is **Owner/Admin-only** — these letters carry salary and exit details.
- **UI**: left-pane **Documents** section with sub-panes **Issued / Generate / Templates**. Generate shows
  a live paper-like preview, flags unfilled fields with inline override inputs, and issues in one click.
  The template editor inserts merge fields at the cursor and previews with sample values. A letter view
  offers **Print / PDF** (print CSS isolates the sheet) and copy. Employee profiles gain a **Documents**
  section listing their letters + a "Generate" deep link.
- **Global search** now returns issued documents; the demo seeds two joining letters, plus employee
  numbers and reporting lines so the generated letters read complete.

### 2026-07-22 — Product named **Orbit** (by Calyvora)
The product now has its own name — **Orbit** — with **Calyvora as the parent company**. Central
`frontend/src/lib/brand.ts` drives a `Wordmark` ("Orbit by Calyvora") across the sidebar + auth pages;
page `<title>` updated. Switching the name later is a one-line change.

### 2026-07-22 — Founder feedback: Bucket C.1 — salary, hikes & payslips (Owner/Admin)
Compensation for People OS. `compensation_records` (Flyway **V13**, RLS-protected) stores point-in-time
salary; the latest is current pay. `GET/POST /api/v1/people/employees/{id}/compensation` returns current
pay + full history with per-record **hike %** ("how much hike we've provided"); `GET .../payslip?month=`
generates a payslip (basic/HRA/special · PF/tax · net) from current salary. All Owner/Admin-only. Employee
detail gains a **Compensation** section (salary, hike-history badges, add-raise, payslip w/ month picker).
Demo seeds an initial salary + review hike per employee. 3 new tests.

### 2026-07-22 — Founder feedback: Bucket B — role-based dashboard + team overview
Dashboard is now role-aware: company KPIs + a **Team overview** are Owner/Admin-only (members get the
personal view). `GET /api/v1/dashboard/team` returns total employees, present vs on-leave today (**derived
from approved leave** — attendance phase 1), who's out + reason, and month leaves for a **leave calendar**.
2 new tests.

### 2026-07-22 — Founder feedback: Bucket A — quick wins + members bug
- **Left sidebar navigation** ("put all tabs on the left") with icons + active state; mobile fallback.
- **Searchable member picker** (`MemberSelect`) replaces the plain assignee dropdown on tasks & tickets —
  filters all company members, usable at ~1k people (fixes "members dropdown not working").
- **Always-on Knowledge search** — reusable `KnowledgeSearch` on the index **and** inside every space.
- **Selectable sprint types** (1 week / 2 weeks / 1 month / custom) auto-fill the sprint end date.

### 2026-07-21 — Light/dark theming
The frontend was dark-only. Added semantic color tokens (`--app`/`--surface`/`--fg`) as CSS variables
wired into Tailwind via `rgb(var(--x) / <alpha-value>)`, so every opacity modifier keeps working, and
migrated all 28 components off hardcoded `text-white`/`bg-white`/`bg-ink`. Dark stays default; a light
theme flips to a soft off-white canvas with crisp white surfaces. Sun/moon toggle in the header + auth
pages, persisted in localStorage, with a no-flash script so light users never see a dark flash. White
text is preserved on accent buttons for contrast. `next build` + `tsc` clean.

### 2026-07-21 — Demo experience: seed data, command center, global search, AI assistant
Four features to make the product demoable — a client should open onto a living, intelligent product.
**8 new backend tests** (69 total), full suite green; frontend typechecks clean.

- **One-click demo seed** (`POST /api/v1/dev/seed-demo`, disabled in prod): provisions "Northwind
  Robotics" — 6 employees across 4 departments, an Atlas project with an active sprint (tasks spread
  TODO/IN_PROGRESS/DONE) + backlog + 3 tickets, and an Engineering Handbook whose pages link back to
  Work tasks. An "Explore the live demo" button on the login page seeds + signs in in one click.
- **Cross-app command-center dashboard**: one summary call spanning People/Work/Knowledge KPIs + active
  sprint progress, composed with the user's open tasks and recent pages.
- **Global ⌘K search** (`GET /api/v1/search`): people, projects, tasks, tickets, spaces, and pages in
  one grouped, tenant-scoped result; a debounced command palette in the app header.
- **Cross-app AI assistant** (`POST /api/v1/assistant/ask`): plain-English questions answered from the
  tenant's own data (RAG). Offline grounded provider by default (never fabricates); upgrades to Claude
  automatically when `ANTHROPIC_API_KEY` is set. A floating "Ask AI" panel with grounded page sources.

### 2026-07-21 — Foundation hardening: Postgres Row-Level Security (SD-2a..d)
Added a **database-enforced** tenant-isolation layer beneath the app's `TenantContext` checks, so a
missed `company_id` filter (or an injection) can no longer leak another tenant's data. **1 new test**
(61 total) that drops to a NOSUPERUSER role and proves the DB itself blocks cross-tenant reads/writes.

- **RLS on all 11 tenant tables** (`company_settings`, People/Work/Knowledge domain tables), `ENABLE`
  + `FORCE`, each with one `tenant_isolation` policy — Flyway `V12`. Auth-surface tables (users,
  invitations, companies, token tables) are excluded: they're queried before a tenant is bound.
- **Per-connection tenant binding.** `TenantAwareDataSource` sets the Postgres session GUC
  `calyvora.company_id` from `TenantContext` on every borrowed connection; unset ⇒ NULL predicate ⇒
  **deny-by-default** (a connection with no bound tenant sees nothing).
- **Operational requirement:** the app's DB role must be `NOSUPERUSER` without `BYPASSRLS` in shared
  environments (superusers bypass RLS by design) — documented in V12 and `application.yml`.

### 2026-07-21 — Foundation hardening: RS256 JWT signing + key rotation (SD-5a/SD-23/SD-24)
Replaced the HS256 shared-secret access token with **RS256 asymmetric signing** and a rotation-ready
key set. Verifiers now need only the public key — there's no shared signing secret to leak.
**7 new backend tests** (60 total); full suite green; no change to the token's claims or the login flow.

- **RS256 signing.** `JwtKeyStore` holds the keys; the active key's `kid` is stamped into the JWS
  header, and verification selects the matching public key by `kid`. HS256 removed entirely.
- **Key rotation.** One active signing key, every configured key trusted for verification — so a new
  key can be published and made active while tokens signed by the retiring key still verify until they
  expire (zero downtime). Configured via `calyvora.security.jwt.{active-kid,keys}` (PKCS#8 PEM).
- **JWKS discovery.** Public keys are served at `GET /.well-known/jwks.json` (RFC 7517), public and
  unauthenticated, so any peer verifies Calyvora tokens without out-of-band key sharing.
- **Dev ergonomics.** With no keys configured, an ephemeral RSA keypair is generated at startup
  (local/dev/test) with a loud warning — a shared environment must supply real PEM keys.

### 2026-07-20 — Work OS depth (Sprint 5): Sprints, Backlog, Tickets + workspace layout
Turned the single Work board into a **project workspace** with a left-pane nav (Board · Backlog ·
Sprints · Tickets). Backend `com.calyvora.work`, Flyway `V10`/`V11`. **5 new integration tests**
(58 total), incl. cross-tenant isolation on sprints & tickets; verified live end-to-end.

- **S1 — Sprints.** `Sprint` (name/goal/dates/status PLANNED→ACTIVE→COMPLETED). CRUD +
  `POST /work/sprints/{id}/start|complete`. **≤1 active sprint per project** (partial unique index);
  completing a sprint **returns unfinished tasks to the backlog**. Tasks gained `sprint_id`.
- **S2 — Backlog.** `GET /work/projects/{id}/backlog` (un-sprinted tasks); `GET .../board` returns the
  active sprint + its tasks, or the backlog if no sprint is active. "Move to sprint" from the backlog.
- **S3 — Support Tickets** (lightweight, in Work — graduates to Service OS, SD-22b). `Ticket`
  (subject/requester/status OPEN→CLOSED/priority, ref `KEY-T{n}`), assignee = People `Employee`.
  `GET/POST /work/projects/{id}/tickets`, `PATCH/DELETE /work/tickets/{id}`.
- **UI:** `/work/{projectId}` is now a left-pane workspace — Kanban board (sprint-aware), backlog list
  with move-to-sprint, sprint management (start/complete/delete), and a tickets list with editor.

### 2026-07-20 — Demo hardening: invite flow works without email
- **Fix:** `GET /api/v1/invitations/preview` was not in the public-endpoints list, so the
  invite-accept page 401'd for a logged-out invitee ("invitation is invalid"). Made it public.
- **Dev mailbox in the app.** Under the `embedded` profile the backend now captures verification /
  invite links in an in-memory `DevMailbox` and serves them at `GET /api/v1/dev/mailbox` (public,
  dev-only). The existing `/dev/mailbox` page now works in **live** mode too — no SMTP needed. The
  Members → invite dialog shows a success step pointing there. Full flow verified end-to-end:
  invite → open dev mailbox → accept link → set password → member logs in.

### 2026-07-20 — Knowledge OS (Sprint 4): the third app completes the trio
Built Knowledge OS (a docs/wiki) full-stack on the foundation, completing the **People / Work /
Knowledge** trio. Backend `com.calyvora.knowledge`, Flyway `V8`/`V9`, frontend
`frontend/src/app/(app)/knowledge/*`. **6 Knowledge OS integration tests** (48 total), each with a
cross-tenant isolation check; verified live end-to-end against the real backend.

- **K1 — Spaces.** `Space` (name, KEY, description, status). `GET/POST /knowledge/spaces`,
  `GET /knowledge/spaces/{id}`, `PATCH /knowledge/spaces/{id}`, `POST .../archive` (OWNER/ADMIN).
  Unique KEY per company. UI: `/knowledge` space cards + create dialog.
- **K2 — Pages & editor.** `Page` (Markdown `body`, status DRAFT/PUBLISHED, `parent_id` tree).
  `GET/POST /knowledge/spaces/{id}/pages`, `GET/PATCH/DELETE /knowledge/pages/{id}`. UI:
  `/knowledge/{spaceId}` two-pane page tree + Markdown editor/reader with publish toggle.
- **K3 — Cross-app links.** A page's **author is a People `Employee`** (auto-provisioned via the
  new `EmployeeService.ensureEmployeeId`) and a page may **link a Work `Task`** (`linkedTaskRef`,
  e.g. `PLT-1`). Proves the **task ↔ doc ↔ person** graph. UI: "Link a Work task" picker.
- **K4 — Search.** `GET /knowledge/search?q=` — tenant-wide title/body search with a snippet. UI:
  debounced search box on `/knowledge`.
- **K5 — My pages.** `GET /knowledge/pages/mine`. UI: `/knowledge/mine`.
- Nav gains **Knowledge**. Decisions **SD-13…SD-17** logged; **PD-06** in the journal.

### 2026-07-10 — Work OS (Sprint 3): the second app + cross-app proof
Built Work OS (projects + tasks) full-stack on the foundation. Backend `com.calyvora.work`, Flyway
`V6`/`V7`, frontend `frontend/src/app/(app)/work/*`. **5 Work OS integration tests** (42 total),
each with a cross-tenant isolation check; verified live in the browser.

- **W1 — Projects.** `Project` (name, KEY, description, status, lead). `GET/POST /work/projects`,
  `GET/PATCH /work/projects/{id}`, `POST .../archive` (OWNER/ADMIN). Unique KEY per company; KEY prefixes
  task refs (e.g. `PLT-1`). UI: `/work` project cards + create dialog.
- **W2 — Tasks + Kanban board.** `Task` (status TODO/IN_PROGRESS/DONE, priority LOW–URGENT,
  **assignee = People `Employee`**, due date, per-project number). `GET/POST /work/projects/{id}/tasks`,
  `PATCH/DELETE /work/tasks/{id}`. UI: `/work/{projectId}` board with columns, move controls, create/edit/
  delete, assignee/priority/due.
- **W3 — My work.** `GET /work/tasks/mine` (open tasks assigned to me across projects). UI: `/work/mine`.
- **Cross-app link proven:** a Work task's assignee is a People OS employee — the shared org graph works.

### 2026-07-10 — People OS (Sprint 2): the first full app
Built the People OS HR module full-stack on the Sprint-1 foundation. Backend package
`com.calyvora.people`, Flyway migrations `V2`–`V5`, frontend under `frontend/src/app/(app)/people/*`.
**18 People OS integration tests** (37 total), each slice with a cross-tenant isolation check; all
verified live in the browser.

- **P1 — Employee directory & profiles.** `Employee` 1:1 with `User`, auto-provisioned. Endpoints:
  `GET /people/employees`, `GET /people/employees/{id}`, `GET/PATCH /people/me`,
  `PATCH /people/employees/{id}` (admin). UI: `/people` directory with search, cards, edit.
- **P2 — Departments & org chart.** `Department` (name/parent/lead). CRUD at `/people/departments`;
  employee department + manager assignment; `/people/org` with department management and a reporting tree.
- **P3 — Onboarding checklists.** Per-employee tasks: `GET/POST /people/employees/{id}/onboarding`,
  `POST .../seed-defaults`, `PATCH /people/onboarding/{taskId}` (self or admin), `DELETE` (admin). UI in
  the employee detail drawer with progress + defaults.
- **P4 — Time-off / leave.** `POST /people/leave`, `GET /people/leave/mine`, `GET /people/leave/balance`,
  `GET /people/leave` (admin inbox), approve/reject/cancel. Vacation balance (allowance/used/pending/
  remaining). UI: `/people/time-off` with request form, my requests, admin approvals.
- **P5 — Self-service.** Employee detail drawer (full profile + onboarding), self profile edit, self leave.

### 2026-07-10 — Sprint 1 (Platform Foundation): backend implemented & verified
- Full Spring Boot backend on Java 21 + Maven: registration, email verification, login/refresh/logout/me
  (JWT HS256 + rotating refresh cookie with **reuse detection**), dashboard, company/settings/members,
  invitation lifecycle (create/list/revoke/preview/accept).
- **19 integration tests** on real embedded Postgres (Zonky, no Docker), including the **cross-tenant
  isolation merge gate**. Runnable locally via the `embedded` Spring profile.
- Fixed: refresh-token rotation revoked the whole family on a duplicate/concurrent refresh
  (StrictMode / two tabs) — guarded with a one-shot session bootstrap on the client and a
  `REQUIRES_NEW` transaction for reuse-revocation on the server.

### 2026-07-09 — Sprint 1 scaffolding + foundation + frontend
- Monorepo: `backend/` (Spring Boot, **Maven**), `frontend/` (Next.js + TS + Tailwind), `infra/`
  (Docker Compose), CI. Backend foundation (TenantContext, stateless-JWT security, error envelope,
  Flyway baseline, OpenAPI). Entire Sprint-1 frontend built and verified against a contract-faithful
  in-browser mock backend.

_Decisions are logged in [DECISIONS.md](DECISIONS.md); narrative in [FOUNDER.md](FOUNDER.md)._
