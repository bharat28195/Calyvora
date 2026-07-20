# 05 · Shared Platform Services

The Foundation Platform is the set of services **every OS-app depends on and none
reimplements**. This is the heart of the "integrated by construction" thesis: if two apps
share the same identity, permissions, events, search, and audit, they are integrated whether
their teams ever talk or not.

**Governing rule:** an app may only touch another app's data through (a) that app's public
API, or (b) domain events. Apps never reach into shared *infrastructure* except through the
shared *service* that owns it (no app talks to Kafka, Redis, or the vector store directly —
it talks to the Eventing, Cache, or AI service). This keeps infrastructure swappable and
policy enforceable in one place.

```mermaid
graph LR
  subgraph Core Identity & Access
    A1[Authentication]:::a
    A2[Identity]:::a
    A3[Authorization]:::a
    A4[Organization/Tenants]:::a
  end
  subgraph Data Fabric
    D1[Storage]:::d
    D2[Search]:::d
    D3[Files]:::d
    D4[Documents]:::d
    D5[Versioning]:::d
  end
  subgraph Interaction
    I1[Workflow]:::i
    I2[Notification]:::i
    I3[Messaging]:::i
    I4[Calendar]:::i
    I5[Tasks]:::i
    I6[Comments]:::i
  end
  subgraph Platform Ops
    O1[Audit]:::o
    O2[Logging]:::o
    O3[Monitoring]:::o
    O4[Configuration]:::o
    O5[Secrets]:::o
    O6[Feature Flags]:::o
    O7[Billing]:::o
  end
  subgraph Intelligence & Integration
    N1[AI Platform]:::n
    N2[Analytics]:::n
    N3[Integration Platform]:::n
    N4[Event Backbone]:::n
  end
  classDef a fill:#e3f2fd; classDef d fill:#e8f5e9; classDef i fill:#fff3e0; classDef o fill:#fce4ec; classDef n fill:#ede7f6;
```

## 5.1 Core identity & access

| Service | Why it exists | Notes |
|---------|---------------|-------|
| **Authentication** | One place to prove *who a principal is*. Prevents every app from hand-rolling login (a security disaster). Supports OIDC federation to customer IdPs, MFA, sessions, and token issuance. | See [08](08-security-architecture.md). |
| **Identity** | The registry of principals (users, service accounts, agents) and their credentials/lifecycle. Users originate in People OS but are *projected* into Identity so non-HR apps don't depend on HR. | SCIM provisioning lives here. |
| **Authorization (AuthZ)** | The single decision point for "may this principal do this action on this resource?" Centralizing it is the only way to guarantee consistent, auditable access across apps *and* for AI agents. | RBAC + ABAC hybrid; policy-as-data. |
| **Organization / Tenants** | Owns the tenant boundary and the org tree (company→BU→dept→team). Every other service resolves scope through it. This is the spine of multi-tenancy. | See [07](07-multi-tenant-strategy.md). |

**Why separate Authentication, Identity, and Authorization?** They change for different
reasons and at different rates (authN = protocols, identity = lifecycle, authZ = policy).
Conflating them is how systems end up unable to add SSO without touching permissions. Clean
separation is a security and evolvability decision.

## 5.2 Data fabric

| Service | Why it exists | Notes |
|---------|---------------|-------|
| **Storage** | Shared patterns and tooling for relational persistence (Postgres) with tenancy, encryption, and migrations built in — so no app reinvents secure, multi-tenant data access. | Each app owns its schemas; the *pattern* is shared. |
| **Search** | One search service (OpenSearch) indexing content across apps via events, giving universal search and per-app search with one relevance/permission model. | Permission-filtered results, always tenant-scoped. |
| **Files** | Object storage for blobs (attachments, images, recordings) with virus scanning, encryption, signed URLs, and quotas. Every app has files; none should manage buckets itself. | S3-compatible; CDN-fronted. |
| **Documents** | Structured rich-content service (the collaborative document model) used by Knowledge OS and embedded elsewhere (CRM notes, tickets). Real-time collaboration lives here. | Built on Versioning. |
| **Versioning** | Generic version history / change tracking usable by any entity (documents, records, configs). Enables undo, audit, and "who changed what when." | Powers Knowledge history & config-as-code. |

## 5.3 Interaction services

