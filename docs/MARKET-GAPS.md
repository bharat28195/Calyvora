# Selling in India and the United States — what is missing, market by market

Orbit is closer to sellable in India than the feature list suggests, and further from sellable in the
United States than it looks. The reason is the same in both cases: **payroll is not a feature, it is a
regulated activity** — and the two countries regulate it so differently that they are effectively two
products.

Written 4 September 2026, against capability verified by functional test on the live deployment.

---

## The finding that reframes everything

Orbit **already has a payslip engine**. `PayslipComponent` models named components with a calculation
kind — percent-of-basic, fixed amount, remainder — marked as earning or deduction, configured once per
company. It can express *"Provident Fund = 12% of Basic"* today.

The employee record **already stores** PF number, UAN, ESI number, professional-tax state, PAN, bank
account and IFSC.

What does not exist is anything that **knows the rules**: the ₹15,000 PF wage ceiling, the split
between EPF and EPS, the employer's matching share, the ₹21,000 ESI eligibility threshold, or
state-wise PT slabs.

**Structure without compliance.** That distinction is the whole analysis, and it is good news: the
hard architectural work is done, and what remains is well-defined, finite rule-writing.

---

## What Orbit has today

Verified by exercising create, update and delete against the live deployment — not read off a roadmap.

| Area | Today | India | US |
|---|---|---|---|
| Core HR & org | People, departments, managers, onboarding, exits + checklist | ✅ | ✅ |
| Attendance | Check-in/out, regularisation with approval, day view | Nearly | Nearly |
| Shifts | Shift definitions, roster assignment | ✅ | ✅ |
| Leave | 4 types, balances, approval flow scoped to a manager's own reports | Nearly | ❌ |
| Payroll structure | Configurable components, payslips, real figures | Structure only | Structure only |
| Statutory fields | PF, UAN, ESI, PT state, PAN modelled | Stored, not computed | Wrong country |
| Expenses | Claim → approve → reimburse | ✅ | ✅ |
| Documents | Letterhead, 5 templates, 22 merge fields, offer letters | ✅ | ✅ |
| Recruitment | Jobs, pipeline, offer letter, hire → employee | ✅ | ✅ |
| Performance | Cycles, self + manager review, hike proposal | ✅ | ✅ |
| Helpdesk / knowledge / feed | Tickets, wiki with search, company feed | ✅ | ✅ |
| Multi-tenant | Vendor / agency / company, seats, INR + USD price lists | ✅ | ✅ |

---

## India — one quarter of work from sellable

Ranked by how often each kills a deal. Everything in the first three is table stakes at greytHR, Keka
and Zoho Payroll: a buyer will not ask whether you have it, they will assume it and discover the
absence during the trial.

### Deal-breakers

**1. Statutory computation — PF, ESI, PT.** Not the fields, the *rules*. The ₹15,000 PF wage ceiling,
employee and employer shares split across EPF and EPS, the ₹21,000 ESI threshold and what happens when
someone crosses it mid-contribution-period, and PT slabs that differ by state. The component engine can
hold the numbers; nothing computes them.

**2. Income tax — declarations, TDS, Form 16.** Old versus new regime selection, 80C/80D declarations
with proof-submission windows, projected annual tax spread across remaining months, monthly TDS
deduction, Form 24Q quarterly returns, Form 16 at year end. The largest single piece of work on this
list, and the one finance teams judge you on.

**3. Salary disbursement.** A bank-ready payment file (ICICI, HDFC, Axis formats) or a payout
integration. You already collect account number, IFSC and payment mode — the last mile is producing the
file that moves money. Without it, someone retypes payroll into net banking. RazorpayX Payroll was
built almost entirely around this one step.

### Loses deals

**4. Full & final settlement.** Exits and an exit checklist exist; the F&F *calculation* does not —
notice-period recovery, leave encashment, gratuity after five years, pending reimbursements, and a
settlement statement. Every departure currently ends in a spreadsheet.

**5. Leave policy engine.** Manager approval landed on 5 September 2026 — a manager now sees and
decides their own reports, HR and admins still see everything. The *policy* engine is still missing,
and that is the part buyers ask about: four fixed types with a flat allowance is a demo, not a policy.
Expected are monthly accrual, carry-forward with caps, encashment, comp-off against weekend work,
sandwich-leave rules and probation restrictions.

