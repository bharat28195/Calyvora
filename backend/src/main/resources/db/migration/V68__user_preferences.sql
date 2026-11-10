-- V68__user_preferences.sql — each person's language, clock and date style.
--
-- Every column is nullable and null means "use the default": the language falls back to the
-- company's (and then English), the timezone to the person's employee record and then the company's,
-- and the date and time formats to whatever the chosen language normally writes. Nobody is moved by
-- this migration — every existing account keeps exactly what it saw before.
alter table users add column language varchar(8);
alter table users add column timezone varchar(64);
alter table users add column date_format varchar(8);
alter table users add column time_format varchar(8);
