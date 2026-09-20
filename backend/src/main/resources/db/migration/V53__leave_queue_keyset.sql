-- The approver's leave queue is read newest-first, a page at a time, from a cursor.
--
-- The query orders by (created_at desc, id desc) and compares against the same pair, which is what
-- makes a page boundary stable when rows share a creation instant. That only performs if the index
-- is in the same shape: matching it exactly lets the planner seek straight to the cursor and read
-- forward, instead of sorting the company's whole leave history on every page and throwing away
-- everything above the cursor. The deeper the reader pages, the bigger that difference gets.
--
-- company_id leads because every query here is tenant-scoped, and Row-Level Security adds the same
-- predicate whether or not the application does.
create index if not exists idx_leave_company_created_id
    on leave_requests (company_id, created_at desc, id desc);

-- The screen that actually reads this queue only ever shows what is still waiting for a decision,
-- so status is an equality that removes most of the table before the sort key is reached. It sits
-- immediately after company_id for that reason: decided leave is the bulk of the history and grows
-- forever, while the pending set stays roughly constant however old the company gets.
create index if not exists idx_leave_company_status_created_id
    on leave_requests (company_id, status, created_at desc, id desc);

-- A manager's queue is the same read narrowed to their reports, so employee_id sits between the
-- tenant and the sort key: it is the equality that selects the rows, and the pair after it keeps
-- them in the order the cursor expects. Without this, scoping a queue to a team means sorting all
-- of that team's history per page.
create index if not exists idx_leave_company_employee_created_id
    on leave_requests (company_id, employee_id, created_at desc, id desc);

-- No RLS statements here on purpose: this file only adds indexes and inserts no rows, so it cannot
-- repeat V45's failure, where a policy created before the migration's own backfill refused that
-- backfill because Flyway has no tenant bound.
