# 13 · Risk Analysis

A constitution that only describes success is propaganda. This document names what can kill or
cripple Calyvora and how we mitigate each. Risks are rated **Likelihood × Impact** (L/M/H) and
paired with concrete mitigations tied to decisions elsewhere in these docs.

## 13.1 Technical risks

| Risk | L | I | Mitigation |
|------|---|---|-----------|
| **Foundation over-engineering / analysis paralysis** — building a perfect platform with no shipped product | M | H | Build the Foundation *with* Phase-1 apps as its first consumers; ship to design partners early; time-box platform work; YAGNI on speculative capabilities. See [11](11-roadmap.md). |
| **Modular monolith rots into a ball of mud** — boundaries erode | M | H | CI-enforced module boundaries (no cross-context internals), separate schemas, events for cross-context reactions, architecture-fitness tests, regular boundary reviews. See [06](06-architecture-principles.md#61-topology). |
| **Premature microservices** — distributed-systems tax before we can pay it | M | H | Explicit "extract only under measured pressure" rule; ADR required to split a service. |
| **Multi-tenant isolation bug leaks cross-tenant data** | L | **Critical** | Defense-in-depth: ambient tenant context + Postgres RLS + lint rules + automated cross-tenant attack tests in CI + per-tenant keys. Treated as the top correctness invariant. See [07](07-multi-tenant-strategy.md#73-defense-in-depth-for-isolation). |
| **Event backbone becomes a coupling/consistency nightmare** — schema drift, lost/duplicate events | M | H | Schema registry with compat enforcement, transactional outbox, idempotent consumers, event versioning, replayability. See [06](06-architecture-principles.md#65-event-driven-architecture). |
| **AI unreliability** — hallucination, wrong actions, cost blowups | H | H | RAG-first grounding + citations, human-in-the-loop for consequential actions, permissioned tools, guardrails, per-tenant AI budgets, evals on prompts. See [09](09-ai-strategy.md). |
| **Scaling hotspots** (search, AI, analytics, noisy neighbors) | M | H | Horizontal + cell-based scaling, per-tenant quotas/rate limits, async worker pools, load/soak testing of critical paths. See [07](07-multi-tenant-strategy.md#74-noisy-neighbor--fairness). |
| **Data-model mistakes are expensive to reverse** | M | H | Data ownership registry, schema review gate, expand-then-contract migrations, single SoR per entity. See [14](14-engineering-standards.md). |

## 13.2 Business risks

| Risk | L | I | Mitigation |
|------|---|---|-----------|
| **Scope is enormous — spread too thin, everything mediocre** | **H** | **H** | The core strategic risk. Mitigate by building *fewer apps deeply first* (People/Work/Knowledge), each individually best-in-class, before breadth. Ruthless phasing. See [11](11-roadmap.md), [12](12-differentiators.md#124-honest-caveats). |
| **"Jack of all trades, master of none" perception** | H | H | Lead with a wedge where the connected value is undeniable and each app is genuinely competitive; don't claim parity everywhere. |
| **Incumbent distribution, trust, switching inertia** | H | M | Sharp SMB/mid-market wedge, AI-native differentiation, painless land-and-expand, overwhelming connected-whole value; SOC 2/ISO to clear trust bars. |
| **Long time-to-revenue** given platform depth | M | H | Land with People OS + Work OS as standalone, sellable products in Phase 1 — revenue before the full vision. |
| **Competitors add "AI" and "integration" marketing** | H | M | Our advantage is *structural* (unified model), not a feature; keep shipping cross-domain experiences they architecturally can't. |
| **Wrong beachhead / GTM** | M | H | Validate with design partners each phase; instrument adoption; be willing to re-segment. |

## 13.3 Scaling risks

| Risk | L | I | Mitigation |
|------|---|---|-----------|
| **Ops complexity outruns team** as tenants/regions grow | M | H | Automation-first ops (GitOps, IaC), cell-based architecture for bounded blast radius, strong observability (OTel), SRE practices & error budgets. |
| **Cost per tenant erodes margins** (esp. AI + siloed tiers) | M | H | Tiered isolation matching cost to plan, per-tenant AI budgets & model routing, usage metering → pricing, boring-tech efficiency. See [07](07-multi-tenant-strategy.md#72-the-isolation-tiers-hybrid-model). |
| **Single-region/single-cloud outage** | L | H | Multi-region DR, cloud-agnostic K8s, tested failover & restore drills. |
| **Database/connection bottlenecks** in pooled tiers | M | M | Read replicas, connection pooling proxy, shard-by-tenant, async offloading. |

## 13.4 Security risks

| Risk | L | I | Mitigation |
|------|---|---|-----------|
| **Major breach = extinction event** (we hold customers' whole operational data) | L | **Critical** | Security-first + Zero-Trust structural design, per-tenant keys/BYOK, least privilege, encryption everywhere, pen tests + bug bounty, small blast radius by design. See [08](08-security-architecture.md). |
| **AI as a new attack surface** (prompt injection, data exfiltration via agents) | M | H | Untrusted content never trusted as instructions, output/PII filtering, agents bounded by human permissions + audit, sandboxed tools, guardrails at the gateway. See [09](09-ai-strategy.md#98-ai-governance-permissions--audit). |
| **Supply-chain / dependency compromise** | M | H | SBOM, signed artifacts, provenance verification, SCA + secret scanning, locked-down CI/CD. See [08](08-security-architecture.md#88-vulnerability--supply-chain-management). |
| **Marketplace/extension abuse** | M | M | Sandboxed extensions, scoped revocable permissions, automated security review pipeline, rate limits. |
| **Insider threat / over-privileged staff** | L | H | Just-in-time, audited, approved production access; least privilege; comprehensive audit. |

## 13.5 Legal & compliance risks

| Risk | L | I | Mitigation |
|------|---|---|-----------|
| **Data-protection non-compliance** (GDPR, localization laws) | M | H | Data residency by region-homing, DSAR/erasure/portability tooling, DPAs, data classification & retention built into the model from day one. See [07](07-multi-tenant-strategy.md#77-regional--data-residency-deployments), [08](08-security-architecture.md#89-compliance). |
| **Missing certifications block enterprise deals** | H | M | SOC 2 (Phase 2), ISO 27001 (Phase 3), vertical certs as pursued; compliance designed in, not retrofitted. |
| **AI regulation** (EU AI Act-style transparency/oversight) | M | M | AI audit + human oversight + transparency + source citation already default; governance is a selling point. |
| **IP / open-source license contamination** | L | M | License scanning in CI; approved-license policy; OpenSearch (not restrictively-licensed Elastic); legal review of dependencies. |
| **Finance/payroll regulatory correctness** | M | H | Finance OS scheduled late on a mature Foundation; heavy testing/audit; region-specific compliance; human approval on postings. See [03](03-enterprise-os-overview.md#37-finance-os). |
| **Data-processing liability across many domains** | M | M | Clear contracts/DPAs, tenant data ownership, sub-processor management, cyber-insurance. |

## 13.6 Hiring & organizational risks

| Risk | L | I | Mitigation |
|------|---|---|-----------|
| **Can't hire enough senior platform talent** for the ambition | M | H | Build the Foundation with a small elite team first; golden-path templates let a broader team be productive on the platform; strong DX as a hiring/retention asset. See [02](02-product-philosophy.md#23-developer-first). |
| **Conway's Law works against us** — team silos fragment the platform | M | H | Align team boundaries to app/module boundaries (one app ≈ one group); shared platform group owns the Foundation; strong architecture governance (Office of the CTO + ADRs). |
| **Knowledge concentration / bus factor** on core platform | M | M | Documentation-as-code (this constitution), pairing, ADRs, no hero-only systems. |
| **Culture erosion under growth** (shortcut pressure) | M | H | Principles are enforced by tooling/gates, not memos ([02](02-product-philosophy.md)); "no shortcuts that mortgage the platform" is a stated non-negotiable. |

## 13.7 Product risks

| Risk | L | I | Mitigation |
|------|---|---|-----------|
| **Individual apps not competitive** vs focused best-of-breed | H | H | Depth-first roadmap; each Phase-1 app must stand alone; design partners validate competitiveness, not just integration. |
| **Complexity overwhelms users** (a platform this big can feel heavy) | M | H | One consistent UX + AI assistant as the simplifying layer; progressive disclosure; sensible defaults; per-app usability targets. |
| **Migration friction** from incumbent tools blocks adoption | M | H | First-class import/migration tooling per app; SSO/SCIM day one; run-alongside via integrations during transition. |
| **Over-automation erodes trust** (agents doing the wrong thing) | M | M | Human-in-the-loop defaults, configurable autonomy, transparency, easy override/undo. |

## 13.8 The three risks that matter most

If we mitigate nothing else, these three are existential and get founder-level attention:

1. **Cross-tenant data isolation failure** (Security/Technical) — one incident can end the
   company. Mitigated structurally and tested adversarially, continuously.
2. **Spreading too thin → mediocre everything** (Business/Product) — the most *likely* way we
   fail. Mitigated by ruthless depth-first phasing.
3. **A major breach** (Security) — mitigated by the entire Zero-Trust, small-blast-radius
   design being non-negotiable from line one.

Everything else is manageable; these three we must get right.
