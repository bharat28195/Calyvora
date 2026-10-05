# Deploy troubleshooting — problems & solutions

Every problem hit while standing up the production environment on Render + Neon, and exactly how it
was fixed. Newest issues are the ones most likely to recur on a fresh setup. Keep this updated as new
issues come up.

The related runbooks: [GO-LIVE-CHECKLIST.md](GO-LIVE-CHECKLIST.md), [NEON-DB-SETUP.md](NEON-DB-SETUP.md),
[DEPLOY.md](DEPLOY.md).

---

## 1. Build: `failed to read dockerfile: open Dockerfile: no such file or directory`

**Cause:** the service was created manually and its **Root Directory** wasn't set, so Render looked
for `Dockerfile` at the repo root — but it lives in `backend/`.

**Fix:** service → Settings → **Root Directory = `backend`** (frontend service: `frontend`). A
Blueprint sync sets this automatically from `render.yaml` (`dockerfilePath` / `rootDir`); a
hand-created service does not.

---

## 2. Startup: `TENANT ISOLATION IS UNSAFE: the database role 'neondb_owner' has the BYPASSRLS attribute`

**Cause:** newer Neon projects give the default `neondb_owner` role the **BYPASSRLS** attribute, which
ignores row-level security. The app refuses to boot as such a role (one tenant could read another's
data). This is a safety feature, not a bug.

**Fix:** connect as a dedicated role **without** BYPASSRLS. In the Neon SQL editor:
```sql
CREATE ROLE orbit_app WITH LOGIN PASSWORD '<letters+numbers>'
  NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
GRANT CONNECT ON DATABASE neondb TO orbit_app;
GRANT USAGE, CREATE ON SCHEMA public TO orbit_app;
```
Set `DB_USERNAME=orbit_app` / `DB_PASSWORD=…`. **Never** set `REQUIRE_TENANT_ISOLATION=false` — that
disables the protection and ships the data leak.

---

## 3. Startup: `permission denied for table flyway_schema_history`

**Cause:** the first deploy ran as `neondb_owner`, so the tables ended up owned by it; after switching
to `orbit_app`, that role had no rights on them.

**Fix (clean):** create `orbit_app` **before the first deploy** and connect as it from the start, so
Flyway creates every table owned by `orbit_app` (see NEON-DB-SETUP.md §1b).
**Fix (recover an existing DB):**
```sql
GRANT orbit_app TO neondb_owner;
REASSIGN OWNED BY neondb_owner TO orbit_app;
GRANT ALL ON SCHEMA public TO orbit_app;
```

---

## 4. Startup: `schema "public" does not exist` (SQLSTATE 3F000)

**Cause:** a `DROP SCHEMA public CASCADE` ran but the schema was never recreated.

**Fix:** recreate it owned by the app role, then redeploy:
```sql
GRANT orbit_app TO neondb_owner;
CREATE SCHEMA public AUTHORIZATION orbit_app;
GRANT ALL ON SCHEMA public TO orbit_app;
```

---

## 5. Deploy: `==> Timed Out` (no open port detected)

**Cause:** two things together — the app bound to a hard-coded `8080` instead of Render's injected
`PORT`, so Render had to scan for the port; and on a 0.5-CPU Starter instance a full Spring Boot +
Hibernate startup took 3+ minutes. Render gave up waiting.

**Fix (code, shipped):**
- `server.port: ${PORT:${SERVER_PORT:8080}}` — bind to the platform's port so it's detected at once.
- `spring.data.jpa.repositories.bootstrap-mode: deferred` — build the EntityManagerFactory on a
  background thread so the web server binds its port without waiting for the JPA metamodel.

**If it still times out:** the instance is simply too small for the startup. Bump the backend to a
larger instance (Standard, 1 CPU / 2 GB) — more CPU roughly halves startup. The config fixes above
should make Starter enough, but this is the lever if not.

---

## 6. Flyway warning: `PostgreSQL 18.x is newer than this version of Flyway … support has not been tested`

**Cause:** Spring Boot 3.3 ships Flyway 10.10, which was tested up to PostgreSQL 16. The production
Neon DB runs PG 18. Migrations still ran fine — it's a warning, not an error.

**Fix (shipped):** override the managed version in `backend/pom.xml`:
```xml
<flyway.version>11.20.3</flyway.version>
```
Flyway 11.20.3+ officially supports PostgreSQL 18. Verified: context boots and all 57 migrations run.

---

## 7. DB connection: connection refused / cross-tenant reads / `channel_binding` errors

**Causes & fixes when building `DB_URL` from a Neon connection string:**
- **Used the `-pooler` host** → one tenant can read another's rows (the pooler splits the session
  setting the app relies on). Use the **direct** host (no `-pooler`); turn off "Connection pooling"
  in Neon's Connect dialog.
- **Left `&channel_binding=require` in the URL** → JDBC connection errors. Drop it; keep
  `?sslmode=require`.
- **Missing `jdbc:` prefix** → "URL must start with jdbc". `DB_URL` must begin `jdbc:postgresql://`.
- **Region mismatch** (DB in a different region than the service) → `UnknownHostException` / slow
  queries. Match regions (Singapore / ap-southeast-1).

---

## 8. Everyone logged out on every restart / deploy

**Cause:** no `JWT_*` keys set, so the app generates a throwaway RS256 keypair at each boot; a restart
invalidates every token.

**Fix:** set `JWT_KID`, `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` (generate with `openssl`, flatten each PEM
to one line). Boot log should read `RS256 JWT key store ready: … ephemeral=false`. Production needs
its own keypair; don't share with the demo.

---

## 9. Platform-owner password won't change / env var ignored

**Two traps, both fixed in code now:**

- **Wrong env var name.** The owner email/password were only `@Value` defaults with no
  `application.yml` binding, so Spring's relaxed binding looked for `CALYVORA_PLATFORM_OWNER_PASSWORD`,
  not the `PLATFORM_OWNER_PASSWORD` the docs told you to set — so the variable was silently ignored and
  the owner kept the hardcoded default. **Fixed:** `application.yml` now binds the clean names
  `PLATFORM_OWNER_EMAIL` and `PLATFORM_OWNER_PASSWORD`, and the hardcoded default password is gone.

- **Setting the password alone does NOT rotate it.** `PlatformOwnerBootstrap` only writes a password
  when it **creates** the owner or **moves it to a new email**. If the owner already exists under the
  same address, boot returns early and the password is left as-is. So to rotate off a compromised
  default on an existing deployment, **change `PLATFORM_OWNER_EMAIL` to a new address** (e.g. your
  business `owner@calyvora.net`) at the same time — that triggers the rename path, which resets the
  password and disables the old address.

**To set up (or rotate) the owner:** set both `PLATFORM_OWNER_EMAIL` (a new address) and
`PLATFORM_OWNER_PASSWORD`, then redeploy. Confirm in the log:
```
Moved the platform owner from <old> to <new> and reset its password. The old address can no longer sign in.
```

---

## What a healthy production boot looks like

```
The following 1 profile is active: "prod"
RS256 JWT key store ready: activeKid=…, ephemeral=false
Successfully validated 57 migrations
Schema "public" is up to date
[TENANT ISOLATION] OK — role 'orbit_app' is NOSUPERUSER without BYPASSRLS; Row-Level Security is enforced.
Tomcat started on port …
Started CalyvoraApplication
```
Then: `/actuator/health` → `200`, and on a prod-profile service `/api/v1/dev/mailbox` → `404`.