**6. Biometric device integration.** ESSL and ZKTeco machines are on the wall of most Indian SMB
offices. "Our attendance comes from the machine at the door" is an early question, and a web check-in
button is not an acceptable answer for a factory or a three-shift operation. **Priority HR's own
clients are exactly this profile** — that is a design partner most founders pay for.

**7. Flexible benefits and reimbursements.** LTA, fuel and driver, telephone, meal cards — declared,
claimed against bills, tax-exempt within limits. Distinct from the expense module because it changes
taxable income.

### Worth having

**8. Overtime and statutory registers.** OT at statutory multiples; muster roll and wage registers
under the Shops & Establishments and Factories Acts. Matters most to the labour-heavy clients already
served by the services business.

### An opening, not a gap

**9. WhatsApp for approvals.** Email is where Indian approvals go to die. Leave requests, approvals and
payslip delivery over WhatsApp would be genuinely differentiating, and incumbents are slow here because
they are built email-first.

---

## United States — sell a different product

### Do not build US payroll

Federal, state, county and city withholding across more than ten thousand tax jurisdictions; FICA,
FUTA and per-state SUTA rates; quarterly 941s, annual W-2s and 1099s; multi-state rules for remote
employees. Then, to actually remit the taxes, **money-transmitter licences in most states**.

This is a multi-year regulated business, not a module. The realistic routes are an embedded payroll API
(Check, Gusto Embedded, Zeal) as infrastructure, or not offering US payroll at all.

### The recommendation: sell HR, not payroll

**BambooHR is a very large company built on exactly that line.** Reposition `calyvora.net` as people,
hiring, performance and documents — *"works alongside your payroll provider"* — and integrate with
Gusto. That turns an unsellable promise into an honest product that already mostly exists.

Even on that narrower line, three things are still required:

**I-9, W-4 and E-Verify.** A US employee cannot legally start without I-9 verification. Orbit's
onboarding is task-based; it needs to be document-based and legally attested. Required even if you
never touch payroll.

**PTO accrual and state sick-leave law.** US leave is accrual-based, not allowance-based: hours earned
per hour worked, carry-over caps, and mandated paid sick leave differing by state and sometimes city.
The flat annual allowance cannot express it, and getting it wrong is legal exposure rather than
inconvenience.

**FLSA timekeeping** for mid-market: exempt versus non-exempt classification, overtime above 40 hours
weekly, meal and rest break records where mandated. Timesheets, not just check-in and check-out.

Then, for anything above a small company: **benefits administration** (health insurance enrolment,
401(k), COBRA, ACA 1095-C) — in the US, benefits are often *why* a company buys an HR system at all —
and the procurement gate of **SOC 2 Type II, SAML SSO, SCIM and audit logs**. "We use row-level
security in PostgreSQL" is true and genuinely good, and is an answer to a question they did not ask.

---

## Missing in both — the cheapest wins

These serve every buyer, cost the least, and several are already half-built.

| Gap | Why it matters | Effort |
|---|---|---|
| Mobile experience | Check-in, leave, payslips. Most employees never open a laptop. | Medium |
| Google / Microsoft sign-in | Removes the password objection and most support load. | Small |
| Audit log | Who changed whose salary. First question from any reviewer. | Small |
| Data export | Answers the lock-in objection, which makes people readier to commit. | Small |
| Two-factor for admins | The accounts that read salaries deserve more than a password. | Small |
| Org chart | The manager relationship is already stored. It is a view, not a feature. | Small |
| Accounting integration | Tally in India, QuickBooks or Xero in the US. Payroll must land in the books. | Medium |
| Asset register | Laptops issued and recovered — the missing half of the exit checklist. | Small |
| Surveys / eNPS | Cheap, and it turns an HR system into an HR *platform* in a pitch. | Small |

---

## Recommended sequence

1. **Finish India properly before touching anything else.** PF, ESI, PT, TDS and Form 16, then the bank
   payment file. That sequence takes Orbit from "interesting" to "buyable" in the market you already
   understand and sell into.
2. **Sell the US as HR, not payroll.** Reposition `calyvora.net`, add I-9/W-4 onboarding and PTO
   accrual, integrate with Gusto.
3. **Use Priority HR as the design partner.** Real clients, real payroll, real edge cases — build the
   compliance engine against actual data rather than against the specification.
4. **Take the cross-market wins alongside.** SSO, audit log, export, org chart, 2FA. Days each, and
   together they make the product feel finished.

---

*Competitor positioning reflects the standard feature sets of the named products from general
knowledge. Re-check specifics before any of it goes into sales material.*
