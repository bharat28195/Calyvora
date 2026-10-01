-- V25__payslip_template.sql — Configurable payslip template (founder: "add template for creating payslip").
--
-- A company defines its payslip structure once as an ordered list of components (earnings and
-- deductions), each computed as a percent of gross, a percent of the basis earning, a fixed amount,
-- or the remainder of gross. Payslip generation reads this template, so payroll structure is
-- configurable rather than hard-coded. Seeded with a sensible default set on first open.

create table payslip_components (
    id          uuid primary key,
    company_id  uuid not null references companies(id),
    name        varchar(60) not null,
    kind        varchar(16) not null,   -- EARNING | DEDUCTION
    calc        varchar(20) not null,   -- PERCENT_OF_GROSS | PERCENT_OF_BASIC | FIXED | REMAINDER
    value       numeric(12,2),          -- percent or fixed amount; null for REMAINDER
    is_basis    boolean not null default false,
    sort_order  integer not null default 0,
    created_at  timestamptz not null default now()
);
create index idx_payslip_components_company on payslip_components(company_id, sort_order);

alter table payslip_components enable row level security;
alter table payslip_components force row level security;
create policy tenant_isolation on payslip_components
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
