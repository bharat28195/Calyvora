-- V43__work_completion_timestamps.sql — when a piece of work was actually delivered.
--
-- Groundwork for evidence-based performance reviews (PD-17, D1): a review asks
-- "what did this person ship between these two dates", which we could not answer
-- honestly. `updated_at` was the only finish-ish timestamp available, and any later
-- edit — a typo fix, a re-assignment, a comment — moves it, so work delivered in
-- Q1 could drift into the Q2 review and out of the Q1 one.
--
-- These columns are written by the status transition itself and cleared when work is
-- reopened, so they mean "finished at", not "last touched at".

alter table tasks add column completed_at timestamptz;
alter table tickets add column resolved_at timestamptz;

-- Backfill. For already-finished rows `updated_at` is the best estimate that exists;
-- it is imprecise for anything edited after completion, but it is strictly better than
-- leaving history invisible to the first review cycle. New transitions are exact.
update tasks set completed_at = updated_at where status = 'DONE';
update tickets set resolved_at = updated_at where status in ('RESOLVED', 'CLOSED');

-- The evidence query is always "this assignee, within this period".
create index idx_tasks_assignee_completed on tasks (assignee_id, completed_at);
create index idx_tickets_assignee_resolved on tickets (assignee_id, resolved_at);
