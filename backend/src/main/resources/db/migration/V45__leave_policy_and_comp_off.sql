-- V45__leave_policy_and_comp_off.sql — leave stops being a constant.
--
-- Until now every company got 25 vacation days, hard-coded as VACATION_ALLOWANCE_DAYS in
-- LeaveService, with no accrual, no carry-forward and no comp-off. That is a demo, not a policy, and
-- it is the thing Indian buyers ask about first after statutory payroll.
--
-- THE DEFAULTS BELOW REPRODUCE THE OLD BEHAVIOUR EXACTLY: vacation = 25 days, granted annually, no
-- carry-forward. An existing company sees no change on the day this deploys, and can then edit its
-- policy. A migration that silently re-computes everyone's balances would be the wrong kind of
-- clever — people plan holidays against these numbers.

-- ---------------------------------------------------------------------------
-- leave_policies — one row per company per leave type.
-- ---------------------------------------------------------------------------
create table leave_policies (
    id                   uuid primary key,
    company_id           uuid        not null references companies(id),
    type                 varchar(24) not null,              -- VACATION|SICK|PERSONAL|UNPAID|COMP_OFF
    -- Whether a day off this type is paid. UNPAID exists precisely to be false, and payroll will need
    -- this column when loss-of-pay is computed from leave rather than typed in by hand.
    paid                 boolean     not null default true,
    -- ANNUAL  — the whole entitlement exists from 1 January (or from joining, pro-rata).
    -- MONTHLY — earned per completed month of service, which is what most Indian policies do.
    accrual              varchar(16) not null default 'ANNUAL',
    days_per_year        numeric(5,1) not null default 0,
    -- Unused days that survive into next year, capped. 0 means use-it-or-lose-it.
    carry_forward_cap    numeric(5,1) not null default 0,
    -- How long a comp-off credit stays usable. Only meaningful for COMP_OFF; a credit that never
    -- expires becomes an unbounded liability on the company's books.
    comp_off_expiry_days int         not null default 90,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    constraint uq_leave_policy_company_type unique (company_id, type),
    constraint ck_leave_policy_accrual check (accrual in ('ANNUAL', 'MONTHLY')),
    constraint ck_leave_policy_days check (days_per_year >= 0 and carry_forward_cap >= 0)
);
create index idx_leave_policy_company on leave_policies(company_id);

alter table leave_policies enable row level security;
alter table leave_policies force row level security;
create policy tenant_isolation on leave_policies
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

-- ---------------------------------------------------------------------------
-- comp_off_credits — a day worked that was not owed, earning a day off later.
--
-- Modelled as credits rather than as a balance number so each one can be traced: worked on this
-- date, approved by this person, expires then, spent on that leave request. A single counter would
-- make "why do I have three days?" unanswerable, and expiry impossible.
-- ---------------------------------------------------------------------------
create table comp_off_credits (
    id          uuid primary key,
    company_id  uuid        not null references companies(id),
    employee_id uuid        not null references employees(id),
    worked_on   date        not null,
    reason      varchar(300),
    status      varchar(16) not null default 'PENDING',  -- PENDING|APPROVED|REJECTED|CONSUMED
    expires_on  date,                                    -- set when approved, from the policy
    decided_by  uuid references users(id),
    decided_at  timestamptz,
    -- Which leave request spent it. Null until used; this is what makes a credit single-use.
    consumed_by uuid references leave_requests(id),
    created_at  timestamptz not null default now(),
    -- One credit per day worked. Without this, asking twice for the same Saturday is two days off.
    constraint uq_comp_off_employee_day unique (employee_id, worked_on),
    constraint ck_comp_off_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CONSUMED'))
);
create index idx_comp_off_company on comp_off_credits(company_id);
create index idx_comp_off_employee on comp_off_credits(employee_id, status);

alter table comp_off_credits enable row level security;
alter table comp_off_credits force row level security;
create policy tenant_isolation on comp_off_credits
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

-- ---------------------------------------------------------------------------
-- Seed every existing company with the policy it already had.
--
-- Vacation 25/ANNUAL/no carry-forward is exactly VACATION_ALLOWANCE_DAYS as it stood. The other
-- types had no entitlement at all and are seeded at 0 rather than at a guessed number: showing a
-- company an allowance it never agreed to is worse than showing it none.
--
-- Written as a set-returning insert rather than a loop so it is one statement over however many
-- companies exist, and skips any that somehow already have a row.
-- ---------------------------------------------------------------------------
insert into leave_policies (id, company_id, type, paid, accrual, days_per_year, carry_forward_cap)
select gen_random_uuid(), c.id, p.type, p.paid, p.accrual, p.days, p.cap
from companies c
cross join (values
    ('VACATION', true,  'ANNUAL', 25.0, 0.0),
    ('SICK',     true,  'ANNUAL',  0.0, 0.0),
    ('PERSONAL', true,  'ANNUAL',  0.0, 0.0),
    ('UNPAID',   false, 'ANNUAL',  0.0, 0.0),
    ('COMP_OFF', true,  'ANNUAL',  0.0, 0.0)
) as p(type, paid, accrual, days, cap)
where not exists (
    select 1 from leave_policies lp where lp.company_id = c.id and lp.type = p.type
);
