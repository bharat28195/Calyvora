-- V85__price_follows_plan.sql — putting a company on a plan sets what it pays (PD-79).
--
-- Until now choosing a plan only switched modules on and off. The bill still came from the
-- published price list (₹149, ₹99 above 100 people, ₹1,299 minimum), so a shop sold the ₹49
-- Attendance & Leave plan was billed ₹149 unless someone also remembered to type an agreed price.
--
-- When a plan is chosen, its price is now copied onto the subscription and this column records which
-- plan it came from. Copying, not reading the plan live, keeps the rule that a price change is never
-- retroactive: editing a plan's price later does not re-price months already billed.
--
-- Null means the price did not come from a plan: either the published list (custom_price = false)
-- or a rate agreed with this customer by hand (custom_price = true), which choosing a plan never
-- overwrites.
--
-- Additive only. No existing row changes: a company already on a plan keeps its current price until
-- the plan is chosen again in the console.

alter table subscriptions add column price_plan_code varchar(32);
