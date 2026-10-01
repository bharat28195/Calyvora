# Sprint 3 — Work OS (Implementation Plan)

> **Status:** IN PROGRESS · **Date:** 2026-07-10 · **Owner:** Founding engineering.
>
> Work OS is the second Phase-1 app: **projects and tasks**, natively linked to the people/org graph
> that [People OS](Sprint2-PeopleOS.md) owns. It is the first proof of the cross-app thesis — a task
> is assigned to a real **employee**, not a free-text name.

---

## 0. Relationship to the platform
Reuses the Sprint-1 foundation (tenancy, JWT, RBAC, error envelope, `TenantContext`) and **reads
People OS**: a task's assignee is an `Employee` (People). No new identity. New bounded context
`com.calyvora.work` (backend), a **Work** section in the app shell (frontend), Flyway `V6`/`V7`.

## 1. Sprint Goal
> Deliver a working project/task tracker: any member can create **projects**, add **tasks**, move them
> across a **Kanban board** (To do → In progress → Done), set priority and due dates, and **assign tasks
> to teammates** from the directory; each person can see **My work** across all projects — all
> tenant-isolated.

## 2. Roles (reuse Sprint-1 set)
Work is collaborative: **any authenticated member** can create projects/tasks and manage tasks.
**Archiving/deleting a project** is OWNER/ADMIN (a light governance gate). Per-project membership/roles
are a future ABAC enhancement.

## 3. Vertical slices (each full-stack + tested)
| # | Slice | Backend | Frontend |
|---|-------|---------|----------|
| W1 | **Projects** | `Project` (name, key, description, status, lead); CRUD | `/work` project list + create |
| W2 | **Tasks + board** | `Task` (status, priority, assignee=Employee, due); CRUD, move, assign | `/work/{projectId}` Kanban board |
| W3 | **My work** | `GET /work/tasks/mine` (assigned to me, across projects) | `/work/mine` list |

## 4. Data model (Flyway `V6`, `V7`)
```sql
-- V6 projects
create table projects (
  id           uuid primary key,
  company_id   uuid not null references companies(id),
  name         varchar(120) not null,
  key          varchar(10)  not null,             -- short code, e.g. ENG
  description  varchar(2000),
  status       varchar(24)  not null default 'ACTIVE',  -- ACTIVE|ARCHIVED
  lead_user_id uuid references users(id),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
create unique index uq_projects_company_key on projects(company_id, lower(key));

-- V7 tasks
create table tasks (
  id             uuid primary key,
  company_id     uuid not null references companies(id),
  project_id     uuid not null references projects(id),
  number         int  not null,                   -- per-project sequential (ENG-1, ENG-2…)
  title          varchar(200) not null,
  description    varchar(4000),
  status         varchar(24) not null default 'TODO',    -- TODO|IN_PROGRESS|DONE
  priority       varchar(24) not null default 'MEDIUM',  -- LOW|MEDIUM|HIGH|URGENT
  assignee_id    uuid references employees(id),   -- cross-app link into People OS
  due_date       date,
  created_by     uuid not null references users(id),
  sort_order     int  not null default 0,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);
create index idx_tasks_project on tasks(project_id);
create index idx_tasks_assignee on tasks(assignee_id);
```
Same isolation rule (SD-2): every table carries `company_id`; every query filters `TenantContext`.

## 5. API (`/api/v1/work`)
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET/POST | `/work/projects` | member | List / create projects |
| GET/PATCH | `/work/projects/{id}` | member | View / update |
| POST | `/work/projects/{id}/archive` | OWNER/ADMIN | Archive |
| GET/POST | `/work/projects/{id}/tasks` | member | List / create tasks |
| PATCH | `/work/tasks/{id}` | member | Update (status, priority, assignee, due, title) |
| DELETE | `/work/tasks/{id}` | member | Delete |
| GET | `/work/tasks/mine` | member | Tasks assigned to me |

## 6. Decisions
- **PD-05 (new):** Work OS is the second app; tasks link to People `Employee` (cross-app graph proof).
- Collaborative RBAC (any member) for v1; per-project roles later.
- Per-project task numbering (`KEY-N`) for human-friendly references.

## 7. Definition of Done (per slice)
Backend (migration, DTOs, validation, service, repo, tenant-scoped) · unit/integration tests incl.
isolation · REST + Swagger · frontend page + live states · responsive · docs/CHANGELOG · tests green.

## 8. Out of scope (v1, logged)
Comments/activity feed, attachments, subtasks/dependencies, sprints/estimates, custom workflows,
cross-app automations (task-from-doc etc.), notifications. Scheduled per [docs/11](11-roadmap.md).
