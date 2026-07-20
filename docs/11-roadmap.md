# 11 · Future Roadmap

The roadmap is sequenced by one rule: **build the Foundation once, deeply, then let apps
compound on it.** We resist the temptation to sprint into many apps before the platform can
carry them — that's how suites become swamps. Phases are outcome-defined, not date-defined
(indicative durations given for planning only).

## Guiding sequencing logic

1. **Foundation before apps.** Identity, tenancy, authZ, eventing, and the app skeleton must
   exist before *any* app, or every app reinvents them badly. But we build the Foundation
   *with* the first app, not in a vacuum (a platform with no consumer is over-engineered).
2. **People OS first.** It produces the org graph and user identity every other app consumes —
   it's both a great standalone product and the backbone. Natural beachhead.
3. **Highest-value, lowest-regulatory-weight apps early.** Work and Knowledge are broadly
   loved, integrate tightly with People, and carry light compliance burden — ideal for
   proving the platform.
4. **Finance last among core apps.** It carries the heaviest correctness/audit/compliance
   load; build it on a *mature* Foundation.
5. **AI woven through from Phase 1**, deepening each phase — never a bolt-on phase at the end.

---

## Phase 1 — Foundation + first proof (the platform thesis) · ~0–9 months

**Goal:** Prove that "one platform, one identity, one AI" is real and delightful, with a
land-able product for the SMB beachhead.

**Foundation (built to production quality, not prototype):**
- Identity, Authentication (OIDC + basic SSO), Authorization (RBAC + core ABAC), Tenancy/Org
  (Tier-1 pooled + RLS), the **service template / golden path**, Event Backbone (Kafka +
  outbox + schema registry), Storage patterns, Files, Search, Notifications, Comments, Audit,
  Config, Secrets, Feature Flags, Observability (OTel), CI/CD + GitOps.
- **AI Platform v1:** Model Gateway, RAG over documents, `pgvector`, prompt registry, the
  universal assistant, AI audit. Knowledge Graph v1 (from Phase-1 app events).
- **Admin Platform v1** and **Billing v1** (subscriptions/entitlements).

**Apps:**
- **People OS** (HRIS, org/directory, onboarding, leave, self-service, basic performance/goals;
  ATS basics).
- **Work OS** (projects, tasks, boards, sprints, basic roadmaps).
- **Knowledge OS** (collaborative docs, spaces, search, versioning).

**AI in Phase 1:** universal assistant; doc Q&A (RAG); drafting/summarization; onboarding
concierge; task-from-text; standup summaries.

**Exit criteria:** design-partner tenants running real HR + work + docs on one login, with a
working cross-app assistant; SOC 2 program started; core SLOs met under load.

---

## Phase 2 — Widen the surface + collaboration + automation · ~9–20 months

**Goal:** Cover more of the operational surface, add the synchronous/collaboration layer, and
turn on cross-app automation — making the "everything connected" value undeniable. Move into
lower mid-market.

**Foundation additions:** Workflow engine (rich approvals), Messaging, Calendar, Tasks
primitive, Integration Platform (external connectors), Tier-2 isolation (schema-per-tenant),
read-replicas/sharding groundwork, **Mobile Platform v1**. **SOC 2 Type II**; GDPR tooling
(residency, DSAR, retention).

**Apps:**
- **CRM OS** (contacts/accounts/leads/opportunities/pipeline, email integration).
- **Service OS** (ticketing, help desk, internal service desk, KB deflection).
- **Meeting OS** (chat/channels, scheduling, meeting capture; video via partner integration).
- **Automation OS** (visual flow builder on the native event backbone + app action library).

**AI in Phase 2:** lead scoring & next-best-action (CRM); support auto-triage, agent-assist &
deflection (Service); meeting transcription/summaries/action-items (Meeting); AI steps inside
automations; knowledge-graph expansion across the new domains.

**Exit criteria:** customers running sales + support + collaboration + HR + work on one
platform with live cross-app automations; mobile in production; first multi-app expansions
within existing accounts (land-and-expand proven).

---

## Phase 3 — Intelligence, money, and the platform economy · ~20–36 months

**Goal:** Complete the core app set, deliver cross-domain analytics and AI-building, and open
the ecosystem. Enter enterprise.

**Foundation additions:** Analytics data platform (columnar warehouse + governed projections),
Tier-3 isolation (dedicated DB/cluster, BYOK), multi-region/data-residency GA, cell-based
architecture, advanced security posture (DLP, access reviews). **ISO 27001**; enterprise
compliance posture.

**Apps:**
- **Analytics OS** (dashboards, cross-app metrics, NL querying, embedded analytics).
- **Finance OS** (invoicing, expenses, procurement, budgets — built on the mature Foundation;
  GL/accounting begins).
- **AI Studio** (agent/prompt/knowledge-base builders on the shared AI Platform).
- **Marketplace v1** (extensions, automation & agent templates; developer portal; security
  review pipeline).

**AI in Phase 3:** natural-language analytics & auto-insights; finance anomaly detection &
forecasting; customer/partner-built agents (AI Studio); AI-authored automations; multi-agent
orchestration.

**Exit criteria:** enterprise/regulated tenants live on Tier-3 with residency + BYOK; first
third-party Marketplace listings; customers building their own agents; analytics answering
cross-domain questions no competitor can.

---

## Phase 4 — Enterprise depth + ecosystem scale · ~36–54 months

**Goal:** Win enterprise decisively and let the ecosystem carry breadth we don't build
ourselves.

- **App depth:** Finance OS full GL/rev-rec/multi-entity/multi-currency, payroll (People+
  Finance); People OS comp/benefits/LMS; Work OS PSA/resource planning/dev tooling; Service OS
  CMDB/change/problem/SLA; CRM CPQ/marketing/customer success; Meeting OS native video.
- **Platform depth:** private/enterprise marketplaces, config-as-code admin, delegated admin,
  advanced governance, vertical solution accelerators (partners build healthcare/legal/etc.),
  paid Marketplace apps + revenue sharing.
- **AI depth:** agentic workflows as a primary work mode (governed, human-supervised), agent
  marketplace, fine-tuning/BYO-model for enterprise, process mining from event history.

**Exit criteria:** reference enterprise customers running the majority of their operations on
Calyvora; a self-sustaining partner/Marketplace economy; AI agents performing meaningful
routine work under governance.

---

## Phase 5 — The operating system as default · ~54 months+

**Goal:** Calyvora is the assumed substrate for running a company.

- **Ecosystem-led breadth:** third parties build the long tail of vertical and niche apps on
  the platform (the "apps on iOS" model); Calyvora focuses on the Foundation, core apps, AI,
  and the economy.
- **Deep AI-native operations:** a meaningful share of operational decisions are proposed,
  executed, or automated by governed agents with a complete view of the business; humans
  supervise exceptions and strategy.
- **Global, multi-region, multi-cloud/on-prem** deployment footprint serving the most
  demanding regulated and sovereign customers.
- **The knowledge graph as a durable corporate asset** — the organization's institutional
  memory, queryable and actionable.

**Exit criteria:** category leadership; the platform's value is defined as much by what the
ecosystem builds on it as by what we build — the hallmark of a true operating system.

---

## Cross-phase invariants (true in every phase)

- Security, tenant isolation, audit, and AI governance are **never** deferred to "later" —
  they ship with each capability.
- Every new app **reuses** the Foundation; if it can't, that's a Foundation gap to fix, not an
  app to special-case.
- No app is "done" — the phase tables above show *entry*, and each app deepens continuously.
- We validate with **design partners** before GA at every phase; the roadmap serves customer
  pull, not a feature checklist.
