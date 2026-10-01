-- V8__knowledge_os_spaces.sql — Knowledge OS slice K1: spaces.
-- Spaces are containers for pages (like Work OS projects). Tenant-owned; a short KEY groups docs.

create table spaces (
    id          uuid primary key,
    company_id  uuid not null references companies(id),
    name        varchar(120) not null,
    key         varchar(10)  not null,
    description varchar(2000),
    status      varchar(24)  not null default 'ACTIVE',   -- ACTIVE|ARCHIVED
    created_by  uuid not null references users(id),
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);
create unique index uq_spaces_company_key on spaces(company_id, lower(key));
create index idx_spaces_company on spaces(company_id);
