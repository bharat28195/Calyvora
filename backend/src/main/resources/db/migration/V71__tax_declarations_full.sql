-- V71__tax_declarations_full.sql — the full Form 124 declaration, proofs and HR review (PD-60).
--
-- What this adds, and why each piece is needed for the tax to be right rather than merely shown:
--   * Each declared line gets a proof status and an accepted amount. Until the proof deadline payroll
--     withholds on what was declared; after it, only on what HR accepted (the usual Indian practice,
--     and the one that stops a year-end lump of tax from unproved claims).
--   * Rent is recorded month range by month range, with the city, so the HRA exemption is worked out
--     by the rules (least of three, month by month) instead of being typed in by the employee.
--   * Houses are recorded with their interest (and rent, if let out), so the ₹2,00,000 limit on a
--     self-occupied home and the set-off of a let-out loss are applied, not assumed.
--   * Income from a previous employer this year (what Form 130 / Form 16 from them shows), so the
--     slabs are applied to the whole year's income and their TDS is credited.
--   * Opening balances also carry PF and professional tax, which count towards Section 123 and 19.
--   * Salary components can be marked as HRA or LTA, which is how the HRA received is known.
--
-- Every new table is tenant data under FORCE row level security, and every data step binds each
-- company in turn: Flyway runs with no tenant, and a plain UPDATE here would change nothing (V59, V70).

-- ---- declared lines: proofs and review ----
alter table tax_declaration_items
    add column accepted_amount numeric(14,2),
    add column proof_status    varchar(16) not null default 'NONE',   -- NONE|SUBMITTED|ACCEPTED|PARTIAL|REJECTED
    add column review_note     varchar(400),
    add column reviewed_by     uuid references users(id) on delete set null,
    add column reviewed_at     timestamptz,
    add column detail          varchar(300);

-- ---- the declaration itself ----
alter table tax_declarations
    add column parents_senior      boolean not null default false,
    add column prev_employer_name  varchar(160),
    add column prev_employer_tan   varchar(10),
    add column prev_income         numeric(14,2),
    add column prev_tds            numeric(14,2),
    add column prev_pf             numeric(14,2),
    add column prev_pt             numeric(14,2),
    add column prev_status         varchar(16) not null default 'NONE', -- NONE|SUBMITTED|ACCEPTED|REJECTED
    add column prev_review_note    varchar(400);

-- ---- rent, for the HRA exemption ----
create table tax_rent_periods (
    id                    uuid primary key,
    company_id            uuid not null references companies(id) on delete cascade,
    declaration_id        uuid not null references tax_declarations(id) on delete cascade,
    from_month            varchar(7) not null,       -- yyyy-MM, inclusive
    to_month              varchar(7) not null,
    monthly_rent          numeric(14,2) not null,
    city                  varchar(80) not null,
    metro                 boolean not null,
    landlord_name         varchar(160),
    landlord_pan          varchar(10),
    landlord_address      varchar(300),
    landlord_relationship varchar(60),               -- Form 124 asks whether the landlord is a relative
    accepted_rent         numeric(14,2),             -- monthly, once reviewed
    proof_status          varchar(16) not null default 'NONE',
    review_note           varchar(400),
    reviewed_by           uuid references users(id) on delete set null,
    reviewed_at           timestamptz,
    constraint ck_rent_months check (from_month <= to_month),
    constraint ck_rent_amount check (monthly_rent >= 0)
);
create index idx_tax_rent_declaration on tax_rent_periods(declaration_id);

-- ---- houses owned ----
create table tax_house_properties (
    id                uuid primary key,
    company_id        uuid not null references companies(id) on delete cascade,
    declaration_id    uuid not null references tax_declarations(id) on delete cascade,
    let_out           boolean not null default false,
    address           varchar(300),
    lender_name       varchar(160),
    lender_pan        varchar(10),
    interest          numeric(14,2) not null default 0,
    annual_rent       numeric(14,2) not null default 0,
    municipal_tax     numeric(14,2) not null default 0,
    accepted_interest numeric(14,2),
    proof_status      varchar(16) not null default 'NONE',
    review_note       varchar(400),
    reviewed_by       uuid references users(id) on delete set null,
    reviewed_at       timestamptz,
    constraint ck_house_amounts check (interest >= 0 and annual_rent >= 0 and municipal_tax >= 0)
);
create index idx_tax_house_declaration on tax_house_properties(declaration_id);

