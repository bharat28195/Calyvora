# Sprint 2 — People OS (Implementation Plan)

> **Status:** IN PROGRESS · **Date:** 2026-07-10 · **Owner:** Founding engineering.
>
> People OS is Calyvora's **beachhead app** (PD-02): the HR **system of record** for a company's
> people. It is the first business module built on the Sprint-1 Foundation (tenancy, identity, auth,
> RBAC). Every other app will consume the people/org graph this app owns.

---

## 0. Relationship to the Foundation
People OS **reuses** the platform's `users` table (identity/auth) and adds HR data on top. It does
**not** re-implement auth. Tenant isolation, JWT, RBAC, error envelope, and the `TenantContext`
pattern from Sprint 1 apply unchanged. New code lives in the `com.calyvora.people` bounded context
(backend) and a **People** section in the app shell (frontend). New schema ships as Flyway `V2`.

## 1. Sprint Goal
> Deliver a working HR core: every company member has an **employee profile**; Owners/Admins manage
> an **employee directory**, **departments + reporting lines (org chart)**, **onboarding checklists**,
> and **time-off** (request → approve → balance); members get **self-service** to view/edit their own
> profile and browse the directory — all tenant-isolated and role-governed.

## 2. Roles (reuse Sprint-1 set)
- **OWNER / ADMIN** → act as HR admins (manage everyone, departments, approve leave).
- **MEMBER** → self-service (own profile, browse directory, request leave).
- *(A dedicated `HR_MANAGER`/`MANAGER` role and per-department scoping is a future ABAC enhancement —
  Sprint-1 roles are enough to ship People OS v1.)*

## 3. Vertical slices (each ships full-stack + tests before the next)
| # | Slice | Backend | Frontend |
|---|-------|---------|----------|
| P1 | **Employee directory & profiles** | `Employee` 1:1 with `User`; auto-provision on read/user-create; list/get/update; job title, dept, type, start date, manager, location, phone, employee no. | `/people` directory (search, cards/table), profile drawer, edit (admin) |
| P2 | **Departments & org chart** | `Department` (name, parent, lead); assign employees; manager reporting line | `/people/org` chart + department管理 |
| P3 | **Onboarding** | `OnboardingTemplate` + `OnboardingTask` per employee; complete/track | New-hire checklist UI |
| P4 | **Time-off** | `LeaveType`, `LeaveRequest` (request→approve/reject), `LeaveBalance` | Request form, approvals inbox, balances |
| P5 | **Self-service** | scope: a member edits own profile; read-only directory | Profile page, directory browse |

## 4. Data model (Flyway `V2__people_os.sql`, incremental per slice)
```sql
-- P1
create table employees (
  id              uuid primary key,
  company_id      uuid not null references companies(id),
  user_id         uuid not null unique references users(id),
  employee_no     varchar(32),
  job_title       varchar(120),
  employment_type varchar(24),                 -- FULL_TIME|PART_TIME|CONTRACT|INTERN
  department_id   uuid,                         -- FK added in P2
  manager_id      uuid references employees(id),
  work_location   varchar(120),
  phone           varchar(40),
  start_date      date,
  employment_status varchar(24) not null default 'ACTIVE', -- ONBOARDING|ACTIVE|TERMINATED
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index idx_employees_company on employees(company_id);
-- P2..P5 tables (departments, onboarding_*, leave_*) added with their slices.
```
Same isolation rule as SD-2: every table carries `company_id`, every query filters `TenantContext`.

## 5. API (base `/api/v1`, JSON, same error envelope)
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET | `/people/employees` | any (self-service browse) | Directory list (search/filter) |
| GET | `/people/employees/{id}` | any | Employee profile |
| GET | `/people/me` | any | My own employee profile |
| PATCH | `/people/employees/{id}` | OWNER/ADMIN | Update any profile |
| PATCH | `/people/me` | any | Update own profile (limited fields) |
| *(P2+)* | `/people/departments`, `/people/leave/*`, `/people/onboarding/*` | per slice | |

## 6. Decisions
- **PD-04 (new):** People OS is the first app; models the org graph every other app reads.
- **Employee ⟂ User:** 1:1 extension table, auto-provisioned, so identity and HR data stay separate
  (a future contractor/non-user person can become an Employee without a login — noted, not built).
- Reuse Sprint-1 roles; per-department authorization deferred to the RBAC+ABAC engine.

## 7. Definition of Done (per slice)
Backend (migration, DTOs, Bean Validation, service, repo, tenant-scoped) · unit + integration tests
(incl. isolation) · REST + Swagger · frontend page/components + live states · responsive · docs +
CHANGELOG · all tests green. Only then the next slice starts.

## 8. Out of scope (v1, logged)
Payroll/compensation, benefits, performance/goals, ATS/recruiting, documents/e-sign, custom fields,
per-department RBAC, contractors-without-login, calendar sync. Scheduled per [docs/11](11-roadmap.md).
