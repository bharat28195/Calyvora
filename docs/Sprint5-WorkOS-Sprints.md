# Sprint 5 — Work OS depth: Sprints, Backlog & a workspace layout

> **Goal:** Turn Work OS from a single Kanban board into a proper **project workspace** with a left-pane
> navigation (Board · Backlog · Sprints · Tickets), agile **sprints** (create → start → complete, with
> tasks assigned in/out), a **backlog** of un-sprinted work, and a lightweight **support tickets** type.
> Same discipline as prior sprints: full-stack vertical slices, integration tests incl. an adversarial
> cross-tenant check, live-verified, committed to a branch. Binding architecture = [/docs](README.md).

## 0. Decisions (Sprint 5)
| ID | Decision | Rationale | Alternatives rejected |
|----|----------|-----------|-----------------------|
| SD-18 | **Sprints belong to a Project**; a task has an optional `sprint_id` (null = backlog) | Matches how teams plan — a project has many sprints, a task is in ≤1 sprint or the backlog | Sprints as a global entity (loses project scoping); many-to-many task↔sprint (needless) |
| SD-19 | **At most one ACTIVE sprint per project**, enforced by a partial unique index | The "current sprint" must be unambiguous for the Board | App-only check (racy); allow many active (confusing board) |
| SD-20 | **Completing a sprint moves its unfinished (non-DONE) tasks back to the backlog** | Standard agile carry-over; nothing is silently lost | Force-close tasks (data loss); leave them stranded on a done sprint |
| SD-21 | **Board = the active sprint**; if a project has no sprints yet, the Board shows all un-sprinted tasks so Work is usable on day one | Zero-friction start; sprints are opt-in depth | Force sprint creation before any board (bad first-run UX) |
| SD-22 | **Support Tickets are a lightweight type inside Work** (subject/requester/priority/status), assignee = People `Employee` | Founder wants tickets now; a thin version proves the shape | Full Service OS now (Phase-2 scope); free-text assignee (loses the org graph) |
| SD-22b | **Tickets are deliberate debt → graduate to Service OS (Phase 2).** Logged, not hidden. | Honesty in the record; tickets' real SoR is customers/SLAs, not projects | Pretend Work is the permanent home |

## 1. Data model (Flyway V10, V11)
- **`sprints`** (V10): `id, company_id, project_id, name, goal, start_date, end_date, status
  PLANNED|ACTIVE|COMPLETED, timestamps`. Partial unique index `(project_id) where status='ACTIVE'`.
  Plus `alter table tasks add column sprint_id uuid references sprints(id)` (+ index).
- **`tickets`** (V11): `id, company_id, project_id, number (per-project, ref `KEY-T{n}`), subject,
  description, requester_name, requester_email, status OPEN|PENDING|RESOLVED|CLOSED, priority
  LOW|MEDIUM|HIGH|URGENT, assignee_id → employees (cross-app), created_by, timestamps`.

## 2. Vertical slices
1. **S1 — Sprints.** CRUD + `start`/`complete` lifecycle; one active per project; complete carries
   unfinished tasks to backlog. Assign a task to a sprint / move to backlog via the task `sprintId`.
2. **S2 — Backlog.** List tasks with no sprint for a project; create straight into backlog; "move to
   sprint" action. Board (active sprint) vs Backlog are the two planning surfaces.
3. **S3 — Support Tickets.** Lightweight ticket CRUD per project; status workflow; assignee = employee.
4. **Workspace UI.** `/work/{projectId}` becomes a left-pane workspace: **Board · Backlog · Sprints ·
   Tickets**, project-scoped, with the sprint switcher on the Board.

## 3. API (extends `/api/v1/work`)
| Method | Path | Notes |
|--------|------|-------|
| GET/POST | `/projects/{id}/sprints` | list / create (PLANNED) |
| PATCH | `/sprints/{id}` | name/goal/dates |
| POST | `/sprints/{id}/start` | PLANNED→ACTIVE (409 if another active) |
| POST | `/sprints/{id}/complete` | ACTIVE→COMPLETED; carry unfinished tasks to backlog |
| DELETE | `/sprints/{id}` | delete (its tasks fall back to backlog) |
| GET | `/projects/{id}/backlog` | tasks with `sprint_id = null` |
| GET | `/projects/{id}/board` | active sprint + its tasks (or un-sprinted if none) |
| PATCH | `/tasks/{id}` | now also accepts `sprintId` ("" = backlog) |
| GET/POST | `/projects/{id}/tickets` | list / create |
| GET/PATCH/DELETE | `/tickets/{id}` | one / update (status/priority/assignee) / delete |

## 4. Definition of Done
- Flyway V10/V11 apply; app boots. Board/Backlog/Sprints/Tickets all work per project.
- Integration tests green incl.: sprint lifecycle (start enforces single-active; complete carries over),
  backlog membership, ticket CRUD + status, **and a cross-tenant isolation test** on sprints & tickets.
- `/work/{projectId}` workspace verified live: create a sprint → add backlog tasks → start sprint →
  board shows them → complete → leftovers return to backlog; create + assign a ticket.
- FOUNDER.md (PD-07), DECISIONS.md (SD-18..22), CONTEXT.md, CHANGELOG.md updated.

## 5. Deliberately deferred
- Drag-and-drop (move via controls, as today); sprint velocity/burndown charts; ticket comments/SLA/
  customer records (→ **Service OS**, Phase 2, SD-22b); sub-tasks; per-project roles.
