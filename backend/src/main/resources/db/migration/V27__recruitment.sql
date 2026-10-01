-- V27__recruitment.sql — Recruitment / ATS (the flagship module Keka, Zoho People & BambooHR lead with).
--
-- Job openings hold the requisitions; candidates attach to an opening and move through a hiring
-- pipeline (applied → screening → interview → offer → hired / rejected). Both tenant-isolated by RLS.

create table job_openings (
    id               uuid primary key,
    company_id       uuid not null references companies(id),
    title            varchar(140) not null,
    department_id    uuid references departments(id),
    location         varchar(120),
    employment_type  varchar(24),
    description      text,
    positions        integer not null default 1,
    status           varchar(16) not null default 'OPEN',   -- OPEN | ON_HOLD | CLOSED
    created_by       uuid references users(id),
    created_at       timestamptz not null default now()
);
create index idx_job_openings_company on job_openings(company_id, created_at desc);

create table candidates (
    id           uuid primary key,
    company_id   uuid not null references companies(id),
    job_id       uuid not null references job_openings(id) on delete cascade,
    name         varchar(140) not null,
    email        varchar(200),
    phone        varchar(40),
    resume_url   varchar(500),
    source       varchar(60),
    stage        varchar(16) not null default 'APPLIED',    -- APPLIED | SCREENING | INTERVIEW | OFFER | HIRED | REJECTED
    rating       integer,
    notes        text,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);
create index idx_candidates_job on candidates(job_id);
create index idx_candidates_company on candidates(company_id);

alter table job_openings enable row level security;
alter table job_openings force row level security;
create policy tenant_isolation on job_openings
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table candidates enable row level security;
alter table candidates force row level security;
create policy tenant_isolation on candidates
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
