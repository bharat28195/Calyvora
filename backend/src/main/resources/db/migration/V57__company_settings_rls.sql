-- V57__company_settings_rls.sql — put tenant isolation back on company_settings.
--
-- V30 switched it off, and said why: the platform owner provisions a new company's settings row
-- while bound to a different tenant, so the policy refused the insert. The reasoning offered at the
-- time was that the row is "benign per-company config (already world-readable via /me)" and that the
-- service always keys reads by the caller's own company id.
--
-- Both halves have aged badly. The row is no longer benign — it now carries the company's legal
-- name, address, logo, session idle timeout and, since V55, whether tax declarations are open — and
-- "the service always keys by company id" is a promise made by every line of application code
-- forever, which is exactly the promise Row-Level Security exists so that nobody has to keep. One
-- forgotten `where company_id = ?` in one query is a cross-tenant read of every customer's settings,
-- and nothing would fail until somebody noticed.
--
-- The real problem V30 hit has a better answer now: TenantBinder names the tenant a write belongs to
-- and runs it under that binding. PlatformService already uses it for the employee row it creates on
-- the same code path — with a comment observing that V30 met this same problem and answered it by
-- switching RLS off. This is the other half of that observation.
--
-- subscriptions stays exempt, deliberately. That table genuinely is platform-managed: the owner
-- console reads and writes every company's subscription, so there is no single tenant such a query
-- belongs to. company_settings has never been like that — every access is on behalf of exactly one
-- company.

alter table company_settings enable row level security;
alter table company_settings force row level security;
create policy tenant_isolation on company_settings
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

-- No backfill and no data change, so this cannot repeat V45's failure, where a policy created before
-- a migration's own backfill refused that backfill because Flyway has no tenant bound.
