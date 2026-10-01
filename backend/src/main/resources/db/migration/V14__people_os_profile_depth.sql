-- V14__people_os_profile_depth.sql — People OS: richer employee profile (feedback C2/C.4-C.6).
-- end_date (contract/exit), skills (comma-separated), and a 1–5 performance rating.
-- No new table → existing employees RLS policy (V12) already covers these columns.

alter table employees add column end_date date;
alter table employees add column skills   varchar(500);
alter table employees add column rating   integer;   -- 1..5; integer to match the JPA Integer mapping
