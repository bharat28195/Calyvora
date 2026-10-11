-- PD-72: the person a letter is for reads it, and accepts it with their signature (or declines).
--
-- signature_requests: one per letter sent for signature. The letter's text is fingerprinted when it
-- is sent (sha-256), so the signed record proves exactly which words were accepted. The signer's
-- drawn signature, typed name, time, IP address and browser are kept as the evidence.
--
-- signature_links: the public link a candidate (who has no login) signs through. Deliberately
-- outside row-level security, like invitations: the link is opened before any tenant is known, and
-- only the hash of the token is stored, so the table alone cannot be used to sign anything.
create table signature_requests (
    id               uuid primary key,
    company_id       uuid not null references companies(id) on delete cascade,
    document_id      uuid not null references generated_documents(id) on delete cascade,
    employee_id      uuid references employees(id) on delete set null,
    signer_name      varchar(200) not null,
    signer_email     varchar(320),
    status           varchar(16) not null,           -- SENT | VIEWED | SIGNED | DECLINED | WITHDRAWN
    document_hash    varchar(64) not null,
    message          text,
    expires_at       timestamptz not null,
    sent_by          uuid,
    sent_at          timestamptz not null default now(),
    viewed_at        timestamptz,
    decided_at       timestamptz,
    decline_reason   varchar(500),
    typed_name       varchar(200),
    signature_image  bytea,
    signature_type   varchar(64),
    signer_ip        varchar(64),
    signer_agent     varchar(400),
    reminders        integer not null default 0,
    last_reminded_at timestamptz
);
create index idx_signature_requests_document on signature_requests (document_id);
create index idx_signature_requests_employee on signature_requests (employee_id);

alter table signature_requests enable row level security;
alter table signature_requests force row level security;
create policy tenant_isolation on signature_requests
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

create table signature_links (
    token_hash  varchar(64) primary key,
    company_id  uuid not null references companies(id) on delete cascade,
    request_id  uuid not null references signature_requests(id) on delete cascade,
    expires_at  timestamptz not null
);
