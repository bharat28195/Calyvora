-- V58__tenant_delete_cascades.sql — make deleting a tenant a single, complete act.
--
-- Forty-eight tables carry a company_id that references companies(id). Two of them cascaded; the
-- other forty-six did not, so `delete from companies where id = ?` failed on the first foreign key
-- it met. Deleting a customer was therefore something only a hand-written list of tables could do —
-- and a hand-written list is exactly the wrong instrument for this job. The day somebody adds a
-- table and forgets the list, an erasure request silently leaves that table's personal data behind,
-- reports success, and nothing anywhere disagrees.
--
-- So the database is made the source of truth instead: every company_id foreign key cascades, and a
-- test asserts that any table with a company_id column has such a key. A new table that forgets it
-- fails the suite rather than a future GDPR request.
--
-- Done as a catalogue sweep rather than forty-six hand-written ALTERs. The constraints were created
-- across thirty migrations with names Postgres chose, and transcribing them would be a fresh chance
-- to mistype one — the sweep cannot miss a table that exists and cannot invent one that does not.
do $$
declare
    fk record;
begin
    for fk in
        select con.conname          as constraint_name,
               rel.relname          as table_name
        from pg_constraint con
                 join pg_class rel on rel.oid = con.conrelid
                 join pg_class ref on ref.oid = con.confrelid
                 join pg_namespace ns on ns.oid = rel.relnamespace
        where con.contype = 'f'
          and ref.relname = 'companies'
          and ns.nspname = current_schema()
          -- company_id only. companies.agency_id is handled separately below, and deliberately
          -- differently: an agency's customers are companies in their own right.
          and (select attname from pg_attribute
               where attrelid = con.conrelid and attnum = con.conkey[1]) = 'company_id'
          and con.confdeltype <> 'c'   -- 'c' = cascade; skip the two that already do
    loop
        execute format('alter table %I drop constraint %I', fk.table_name, fk.constraint_name);
        execute format(
            'alter table %I add constraint %I foreign key (company_id) references companies(id) on delete cascade',
            fk.table_name, fk.constraint_name);
    end loop;
end $$;

-- The one reference that must NOT cascade.
--
-- companies.agency_id points at another company: an agency that resells to its own customers. Those
-- customers are tenants in their own right, with their own staff, payroll and history. Deleting the
-- agency must not delete them — it must leave them standing, unparented, for the vendor to reassign
-- or wind down deliberately. Cascading here would turn "remove this reseller" into "remove every
-- company that reseller ever signed", which is the single most destructive thing this schema could
-- be asked to do by accident.
alter table companies drop constraint if exists companies_agency_id_fkey;
alter table companies add constraint companies_agency_id_fkey
    foreign key (agency_id) references companies(id) on delete set null;
