-- V42__trial_requests.sql — "Start free trial" becomes a request, not a signup (PD-21).
--
-- Until now the marketing site's trial button pointed at /register, which created a live company and
-- an ADMIN who could sign in immediately. Anyone who found the URL had a workspace. That is the wrong
-- door for a product sold by a person: the vendor wants to know who is asking, decide, and only then
-- hand over an account.
--
-- So the public surface stores an *enquiry* instead. No company, no user, no password, nothing that
-- can be logged into — just the details of someone who asked, and a status the vendor moves. The
-- account is created later, by the platform owner, through the console route that already exists.
--
-- Deliberately outside RLS, like `users`, `invitations` and `companies`: the row is written by an
-- anonymous caller who has no tenant, so a policy keyed on `calyvora.company_id` could never pass.
-- Nothing here is a tenant's data — it is the vendor's own sales queue.

create table trial_requests (
    id              uuid primary key,
    company_name    varchar(200) not null,
    contact_name    varchar(200) not null,
    email           varchar(255) not null,
    phone           varchar(40),
    team_size       varchar(40),
    note            varchar(2000),
    status          varchar(16) not null default 'NEW',
    -- Which page sent them, so the vendor can tell an Orbit enquiry from an HR-services one.
    source          varchar(80),
    created_at      timestamptz not null default now(),
    decided_at      timestamptz,
    -- Set on approval: the company that was provisioned for them. Keeps the enquiry and the customer
    -- joined up, so a second request from the same person shows what already happened last time.
    company_id      uuid references companies(id) on delete set null,
    constraint trial_requests_status_check check (status in ('NEW', 'APPROVED', 'DECLINED'))
);

create index trial_requests_status_idx on trial_requests (status, created_at desc);

-- One open enquiry per address. Someone who clicks the button three times because nothing visibly
-- happened must not produce three rows in the queue — but once a request is decided, that same
-- person may come back and ask again, which is why the constraint is partial rather than a plain
-- unique on email.
create unique index trial_requests_open_email_idx on trial_requests (lower(email)) where status = 'NEW';
