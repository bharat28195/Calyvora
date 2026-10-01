# The database — Neon, and the two things that fail silently

Orbit's database moved from Render's managed Postgres to **Neon** on 4 September 2026.

## Why we moved

Render **deletes** a free Postgres 30 days after it is created. Not sleeps — deletes. Everything else
on that free tier degrades; this one destroys the data, which makes it unusable for anything you intend
to keep. Neon's free tier has no expiry and upgrades in place, so the move is a one-time cost paid
deliberately rather than an emergency later.

## Current setup

| | |
|---|---|
| Provider | Neon (`winter-sun-22777850`) |
| Region | AWS `ap-southeast-1` (Singapore) — **same region as the Render services** |
| Database | `neondb` |
| App role | `calyvora` |
| Version | PostgreSQL 18.6 |

Region matching is the only latency decision that matters here. Same region is ~1–2ms per query;
cross-region is ~50ms, and one page load makes a dozen queries.

Configured on the backend service as three dashboard secrets (`sync: false` in `render.yaml`, so they
are never in the repository):

```
DB_URL       jdbc:postgresql://<host>/neondb?sslmode=require
DB_USERNAME  calyvora
DB_PASSWORD  <set in the Render dashboard>
```

`application.yml` prefers a full `DB_URL` when given, so no application change was needed.

Two things people get wrong, both of which fail with unhelpful errors:

- **`DB_URL` must start with `jdbc:`.** Neon hands you a `postgresql://` URI; Java needs the prefix.
- **No username or password inside `DB_URL`.** They are separate variables. This is also why a
  password containing `@` is fine here and would not be if it lived in the URL.

---

## Trap 1 — never use the pooled hostname

Neon offers a hostname ending in `-pooler`. **Do not use it for this application.**

The pooler is PgBouncer in *transaction* mode. `TenantAwareDataSource` binds the tenant with a
**session**-scoped setting:

```java
select set_config('calyvora.company_id', ?, false)   // false = session scope
```

Under transaction pooling, that setting and the query relying on it can land on different server
connections. Best case a screen shows nothing; **worst case one tenant reads another's rows.** Flyway
also needs a direct connection for its advisory lock.

---

## Trap 2 — the default role bypasses all tenant isolation

Neon's default role, `neondb_owner`, holds **`BYPASSRLS`**. A role with that attribute **ignores every
row-level security policy without erroring.** Connect the application as `neondb_owner` and the
isolation enforced by 21 of the migrations is silently inert — every screen still works, and the
database has quietly stopped being the thing that separates customers.

This was caught before any data existed, by running the check rather than assuming:

```sql
select current_user, rolsuper, rolbypassrls from pg_roles where rolname = current_user;
```

```
neondb_owner   false   TRUE     <-- never connect the app as this
calyvora       false   false    <-- the application role
```

The `calyvora` role was created without it:

```sql
create role calyvora with login password '...'
  nosuperuser nocreatedb nocreaterole nobypassrls inherit;
grant all on schema public to calyvora;
grant all privileges on database neondb to calyvora;
```

`alter schema public owner to calyvora` is rejected (it needs `neondb_owner` to be a member of the new
role) and is **not required** — `grant all on schema public` lets Flyway create tables, and whoever
creates a table owns it, which is what makes `FORCE ROW LEVEL SECURITY` apply to the app role.

Note that `alter role ... nosuperuser nobypassrls` is also rejected even when restating attributes the
role already has — Postgres checks the permission, not whether the value would change. Set the password
alone and assert the attributes with a `select`.

**Run that check again after any role change, and after any provider migration.** It is one query, and
it is the difference between having tenant isolation and appearing to.

### Proof that it works

Verified on the real schema, not a mock:

```sql
select count(*) from employees;                                    -- 0
set calyvora.company_id = '91d3cb21-…';                            -- bind Northwind
select count(*) from employees;                                    -- 6
```

Zero rows until a tenant is bound. That is the database refusing, independently of application code.

---

## Connecting with DBeaver

There is no "Neon" driver — Neon *is* Postgres. **New Database Connection → PostgreSQL.**

| Field | Value |
|---|---|
| Host | the Neon host, **without** `-pooler` |
| Port | `5432` |
| Database | `neondb` |
| Username | `calyvora` |
| Password | as set in Render |

**SSL tab → Use SSL → mode `require`.** Neon refuses plain connections, and the error does not mention
SSL.

Two things that will confuse you in the first minute:

1. **Connect to `neondb`, not `postgres`.** The `postgres` database is Neon's own — its `neon` and
   `neon_migration` schemas are not yours. Edit the connection's *Database* field, or tick *Show all
   databases* on the PostgreSQL tab.

2. **Most tables will look empty.** `select * from employees` returns nothing, with no error. That is
   `FORCE ROW LEVEL SECURITY` working. Bind a tenant first, in the same editor tab:

   ```sql
   select id, name from companies order by name;   -- companies is outside RLS
   set calyvora.company_id = '<the id>';
   select * from employees;
   ```

   The setting is **per connection**. A new editor tab may open a new connection, and a query that
   mysteriously returns nothing usually means no tenant is bound on it.

`companies`, `users`, `invitations` and the token tables sit outside RLS on purpose — they are read
before any tenant exists (login, verification, invite acceptance).

Use `calyvora`, not `neondb_owner`. Connecting as the owner shows you data the application itself
cannot see, which makes debugging actively misleading.

---

## What the migration did and did not carry

Nothing was dumped or restored. The schema is built by Flyway (**43 migrations**, V38 does not exist —
the numbering skips it) and the platform owner is recreated by `PlatformOwnerBootstrap` at boot, so an
empty database is all that was needed.

Deliberately left behind: ten test companies accumulated during QA (`Deploy Probe`, `Live Test Co`,
`QA Sandbox …`, `Test Company 1/2`, `Verify Demo …`). Starting empty removed them for free.

Genuinely lost: one real company row, `Bharat Enterprizes`, recreated from the platform console.

Verified after the move: 49 tables, 43 migrations all successful, and an identical role-by-role API
sweep to the one run before it — same passes, same known permission gaps, nothing newly broken.

---

## Operational notes

- **Neon auto-resumes.** It suspends when idle and wakes on the first connection in about a second.
  There is nothing to wake manually. When the app is unreachable, it is the Render service
  hibernating, not the database — see `GO-LIVE.md`.
- **Free tier limits:** 0.5 GB storage. Ample for now; watch it as customers arrive.
- **Branching** is Neon's real advantage for us: fork production and run a migration against a copy of
  real data. Migration V40 passed 264 tests and still broke a deploy. Branching is the tool that
  catches that class of failure.
- **Rotate `neondb_owner`'s password.** It is the role that can bypass every policy, and nothing uses
  it. Neon's console reset auto-generates one; you can choose your own with
  `alter role neondb_owner with password '…'` in Neon's SQL editor.
