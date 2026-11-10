-- V66__tds_opening_and_earned_gross.sql — income tax that knows the whole year.
--
-- Income tax is levied on the year, but a company usually starts using Orbit part-way through one,
-- and employees join from other employers. Both mean salary was already paid, and tax already
-- deducted, this year somewhere Orbit cannot see. Without those figures the year's tax is projected
-- on the wrong income and the withholding for the rest of the year is wrong with it. This table
-- records them per employee per financial year (the "Form 12B" figures for a new joiner, or the old
-- payroll system's year-to-date for a company that moved mid-year).

create table tds_opening_balances (
    id               uuid primary key,
    company_id       uuid           not null references companies(id) on delete cascade,
    employee_id      uuid           not null,
    financial_year   varchar(7)     not null,              -- 2026-27
    -- The last month these figures cover, inclusive (YYYY-MM). Orbit takes over from the month after.
    covered_through  varchar(7)     not null,
    income           numeric(14, 2) not null default 0,
    tds              numeric(14, 2) not null default 0,
    note             varchar(200),
    updated_at       timestamptz    not null default now(),
    constraint uq_tds_opening unique (company_id, employee_id, financial_year),
    constraint ck_tds_opening_amounts check (income >= 0 and tds >= 0)
);
create index idx_tds_opening_company on tds_opening_balances(company_id, financial_year);

alter table tds_opening_balances enable row level security;
alter table tds_opening_balances force row level security;
create policy tenant_isolation on tds_opening_balances
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

-- Labour Welfare Fund: a company switch like ESI and PT, and the two shares on each locked payslip.
alter table statutory_settings add column lwf_enabled boolean not null default false;
alter table payslip_snapshots add column lwf_employee numeric(10, 2);
alter table payslip_snapshots add column lwf_employer numeric(10, 2);

-- What was actually paid in a finalised month, after loss of pay — the income tax is levied on, and
-- the gross the returns report. Back-filled from gross for any month finalised before this column
-- existed (loss of pay on those is read from the stored payslip when it matters).
alter table payslip_snapshots add column earned_gross numeric(14, 2);
update payslip_snapshots set earned_gross = gross where earned_gross is null;
alter table payslip_snapshots alter column earned_gross set not null;
