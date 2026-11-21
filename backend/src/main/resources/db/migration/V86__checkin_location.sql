-- PD-81: location at check-in, for the mobile app (and any browser that shares its location).
--
-- 1. office_locations: the places a company counts as "at work" — a point and a radius in metres.
-- 2. company_settings.checkin_location_mode: OFF (never asked, never stored), RECORD (stored and
--    shown to managers, never refused) or ENFORCE (a check-in outside every office is refused).
-- 3. attendance_punches: where each check-in and check-out happened. Only at those two moments —
--    nobody is tracked through the day.
create table office_locations (
    id          uuid primary key,
    company_id  uuid not null references companies(id) on delete cascade,
    name        varchar(120) not null,
    latitude    double precision not null,
    longitude   double precision not null,
    radius_m    integer not null default 200,
    created_at  timestamptz not null default now()
);
create index idx_office_locations_company on office_locations (company_id);

alter table office_locations enable row level security;
alter table office_locations force row level security;
create policy tenant_isolation on office_locations
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table company_settings add column checkin_location_mode varchar(16) not null default 'OFF';

alter table attendance_punches add column in_latitude   double precision;
alter table attendance_punches add column in_longitude  double precision;
alter table attendance_punches add column in_accuracy_m integer;
alter table attendance_punches add column in_office     varchar(120);
alter table attendance_punches add column in_distance_m integer;
alter table attendance_punches add column out_latitude   double precision;
alter table attendance_punches add column out_longitude  double precision;
alter table attendance_punches add column out_accuracy_m integer;
alter table attendance_punches add column out_office     varchar(120);
alter table attendance_punches add column out_distance_m integer;
