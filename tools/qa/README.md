# QA harness

Two scripts that test the **deployed** application over HTTP. No dependencies — plain Node 18+.

```bash
# read-only: every endpoint, as six different roles
node tools/qa/sweep.mjs

# functional: creates, changes and deletes real objects in all 24 modules
PLATFORM_OWNER_PASSWORD='…' node tools/qa/features.mjs
```

| Variable | Default |
|---|---|
| `ORBIT_API` | `https://calyvora-backend.onrender.com` |
| `PLATFORM_OWNER_EMAIL` | `bharat28195@calyvora.in` |
| `PLATFORM_OWNER_PASSWORD` | *(none — set it, or the platform checks are skipped)* |

The platform-owner password is read from the environment and never committed. Demo-account passwords
(`demopass123`) are in the seed data and are not secrets.

## Why these exist alongside the unit tests

The unit tests check logic in isolation. These check the thing customers actually touch: a real
deployment, over the network, with real authentication, real tenant binding and real database rows.

**Three failures found this way could not have been found any other way:**

- Dev endpoints answering `200` to anonymous callers, because the prod profile was never switched on.
  Nothing in the code is wrong — the *deployment* is.
- A `429` on first-ever login, traced by response header to `x-render-routing: hibernate-rate-limited`
  — the host refusing to wake an idle service. Invisible to any local test.
- The JWT signing key changing on every restart, seen by decoding tokens from three separate runs.

## `sweep.mjs` — read-only

Logs in as owner, admin, HR, manager, member and platform owner, then requests every read endpoint,
resolving path ids from earlier responses. Reports only non-200s plus a per-role tally.

Use it after any deploy. A clean run means every screen still loads for every role.

Expected output on a healthy deployment — the 403s are correct role boundaries, not failures:

```
OWNER   ava.chen      72/72 ok
ADMIN   marcus.reed   16/16 ok
HR      leo.martins   15/16 ok    (company/members denied)
MANAGER tom.becker    11/16 ok    (leave, payroll, analytics, members, compensation denied)
MEMBER  priya.nair    11/16 ok    (same five)
PLATFORM owner         8/8  ok
```

## `features.mjs` — functional

Exercises each module end to end and prints `PASS`/`FAIL` per feature: department create → rename →
delete, leave requested by a member and approved by an owner with the balance checked afterwards,
expense claim → approve → reimburse, recruitment job → candidate → stage → offer letter, and so on.
It cleans up what has a delete endpoint.

**Request bodies come from the DTO records, not from guesswork.** This matters more than it sounds: a
wrong field name returns `400 Validation failed`, which is indistinguishable from a broken feature.
Three modules were briefly recorded as broken for exactly that reason before the DTOs were checked —
the leave type had to be one of four names, a candidate has a single `name` field rather than
first/last, and the client-request status enum has no `OPEN`. **If a feature fails here, read the DTO
before believing it.**

## Two things to know before running

**Pace.** Both scripts sleep between requests. That is deliberate: the free tier hibernates and the
host rate-limits wake-ups, so a fast burst against a sleeping service returns `429` for everything and
tells you nothing about the application.

**Wake the service first**, or the first calls fail on a cold start:

```bash
curl https://calyvora-backend.onrender.com/actuator/health/readiness   # wait for {"status":"UP"}
```

## What `features.mjs` leaves behind

Some objects have no delete endpoint, so a run leaves: an approved leave request, an attendance record,
a reimbursed expense claim, a resolved helpdesk ticket, a performance cycle with its generated reviews,
an archived project and knowledge space, and a trial request in the platform console. All notifications
are marked read.

Run it against the demo company (Northwind Robotics), never against a real customer's workspace.
