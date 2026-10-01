-- V37__price_lists.sql — move the price list out of code and into data the platform owner can edit.
--
-- Changing what you charge should not require a deploy. It also should not quietly rewrite history:
-- if last month's invoice said ₹14,900, it has to keep saying ₹14,900 after a price change, or the
-- billing page becomes something a customer can't trust. So a price list is *versioned by the date it
-- takes effect*, and every calculation asks for the list in force for the month being priced.
--
-- Platform-level data like companies and subscriptions — deliberately not RLS-scoped, since one price
-- list governs every tenant.

create table price_lists (
    id             uuid primary key,
    effective_from date not null,
    note           varchar(200),
    created_at     timestamptz not null default now()
);
-- One list per start date: two lists claiming the same day would make "which price applies" ambiguous.
create unique index idx_price_lists_effective_from on price_lists(effective_from);

create table price_list_tiers (
    id             uuid primary key,
    price_list_id  uuid not null references price_lists(id) on delete cascade,
    -- Employees covered by this tier, cumulative. NULL on the final, open-ended tier.
    up_to          integer,
    rate           numeric(10,2) not null,
    sort_order     integer not null
);
create index idx_price_list_tiers_list on price_list_tiers(price_list_id, sort_order);

-- The list already published on the website and charged by the app: ₹149 for the first 100
-- employees, ₹99 beyond. Dated well in the past so it governs every invoice generated so far.
insert into price_lists (id, effective_from, note)
values ('00000000-0000-0000-0000-000000000001'::uuid, date '2020-01-01', 'Initial published price list');

insert into price_list_tiers (id, price_list_id, up_to, rate, sort_order) values
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001'::uuid, 100,  149, 0),
    (gen_random_uuid(), '00000000-0000-0000-0000-000000000001'::uuid, null,  99, 1);
