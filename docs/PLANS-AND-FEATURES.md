# Plans and features — selling a smaller product

Two things, built together on 6 September 2026:

- **Plans** — named packages of modules, each with a price. What a customer buys.
- **Per-company overrides** — turning one module on or off for one customer, whatever their plan says.
  What you promised them in the sales call.

Console: **Platform → Plans & features**.

---

## Nothing changed for anybody

Every existing customer keeps every module. Companies start with **no plan**, and a company with no
plan falls back to each feature's own default — and **every module defaults on**. A plan has to be
assigned deliberately before anybody loses a screen.

The one exception is `STATUTORY_PAYROLL`, which defaults off and always did (see
[STATUTORY-PAYROLL.md](STATUTORY-PAYROLL.md)).

---

## How a feature's state is decided

Four rules, in order. The first that applies wins.

| # | Rule | Why it is where it is |
|---|---|---|
| 1 | **The company's own override** | A promise made in a sales call outranks a table — including turning something *on* that their plan omits |
| 2 | **Their agency's override** | An agency buying twenty companies negotiates once. Setting it twenty times is how one gets missed |
| 3 | **Their plan** | Authoritative *in both directions*: a feature the plan omits is off, not merely unmentioned. That is what makes a plan mean anything |
| 4 | **The feature's default** | Every module on, so nobody who predates plans loses anything |

The console shows **which rule decided each one** — "set for them", "from their agency", "from
GROWTH", or "default". That matters more than it sounds: *"recruitment is off"* is an unanswerable
support question without it, because only one of those four is ever a mistake.

### The third state

A module has three settings per company, not two: **On**, **Off**, and **Plan**.

"Plan" clears the override and puts them back on whatever their package says. Without it there would
be no way back, and an owner would have to remember what the plan included in order to undo a change.
On the API, that is a `POST` with the feature and **no `enabled` field** — omitting it clears rather
than meaning false.

---

## The plans

Seeded, and editable in the console:

| Plan | Includes | Price |
|---|---|---|
| **Essentials** | Payroll, company feed | ₹99 |
| **Growth** | + recruitment, expenses, shifts, helpdesk, clients | ₹149 |
| **Complete** | + performance, work tracker, knowledge base, AI assistant, insights | ₹199 |

**People, attendance, leave, documents and the dashboard are in every plan and are not switchable at
all.** They are what an HR product *is*; selling a package without them would not be a smaller
product, it would be a broken one — and a switch that must never be flipped is a liability rather
than a feature.

A plan's price may be left blank, which means "charge the published price list". A plan can therefore
restrict features **without changing anybody's bill** — exactly what a grandfathered customer needs.

### Plans are retired, never deleted

There is no delete, deliberately. Companies point at plan codes, and a deleted plan would leave them
pointing at nothing — silently reverting each one to feature defaults, which means **silently giving
them the whole product**. Retiring (`active = false`) keeps existing customers on it and stops it
being assigned to anybody new.

---

## Enforcement is server-side

`FeatureGuardFilter` maps API path prefixes to features and answers **403 with the feature name** when
a company reaches for a module it does not have.

This is the part that makes plans real. Hiding a nav entry while the API stays open leaves the module
one typed URL away — the same mistake the subscription lock made before it was enforced server-side,
where cancelling a subscription changed a flag the frontend chose to respect.

**403, not 404.** Pretending the endpoint does not exist would be a lie that costs a support call. The
honest answer is that it exists and they do not have it, which is a sales conversation.

The mapping lives on the `Feature` enum itself so the guard and the catalogue cannot drift apart, and
a new endpoint under an existing prefix is covered the day it is written rather than the day somebody
remembers to annotate it.

**Never gated:** anything before a tenant is bound (login, the platform console), and people,
attendance, leave and the subscription screen. Gating those would lock somebody out of a product they
are still paying for.

---

## Where the plan is stored, and the mistake worth recording

On **`companies.plan_code`**, not on the subscription.

The first attempt put it on `subscriptions`, reasoning that seats, price and end date already live
there so a plan is the same kind of fact. **Nine tests failed immediately with a 404**, and the reason
is a good one: a subscription row is created *lazily* — `BillingService.getOrCreate` makes one the
first time billing is read — so a company that self-registered has none. A plan kept there could not
be assigned to a brand-new customer at all.

The deeper point the failure exposed: **entitlement is not a billing fact.** What a company may use
has to be answerable on every request, including for a trial that has never been invoiced.

`company_features` and `plans` also carry **no row-level security**, which is deliberate rather than
an oversight. The platform owner writes them for *other* tenants from a session bound to its own
platform company; a policy keyed on `calyvora.company_id` would make every such write invisible and
silently do nothing. They join `companies`, `users` and `subscriptions` on the un-RLS'd control
surface, and hold no personal data.

---

## Adding a feature

1. Add a constant to `Feature` with its label, description, default and API path prefix.
2. If it should be sellable, add it to the plans that include it — in the console, no deploy.
3. Tag the nav entry in `app-shell.tsx` with `feature: "YOUR_FEATURE"`.

Default it **on** unless it is something you want to trust gradually. A customer must never lose a
screen because we added a way to switch it off.
