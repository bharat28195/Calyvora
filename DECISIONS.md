# DECISIONS.md — Architecture & Product Decision Ledger

> Canonical, append-only ledger of significant decisions. Newest first. Each entry is immutable;
> superseded decisions are marked, never deleted. Deep rationale lives in [/docs](docs/README.md);
> narrative lives in [FOUNDER.md](FOUNDER.md). IDs: `ADR-##` architecture, `PD-##` product,
> `SD-##` Sprint-scoped implementation decision.

## Foundation hardening — RS256 JWT + key rotation (2026-07-21)

| ID | Decision | Rationale (short) | Alternatives rejected | Status |
|----|----------|-------------------|-----------------------|--------|
| SD-5a | **Access tokens now signed with RS256** (asymmetric), not the HS256 shared secret | Verifiers hold only the public key — no shared secret to leak; a compromised verifier can't mint tokens | Keep HS256 (couples every verifier to the signing secret) | Accepted — **fulfils the deferred half of SD-5** |
| SD-23 | **Key rotation via a `kid`-tagged key set**: one active signing key, all configured keys trusted for verification | Zero-downtime rotation — publish the new key, flip active, retire the old once its last token expires | Single fixed key (no rotation path); restart-to-rotate (drops live tokens) | Accepted |
| SD-24 | **Public keys served at `/.well-known/jwks.json`** (RFC 7517) | Standard discovery so the frontend / a gateway / future services verify tokens without out-of-band key sharing | Bundle keys into each client (stale on rotation) | Accepted |
| SD-25 | **Empty key config → ephemeral in-memory keypair at startup** (local/dev/test only, logs a warning) | Keeps dev zero-config while making it loud that a shared env MUST supply PEM keys | Require PEM keys everywhere (dev friction); ship a committed dev private key (leak risk) | Accepted |

Fulfils the `RS256 in Sprint 2` follow-up on **SD-5** below. Keys are PKCS#8 PEM via
`calyvora.security.jwt.{active-kid,keys}`; see [application.yml](backend/src/main/resources/application.yml).

## Sprint 5 — Work OS depth: Sprints, Backlog & Tickets (2026-07-20)

| ID | Decision | Rationale (short) | Alternatives rejected | Status |
|----|----------|-------------------|-----------------------|--------|
| SD-18 | **Sprints belong to a project; a task has an optional `sprint_id`** (null = backlog) | Matches how teams plan; a task is in ≤1 sprint or the backlog | Global sprints; many-to-many task↔sprint | Accepted |
| SD-19 | **≤1 ACTIVE sprint per project**, enforced by a partial unique index | The board's "current sprint" must be unambiguous | App-only check (racy); many active | Accepted |
| SD-20 | **Completing a sprint returns its non-DONE tasks to the backlog** | Standard agile carry-over; nothing lost | Force-close tasks; strand them | Accepted |
| SD-21 | **Board = active sprint; if none, board shows the backlog** | Work is usable before any sprint exists | Force sprint creation first | Accepted |
| SD-22 | **Support tickets are a lightweight type inside Work** (assignee = People employee) | Founder wants tickets now; thin version proves the shape | Full Service OS now; free-text assignee | Accepted (debt) |
| SD-22b | **Tickets are deliberate debt → graduate to Service OS (Phase 2)** | Honesty in the record; tickets' real SoR is customers/SLAs | Pretend Work is the permanent home | Accepted; logged |

Full context: [docs/Sprint5-WorkOS-Sprints.md](docs/Sprint5-WorkOS-Sprints.md).

## Sprint 4 — Knowledge OS (2026-07-20)

| ID | Decision | Rationale (short) | Alternatives rejected | Status |
|----|----------|-------------------|-----------------------|--------|
| SD-13 | **Spaces contain Pages** (Space ≈ Work Project) | Reuses the proven container pattern; gives docs a tenant-scoped home with a KEY | One flat page pool; folders-as-pages only | Accepted |
| SD-14 | **A Page's author is a People `Employee`; a Page may link one Work `Task`** | The cross-app moat made concrete — real FKs into People and Work (`author_id`, `linked_task_id`), not name strings | Free-text author (throws away the graph); no task link (loses doc↔task) | Accepted |
| SD-15 | **Page body is plain Markdown stored as `text`** | Zero-dependency, portable, renders anywhere; enough for MVP | Rich JSON block model (heavy); HTML (unsafe) | Accepted; rich editor later |
| SD-16 | **Pages have `DRAFT`/`PUBLISHED` status + optional `parent_id` tree** | Mirrors real wikis (drafts + nesting) without a CMS | Versioning/history (deferred); flat-only | Accepted |
| SD-17 | **Collaborative RBAC:** any member creates/edits spaces & pages; **archiving a space is OWNER/ADMIN** | Same posture as Work OS — knowledge is a team asset | Per-space ACLs (deferred); author-only edit (too rigid) | Accepted |

