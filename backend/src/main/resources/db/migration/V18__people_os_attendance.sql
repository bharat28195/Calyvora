-- V18__people_os_attendance.sql — Daily attendance (founder feedback C.4 / B6 phase 2).
--
-- Phase 1 derived "present vs on leave" from approved leave requests. This adds the real thing:
-- one row per employee per day. Approved leave still auto-fills the day (see AttendanceService) —
-- a marked row simply wins over the derived value, so nobody has to double-enter time off.

create table attendance_records (
    id          uuid primary key,
    company_id  uuid not null references companies(id),
    employee_id uuid not null references employees(id) on delete cascade,
    on_date     date not null,
    status      varchar(24) not null,          -- PRESENT | WORK_FROM_HOME | HALF_DAY | ABSENT | ON_LEAVE | HOLIDAY | WEEK_OFF
    check_in    time,
    check_out   time,
    note        varchar(400),
    marked_by   uuid references users(id),     -- null when the employee marked themselves in
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint uq_attendance_employee_day unique (employee_id, on_date)
);
create index idx_attendance_company_date on attendance_records(company_id, on_date);
create index idx_attendance_employee_date on attendance_records(employee_id, on_date);

-- Row-Level Security (mirrors V12).
alter table attendance_records enable row level security;
alter table attendance_records force row level security;
create policy tenant_isolation on attendance_records
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
