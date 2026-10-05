-- V59__drop_default_statutory_template_deductions.sql — take the made-up PF and income tax off payslips.
--
-- The default payslip template (V25, PayslipTemplateService) shipped two DEDUCTION components:
-- "Provident fund" at 12% of basic and "Income tax" at a flat 10% of gross. Neither is how either is
-- actually worked out. Real PF and TDS come from the statutory engine — PF on capped basic, TDS from
-- each employee's declaration — and both are switched OFF per company until the numbers are checked.
-- The template bypassed that switch: every payslip showed a ~16% "PF + income tax" deduction with the
-- statutory features off, and a company that turned PF or TDS on had both deducted TWICE, because the
-- statutory lines are added on top of the template's.
--
-- Removed only where they are still EXACTLY the shipped defaults (name, kind, calc and value). A
-- company that renamed one, or changed its rate, made a decision; that row is theirs and is left alone.
--
-- Why the loop and set_config: payslip_components is under FORCE row level security, and Flyway runs
-- with no tenant bound, so a plain DELETE here would match no rows at all and report success. Binding
-- each company in turn is the V50 pattern; is_local => true confines it to this transaction.
do $$
declare
    c uuid;
begin
    for c in select id from companies loop
        perform set_config('calyvora.company_id', c::text, true);
        delete from payslip_components
         where company_id = c
           and kind = 'DEDUCTION'
           and (   (name = 'Provident fund' and calc = 'PERCENT_OF_BASIC' and value = 12)
                or (name = 'Income tax'     and calc = 'PERCENT_OF_GROSS' and value = 10));
    end loop;
    perform set_config('calyvora.company_id', '', true);
end $$;
