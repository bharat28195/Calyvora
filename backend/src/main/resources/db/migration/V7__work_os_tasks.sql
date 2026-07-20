-- V7__work_os_tasks.sql — Work OS slice W2: tasks.
-- Tasks live in a project and can be assigned to an employee (cross-app link into People OS).

create table tasks (
    id          uuid primary key,
    company_id  uuid not null references companies(id),
    project_id  uuid not null references projects(id),
    number      int  not null,                          -- per-project sequential (KEY-N)
    title       varchar(200) not null,
    description varchar(4000),
    status      varchar(24) not null default 'TODO',    -- TODO|IN_PROGRESS|DONE
    priority    varchar(24) not null default 'MEDIUM',  -- LOW|MEDIUM|HIGH|URGENT
    assignee_id uuid references employees(id),
    due_date    date,
    created_by  uuid not null references users(id),
    sort_order  int  not null default 0,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
create index idx_tasks_project on tasks(project_id);
create index idx_tasks_assignee on tasks(assignee_id);
create index idx_tasks_company on tasks(company_id);
