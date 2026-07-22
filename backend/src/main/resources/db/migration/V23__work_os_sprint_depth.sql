-- V23__work_os_sprint_depth.sql — Sprint depth (founder request: "add more sprint functionalities
-- which is used by any industry").
--
-- Three additions, each earning its place:
--   * story points on a task — the unit velocity and burndown are measured in;
--   * a capacity figure on a sprint — what the team believes it can take on, so over-commitment is
--     visible at planning time rather than at the retro;
--   * a daily snapshot of remaining points per sprint, because a burndown you compute from current
--     state can only ever draw today. History has to be recorded as it happens.

alter table tasks add column story_points integer;

alter table sprints add column capacity_points integer;

create table sprint_snapshots (
    id                 uuid primary key,
    company_id         uuid not null references companies(id),
    sprint_id          uuid not null references sprints(id) on delete cascade,
    on_date            date not null,
    remaining_points   integer not null,
    completed_points   integer not null,
    remaining_tasks    integer not null,
    created_at         timestamptz not null default now(),
    constraint uq_sprint_snapshot_day unique (sprint_id, on_date)
);
create index idx_sprint_snapshots_sprint on sprint_snapshots(sprint_id, on_date);

alter table sprint_snapshots enable row level security;
alter table sprint_snapshots force row level security;
create policy tenant_isolation on sprint_snapshots
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
