-- V77: remember that someone was reminded they forgot to check out (PD-67), so the reminder goes once.
-- Schema only.
alter table attendance_punches add column reminded_at timestamptz;
