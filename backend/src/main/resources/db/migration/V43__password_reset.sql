-- V43__password_reset.sql — forgotten passwords, by one-time code (PD-23).
--
-- Until now there was no way back into an account. If you forgot your password, an administrator had
-- to be asked to make you a new one, and the platform owner had nobody at all to ask. Every previous
-- change that touched credentials — approving a trial, moving the owner account — had to hand the
-- password over out of band precisely because of this gap.
--
-- A short numeric code rather than a link. A code can be read off one device and typed into another,
-- which is what people actually do — and it is the same mechanism an SMS would carry, so moving the
-- channel later needs a sender, not a redesign.
--
-- Email only, deliberately. SMS was the original ask and was dropped on cost: Indian transactional
-- SMS is a few paise a message and needs DLT registration before a gateway delivers anything, while
-- email is already wired and free at this volume. No `users.phone` column is added here — schema for
-- a feature nobody is using is the kind of thing that is awkward to unwind (see V40), and the day
-- SMS is switched on it can be added then, against a real requirement.

create table password_reset_codes (
    id           uuid primary key,
    user_id      uuid not null references users(id) on delete cascade,
    -- Hashed, never stored in the clear. A leaked database must not hand over live reset codes, and
    -- the code has exactly the power of a password while it lives.
    code_hash    varchar(64) not null,
    -- Always EMAIL today. Recorded rather than assumed so support can answer "where did it go?"
    -- without reading the code that sent it.
    channel      varchar(16) not null,
    expires_at   timestamptz not null,
    consumed_at  timestamptz,
    -- Wrong guesses. A six-digit code is one in a million per attempt but only a thousand tries from
    -- even odds, so attempts are capped and counted here rather than left to a rate limiter alone.
    attempts     int not null default 0,
    created_at   timestamptz not null default now()
);

create index password_reset_codes_user_idx on password_reset_codes (user_id, created_at desc);

-- Deliberately outside RLS, like the rest of the auth surface: the caller has no tenant yet.
