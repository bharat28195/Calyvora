# 04 · Complete Product Map

The full hierarchy of the Enterprise OS, with, for every application: its **Mission**, its
**Core modules** (v1 scope), its **Future modules** (later phases), and its **AI
opportunities** (how the AI Platform makes it categorically better). Phasing is summarized
here and detailed in [11 · Roadmap](11-roadmap.md).

## 4.1 The hierarchy

```
Calyvora Enterprise OS
├── Foundation Platform          → see 05 · Shared Platform Services
├── People OS                    (Phase 1)
├── Work OS                      (Phase 1)
├── Knowledge OS                 (Phase 1)
├── CRM OS                       (Phase 2)
├── Service OS                   (Phase 2)
├── Meeting OS                   (Phase 2)
├── Automation OS                (Phase 2)
├── Analytics OS                 (Phase 3)
├── Finance OS                   (Phase 3)
├── AI Studio                    (Phase 3)
├── Marketplace                  (Phase 3)
├── Mobile Platform              (Phase 2, cross-cutting)
└── Admin Platform               (Phase 1, cross-cutting)
```

---

## 4.2 People OS

**Mission:** Be the single, trusted source of truth for the organization's people and their
full lifecycle, and thereby the identity backbone of the entire platform.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| Employee records (HRIS) & profiles | Payroll (with Finance OS) |
| Org structure & directory | Benefits administration |
| Recruiting / ATS (jobs, candidates, pipelines) | Compensation planning |
| Onboarding / offboarding workflows | Learning Management (LMS) |
| Time, attendance & leave | Workforce planning & headcount modeling |
| Performance reviews & goals/OKRs | Contingent/vendor workforce |
| Employee self-service & approvals | Succession & talent management |

**AI opportunities:** resume screening & candidate ranking (bias-audited); JD and offer-letter
drafting; interview scheduling agent; onboarding concierge agent for new hires; attrition-risk
signals; goal/review drafting from work activity; policy Q&A grounded in HR docs.

---

## 4.3 Work OS

**Mission:** Connect strategy to execution — make it effortless to plan, track, and ship
work, with everything linked to the people, knowledge, and customers it serves.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| Projects & workspaces | Portfolio & program management |
| Tasks, issues, sub-tasks | Resource & capacity planning |
| Boards (Kanban), lists, timeline/Gantt | Dev tooling (repo/CI/CD integration, releases) |
| Sprints & backlogs (Agile) | Professional-services automation (PSA) |
| Roadmaps | Test/QA management |
| Goals/OKRs linked to work | Forms & intake |
| Custom fields, views, filters | Time tracking → billing (with Finance OS) |

**AI opportunities:** natural-language project/task creation; auto-triage and assignment;
sprint planning & estimation assistant; daily standup and status summaries from activity;
risk/slippage detection on roadmaps; "what changed since I was away" digests; agentic task
execution for routine items.

---

## 4.4 Knowledge OS

**Mission:** Capture, organize, and surface the organization's collective knowledge, and be
the richest grounding source for the platform's AI.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| Rich collaborative document editor | Structured databases/tables |
| Spaces & wikis | Whiteboards & diagrams |
| Page hierarchy & linking | Forms |
| Comments & mentions | Public knowledge-base / help center publishing |
| Templates | Read-tracking & knowledge analytics |
| Full-text & semantic search | Auto-generated docs from events/meetings |
| Versioning & history | Knowledge agents (domain experts) |

**AI opportunities:** semantic search and Q&A over the entire corpus (RAG); auto-summaries;
draft/expand/rewrite; "ask the docs" agent; automatic linking of related knowledge;
stale-content detection; meeting/notes → structured knowledge.

---

## 4.5 CRM OS

**Mission:** Give revenue teams a single, 360° view of every customer relationship, natively
connected to the work, support, and money behind it.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| Contacts & accounts | Marketing automation & campaigns |
| Leads & lead management | CPQ (configure-price-quote) |
| Opportunities & pipeline | Customer success & health scoring |
| Activities & timeline | Partner relationship management |
| Email integration & tracking | Revenue intelligence & forecasting |
| Quotes (with Documents) | Territory & quota management |
| Pipeline dashboards (with Analytics) | Customer portal |

