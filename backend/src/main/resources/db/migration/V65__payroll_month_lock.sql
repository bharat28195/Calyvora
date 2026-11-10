-- V65__payroll_month_lock.sql — a month's payroll can be finalised, and then it never changes.
--
-- Until now every payslip was computed on demand from today's salary, attendance and settings. That is
-- fine for a month still being prepared and wrong for one already paid: change a salary in November
-- and October's payslip silently changed with it, and the PF, ESI and TDS figures already filed for
-- October no longer matched anything Orbit could show. Every statutory return is built on the month
-- as it was paid, so the month has to be frozen at the moment it is paid.
--
-- Finalising a month stores each payslip exactly as it was computed. From then on the payslip, the
-- run and every statutory file for that month are read from here, and income tax for later months
-- knows how much was actually withheld.

create table payroll_months (
    id                uuid primary key,
    company_id        uuid           not null references companies(id) on delete cascade,
    month             varchar(7)     not null,              -- YYYY-MM
    finalized_at      timestamptz    not null default now(),
    finalized_by      uuid,
    employees         integer        not null,
    total_gross       numeric(14, 2) not null,
    total_net         numeric(14, 2) not null,
    total_employer    numeric(14, 2) not null,
    constraint uq_payroll_month unique (company_id, month)
);
create index idx_payroll_months_company on payroll_months(company_id);

alter table payroll_months enable row level security;
alter table payroll_months force row level security;
create policy tenant_isolation on payroll_months
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

-- One row per employee per finalised month. The full payslip is kept as JSON so it can be shown
-- again exactly as issued; the figures every return needs are also columns, so building an ECR or a
-- 24Q is a query rather than a parse of every document.
create table payslip_snapshots (
    id                uuid primary key,
    company_id        uuid           not null references companies(id) on delete cascade,
    month             varchar(7)     not null,
    employee_id       uuid           not null,
    payload           text           not null,
    gross             numeric(14, 2) not null,
    net               numeric(14, 2) not null,
    working_days      integer        not null,
    lop_days          numeric(5, 1)  not null,
    pf_wages          numeric(14, 2),
    employee_pf       numeric(14, 2),
    employer_eps      numeric(14, 2),
    employer_epf      numeric(14, 2),
    esi_wages         numeric(14, 2),
    employee_esi      numeric(14, 2),
    employer_esi      numeric(14, 2),
    professional_tax  numeric(14, 2),
    pt_state          varchar(4),
    income_tax        numeric(14, 2),
    created_at        timestamptz    not null default now(),
    constraint uq_payslip_snapshot unique (company_id, month, employee_id)
);
create index idx_payslip_snapshots_company_month on payslip_snapshots(company_id, month);
create index idx_payslip_snapshots_employee on payslip_snapshots(employee_id);

alter table payslip_snapshots enable row level security;
alter table payslip_snapshots force row level security;
create policy tenant_isolation on payslip_snapshots
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
