-- V44__price_list_currency.sql — one published price list per currency.
--
-- Until now there was a single list and it was implicitly rupees, which meant a customer outside
-- India could only be sold to by agreeing a per-company price by hand. That works for the first few
-- and not for a market: the standard list is what a website can quote and what a self-serve signup is
-- billed against.
--
-- Converting the rupee price was never the answer. ₹149 is about $1.70, roughly six times below the
-- cheapest credible competitor in the US (BambooHR ~$10/employee, Gusto $6 + a $49 base), and in B2B
-- software a price that far under the field reads as "not serious" rather than as a bargain. So the
-- USD list is priced against its own market: $6 per employee, $5 above 100, a $49 monthly minimum,
-- and the same two-months-free annual term. A 20-person company pays $120/month where BambooHR would
-- charge $200 — clearly cheaper, still credible.
--
-- The INR list is deliberately untouched.

alter table price_lists add column currency varchar(3) not null default 'INR';

-- Uniqueness moves from the date to the pair. Two lists starting the same day was ambiguous when
-- there was one currency; with several it is the normal case, and only a clash *within* a currency
-- leaves "which price applies" unanswerable.
drop index if exists idx_price_lists_effective_from;
create unique index idx_price_lists_currency_effective_from on price_lists(currency, effective_from);

-- The USD list, effective from the same far-past date as the rupee one so that every month already
-- recorded has a price to be read against — a company switched to USD must not hit a month with no
-- list, which is an exception rather than a bill.
insert into price_lists (id, effective_from, note, currency, monthly_minimum, annual_months_charged)
values ('00000000-0000-0000-0000-000000000002', date '2020-01-01',
        'Initial published price list (USD)', 'USD', 49, 10);

insert into price_list_tiers (id, price_list_id, up_to, rate, sort_order) values
    ('00000000-0000-0000-0000-000000000021', '00000000-0000-0000-0000-000000000002', 100, 6, 0),
    ('00000000-0000-0000-0000-000000000022', '00000000-0000-0000-0000-000000000002', null, 5, 1);
