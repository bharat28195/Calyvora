-- V73: what a company's letters need to say about the company itself (PD-63).
--
-- Schema only: every column is nullable or defaulted and no row is rewritten, so nothing here runs
-- per tenant and nothing is hidden from Flyway by RLS.

-- Statutory identity, printed in the footer of every page (Indian letterheads carry the CIN and GSTIN).
alter table letterheads add column cin varchar(32);
alter table letterheads add column gstin varchar(20);
alter table letterheads add column website varchar(160);
alter table letterheads add column email varchar(160);

-- How a date reads on a letter: LONG "4 March 2026", SHORT "04 Mar, 2026", NUMERIC "04/03/2026".
alter table letterheads add column date_style varchar(10) not null default 'LONG';

-- The company's standard terms, written once and quoted by every letter that needs them.
alter table letterheads add column probation_days integer;
alter table letterheads add column notice_probation varchar(80);
alter table letterheads add column notice_period varchar(80);
alter table letterheads add column working_days varchar(80);
alter table letterheads add column working_hours varchar(80);
alter table letterheads add column pay_day varchar(80);
alter table letterheads add column jurisdiction varchar(80);

-- Which set of starter templates a company has been given. Companies that already have templates
-- had set 1 (the original five); set 2 adds the rest of the letter library. A template a company
-- deleted is never put back — only sets it has not yet received are added.
alter table company_settings add column letter_starters_seeded integer not null default 1;
