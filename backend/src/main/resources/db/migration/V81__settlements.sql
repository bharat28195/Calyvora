-- PD-73: full and final settlement, worked out from payroll when someone leaves.
--
-- One per departing employee. While DRAFT it is recomputed every time it is opened, from the payroll
-- engine (the final month's payslip), leave balances, service length, notice served and unpaid
-- expense claims, plus the choices and manual lines HR adds here. Approving freezes the statement
-- (statement_json, net_payable) and issues the F&F letter; marking it paid records when and how.
create table settlements (
    id                  uuid primary key,
    company_id          uuid not null references companies(id) on delete cascade,
    employee_id         uuid not null references employees(id) on delete cascade,
    status              varchar(12) not null default 'DRAFT',      -- DRAFT | APPROVED | PAID
    encash_types        varchar(100) not null default 'VACATION',  -- comma-separated leave types
    encash_days         numeric(7,2),                              -- null = the balance on the last day
    encash_basis        varchar(12) not null default 'BASIC_26',   -- BASIC_26 | BASIC_30 | GROSS_30
    notice_days         integer,                                   -- null = from the letterpad's notice period
    notice_waived       boolean not null default false,
    include_gratuity    boolean not null default true,
    extra_lines         text,                                      -- JSON [{label, amount, deduction}]
    note                varchar(1000),
    statement_json      text,
    net_payable         numeric(14,2),
    approved_by         uuid,
    approved_at         timestamptz,
    document_id         uuid references generated_documents(id) on delete set null,
    paid_on             date,
    paid_reference      varchar(120),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    constraint uq_settlements_employee unique (employee_id)
);
create index idx_settlements_company on settlements (company_id);

alter table settlements enable row level security;
alter table settlements force row level security;
create policy tenant_isolation on settlements
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
