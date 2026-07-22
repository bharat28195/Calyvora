-- V17__document_os.sql — Documents module (founder feedback D2 + D3): a template library plus
-- generated documents. Fill in an employee → a proper letter (joining / relieving / experience …)
-- is rendered from the template's merge fields and stored. Tenant-owned; RLS-protected (SD-2).

create table document_templates (
    id          uuid primary key,
    company_id  uuid not null references companies(id),
    name        varchar(160) not null,
    kind        varchar(32)  not null default 'CUSTOM',  -- OFFER_LETTER | JOINING_LETTER | RELIEVING_LETTER
                                                         -- | EXPERIENCE_LETTER | PROMOTION_LETTER | CUSTOM
    description varchar(400),
    body        text         not null,                   -- markdown-ish text with {{merge.fields}}
    built_in    boolean      not null default false,      -- seeded starter template (still editable)
    created_by  uuid references users(id),
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);
create index idx_document_templates_company on document_templates(company_id);

create table generated_documents (
    id          uuid primary key,
    company_id  uuid not null references companies(id),
    template_id uuid references document_templates(id) on delete set null,
    employee_id uuid references employees(id) on delete set null,
    title       varchar(200) not null,
    kind        varchar(32)  not null default 'CUSTOM',
    body        text         not null,                   -- fully rendered, frozen at generation time
    generated_by uuid references users(id),
    created_at  timestamptz  not null default now()
);
create index idx_generated_documents_company on generated_documents(company_id);
create index idx_generated_documents_employee on generated_documents(employee_id);

-- Row-Level Security for both new tenant tables (mirrors V12/V16).
alter table document_templates enable row level security;
alter table document_templates force row level security;
create policy tenant_isolation on document_templates
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table generated_documents enable row level security;
alter table generated_documents force row level security;
create policy tenant_isolation on generated_documents
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
