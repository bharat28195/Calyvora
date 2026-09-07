-- Designations: the ladder a company calls its own.
--
-- Every customer has a different one. A ten-person agency has "Junior", "Senior", "Lead"; a product
-- firm has "SDE-1" through "Principal"; a hospital has none of those. Hard-coding a ladder would mean
-- a code change per customer, and `employees.job_title` on its own is free text that drifts into
-- "Sr. Developer", "Senior Developer" and "Sr Dev" for three people doing the same job — which then
-- makes every headcount-by-level report wrong.
--
-- WHAT THIS IS NOT: a permission. A designation grants nothing at all. Access comes from the
-- reporting tree (see OrgScope) and from the six-role capability ladder, and neither consults this
-- table. That is the whole point of letting customers edit it: a title you can type for yourself must
-- never be able to widen what you can see.
create table designations (
    id          uuid primary key,
    company_id  uuid        not null references companies (id) on delete cascade,
    name        varchar(120) not null,
    -- Where it sits in the company's ladder, ascending: intern 0, junior 10, senior 20. Sparse on
    -- purpose so a level can be inserted between two others without renumbering the rest.
    level       integer     not null default 0,
    archived    boolean     not null default false,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

-- One "Senior Developer" per company, not per platform: two customers must be able to use the same
-- word. Case-insensitive, because "senior developer" and "Senior Developer" are the same rung and
-- allowing both recreates the drift this table exists to stop.
create unique index designations_company_name_uk on designations (company_id, lower(name));
create index designations_company_level_idx on designations (company_id, level);

-- Tenant isolation, same as every other table holding customer data.
alter table designations enable row level security;
alter table designations force row level security;
create policy tenant_isolation on designations
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

-- The link from a person to their rung. Nullable, and stays nullable: existing customers have people
-- with job titles and no ladder at all, and forcing one on them at migration time would either invent
-- designations from whatever strings happen to be in job_title or block the deploy.
--
-- job_title survives alongside it. They are different things: the designation is the rung ("Senior
-- Engineer"), the job title is what goes on a business card ("Senior Engineer, Payments"). Replacing
-- one with the other would lose data on every customer who has bothered to fill it in.
alter table employees add column designation_id uuid references designations (id) on delete set null;
create index employees_designation_idx on employees (designation_id);
