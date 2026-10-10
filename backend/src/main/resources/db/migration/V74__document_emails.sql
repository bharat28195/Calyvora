-- V74: every time a letter is emailed, who it went to and whether it was delivered (PD-64).
-- New table only; nothing existing changes.

create table document_emails (
    id           uuid primary key,
    company_id   uuid not null references companies(id) on delete cascade,
    document_id  uuid not null references generated_documents(id) on delete cascade,
    sent_to      varchar(320) not null,
    subject      varchar(300) not null,
    sent_by      uuid references users(id) on delete set null,
    sent_at      timestamptz not null default now(),
    delivered    boolean not null,
    error        varchar(500)
);

create index document_emails_document_idx on document_emails (document_id, sent_at desc);

alter table document_emails enable row level security;
alter table document_emails force row level security;
create policy tenant_isolation on document_emails
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
