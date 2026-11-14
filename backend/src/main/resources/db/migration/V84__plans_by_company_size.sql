-- Packages that let a small company buy only what it uses (2026-11-14).
--
-- The three plans from V47 were built when Orbit still had a work tracker and a clients module, and
-- started at payroll. A shop whose CA runs payroll could not buy attendance and leave alone. They are
-- retired, never deleted: a company already on one keeps it, and nobody new can be put on it.
--
-- People, attendance, leave, letters, the dashboard and the inbox are in every plan without being
-- listed: they are not switchable features. Payroll covers every screen that shows or changes pay,
-- including tax declarations and the full and final settlement. Statutory payroll and income tax
-- withholding are in no plan on purpose: they stay off until a customer's figures have been checked,
-- and are switched on for that one company.

update plans set active = false where code in ('ESSENTIALS', 'GROWTH', 'COMPLETE');

insert into plans (code, name, description, price_per_employee, sort_order) values
    ('ATTENDANCE', 'Attendance & Leave',
     'People, attendance, leave, holidays, shifts, letters and announcements. For a company whose payroll is run elsewhere.',
     49, 10),
    ('PAYROLL', 'Payroll',
     'Everything in Attendance & Leave, plus payroll, payslips, the bank file, tax declarations and full and final settlement.',
     79, 11),
    ('GROWTH_2026', 'Growth',
     'Everything in Payroll, plus expenses, recruitment, the helpdesk and company documents.',
     119, 12),
    ('COMPLETE_2026', 'Complete',
     'Everything in Growth, plus performance reviews, insights and the AI assistant.',
     149, 13);

insert into plan_features (plan_code, feature) values
    ('ATTENDANCE', 'SHIFTS'),
    ('ATTENDANCE', 'FEED'),

    ('PAYROLL', 'SHIFTS'),
    ('PAYROLL', 'FEED'),
    ('PAYROLL', 'PAYROLL'),

    ('GROWTH_2026', 'SHIFTS'),
    ('GROWTH_2026', 'FEED'),
    ('GROWTH_2026', 'PAYROLL'),
    ('GROWTH_2026', 'EXPENSES'),
    ('GROWTH_2026', 'RECRUITMENT'),
    ('GROWTH_2026', 'HELPDESK'),
    ('GROWTH_2026', 'KNOWLEDGE'),

    ('COMPLETE_2026', 'SHIFTS'),
    ('COMPLETE_2026', 'FEED'),
    ('COMPLETE_2026', 'PAYROLL'),
    ('COMPLETE_2026', 'EXPENSES'),
    ('COMPLETE_2026', 'RECRUITMENT'),
    ('COMPLETE_2026', 'HELPDESK'),
    ('COMPLETE_2026', 'KNOWLEDGE'),
    ('COMPLETE_2026', 'PERFORMANCE'),
    ('COMPLETE_2026', 'ANALYTICS'),
    ('COMPLETE_2026', 'ASSISTANT');
