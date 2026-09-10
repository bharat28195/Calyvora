-- Every user gets their employee profile, so that reading the directory can stop creating them.
--
-- Profiles have until now been provisioned lazily, on the first authenticated read. That made every
-- GET a potential write: the day sheet could not run in a read-only transaction, and Hibernate
-- dirty-checked a thousand loaded entities to answer a question that changed nothing. Provisioning
-- now happens where the user is created. This backfills everyone who came before that.
--
-- Why the loop and the set_config: employees is under FORCE row level security, so an insert must
-- satisfy `company_id = current_setting('calyvora.company_id')`. Flyway has no tenant bound, and a
-- plain INSERT here would be refused with SQLSTATE 42501 — which is exactly how V45 broke every
-- deploy for weeks. The fix there was to order the statements correctly; the fix here is to bind the
-- tenant per company, the same way the application does.
--
-- set_config(..., is_local => true) is transaction-scoped, so it is confined to this migration and
-- cannot leak into a pooled connection afterwards.
do $$
declare
    c uuid;
begin
    for c in select distinct company_id from users loop
        perform set_config('calyvora.company_id', c::text, true);
        insert into employees (id, company_id, user_id)
        select gen_random_uuid(), u.company_id, u.id
          from users u
         where u.company_id = c
           and not exists (select 1 from employees e where e.user_id = u.id);
    end loop;
    perform set_config('calyvora.company_id', '', true);
end $$;
