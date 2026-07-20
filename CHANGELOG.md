# Changelog

All notable changes to Calyvora. Newest first. Dates are absolute (ISO `YYYY-MM-DD`).

## [Unreleased]

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
