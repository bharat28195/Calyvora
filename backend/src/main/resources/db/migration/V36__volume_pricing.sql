-- V36__volume_pricing.sql — put companies on the published price list (₹149 for the first 100
-- employees, ₹99 beyond) instead of a flat ₹100 stored per company.
--
-- The stored `price_per_employee` has to keep working for companies the platform owner has quoted a
-- special rate to, so a flag distinguishes the two: `custom_price` false means "bill from the
-- standard list", true means "this company negotiated its own rate, leave it alone".
--
-- Every existing row was created with the old default of 100 and nobody chose it, so those move to
-- the standard list. Any row whose price differs from that default was deliberately set by the
-- owner in the platform console and keeps its rate.

alter table subscriptions add column custom_price boolean not null default false;

update subscriptions set custom_price = true where price_per_employee <> 100;
