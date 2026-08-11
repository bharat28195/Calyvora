-- V40__agency_tier.sql — a third tier between the vendor and a single company (PD-18).
--
-- The site sells "manage every company from one console" to agencies and groups, and the only thing
-- that fitted was the platform-owner console: the vendor's own view, which reads EVERY tenant and can
-- start and end subscriptions. Handing that to a customer running several companies would expose
-- every other customer and let them activate their own billing.
--
-- An agency reuses the shape the platform tier already established in V35 — a company row flagged as
-- special, whose members get a console — rather than a parallel identity model. The agency owner needs
-- a home company for users.company_id and the RLS tenant binding either way, so a flagged company row
-- is the cheapest thing that works.
--
-- No RLS change is needed or wanted. The agency console reads only companies/users/subscriptions, the
-- three tables V12 deliberately leaves outside RLS. A member company's HR data stays unreachable
-- because the agency owner's tenant binding is its OWN workspace id, so every RLS-protected table
-- returns zero rows for it. That is precisely why the console is scoped to company-level summaries.

-- The agency's own workspace row (mirrors is_platform).
alter table companies add column is_agency boolean not null default false;

-- A member company points at its agency's workspace row. Null = a direct customer, which is every
-- company that exists today.
alter table companies add column agency_id uuid null references companies (id);

create index idx_companies_agency on companies (agency_id) where agency_id is not null;

-- Replace the demo platform owner with the real one. The account is bootstrapped at startup now
-- (PlatformOwnerBootstrap) rather than by the dev-only seeder, so it exists in prod too — where the
-- seeding endpoints do not. Removing the row here lets that bootstrap create it cleanly, and takes
-- away a widely-known credential that could read every tenant on the platform.
--
-- The first version of this was a bare `delete from users where email = …`, which passed every test
-- and failed on the real database: an account that has actually been *used* is referenced from
-- refresh_tokens, and from a dozen `created_by` columns besides. The test databases were fresh, so
-- nothing pointed at it there. Hence the shape below — revoke the sessions, then delete if the row is
-- genuinely unreferenced, and neutralise it if it is not.

-- Session and token artefacts: deleting these is right regardless. It revokes any live session the
-- old owner still had, which is the security half of this change.
delete from refresh_tokens where user_id in (select id from users where email = 'owner@priorityhr.app');
delete from email_verification_tokens where user_id in (select id from users where email = 'owner@priorityhr.app');

do $$
begin
    delete from users where email = 'owner@priorityhr.app';
exception
    when foreign_key_violation then
        -- The account authored something — a document, a ticket, an invitation — and deleting it
        -- would mean cascading through half the schema to erase a demo login. Not worth it, and
        -- destructive. Retiring it achieves the same end: the credential no longer works, the email
        -- is freed for the real owner, and the audit trail it left stays intact.
        --
        -- Handled in a sub-block so the exception rolls back only this statement; the migration's
        -- own transaction carries on.
        update users
        set email = 'retired+owner@priorityhr.invalid',
            password_hash = null,
            status = 'DISABLED'
        where email = 'owner@priorityhr.app';
end $$;

-- Drop the platform company only if that deletion left it with no members at all; a deployment where
-- someone has already added a second platform user must keep it.
delete from company_settings
where company_id in (
    select c.id from companies c
    where c.is_platform
      and not exists (select 1 from users u where u.company_id = c.id)
);

delete from companies c
where c.is_platform
  and not exists (select 1 from users u where u.company_id = c.id);
