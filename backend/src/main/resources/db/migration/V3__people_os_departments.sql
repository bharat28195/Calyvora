-- V3__people_os_departments.sql — People OS slice P2: departments + reporting.
-- Departments form a hierarchy (parent_id); an optional lead is a user. Tenant-owned.

create table departments (
    id           uuid primary key,
    company_id   uuid not null references companies(id),
    name         varchar(120) not null,
    parent_id    uuid references departments(id),
    lead_user_id uuid references users(id),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);
create index idx_departments_company on departments(company_id);

-- now that departments exist, wire the employees.department_id FK (column added in V2)
alter table employees
    add constraint fk_employees_department foreign key (department_id) references departments(id);
