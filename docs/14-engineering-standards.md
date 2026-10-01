# 14 · Engineering Standards

Standards exist so that 100 engineers produce *one* coherent system, not 100 dialects. These
are the rules; where a rule has a non-obvious rationale, it's stated. Deviations require an
ADR. Standards are enforced by tooling and review gates wherever possible — a standard that
relies on memory is a suggestion.

## 14.1 Naming

- **Ubiquitous language:** use the [glossary](00-glossary.md) terms exactly. `Tenant` ≠ `Org`;
  `User` ≠ `Principal`. Domain terms in code match domain terms in product and docs.
- **Consistency over cleverness:** names are descriptive and boring. No abbreviations except a
  short approved list (`id`, `url`, `db`). No `tmp`, `data2`, `helperUtils`.
- **Casing conventions** (per language): PascalCase types, camelCase members (Java/TS),
  snake_case DB identifiers, kebab-case URLs and repo/package names, SCREAMING_SNAKE for
  constants/env vars, lower.dotted Java package names.
- **Events:** `PastTenseFact` names (`InvoicePaid`, `EmployeeHired`), namespaced by context
  (`finance.invoice.paid`).
- **APIs:** resources are plural nouns (`/v1/invoices`), not verbs; actions that aren't CRUD
  are sub-resources or explicit action endpoints.
- **Booleans** read as assertions (`isActive`, `hasAccess`); avoid negatives (`isNotDisabled`).

## 14.2 Repositories & code organization

