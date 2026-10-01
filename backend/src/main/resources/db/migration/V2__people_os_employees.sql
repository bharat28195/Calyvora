-- V2__people_os_employees.sql — People OS slice P1: employee profiles.
-- An employee is a 1:1 HR extension of a platform user (see docs/Sprint2-PeopleOS.md §4).
-- Tenant-owned: carries company_id, filtered via TenantContext (SD-2).

create table employees (
    id                uuid primary key,
    company_id        uuid not null references companies(id),
    user_id           uuid not null unique references users(id),
    employee_no       varchar(32),
    job_title         varchar(120),
    employment_type   varchar(24),                        -- FULL_TIME|PART_TIME|CONTRACT|INTERN
    department_id     uuid,                                -- FK added with departments (P2)
    manager_id        uuid references employees(id),
    work_location     varchar(120),
    phone             varchar(40),
    start_date        date,
    employment_status varchar(24) not null default 'ACTIVE', -- ONBOARDING|ACTIVE|TERMINATED
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);
create index idx_employees_company on employees(company_id);
create index idx_employees_manager on employees(manager_id);
