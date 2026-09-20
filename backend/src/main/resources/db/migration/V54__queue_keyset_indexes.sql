-- The other three queues that grow forever, indexed the way V53 did leave.
--
-- Each one is read newest-first from a cursor, ordered and compared on (created_at, id) together.
-- The index has to match that pair or the planner sorts the whole history on every page and discards
-- everything above the cursor — which costs more the deeper the reader goes, exactly backwards from
-- what paging is for.

-- Expense claims: the approver's queue, and one person's own list.
create index if not exists idx_expense_company_status_created_id
    on expense_claims (company_id, status, created_at desc, id desc);
create index if not exists idx_expense_company_employee_created_id
    on expense_claims (company_id, employee_id, created_at desc, id desc);

-- The money totals are aggregates over every matching row rather than sums of the page, so they are
-- their own read. The status index above serves the two that filter on status alone; this one adds
-- reimbursed_at for "reimbursed this calendar year", which is a range on top of the equality.
create index if not exists idx_expense_company_status_reimbursed_at
    on expense_claims (company_id, status, reimbursed_at);

-- Helpdesk: tickets are raised in bursts, so same-instant rows are normal and the id in the sort key
-- is what keeps a page boundary inside a burst from dropping and repeating them.
create index if not exists idx_ticket_company_status_created_id
    on helpdesk_tickets (company_id, status, created_at desc, id desc);

-- Generated documents: a permanent record per letter issued, so this list only ever grows. Company
-- wide for the documents screen, per employee for the file on a person's profile.
create index if not exists idx_document_company_created_id
    on generated_documents (company_id, created_at desc, id desc);
create index if not exists idx_document_company_employee_created_id
    on generated_documents (company_id, employee_id, created_at desc, id desc);

-- No RLS statements here on purpose: this file only adds indexes and inserts no rows, so it cannot
-- repeat V45's failure, where a policy created before the migration's own backfill refused that
-- backfill because Flyway has no tenant bound.
