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

### Set the JWT signing keys (do this before you demo)

`render.yaml` marks three backend variables `sync: false`, meaning **you** set them in the dashboard —
a private key must never be committed. Until they're set the app generates a throwaway keypair at
boot, so **every restart logs all users out** (and free-tier services restart whenever they wake from
idle — mid-demo).

Generate a keypair, then flatten each PEM to a single line (the app strips the armor and ignores
whitespace, so one line pastes cleanly into a dashboard field):

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
tr -d '\r\n' < jwt-private.pem   # -> JWT_PRIVATE_KEY
tr -d '\r\n' < jwt-public.pem    # -> JWT_PUBLIC_KEY
```

In Render → `calyvora-backend` → **Environment**, set:

| Key | Value |
|-----|-------|
| `JWT_KID` | any stable label, e.g. `orbit-202608` |
| `JWT_PRIVATE_KEY` | the single-line private PEM |
| `JWT_PUBLIC_KEY` | the single-line public PEM |

Save (the service redeploys). The log should then read `RS256 JWT key store ready: activeKid=<your
kid>, ... ephemeral=false`. Keep `jwt-private.pem` out of git.

The two values are easy to mix up — check before saving:

| Variable | Starts with | Length |
|----------|-------------|--------|
| `JWT_PRIVATE_KEY` | `-----BEGIN PRIVATE KEY-----` | ~1,700 chars |
| `JWT_PUBLIC_KEY` | `-----BEGIN PUBLIC KEY-----` | ~450 chars |

If they're swapped or malformed the app **does not** refuse to start — it logs an ERROR naming the
problem and falls back to a generated keypair, so a typo costs token persistence rather than uptime:

```
ERROR ... RS256 JWT key configuration is invalid, so the app is running on an EPHEMERAL keypair —
everyone is logged out on each restart until this is fixed. Cause: Invalid RSA public key for kid
'orbit-202608' ...: expected a 'PUBLIC KEY' PEM but the value is a 'PRIVATE KEY' PEM — the public
and private keys look swapped
```

**Rotating later, with no downtime:** move the current three values into `JWT_KID_PREV` /
`JWT_PRIVATE_KEY_PREV` / `JWT_PUBLIC_KEY_PREV`, put a fresh keypair in the primary three, redeploy.
Tokens signed by the old key keep verifying; drop the `_PREV` trio once they've all expired (15 min).

### Turn on real email — otherwise nobody can finish signing up

Two transports are supported and both are fully wired: **Resend** (HTTPS) and **SMTP** (your own
mailbox, e.g. Hostinger). Read the next paragraph before choosing.

**The deciding factor is the host, not the mailbox.** Hosting platforms commonly block outbound SMTP
on 25, 587 and 465 to stop spam, and Render's free instances are expected to be among them — the
connection simply times out, which is *not* a credential problem and no amount of fiddling with
STARTTLS fixes. That failure is dangerous here because a mail failure is deliberately swallowed (an
outage must never roll back a completed signup), so the symptom is silent: registration returns
success, the verification email goes nowhere, the account can never be activated. **So never assume
SMTP works — prove it with `/dev/test-email` below, which reports the connection error instead of
hiding it.**

Resend sends over HTTPS on port 443, which no host blocks, so it is the recommended choice on Render.

**Owning a mailbox does not commit you to SMTP.** The mailbox provider and the sending transport are
separate: with Resend you still send *from* `no-reply@calyvora.in`, and replies still land in your
own Hostinger inbox. Resend only carries the outbound message.

#### Option A — Resend (recommended on Render)

1. Sign up at **https://resend.com** (free tier: 3,000 emails/month).
2. **Add your domain** and paste the SPF/DKIM records it gives you into your DNS. Until the domain is
   verified you can only send to your own address — enough to test, not enough to demo.
3. Create an **API key**.
4. In Render → `calyvora-backend` → **Environment**:

| Key | Value |
|-----|-------|
| `RESEND_API_KEY` | the key from step 3 (starts `re_…`) |
| `MAIL_FROM` | an address at your verified domain, e.g. `no-reply@calyvora.in` |

That's it — no host, port or TLS flags. The startup log confirms the choice:

```
Outgoing email: provider=RESEND, from=no-reply@calyvora.in, endpoint=https://api.resend.com/emails
```

#### Option B — a Hostinger mailbox over SMTP

Use this if you'd rather send through the mailbox you already pay for, or once the app runs somewhere
that permits outbound SMTP. Create the mailbox in **hPanel → Emails** first, then in Render →
`calyvora-backend` → **Environment**:

| Key | Value |
|-----|-------|
| `MAIL_HOST` | `smtp.hostinger.com` |
| `MAIL_PORT` | `465` |
| `MAIL_USERNAME` | the full mailbox address, e.g. `no-reply@calyvora.in` |
| `MAIL_PASSWORD` | that mailbox's password |
| `MAIL_FROM` | **the same address as `MAIL_USERNAME`** |
| `MAIL_SMTP_AUTH` | `true` |
| `MAIL_SMTP_SSL` | `true` |

`MAIL_FROM` must equal `MAIL_USERNAME`: providers reject a `From:` that differs from the
authenticated mailbox ("sender denied"). Use **either** port 465 with `MAIL_SMTP_SSL=true`, **or**
port 587 with `MAIL_SMTP_STARTTLS=true` — never both flags at once. Leave `RESEND_API_KEY` unset, or
it wins the inference. The startup log confirms:

```
Outgoing email: provider=SMTP, from=no-reply@calyvora.in, endpoint=smtp.hostinger.com:465
```

Then run the test call below. **`connect timed out` means Render is blocking outbound SMTP** — the
credentials are irrelevant at that point; switch to Option A, keeping the same `MAIL_FROM`.

One structural advantage of Resend worth knowing: a shared mailbox has a low hourly send cap and a
reputation you also use for ordinary correspondence. A burst of invitations can trip that cap, and
because sends are swallowed, the invitations vanish quietly.

**Verify it in one call** (staging only, disabled in `prod`):

```bash
curl -X POST "https://calyvora-backend.onrender.com/api/v1/dev/test-email?to=you@example.com"
```

It reports the real provider error instead of hiding it, and echoes the settings in effect (never the
key or password):

```json
{"sent":true,"provider":"RESEND","error":null,
 "config":{"endpoint":"https://api.resend.com/emails","from":"no-reply@calyvora.in",
           "username":null,"auth":false,"starttls":false,"ssl":false}}
