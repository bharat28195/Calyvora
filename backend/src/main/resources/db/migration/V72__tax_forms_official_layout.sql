-- V72: what Form 124 and Form 130 (Parts A and B) print that Orbit did not yet hold.
--
-- Schema only — every new column is nullable or defaulted and no existing row changes, so there is
-- no per-company backfill (and nothing for RLS to hide from Flyway).

-- Form 124 item 1: "Name and address of the employee". Kept on the year's declaration: it is the
-- address the employee certified that year.
alter table tax_declarations add column employee_address varchar(400);

-- Form 124 item 3: the lender's address and whether it is a financial institution, the employer or
-- someone else.
alter table tax_house_properties add column lender_address varchar(300);
alter table tax_house_properties add column lender_type varchar(24);

-- The person responsible for deducting tax, who signs both forms' verification, and the CIT (TDS)
-- whose jurisdiction the TAN falls under (Form 130 Part A).
alter table company_settings add column tds_signer_name varchar(160);
alter table company_settings add column tds_signer_parent varchar(160);
alter table company_settings add column tds_signer_designation varchar(120);
alter table company_settings add column tds_signer_place varchar(80);
alter table company_settings add column cit_tds_address varchar(400);

-- The challan each month's TDS was paid on (Form 130 Part A, section II). Several months may share
-- one challan, so the same BSR / date / serial can appear on more than one row.
create table tds_challans (
    id             uuid primary key,
    company_id     uuid not null references companies(id) on delete cascade,
    month          varchar(7) not null,            -- YYYY-MM of the salary the tax was deducted from
    bsr_code       varchar(7) not null,
    deposit_date   date not null,
    challan_serial varchar(5) not null,
    amount         numeric(14, 2) not null,
    updated_at     timestamptz not null default now(),
    unique (company_id, month)
);

-- The receipt number of each quarter's 24Q as filed (Form 130 Part A, the quarterly summary).
create table tds_quarter_returns (
    id          uuid primary key,
    company_id  uuid not null references companies(id) on delete cascade,
    quarter     varchar(10) not null,              -- e.g. 2026-27-Q3
    receipt_no  varchar(16) not null,
    updated_at  timestamptz not null default now(),
    unique (company_id, quarter)
);

alter table tds_challans enable row level security;
alter table tds_challans force row level security;
create policy tenant_isolation on tds_challans
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table tds_quarter_returns enable row level security;
alter table tds_quarter_returns force row level security;
create policy tenant_isolation on tds_quarter_returns
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
