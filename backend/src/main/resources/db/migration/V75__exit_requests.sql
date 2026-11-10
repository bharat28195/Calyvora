-- V75: an exit is first a request, approved by an admin, before anyone is put on notice (PD-65).
-- Schema only: new nullable columns; no existing row changes.

alter table employees add column exit_request_last_day date;
alter table employees add column exit_request_reason varchar(500);
alter table employees add column exit_requested_by uuid references users(id) on delete set null;
alter table employees add column exit_requested_at timestamptz;
alter table employees add column exit_request_checklist boolean;
