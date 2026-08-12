# HR Differentiator Backlog

> The execution checklist for what makes Orbit win an HR deal against Zoho People, Keka, and
> BambooHR. Derived from the competitor scan of **2026-08-12** (FOUNDER.md §8, PD-22).
> [12-differentiators.md](12-differentiators.md) is the *architectural* argument; this file is the
> *feature-level* proof of it inside People OS.

**Branch:** `feature/orbit-differentiators`, in a separate worktree at `../Calyvora-differentiators` (see L-2).

## The thesis

We do not win by matching their checklist. Zoho, Keka, and BambooHR have a decade of head start on
attendance, payroll, and reviews; a pitch of "we also have those" makes us a worse Keka.

We win because **the work side of the business already lives in the same database as the people
side**. `work` (projects, sprints, tasks, tickets), `client`, `knowledge`, and `people` are one
schema, one tenant, one permission model. Keka bolted PSA on. Zoho splits it across five products
you integrate. BambooHR does not do it at all.

> **The pitch, in one line:** *Your HR system knows what your people actually did.*

Everything in §A is a proof point for that sentence. §B is the defensive list — features whose
absence loses a deal before we get to present §A.

---

## §A · Differentiators — what we lead with

Ordered by demo impact per unit of build effort. **D1 is the one that wins meetings.**

### D1 · Evidence-based performance reviews
Every competitor's review is a blank form filled from memory six months later. Every HR buyer knows
this and hates it. We have sprint reports, task history, ticket resolution, and goals in the *same
database* as the review — so we can open the review pre-filled with what the person actually
shipped, each line linking back to the source record. The manager judges; they don't reconstruct.

**Why they structurally can't:** the work data lives in another product, or nowhere.

- [x] `V43` — `tasks.completed_at` / `tickets.resolved_at`, stamped by the status transition itself.
      Without these, a period could only be approximated by `updated_at`, which any later edit moves.
- [x] `ContributionService` (in `work`) — what an employee finished between two dates, returned as a
      rendered DTO so `performance` never touches Work's entities
- [x] `PerformanceEvidenceService` — assembles the pack; delegates authorization wholesale to
      `PerformanceReviewService.requireViewable`, so the evidence cannot drift onto a weaker rule
      than the review it describes
