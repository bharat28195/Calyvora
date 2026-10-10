-- PD-69: letters of any length on the company letterpad.
--
-- 1. A continuation sheet for page two onwards: page 2 of an uploaded PDF/Word letterpad, a separate
--    upload, or derived from page one with the header cleared (source PAGE2 | UPLOADED | DERIVED).
-- 2. The writing area on each kind of page, in millimetres on A4: measured on upload, editable by HR.
--    Null means "not measured yet" — letterpads uploaded before this are measured on first read.
-- 3. later_pages: CONTINUATION (the continuation sheet) or SAME (the full letterpad on every page).
alter table letterheads
    add column continuation_image  bytea,
    add column continuation_type   varchar(64),
    add column continuation_source varchar(12),
    add column later_pages         varchar(12) not null default 'CONTINUATION',
    add column first_top_mm        integer,
    add column first_bottom_mm     integer,
    add column later_top_mm        integer,
    add column later_bottom_mm     integer,
    add column side_mm             integer;
