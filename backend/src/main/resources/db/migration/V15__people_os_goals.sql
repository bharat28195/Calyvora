-- V15__people_os_goals.sql — People OS: employee goals (feedback C8; the concrete half of performance).
-- A goal has a progress % and a status; owned by an employee, editable by that employee or an admin.
-- Tenant-owned; RLS-protected (SD-2).

create table goals (
    id           uuid primary key,
    company_id   uuid not null references companies(id),
    employee_id  uuid not null references employees(id),
    title        varchar(200) not null,
    description  varchar(2000),
    status       varchar(24)  not null default 'OPEN',   -- OPEN | ACHIEVED | MISSED
    progress     integer      not null default 0,        -- 0..100
    target_date  date,
    created_by   uuid references users(id),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now()
);
create index idx_goals_employee on goals(employee_id);
create index idx_goals_company on goals(company_id);

alter table goals enable row level security;
alter table goals force row level security;
create policy tenant_isolation on goals
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
