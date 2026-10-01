-- V32__payslip_branding.sql — let a company brand its payslips (founder: "change how company name
-- comes on top"). A legal name + address show in the printed payslip header. Stored on the existing
-- (now platform-managed, non-RLS) company_settings row.

alter table company_settings add column legal_name varchar(160);
alter table company_settings add column address varchar(300);
