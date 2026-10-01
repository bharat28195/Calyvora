-- V10__work_os_sprints.sql — Work OS slice S1: sprints.
-- A project has many sprints; a task belongs to at most one sprint (null sprint_id = backlog).
-- At most one ACTIVE sprint per project, enforced by a partial unique index (SD-19).

create table sprints (
    id          uuid primary key,
    company_id  uuid not null references companies(id),
    project_id  uuid not null references projects(id),
    name        varchar(120) not null,
    goal        varchar(500),
    start_date  date,
    end_date    date,
    status      varchar(24) not null default 'PLANNED',   -- PLANNED|ACTIVE|COMPLETED
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
create index idx_sprints_project on sprints(project_id);
create index idx_sprints_company on sprints(company_id);
create unique index uq_one_active_sprint_per_project on sprints(project_id) where status = 'ACTIVE';

alter table tasks add column sprint_id uuid references sprints(id);
create index idx_tasks_sprint on tasks(sprint_id);
