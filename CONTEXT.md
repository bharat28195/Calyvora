# CONTEXT.md — Calyvora build state & resume guide

> **Purpose:** the single file to read first when resuming work. Captures where we are, how to run
> everything on this machine, decisions made, and what's left. Keep it updated as work progresses.
> Binding architecture = [/docs](docs/README.md). Narrative/decisions = [FOUNDER.md](FOUNDER.md) /
> [DECISIONS.md](DECISIONS.md). This file = practical "how to pick up where we left off."

**Last updated:** 2026-07-22 · **Branch:** `feature/orbit` · **Product name: "Orbit" (by Calyvora — parent co.).**

**Current focus — Founder feedback buckets (see [docs/Founder-Feedback-Backlog.md](docs/Founder-Feedback-Backlog.md)):**
Working through the founder's 8-page handwritten notes (2026-07-22). Done so far: **Bucket A** (quick wins:
searchable member picker, always-on Knowledge search, selectable sprint types, **left sidebar nav**, wordmark),
**branding** (product = **Orbit**), **Bucket B** (role-based dashboard + team overview: headcount, present vs
on-leave, leave reasons, **leave calendar** — attendance *derived from leave*, phase 1), **Bucket C.1**
(**salary + hike history + payslips**, Owner/Admin-only, V13). **74 backend tests, all live & verified.**
**Next:** C.2 richer employee profile (working-on / start-end / skills / ratings) → C.3 performance+goals →
C.4 full daily attendance (phase 2) → Bucket D (Clients ⭐, Documents+templates, Notifications, Inbox).

**Prior state:** Phase-1 trio (People/Work/Knowledge) + Work OS depth + **foundation hardening** (RLS SD-2,
RS256 SD-5) + **demo suite** (one-click seed, cross-app dashboard, ⌘K global search, AI assistant) +
**light/dark theming**. Cross-app graph proven (task ↔ doc ↔ person).

---

