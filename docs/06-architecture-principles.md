# 06 · Architecture Principles

This document states the platform's technical decisions and, for each, the *why* and the
rejected alternatives. These are binding; changing one requires an ADR.

## 6.1 Topology: Modular Monolith → selective services

**Decision:** Build each OS-app and the Foundation as a **Modular Monolith** organized into
strict bounded contexts. Extract a module into its own deployable service **only** when a
specific, measured pressure demands it (independent scaling, isolation, team autonomy, or a
different runtime). Deploy everything on Kubernetes.

**Why (and why not the alternatives):**

| Option | Verdict | Reasoning |
|--------|---------|-----------|
| Single monolith (no module boundaries) | ✗ | Fast at first, but becomes an unmaintainable ball of mud; can't scale hot paths; kills team autonomy at our target size. |
| **Modular monolith** | ✓ **Default** | Enforced internal boundaries (separate schemas, module APIs, events) give us most benefits of microservices — clear ownership, testability, future extractability — with a fraction of the operational cost. In-process calls are simple and fast; there's no distributed-systems tax until we choose to pay it. |
| Microservices from day one | ✗ (early) | The classic startup killer: you pay for network failures, distributed transactions, deployment orchestration, and observability complexity *before* you have the load, team size, or domain clarity to justify it. Premature decomposition freezes wrong boundaries. |

The modular monolith is a **strategy, not a compromise**: because modules already
communicate via defined interfaces and events, extracting one to a service later is a
deployment change, not a rewrite. We get the option to scale out without prepaying for it.
The AI Platform, Search, and the Event Backbone are likely the *first* services to run
separately because they have genuinely different scaling and runtime profiles.