| Service | Why it exists | Notes |
|---------|---------------|-------|
| **Workflow** | A central engine for approvals and multi-step processes (onboarding, expense approval, deal desk). Every app needs approvals; one engine means one consistent, auditable, configurable model. | Distinct from Automation OS: Workflow = structured/stateful processes; Automation = event-driven glue. Shared engine core. |
| **Notification** | One service for delivering alerts across channels (in-app, email, push, chat, SMS) with user preferences, batching, and localization. Stops every app from building its own email pipeline and preference center. | Consumes events; respects quiet hours & prefs. |
| **Messaging** | The real-time chat/conversation primitive powering Meeting OS and in-context comments/mentions everywhere. | Channels, threads, presence. |
| **Calendar** | Shared scheduling & availability, integrating with external calendars (Google/Microsoft). Used by Meeting OS, People OS (leave), CRM (meetings), Service (change windows). | Free/busy, invites, recurrence. |
| **Tasks** | A generic actionable-item primitive so "action items" from meetings, tickets, and reviews can all become first-class tasks (surfaced in Work OS or a unified inbox) without each app inventing its own. | Work OS is the rich UI; the primitive is shared. |
| **Comments** | Threaded comments, mentions, and reactions attachable to *any* entity in any app, with one notification and permission model. | Universal collaboration layer. |

## 5.4 Platform operations

| Service | Why it exists | Notes |
|---------|---------------|-------|
| **Audit** | An immutable, tamper-evident record of *who did what, when, to what, and why* across every app and every agent. Non-negotiable for enterprise trust, security forensics, and compliance. | Append-only; queryable in Admin. See [08](08-security-architecture.md). |
| **Logging** | Structured, centralized, tenant-tagged application logs. Distinct from Audit (Audit = business/security truth; Logging = operational diagnostics). | OpenTelemetry logs. |
| **Monitoring** | Metrics, traces, alerting, dashboards, and SLO tracking for the fleet. You cannot operate at scale what you cannot see. | OTel + Prometheus/Grafana. |
| **Configuration** | Centralized, versioned, environment- and tenant-aware configuration. Prevents config sprawl and enables config-as-code. | Distinct from Secrets and Feature Flags. |
| **Secrets** | Secure storage and rotation of credentials/keys (external vault). No secret ever lives in code, env files, or config. | See [08](08-security-architecture.md). |
| **Feature Flags** | Runtime control of feature exposure by tenant/cohort/percentage — the backbone of progressive delivery and per-tier packaging. | Powers canary + entitlements. |
| **Billing** | Metering, subscriptions, entitlements, invoicing *for Calyvora itself*, and per-app/per-seat/usage plans. The commercial engine. | Distinct from Finance OS (customers' finances). |

## 5.5 Intelligence & integration

| Service | Why it exists | Notes |
|---------|---------------|-------|
| **Event Backbone** | The Kafka-based nervous system carrying all domain events. This is *the* integration substrate; without it, apps would be point-to-point coupled. | CloudEvents + schema registry + outbox. See [06](06-architecture-principles.md#65-event-driven-architecture). |
| **AI Platform** | The shared model gateway, RAG, knowledge graph, vector store, agent runtime, memory, and AI governance. AI is a platform primitive, so it lives here — not duplicated per app. | The subject of [09](09-ai-strategy.md). |
| **Analytics (platform)** | The shared data pipeline that turns events into governed read models/warehouse data powering Analytics OS and embedded metrics everywhere. | Distinct from Analytics OS (the product/UI). |
| **Integration Platform** | Managed connectors, webhooks, API keys, and iPaaS-style connectivity to *external* systems (email, calendars, legacy ERPs, third-party SaaS). Powers Automation OS's outbound reach and inbound sync. | OAuth app management, rate-limited egress. |

## 5.6 Design rules for shared services

1. **Own one thing.** Each service has a single responsibility and a single System of
   Record. Overlap (e.g., Workflow vs Automation, Audit vs Logging) is resolved by explicit
   boundaries above, not left ambiguous.
2. **API + events, never shared tables.** Apps integrate through a service's API and the
   events it emits — never by reading its database. This is what keeps the platform
   loosely coupled despite being one codebase.
3. **Tenant-aware by construction.** Every shared service takes `tenant_id` in context and
   enforces isolation itself, so apps can't accidentally leak across tenants.
4. **Secure & audited by default.** Using the shared service is the *secure* path; audit,
   encryption, and permission checks come for free, removing the temptation to cut corners.
5. **Swappable infrastructure.** Because apps talk to the *service* (not Kafka/Redis/the
   vector DB directly), we can replace the underlying technology without touching a single app.
