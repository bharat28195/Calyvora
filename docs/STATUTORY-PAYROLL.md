# Statutory payroll — Provident Fund, behind a per-customer switch

Started 6 September 2026. **PF is built; ESI, professional tax and TDS are not.**

Everything here is off for every company until you turn it on. A compliance engine is the first thing
in Orbit that can print a wrong figure on somebody's payslip and have them act on it, so it is trusted
one customer at a time, after their numbers have been checked against a real run.

---

## The switch

Two independent gates, and **neither implies the other**:

| Gate | Who controls it | Where |
|---|---|---|
| `STATUTORY_PAYROLL` for the company | **you**, the vendor | Platform console → company row menu |
| `pfStatus = ENABLED` for a person | the company's HR | Employee → Finance |

A company with the feature on still deducts nothing from an employee who is not enrolled, and an
employee marked enrolled at a company without the feature sees no change. Both are tested.

### Turning it on for a customer

Platform console → the company's row → **⋯** → *Turn on statutory payroll (PF)*. It takes effect on
the next payslip generated; nothing is backdated.

Turning it back off is the same menu item, and stops the deduction immediately. That was a design
requirement: "turn it off for that one company while we look at it" must never mean a redeploy.

### What the customer sees

**Payroll → Statutory (PF)** is visible whether or not the feature is on, and says which. Hiding it
when off would leave an HR manager who has been told "PF starts next month" with nowhere to check the
rates first; showing it silently would imply deductions are already happening.

They can edit the rates at any time. They cannot switch the feature on — that is what you sell.

---

## The rules, and the two that catch people out

The employee contributes 12% of **PF wages** (basic + dearness allowance). The employer contributes
12% too, but it is *split*: 8.33% to the Pension Scheme (EPS) and the remainder to EPF. Admin charges
0.5% and EDLI 0.5% are employer-only.

**1. EPS is always capped at the wage ceiling** — even when the employer contributes on a higher wage.
A company may lawfully opt out of the ceiling for PF; it does not thereby opt out for EPS. Computing
8.33% of a ₹50,000 basic puts ₹4,165 into the pension share instead of ₹1,250. **The employer total is
unchanged**, which is exactly why nobody notices until the EPFO rejects the return.

**2. The employer's EPF share is the remainder, not its own percentage.** Deriving it by subtraction
keeps the two halves adding to the employer total to the rupee, whatever rounding does — and the filed
return lists both halves.

Everything is rounded to whole rupees, per component, because that is what the ECR file carries.

### PF is computed on basic, not gross

`PayslipTemplateService` marks one earning as the basis — "Basic" in every default template — and that
is the PF wage. Using gross would overstate every PF deduction in the company by roughly a factor of
two, **and it would look plausible on the payslip**. The basis amount is now returned explicitly from
the template calculation so no caller has to re-derive or guess it.

---

## Configuration

`Payroll → Statutory (PF)`, or `PATCH /api/v1/payroll/pf-settings`.

| Setting | Default | Note |
|---|---|---|
| Wage ceiling | 15,000 | Has moved before and will again — a row, not a constant |
| Cap at ceiling | on | Off = contribute on full basic. Both lawful; thousands a month per employee |
| Employee rate | 12% | |
| Employer rate | 12% | |
| Of which EPS | 8.33% | Carved out of the employer rate, never added — refused if it exceeds it |
| Admin charges | 0.5% | Employer only |
| EDLI | 0.5% | Employer only, on the capped wage |

A company with no settings row uses these defaults, so a customer who is switched on before visiting
the screen still gets lawful figures.

---

## What it changes on screen

- **Payslip** gains an employee PF deduction line — a real deduction, inside net, not a note beside
  it — and an employer-contributions block showing EPS, EPF, admin, EDLI and the PF wages the
  calculation used. That last number answers the most common payslip question in India: *"why is my PF
  ₹1,800 when my basic is ₹50,000?"*
- **Payroll run** gains a PF column and two totals: employer PF, and gross + employer PF, which is
  what the month actually costs. Columns appear only when there is something in them.

The statutory block is **null**, not zeroes, when it does not apply. "PF: 0" reads as an error to the
person holding the payslip; an absent section reads as "not applicable", which is the truth.

---

## How it is tested

**`PfCalculatorTest`** — 10 unit tests, 0.11 seconds. Wages in, expected splits out, checkable by hand
against the Act. Includes the ceiling opt-out case, the EDLI-follows-the-cap case, whole-rupee
rounding, and a moved ceiling.

**`StatutoryPayrollIntegrationTest`** — 10 tests through real HTTP, and these are the commercially
important ones:

- nothing changes for a company until the vendor turns it on
- **turning it on for one company leaves another alone** — a flag that leaked across tenants would
  deduct money from the salaries of a customer who never asked
- the deduction actually reduces net pay (showing PF while paying as if it were not deducted would
  look right and transfer the wrong amount)
- an employee who is not enrolled gets nothing, even with the feature on
- turning it back off stops it
- a company can read its own flags but cannot set them

---

## Design notes worth keeping

**`company_features` has no row-level security, deliberately.** The platform owner toggles these for
*other* tenants from a session bound to its own platform company; an RLS policy keyed on
`calyvora.company_id` would make every such write invisible and silently do nothing. It sits with
`companies`, `users` and `subscriptions` on the un-RLS'd control surface, and holds no personal data —
a company id, a feature name, a boolean. `pf_settings` **is** RLS'd: it is tenant data.

**Rates are columns, not constants.** Statutes change. A rate change should be an `UPDATE` and a line
in the release notes, not a redeploy — and a company with a different arrangement agreed with its
auditor can hold it without forcing a fork.

---

## What is next, in order

1. **ESI** — 0.75% employee / 3.25% employer, eligibility threshold ₹21,000, and the rule that
   someone who crosses it mid-contribution-period stays in until the period ends. That last part is
   where implementations usually go wrong.
2. **Professional tax** — slab tables per state. Tedious rather than hard, and needed by any customer
   outside one state.
3. **TDS** — regime choice, 80C/80D declarations, projected annual tax spread over remaining months,
   Form 24Q, Form 16. The largest piece, and the one finance teams judge you on.
4. **The ECR file** for EPFO upload, and a bank payment file. Without these the numbers are correct
   and somebody still retypes them into a portal.
