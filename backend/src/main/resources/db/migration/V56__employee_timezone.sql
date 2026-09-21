-- V56__employee_timezone.sql — where each person actually is.
--
-- Attendance has used the company's timezone since the UTC-host defect (a punch at 04:15 IST landed
-- on the previous day). That is right for a company in one place and wrong the moment it is not: a
-- Bengaluru company with a designer in Berlin records her 09:00 check-in as 12:30, and every
-- "late" flag on her month is an artefact of the clock rather than of her.
--
-- Nullable on purpose. Null means "same as the company", which is what almost everybody is, and it
-- keeps the fallback chain in one place — employee, then company, then the product default — rather
-- than copying the company's zone onto every row and then having to keep the copies in step.
alter table employees
    add column timezone varchar(64);
