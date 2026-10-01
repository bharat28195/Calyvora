-- V20__holidays.sql — Company holiday calendar (founder request, 2026-07-22).
--
-- Closes the gap logged as debt when attendance shipped: the work-week was hardcoded and there was no
-- holiday list, so a public holiday looked like an unmarked day. A holiday now resolves the day for
-- everyone automatically, and feeds the "upcoming" widget on the dashboard.

create table holidays (
    id          uuid primary key,
    company_id  uuid not null references companies(id),
    name        varchar(160) not null,
    on_date     date not null,
    /** OPTIONAL holidays are offered but not automatic — people choose to take them. */
    optional    boolean not null default false,
    note        varchar(400),
    created_by  uuid references users(id),
    created_at  timestamptz not null default now(),
    constraint uq_holiday_company_day unique (company_id, on_date, name)
);
create index idx_holidays_company_date on holidays(company_id, on_date);

alter table holidays enable row level security;
alter table holidays force row level security;
create policy tenant_isolation on holidays
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