- [x] Completed work items in period (ref, title, project, finished-on, deep link, points)
- [x] Sprints contributed to, carrying *their* share rather than the team's totals
- [x] Tickets resolved in period
- [x] Attendance/leave summary for the period (context, never a score input)
- [x] `GET /performance/reviews/{id}/evidence` — tenant-scoped, permission-checked
- [x] Evidence panel in the review UI, collapsed by default, fetched on first expand
- [x] Integration tests: period boundary, reopened work, self/manager/stranger, cross-tenant (5/5)
- [x] Employee sees their own evidence pack before writing their self-assessment
- [x] Mock backend implementation, so the offline demo shows the panel too
- [x] "Cite this" — drops an evidence line into whichever narrative the viewer has open (the member's
      self-assessment, or the manager's summary), appended on its own line without clobbering what
      they have typed. Citing the same line twice inserts it twice.
- [x] Period bounds read the **company's** clock, not the server's. `CompanyZone` now owns that rule
      for the whole app and `AttendanceService` uses it too, so the two cannot drift. Covered by a
      test that picks a zone at runtime whose calendar date differs from UTC's, and so fails against
      the old UTC arithmetic whichever way the offset falls.

**Known limits, deliberately left:**

- **Backfilled rows are approximate.** Work finished before V43 took `updated_at` as its completion
  date. There is no better source — the information was never recorded — so the first cycle's
  evidence is imprecise for older items. Every transition after V43 is exact. Not fixable in code;
  it fades as history accumulates.
- **Goals are not repeated in the pack.** `PerformanceReviewResponse` already carries them and the
  card already renders them, so including them here would only duplicate payload. The open question
  is different and needs schema: showing goal progress *as at period close* rather than as it stands
  today would require goal history, which we do not store.

### D2 · Cost-to-revenue per person and per project
We hold compensation, attendance, projects, and clients. Nobody else holds all four. Join them:
what did this project cost in salaried hours, what did we bill, what is the margin, which client is
unprofitable. Keka's PSA gets closest but starts from *manually entered* timesheets — ours derives
from attendance plus real work activity.

For any services business (agencies, IT services, consulting) this is the number the founder
actually cares about, and no HR vendor gives it to them.

- [ ] Cost rate per employee derived from compensation ÷ working days
- [ ] Effort attribution: work-item activity → project, per employee, per period
- [ ] Billable vs non-billable flag on projects and work items
- [ ] Contract/rate value on `Client`
- [ ] Project P&L: cost, billed, margin, trend
- [ ] Per-person utilisation and realisation
- [ ] Analytics page: margin by project, by client, by team
- [ ] Guard: compensation figures visible only to roles that already see pay

### D3 · Cross-domain AI assistant — *already built, badly undersold*
[`AssistantService`](../backend/src/main/java/com/calyvora/assistant/AssistantService.java) already
does tenant-scoped RAG across People, Work, and Knowledge. Zia and Bamboo AI answer *HR* questions
(leave balance, policy lookup). Ours can answer *staffing* questions — a different and much higher
value class, and it only works because of the shared schema.

- [x] Cross-domain retrieval over People + Work + Knowledge
- [x] Tenant-scoped grounding; Claude when configured, local grounded provider otherwise
- [ ] Staffing queries: "who is free next month", "who has worked with this client before"
- [ ] Capacity queries: "which team is over-committed this sprint"
- [ ] Cite sources as deep links, not prose
- [ ] Suggested-question chips that *demonstrate* cross-domain reach on first open
- [ ] Marketing: stop describing this as a chatbot; it is a staffing brain

### D4 · Onboarding that provisions real work
`OnboardingService` does checklists. Extend it so day one ends with the new hire *in the system
doing actual work*: knowledge pages assigned, project access granted, first tasks seeded. The
competitors' onboarding ends at "signed the handbook."

- [ ] Onboarding template steps can grant project access
- [ ] Steps can assign knowledge-space reading with acknowledgement
- [ ] Steps can seed starter work items assigned to the new hire
- [ ] Buddy/mentor assignment surfaced in both people's Inbox
- [ ] Onboarding progress visible to hiring manager and HR

### D5 · Helpdesk deflected by the knowledge base
We own `helpdesk` and `knowledge`. Put the assistant between them: answer before the ticket is
filed, and turn a resolved ticket into a knowledge page. Zoho has both products — as separate
purchases that don't talk.

- [ ] Suggest knowledge answers while the ticket is being typed
- [ ] "This answered it" → deflected, with a deflection metric
- [ ] Promote a resolved ticket into a knowledge page in one click
- [ ] Deflection rate on the analytics dashboard

### D6 · Staffing-agency mode
`client` plus the multi-tenant platform layer means we can serve staffing and recruitment agencies —
placing people *at* clients, tracking who is deployed where, billing per placement. None of the
three serve this; agencies use spreadsheets. Pairs with the HR Services page already on the site.

- [ ] Placement: employee ↔ client ↔ period ↔ bill rate
- [ ] Deployment view: who is placed where, who is on bench
- [ ] Bench cost report
- [ ] Per-placement billing feeding the existing billing engine

---

## §B · Table stakes — what we must close to stop losing deals

Present in **all three** competitors, absent in ours. These do not win deals; their absence loses
them before the demo. Cheap ones first — they are days, not weeks.

- [ ] **E-signature** on offer letters and documents *(all three have it; our clearest single hole)*
- [ ] **Engagement / pulse surveys** with eNPS *(BambooHR has moved past raw eNPS to scored wellbeing)*
- [ ] **Offboarding** — exit checklist, clearance, asset return
- [ ] **CSV import/export** for employees; report export
- [ ] **Custom fields** on the employee record (per-tenant)
- [ ] **Workflow / approval builder** — ours are hardcoded per module *(weeks, not days)*
- [ ] **Benefits administration** — enrolment, plans, dependants
- [ ] **Public API + integrations** — Google Workspace / Microsoft 365 directory sync, webhooks
- [ ] **SAML / SSO** — enterprise gate
- [ ] **Custom report builder** — our `analytics` serves fixed dashboards only
- [ ] 360° feedback and continuous 1:1s *(2 of 3 have it)*
- [ ] Geofencing / biometric / mobile punch-in *(2 of 3)*
- [ ] Recognition & rewards — partial; `feed` has some celebration surface
- [ ] Mobile app *(2 of 3)*

### India statutory payroll — its own tier
If India is the target market this is **not optional and not a differentiator** — it is the price of
entry, and it is Keka's moat. We store PF/ESI/PAN/UAN *identifiers* in `EmployeeFinance` but compute
none of it.

- [ ] TDS computation, both regimes
- [ ] Professional Tax slabs by state; LWF
- [ ] Form 16 and Form 24Q generation
- [ ] PF / ESIC return files
- [ ] Employee tax declarations + investment-proof upload and verification
- [ ] Full-and-final settlement: gratuity, leave encashment
- [ ] Loans and salary advances with EMI recovery from payroll
- [ ] Tenant-configurable salary structure / CTC breakup builder

---

## §C · Explicitly not building

Recorded so we don't drift into them. Each is expensive, needs data or partnerships we don't have,
and none wins a deal against an incumbent that has had it for a decade.

| Not building | Who has it | Why not |
|---|---|---|
| **LMS / training / courses** | Zoho | A product in itself; buyers usually already own one. Keka doesn't lead with it either. |
| **Salary benchmarking data** | BambooHR | Needs a data corpus we cannot fake. |
| **Pay-equity analysis** | BambooHR | US-market concern; depends on benchmarking above. |
| **Asset management** | Keka | Low differentiation. Revisit only as part of D4/offboarding. |

---

## Honest caveats

Two things to hold alongside this plan:

- **The gap list is biased against us.** It compares competitor *marketing pages* against our
  *source code*. Their pages list aspirations and paid add-ons at full volume — BambooHR's payroll,
  benefits, and time tracking are all add-ons, not base plan. The gaps are real; the severity is
  inflated relative to what a customer gets on day one.
- **D2 needs verification before we build the pitch on it.** Keka's PSA may join compensation to
  project margin more deeply than their site suggests. Get a trial account first.

Sources: [Zoho People](https://www.zoho.com/people/), [Keka](https://www.keka.com/),
[Keka PSA](https://www.keka.com/psa-software), [BambooHR](https://www.bamboohr.com/).
