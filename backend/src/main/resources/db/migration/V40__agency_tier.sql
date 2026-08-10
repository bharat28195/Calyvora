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
-- seeding endpoints do not. Deleting the row here lets that bootstrap create it cleanly, and removes
-- a widely-known credential that could read every tenant on the platform.
delete from users where email = 'owner@priorityhr.app';

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
