-- V16__client_os.sql — Clients module (founder feedback D1 ⭐): client details + what each client
-- has requested. A request belongs to a client. Tenant-owned; RLS-protected (SD-2).

create table clients (
    id            uuid primary key,
    company_id    uuid not null references companies(id),
    name          varchar(160) not null,
    contact_name  varchar(160),
    contact_email varchar(200),
    phone         varchar(40),
    website       varchar(200),
    status        varchar(24)  not null default 'LEAD',   -- LEAD | ACTIVE | CHURNED
    notes         varchar(4000),
    created_by    uuid references users(id),
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);
create index idx_clients_company on clients(company_id);

create table client_requests (
    id           uuid primary key,
    company_id   uuid not null references companies(id),
    client_id    uuid not null references clients(id) on delete cascade,
    title        varchar(200) not null,
    description  varchar(2000),
    status       varchar(24)  not null default 'REQUESTED',  -- REQUESTED | IN_PROGRESS | DELIVERED | DECLINED
    created_at   timestamptz  not null default now()
);
create index idx_client_requests_client on client_requests(client_id);
create index idx_client_requests_company on client_requests(company_id);

-- Row-Level Security for both new tenant tables (mirrors V12).
alter table clients enable row level security;
alter table clients force row level security;
create policy tenant_isolation on clients
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table client_requests enable row level security;
alter table client_requests force row level security;
create policy tenant_isolation on client_requests
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
