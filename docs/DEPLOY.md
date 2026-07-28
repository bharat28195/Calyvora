# Deploying Orbit (Priority HR) — first-time guide

This is a plain-English, click-by-click guide to putting the app on the internet so you can test it
like a real product (like Keka/Zoho). No prior devops experience assumed.

The app has **three parts**:

| Part | What it is | Where it runs |
|------|------------|---------------|
| **Database** | PostgreSQL — stores every company's data | Managed Postgres |
| **Backend** | Spring Boot (Java) — the API + all business logic | A Docker web service |
| **Frontend** | Next.js — the screens you click | A Node web service |

The browser only talks to the **frontend**; the frontend quietly forwards `/api/*` calls to the
**backend**; the backend talks to the **database**. You don't have to wire that — it's already built.

---

## ⚠️ The one rule that matters: the database role must NOT be a superuser

The app is multi-tenant (many companies share one database) and keeps them apart using Postgres
**Row-Level Security (RLS)**. Postgres **superuser** roles *ignore* RLS — so if the app connects as a
superuser, one company could see another's data. The app now **refuses to start** if it detects an
unsafe role (you'll see a clear error in the logs), so you can't ship this by accident.

- ✅ **Render, Neon, Supabase, AWS RDS** give you a *non-superuser* role by default — safe, nothing to do.
- ⚠️ **Railway / a raw Docker Postgres** give you the `postgres` **superuser** — you must create a
  dedicated app role first (one SQL snippet, shown in the Railway section below).

---

## Option A — Render.com (recommended for your first deploy)

Render can create all three parts from one file (`render.yaml`, already in this repo) and its Postgres
is safe out of the box. Free tier is enough to test (with two caveats: services **sleep after ~15 min
idle** so the first request is slow, and the **free database is deleted after 30 days** — upgrade to
the $7/mo "starter" plans for always-on + permanent).

### Steps

1. **Push this branch to GitHub** (if it isn't already):
   ```bash
   git push origin product/hr-platform
   ```
2. Go to **https://render.com** → sign up (use "Sign in with GitHub").
3. Click **New +** (top right) → **Blueprint**.
4. **Connect your GitHub repo**, pick this repository, and choose the `product/hr-platform` branch.
5. Render reads `render.yaml` and shows three resources: `calyvora-db`, `calyvora-backend`,
   `calyvora-frontend`. Click **Apply**.
6. Wait ~5–10 minutes for the first build (the Java backend is the slow one). You can watch each
   service's **Logs** tab.
7. When `calyvora-backend` logs show `Started ... in X seconds` and
   `[TENANT ISOLATION] OK`, and the frontend is "Live", open the **frontend** URL
   (`https://calyvora-frontend.onrender.com`).

### Load sample data (5 companies, admins, employees, payroll…)

Because this test deploy runs under the `staging` profile, the one-click seeding still works. After
the services are up, seed from your own machine (replace with your backend URL):

```bash
# Creates Northwind demo + platform owner, returns a login you can use:
curl -X POST https://calyvora-backend.onrender.com/api/v1/dev/seed-demo

# Adds 5 varied sample companies for the owner console:
curl -X POST https://calyvora-backend.onrender.com/api/v1/dev/seed-platform
```

(You can also run these in your browser's dev-tools console, or with any REST tool like Postman.)

**Logins after seeding** (password `demopass123` for all):
- **Platform owner:** `owner@priorityhr.app`  → sees the Platform console (all companies)
- **Company admin:** `ava.chen@northwind.demo`
- **HR:** `leo.martin@northwind.demo` · **Manager:** `tom.becker@northwind.demo` · **Employee:** `sara.okoro@northwind.demo`

---

## Option B — Railway (all-in-one, no sleeping, needs the DB role fix)

Railway keeps services always-on and is very beginner-friendly, but its Postgres gives you the
`postgres` **superuser**, which is unsafe for this app. Do the 4-line fix below.

1. https://railway.app → **New Project** → **Deploy from GitHub repo** → pick this repo.
2. Add a **PostgreSQL** database to the project (New → Database → PostgreSQL).
3. Open the Postgres service → **Data / Query** tab and run this once to create a safe app role:
   ```sql
   CREATE ROLE calyvora_app WITH LOGIN PASSWORD 'pick-a-strong-password' NOSUPERUSER;
   GRANT ALL ON DATABASE railway TO calyvora_app;         -- 'railway' is the default db name
   ALTER DATABASE railway OWNER TO calyvora_app;          -- so Flyway-created tables are RLS-forced
   ```
4. Create two services from this repo — **backend** (root `backend/`, it'll use the Dockerfile) and
   **frontend** (root `frontend/`, it'll use its Dockerfile).
5. Set environment variables (Railway → each service → Variables):

   **Backend:**
   | Key | Value |
   |-----|-------|
   | `SPRING_PROFILES_ACTIVE` | `staging` |
   | `DB_HOST` | (Postgres service's host) |
   | `DB_PORT` | `5432` |
   | `DB_NAME` | `railway` |
   | `DB_USERNAME` | `calyvora_app` |
   | `DB_PASSWORD` | the password you chose above |

   **Frontend** (set these as **build-time** variables — the proxy URL is baked at build):
   | Key | Value |
   |-----|-------|
   | `NEXT_PUBLIC_API_MODE` | `live` |
   | `BACKEND_ORIGIN` | your backend's public URL, e.g. `https://calyvora-backend.up.railway.app` |

6. Deploy. Seed the same way as Option A (`curl -X POST <backend-url>/api/v1/dev/seed-demo`).

---

## Option C — Hostinger VPS (cheapest, most manual)

Only Hostinger's **KVM VPS** plans work (not shared/web hosting — those can't run Java). You get an
Ubuntu box and install everything yourself: PostgreSQL, Java 21, Node, and Nginx as the front door.
This is the most control and lowest cost, but you manage OS updates, backups and TLS. Ask me for the
step-by-step VPS script when you want to go this route — Render/Railway are the better first deploy.

---

## Environment variable reference

The backend reads everything from env vars (base config in `application.yml`):

| Key | Meaning | Example |
|-----|---------|---------|
| `SPRING_PROFILES_ACTIVE` | `staging` (test, seeding ON) or `prod` (live, seeding OFF) | `staging` |
| `DB_URL` | Full JDBC URL — set this **instead of** the three below (needed for Neon, add `?sslmode=require`) | `jdbc:postgresql://ep-x.neon.tech/calyvora?sslmode=require` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | DB location if not using `DB_URL` | `...`/`5432`/`calyvora` |
| `DB_USERNAME` / `DB_PASSWORD` | DB login (must be NOSUPERUSER) | |
| `REQUIRE_TENANT_ISOLATION` | Safety check on/off. Leave `true`. | `true` |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_FROM` | SMTP for invite/verification email. Optional — invites work without it; set later for real email. | |

The frontend reads (at **build** time): `NEXT_PUBLIC_API_MODE=live` and `BACKEND_ORIGIN=<backend URL>`.

---

## Going from "test" to "real customers"

When you're done testing and want a real production instance:
1. Switch the backend to `SPRING_PROFILES_ACTIVE=prod` (this disables the demo-seed endpoints and Swagger).
2. Upgrade off the free plans (no sleeping, database won't expire).
3. Configure a real SMTP provider (`MAIL_*`) so invitation/verification emails actually send.
4. Point your custom domain at the frontend (Render/Railway both have a "Custom Domain" setting with
   free HTTPS).

---

## Troubleshooting

- **Backend crashes with `TENANT ISOLATION IS UNSAFE`** → your DB role is a superuser. Use Render/Neon,
  or do the Railway role fix above.
- **Frontend loads but every action fails / "Network error"** → `BACKEND_ORIGIN` is wrong or was set
  *after* the build. Fix it and **rebuild** the frontend.
- **Backend can't connect to DB** → check `DB_HOST/PORT/NAME/USERNAME/PASSWORD`; for Neon/managed DBs
  reachable over the public internet, use `DB_URL` with `?sslmode=require`.
- **Everything is slow on the first click** → free-tier services were asleep; the first request wakes
  them (~30–60s). Upgrade to starter to keep them awake.
