-- V24__performance_reviews.sql — Performance review cycles (founder request C.7).
--
-- The annual loop: an Owner/Admin opens a named cycle for a period. Every active employee gets one
-- review row, snapshotting who their manager was at open time (the org chart can change mid-cycle,
-- but a review belongs to the relationship it started under). The member writes a self-assessment;
-- their manager writes the official review, a 1–5 rating, and a hike recommendation; an admin
-- approves, and approval writes the raise straight into compensation_records — one flow from review
-- to raise, so "give a hike based on what they achieved" is a single, auditable action.

create table review_cycles (
    id            uuid primary key,
    company_id    uuid not null references companies(id),
    name          varchar(120) not null,
    period_start  date not null,
    period_end    date not null,
    status        varchar(16) not null default 'OPEN',   -- OPEN | CLOSED
    created_by    uuid references users(id),
    created_at    timestamptz not null default now()
);
create index idx_review_cycles_company on review_cycles(company_id, created_at desc);

create table performance_reviews (
    id                   uuid primary key,
    company_id           uuid not null references companies(id),
    cycle_id             uuid not null references review_cycles(id) on delete cascade,
    employee_id          uuid not null references employees(id) on delete cascade,
    -- Snapshot of the reporting manager when the cycle opened (an employee id, or null for top of chain).
    manager_id           uuid references employees(id),
    status               varchar(24) not null default 'PENDING_SELF',
                         -- PENDING_SELF | PENDING_MANAGER | SUBMITTED | APPROVED | CLOSED
    self_assessment      text,
    self_submitted_at    timestamptz,
    manager_rating       integer,                          -- 1..5, set by the manager
    manager_summary      text,
    strengths            text,
    improvements         text,
    hike_type            varchar(16),                      -- PERCENT | NEW_SALARY | NONE
    hike_percent         numeric(5,2),
    proposed_salary      numeric(12,2),
    hike_note            varchar(500),
    manager_submitted_at timestamptz,
    decided_by           uuid references users(id),
    decided_at           timestamptz,
    -- The compensation_records row created when an admin approves a hike (null if no raise applied).
    applied_comp_id      uuid,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    unique (cycle_id, employee_id)
);
create index idx_perf_reviews_company  on performance_reviews(company_id);
create index idx_perf_reviews_cycle    on performance_reviews(cycle_id);
create index idx_perf_reviews_employee on performance_reviews(employee_id);
create index idx_perf_reviews_manager  on performance_reviews(manager_id);

alter table review_cycles enable row level security;
alter table review_cycles force row level security;
create policy tenant_isolation on review_cycles
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table performance_reviews enable row level security;
alter table performance_reviews force row level security;
create policy tenant_isolation on performance_reviews
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