```

Common failures in the `error` field:
- `Resend returned 403: The domain is not verified` → finish step 2; DNS can take a few minutes.
- `Resend returned 401: API key is invalid` → wrong or rotated `RESEND_API_KEY`.
- `Resend returned 422: from is not valid` → `MAIL_FROM` isn't at a domain you've verified.
- `MailConnectException … connect timed out` (SMTP) → the host is blocking outbound SMTP. Not fixable
  with credentials or TLS flags; switch to Resend, keeping the same `MAIL_FROM`.
- `AuthenticationFailedException … 535` (SMTP) → wrong `MAIL_USERNAME`/`MAIL_PASSWORD`. The username
  is the **full address**, not the part before the `@`.
- `SMTPSendFailedException … sender denied` (SMTP) → `MAIL_FROM` differs from `MAIL_USERNAME`.

#### Choosing the transport explicitly

`MAIL_PROVIDER` pins it; left unset the app infers one. Order of inference: a `RESEND_API_KEY` means
Resend, otherwise a `MAIL_USERNAME` means SMTP, otherwise **console** — links are written to the log
and never delivered, and the app says so loudly at startup rather than pretending to send.

| `MAIL_PROVIDER` | Needs | Use when |
|-----------------|-------|----------|
| `resend` | `RESEND_API_KEY`, `MAIL_FROM` | Any hosted deployment — **recommended** |
| `smtp` | `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/`MAIL_PASSWORD`/`MAIL_FROM` | Your own mailbox (Hostinger, Zoho…), on a host that allows outbound SMTP |
| `console` | nothing | Local development |

**A provider named but not credentialed falls back to console**, loudly:

```
ERROR ... MAIL_PROVIDER is set to RESEND but RESEND_API_KEY is not configured, so no email can be
          sent. Falling back to the console transport. Set RESEND_API_KEY to deliver mail for real.
```

`render.yaml` therefore leaves `MAIL_PROVIDER` unset (`sync: false`) and lets inference pick: it used
to pin `resend`, which made the whole `MAIL_*` SMTP set silently inert no matter what you put in the
dashboard. Pinning a provider also used to skip inference and make *every* send throw, so the
deployment mailed nothing at all until someone went looking; it now degrades to capture-and-log.

Set `MAIL_PROVIDER` explicitly only to force a transport when both sets of credentials are present.

