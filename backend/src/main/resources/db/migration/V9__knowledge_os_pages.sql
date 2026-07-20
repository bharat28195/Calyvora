-- V9__knowledge_os_pages.sql — Knowledge OS slices K2/K3: pages.
-- Pages are Markdown documents inside a space. The cross-app knowledge graph lives here:
--   author_id      -> employees(id)  (People OS — who wrote it, a real person link)
--   linked_task_id -> tasks(id)      (Work OS   — the task this doc is about, doc<->task)
-- parent_id gives a page tree; created_by is the platform user who created the row.

create table pages (
    id             uuid primary key,
    company_id     uuid not null references companies(id),
    space_id       uuid not null references spaces(id),
    parent_id      uuid references pages(id),
    title          varchar(200) not null,
    body           text,
    status         varchar(24) not null default 'DRAFT',   -- DRAFT|PUBLISHED
    author_id      uuid references employees(id),
    linked_task_id uuid references tasks(id),
    sort_order     int  not null default 0,
    created_by     uuid not null references users(id),
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);
create index idx_pages_space on pages(space_id);
create index idx_pages_company on pages(company_id);
create index idx_pages_author on pages(author_id);
create index idx_pages_parent on pages(parent_id);
