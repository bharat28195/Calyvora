# Changelog

All notable changes to Calyvora. Newest first. Dates are absolute (ISO `YYYY-MM-DD`).

## [Unreleased]

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
