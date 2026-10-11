-- The end date a "your subscription ends in 5 days" reminder was last sent for (PD-76). Keyed to the
-- date, not a flag, so when the owner extends a subscription the new end date gets its own reminder.
alter table subscriptions add column expiry_reminded_for date;
