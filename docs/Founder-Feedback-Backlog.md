# Founder Feedback Backlog

> **Living tracker** for the founder's handwritten product notes (received **2026-07-22**, 8-page PDF).
> This is the single source of truth for that feedback — every item below is transcribed and status-tracked
> so any session can resume without losing context. Update the status column as work lands.
> See also [CONTEXT.md](../CONTEXT.md), [CHANGELOG.md](../CHANGELOG.md), [DECISIONS.md](../DECISIONS.md).

**Legend:** ✅ done · 🔜 in progress · ⬜ pending · 💤 deferred/blocked
**Chosen order (founder, 2026-07-22):** all four buckets, in order → **A) Quick wins + bug** → **B) Role-based dashboards + attendance** → **C) People OS depth** → **D) New modules**. Branding: founder asked me to **suggest product names** (Calyvora = parent company).

---

## Bucket A — Quick wins + bug fix

| # | Item | Source | Status | Notes |
|---|------|--------|--------|-------|
| A1 | **Members/assignee dropdown "not working"; must show all company members and scale to ~1k people** ("from user/member's parent node") | p6 | ✅ | `MemberSelect` searchable combobox (`components/ui/member-select.tsx`) lists all company members, filters by name/email/title; wired into task + ticket assignee. |
| A2 | **Knowledge search should be present at all times** | p4 | ✅ | Extracted reusable `KnowledgeSearch` (`components/knowledge/knowledge-search.tsx`); now on the Knowledge index **and** inside every space. |
| A3 | **Selectable sprint types** — 1 week / 2 weeks / month | p4 | ✅ | Sprint-length picker (1w/2w/1month/custom) auto-fills end date from start; frontend-only, no migration. |
| A4 | **Put all tabs on the left** (left sidebar nav) so a tab can be selected there | p5–6 | ✅ | Left sidebar with icons + active state; mobile fallback nav. `app-shell.tsx`. |
| A5 | **Nicer logo / make company name look good** | p5 | ✅ (partial) | Gradient app-mark + `Wordmark` component + central `brand.ts`. Full logo art still open. |

**Bucket A complete** (2026-07-22) — pending only the product-name pick (BR1) to finish the wordmark.

---

## Bucket B — Role-based dashboards + attendance

| # | Item | Source | Status | Notes |
|---|------|--------|--------|-------|
| B1 | **Different dashboards for member/employee vs admin/owner** | p4 | ✅ | Company KPIs + Team overview are Owner/Admin-only; members get the personal view. |
| B2 | **Owner: total employees count** | p1 | ✅ | "Total employees" tile in Team overview. |
| B3 | **Owner: on-leave vs present count** | p1 | ✅ | Present vs on-leave-today tiles, derived from approved leave (`GET /dashboard/team`). |
| B4 | **Owner: reason for leave** | p1 | ✅ | "Out today" list shows name + leave type + reason. |
| B5 | **Leave days shown on a calendar** ("how many days in calendar") | p1 | ✅ | Month leave calendar (amber days = someone on leave, ring = today). |
| B6 | **Attendance option** (present as a feature/preset) | p6 | 🔜 (phase 1) | **Decision: "both, phased."** Phase 1 shipped = attendance *derived from leave*. Phase 2 (full daily attendance record + marking UI) deferred to Bucket C alongside payroll. |

---

## Bucket C — People OS depth

