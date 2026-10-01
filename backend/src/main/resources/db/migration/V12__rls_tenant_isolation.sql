-- V12__rls_tenant_isolation.sql — Postgres Row-Level Security (SD-2).
--
-- Defense-in-depth below the application layer: even if a service forgets its
-- company_id filter (or an injection slips past it), the database itself refuses
-- to return or mutate another tenant's rows. This complements — does not replace —
-- the app-layer TenantContext checks; both must agree.
--
-- Mechanism: every tenant-owned table gets a policy keyed off the per-connection
-- GUC `calyvora.company_id`, which TenantAwareDataSource sets from the authenticated
-- request's tenant. An unset/empty GUC resolves to NULL, so the equality is NULL and
-- NO rows are visible — deny-by-default for any connection without a bound tenant.
--
-- IMPORTANT: RLS is bypassed by SUPERUSER and BYPASSRLS roles. The application's
-- database role in any shared environment MUST be NOSUPERUSER and MUST NOT hold
-- BYPASSRLS, or this layer is inert. FORCE ROW LEVEL SECURITY below additionally
-- subjects the table OWNER to the policies (owners are otherwise exempt).

do $$
declare
    t text;
    -- Tenant-owned tables carrying a company_id. Deliberately excludes the auth
    -- surface (users, invitations, *_tokens, companies): those are queried before a
    -- tenant is bound — registration, login, email verification, invite acceptance —
    -- so a tenant-scoped policy would break them and buys little (each is already a
    -- narrow lookup by unique email/token).
    tenant_tables text[] := array[
        'company_settings',
        'employees', 'departments', 'onboarding_tasks', 'leave_requests',
        'projects', 'tasks', 'sprints', 'tickets',
        'spaces', 'pages'
    ];
begin
    foreach t in array tenant_tables loop
        execute format('alter table %I enable row level security', t);
        execute format('alter table %I force row level security', t);
        -- One policy for all commands: a row is visible/writable iff its company_id
        -- equals the bound tenant. WITH CHECK also blocks INSERT/UPDATE that would
        -- plant a row under another tenant.
        execute format($f$
            create policy tenant_isolation on %I
                using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
                with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
        $f$, t);
    end loop;
end $$;