**Enforcing module boundaries inside the monolith:** separate database schemas per context
(no cross-schema joins), module-level public interfaces, dependency rules checked in CI
(a module may not import another module's internals), and events for cross-context reactions.

> The language/runtime each service is written in is a separate decision — see
> [§6.14 Runtime & languages](#614-runtime--languages).

## 6.2 Service communication

- **Synchronous, in-process** within a monolith: direct module-API calls. Simple, fast,
  transactional.
- **Synchronous, cross-service** (once extracted): gRPC for internal service-to-service
  (typed, efficient, streaming), REST/GraphQL only at the edge. Every call is authenticated
  (mTLS) and authorized.
- **Asynchronous, cross-context/cross-app**: **domain events** over the Event Backbone.
  This is the *default and preferred* integration path (see §6.5). Prefer async; reach for
  synchronous cross-service calls only when a request genuinely needs an immediate answer.

**Rule:** No distributed transactions across services. Use the **Saga pattern** with
compensating actions and the outbox for eventual consistency. Two-phase commit across
services is prohibited — it doesn't scale and it couples availability.

## 6.3 API Gateway & edge

**Decision:** A single **API Gateway** is the front door for all external traffic (web,
mobile, partners, public API). It handles TLS termination, authentication, rate limiting,
request routing, WAF, and request/response logging. Behind it, **BFF** layers tailor APIs to
clients.

**Why:** Cross-cutting concerns (auth, rate limits, throttling, observability, versioning)
belong in one enforced place, not scattered across services. It also gives us one point to
apply Zero-Trust policy and to shield internal topology from clients.

## 6.4 GraphQL vs REST

**Decision — use both, deliberately:**

| Surface | Protocol | Why |
|---------|----------|-----|
| First-party web & mobile apps | **GraphQL (BFF)** | Clients aggregate data from many domains in one round-trip; avoids over/under-fetching; strongly-typed schema is great DX and drives codegen. Ideal for a UI that stitches People + Work + CRM data. |
| Public & partner API | **REST + OpenAPI** | Ubiquitous, cacheable, simple to consume, stable versioning, first-class in every language and every integration tool. Enterprises and iPaaS expect REST. |
| Internal service-to-service | **gRPC** | Performance, typing, streaming, code generation. |
| Real-time (chat, presence, live docs, notifications) | **WebSockets / SSE** | Push semantics GraphQL/REST can't serve well. GraphQL subscriptions over WS where it fits. |

We reject "GraphQL for everything" (bad fit for public/partner integrations and caching) and
"REST for everything" (painful for rich, multi-domain first-party UIs). Match the protocol
to the consumer.

## 6.5 Event-Driven Architecture

**Decision:** Domain events on **Apache Kafka** are the backbone of cross-context and
cross-app integration.

- **Envelope:** every event uses **CloudEvents** (id, type, source, subject, tenant, time,
  data, schema version). Uniform envelope = uniform tooling, routing, and audit.
- **Schema registry:** all event payloads have registered, versioned schemas (Avro/Protobuf/
  JSON-Schema). Backward-compatible evolution is enforced; breaking changes require a new
  event version. This prevents the "someone changed a field and broke five consumers" failure.
- **Transactional Outbox:** state changes and their events are written in one local
  transaction to an outbox table; a relay publishes to Kafka. Guarantees no lost events and
  no events without the state change (exactly the integrity the whole platform depends on).
- **Idempotent consumers:** every consumer is idempotent (dedupe by event id) because
  delivery is at-least-once.
- **Event taxonomy:** events are past-tense facts (`InvoicePaid`), owned by the producing
  context, and treated as a public contract.

**Why Kafka** (vs RabbitMQ/SQS/NATS): we need durable, replayable, high-throughput, ordered
(per-key) event streams that multiple independent consumers (search, analytics, AI
projections, automations, audit) read at their own pace. Kafka's log-retention + consumer
groups are purpose-built for this. RabbitMQ is a great queue but a poor event log; managed
cloud queues create lock-in we're avoiding. *Fallback:* Redpanda (Kafka-API-compatible) if
we want lower operational overhead — same contract, swappable.

## 6.6 Caching (Redis)

**Decision:** **Redis** for caching, ephemeral state, rate-limit counters, session/token
data, distributed locks, and lightweight pub/sub for presence.

**Rules:** cache keys are **always tenant-scoped** (isolation over hit-rate, per the
[Priority Order](02-product-philosophy.md#216-priority-order-tie-breaker)); caches are a
performance optimization, never a source of truth; explicit TTLs and invalidation on the
owning event. **Why Redis:** ubiquitous, fast, mature, does many jobs (cache, locks,
counters, streams) well enough to avoid adding more infra (boring-technology principle).

## 6.7 Search (OpenSearch/Elasticsearch)

**Decision:** **OpenSearch** as the search and text-analytics engine, fed by domain events
(CQRS read model). Provides universal cross-app search and per-app search with one relevance
and permission-filtering model. Results are always tenant-scoped and permission-filtered at
query time. **Why OpenSearch over Elasticsearch:** open-source licensing avoids the vendor
lock-in and license risk of Elastic's newer terms — consistent with our open-data-layer
stance. Semantic/vector search is handled by the AI Platform's vector store (§ [09](09-ai-strategy.md)),
with hybrid (keyword + vector) ranking.

## 6.8 Storage

| Need | Choice | Why |
|------|--------|-----|
| Transactional system-of-record | **PostgreSQL** | Rock-solid, relational integrity, JSONB for flexibility, row-level security for tenancy, huge ecosystem, and `pgvector` lets small/mid tenants do vector search without extra infra. The default for nearly everything. |
| Blob/object storage | **S3-compatible object store** | Cheap, durable, scalable for files, images, recordings, exports; CDN-frontable. |
| Cache/ephemeral | **Redis** | §6.6 |
| Search index | **OpenSearch** | §6.7 |
| Event log | **Kafka** | §6.5 |
| Vectors | **pgvector → dedicated vector store at scale** | Start simple; graduate hot tenants. See [09](09-ai-strategy.md). |
| Analytics/warehouse | **Columnar store (e.g., ClickHouse) + object-store lakehouse** | Cross-app BI at scale needs columnar; kept separate from OLTP. |

We deliberately **avoid a zoo of specialized databases**. Postgres + object store + Kafka +
Redis + OpenSearch covers the vast majority of needs. New datastores require an ADR
justifying why the existing five can't do the job.

## 6.9 Security (summary; full detail in [08](08-security-architecture.md))

Zero Trust throughout; mTLS between services; central AuthN/AuthZ; encryption in transit and
at rest with per-tenant keys; secrets in a vault; least privilege; everything audited.
Security is a design input at every review gate, not a final check.

## 6.10 Observability

**Decision:** **OpenTelemetry** as the single instrumentation standard for traces, metrics,
and logs across every service, with tenant and principal context propagated on every span.

- **Traces:** distributed tracing across gateway → BFF → services → datastores (and across
  async event flows via trace context in the event envelope).
- **Metrics:** RED (Rate/Errors/Duration) per endpoint, USE for resources, and business
  SLOs; Prometheus + Grafana.
- **Logs:** structured, tenant-tagged, centralized; correlated to traces by id.
- **SLOs & alerting:** every critical path has an SLO and an alert; error budgets govern
  release pace.

**Why OTel:** vendor-neutral, portable (fits cloud-agnostic), and unifies the three pillars
so on-call engineers debug from one correlated view. You cannot run a multi-tenant fleet you
can't see.

## 6.11 CI/CD & delivery

- **Trunk-based development** with short-lived branches; small, frequent merges to `main`.
- **Every merge to `main` is potentially shippable**; releases are decoupled from deploys via
  feature flags.
- **Pipeline gates:** build → unit/integration/contract tests → SAST/DAST/dependency &
  license scan → schema/migration check → performance smoke on critical paths → sign-off.
- **GitOps:** the deployed state of every environment is declared in git; a controller
  reconciles. No manual `kubectl` in production.
- **Progressive delivery:** canary / percentage rollout by tenant cohort with automated
  rollback on SLO regression.

**Why trunk-based + GitOps + flags:** it's the combination that lets a large org ship many
times a day *safely* — decoupling "merged," "deployed," and "released" is what makes speed
and stability compatible.

## 6.12 Versioning

- **APIs:** semantic, explicit versioning. Public REST APIs are versioned in the path
  (`/v1/`), never broken silently; deprecations are announced with a support window. GraphQL
  evolves additively with field deprecation.
- **Events:** schema-registry-enforced backward compatibility; new incompatible shape = new
  event version, old consumers keep working.
- **Data:** all schema changes via reviewed, reversible migrations; expand-then-contract for
  zero-downtime.

**Why strict versioning:** partners, the Marketplace, automations, and AI agents all depend
on our contracts. Breaking them silently destroys trust in the platform. Contracts are sacred.

## 6.13 Deployment & runtime

- **Kubernetes** everywhere; services are stateless, twelve-factor, horizontally scalable,
  with health probes, resource limits, and graceful shutdown.
- **Service mesh** (e.g., Linkerd/Istio) for mTLS, traffic policy, and telemetry.
- **Cloud-agnostic:** managed Kubernetes on a default cloud, but no hard dependency on any
  single cloud's proprietary services on the critical path — enabling multi-region,
  multi-cloud, and (for regulated enterprise) customer-cluster deployment. Cloud-specific
  managed services may be used where they're clearly superior *and* have an open fallback,
  documented per ADR.
- **Infrastructure as Code** for everything (Terraform/Pulumi); no click-ops.

**Why cloud-agnostic Kubernetes:** it's the price of admission for the enterprise/regulated
segment (data residency, on-prem/private-cloud options) and it protects our margins and
negotiating position by avoiding deep lock-in. The trade-off — we manage more ourselves than
if we went all-in on one cloud's PaaS — is worth it for a platform of this ambition, and is
mitigated by the boring-technology principle keeping the managed surface small.

## 6.14 Runtime & languages

**Decision:** A **polyglot-but-disciplined** stack. Each part of the platform uses the
language that best fits its job, but the number of languages is kept deliberately small so the
platform stays coherent, hireable-for, and operable.

| Layer | Primary language / framework | Why |
|-------|------------------------------|-----|
| **Transactional core & OS-apps (the backend)** | **Java (LTS) + Spring Boot** | The reference default. See rationale below. |
| **Edge / BFF & real-time** | **TypeScript / Node.js** | Shares types with the web frontend, excellent for GraphQL BFFs, WebSockets/SSE, and I/O-bound aggregation. |
| **AI/ML pipelines & data science** | **Python** | The lingua franca of ML tooling, embeddings, evals, and data work; used inside the AI Platform's pipelines. |
| **AI orchestration / agent runtime** | **TypeScript** *or* **Python** | Whichever best fits the SDKs in play; sits behind the AI Platform's interfaces, so the choice is contained. |

**Why Java + Spring Boot is the backend default:**

- **Enterprise pedigree and correctness.** Java is the most-proven language for large,
  long-lived, transactional enterprise systems — exactly what Finance OS, People OS, and the
  Foundation are. Strong typing, mature concurrency, and battle-tested reliability fit a
  platform where correctness and data integrity outrank cleverness (our
  [Priority Order](02-product-philosophy.md#216-priority-order-tie-breaker)).
- **The ecosystem we already depend on is Java-first.** Kafka, the whole event-streaming
  stack, Debezium (change-data-capture for the outbox), Elasticsearch/OpenSearch, and most
  of the data-infrastructure world are JVM-native. Building the backend on the JVM means
  first-class libraries for our chosen substrate ([§6.5](#65-event-driven-architecture),
  [§6.7](#67-search-opensearchelasticsearch)) instead of second-class bindings.
- **Spring Boot gives us the "golden path" cheaply.** Spring Boot + Spring Modulith is a
  near-perfect fit for our [modular-monolith](#61-topology) strategy: enforced module
  boundaries, an events mechanism, and an easy path to extract a module into its own service
  later. Spring Security, Spring Data (JPA + Postgres), Spring for Kafka, and
  Micrometer/OpenTelemetry cover auth, persistence, eventing, and observability out of the box.
- **The largest enterprise talent pool.** Java has one of the deepest, most available hiring
  pools in the world — directly de-risking the *hiring risk* in
  [13.6](13-risk-analysis.md#136-hiring--organizational-risks). It is also the stack **you**
  can contribute in, which matters: the team building the platform must be able to build it.
- **Boring, proven technology** — precisely the [principle](02-product-philosophy.md#215-boring-proven-technology-by-default)
  we want for the substrate, so the innovation budget goes to the product and the AI.

**Trade-off vs. .NET (the rejected alternative):** .NET/C# is an equally excellent enterprise
runtime and was a viable default. We choose Java because (a) the surrounding data/event
ecosystem is JVM-native, reducing integration friction, (b) the enterprise + open-source
talent pool is broader and more cloud-agnostic-friendly, aligning with our
[no-lock-in](#613-deployment--runtime) stance, and (c) it matches the team's contribution
strength. This is not a knock on .NET — it's optimizing for ecosystem fit, hireability, and
who is actually building Calyvora. Reversing this decision requires an ADR.

**Discipline rules:** adding a *new* backend language requires an ADR justifying why Java/TS/
Python can't do the job. Internal services still communicate the same way regardless of
language — typed **gRPC** between services and **CloudEvents** on the event backbone
([§6.2](#62-service-communication), [§6.5](#65-event-driven-architecture)) — so a service's
implementation language is an internal detail, never leaked across a boundary.