| # | Item | Source | Status | Notes |
|---|------|--------|--------|-------|
| C1 | **Salary tab** — full salary, all salary details | p1–2 | ✅ | `compensation_records` (V13, RLS) + `GET/POST /people/employees/{id}/compensation`; Compensation section on the employee detail (Owner/Admin only). Add-raise inline form. |
| C2 | **Yearly hike / hike history** ("how much hike we have provided") | p1–2 | ✅ | Full history with per-record **hike %** badges (derived from consecutive records). Demo seeded with initial + review hike. |
| C3 | **Employee payslip** — salary everything in one place | p7 | ✅ | `GET /people/employees/{id}/payslip?month=` — computed breakdown (basic/HRA/special · PF/tax · net). Payslip card with month picker. |
| C4 | **Richer employee profile**: what he/she is working on · when started / end date · delay vs advance · how they're performing | p2 | ✅ | **Working on** = open tasks assigned to them (`GET /people/employees/{id}/work`, in `work` pkg to avoid a cycle) with **overdue** flag (= "delay"). **End date** added (V14). "How performing" = the rating (C6). |
| C5 | **Full employee details + skills** | p3 | ✅ | `skills` (comma-sep, V14) as editable chips on the profile + edit dialog. |
| C6 | **Ratings** | p3 | ✅ | 1–5 star `rating` (V14), shown on profile, editable by admin. (Deeper "performance" = C7.) |
| C7 | **Performance** | p3 | 🔜 (partial) | Covered for now by the **rating** (C6) + **goals progress** (C8). A fuller review-cycle module (periodic reviews, reviewer, cycle) is future. |
| C8 | **Goals** | p3 | ✅ | `goals` table (V15, RLS) + `GET/POST/PATCH/DELETE /people/employees/{id}/goals`. Editable by admin or the goal owner (self-service). Progress bar + status; 100% → auto-ACHIEVED. Demo seeds goals. |
| C9 | **Kanban-like section in employee tab** ("look on kanban like it has one section") | p3 | ⬜ | *Ambiguous* — confirm intent (employee view laid out like a board?). |

---

## Bucket D — New modules

| # | Item | Source | Status | Notes |
|---|------|--------|--------|-------|
| D1 | ⭐ **Clients tab** — client details: what each client requested + all their info | p8 (starred) | ✅ | New `com.calyvora.client` module (V16, clients + client_requests, RLS). `/api/v1/clients` CRUD + `/{id}/requests` CRUD; open-request rollups. Sidebar **Clients** tab, list + detail (requests with status), global-search integration ("client" hits). Demo seeds 3 clients w/ requests. |
| D2 | **Documentation tab** — auto-generate joining/resigning letters: fill in name → a proper document is generated | p7 | ⬜ | Document generation from templates. |
| D3 | **Document templates** should be present | p7 | ⬜ | Template library backing D2. |
| D4 | **Notifications** | p3 | ⬜ | Notification system. |
| D5 | **Inbox** | p3 | ⬜ | In-app inbox/messages. |
| D6 | **Organization** | p3 | ⬜ | Org view (already have People → Org chart; confirm if this means more). |

---

## Branding & packaging (cross-cutting)

| # | Item | Source | Status | Notes |
|---|------|--------|--------|-------|
| BR1 | **Product name** — the OS gets its own name; **Calyvora = parent company** | p5 | ✅ | **Product = "Orbit", parent = "Calyvora"** (founder pick 2026-07-22). Set in `frontend/src/lib/brand.ts`. |
| BR2 | **Add product name** to the UI | p5 | ✅ | Wordmark shows "**Orbit** by Calyvora" (sidebar + auth pages); page `<title>` updated. |
| BR3 | **Modular packaging / presets** — let a client buy just one module; a preset/selector so modules can be toggled per customer; "if user goes to that tab he can select" | p5–6 | ⬜ | Per-tenant module entitlements + a selection UI. Larger platform work. |

**Product name (decided 2026-07-22):** **Orbit** — by Calyvora. Shortlist offered: Orbit ✓, Nexus, Cortex, Meridian.

---

## Notes / ambiguities to confirm with founder
- **C9** "kanban like it has one section" — unclear; confirm the intended layout.
- **D6** "Organization" — may already be covered by People → Org chart; confirm scope.
- **A1** "parent node" phrasing interpreted as: list all members of the company (tenant), searchable at scale.
- **B3/B6** Attendance is implied but not yet a modeled concept — decide model (daily attendance vs derive present/on-leave from leave requests).

## Change log for this backlog
- **2026-07-22** — Created from the 8-page handwritten notes. Bucket A started: A4 (left nav) + A5 (wordmark) done; A1/A2/A3 in progress/next. Branding wiring (BR1/BR2) scaffolded via `brand.ts` + `Wordmark`.
