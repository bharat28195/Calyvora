-- V62__company_files.sql — files in the company documents area (policies as PDFs, forms, handbooks).
--
-- Pages cover documents written in Orbit; this covers the ones that already exist as files. A file
-- belongs to a space (a folder such as "Employee Handbook"), is uploaded by a publisher, and is
-- readable by everyone in the company.
--
-- Metadata and bytes are separate tables on purpose. Listing a folder reads only company_files and
-- never drags megabytes of bytea through the query. company_file_blobs holds the bytes when they are
-- stored in the database (storage = 'DB', the default); when Cloudflare R2 is configured the bytes
-- live there under storage_key and no blob row exists.
--
-- Both are tenant data: row-level security, forced, and company_id foreign keys that cascade (V58),
-- so deleting a company removes its files with it.
create table company_files (
    id           uuid primary key,
    company_id   uuid         not null references companies(id) on delete cascade,
    space_id     uuid         not null references spaces(id) on delete cascade,
    title        varchar(200) not null,
    file_name    varchar(255) not null,
    content_type varchar(120) not null,
    size_bytes   bigint       not null,
    storage      varchar(8)   not null,      -- DB | R2
    storage_key  varchar(300),               -- the R2 object key; null for DB storage
    uploaded_by  uuid         references users(id) on delete set null,
    created_at   timestamptz  not null default now()
);
create index idx_company_files_space on company_files(company_id, space_id, created_at desc);

create table company_file_blobs (
    file_id    uuid  primary key references company_files(id) on delete cascade,
    company_id uuid  not null references companies(id) on delete cascade,
    data       bytea not null
);

alter table company_files enable row level security;
alter table company_files force row level security;
create policy tenant_isolation on company_files
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table company_file_blobs enable row level security;
alter table company_file_blobs force row level security;
create policy tenant_isolation on company_file_blobs
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
