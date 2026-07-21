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
| A1 | **Members/assignee dropdown "not working"; must show all company members and scale to ~1k people** ("from user/member's parent node") | p6 | 🔜 | Fix = searchable **MemberSelect** combobox pulling all company members; replaces the plain `<select>` on task & ticket assignee (and reuse for manager/lead pickers). |
| A2 | **Knowledge search should be present at all times** | p4 | ⬜ | Persistent search box on Knowledge screens (in addition to global ⌘K). |
| A3 | **Selectable sprint types** — 1 week / 2 weeks / month | p4 | ⬜ | Add sprint cadence/type on sprint create; optionally auto-fill end date from start + cadence. |
| A4 | **Put all tabs on the left** (left sidebar nav) so a tab can be selected there | p5–6 | ✅ | Left sidebar with icons + active state; mobile fallback nav. `app-shell.tsx`. |
| A5 | **Nicer logo / make company name look good** | p5 | ✅ (partial) | Gradient app-mark + `Wordmark` component + central `brand.ts`. Full logo art still open. |

---

## Bucket B — Role-based dashboards + attendance

| # | Item | Source | Status | Notes |
|---|------|--------|--------|-------|
| B1 | **Different dashboards for member/employee vs admin/owner** | p4 | ⬜ | Role-aware dashboard content. |
| B2 | **Owner: total employees count** | p1 | ⬜ | Headcount tile (have `memberCount` already — surface prominently in owner view). |
| B3 | **Owner: on-leave vs present count** | p1 | ⬜ | Needs attendance/leave-today aggregation. |
| B4 | **Owner: reason for leave** | p1 | ⬜ | Surface leave reasons (leave requests already store type/reason). |
| B5 | **Leave days shown on a calendar** ("how many days in calendar") | p1 | ⬜ | Calendar view of leave/attendance. |
| B6 | **Attendance option** (present as a feature/preset) | p6 | ⬜ | New Attendance concept (present/absent/on-leave per day). Likely People OS. |

---

## Bucket C — People OS depth

| # | Item | Source | Status | Notes |
|---|------|--------|--------|-------|
| C1 | **Salary tab** — full salary, all salary details | p1–2 | ⬜ | New salary/compensation area (People OS). Sensitive — RBAC (owner/admin only). |
| C2 | **Yearly hike / hike history** ("how much hike we have provided") | p1–2 | ⬜ | Comp history with raises over time. |
| C3 | **Employee payslip** — salary everything in one place | p7 | ⬜ | Generate/view payslips. |
| C4 | **Richer employee profile**: what he/she is working on · when started / end date · delay vs advance · how they're performing | p2 | ⬜ | Extend employee profile + link to current Work items. |
| C5 | **Full employee details + skills** | p3 | ⬜ | Skills list on profile. |
| C6 | **Ratings** | p3 | ⬜ | Employee ratings. |
| C7 | **Performance** | p3 | ⬜ | Performance module. |
| C8 | **Goals** | p3 | ⬜ | Goals/OKRs. |
| C9 | **Kanban-like section in employee tab** ("look on kanban like it has one section") | p3 | ⬜ | *Ambiguous* — confirm intent (employee view laid out like a board?). |

---

## Bucket D — New modules

| # | Item | Source | Status | Notes |
|---|------|--------|--------|-------|
| D1 | ⭐ **Clients tab** — client details: what each client requested + all their info | p8 (starred) | ⬜ | New Clients module. Founder starred it = high priority within D. |
| D2 | **Documentation tab** — auto-generate joining/resigning letters: fill in name → a proper document is generated | p7 | ⬜ | Document generation from templates. |
| D3 | **Document templates** should be present | p7 | ⬜ | Template library backing D2. |
| D4 | **Notifications** | p3 | ⬜ | Notification system. |
| D5 | **Inbox** | p3 | ⬜ | In-app inbox/messages. |
| D6 | **Organization** | p3 | ⬜ | Org view (already have People → Org chart; confirm if this means more). |

---

## Branding & packaging (cross-cutting)

| # | Item | Source | Status | Notes |
|---|------|--------|--------|-------|
| BR1 | **Product name** — the OS gets its own name; **Calyvora = parent company** | p5 | 🔜 | Founder wants **name suggestions**. Wired `brand.ts` (`product`/`parent`) + `Wordmark` so it flips in one place once chosen. |
| BR2 | **Add product name** to the UI | p5 | 🔜 | Same as BR1; wordmark shows "<Product> by Calyvora" once product ≠ parent. |
| BR3 | **Modular packaging / presets** — let a client buy just one module; a preset/selector so modules can be toggled per customer; "if user goes to that tab he can select" | p5–6 | ⬜ | Per-tenant module entitlements + a selection UI. Larger platform work. |

**Product-name shortlist (proposed 2026-07-22 — awaiting founder pick):** _to be recorded here once presented._

---

## Notes / ambiguities to confirm with founder
- **C9** "kanban like it has one section" — unclear; confirm the intended layout.
- **D6** "Organization" — may already be covered by People → Org chart; confirm scope.
- **A1** "parent node" phrasing interpreted as: list all members of the company (tenant), searchable at scale.
- **B3/B6** Attendance is implied but not yet a modeled concept — decide model (daily attendance vs derive present/on-leave from leave requests).

## Change log for this backlog
- **2026-07-22** — Created from the 8-page handwritten notes. Bucket A started: A4 (left nav) + A5 (wordmark) done; A1/A2/A3 in progress/next. Branding wiring (BR1/BR2) scaffolded via `brand.ts` + `Wordmark`.
