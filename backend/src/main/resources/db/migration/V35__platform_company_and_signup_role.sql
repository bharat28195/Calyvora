-- V35__platform_company_and_signup_role.sql
--
-- Two related fixes. Self-registration assigned Role.OWNER, and /api/v1/platform/** is guarded by
-- `hasRole('OWNER')` while listing `companyRepository.findAll()` — so anyone who signed up could see
-- every customer on the platform, their headcount, seats and billing. Nothing exploited it only
-- because email verification was broken, so no self-registered account could ever log in. Removing
-- that gate (founder request: workspaces should be usable immediately) would have opened it.
--
-- 1. A company is now explicitly marked as *the* platform company. Role alone no longer grants the
--    owner console — the caller must also belong to that company, so a stray OWNER row can't reach
--    it even if one is ever created again.
-- 2. Existing self-registered OWNERs are demoted to ADMIN, which is what a company signup should
--    always have produced: an admin of their own company.

alter table companies add column is_platform boolean not null default false;

-- The platform company is the one the vendor account belongs to. Seeded deployments have exactly
-- one; a deployment that has never been seeded has none, and the console is simply unreachable
-- until a platform owner exists.
update companies set is_platform = true
where id in (select company_id from users where email = 'owner@priorityhr.app');

-- Anyone else holding OWNER got it from self-registration. They own a company, not the platform.
update users set role = 'ADMIN'
where role = 'OWNER'
  and company_id not in (select id from companies where is_platform);
