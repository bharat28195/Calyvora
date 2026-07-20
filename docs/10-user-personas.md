# 10 · User Personas

Personas keep us honest about *who* we build for. Each entry gives **Goals**, **Pain points**
(with today's fragmented tools), and **Platform usage** (which OS-apps they live in and how
AI serves them). Personas map to the [Authorization](08-security-architecture.md#83-authorization-authz-rbac--abac-hybrid)
model — most are roles, refined by ABAC context (e.g., "manager *of these people*").

---

### Employee (the universal persona)
- **Goals:** Do their actual job with minimal friction; find information; get requests
  handled; know what's expected of them.
- **Pain points today:** Logs into 10+ tools; can't find the right doc/person/policy;
  requests disappear into email; no single place that knows their context.
- **Platform usage:** Work OS (tasks), Knowledge OS (docs/search), Meeting OS (chat/meetings),
  People OS self-service (leave, profile, goals), Service OS (raise requests). **AI:** the
  universal assistant answers "how do I…", drafts, summarizes, and handles routine requests.

### Manager
- **Goals:** Keep the team unblocked and aligned; approve things quickly; understand
  performance and workload; make good people decisions.
- **Pain points today:** Approvals scattered across tools; no unified view of the team's work,
  goals, leave, and morale; status-chasing eats their week.
- **Platform usage:** Work OS (portfolio/team boards, capacity), People OS (team, reviews,
  approvals), Analytics OS (team dashboards), Workflow (approvals inbox). **AI:** team status
  digests, review drafting from work activity, workload/risk alerts, one-tap approvals.

### HR / People Operations
- **Goals:** Hire, onboard, retain, and support employees; keep records accurate and
  compliant; run reviews and comp.
- **Pain points today:** HRIS ≠ ATS ≠ payroll ≠ performance tool; manual data re-entry;
  compliance spread across systems; onboarding is a checklist nightmare.
- **Platform usage:** People OS (all of it — the SoR), Workflow (onboarding/approvals),
  Knowledge OS (policies), Admin (user lifecycle), Analytics (headcount/attrition). **AI:**
  candidate screening, JD/offer drafting, onboarding concierge, policy Q&A, attrition signals.

### Recruiter
- **Goals:** Fill roles fast with quality candidates; keep pipelines moving; give a good
  candidate experience.
- **Pain points today:** ATS disconnected from HRIS and calendar; scheduling ping-pong;
  manual screening; poor collaboration with hiring managers.
- **Platform usage:** People OS/ATS (jobs, pipelines, candidates), Calendar/Meeting OS
  (interviews), Knowledge OS (scorecards/templates). **AI:** resume ranking, interview
  scheduling agent, outreach drafting, structured-feedback summaries.

### Engineering / Product / Design (Makers)
- **Goals:** Ship. Plan work, track progress, collaborate on specs and code, minimize process
  overhead.
- **Pain points today:** Jira ≠ docs ≠ chat ≠ repo; context-switching; duplicated status;
  specs disconnected from the work items that implement them.
- **Platform usage:** Work OS (sprints/issues/roadmaps), Knowledge OS (specs/docs), Meeting OS
  (standups/chat), later dev tooling (repo/CI links). **AI:** task creation from a spec,
  triage, standup summaries, "what changed," estimation help.

### Finance / Accounting
- **Goals:** Accurate books; timely invoicing and collections; controlled spend; clean audits;
  reliable forecasts.
- **Pain points today:** Finance system disconnected from CRM (revenue), People (headcount
  cost), and Work (billable time); manual reconciliation; expense chasing; audit prep pain.
- **Platform usage:** Finance OS (invoicing/expenses/procurement/budgets), Workflow
  (approvals), Analytics OS (financial reporting), Audit (compliance). **AI:** invoice/receipt
  extraction, anomaly/fraud detection, cash-flow forecasting, spend analysis — advisory, with
  human approval on postings.

### Sales
- **Goals:** Hit quota; spend time selling not logging; know which deals to work; close faster.
- **Pain points today:** CRM data entry burden; CRM ≠ support ≠ finance ≠ delivery; no true
  360° customer view; forecasting is guesswork.
- **Platform usage:** CRM OS (pipeline/accounts/activities), Meeting OS (calls/notes), Finance
  OS (quotes/invoices), Service OS (customer health), Analytics OS (pipeline). **AI:** lead
  scoring, next-best-action, auto-logged call notes, email drafting, forecast assistance,
  call-prep agent.

### Support Agent
- **Goals:** Resolve tickets fast and well; find answers; avoid repetitive work; keep
  customers happy.
- **Pain points today:** Help desk disconnected from CRM, product, and knowledge base;
  context-hunting per ticket; repetitive replies.
- **Platform usage:** Service OS (tickets/queues/agent workspace), Knowledge OS (KB), CRM OS
  (customer context), Meeting OS (escalation). **AI:** auto-triage, suggested replies,
  KB retrieval, resolution summaries, deflection bot handling routine tickets.

### IT Admin
- **Goals:** Provision/deprovision access safely; keep the org secure and compliant; configure
  apps; support employees.
- **Pain points today:** 20 admin consoles; inconsistent SSO/SCIM; offboarding misses tools;
  no unified audit; security posture is guesswork.
- **Platform usage:** **Admin Platform** (users, roles, SSO/SCIM, billing, audit, config),
  Service OS (IT service desk), Automation OS (provisioning flows). **AI:** natural-language
  admin, access anomaly detection, audit Q&A, license optimization.

### CEO / Executive
- **Goals:** See the whole business truthfully; make decisions with confidence; drive
  alignment; spot risk early.
- **Pain points today:** Data lives in 20 systems and disagrees; "one number" takes analysts a
  week; strategy (goals) disconnected from execution (work) and results (finance).
- **Platform usage:** Analytics OS (cross-domain dashboards), Work OS (OKRs/roadmap), People OS
  (org health), Finance OS (financials). **AI:** natural-language business questions across all
  domains, auto-insights, proactive risk alerts, executive briefings.

### Vendor / Supplier (external, limited)
- **Goals:** Submit invoices/POs, get paid, exchange documents.
- **Pain points today:** Email-and-spreadsheet chaos; no visibility into status.
- **Platform usage:** Scoped external access to Finance OS (invoices/POs) and Documents via a
  vendor portal — strictly limited by ABAC scopes. **AI:** invoice status Q&A, guided
  submission.

### Partner (external, builder/reseller)
- **Goals:** Build on, extend, or resell Calyvora; serve their own clients.
- **Pain points today:** Closed platforms; poor extensibility; no economics for building.
- **Platform usage:** **Marketplace** + **AI Studio** + developer portal (extensions, agents,
  templates), scoped to what customers grant. **AI:** AI-assisted extension/agent building.

### Customer (external end-user of a Calyvora tenant)
- **Goals:** Get support, access shared resources, self-serve — from the companies that run on
  Calyvora.
- **Pain points today:** Clunky, disconnected customer portals.
- **Platform usage:** Service OS customer portal (tickets/KB), CRM-driven customer portal,
  scoped document sharing — always tenant-scoped and permission-bounded. **AI:** self-service
  deflection agent, guided help.

---

## 10.1 Persona → app coverage matrix

| Persona | People | Work | Know | CRM | Fin | Svc | Meet | Ana | Auto | Admin |
|---------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Employee | ● | ● | ● |  |  | ● | ● |  |  |  |
| Manager | ● | ● | ● |  | ○ |  | ● | ● | ○ |  |
| HR | ● | ○ | ● |  | ○ | ○ |  | ● | ○ | ○ |
| Recruiter | ● |  | ○ |  |  |  | ● |  |  |  |
| Maker (Eng/Prod) | ○ | ● | ● |  |  | ○ | ● | ○ | ○ |  |
| Finance | ○ | ○ |  | ○ | ● | ○ |  | ● | ○ |  |
| Sales |  | ○ | ○ | ● | ○ | ○ | ● | ● | ○ |  |
| Support | ○ |  | ● | ● |  | ● | ○ | ○ | ○ |  |
| IT Admin | ○ |  |  |  |  | ● |  | ○ | ● | ● |
| CEO/Exec | ● | ● | ○ | ● | ● | ○ |  | ● |  |  |
| Vendor |  |  | ○ |  | ● |  |  |  |  |  |
| Partner |  |  |  |  |  |  |  |  | ○ | ○ |
| Customer |  |  | ○ | ○ |  | ● |  |  |  |  |

● primary · ○ secondary/occasional. This matrix validates the app decomposition: no persona
needs a tool we don't have, and most personas span multiple apps — which is exactly the
cross-app value the platform delivers and competitors can't.
