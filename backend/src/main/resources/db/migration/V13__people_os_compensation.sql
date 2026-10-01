-- V13__people_os_compensation.sql — People OS: compensation history (salary + hikes).
-- Each row is a point-in-time salary; the latest by effective date is the current pay.
-- Hike % is derived by comparing consecutive records. Tenant-owned; RLS-protected (SD-2).

create table compensation_records (
    id             uuid primary key,
    company_id     uuid not null references companies(id),
    employee_id    uuid not null references employees(id),
    effective_date date not null,
    annual_amount  numeric(14, 2) not null,
    currency       varchar(3)   not null default 'USD',
    change_type    varchar(24)  not null default 'ADJUSTMENT',  -- INITIAL | HIKE | ADJUSTMENT
    reason         varchar(500),
    created_by     uuid references users(id),
    created_at     timestamptz  not null default now()
);
create index idx_comp_employee on compensation_records(employee_id);
create index idx_comp_company on compensation_records(company_id);

-- Row-Level Security for the new tenant table (mirrors V12).
alter table compensation_records enable row level security;
alter table compensation_records force row level security;
create policy tenant_isolation on compensation_records
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
