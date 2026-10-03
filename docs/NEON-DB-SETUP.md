# Neon database setup — from scratch

The exact procedure to create a Neon Postgres database for an Orbit backend and wire it into Render.
Written after setting up the `orbit-prod` production database on 2026-10-03. Follow it top to bottom;
every step is a dashboard action.

**Why Neon, not Render's Postgres:** Render deletes a free Postgres 30 days after it's created (not
sleeps — deletes). Neon's free plan has no expiry, gives up to 100 projects, and each project gets its
own 0.5 GB storage + compute allowance. One Neon project per environment (production, demo) is free.

**What the app needs from Neon:** just a connection string. The app owns its schema with **Flyway**
(it builds every table on first boot) and keeps tenants apart with Postgres **row-level security**.
So do **not** use Neon's own migration tooling (`neon deploy`, `neon.ts`) — it would fight Flyway.
Ignore Neon's "set up Neon for an agent" CLI/MCP wizard; it is not needed here.

---

## 1. Create the project

1. Sign in at **console.neon.tech**.
2. **New Project**.
3. Settings:
   - **Name:** the environment — e.g. `orbit-prod` (production) or `orbit-demo` (demo).
   - **Postgres version:** leave the default.
   - **Region:** **Singapore (AWS ap-southeast-1)** — must match the Render service's region, or every
     query pays cross-region latency.
4. Create. Neon makes a database (usually `neondb`) and a role (usually `neondb_owner`).

One Neon project **per environment**. Production and demo must never share a database.

---

## 2. Open the connection dialog and turn OFF pooling

1. Project → branch (e.g. `production`) → **Connect**.
2. Tab: **Postgres database**. Database `neondb`, Role `neondb_owner` (the defaults are fine).
3. **Turn the "Connection pooling" toggle OFF.**

⚠️ **This is the step everyone gets wrong.** With pooling ON the host ends in `-pooler`, e.g.
`ep-blue-frost-b3gka5fy-pooler.c-4.ap-southeast-1.aws.neon.tech`. The app binds each request's tenant
on a session-scoped setting, and a transaction pooler can land that setting and the query that relies
on it on different physical connections — best case a screen shows nothing, **worst case one tenant
reads another tenant's rows.** Flyway's advisory lock also needs a direct connection. So the host must
**not** contain `-pooler`.

With pooling OFF you get the direct string, e.g.:
```
postgresql://neondb_owner:npg_XXXXXXXX@ep-blue-frost-b3gka5fy.c-4.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
```

---

## 3. Convert the string into the three env vars

The app reads **three separate** variables, not one URL with credentials in it. From the string above:

| Env var | Value | Where it is in the string |
|---|---|---|
| `DB_USERNAME` | `neondb_owner` | between `//` and `:` |
| `DB_PASSWORD` | `npg_XXXXXXXX` | between `:` and `@` |
| `DB_URL` | `jdbc:postgresql://ep-blue-frost-b3gka5fy.c-4.ap-southeast-1.aws.neon.tech/neondb?sslmode=require` | the host + db, reshaped (see below) |

Building `DB_URL`, three rules:
1. **Prefix with `jdbc:`** — the Java driver requires it.
2. **Use the direct host** (no `-pooler`).
3. **Keep `?sslmode=require`, but DROP `&channel_binding=require`** — that's a libpq flag the JDBC
   driver doesn't use, and leaving it in causes connection errors.
4. Do **not** put the username/password in `DB_URL` — they go in their own two variables.

So the shape is always:
```
jdbc:postgresql://<DIRECT-HOST>/<DB-NAME>?sslmode=require
```

---

## 4. Set them on the Render backend service

Render → the backend service (`orbit-prod-backend` for production) → **Environment** → add/update:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (all `sync:false` — never commit them). Save; the service
redeploys and Flyway builds the schema in the empty database.

For the **demo** backend (`calyvora-backend`), do the same with the `orbit-demo` project's string.

---

## 5. Rotate the password if it was ever shown

A Neon password is shown in the Connect dialog and in any screenshot or paste of it. If it has been
seen anywhere (a screenshot, a chat, a ticket), click **"Reset password"** in that dialog, then update
`DB_PASSWORD` in Render with the new value. The host and username don't change. Do this before a real
customer is on the database.

---

## 6. Verify

After the backend deploys:
- `https://<backend>.onrender.com/actuator/health` → `200 {"status":"UP"}` (it connected and migrated).
- `https://<backend>.onrender.com/api/v1/dev/mailbox` → `404` on a `prod`-profile service (dev
  endpoints don't exist in prod — this is also how you confirm the service is really in prod mode).

Isolation check — Neon SQL Editor:
```sql
select current_user, rolsuper, rolbypassrls from pg_roles where rolname = current_user;
```
`rolsuper` and `rolbypassrls` must both be **false**. The app enforces this at boot
(`TenantIsolationVerifier`) and **refuses to start** if the role can bypass RLS — because such a role
would let one tenant read another's data.

**⚠️ Newer Neon projects give `neondb_owner` the BYPASSRLS attribute** (confirmed on the orbit-prod
project, Oct 2026). If your check shows `rolbypassrls = true`, do NOT use that role and do NOT set
`REQUIRE_TENANT_ISOLATION=false` (that just ships the data leak). Create a dedicated app role instead:

```sql
-- As neondb_owner, in the project's SQL editor.
CREATE ROLE calyvora_app WITH LOGIN PASSWORD '<letters+numbers>'
  NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
GRANT CONNECT ON DATABASE neondb TO calyvora_app;

-- Only safe on a FRESH database (no real data). Rebuilds the schema owned by the new role so
-- Flyway re-runs cleanly and future migrations work (single owner). SKIP the DROP if the DB holds
-- real data — instead reassign ownership, which is more involved.
DROP SCHEMA public CASCADE;
CREATE SCHEMA public AUTHORIZATION calyvora_app;
GRANT ALL ON SCHEMA public TO calyvora_app;
```
Then set `DB_USERNAME = calyvora_app` / `DB_PASSWORD = <chosen>` (keep `DB_URL` the same) and redeploy.
Boot log should then read `[TENANT ISOLATION] OK — role 'calyvora_app' is NOSUPERUSER without BYPASSRLS`.

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Backend logs `UnknownHostException` on the DB host | Region mismatch, or you used Render's private DB host. Use the Neon public host; match regions (Singapore). |
| Connection errors mentioning channel binding / SSL | You left `channel_binding=require` in `DB_URL`, or dropped `sslmode=require`. Use exactly `?sslmode=require`. |
| `TENANT ISOLATION IS UNSAFE` at boot | The DB role has BYPASSRLS (newer Neon `neondb_owner` does). Create a dedicated `calyvora_app` role as shown in the isolation-check section and point the backend at it. Don't use `REQUIRE_TENANT_ISOLATION=false`. |
| Tenants can see each other | You're on the `-pooler` host. Switch `DB_URL` to the direct host. |
| Driver error "URL must start with jdbc" | `DB_URL` is missing the `jdbc:` prefix. |
