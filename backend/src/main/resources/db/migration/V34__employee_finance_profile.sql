-- V34__employee_finance_profile.sql — the "My Finances" record behind an employee's pay.
--
-- Everything a payslip and a statutory filing need that isn't salary: how the person is paid (bank),
-- what they're enrolled in (PF / ESI / professional tax), and the identity used on those filings
-- (PAN, date of birth, parent's name). Kept in its own table rather than widening `employees`
-- because it is materially more sensitive than a directory row — the directory is readable by every
-- colleague, and none of this ever should be.
--
-- One row per employee, created on demand. Tenant-isolated like every other people-os table.

create table employee_finance (
    employee_id       uuid primary key references employees(id) on delete cascade,
    company_id        uuid not null references companies(id),

    -- How salary reaches them.
    payment_mode      varchar(20) not null default 'BANK_TRANSFER',  -- BANK_TRANSFER / CHEQUE / CASH
    bank_name         varchar(120),
    bank_account_no   varchar(40),
    bank_ifsc         varchar(20),
    bank_account_name varchar(120),
    bank_branch       varchar(120),

    -- Provident fund.
    pf_status         varchar(20) not null default 'NOT_ELIGIBLE',   -- ENABLED / NOT_ELIGIBLE
    pf_number         varchar(40),
    uan               varchar(20),
    pf_join_date      date,
    pf_account_name   varchar(120),

    -- Employees' State Insurance.
    esi_status        varchar(20) not null default 'NOT_ELIGIBLE',   -- ELIGIBLE / NOT_ELIGIBLE
    esi_number        varchar(40),

    -- Professional tax is levied per state, so both matter on the payslip.
    pt_state          varchar(60),
    pt_location       varchar(60),

    -- Identity as it appears on statutory filings.
    pan_number        varchar(20),
    pan_verified      boolean not null default false,
    date_of_birth     date,
    parent_name       varchar(120),

    updated_at        timestamptz not null default now()
);
create index idx_employee_finance_company on employee_finance(company_id);

alter table employee_finance enable row level security;
alter table employee_finance force row level security;
create policy tenant_isolation on employee_finance
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
