-- V28__shifts.sql — Shift scheduling / rostering (roadmap #1; Keka/Zoho core for hourly & ops teams).
--
-- A shift is a reusable template (name + start/end time). A roster entry assigns one employee to one
-- shift on one day — at most one shift per employee per day. Both tenant-isolated by RLS.

create table shifts (
    id          uuid primary key,
    company_id  uuid not null references companies(id),
    name        varchar(60) not null,
    start_time  time not null,
    end_time    time not null,
    color       varchar(16),
    created_at  timestamptz not null default now()
);
create index idx_shifts_company on shifts(company_id, start_time);

create table shift_assignments (
    id           uuid primary key,
    company_id   uuid not null references companies(id),
    employee_id  uuid not null references employees(id) on delete cascade,
    shift_id     uuid not null references shifts(id) on delete cascade,
    on_date      date not null,
    created_at   timestamptz not null default now(),
    unique (employee_id, on_date)
);
create index idx_shift_assignments_company_date on shift_assignments(company_id, on_date);

alter table shifts enable row level security;
alter table shifts force row level security;
create policy tenant_isolation on shifts
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table shift_assignments enable row level security;
alter table shift_assignments force row level security;
create policy tenant_isolation on shift_assignments
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
