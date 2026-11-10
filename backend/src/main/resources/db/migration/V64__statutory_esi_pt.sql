-- V64__statutory_esi_pt.sql — ESI and professional tax join PF in the statutory engine.
--
-- Same switch as PF: nothing here does anything until the vendor turns STATUTORY_PAYROLL on for a
-- company (V46). Within that, ESI and professional tax each have their own company-level switch,
-- because a company can be registered for one and not the other — an office of forty engineers all
-- earning above the ESI ceiling has no ESI code at all, but still owes professional tax in most states.

-- ---------------------------------------------------------------------------
-- statutory_settings — one row per company: the ESI rates and the registration numbers every
-- statutory file has to carry (PF establishment code on the ECR, ESI employer code on the monthly
-- contribution file, TAN on Form 24Q and Form 16).
--
-- The registration numbers live here rather than on company_settings because only payroll reads them
-- and only people who manage payroll may change them; company_settings is editable by any admin.
-- ---------------------------------------------------------------------------
create table statutory_settings (
    company_id            uuid primary key references companies(id) on delete cascade,

    esi_enabled           boolean        not null default false,
    -- ESI Act rates in force since 1 July 2019: employee 0.75%, employer 3.25% of gross wages.
    esi_employee_rate     numeric(5, 2)  not null default 0.75,
    esi_employer_rate     numeric(5, 2)  not null default 3.25,
    -- Employees whose gross wages are at or below this at the start of a contribution period are
    -- covered for that whole period. 21000/month since 1 January 2017.
    esi_wage_ceiling      numeric(12, 2) not null default 21000.00,

    pt_enabled            boolean        not null default false,

    pf_establishment_code varchar(32),
    esi_employer_code     varchar(32),
    tan                   varchar(16),
    company_pan           varchar(16),
    pt_registration_no    varchar(48),

    updated_at            timestamptz    not null default now(),
    constraint ck_esi_rates check (
        esi_employee_rate >= 0 and esi_employer_rate >= 0
        and esi_employee_rate <= 100 and esi_employer_rate <= 100
        and esi_wage_ceiling > 0
    )
);

alter table statutory_settings enable row level security;
alter table statutory_settings force row level security;
create policy tenant_isolation on statutory_settings
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

-- ---------------------------------------------------------------------------
-- Gender on the statutory profile. Maharashtra's professional tax exempts women earning up to
-- 25,000 a month but men only up to 7,500; without this the engine would deduct PT from women the
-- law exempts. Nullable — unknown is treated by the calculator as the slab that does NOT exempt, and
-- the readiness check reports it, rather than guessing from a name.
-- ---------------------------------------------------------------------------
alter table employee_finance add column gender varchar(16);
