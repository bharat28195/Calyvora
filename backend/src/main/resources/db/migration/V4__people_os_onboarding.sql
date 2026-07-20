-- V4__people_os_onboarding.sql — People OS slice P3: onboarding checklists.
-- A per-employee list of tasks tracked to completion. Tenant-owned.

create table onboarding_tasks (
    id           uuid primary key,
    company_id   uuid not null references companies(id),
    employee_id  uuid not null references employees(id),
    title        varchar(200) not null,
    sort_order   int         not null default 0,
    completed    boolean     not null default false,
    completed_at timestamptz,
    created_at   timestamptz not null default now()
);
create index idx_onboarding_employee on onboarding_tasks(employee_id);
create index idx_onboarding_company on onboarding_tasks(company_id);
