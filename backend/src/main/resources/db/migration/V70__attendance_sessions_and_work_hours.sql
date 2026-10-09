-- V70__attendance_sessions_and_work_hours.sql — several check-ins a day, and how long a day should be.
--
-- 1. attendance_punches: one row per check-in/check-out pair. A day with a lunch break is two
--    sessions (09:00–13:00, 14:00–18:00). attendance_records keeps the first in and the last out,
--    so every screen that reads those two columns keeps working; effective hours are the sum of the
--    sessions and gross hours are first-in to last-out.
-- 2. Required work hours: per shift (shifts.work_minutes) and a company default for anyone who is
--    not rostered (company_settings.work_day_minutes). Both default to 9 hours.
-- 3. Clocks: a person or company pinned to UTC goes back to Asia/Kolkata. Attendance dates follow
--    each person's clock, so one employee on UTC filed an IST morning check-in on the previous day
--    and the admin's day sheet never showed it. Any other zone somebody chose is left alone.

create table attendance_punches (
    id          uuid primary key,
    company_id  uuid not null references companies(id) on delete cascade,
    employee_id uuid not null references employees(id) on delete cascade,
    on_date     date not null,
    check_in    time not null,
    check_out   time,
    created_at  timestamptz not null default now()
);
create index idx_punches_employee_date on attendance_punches(employee_id, on_date);
create index idx_punches_company_date on attendance_punches(company_id, on_date);

alter table attendance_punches enable row level security;
alter table attendance_punches force row level security;
create policy tenant_isolation on attendance_punches
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table shifts add column work_minutes integer not null default 540;
alter table company_settings add column work_day_minutes integer not null default 540;
alter table company_settings add column work_day_start time not null default '09:30';
-- Nobody checked in by shift start + this grace is absent for the day.
alter table company_settings add column absent_grace_minutes integer not null default 120;
-- Absent-after-grace and short-day-is-half-day apply from this date on: today, so that switching the
-- rules on never reaches back and docks pay for months already worked. Null turns both rules off.
alter table company_settings add column attendance_rules_from date;

-- users is outside RLS (it is read before a tenant is known), so it is updated directly.
update users set timezone = null
    where timezone in ('UTC', 'Etc/UTC', 'GMT', 'Etc/GMT', 'Z', 'UCT', 'Etc/UCT', 'Universal', 'Zulu');

-- The rest is tenant data under FORCE row level security, and Flyway runs with no tenant bound: a
-- plain statement here would match no rows and report success (V59). Each company in turn, with
-- is_local => true confining the binding to this transaction.
do $$
declare
    c uuid;
begin
    for c in select id from companies loop
        perform set_config('calyvora.company_id', c::text, true);

        -- Every existing day with a check-in becomes its first (and only) session, so history keeps its hours.
        insert into attendance_punches (id, company_id, employee_id, on_date, check_in, check_out, created_at)
        select gen_random_uuid(), company_id, employee_id, on_date, check_in,
               case when check_out is not null and check_out >= check_in then check_out end, created_at
          from attendance_records
         where company_id = c and check_in is not null;

        update company_settings set attendance_rules_from = current_date where company_id = c;

        update company_settings set timezone = 'Asia/Kolkata'
         where company_id = c
           and timezone in ('UTC', 'Etc/UTC', 'GMT', 'Etc/GMT', 'Z', 'UCT', 'Etc/UCT', 'Universal', 'Zulu');
        update employees set timezone = null
         where company_id = c
           and timezone in ('UTC', 'Etc/UTC', 'GMT', 'Etc/GMT', 'Z', 'UCT', 'Etc/UCT', 'Universal', 'Zulu');
    end loop;
    perform set_config('calyvora.company_id', '', true);
end $$;
