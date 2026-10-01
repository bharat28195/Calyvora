-- V5__people_os_leave.sql — People OS slice P4: time-off / leave.
-- A leave request goes PENDING -> APPROVED/REJECTED (by an admin) or CANCELLED (by the requester).

create table leave_requests (
    id          uuid primary key,
    company_id  uuid not null references companies(id),
    employee_id uuid not null references employees(id),
    type        varchar(24) not null,                      -- VACATION|SICK|PERSONAL|UNPAID
    start_date  date not null,
    end_date    date not null,
    days        int  not null,
    reason      varchar(500),
    status      varchar(24) not null default 'PENDING',    -- PENDING|APPROVED|REJECTED|CANCELLED
    decided_by  uuid references users(id),
    decided_at  timestamptz,
    created_at  timestamptz not null default now()
);
create index idx_leave_company on leave_requests(company_id);
create index idx_leave_employee on leave_requests(employee_id);