-- ---- proof files ----
-- Bytes in the database, as letterpads are (V52): there is no object store yet, files are capped at
-- 5 MB in the application, and a year's proofs per person are a handful of files.
create table tax_proofs (
    id             uuid primary key,
    company_id     uuid not null references companies(id) on delete cascade,
    declaration_id uuid not null references tax_declarations(id) on delete cascade,
    owner_type     varchar(12) not null,             -- ITEM|RENT|HOUSE|PREVIOUS
    owner_id       uuid not null,
    file_name      varchar(255) not null,
    content_type   varchar(120) not null,
    size_bytes     integer not null,
    content        bytea not null,
    uploaded_by    uuid references users(id) on delete set null,
    created_at     timestamptz not null default now()
);
create index idx_tax_proofs_owner on tax_proofs(declaration_id, owner_type, owner_id);

alter table tax_rent_periods enable row level security;
alter table tax_rent_periods force row level security;
create policy tenant_isolation on tax_rent_periods
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
alter table tax_house_properties enable row level security;
alter table tax_house_properties force row level security;
create policy tenant_isolation on tax_house_properties
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
alter table tax_proofs enable row level security;
alter table tax_proofs force row level security;
create policy tenant_isolation on tax_proofs
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

-- ---- the proof window ----
alter table company_settings
    add column tax_proofs_open    boolean not null default false,
    add column tax_proof_deadline date;

-- ---- opening balances also carry PF and professional tax ----
alter table tds_opening_balances
    add column employee_pf      numeric(14,2) not null default 0,
    add column professional_tax numeric(14,2) not null default 0;

-- ---- salary components that are HRA or LTA ----
alter table payslip_components add column tax_tag varchar(8);   -- HRA|LTA|null

-- ---- data, one company at a time ----
do $$
declare
    c uuid;
begin
    for c in select id from companies loop
        perform set_config('calyvora.company_id', c::text, true);

        update payslip_components set tax_tag = 'HRA'
         where company_id = c and kind = 'EARNING'
           and (lower(name) like '%house rent%' or lower(name) = 'hra' or lower(name) like 'hra %');
        update payslip_components set tax_tag = 'LTA'
         where company_id = c and kind = 'EARNING'
           and (lower(name) like '%leave travel%' or lower(name) = 'lta' or lower(name) like 'lta %');

        -- A parents' claim above ₹25,000 can only be lawful if a parent is a senior citizen, so a
        -- declaration that made one is read as having said so — rather than quietly losing the excess.
        update tax_declarations d set parents_senior = true
         where d.company_id = c
           and exists (select 1 from tax_declaration_items i
                        where i.declaration_id = d.id and i.deduction = 'SECTION_80D_PARENTS' and i.amount > 25000);

        -- A self-occupied home loan becomes a house, which is where its interest now lives.
        insert into tax_house_properties (id, company_id, declaration_id, let_out, interest)
        select gen_random_uuid(), i.company_id, i.declaration_id, false, i.amount
          from tax_declaration_items i
         where i.company_id = c and i.deduction = 'HOME_LOAN_INTEREST' and i.amount > 0;
        delete from tax_declaration_items where company_id = c and deduction = 'HOME_LOAN_INTEREST';

        -- The old single lines become the closest new line, so no claim is lost.
        update tax_declaration_items set deduction = 'OTHER_123'              where company_id = c and deduction = 'SECTION_80C';
        update tax_declaration_items set deduction = 'NPS_ADDITIONAL'         where company_id = c and deduction = 'SECTION_80CCD_1B';
        update tax_declaration_items set deduction = 'EMPLOYER_NPS'           where company_id = c and deduction = 'SECTION_80CCD_2';
        update tax_declaration_items set deduction = 'HEALTH_SELF_PREMIUM'    where company_id = c and deduction = 'SECTION_80D_SELF';
        update tax_declaration_items set deduction = 'HEALTH_PARENTS_PREMIUM' where company_id = c and deduction = 'SECTION_80D_PARENTS';
        update tax_declaration_items set deduction = 'EDUCATION_LOAN'         where company_id = c and deduction = 'SECTION_80E';
        update tax_declaration_items set deduction = 'DONATION_100'           where company_id = c and deduction = 'SECTION_80G';
        update tax_declaration_items set deduction = 'SAVINGS_INTEREST'       where company_id = c and deduction = 'SECTION_80TTA';
        update tax_declaration_items set deduction = 'LTA'                    where company_id = c and deduction = 'LTA_EXEMPTION';
        update tax_declaration_items set deduction = 'PROFESSIONAL_TAX_OTHER' where company_id = c and deduction = 'PROFESSIONAL_TAX';
    end loop;
    perform set_config('calyvora.company_id', '', true);
end $$;