Also: added `EmployeeService.ensureEmployeeId(companyId, userId)` so other OS-apps attach authorship to
the People org graph without duplicating provisioning (People OS owns that rule). Full context:
[docs/Sprint4-KnowledgeOS.md](docs/Sprint4-KnowledgeOS.md).

## Sprint 1 — Platform Foundation (2026-07-05)

| ID | Decision | Rationale (short) | Alternatives rejected | Status |
|----|----------|-------------------|-----------------------|--------|
| SD-1 | **JPA/Hibernate** for persistence | Velocity for related entities; less boilerplate | Spring Data JDBC (more explicit, more code) | Accepted |
| SD-2 | **Tenant isolation via `company_id` + `TenantContext` (app-layer)**; RLS deferred to Sprint 2 | One-sprint pragmatism; guarded by adversarial cross-tenant tests | RLS now (higher setup cost) | Accepted (debt logged) |
| SD-3 | **Email globally unique; user in exactly one company** | Simplest correct MVP | `memberships` join table now (over-engineered for Sprint 1) | Accepted; superseded plan in Future |
| SD-4 | **Create Company + Owner at register (PENDING); verify activates** | Avoids separate registrations table | Separate `registrations` staging table | Accepted |
| SD-5 | **Access JWT ~15m + rotating refresh cookie (hashed, reuse-detection)**; HS256 | Security/simplicity balance | Long-lived JWT (unsafe); sessions (stateful) | Accepted; **HS256→RS256 done 2026-07-21, see SD-5a/SD-23** |
| SD-6 | **Mailpit** for local email; `EmailService` interface | Zero-dependency local demo; swappable | Real SMTP in dev (friction) | Accepted |
| SD-7 | **Monorepo**, ~~Gradle (Kotlin DSL)~~ backend | Atomic changes; matches constitution | Polyrepo; Maven | **Superseded by SD-9** (build tool → Maven); monorepo stance still Accepted |
| SD-9 | **Maven** (not Gradle) as the backend build tool | Founder directive (2026-07-09): team standardizes on Maven — familiarity + ubiquitous Spring Boot tooling/examples. Supersedes the Gradle half of SD-7. | Gradle Kotlin DSL (rejected per founder) | Accepted |
| SD-10 | **Zonky embedded Postgres** for integration tests + an `embedded` run profile | Dev machine has no Docker; embedded PG runs a real Postgres binary so tenant-isolation tests and local runs use real SQL (not H2). | Testcontainers (needs Docker); H2 (not Postgres-faithful) | Accepted (CI still uses Docker/Testcontainers-capable runner) |
| SD-11 | **Refresh-token reuse revocation runs in a `REQUIRES_NEW` tx** (`RefreshTokenRevoker`) | On reuse we revoke the whole family then throw 401; a same-tx revoke would be rolled back by that throw, leaving stolen tokens live. | Revoke in the same tx (insecure — rolled back) | Accepted |
| SD-12 | **`ConsoleEmailService` prints links under the `embedded` profile**; mail-send failures are swallowed; actuator mail health disabled | Local dev without Mailpit still needs the verification/invite link, and a downed SMTP must never 500 a registration or mark the app DOWN. | Require Mailpit always (friction) | Accepted |
| SD-8 | **Roles `OWNER/ADMIN/MEMBER` only** | Smallest set to prove RBAC | Full RBAC+ABAC engine now | Accepted; full engine later |

Full context: [docs/Sprint1.md §0](docs/Sprint1.md#0-key-sprint-1-decisions-please-veto-any-you-disagree-with).

## Foundational (2026-07-05) — see FOUNDER.md for full detail
`ADR-01` Modular monolith · `ADR-02` Postgres/Redis/Kafka/OpenSearch/pgvector · `ADR-03` Kafka +
outbox + CloudEvents · `ADR-04` Zero-Trust security (OIDC/SSO, RBAC+ABAC, per-tenant keys, audit) ·
`ADR-05` AI as Foundation primitive · `ADR-06` Hybrid 3-tier multi-tenancy · `ADR-07` **Backend =
Java + Spring Boot** (was .NET). · `PD-01` 14-app decomposition · `PD-02` Depth-first phasing.
Detail: [FOUNDER.md §4](FOUNDER.md#4-architecture-decision-log).
