-- V26__subscriptions.sql — Per-employee subscription billing (founder: sell the HR module; charge
-- ₹100 per employee per month, metered on active headcount each month).
--
-- One subscription per company. The monthly charge is price_per_employee × active headcount, so a
-- company with 5 people in January and 20 in February is billed for 20 in February. paid_through
-- records the last settled month.

create table subscriptions (
    id                  uuid primary key,
    company_id          uuid not null references companies(id) unique,
    plan                varchar(40) not null default 'PER_EMPLOYEE',
    price_per_employee  numeric(10,2) not null default 100,
    currency            varchar(3) not null default 'INR',
    status              varchar(16) not null default 'TRIALING',  -- TRIALING | ACTIVE | PAST_DUE | CANCELLED
    paid_through        varchar(7),                               -- 'YYYY-MM'
    started_at          timestamptz,
    trial_ends_at       timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

alter table subscriptions enable row level security;
alter table subscriptions force row level security;
create policy tenant_isolation on subscriptions
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