**AI opportunities:** lead scoring & prioritization; next-best-action; email and follow-up
drafting; call/meeting note summarization into the record; pipeline risk and forecast
assistance; auto-enrichment; churn prediction; a sales-assistant agent that preps for calls.

---

## 4.6 Service OS

**Mission:** Resolve every request — internal or external — faster, with full context and
automated fulfillment.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| Ticketing & queues | Asset & configuration management (CMDB) |
| Help desk (customer support) | Change & problem management |
| Internal service desk (IT/HR/facilities) | SLA management & escalation |
| Service catalog & request forms | Employee/customer service portal |
| Knowledge-base deflection (with Knowledge OS) | Field service |
| SLAs (basic) & assignment rules | Major-incident command |
| Agent workspace | On-call & alerting integration |

**AI opportunities:** auto-triage, categorization & routing; agent-assist (suggested
replies, KB retrieval); customer-facing deflection agent; resolution summarization; sentiment
& escalation detection; auto-fulfillment of common requests via Automation OS.

---

## 4.7 Finance OS

**Mission:** Turn the organization's operational activity into accurate, auditable financial
truth, with money flowing automatically from the apps that generate it.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| Invoicing & billing | General ledger & double-entry accounting |
| Expenses & reimbursements | Revenue recognition |
| Procurement & purchase orders | Multi-entity/multi-currency consolidation |
| Budgets & cost centers | Tax management |
| Approvals (with Workflow) | Treasury & cash management |
| Vendor management | Payroll (with People OS) |
| Financial reporting (with Analytics) | Fixed assets |

**AI opportunities:** invoice/receipt data extraction (OCR + LLM); expense policy checking &
anomaly/fraud detection; cash-flow forecasting; spend analysis; PO-to-invoice matching;
narrative financial summaries for leadership; audit-anomaly flagging. *All AI here is
advisory with human approval on postings — see [09](09-ai-strategy.md).*

---

## 4.8 Meeting OS

**Mission:** Make synchronous collaboration productive and *persistent* — every meeting and
conversation feeds the organization's memory and drives action.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| Team chat & channels | Native video/audio meetings |
| Direct & group messaging | Whiteboarding |
| Scheduling (with Calendar) | Voice/meeting agents that attend |
| Meeting notes & action items | Contact-center / telephony |
| Transcription & summaries (with AI) | Live co-editing surfaces |
| Video (via partner integration in v1) | Webinars & large events |
| Threaded conversations & search | Presence & status automation |

**AI opportunities:** live transcription & summarization; action-item extraction → Work OS
tasks; "catch me up" on channels/threads; meeting prep briefs; decision logging into
Knowledge OS; a meeting agent that answers questions from prior discussions.

---

## 4.9 Analytics OS

**Mission:** Make the connectedness of the platform legible — let anyone ask and answer
questions across every domain, governed by the same permissions as the source data.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| Dashboards & reports | Predictive/ML analytics |
| Cross-app metrics library | Governed semantic layer |
| Chart & visualization builder | Data-app builder |
| Data explorer / ad-hoc query | External data connectors & warehouse sync |
| Scheduled reports & subscriptions | Embedded analytics SDK (for apps/Marketplace) |
| Row/column-level security | Data catalog & lineage |
| Natural-language querying (with AI) | Goal/metric tracking & alerting |

**AI opportunities:** natural-language-to-query ("show revenue per head by team, YoY");
automated insight & anomaly narration; dashboard generation from a prompt; proactive alerts
on metric changes; forecast generation.

---

## 4.10 Automation OS

**Mission:** Let anyone connect and automate the whole platform — turning cross-app processes
from manual toil into reliable, governed, AI-augmented flows.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| Visual flow builder (triggers/conditions/actions) | AI-authored flows from natural language |
| Event triggers (native event backbone) | Agentic steps (an agent as a flow node) |
| App action library (all OS-apps) | Process mining from event history |
| Scheduling & delays | Human-in-the-loop approval steps |
| Branching & error handling | Automation template marketplace |
| External connectors (with Integration Platform) | Simulation & dry-run |
| Run history & observability | RPA-style UI automation for legacy systems |