**Try it locally before deploying.** Put the credentials in `backend/.env.local` (gitignored) and run
against the embedded database — no Docker, no Postgres install:

```bash
cd backend
set -a && . ./.env.local && set +a
./mvnw spring-boot:run -Dspring-boot.run.profiles=embedded
```

With `RESEND_API_KEY` (or `MAIL_USERNAME`) set, the console transport stands aside so the **real**
send path runs — the same one a deployment uses. A signup against `POST /api/v1/auth/register` then
sends a genuine verification email, so you can prove the whole flow before it matters in front of a
customer. Registration also returns `{"emailSent": true|false}`, and the signup screen tells the user
the truth either way instead of always claiming "check your email".

#### Demoing verification with no mail provider at all

Every captured link is also served from **`GET /api/v1/dev/mailbox`** and rendered at
**`/dev/mailbox`** in the app, in every profile except `prod`. So the full round trip — register,
open the link, account verified — is demonstrable on a staging deployment with nothing configured.
The signup screen links to it automatically: the frontend asks the backend whether the mailbox exists
rather than inferring it from its own build, which is why it now appears on staging (a production
frontend build talking to a non-prod API) and never against a `prod` backend.

> ⚠️ The mailbox is **unauthenticated by design** — the person who needs a verification link is the
> one who cannot log in yet. Anyone who can reach a non-prod deployment can therefore read pending
> verification and invite links and act on them. That is fine for a demo tenant and is exactly why
> the bean does not exist under the `prod` profile. Run `prod` for anything holding real customer data.

> **Per-tenant sending** (each customer sending from their own mailbox) is not wired up yet, but the
> layer is built for it: `EmailSettingsResolver` chooses the mailbox per company and everything else
> — the transports, the callers — is already independent of where the settings came from.

### Load sample data (5 companies, admins, employees, payroll…)

Because this test deploy runs under the `staging` profile, seeding still works.

**Open <https://orbit.calyvora.in/demo/seed> in a browser.** It builds everything in one go — the
populated Northwind company plus the five sample companies that fill the owner console — and then
lists every login with a copy button. Reload it as often as you like: seeding only fills gaps and
never overwrites anything that already exists.

There is no button for this on the login screen any more. Preparing demo data is something the
person running the demo does deliberately, beforehand — not a one-click door on the same screen real
customers sign in through.

If you would rather do it from a terminal, the same thing over the API:

```bash
curl https://calyvora-backend.onrender.com/api/v1/dev/seed-all
```

**Logins after seeding:**

| Who | Email | Password |
|---|---|---|
| **Platform owner (you)** | `bharat28195@calyvora.in` | `Bharat@28195#` |
| Company admin | `ava.chen@northwind.demo` | `demopass123` |
| HR | `leo.martins@northwind.demo` | `demopass123` |
| Manager | `tom.becker@northwind.demo` | `demopass123` |
| Employee | `priya.nair@northwind.demo` | `demopass123` |
| Agency owner | `owner@vertexgroup.demo` | `demopass123` |

⚠ **The owner password above is the built-in default and lives in the source tree, so it is not a
secret.** It exists so a fresh deployment is usable immediately. Set `PLATFORM_OWNER_PASSWORD` on any
deployment that holds real customer data — that account reads every company on the platform, and the
app warns at startup for as long as the default is in use.

> Changing `PLATFORM_OWNER_EMAIL` on a deployment that already has an owner **moves** the existing
> account rather than creating a second one, resetting its password to the configured value. The old
> address stops working. That reconciliation is what carried the account over from the previous
> `ownerorbit@calyvora.in`.

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

