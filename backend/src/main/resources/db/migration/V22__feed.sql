-- V22__feed.sql — Company feed (founder request: "some kind of posts page where people can post
-- things like birthday posts or any other useful posts, with post visibility options").
--
-- Visibility is per post: COMPANY (everyone) or DEPARTMENT (one team). It's stored on the row rather
-- than derived, because who can see a post must not silently change when someone moves team.

create table posts (
    id            uuid primary key,
    company_id    uuid not null references companies(id),
    author_id     uuid not null references users(id) on delete cascade,
    kind          varchar(24)  not null default 'UPDATE',  -- UPDATE | ANNOUNCEMENT | CELEBRATION | QUESTION
    body          varchar(4000) not null,
    visibility    varchar(24)  not null default 'COMPANY', -- COMPANY | DEPARTMENT
    /** Set only when visibility = DEPARTMENT. */
    department_id uuid references departments(id) on delete cascade,
    /** Pinned posts float to the top of the feed; Owner/Admin only. */
    pinned        boolean      not null default false,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);
create index idx_posts_company_created on posts(company_id, created_at desc);

create table post_reactions (
    post_id    uuid not null references posts(id) on delete cascade,
    user_id    uuid not null references users(id) on delete cascade,
    company_id uuid not null references companies(id),
    emoji      varchar(16) not null default '👍',
    created_at timestamptz not null default now(),
    primary key (post_id, user_id, emoji)
);

create table post_comments (
    id         uuid primary key,
    company_id uuid not null references companies(id),
    post_id    uuid not null references posts(id) on delete cascade,
    author_id  uuid not null references users(id) on delete cascade,
    body       varchar(2000) not null,
    created_at timestamptz not null default now()
);
create index idx_post_comments_post on post_comments(post_id, created_at asc);

-- Row-Level Security on all three (mirrors V12).
alter table posts enable row level security;
alter table posts force row level security;
create policy tenant_isolation on posts
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table post_reactions enable row level security;
alter table post_reactions force row level security;
create policy tenant_isolation on post_reactions
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);

alter table post_comments enable row level security;
alter table post_comments force row level security;
create policy tenant_isolation on post_comments
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
