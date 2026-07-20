# Sprint 4 — Knowledge OS (the third Phase-1 app)

> **Goal:** Ship **Knowledge OS** — a docs/wiki system — as the third app on the Foundation, completing
> the **People / Work / Knowledge** trio (PD-02). Knowledge OS is where a company's institutional memory
> lives, and — the whole point — it is *integrated by construction*: a page's **author is a People OS
> `Employee`** (person link) and a page can **reference a Work OS `Task`** (doc↔task). That closes the
> **task ↔ doc ↔ person** triangle the platform thesis has promised since [FOUNDER.md §2](../FOUNDER.md).
>
> Binding architecture = [/docs](README.md). This plan follows the same shape as
> [Sprint2-PeopleOS](Sprint2-PeopleOS.md) and [Sprint3-WorkOS](Sprint3-WorkOS.md): vertical slices,
> full-stack, each with an adversarial cross-tenant test as the merge gate.

## 0. Decisions (Sprint 4)
| ID | Decision | Rationale | Alternatives rejected |
|----|----------|-----------|-----------------------|
| SD-13 | **Spaces contain Pages** (Space ≈ Work Project) | Reuses the proven container pattern; gives docs a tenant-scoped home with its own KEY | One flat page pool (no organization); folders-as-pages only |
| SD-14 | **A Page's author is a People `Employee`; a Page may link one Work `Task`** | This is the cross-app moat made concrete — real FKs into People and Work, not name strings | Free-text author (throws away the graph); no task link (loses doc↔task) |
| SD-15 | **Page body is plain Markdown stored as `text`** | Zero-dependency, portable, renders anywhere; good enough for MVP | Rich JSON block model (heavy); HTML (unsafe/awkward) |
| SD-16 | **Pages have a `DRAFT`/`PUBLISHED` status and an optional `parent_id` tree** | Mirrors real wikis (drafts + nesting) without a heavy CMS | Versioning/history (deferred); flat-only (loses structure) |
| SD-17 | **Collaborative RBAC:** any member creates/edits pages & spaces; **archiving a space is OWNER/ADMIN** | Same posture as Work OS (SD in Sprint 3) — knowledge is a team asset | Per-space roles (deferred); author-only edit (too rigid for a wiki) |

## 1. Data model (Flyway V8, V9)
- **`spaces`** (V8): `id, company_id, name, key(≤10), description, status ACTIVE|ARCHIVED, created_by, timestamps`.
  Unique `(company_id, lower(key))`. A space groups pages and gives them a short KEY.
- **`pages`** (V9): `id, company_id, space_id, parent_id?, title, body(text), status DRAFT|PUBLISHED,
  author_id → employees (cross-app), linked_task_id → tasks (cross-app), sort_order, created_by, timestamps`.
  Indexed on space, company, author, parent.

Both tables carry `company_id` and are queried through `TenantContext` (SD-2). RLS remains the Sprint-2+
backstop debt — unchanged by this sprint.

## 2. Vertical slices
1. **K1 — Spaces.** `/knowledge` list + create; per-space KEY; archive (OWNER/ADMIN). Mirrors Work W1.
2. **K2 — Pages & editor.** `/knowledge/{spaceId}` two-pane: page tree ← list, reader/editor → right.
   Create/edit/delete, Markdown body, publish/unpublish, optional parent (nesting).
3. **K3 — Cross-app links.** Author auto-set to the creator's `Employee`; optional **Link a Work task**
   picker → the page shows `🔗 PLT-3`. Proves doc↔task↔person.
4. **K4 — Search.** `/knowledge` search box → `GET /knowledge/search?q=` across title + body, tenant-scoped,
   with a matched-text snippet.
5. **K5 — My pages.** `GET /knowledge/pages/mine` — everything I authored, across spaces.

## 3. API (base `/api/v1/knowledge`)
| Method | Path | Notes |
|--------|------|-------|
| GET | `/spaces` | list (active + archived) |
| GET | `/spaces/{id}` | one |
| POST | `/spaces` | create (any member) |
| PATCH | `/spaces/{id}` | rename / description |
| POST | `/spaces/{id}/archive` | OWNER/ADMIN |
| GET | `/spaces/{spaceId}/pages` | page summaries (tree via `parentId`) |
| POST | `/spaces/{spaceId}/pages` | create page (author = me) |
| GET | `/pages/{id}` | full page (body + resolved author/task) |
| PATCH | `/pages/{id}` | title/body/status/parent/linkedTask |
| DELETE | `/pages/{id}` | delete (also reparents children to null) |
| GET | `/pages/mine` | pages I authored |
| GET | `/search?q=` | tenant-wide title/body search + snippet |

## 4. Definition of Done
- Flyway V8/V9 apply on the embedded Postgres; app boots.
- Knowledge OS integration test suite is green, **including a cross-tenant isolation test on every slice**
  (a space/page in company A is invisible and unreachable from company B) and a **cross-app test**
  (a page authored by a People employee, linked to a Work task, surfaces both).
- Frontend `/knowledge` built and verified live against the real backend: create space → create page →
  write Markdown → publish → link a Work task → search finds it → it appears in *My pages*.
- Nav gains **Knowledge**; `next build` clean.
- [FOUNDER.md](../FOUNDER.md) (PD-06), [DECISIONS.md](../DECISIONS.md) (SD-13..17), [CONTEXT.md](../CONTEXT.md),
  and [CHANGELOG.md](../CHANGELOG.md) updated.

## 5. Deliberately deferred (logged as debt)
- **Page version history / diffs** — the obvious next depth increment.
- **Rich block editor & embeds** — Markdown-only for now (SD-15).
- **Comments / mentions / reactions** — a shared Comments primitive is a platform service (backlog).
- **Full-text search** (OpenSearch/tsvector) — MVP uses `ILIKE`; graduate under load (ADR-02).
- **Permissions per space** — reuses Sprint-1 roles (SD-17), no per-space ACLs yet.
</content>