6. Deploy. Seed the same way as Option A — `<frontend-url>/demo/seed` in a browser, or
   `curl <backend-url>/api/v1/dev/seed-all`.

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
| `FRONTEND_BASE_URL` | Where invite / verification email links point. **Must** be the public frontend url — defaults to `localhost:3000`. | `https://calyvora-frontend.onrender.com` |
| `CORS_ALLOWED_ORIGINS` | Origins allowed to call the API. Same value as above. | `https://calyvora-frontend.onrender.com` |
| `JWT_KID` / `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | RS256 signing keys. Unset ⇒ ephemeral keypair ⇒ everyone logged out on each restart. See the section above. | |
| `JWT_KID_PREV` / `JWT_PRIVATE_KEY_PREV` / `JWT_PUBLIC_KEY_PREV` | Optional retired key, kept trusted for verification during a rotation. | |
| `MAIL_HOST` / `MAIL_PORT` | SMTP server. Unset ⇒ `localhost:1025` ⇒ **no mail is ever sent** (failure is swallowed). | `smtp.hostinger.com` / `587` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Mailbox login. Username must be the full address. | |
| `MAIL_FROM` | `From:` header — must equal `MAIL_USERNAME`. | `no-reply@yourdomain.com` |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` / `MAIL_SMTP_SSL` | `true`/`true`/`false` for port 587; `true`/`false`/`true` for port 465. | |
| `MAIL_TIMEOUT_MS` | SMTP connect/read/write timeout. Keeps a dead mail host from stalling signup. | `10000` |
| `TRIAL_NOTIFY_EMAIL` | Who gets the "someone wants a free trial" email. Unset ⇒ the platform owner's login address. | `connect@calyvora.in` |
| `OPEN_REGISTRATION` | `true` reopens public self-signup at `/auth/register`. **Leave unset.** With it off (the default), a workspace only exists because you approved a trial request. | `false` |

The frontend reads (at **build** time): `NEXT_PUBLIC_API_MODE=live` and `BACKEND_ORIGIN=<backend URL>`.

---

## Your own domain (`orbit.calyvora.in`)

`render.yaml` already declares the domain, so a blueprint sync registers it and provisions the TLS
certificate — no dashboard clicking. **Render cannot create the DNS record for you**, and that is the
one step that makes it actually resolve:

1. **Hostinger → hPanel → Domains → `calyvora.in` → DNS / Nameservers** (the DNS zone editor).
2. Click **Add record** and fill in:

   | Field | Value |
   |-------|-------|
   | Type | `CNAME` |
   | Name | `orbit` — just the word, **not** `orbit.calyvora.in`. Hostinger appends the domain itself; typing the full name creates `orbit.calyvora.in.calyvora.in`, which is the single most common mistake here. |
   | Points to / Target | `calyvora-frontend.onrender.com` (no `https://`, no trailing slash) |
   | TTL | leave the default (usually 14400) |

3. Save. Then in Render → `calyvora-frontend` → **Settings → Custom Domains**, add
   `orbit.calyvora.in` if the blueprint sync hasn't already, and wait for **Verified** — usually a few
   minutes, occasionally up to an hour. Render issues the TLS certificate automatically once it
   verifies; until then the address will show a certificate warning, which is expected and resolves
   itself.

To check from your own machine rather than guessing:

```bash
nslookup orbit.calyvora.in
# should answer with calyvora-frontend.onrender.com
```

The service **keeps its `onrender.com` url as well**, so nothing breaks while DNS propagates.

### A note on `app.calyvora.in`

`FRONTEND_BASE_URL` pointed at `app.calyvora.in` until 2026-08-15, **but that DNS record was never
created** — `app.calyvora.in` returns NXDOMAIN. Every invitation, verification and trial-approval link
sent during that time therefore pointed at a hostname that does not resolve, and clicking one gave a
browser error rather than the app.

Nothing needs preserving, so the name is gone from `render.yaml` and from the CORS list. If anyone
still holds one of those broken links, re-send the invitation rather than trying to rescue the URL.

**The lesson worth keeping:** `FRONTEND_BASE_URL` is the one setting whose breakage is completely
invisible from inside the app. The service is healthy, every page loads, tests pass — and yet nobody
can act on an email. After changing it, resolve it before trusting it:

```bash
nslookup orbit.calyvora.in
```

**The one thing to get the right way round:** `FRONTEND_BASE_URL` (backend) is stamped into every
invitation and verification link. It is now `https://orbit.calyvora.in`, so **if you deploy before the
DNS record exists, emailed links will point at a hostname that doesn't resolve yet.** The app itself
is unaffected — it still serves on `onrender.com`. If you need to deploy first, set
`FRONTEND_BASE_URL` back to `https://calyvora-frontend.onrender.com` until DNS is live.

The marketing site's Sign in / Start free trial buttons already point at `https://orbit.calyvora.in`.

### Where the two halves live

| | Host | Why |
|---|------|-----|
| `calyvora.in` (marketing site) | Hostinger shared hosting | Static files, uploaded by hand into `public_html`. No build step — **and no git deploy: pushing does not update it.** |
| `orbit.calyvora.in` (the product) | Render | Needs Java, Node and Postgres. Shared hosting cannot run it. |

