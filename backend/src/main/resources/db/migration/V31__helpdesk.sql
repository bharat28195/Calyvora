-- V31__helpdesk.sql — HR Helpdesk (roadmap #2): employees raise HR/payroll/IT queries and track them.
--
-- A ticket is raised by an employee, optionally assigned to an HR/admin, and moves OPEN → IN_PROGRESS
-- → RESOLVED → CLOSED. Comments form the conversation thread. Both tenant-isolated by RLS.

create table helpdesk_tickets (
    id           uuid primary key,
    company_id   uuid not null references companies(id),
    raised_by    uuid not null references users(id),
    category     varchar(20) not null,
    subject      varchar(160) not null,
    description  varchar(4000),
    priority     varchar(12) not null default 'MEDIUM',
    status       varchar(16) not null default 'OPEN',
    assignee_id  uuid references users(id),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    resolved_at  timestamptz
);
create index idx_helpdesk_tickets_company on helpdesk_tickets(company_id, status, created_at desc);
create index idx_helpdesk_tickets_raised_by on helpdesk_tickets(raised_by);

create table helpdesk_comments (
    id           uuid primary key,
    company_id   uuid not null references companies(id),
    ticket_id    uuid not null references helpdesk_tickets(id) on delete cascade,
    author_id    uuid not null references users(id),
    body         varchar(4000) not null,
    created_at   timestamptz not null default now()
);
create index idx_helpdesk_comments_ticket on helpdesk_comments(ticket_id, created_at);

alter table helpdesk_tickets enable row level security;
alter table helpdesk_tickets force row level security;
create policy tenant_isolation on helpdesk_tickets
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table helpdesk_comments enable row level security;
alter table helpdesk_comments force row level security;
create policy tenant_isolation on helpdesk_comments
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
