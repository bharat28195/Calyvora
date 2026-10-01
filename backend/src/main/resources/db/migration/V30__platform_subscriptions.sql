-- V30__platform_subscriptions.sql — the platform-owner (vendor) layer (PD-10, founder pts 1/6/7/8).
--
-- The OWNER sits above all companies and controls each subscription: seat limit, end date, and
-- whether the company's app is live at all. Subscriptions therefore become a *platform-managed*
-- concern rather than tenant-isolated data — the owner must read/write every company's row — so RLS
-- is lifted here (the app still filters by company_id for a company's own read). Seats + an end date
-- are added; seat-increase requests are a new table.

alter table subscriptions add column seats integer not null default 5;
alter table subscriptions add column ends_at date;

-- Lift tenant isolation: this table is now read/written across tenants by the platform owner.
drop policy if exists tenant_isolation on subscriptions;
alter table subscriptions no force row level security;
alter table subscriptions disable row level security;

-- company_settings likewise: the owner provisions a new company's settings row from the platform
-- context (a different tenant), so RLS would block that insert. It is benign per-company config
-- (already world-readable via /me) and the service always keys reads/writes by the caller's own
-- company id, so isolation is preserved at the app layer.
drop policy if exists tenant_isolation on company_settings;
alter table company_settings no force row level security;
alter table company_settings disable row level security;

-- A company admin asks the owner for more seats (Netflix-style); the owner approves and the seat
-- limit bumps. Platform-managed, so not tenant-isolated.
create table seat_requests (
    id              uuid primary key,
    company_id      uuid not null references companies(id) on delete cascade,
    requested_seats integer not null,
    status          varchar(16) not null default 'PENDING',
    note            varchar(300),
    created_at      timestamptz not null default now(),
    decided_at      timestamptz
);
create index idx_seat_requests_status on seat_requests(status, created_at);
