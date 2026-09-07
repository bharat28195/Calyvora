# Org structure and visibility

_Who can see whose data, and why it is decided the way it is. Companion to
[PLANS-AND-FEATURES.md](PLANS-AND-FEATURES.md), which covers what a customer has **bought**; this
covers what a person inside that customer may **see**._

---

## The one rule

> **Capability comes from your role. Visibility comes from the reporting tree. A job title grants
> nothing at all.**

Three separate mechanisms decide any request, and they compose:

| Layer | Question it answers | Where it lives |
|---|---|---|
| **Plan / features** | Has this *company* bought this module? | `FeatureGuardFilter`, `Feature` |
| **Role** | May this *person* do this kind of thing at all? | `@PreAuthorize`, `Role` |
| **Org scope** | Whose *rows* may they see? | `OrgScope` |

A request has to pass all three. Being HR does not get you a module the company has not bought; having
a big team does not let you run payroll.

---

## Roles: six, fixed, capability only

`OWNER`, `AGENCY_OWNER`, `ADMIN`, `HR`, `MANAGER`, `MEMBER` (see `Role`). These are **not**
customer-editable, and that is on purpose — they gate irreversible operations like running payroll,
issuing letters, and starting a subscription.

`OWNER` is the platform vendor, above every company. `AGENCY_OWNER` runs several companies and sees
only their summaries. The other four are within one company.

## Org scope: the tree, transitively

`OrgScope` answers "who may this person see", and it is the only thing that answers it.

- **OWNER, ADMIN, HR → the whole company.** Their job is the company, not a team.
- **Everyone else → themselves plus their whole downline**, walked transitively through
  `employees.manager_id`.

Two consequences that are easy to get wrong, and were wrong before:

**MANAGER is not on the whole-company list.** A manager's reach comes from *having reports*. A
MANAGER with nobody under them sees nobody, which is correct. Conversely a `MEMBER` with two interns
under them leads a team and gets the team screens — the section keys on the tree, not the role.

**It is transitive.** A head of department whose four leads each have seven people sees all
thirty-two, not four. Approvals work the same way: anyone up the chain can decide a request, which is
what happens in practice when the direct manager is the one on holiday.

An empty result means *nobody*, never *everybody*. Call sites must not treat an empty id set as an
absent filter.

### Cycles

`manager_id` is a plain self-referencing column with no database constraint against a loop. Since the
tree now carries authority, A→B→A would make each of them the other's subordinate and hand them each
other's data — a privilege escalation two profile edits deep, available to anyone who can edit an org
chart. `EmployeeService.requireNoCycle` walks upward from the proposed manager and refuses. `OrgScope`
is independently cycle-safe (visited set plus a depth cap) so a row written before this rule existed
cannot hang a request.

---

## What a team lead can and cannot see

**Can:** attendance, time off, expense claims, performance reviews, goals, exit clearance — for their
own downline only.

**Cannot: pay.** No salary, CTC, payslip or bank detail appears on any team screen, and it is not
merely hidden — `TeamService` never assembles it, and `PerformanceReviewResponse.withoutPay()` strips
the figures from a review before it leaves the server for anyone who is not HR, leadership, or the
person whose salary it is.

A hike is still proposed by a manager, as a **percentage**. That is all the judgement needs; the
absolute number it applies to is HR's.

> An **expense claim is not pay.** It is money the person is out of pocket for, and the lead who
> approved the trip is the one who can say whether the taxi was real. It belongs on the team screens.

---

## Designations: the ladder, customer-defined

`designations` (Flyway V48) is a per-company list — Intern, Junior Developer, Senior Engineer, Lead,
Principal, or whatever that customer calls theirs. Editable under **People → Designations** by ADMIN
and HR.

**A designation grants nothing.** Neither `OrgScope` nor any `@PreAuthorize` reads the table. That is
precisely what makes it safe to hand to a customer: *a permission you can award yourself by renaming
your own row is not a permission.* The editor says so on the page, because "designations" reads like
permissions to anyone who has used another HR product.

Practical notes:

- **Optional.** Existing customers have job titles and no ladder; nothing changed for them at
  migration. The picker only appears once a company has defined at least one rung.
- **`job_title` survives alongside it.** The designation is the rung ("Senior Engineer"); the job
  title is what goes on a business card ("Senior Engineer, Payments").
- **Levels are sparse** (0, 10, 20) so a rung can be slotted between two others without renumbering.
- **Archive, don't delete, once anyone holds it.** Deleting would blank the level on their profile;
  archiving keeps their history and stops it being offered to anyone new. The API refuses a delete
  that would orphan people, and names the count.
- **Unique per company, case-insensitively.** "Senior Developer" and "senior developer" are one rung —
  allowing both recreates the drift the table exists to prevent.

## Teams

Departments (`departments`) already carry a `parent_id` and a `lead_user_id`, so a company can model
nested teams each with their own lead without a new table. Department membership is *organisational*,
not a permission: reporting lines still decide visibility, so moving somebody between departments does
not change who can read their attendance.

---

## Modelling a real company

A firm with an admin team, an HR team, managers, leads, senior and junior developers and interns maps
like this:

| Real-world | Role | Designation | Reports to |
|---|---|---|---|
| Founder / ops | `ADMIN` | — | — |
| HR team | `HR` | HR Executive / HR Manager | Head of HR |
| Engineering head | `MANAGER` | Head of Engineering | Founder |
| Team lead | `MEMBER` or `MANAGER` | Lead Engineer | Head of Engineering |
| Senior dev | `MEMBER` | Senior Engineer | Lead Engineer |
| Intern | `MEMBER` | Intern | Senior Engineer |

The senior engineer gets "My team" for their intern **without any role change**, because the intern
reports to them. The head of engineering sees the leads, the seniors and the interns. Neither sees
anybody's salary. HR sees everyone, salary included. Admin sees everything.

Every arrow in that table is customer-editable. None of it required us to ship a permission matrix.

---

## Deliberately not built

- **Customer-defined roles with a permission grid.** Considered and shelved (PD-32). It re-keys every
  endpoint off permissions instead of roles and turns "why can't X see Y" into a per-customer
  investigation. The tree answers the question customers were actually asking. Revisit when a real
  customer asks to move a *specific* permission.
- **Manager approval of team expenses.** Still Admin/HR, so the team expenses screen is read-only —
  showing a button that 403s is worse than showing none.
- **Department-based visibility.** Reporting lines only, for now. Two mechanisms that can each grant
  access are two mechanisms to audit.
