-- V21__expenses.sql — Expense claims (founder request: "claim expenses for official travel").
--
-- A claim is submitted by an employee, approved by their manager (or an admin), then marked
-- reimbursed once it's actually paid. Approval and payment are separate states on purpose: an
-- approved claim that hasn't been paid is exactly the thing people chase.

create table expense_claims (
    id           uuid primary key,
    company_id   uuid not null references companies(id),
    employee_id  uuid not null references employees(id) on delete cascade,
    title        varchar(200) not null,
    category     varchar(24)  not null default 'OTHER',    -- TRAVEL | ACCOMMODATION | MEALS
                                                           -- | SUPPLIES | TRAINING | OTHER
    amount       numeric(12,2) not null,
    currency     varchar(3)   not null default 'INR',
    spent_on     date         not null,
    description  varchar(1000),
    /** Where the receipt lives. File upload is future work; a link keeps the flow usable today. */
    receipt_url  varchar(500),
    status       varchar(24)  not null default 'SUBMITTED', -- SUBMITTED | APPROVED | REJECTED | REIMBURSED
    decided_by   uuid references users(id),
    decided_at   timestamptz,
    decision_note varchar(400),
    reimbursed_at timestamptz,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now()
);
create index idx_expense_claims_company on expense_claims(company_id, created_at desc);
create index idx_expense_claims_employee on expense_claims(employee_id, created_at desc);

alter table expense_claims enable row level security;
alter table expense_claims force row level security;
create policy tenant_isolation on expense_claims
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
