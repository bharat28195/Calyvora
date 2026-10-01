-- V1__baseline.sql — Calyvora Sprint 1 platform-foundation schema.
-- Postgres. All timestamps timestamptz. IDs uuid (app-generated).
-- Tenant-owned tables carry company_id (SD-2: app-layer isolation this sprint; RLS in Sprint 2).

create extension if not exists "pgcrypto";  -- gen_random_uuid()

-- ---------------------------------------------------------------------------
-- companies
-- ---------------------------------------------------------------------------
create table companies (
    id         uuid primary key,
    name       varchar(120) not null,
    slug       varchar(140) not null unique,
    status     varchar(24)  not null default 'PENDING',   -- PENDING | ACTIVE | SUSPENDED
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);

-- ---------------------------------------------------------------------------
-- users  (email globally unique — SD-3; a user belongs to exactly one company)
-- ---------------------------------------------------------------------------
create table users (
    id                uuid primary key,
    company_id        uuid not null references companies(id),
    email             varchar(255) not null unique,
    password_hash     varchar(100),                        -- null until set (invited users)
    first_name        varchar(80)  not null,
    last_name         varchar(80)  not null,
    role              varchar(24)  not null,               -- OWNER | ADMIN | MEMBER
    status            varchar(24)  not null,               -- PENDING_VERIFICATION | INVITED | ACTIVE | DISABLED
    email_verified_at timestamptz,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now()
);
create index idx_users_company on users(company_id);

-- ---------------------------------------------------------------------------
-- email_verification_tokens  (only sha-256 hashes stored — never the raw token)
-- ---------------------------------------------------------------------------
create table email_verification_tokens (
    id          uuid primary key,
    user_id     uuid not null references users(id),
    token_hash  varchar(64) not null,
    expires_at  timestamptz not null,
    consumed_at timestamptz,
    created_at  timestamptz not null default now()
);
create index idx_evt_user on email_verification_tokens(user_id);

-- ---------------------------------------------------------------------------
-- invitations
-- ---------------------------------------------------------------------------
create table invitations (
    id          uuid primary key,
    company_id  uuid not null references companies(id),
    email       varchar(255) not null,
    role        varchar(24)  not null,                     -- ADMIN | MEMBER (never OWNER)
    token_hash  varchar(64)  not null,
    status      varchar(24)  not null default 'PENDING',   -- PENDING | ACCEPTED | REVOKED | EXPIRED
    invited_by  uuid not null references users(id),
    expires_at  timestamptz  not null,
    accepted_at timestamptz,
    created_at  timestamptz  not null default now()
);
-- at most one active (PENDING) invite per (company, email)
create unique index uq_invite_active on invitations(company_id, lower(email))
    where status = 'PENDING';
create index idx_invite_company on invitations(company_id);

-- ---------------------------------------------------------------------------
-- refresh_tokens  (rotating, hashed at rest, family for reuse-detection — SD-5)
-- ---------------------------------------------------------------------------
create table refresh_tokens (
    id         uuid primary key,
    user_id    uuid not null references users(id),
    token_hash varchar(64) not null,
    family_id  uuid not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    user_agent varchar(255),
    created_at timestamptz not null default now()
);
create index idx_rt_user   on refresh_tokens(user_id);
create index idx_rt_family on refresh_tokens(family_id);

-- ---------------------------------------------------------------------------
-- company_settings  (1:1 with company)
-- ---------------------------------------------------------------------------
create table company_settings (
    company_id uuid primary key references companies(id),
    timezone   varchar(64)  not null default 'UTC',
    locale     varchar(16)  not null default 'en',
    logo_url   varchar(500),
    updated_at timestamptz  not null default now()
);
