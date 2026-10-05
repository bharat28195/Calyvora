-- V63__company_roles.sql — custom roles: a company decides what each role may do (PD-54).
--
-- A role is a named set of permissions. Each permission carries a scope — COMPANY or TEAM — because
-- "may approve leave" means something different for HR (anyone) and for a team lead (their own
-- reports, as the reporting tree says). The tree still decides WHOSE; the permission decides WHAT.
--
-- Built-in roles (ADMIN, HR, MANAGER, EMPLOYEE) get a row only when a company first looks at or edits
-- its roles. Until then — and for anyone with no company_role_id — access is computed from users.role
-- exactly as before, so applying this migration changes nobody's access. Nothing is backfilled.
--
-- Both tables are tenant data: forced row-level security, company_id foreign keys that cascade (V58).
create table company_roles (
    id          uuid         primary key,
    company_id  uuid         not null references companies(id) on delete cascade,
    name        varchar(60)  not null,
    description varchar(300),
    -- Which built-in this row is (ADMIN | HR | MANAGER | EMPLOYEE), or null for a custom role.
    builtin     varchar(16),
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);
create unique index ux_company_roles_name on company_roles(company_id, lower(name));
create unique index ux_company_roles_builtin on company_roles(company_id, builtin) where builtin is not null;

create table company_role_permissions (
    role_id    uuid        not null references company_roles(id) on delete cascade,
    company_id uuid        not null references companies(id) on delete cascade,
    permission varchar(48) not null,
    scope      varchar(8)  not null,      -- COMPANY | TEAM
    primary key (role_id, permission)
);

-- The role a person holds, when it is not simply their users.role. Deleting a role puts its members
-- back on the built-in their users.role names (the application refuses to delete a role in use,
-- but the database should never leave a dangling pointer either way).
alter table users add column company_role_id uuid references company_roles(id) on delete set null;

alter table company_roles enable row level security;
alter table company_roles force row level security;
create policy tenant_isolation on company_roles
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table company_role_permissions enable row level security;
alter table company_role_permissions force row level security;
create policy tenant_isolation on company_role_permissions
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
