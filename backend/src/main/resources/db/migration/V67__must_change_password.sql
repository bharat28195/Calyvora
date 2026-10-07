-- V67__must_change_password.sql — a temporary password is changed at first sign-in.
--
-- A company's first admin is created by Calyvora (from a trial request, or directly) with a password
-- Calyvora chose and emailed. That password has been in an inbox and possibly in a chat, so the admin
-- is asked to replace it before doing anything else. The flag is set when an account is created with a
-- password somebody else picked, and cleared the moment the owner of the account sets their own.
-- Existing accounts are untouched: every one of them chose its own password already.
alter table users add column must_change_password boolean not null default false;