**AI opportunities:** describe-a-process → generated flow; classify/extract/decide steps
powered by the model gateway; suggest automations from observed repetitive behavior;
self-healing flows that adapt to schema changes.

---

## 4.11 AI Studio

**Mission:** Turn every customer and partner into an AI builder on top of the shared,
governed AI Platform — without writing infrastructure.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| Agent builder (tools, instructions, memory) | Multi-agent orchestration designer |
| Prompt library & versioning | Fine-tuning / adapter management |
| Knowledge-base builder (RAG sources) | Agent marketplace publishing |
| Model selection & routing config | Evaluation & A/B testing harness |
| AI action/tool registry (permissioned) | Cost & usage governance dashboards |
| Test/sandbox & preview | Custom model bring-your-own (BYO) |
| Guardrails & policy config | Voice/multimodal agent builder |

**AI opportunities:** *AI Studio is itself the AI opportunity* — it exposes the platform's AI
primitives (RAG, agents, KG, tools, memory) as building blocks. It also uses AI to help build
AI: prompt optimization, auto-generated tool descriptions, eval generation. See [09](09-ai-strategy.md).

---

## 4.12 Analytics of the platform aside — Mobile Platform

**Mission:** Deliver every OS-app to phones as a first-class, offline-tolerant experience —
not a stripped-down afterthought.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| Cross-platform app shell (iOS/Android) | Per-app deep native experiences |
| Auth, biometrics & device trust | Wearable & tablet-optimized surfaces |
| Push notifications | Offline-first for more modules |
| Offline cache + action queue (selected modules) | Mobile-specific capture (scan, voice, photo) |
| Universal search & inbox | On-device AI features |
| App-switcher across OS-apps | Field-service & frontline-worker modes |

**AI opportunities:** voice-first assistant on mobile; camera capture → structured data
(receipts, business cards, documents); location/context-aware suggestions; on-the-go
approvals via the assistant.

---

## 4.13 Marketplace

**Mission:** Make Calyvora a true platform economy — third parties extend it, customers
install with confidence, and everyone shares in the value.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| App/extension listing & discovery | Paid apps & revenue sharing |
| Install/uninstall with scoped permissions | Certified/verified partner program |
| Extension SDK & sandbox runtime | Private/enterprise marketplaces |
| Automation & AI-agent templates | Ratings, reviews & analytics for developers |
| Developer portal & docs | Vertical solution bundles |
| Permission/consent review flow | OAuth app directory for external integrations |
| Security review pipeline | Managed-service / partner listings |

**AI opportunities:** AI-assisted extension development; agent marketplace (install a
pre-built agent); AI-driven discovery ("find me an app that does X"); automated security &
policy review of submissions.

---

## 4.14 Admin Platform

**Mission:** Give tenant administrators one control plane to govern identity, security,
billing, and configuration across every app — and give *us* the operational console to run
the fleet.

| Core modules (v1) | Future modules |
|-------------------|----------------|
| User & lifecycle management | Delegated & scoped admin |
| Org & directory management | Access reviews & certification (compliance) |
| Roles & permissions (RBAC/ABAC) console | Advanced security posture & DLP |
| SSO / SCIM / IdP configuration | Data residency & retention self-service |
| Billing, subscriptions & usage | Sandbox/test-tenant management |
| Audit log explorer | Config-as-code / API-driven admin |
| Per-app configuration & feature flags | Cost & license optimization insights |

**AI opportunities:** natural-language admin ("give the finance team view-only access to Q3
budgets"); anomaly detection on access patterns; policy recommendation; audit-log Q&A;
license-optimization suggestions.

---

## 4.15 Packaging (commercial view)

Architecturally every app is independent; commercially we bundle for adoption. Indicative
suites (subject to GTM iteration):

- **People Suite** — People OS (+ Knowledge for policies)
- **Work Suite** — Work OS + Knowledge OS (+ Meeting)
- **Revenue Suite** — CRM OS + Service OS (+ Meeting)
- **Operations Suite** — Finance OS + Automation OS + Analytics OS
- **Enterprise OS (full)** — everything + AI Studio + Marketplace + dedicated isolation tier

Foundation, Admin, and the AI Platform are never sold separately — they are the OS itself.
