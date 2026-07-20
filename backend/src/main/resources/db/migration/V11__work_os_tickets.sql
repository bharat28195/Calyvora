-- V11__work_os_tickets.sql — Work OS slice S3: lightweight support tickets.
-- Deliberate debt (SD-22b): tickets' true system of record is Service OS (Phase 2). For now they live
-- in Work, scoped to a project, with a per-project number (ref KEY-T{n}) and a People employee assignee.

create table tickets (
    id              uuid primary key,
    company_id      uuid not null references companies(id),
    project_id      uuid not null references projects(id),
    number          int  not null,                          -- per-project sequential (KEY-T{n})
    subject         varchar(200) not null,
    description     varchar(4000),
    requester_name  varchar(160),
    requester_email varchar(200),
    status          varchar(24) not null default 'OPEN',    -- OPEN|PENDING|RESOLVED|CLOSED
    priority        varchar(24) not null default 'MEDIUM',  -- LOW|MEDIUM|HIGH|URGENT
    assignee_id     uuid references employees(id),          -- cross-app link into People OS
    created_by      uuid not null references users(id),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
create index idx_tickets_project on tickets(project_id);
create index idx_tickets_company on tickets(company_id);
create index idx_tickets_assignee on tickets(assignee_id);
