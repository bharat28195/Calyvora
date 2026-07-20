# Calyvora Enterprise OS — Architectural Constitution

> **Status:** Founding document set (v0.1) · **Owner:** Office of the CTO · **Audience:** All engineering, product, design, security, and leadership.

This `/docs` tree is the **constitution** of Calyvora: the single, authoritative source of
architectural and product truth. It is written to be detailed enough that a team of ~100
engineers can begin implementation immediately, and opinionated enough that we do not
re-litigate settled decisions in every standup.

## What Calyvora is

Calyvora is an **AI-Native Enterprise Operating System (Enterprise OS)** — one composable
ecosystem that replaces the 20–30 disconnected tools an organization runs today (HR, work
management, knowledge, CRM, finance, service desk, meetings, analytics, automation) with a
set of applications that each work independently *and* integrate natively through one
platform, one identity, one data fabric, and one AI layer.

## How to read this

Read in order the first time. After that, jump by concern.

| # | Document | Read if you care about… |
|---|----------|--------------------------|
| 00 | [Glossary](00-glossary.md) | Precise meaning of tenant, org, OS-app, module, agent, etc. |
| 01 | [Executive Vision](01-executive-vision.md) | Why we exist, who we serve, the 10-year bet |
| 02 | [Product Philosophy](02-product-philosophy.md) | The engineering principles and how they're enforced |
| 03 | [Enterprise OS Overview](03-enterprise-os-overview.md) | The full ecosystem, app by app |
| 04 | [Complete Product Map](04-product-map.md) | The hierarchy, modules, and AI opportunities per app |
| 05 | [Shared Platform Services](05-shared-platform-services.md) | The reusable services every app builds on |
| 06 | [Architecture Principles](06-architecture-principles.md) | Topology, APIs, events, caching, delivery |
| 07 | [Multi-Tenant Strategy](07-multi-tenant-strategy.md) | Isolation tiers, data residency, scaling |
| 08 | [Security Architecture](08-security-architecture.md) | AuthN/Z, encryption, compliance, Zero-Trust |
| 09 | [AI Strategy](09-ai-strategy.md) | The AI platform, agents, RAG, knowledge graph |
| 10 | [User Personas](10-user-personas.md) | Who uses the product and what they need |
| 11 | [Future Roadmap](11-roadmap.md) | Phases 1–5 and what ships in each |
| 12 | [Product Differentiators](12-differentiators.md) | Competitor weaknesses and how we beat them |
| 13 | [Risk Analysis](13-risk-analysis.md) | What can kill us and how we mitigate it |
| 14 | [Engineering Standards](14-engineering-standards.md) | How we name, branch, test, review, and ship |
| 15 | [Final Recommendation](15-final-recommendation.md) | The CTO's build-order and what to avoid |

## How this constitution is governed

- **This is code.** The docs live in the same monorepo as the platform, are reviewed via
  pull request, and change only with review from the owning domain lead + the Office of the CTO.
- **Amendments over rewrites.** Material changes are proposed as an **ADR** (Architecture
  Decision Record) under `docs/adr/` (introduced in Phase 1) that references and supersedes
  the relevant section. Nothing here is deleted silently; decisions are superseded on the record.
- **Opinionated by design.** Where a section names a technology, it also states *why* and its
  documented fallback. Disagreement is welcome — as an ADR, not as drift.
- **Consistency is enforced.** Decisions in 06–09 are the source; 03–05 and 11 must reflect
  them. A change to a foundational decision requires updating dependents in the same PR.

## Non-negotiables (the short version)

1. **One identity, one permission model, one audit trail** across every application.
2. **AI is a platform primitive**, not a feature bolted onto each app.
3. **Multi-tenant and secure by construction** — never retrofitted.
4. **Every capability is an API and an event** before it is a screen.
5. **We do not take shortcuts that mortgage the platform** to ship a single app faster.
