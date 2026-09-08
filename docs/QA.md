# Testing the deployed application

The unit tests check logic in isolation. This checks the thing customers touch: a real deployment,
over the network, with real authentication, tenant binding and database rows.

Harness lives in [`tools/qa/`](../tools/qa/README.md).

```bash
curl https://calyvora-backend.onrender.com/actuator/health/readiness   # wake it first

node tools/qa/sweep.mjs                                        # read-only, all roles
PLATFORM_OWNER_PASSWORD='…' node tools/qa/features.mjs         # functional, all modules
```

---

## Latest run — 8 September 2026, on Neon

**30 of 30 modules working**, and **6/6 roles clean on every read endpoint**
(`sweep.mjs`: owner 72/72, admin 16/16, platform owner 8/8; HR, manager and member show only the
403s they are supposed to show).

The six modules beyond the 24 below are new, and four of them had **never run in production before
this week** — a broken V45 migration aborted every deploy for weeks, so a green test suite was saying
nothing about whether the code had ever executed anywhere real.

| Module | What was proven |
|---|---|
| My team (PD-32) | A plain MEMBER with one report leads a team; a MANAGER sees **1 of 7** people, not the company; no pay field anywhere on the payload |
| Designations | Create → rename → delete, on the company's own ladder |
| Statutory payroll (PF) | Answers, `enabled=false`, wage ceiling 15,000 — off per customer by design |
| Bank file | Preview renders; problems flagged **before** download, not by the bank |
| Plans & features | 3 plans; this company has 12/13 modules on |
| Leave policy | 5 policies; vacation **25 days — unchanged from the old hard-coded constant** |

### Three "failures" that were the harness, not the product

The first run reported 21/24. All three were stale request payloads, and each produced a 400 that
reads exactly like a broken feature:

| Reported as broken | Actually |
|---|---|
| Leave — request then approve | Harness sent `type: "CASUAL"`. There has never been a CASUAL type. Leave works; the API rejected an invalid enum with a precise field error |
| Recruitment — add candidate | Harness sent `firstName`/`lastName`; `CandidatePayload` takes a single `name` |
| Clients — add request | Harness sent `status: "OPEN"`; valid values are `REQUESTED\|IN_PROGRESS\|DELIVERED\|DECLINED` |

Each was confirmed working against production with a correct payload before the harness was changed —
the harness is not evidence until its own request shape has been checked against the DTO. This is the
failure mode the file header already warned about, which is worth noting: the warning was there and
the payloads drifted anyway.

A fourth "failure" was a bug in a **new check**: a `/ctc/i` search for leaked pay matched
`directCount` (dire-**ctC**-ount). Now matched as exact JSON keys. A check that cries wolf gets
deleted, so a false positive is not a harmless one.

### Open finding

**The platform owner account is on its built-in default password in production.** That account reads
every customer on the platform. `PlatformOwnerBootstrap` warns about exactly this at boot. Set
`PLATFORM_OWNER_PASSWORD` in the Render dashboard and restart.

---

## Previous run — 4 September 2026, on Neon

> **Superseded by the 8 September 2026 run below.** Kept because it is the last run before the
> deploy pipeline was found to be broken, and therefore the last run whose "working" meant "working in
> production" rather than "working in a build nobody shipped".

**24 of 24 modules working.** Exercised through create, update and delete, not page loads.

| Module | What was proven |
|---|---|
| Auth | Session, six roles |
| People | Department create → rename → delete; profile edit read back |
| Attendance | Check-in, check-out, day shows `PRESENT` |
| Leave | Member requests → visible to approver → approved → balance `used 2, remaining 23` |
| Payroll | Real figures — gross ₹18,333.33, net ₹15,400 — and payslips |
| Expenses | Claim → approve → `REIMBURSED` |
| Documents | 5 templates, 22 merge fields, preview renders |
| Recruitment | Job → candidate → stage → **offer letter generated** |
| Work | Project, task, sprint, board; archived |
| Knowledge | Space, page, edit, **search found the page** |
| Helpdesk | Member raises → admin comments → `RESOLVED` |
| Feed | Post, comment, react, pin, delete |
| Performance | Cycle created, **6 reviews auto-generated** |
| Shifts, Clients, Notifications | Create, assign, delete, mark read |
| AI assistant | Answered from real data |
| Agency console | 2 companies, spend ₹2,598 |
| Platform console | 8 companies, pricing, trials, agencies |
| Trial signup | `received=true, emailSent=true` — mail is live |

Role separation held on every probe: members refused payroll, billing, clients, exits and the platform
console. Tenant isolation verified at the database — `0` rows until a tenant is bound.

### Read sweep, same day

```
OWNER   ava.chen      72/72   ADMIN   marcus.reed   16/16
HR      leo.martins   15/16   MANAGER tom.becker    11/16
MEMBER  priya.nair    11/16   PLATFORM owner         8/8
```

Byte-for-byte identical before and after the Neon migration. The 403s are correct role boundaries,
except two that are product decisions — see `GO-LIVE.md`.

---

## Three defects only this kind of testing finds

Worth recording, because each was invisible to the unit suite:

**Dev endpoints open to the world.** `/api/v1/dev/seed-all`, `/mailbox`, `/mail-status` and
`/test-email` answer `200` with no credentials. Nothing in the code is wrong — the `@Profile("!prod")`
guard is correct, and the *deployment* never enabled the prod profile. Only a request to the real
deployment reveals it.

**A `429` on a first-ever login.** Reproduced 7 times out of 7, then traced by response header:

```
x-render-routing: hibernate-rate-limited
```

The host hibernates an idle free service and rate-limits how often it may be woken. It fires on the
**first** request after idleness, per service, not per client. Two earlier diagnoses were wrong — it is
not a per-IP limiter reacting to volume, and it is not the client retry feeding it. The header settled
it; guessing had not.

**JWT signing keys rotating on every restart.** Three key ids across one day of testing —
`dev-642afb83…`, `dev-7b1c202e…`, `dev-28ea91f8…` — each restart invalidating every session. Found by
decoding tokens from separate runs, which no single-run test can do.

---

## The rule this harness taught

**A `400 Validation failed` from a wrong payload is indistinguishable from a broken feature.**

Three modules were briefly recorded as broken before the DTOs were checked: the leave type had to be
one of four names, a candidate has a single `name` field rather than first/last, and the client-request
status enum has no `OPEN`. All three passed once the payload matched the record.

The harness now builds request bodies from the DTO definitions. **If a feature fails, read the DTO
before believing the failure.**

---

## Pacing, and why the scripts sleep

Both scripts sleep between requests. A fast burst against a hibernating service returns `429` for
everything and tells you nothing about the application. Wake the service, then run.

## What a functional run leaves behind

Some objects have no delete endpoint: an approved leave request, an attendance record, a reimbursed
expense claim, a resolved ticket, a performance cycle with generated reviews, an archived project and
space, and a trial request in the platform console. Notifications are marked read.

**Run against the demo company, never a customer workspace.**
