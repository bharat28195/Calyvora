-- V6__work_os_projects.sql — Work OS slice W1: projects.
-- Projects are containers for tasks. Tenant-owned; a short KEY (e.g. ENG) prefixes task numbers.

create table projects (
    id           uuid primary key,
    company_id   uuid not null references companies(id),
    name         varchar(120) not null,
    key          varchar(10)  not null,
    description  varchar(2000),
    status       varchar(24)  not null default 'ACTIVE',   -- ACTIVE|ARCHIVED
    lead_user_id uuid references users(id),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now()
);
create unique index uq_projects_company_key on projects(company_id, lower(key));
create index idx_projects_company on projects(company_id);
