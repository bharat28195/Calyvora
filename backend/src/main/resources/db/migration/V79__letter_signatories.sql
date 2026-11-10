-- PD-71: an authorised signatory's signature, and the company seal, printed on letters.
--
-- 1. letter_signatories: who may sign the company's letters — name, title and a signature image.
--    Never deleted, only switched off: an issued letter names its signatory, and its signature must
--    still print on that letter years later.
-- 2. The company seal (stamp), on the letterpad.
-- 3. A template may name its signatory; a letter remembers the one it was issued with.
create table letter_signatories (
    id          uuid primary key,
    company_id  uuid not null references companies(id) on delete cascade,
    name        varchar(120) not null,
    title       varchar(120),
    image       bytea,
    image_type  varchar(64),
    is_default  boolean not null default false,
    active      boolean not null default true,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
create index idx_letter_signatories_company on letter_signatories (company_id);

alter table letter_signatories enable row level security;
alter table letter_signatories force row level security;
create policy tenant_isolation on letter_signatories
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table letterheads add column seal_image bytea;
alter table letterheads add column seal_type varchar(64);

alter table document_templates add column signatory_id uuid references letter_signatories(id);
alter table generated_documents add column signatory_id uuid references letter_signatories(id);