- **Monorepo** for the platform (Foundation + first-party apps). *Why:* atomic cross-cutting
  changes, shared tooling, one dependency graph, easy refactoring across module boundaries,
  consistent CI. Enforced module boundaries (see [06](06-architecture-principles.md#61-topology))
  give us decoupling *without* repo fragmentation.
- **Structure by domain, not by layer.** Group code by bounded context/module (all of
  "Payroll" together), not by technical layer (all controllers together). Domain cohesion beats
  layer cohesion.
- **Clear public interfaces per module;** internals are private. Cross-module use goes through
  the module's published interface or its events — never its internals. CI enforces this.
- **Shared libraries** for genuinely cross-cutting concerns (tenancy, auth client, telemetry,
  the service template) — versioned, owned, documented.
- Third-party/customer-facing SDKs live in their own published packages.

## 14.3 Git & branching

- **Trunk-based development.** `main` is always releasable. Short-lived feature branches
  (target < 2 days), small PRs. *Why:* minimizes merge hell and integration risk; pairs with
  feature flags to decouple merge from release. See [06](06-architecture-principles.md#611-cicd--delivery).
- **Conventional Commits** (`feat:`, `fix:`, `chore:`, `refactor:`, `docs:`…) for readable
  history and automated changelogs/versioning.
- **PR discipline:** every change via PR; no direct pushes to `main`; branch protection
  requires green CI + review; linear or squash-merge history.
- **Feature flags, not long-lived branches,** for incomplete work. Ship dark, enable
  progressively.
- **Signed commits** and protected release process.

## 14.4 Architecture (rules of the road)

- **Respect the layering:** apps depend on the Foundation; the Foundation never depends on an
  app. Modules depend via interfaces/events, never shared mutable tables.
- **Single System of Record** per entity; no duplicate ownership. Cross-context data via events
  (read models), not shared writes.
- **API-first, event-first:** design the contract (OpenAPI/GraphQL) and the events before the
  implementation and before the UI. See [02](02-product-philosophy.md#21-api-first).
- **Tenant-first:** tenant context is ambient and mandatory in every code path.
  See [07](07-multi-tenant-strategy.md).
- **Stateless services;** state lives in datastores, not memory or disk.
- **Async by default** for anything that can tolerate it; the request path stays fast.
- New foundational tech, a new datastore, or a service extraction each require an **ADR**.

## 14.5 API design

- **Consistency across all APIs:** same auth, pagination (cursor-based), filtering, sorting,
  error envelope, and idempotency conventions everywhere. A developer who learns one Calyvora
  API knows them all.
- **Versioning:** public REST versioned in path (`/v1/`); never break a published contract
  silently; deprecate with a window. GraphQL evolves additively with `@deprecated`.
  See [06](06-architecture-principles.md#612-versioning).
- **Idempotency:** mutating endpoints accept an idempotency key; retries are safe.
- **Errors:** structured, machine-readable error codes + human messages + correlation id;
  correct HTTP semantics; never leak internals or stack traces.
- **Pagination is mandatory** — no unbounded list endpoints.
- **Every API action is registrable as an AI tool** (safe, permissioned) — API-first is what
  makes [AI-first](02-product-philosophy.md#22-ai-first-ai-native) real.
- **Documented:** OpenAPI/GraphQL schema is the source of truth, generated docs, examples, and
  a sandbox.

## 14.6 Database design

- **Every table is tenant-scoped** (`tenant_id`, RLS policy) unless it is explicitly global
  infrastructure metadata (rare, reviewed).
- **Migrations only** — schema changes are versioned, reviewed, reversible, and
  **expand-then-contract** for zero-downtime. No manual production schema edits, ever.
- **No cross-schema/cross-context joins;** integrate via events/read models.
- **Explicit constraints:** foreign keys, not-null, unique, and check constraints express
  invariants in the DB, not just app code. Correctness lives close to the data.
- **Indexing & performance:** index for known query patterns; no unbounded scans; review query
  plans for hot paths.
- **Data classification** labels drive encryption/retention/residency automatically; every
  entity declares its SoR, owner, and retention.
- **Soft-delete + retention** where the domain and compliance require recoverability/erasure.

## 14.7 Testing

- **Test pyramid:** many fast unit tests, fewer integration tests, few end-to-end tests.
  *Why:* speed + reliability; over-relying on slow, flaky E2E tests kills velocity.
- **Contract tests** for every API and every event schema (producer/consumer) — the safety net
  for a decoupled, event-driven platform.
- **Mandatory isolation tests:** automated cross-tenant access attempts that must fail. Non-
  negotiable. See [07](07-multi-tenant-strategy.md#73-defense-in-depth-for-isolation).
- **Security tests** (authz on every sensitive endpoint, injection, SAST/DAST) in CI.
- **Performance/load tests** on critical paths; **chaos tests** for resilience of key flows.
- **AI evals:** prompts and agents have evaluation suites (accuracy, grounding, safety,
  regression) — prompts are tested like code. See [09](09-ai-strategy.md#97-memory-prompts-and-semantic-search).
- **Coverage is a signal, not a target** — we require meaningful tests on behavior, not a gamed
  percentage. Critical paths (auth, tenancy, money, data integrity) demand the highest rigor.
- **CI gate:** no merge without green tests.

## 14.8 Code reviews

- **Every change is reviewed** by at least one qualified owner; changes to Foundation,
  security, tenancy, or data models require a domain owner + heightened scrutiny.
- **Review for:** correctness, security (esp. tenant isolation & authz), the standards in this
  doc, clarity, and test adequacy — not style (that's automated).
- **Small PRs, fast reviews.** Large PRs are split. Reviews are a priority, not a backlog.
- **Constructive & specific:** reviewers explain the *why*; authors respond to every comment.
- **Author is responsible;** reviewer is accountable for what they approve. Blocking concerns
  are resolved, not overridden silently.

## 14.9 Logging & observability

- **Structured logging** (JSON), always tagged with `tenant_id`, `principal`, `trace_id`,
  `request_id`. Never log secrets or sensitive PII (redact by policy).
- **Three pillars via OpenTelemetry** — traces, metrics, logs, correlated by ids. See
  [06](06-architecture-principles.md#610-observability).
- **Audit vs logging are distinct:** business/security truth goes to the Audit service
  (immutable, retained); diagnostics go to logging. See [08](08-security-architecture.md#85-audit-logging).
- **Log levels used correctly:** ERROR = actionable failure, WARN = degraded, INFO = notable
  business/operational events, DEBUG = diagnostic. No log spam; no silent failures.
- **Every service exposes health/readiness probes and RED metrics;** every critical path has an
  SLO and an alert.

## 14.10 Error handling

- **Fail loud, fail safe.** Never swallow errors silently. On failure, do the safe thing
  (deny access, don't post the transaction) — never the permissive thing.
- **Errors are typed and meaningful;** distinguish expected domain errors (validation, not-
  found, forbidden) from unexpected faults. Only unexpected faults page someone.
- **Resilience patterns:** timeouts, retries with backoff + jitter (idempotent ops only),
  circuit breakers, and graceful degradation on dependency failure. No unbounded waits.
- **Correlation:** every error carries a correlation id surfaced to the client for support.
- **No sensitive data in error responses;** internals stay internal.

## 14.11 Documentation

- **Docs-as-code:** documentation lives with the code, reviewed in PRs, versioned. Stale docs
  are bugs.
- **This constitution** is the top-level architecture doc; module-level design docs and **ADRs**
  (`docs/adr/`) record decisions. Every significant decision is an ADR — immutable, superseded
  not deleted.
- **Every service/module ships:** a README (what/why/how to run), API docs (generated), and a
  design doc for non-trivial features (including required "AI opportunities" and "security/
  threat model" sections).
- **Runbooks** for operational procedures (which become automation — [automation-first](02-product-philosophy.md#211-automation-first)).

## 14.12 How standards are enforced (not just stated)

| Standard | Enforcement |
|----------|-------------|
| Naming, casing, style | Linters/formatters in CI (auto-fail) |
| Module boundaries | Architecture-fitness/dependency rules in CI |
| Tenant scoping | Lint rule + RLS + isolation tests |
| API/event contracts | Contract tests + schema-registry compatibility gate |
| Security | SAST/DAST/SCA/secret-scan gates + review |
| Test presence & pass | CI merge gate |
| Migrations & schema | Migration review gate + no manual prod changes (GitOps) |
| Architecture decisions | ADR requirement + Office-of-the-CTO sign-off for foundational changes |

> A principle enforced by a machine is a principle. A principle enforced by hope is a wish. We
> build the machines.
