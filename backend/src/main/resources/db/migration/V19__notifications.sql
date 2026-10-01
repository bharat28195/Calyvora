-- V19__notifications.sql — Notifications + Inbox (founder feedback D4 + D5).
--
-- One row per person per thing they need to know about: a leave request waiting on their approval,
-- a decision on their own request, a goal their manager set them. Deliberately denormalized (title,
-- body and link are frozen at send time) so an inbox entry still reads correctly after the thing it
-- points at changes or is deleted.

create table notifications (
    id           uuid primary key,
    company_id   uuid not null references companies(id),
    recipient_id uuid not null references users(id) on delete cascade,
    actor_id     uuid references users(id),          -- who caused it; null for system events
    type         varchar(40) not null,               -- LEAVE_REQUESTED | LEAVE_APPROVED | LEAVE_REJECTED
                                                     -- | GOAL_ASSIGNED | DOCUMENT_ISSUED | ANNOUNCEMENT
    title        varchar(200) not null,
    body         varchar(600),
    link         varchar(300),                       -- where clicking it should take you
    entity_type  varchar(40),                        -- e.g. LEAVE_REQUEST, GOAL
    entity_id    uuid,
    read_at      timestamptz,
    created_at   timestamptz not null default now()
);
create index idx_notifications_recipient on notifications(recipient_id, created_at desc);
create index idx_notifications_unread on notifications(recipient_id) where read_at is null;

alter table notifications enable row level security;
alter table notifications force row level security;
create policy tenant_isolation on notifications
    using (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid)
    with check (company_id = nullif(current_setting('calyvora.company_id', true), '')::uuid);
