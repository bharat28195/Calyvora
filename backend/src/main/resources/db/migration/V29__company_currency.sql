-- V29__company_currency.sql — company-wide display currency (localization, founder pt 4).
--
-- Timezone and locale already live on company_settings; add the currency the whole app formats money
-- in. Default INR (the launch market). No RLS change — company_settings is keyed by company_id and
-- already isolated.

alter table company_settings add column currency varchar(8) not null default 'INR';
