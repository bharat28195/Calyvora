# 00 · Glossary

> Precise, shared vocabulary. Ambiguous words cause architectural drift. When these terms
> appear in any Calyvora document, code, or API, they carry exactly the meaning below.

## Platform structure

| Term | Definition |
|------|------------|
| **Enterprise OS / Platform** | The whole Calyvora product: the Foundation Platform plus all OS-apps, sharing one identity, data fabric, and AI layer. |
| **Foundation Platform** | The always-on substrate every app depends on: identity, tenancy, authorization, data fabric, eventing, AI platform, admin. Not sold separately; it *is* the operating system. |
| **OS-App** (or **App**) | A first-party business application (e.g., People OS, Work OS). Independently useful, independently deployable, but built on the Foundation Platform. |
| **Module** | A bounded feature area inside an OS-app (e.g., "Payroll" inside Finance OS, "Sprints" inside Work OS). The unit of ownership by a squad. |
| **Bounded Context** | (DDD) A boundary within which a domain model is consistent and terms are unambiguous. Usually 1:1 with a module or small cluster of modules. The unit we may later extract into its own service. |
| **Package / Suite** | A commercial bundle of OS-apps sold together (e.g., "People Suite"). A packaging concept, not an architectural one. |

## Tenancy & identity

| Term | Definition |
|------|------------|
| **Tenant** | The top-level isolation boundary = one paying customer organization. All data is partitioned by `tenant_id`. Cross-tenant access is impossible by construction, not by convention. |
| **Organization (Org)** | A structural unit *inside* a tenant (company → business unit → department → team). A tenant has exactly one org tree. Do not confuse with Tenant. |
| **Workspace** | A collaboration container inside a tenant (e.g., a Work OS project space or a Knowledge OS space). Scopes membership and permissions below the org level. |
| **Principal** | Any authenticated actor: a **User**, a **Service Account**, or an **AI Agent**. All three are first-class and auditable. |
| **User** | A human principal with a lifecycle (invited → active → suspended → deprovisioned) governed by People OS + Identity. |
| **Service Account** | A non-human principal used by integrations/automation. Holds scoped credentials, never a password. |
| **Identity Provider (IdP)** | The system that authenticates users. Calyvora is an OIDC **Relying Party** to customer IdPs (Entra ID, Google, Okta) and can also be an IdP for downstream apps. |

## Authorization

| Term | Definition |
|------|------------|
| **RBAC** | Role-Based Access Control: permissions granted via roles assigned to principals. |
| **ABAC** | Attribute-Based Access Control: permissions decided by attributes of principal, resource, action, and context (e.g., "manager can view reports of *their* reports during business hours"). |
| **Permission** | An atomic allow decision, expressed as `action` on `resource-type` within a `scope` (e.g., `payroll.run:approve` on `org:123`). |
| **Policy** | A rule (RBAC binding or ABAC expression) evaluated by the central **Authorization Service** to yield allow/deny. |
| **Scope** | The boundary a permission applies to: tenant, org node, workspace, or resource instance. |

## Data & events

| Term | Definition |
|------|------------|
| **System of Record (SoR)** | The single service that authoritatively owns a piece of data. Every entity has exactly one SoR. |
| **Data Fabric** | The set of shared data services (storage, search, files, versioning) and the contracts that let apps share data without direct database access. |
| **Domain Event** | An immutable, past-tense fact published to the event backbone (e.g., `EmployeeHired`, `InvoicePaid`). The primary way apps integrate. |
| **Outbox** | A transactional table used to publish events atomically with the state change that produced them (no lost or phantom events). |
| **CloudEvents** | The standard envelope format for all Calyvora events (type, source, subject, tenant, id, time, data). |
| **Projection / Read Model** | A denormalized view built by consuming events, optimized for a query pattern (e.g., search index, analytics table). |

## AI

| Term | Definition |
|------|------------|
| **AI Platform** | The shared service providing model access, RAG, agents, memory, prompt management, and AI governance to every app. |
| **Agent** | An AI principal that can perceive (read context), reason (LLM), and act (call tools/APIs) on behalf of a user or the org, under scoped permissions and full audit. |
| **Tool** | A permissioned capability an agent may invoke — almost always a Calyvora API guarded by the same Authorization Service that guards humans. |
| **RAG** | Retrieval-Augmented Generation: grounding LLM output in retrieved tenant data (documents, records, knowledge graph). |
| **Knowledge Graph (KG)** | A tenant-scoped graph of entities (people, projects, customers, documents) and their relationships, used for grounding and reasoning. |
| **Embedding / Vector** | A numeric representation of content used for semantic search; stored in the vector store, always tagged with `tenant_id` and ACL metadata. |
| **Model Gateway** | The single egress point for all LLM calls: handles routing, provider abstraction, cost/rate control, prompt-injection defense, and AI audit. |

## Delivery & operations

| Term | Definition |
|------|------------|
| **ADR** | Architecture Decision Record: a short, numbered, immutable document capturing one decision, its context, and consequences. The mechanism for amending this constitution. |
| **BFF** | Backend-for-Frontend: a GraphQL layer tailored to a client (web, mobile) that aggregates domain APIs. |
| **Progressive Delivery** | Shipping behind feature flags with canary/percentage rollout and automated rollback. |
| **SLO / SLA** | Service Level Objective (internal target) / Agreement (external contract). |
