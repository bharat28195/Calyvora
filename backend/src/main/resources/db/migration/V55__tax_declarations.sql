-- V55__tax_declarations.sql — what each employee declares for income tax, and under which regime.
--
-- India runs two tax regimes in parallel. The new one has wider slabs and a larger rebate but
-- disallows almost every deduction; the old one taxes more steeply and lets people subtract what
-- they have invested and spent. Which is cheaper depends entirely on the individual, so the choice
-- is per employee per financial year — never a company-wide setting.
--
-- A financial year is stored as the label people actually use ('2026-27') rather than a start date.
-- It is what appears on every form and payslip, it sorts correctly as text, and it makes the unique
-- constraint below say exactly what it means: one declaration per person per year.

create table tax_declarations (
    id              uuid primary key,
    company_id      uuid not null references companies(id),
    employee_id     uuid not null references employees(id) on delete cascade,
    financial_year  varchar(9) not null,
    regime          varchar(8) not null default 'NEW',
    -- DRAFT while the employee is still editing; SUBMITTED once they have declared it and payroll
    -- may rely on it. Kept rather than inferred from submitted_at so a declaration can be reopened.
    status          varchar(16) not null default 'DRAFT',
    submitted_at    timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    constraint uq_tax_declaration_year unique (company_id, employee_id, financial_year)
);
create index idx_tax_declarations_company_year on tax_declarations(company_id, financial_year);

-- One row per deduction claimed. A child table rather than a column each, because the list of
-- sections is a tax-law fact that changes with the Finance Act — adding 80CCH should be an enum
-- value, not a migration — and rather than JSON because nothing else here stores JSON and the
-- amounts want to be summable in SQL.
--
-- company_id is duplicated onto the child for Row-Level Security: the policy has to be enforceable
-- on this table directly, not only through a join to its parent.
create table tax_declaration_items (
    id              uuid primary key,
    company_id      uuid not null references companies(id),
    declaration_id  uuid not null references tax_declarations(id) on delete cascade,
    deduction       varchar(40) not null,
    -- What the employee claimed, uncapped. The statutory ceiling is applied when the tax is
    -- computed, not on the way in, so that a later change to a limit reprices existing declarations
    -- and so the screen can show "you claimed X, Y is allowable" rather than silently trimming.
    amount          numeric(14,2) not null default 0,
    constraint uq_tax_declaration_item unique (declaration_id, deduction)
);
create index idx_tax_declaration_items_declaration on tax_declaration_items(declaration_id);

alter table tax_declarations enable row level security;
alter table tax_declarations force row level security;
create policy tenant_isolation on tax_declarations
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table tax_declaration_items enable row level security;
alter table tax_declaration_items force row level security;
create policy tenant_isolation on tax_declaration_items
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

-- Whether HR has opened declarations for the year. Employees choose their own regime; HR decides
-- when the window is open, which is the real-world sequence — declarations are collected at the
-- start of the year and frozen before the last payroll of it.
alter table company_settings
    add column tax_declarations_open boolean not null default true;
