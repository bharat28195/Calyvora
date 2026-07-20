# 02 · Product Philosophy

These are the engineering principles that govern every decision at Calyvora. Each is stated
as a rule, justified with *why*, and paired with *how we enforce it* — because a principle
that isn't enforced is a poster, not a philosophy. When two principles conflict, the
**Priority Order** in §2.16 decides.

---

## 2.1 API First

**Rule:** Every capability is designed as a versioned, documented API *before* any UI is
built on it. If it isn't in the API, it doesn't exist.

**Why:** The API is the contract. UIs, mobile apps, partners, automation, and AI agents are
all just clients. Building UI-first produces logic trapped in the frontend, un-automatable
features, and the exact silos we're trying to destroy. API-first is what makes the platform
composable and what makes AI agents possible (an agent's "hands" are our APIs).

**Enforced by:** API design review gate before implementation; OpenAPI/GraphQL schema in the
repo as the source of truth; contract tests; a lint rule that fails builds if a UI route
calls business logic not exposed via a domain API.

## 2.2 AI First (AI-Native)

**Rule:** Every module is designed assuming an AI agent is a first-class user of it. Data is
structured for retrieval and reasoning; actions are exposed as tools; permissions apply to
agents identically to humans.

**Why:** Retrofitting AI onto a product built for humans-clicking-buttons yields a chat
sidebar that can't actually *do* anything. AI-native means the leverage is structural. See
[09 · AI Strategy](09-ai-strategy.md).

**Enforced by:** Every domain API registers its safe, permissioned actions as **AI tools**;
"AI opportunities" is a required field in every module's design doc; entities publish
retrieval-friendly representations to the knowledge graph.

## 2.3 Developer First

**Rule:** Our own developers are our first customers. Internal platform services must be as
well-documented, well-tested, and pleasant to use as anything we'd sell.

**Why:** We will build dozens of apps on the Foundation. If the Foundation is painful, every
app inherits that pain, multiplied. Developer velocity on a shared platform is the single
biggest determinant of how fast we can grow the ecosystem. A great internal developer
experience (DX) is a compounding asset.

**Enforced by:** Golden-path templates and a service scaffolding CLI; every shared service
ships an SDK, sandbox, and docs; a platform NPS survey of internal engineers each quarter;
"time-to-first-successful-call" tracked as a platform SLO.

## 2.4 Composable

**Rule:** Capabilities are small, independent, and combinable. An app is an assembly of
modules; a workflow is an assembly of capabilities; a solution is an assembly of apps.

**Why:** Composability is what lets a customer adopt one app and grow into twenty, and what
lets partners build verticals on top. It's the difference between a platform and a product.

**Enforced by:** Modules expose capabilities through stable interfaces and events, never
through shared mutable database tables; no module reaches into another's internals.

## 2.5 Plugin-Based / Extensible

**Rule:** The platform is extended, not forked. Customers and partners add functionality
through defined extension points (custom fields, workflow steps, UI blocks, event handlers,
agent tools) — never by modifying core.

**Why:** Every serious enterprise needs to customize. If customization means forking or
professional-services spaghetti, the product becomes unmaintainable and unupgradeable (the
classic on-prem ERP death spiral). A clean extension model is what makes the Marketplace and
long-term maintainability possible simultaneously.

**Enforced by:** A stable extension SDK and sandbox; extensions run with scoped permissions;
core has no customer-specific code, ever. See [Marketplace](04-product-map.md#413-marketplace).

## 2.6 Event-Driven

**Rule:** State changes are published as immutable domain events. Cross-module and cross-app
integration happens by producing and consuming events, not by synchronous calls into each
other's databases.

**Why:** Events decouple producers from consumers, enable async scale, give us a natural
audit log and a replayable history, and let new capabilities (search indexes, analytics,
AI projections, automations) subscribe without the producer knowing they exist. This is the
mechanism that makes "integrated by construction" real. See
[06 · Architecture](06-architecture-principles.md#65-event-driven-architecture).

**Enforced by:** The **transactional outbox** pattern is mandatory for state-changing
operations; all events use the CloudEvents envelope and are registered in a schema registry;
direct cross-context DB access is prohibited by code review and schema permissions.

## 2.7 Cloud-Native

**Rule:** Everything is built to run on Kubernetes: stateless services, externalized config,
horizontal scaling, health checks, graceful shutdown, twelve-factor throughout.

**Why:** Cloud-native primitives give us portability (any cloud, or a customer's cluster for
regulated deals), elastic scale, and self-healing operations. It's the foundation of both
our economics and our enterprise reach. See [06](06-architecture-principles.md).

**Enforced by:** A shared service template; no service reads local disk for state; readiness/
liveness probes and resource limits required to deploy; chaos and load testing in CI for
critical paths.

## 2.8 Multi-Tenant

**Rule:** Every service is multi-tenant from the first line of code. `tenant_id` is a
non-optional part of every request context, every row, every event, every cache key, every
log line, and every vector.

**Why:** Multi-tenancy retrofitted is a data breach waiting to happen and a rewrite waiting
to be scheduled. Building it in from the start is non-negotiable. See
[07 · Multi-Tenant Strategy](07-multi-tenant-strategy.md).

**Enforced by:** Tenant context is injected by the platform, not passed by app code; Postgres
row-level security as a backstop; automated tests that attempt cross-tenant access and must
fail; a lint rule forbidding queries without a tenant predicate.

## 2.9 Security First

**Rule:** Security is a design input, not a review at the end. The secure path is the easy
path; the insecure path is impossible or loud.

**Why:** We hold the entire operational data of our customers. A single serious breach is an
extinction event for a company like ours. Security is existential, not a feature. See
[08 · Security Architecture](08-security-architecture.md).

**Enforced by:** Threat modeling in design review; secure-by-default platform primitives
(auth, encryption, secrets are provided, not hand-rolled); SAST/DAST/dependency scanning in
CI; least-privilege everywhere; regular pen tests and a bug bounty.

## 2.10 Zero Trust

**Rule:** No implicit trust based on network location. Every request — including
service-to-service and agent-to-service — is authenticated, authorized, and encrypted.

**Why:** "Inside the firewall = trusted" is how a single foothold becomes a total
compromise. Zero Trust contains blast radius. See
[08](08-security-architecture.md#86-zero-trust--service-identity).

**Enforced by:** mTLS between services (service mesh); every service validates identity and
permission on every call; short-lived credentials; no long-lived shared secrets.

## 2.11 Automation First

**Rule:** If a task is done more than a few times, it should be automatable *by the customer*
through the platform, and *by us* through tooling. Manual toil is a bug.

**Why:** Automation is a headline value proposition to customers (the [Automation OS]) and
an operational necessity for us (we cannot scale to millions of tenants with manual ops).
Automation-first thinking also forces clean, callable APIs (see API First).

**Enforced by:** No manual production changes (GitOps); runbooks become automated jobs;
every app exposes its actions to the Automation OS and to agents.

## 2.12 Offline-Ready

**Rule:** Client applications (web and mobile) are designed to tolerate intermittent
connectivity: read cached data, queue actions, and reconcile on reconnect where the domain
allows it.

**Why:** Real users work on trains, planes, and bad hotel wifi. A platform that's useless
offline is a platform people resent. *Trade-off:* full offline-first (CRDT-level) is
expensive and unnecessary for most modules; we apply it selectively (notes, tasks, mobile
capture) rather than universally. Financial postings and approvals remain online-authoritative.

**Enforced by:** A shared client data layer with caching + an action queue; per-module
classification of "offline-capable" vs "online-only" in its design doc.

## 2.13 Scalable

**Rule:** Design for 100× the current load's *shape*, not 100× its cost. Scale horizontally,
avoid single points of contention, make the expensive thing async.

**Why:** We're building for millions of tenants over a decade. But *premature* scaling is
also a trap — see the Priority Order. We build the modular monolith so we *can* scale
specific hot paths out, without paying microservice overhead everywhere from day one. See
[06](06-architecture-principles.md#61-topology).

**Enforced by:** Load/soak testing of critical paths; capacity reviews; no unbounded queries
(pagination mandatory); async-by-default for anything that can be.

## 2.14 Data as a First-Class Asset

**Rule:** Data has a single System of Record, an explicit schema, an owner, a retention
policy, and a residency. There are no "temporary" tables that become permanent.

**Why:** The organization's data — its knowledge graph — is the durable asset and the fuel
for AI. Sloppy data ownership is how you end up with the fragmentation we're trying to kill.

**Enforced by:** Data ownership registry; schema review; every entity declares its SoR;
data-classification labels drive encryption, retention, and residency automatically.

## 2.15 Boring, Proven Technology (by default)

**Rule:** Prefer mature, well-understood technology for the substrate. Spend our
"innovation budget" on the product and the AI layer, not on exotic infrastructure.

**Why:** We have a finite number of hard problems we can afford to have. Postgres, Kafka,
Redis, and Kubernetes are boring on purpose — they let us pour novelty into what
differentiates us. *Trade-off:* we consciously say no to trendy databases and frameworks
unless they clear a high bar. See [06](06-architecture-principles.md).

**Enforced by:** New foundational technology requires an ADR with an explicit "why not the
boring option" section and sign-off from the Office of the CTO.

## 2.16 Priority Order (tie-breaker)

When principles conflict, resolve in this order:

1. **Security & Tenant Isolation** — never traded away.
2. **Correctness & Data Integrity** — a wrong answer fast is worse than a right answer slow.
3. **User & Developer Experience** — the product must be a joy, or nothing else matters.
4. **Scalability & Performance** — necessary, but not at the cost of the three above, and
   never *prematurely*.
5. **Cost & Velocity** — optimize last, once the above hold.

> Example resolution: "Multi-tenant" (isolation) beats "Scalable" (a shared cache would be
> faster). We tenant-scope the cache key even though it lowers hit rates. Isolation wins.