### Updating the marketing site

Everything in `website/orbit/` goes into `public_html`, keeping the same filenames:

| File | |
|---|---|
| `index.html` | the home page |
| `hr-services.html` | Priority HR Services |
| `about.html` | the group, the story and the straight answers (PD-19) |
| `orbit.css` | shared stylesheet for all three |
| `renu-rao.jpg` | the director's photo — a **placeholder**, replace with the real one |
| `favicon.svg`, `favicon.ico`, `apple-touch-icon.png` | the tab icon |

Upload via Hostinger's **File Manager** (hPanel → Files → File Manager → `public_html` → Upload) or
FTP. Overwrite what's there. Nothing is generated, so what you upload is exactly what is served.

**Upload `orbit.css` whenever you upload a page** — the pages share it, and a new page with an old
stylesheet renders unstyled sections rather than failing visibly.

---

## Going from "test" to "real customers"

When you're done testing and want a real production instance:
1. Switch the backend to `SPRING_PROFILES_ACTIVE=prod` (this disables the demo-seed endpoints and Swagger).
2. Upgrade off the free plans (no sleeping, database won't expire).
3. Set `RESEND_API_KEY` + `MAIL_FROM` so invitation/verification emails actually send (see the email
   section above — **do not** use SMTP on Render).
4. Point `orbit.calyvora.in` at the frontend (see the section above; `render.yaml` already declares it).

---

## What can and cannot stop the app from starting

Config mistakes shouldn't turn into outages, so most bad settings degrade loudly instead of killing
the boot. Only two things are fatal, and both are cases where running on would be worse than being
down:

| Problem | Behaviour |
|---------|-----------|
| Database unreachable / migration fails | **Fatal** — there is no app without its data |
| DB role bypasses Row-Level Security | **Fatal** — booting anyway would let one company read another's data |
| JWT keys missing, malformed, or swapped | Degrades: ERROR in the log + generated keypair; logins work, tokens don't survive restarts |
| SMTP unset, wrong host, bad password | Degrades: send failures are logged; signup and invites still succeed |

So a mistyped secret costs you a warning in the log, not a dead service. Check the startup log after
any deploy for `ephemeral=false` and no `[TENANT ISOLATION]` error.

## Troubleshooting

- **Backend crashes with `TENANT ISOLATION IS UNSAFE`** → your DB role is a superuser. Use Render/Neon,
  or do the Railway role fix above.
- **Frontend loads but every action fails / "Network error"** → `BACKEND_ORIGIN` is wrong or was set
  *after* the build. Fix it and **rebuild** the frontend.
- **`/api/...` returns a bare 500 "Internal Server Error" from the frontend** (but the same call works
  against the backend url directly) → the Next.js proxy can't reach `BACKEND_ORIGIN`. On Render, don't
  use `fromService: { property: host }` for this — that yields the *private* network name, which
  speaks plain http on port 8080 and isn't reachable as `https://<name>`. Set `BACKEND_ORIGIN` to the
  backend's **public** url (`https://calyvora-backend.onrender.com`) and **rebuild** the frontend;
  the rewrite is baked in at build time, so a restart alone won't pick it up.
- **Backend can't connect to DB** → check `DB_HOST/PORT/NAME/USERNAME/PASSWORD`; for Neon/managed DBs
  reachable over the public internet, use `DB_URL` with `?sslmode=require`.
- **`UnknownHostException: dpg-xxxxxxxx-a` on Render** → the database and the backend are in
  **different regions**. Render's private database hostname only resolves inside its own region, so a
  Singapore service cannot see an Oregon database. `render.yaml` now pins the database to `singapore`
  too, but **a database's region cannot be changed after it is created** — delete the existing
  `calyvora-db` in the Render dashboard (Settings → Delete), then re-sync the blueprint (Blueprint →
  Manual Sync) so it is recreated in the right region. Check the region badge on all three resources
  afterwards; they must be identical.
- **Everything is slow on the first click** → free-tier services were asleep; the first request wakes
  them (~30–60s). Upgrade to starter to keep them awake.
- **Signup succeeds but no verification email arrives** → SMTP isn't configured, and the failure is
  swallowed on purpose (see the email section above). Run
  `curl -X POST "<backend>/api/v1/dev/test-email?to=you@example.com"` — it returns the real provider
  error. Also check the backend log for `Failed to send email`.
