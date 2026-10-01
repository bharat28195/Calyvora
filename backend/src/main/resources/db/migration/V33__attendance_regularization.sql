-- V33__attendance_regularization.sql — "I forgot to clock in" fix-up flow (founder request).
--
-- An employee raises a regularization for a past day (the in/out they should have logged + a reason).
-- Their manager (or HR/admin) approves, which writes the attendance record for that day. Tenant-isolated.

create table attendance_regularizations (
    id            uuid primary key,
    company_id    uuid not null references companies(id),
    employee_id   uuid not null references employees(id) on delete cascade,
    on_date       date not null,
    check_in      time,
    check_out     time,
    status        varchar(12) not null default 'PENDING',   -- PENDING / APPROVED / REJECTED
    reason        varchar(500),
    decided_by    uuid references users(id),
    decision_note varchar(500),
    decided_at    timestamptz,
    created_at    timestamptz not null default now()
);
create index idx_regularizations_company on attendance_regularizations(company_id, status, created_at desc);
create index idx_regularizations_employee on attendance_regularizations(employee_id);

alter table attendance_regularizations enable row level security;
alter table attendance_regularizations force row level security;
create policy tenant_isolation on attendance_regularizations
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