## 0. TL;DR resume steps
Env for backend (JDK + Maven installed but NOT on PATH):
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
$env:PATH = "$env:JAVA_HOME\bin;$env:USERPROFILE\.calyvora-tools\apache-maven-3.9.9\bin;$env:PATH"
```
(Committed wrapper `backend/mvnw` also works — only-script, downloads Maven 3.9.9.)

**Run the whole app locally (no Docker needed):**
1. Backend (embedded Postgres, prints email links to console):
   ```powershell
   cd backend; mvn -q -DskipTests package
   java -jar target\calyvora-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=embedded
   ```
   Health: http://localhost:8080/actuator/health · Swagger: http://localhost:8080/swagger-ui.html
2. Frontend against the live backend: `frontend/.env.local` has `NEXT_PUBLIC_API_MODE=live`; then
   `cd frontend && npm run dev` → http://localhost:3000. (Delete `.env.local` to use the mock instead.)
3. Verification/invite links print in the backend console (ConsoleEmailService, embedded profile).

**Tests:** `cd backend; mvn test` (19 tests, real embedded Postgres, no Docker) · `cd frontend; npm test`.

See §4 for status and §5 for next actions.

---

## 1. What Calyvora is
AI-Native Enterprise OS. Phase 1 = 3 apps (People/Work/Knowledge OS) on a shared Foundation.
**Sprint 1** builds the Foundation spine (tenancy, identity, auth, RBAC, invitations, app shell) —
NOT a business module. Full plan: [docs/Sprint1.md](docs/Sprint1.md).

## 2. Repo layout (monorepo)
| Path | What | State |
|------|------|-------|
| `backend/` | Spring Boot 3 · Java 21 · **Maven** | Foundation + domain layer done; services/controllers/tests in progress |
| `frontend/` | Next.js 14 · TS · Tailwind | **ALL Sprint-1 screens built + verified live** (against mock) |
| `infra/` | docker-compose (Postgres+Mailpit) | written; unused (no Docker on this machine) |
| `web/` | static marketing site (calyvora.in) | separate, leave as-is |
| `docs/` | constitution 00–15 + Sprint1 plan | done |

## 3. Toolchain on THIS machine (important constraints)
- ✅ Node 24 / npm 11 — frontend runs & verifies locally.
- ✅ JDK 21 at `C:\Program Files\Java\jdk-21.0.11` (installed 2026-07-10; NOT on PATH — set JAVA_HOME).
- ✅ Maven 3.9.9 at `C:\Users\ramaw\.calyvora-tools\apache-maven-3.9.9` (downloaded; NOT on PATH).
  Also `backend/mvnw` committed (only-script wrapper).
- ❌ **No Docker, no native Postgres.** → Testcontainers can't run. Plan: use **Zonky embedded
  Postgres** (real Postgres binary, no Docker) for integration tests, and for local `run`.

## 4. Current status (2026-07-10)
### Frontend — DONE & VERIFIED
All screens built and walked end-to-end in the browser: landing, register, verify-email, login,
accept-invite, dashboard, members+invite dialog, settings, `/dev/mailbox`. Golden path verified:
register → verify → login → invite → accept → active member; RBAC nav gating; logout; settings save.
7 Vitest tests pass; `next build` clean (10 routes + middleware).
- Runs against an in-browser **mock backend** (`frontend/src/lib/mock/backend.ts`, localStorage).
- Switch to real backend: env `NEXT_PUBLIC_API_MODE=live` (client already targets `/api/v1/*`,
  proxied to :8080 via `next.config.mjs`).

### Backend — DONE & VERIFIED ✅
- Foundation (`common/`), domain layer (6 entities + enums + repos), and ALL feature code:
  `AuthService`/`AuthController` (register/verify/resend/login/refresh/logout/me),
  `RefreshTokenService` + `RefreshTokenRevoker` (rotation + reuse detection via REQUIRES_NEW),
  `CompanyService`/`CompanyController` (company/settings/members), `DashboardService/Controller`,
  `InvitationService`/`InvitationController` (create/list/revoke/preview/accept),
  `EmailService` (`SmtpEmailService` for Mailpit; `ConsoleEmailService` for the embedded profile).
- **19 tests green** (`mvn test`) on real embedded Postgres (Zonky, no Docker): auth flow, invitations,
  utils, and the **cross-tenant isolation merge gate** (SD-2) + refresh reuse detection.
- **Ran live** (embedded profile) and **verified in the browser**: register→verify→login→dashboard
  against the real backend (Initech LLC demo), httpOnly refresh cookie, RBAC nav. Frontend `api.ts`
  live mode confirmed end-to-end.
- Key runtime notes: `EmbeddedPostgresConfig` (@Profile embedded) boots throwaway PG; mail health
  indicator disabled (`management.health.mail.enabled=false`); mail send failures are swallowed.

## 5. Sprint 2: People OS (the first full app) — ✅ COMPLETE
Plan: [docs/Sprint2-PeopleOS.md](docs/Sprint2-PeopleOS.md). All five vertical slices shipped
full-stack (`com.calyvora.people`, Flyway V2–V5, `frontend/src/app/(app)/people/*`), **18 People OS
integration tests** (incl. cross-tenant isolation on every slice), all **verified live in the browser**:
1. ✅ **P1 Directory & profiles** — `Employee` 1:1 with `User` (auto-provisioned); `/people/employees`,
   `/people/me`, PATCH; `/people` directory (search, cards, admin edit / self-service).
2. ✅ **P2 Departments + org chart** — `/people/departments` CRUD, employee dept/manager assignment,
   `/people/org` reporting tree.
3. ✅ **P3 Onboarding** — per-employee checklist (`/people/employees/{id}/onboarding` + seed-defaults,
   toggle self/admin); UI in the employee detail drawer.
4. ✅ **P4 Time-off** — `/people/leave` request → approve/reject/cancel + vacation balance;
   `/people/time-off` page with request form, my requests, admin approvals inbox.
5. ✅ **P5 Self-service** — employee detail drawer (full profile + onboarding), self profile edit,
   self leave.

**Bug fixed earlier:** refresh-token rotation treated a *duplicate* refresh (React StrictMode dev
double-invoke; also two tabs in prod) as reuse and revoked the whole family → cascading 401s. Fixed
with a one-shot bootstrap guard in `SessionProvider` (`frontend/src/hooks/useSession.tsx`).

## 6. Sprint 3: Work OS — ✅ COMPLETE
Plan: [docs/Sprint3-WorkOS.md](docs/Sprint3-WorkOS.md). Backend `com.calyvora.work`, Flyway `V6`/`V7`,
frontend `frontend/src/app/(app)/work/*`. **5 Work OS integration tests** (42 total), verified live.
- **W1 Projects** — `Project` (name/KEY/description/status/lead); `/work/projects` CRUD + archive
  (OWNER/ADMIN); `/work` list + create. Per-project KEY (e.g. `PLT`) prefixes task refs.
- **W2 Tasks + Kanban board** — `Task` (status TODO/IN_PROGRESS/DONE, priority, **assignee = People
  `Employee`**, due date); `/work/projects/{id}/tasks`, `PATCH/DELETE /work/tasks/{id}`; `/work/{projectId}`
  board with move controls, create/edit/delete, assignee + priority + due.
- **W3 My work** — `GET /work/tasks/mine` (open tasks assigned to me across projects); `/work/mine`.
- **Cross-app proof:** a Work task's assignee is a People OS employee — the shared org graph in action.

## 6b. Sprint 4: Knowledge OS — ✅ COMPLETE
Plan: [docs/Sprint4-KnowledgeOS.md](docs/Sprint4-KnowledgeOS.md). Backend `com.calyvora.knowledge`,
Flyway `V8`/`V9`, frontend `frontend/src/app/(app)/knowledge/*`. **6 Knowledge OS integration tests**
(48 total), verified live end-to-end (register→…→create space→page→link Work task→publish→search→my pages).
- **K1 Spaces** — `Space` (name/KEY/description/status); `/knowledge/spaces` CRUD + archive (OWNER/ADMIN).
  `/knowledge` = space cards + create + a debounced global search box.
- **K2 Pages** — `Page` (Markdown `body`, DRAFT/PUBLISHED, `parent_id` tree). `/knowledge/spaces/{id}/pages`,
  `GET/PATCH/DELETE /knowledge/pages/{id}`. `/knowledge/{spaceId}` = two-pane page tree + Markdown editor/reader.
- **K3 Cross-app** — page **author = People `Employee`** (auto-provisioned via new `EmployeeService.ensureEmployeeId`);
  page may **link a Work `Task`** → `linkedTaskRef` like `PLT-1`. "Link a Work task" picker in the editor.
- **K4 Search** — `GET /knowledge/search?q=` title/body, tenant-scoped, with snippet.
- **K5 My pages** — `GET /knowledge/pages/mine`; `/knowledge/mine`.
- **Cross-app proof:** one page ties a person (author) to a task (`PLT-1`) to knowledge — the trio's graph.

## 6c. Sprint 5: Work OS depth (Sprints · Backlog · Tickets) — ✅ COMPLETE
Plan: [docs/Sprint5-WorkOS-Sprints.md](docs/Sprint5-WorkOS-Sprints.md). Backend `com.calyvora.work`,
Flyway `V10`/`V11`. **5 new integration tests** (58 total), verified live end-to-end.
- **Workspace UI:** `/work/{projectId}` is now a left-pane workspace — **Board · Backlog · Sprints · Tickets**.
- **S1 Sprints** — `Sprint` (name/goal/dates/status); `/work/projects/{id}/sprints` CRUD + `start`/`complete`.
  ≤1 ACTIVE/project (partial unique index); complete returns unfinished tasks to backlog. `tasks.sprint_id` added.
- **S2 Backlog/Board** — `GET /work/projects/{id}/backlog` (un-sprinted); `GET .../board` (active sprint + tasks, else backlog).
  Assign a task to a sprint / move to backlog via `PATCH /work/tasks/{id}` `sprintId` ("" = backlog).
- **S3 Tickets** — `Ticket` (subject/requester/status/priority, ref `KEY-T{n}`, assignee = People employee);
  `/work/projects/{id}/tickets`, `PATCH/DELETE /work/tickets/{id}`. **Debt: graduates to Service OS (SD-22b).**

## 7. What's next (open)
- **★ Founder feedback backlog (2026-07-22) — the active roadmap:** all handwritten notes transcribed &
  status-tracked in **[docs/Founder-Feedback-Backlog.md](docs/Founder-Feedback-Backlog.md)**. Order:
  A) quick wins + members-dropdown bug → B) role-based dashboards + attendance → C) People OS depth
  (salary/payslip/profile/perf/goals) → D) new modules (Clients ⭐, Documents, Notifications/Inbox).
  Branding: product name pending (Calyvora = parent); wired via `frontend/src/lib/brand.ts` + `Wordmark`.
- **Foundation hardening (deferred Sprint-1 debt — CLEARED 2026-07-21):**
  ~~Postgres RLS (SD-2)~~ **DONE** (SD-2a..d — RLS `ENABLE`+`FORCE` on all 11 tenant tables, V12,
  bound per-connection via `TenantAwareDataSource` GUC; app DB role must be `NOSUPERUSER` in prod).
  ~~RS256 JWT + key rotation (SD-5)~~ **DONE** (SD-5a/SD-23/SD-24 — RS256, `kid` rotation, JWKS).
- **App depth (future):** Knowledge (page version history, rich editor, comments, per-space permissions,
  full-text/tsvector search); People (payroll/comp, performance, accruals); Work (comments, subtasks,
  filters, sprints, notifications).
- **Docs polish:** API.md, Database.md, Playwright happy-path E2E.
- **Platform:** ~~universal AI assistant across the three apps~~ **SHIPPED 2026-07-21** (`com.calyvora.assistant`,
  `POST /api/v1/assistant/ask`) — RAG over People/Work/Knowledge; offline grounded provider by default,
  Claude when `ANTHROPIC_API_KEY` set. **Closes the Phase-1 exit criterion** (working cross-app assistant).

## Demo features (2026-07-21) — make it demoable
- **One-click seed:** `POST /api/v1/dev/seed-demo` (dev only) → "Northwind Robotics"; login page has an
  **"Explore the live demo"** button (live mode) that seeds + signs in. Owner: `ava.chen@northwind.demo` / `demopass123`.
- **Command center dashboard**, **global ⌘K search** (`/api/v1/search`), **AI assistant panel** (floating "Ask AI").

## 6. Key decisions (see DECISIONS.md / FOUNDER.md for full)
- **SD-9**: build tool = **Maven** (was Gradle SD-7). Founder directive.
- **Frontend-first**: build/verify UI before backend (toolchain gap). Mock backend mirrors §7 contract.
- **No Docker** → Zonky embedded Postgres for tests/local run.
- SD-1..SD-8: JPA; app-layer tenant isolation + RLS deferred to Sprint 2; email globally unique;
  register creates Company+Owner PENDING; **RS256 JWT (SD-5a, was HS256) + rotating refresh**; Mailpit;
  roles OWNER/ADMIN/MEMBER.

## 7. Working agreements
- **No PRs** — complete the full app locally first (founder directive 2026-07-10).
- Maintain FOUNDER.md on every major decision (standing duty).
- Keep this CONTEXT.md current so any session can resume from it.
