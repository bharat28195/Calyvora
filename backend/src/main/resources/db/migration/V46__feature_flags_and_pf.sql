-- V46__feature_flags_and_pf.sql — statutory payroll, behind a switch.
--
-- Provident Fund is the first piece of Indian statutory payroll, and it is the first thing that can
-- put a wrong number on somebody's payslip. It ships OFF for every company, including existing ones,
-- and is turned on per customer once the numbers have been checked against their real payroll.
--
-- The flag is deliberately per company rather than a global config: a compliance engine is trusted
-- one customer at a time, and "turn it off for that one company while we look at it" must not mean a
-- redeploy.

-- ---------------------------------------------------------------------------
-- company_features — per-company capability switches, owned by the vendor.
--
-- NO ROW-LEVEL SECURITY, and that is the point rather than an oversight. The platform owner toggles
-- these for OTHER tenants, from a session bound to its own platform company; an RLS policy keyed on
-- calyvora.company_id would make every such write invisible and silently do nothing. It sits with
-- companies, users and subscriptions on the deliberately un-RLS'd control surface (see V12's note),
-- and it holds no personal data — only a company id, a feature name and a boolean.
-- ---------------------------------------------------------------------------
create table company_features (
    id         uuid primary key,
    company_id uuid        not null references companies(id),
    feature    varchar(48) not null,
    enabled    boolean     not null default false,
    updated_at timestamptz not null default now(),
    constraint uq_company_feature unique (company_id, feature)
);
create index idx_company_features_company on company_features(company_id);

-- ---------------------------------------------------------------------------
-- pf_settings — one row per company, holding the rates as the law states them.
--
-- Rates are columns rather than constants in code because they are set by statute and statutes
-- change: the ceiling has moved before and is expected to move again. A rate change should be an
-- UPDATE and a note in the release, not a redeploy — and a company that agreed a different
-- arrangement with its auditor can hold it here rather than forcing a fork.
--
-- Defaults are the EPF & MP Act rates in force for 2026:
--   employee 12% of PF wages; employer 12%, of which 8.33% goes to the Pension Scheme (EPS) capped
--   at the ceiling and the balance to EPF; admin charges 0.5%; EDLI 0.5%.
-- ---------------------------------------------------------------------------
create table pf_settings (
    company_id          uuid primary key references companies(id),
    -- The statutory wage ceiling for PF. 15000/month at the time of writing.
    wage_ceiling        numeric(12, 2) not null default 15000.00,
    -- Whether contributions are capped at the ceiling (the common choice) or computed on actual PF
    -- wages however high they run. Both are lawful; the difference is thousands of rupees a month per
    -- employee, so it is a setting rather than an assumption.
    restrict_to_ceiling boolean        not null default true,
    employee_rate       numeric(5, 2)  not null default 12.00,
    employer_rate       numeric(5, 2)  not null default 12.00,
    eps_rate            numeric(5, 2)  not null default 8.33,
    admin_charge_rate   numeric(5, 2)  not null default 0.50,
    edli_rate           numeric(5, 2)  not null default 0.50,
    updated_at          timestamptz    not null default now(),
    constraint ck_pf_rates check (
        employee_rate >= 0 and employer_rate >= 0 and eps_rate >= 0
        and admin_charge_rate >= 0 and edli_rate >= 0
        and eps_rate <= employer_rate      -- EPS is carved out of the employer's 12%, never added to it
        and wage_ceiling > 0
    )
);

alter table pf_settings enable row level security;
alter table pf_settings force row level security;
create policy tenant_isolation on pf_settings
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

-- No seeding. A company with no pf_settings row uses the statutory defaults from the entity, and no
-- company has the feature switched on, so nothing changes for anybody on the day this deploys.
