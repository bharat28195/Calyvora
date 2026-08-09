-- V39__price_list_minimum_and_annual.sql — two commercial terms that belong to the price list.
--
-- monthly_minimum: the floor a company pays regardless of headcount. Without one, a four-person
-- customer pays ~₹600/month and costs far more than that in support — the classic way a per-seat SaaS
-- loses money on its smallest accounts. At ₹1,299 it is still roughly five times below Keka's
-- ₹6,999/month minimum, which is the whole reason a startup would choose us.
--
-- annual_months_charged: how many months an annual prepayment is billed for. 10 means "two months
-- free" — cash upfront, and a customer who has paid for a year is far less likely to drift away.
--
-- Both live on the price list rather than in code so they change with a form, and both are versioned
-- with it, so an invoice already issued keeps the terms that applied when it was raised.

alter table price_lists add column monthly_minimum numeric(10,2) not null default 0;
alter table price_lists add column annual_months_charged integer not null default 12;

update price_lists
set monthly_minimum = 1299, annual_months_charged = 10
where id = '00000000-0000-0000-0000-000000000001';
