# Go-live checklist — what stands between Orbit and a paying customer

Everything here was found by testing the live deployment, not by reading the code. Ordered by what it
costs you if it goes wrong on the day a real customer signs.

Last verified: **4 September 2026**, against `calyvora-backend.onrender.com` with the database on Neon.

---

## 1. Blocking — do not take money before these are done

### 1.1 Close the dev endpoints

**Status: OPEN.** `/api/v1/dev/seed-all`, `/api/v1/dev/mailbox`, `/api/v1/dev/mail-status` and
`/api/v1/dev/test-email` all answer **200 with no credentials at all** on the live deployment. They are
guarded by `@Profile("!prod")`, and the prod profile has never been switched on, so the guard does
nothing.

What an anonymous caller can do today: seed data into your live database, and **send email from your
verified domain** — which burns your Resend quota and your domain's sending reputation. If the mail
provider were ever switched back to `CONSOLE`, `/dev/mailbox` would list password-reset codes to the
public.

**Fix:** set `SPRING_PROFILES_ACTIVE=prod` on the backend service.

**Sequence matters:** the prod profile also closes the seeding endpoint used to prepare demos. Seed
first, then flip.

### 1.2 Fix the JWT signing keys

**Status: OPEN.** The signing key is generated fresh at every boot. Three token key ids observed
across one day of testing:

```
dev-642afb83…   →   dev-7b1c202e…   →   dev-28ea91f8…
```

Each restart invalidates **every session in existence**. On a hibernating free tier that happens
constantly, and users experience it as "this app keeps logging me out". No frontend change can fix it.

**Fix:** generate one RSA keypair and set `JWT_KID`, `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` in the Render
dashboard. See `DEPLOY.md`.

### 1.3 Replace the platform-owner password

**Status: OPEN.** `PlatformOwnerBootstrap` falls back to a password that is committed in the source
tree, and that fallback is in use — it was verified by signing in with it. Anyone who reads the
repository can sign in as the account that sees **every customer on the platform**.

**Fix:** set `PLATFORM_OWNER_PASSWORD` in the Render dashboard.

### 1.4 Fill in the legal pages

**Status: WRITTEN, NEEDS YOUR DETAILS.** `website/orbit/privacy.html` and `website/orbit/terms.html`
now exist and are linked from every footer on both sites. Every field that must be filled is wrapped
in `[[DOUBLE BRACKETS]]` and highlighted in yellow on the page so it cannot be shipped by accident.

You cannot lawfully take money from a company for holding its employees' personal data without these,
and no Indian payment gateway will activate an account without a published refund position. See
`LEGAL.md` for the full list of fields and why each is required.

---

## 2. Do before the first demo

### 2.1 Stop the services hibernating

**Root cause of every "too many requests" and "server is off" report so far.** The response header
names it:

```
x-render-routing: hibernate-rate-limited
```

Render hibernates idle free services and then **rate-limits how often they can be woken**. It is per
service, not per IP, and it fires on the **first** request after idleness — so a customer opening your
demo link cold can be refused before typing anything. Measured wake times: 18s, 47s, 115s.

| Option | Cost | Effect |
|---|---|---|
| Open the site 2–3 min before a demo | free | works, but you must remember |
| UptimeRobot on both URLs, 5-min interval | free | never hibernates, so never wake-limited |
| Upgrade both services off free | ~$14/mo | the problem stops existing |

URLs to monitor:
- `https://calyvora-backend.onrender.com/actuator/health/readiness`
- `https://orbit.calyvora.in/login`

### 2.2 Turn on email verification

`REQUIRE_EMAIL_VERIFICATION` is off. That was right while mail was broken; mail now demonstrably works
(`provider: RESEND, delivers: true`, and a live trial signup returned `emailSent: true`). Until it is
on, anyone can register against any address.

### 2.3 Check the console is presentable

The platform console is the screen you would show an investor. After the Neon move it contains only
seeded demo companies — no test rows. Keep it that way: seeding is now the only thing that should
create companies there.

---

## 3. Known product gaps that will come up in a sale

Not defects — decisions. Detail and market-by-market analysis in `MARKET-GAPS.md`.

| Gap | Effect on a deal |
|---|---|
| ~~Managers cannot approve leave~~ | **Fixed 5 Sep 2026.** Managers now see and decide their own reports' leave; HR and admins still see everything. |
| HR cannot see the company members list | Strange line to draw in an HR product. |
| No PF / ESI / PT / TDS computation | **Blocks the Indian sale.** Structure exists; the rules do not. |
| No bank payment file | Someone retypes payroll into net banking. |
| No audit log | First question from a finance or security reviewer. |
| No data export | Feeds the lock-in objection. |
| No SSO or 2FA | Procurement checklist item. |

---

## 4. What is verified working

So the list above is read in proportion. A functional test on 4 September 2026 exercised **24 of 24
modules through create, update and delete** — not just page loads:

- People, departments, profile editing
- Attendance check-in/out, leave request → approve → balance across two roles
- Payroll run producing real figures, payslips
- Expenses claim → approve → reimburse
- Documents: 5 templates, 22 merge fields, offer-letter generation
- Recruitment: job → candidate → stage → offer letter
- Work, knowledge (with search), helpdesk, feed, performance (6 reviews auto-generated)
- Shifts, clients, notifications, AI assistant, search, analytics
- Agency console, platform console, public trial signup (email delivered)

Role separation held on every probe: members are refused payroll, billing, clients, exits and the
platform console. Tenant isolation is enforced by the database itself — verified directly on Neon,
`0` rows returned until a tenant is bound.

---

## 5. Order I would do it in

1. `SPRING_PROFILES_ACTIVE=prod` — closes an open door
2. JWT keys — stops logging your users out
3. `PLATFORM_OWNER_PASSWORD` — closes the second open door
4. UptimeRobot on both services — makes demos reliable, free
5. Fill in the legal pages and publish them
6. `REQUIRE_EMAIL_VERIFICATION=true`
7. Upgrade off the free tier when there is revenue to justify it

Steps 1–3 and 6 are four fields in the Render dashboard. Step 4 takes two minutes. Step 5 needs a
lawyer's eye and your registered details.
