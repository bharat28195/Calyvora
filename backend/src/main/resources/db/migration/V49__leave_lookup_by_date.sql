-- The day sheet and the payroll run ask the same question: "whose approved leave overlaps these
-- dates". Until now the application asked for the company's ENTIRE leave history and filtered it in
-- Java, so the only index that mattered was idx_leave_company. Now the window is in the query, and
-- this is the index that makes the window worth having.
--
-- Column order follows the predicate: company_id and status are equality, start_date is the range
-- that actually narrows (a request starting after the window ends cannot overlap it). end_date rides
-- along so the row itself answers the second half without a heap fetch.
--
-- No RLS statements here on purpose. This file only adds an index; it inserts no rows, so it cannot
-- repeat V45's failure, where a policy created before the migration's own backfill refused that
-- backfill because Flyway has no tenant bound.
create index if not exists idx_leave_company_status_dates
    on leave_requests (company_id, status, start_date, end_date);
