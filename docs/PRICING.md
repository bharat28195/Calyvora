# Pricing

What Orbit charges, why those numbers, and how to change them.

Prices are **data, not code**: the platform owner edits them at `/platform/pricing` and the change is
live immediately. Nothing here requires a deploy.

---

## The published lists

There is **one list per currency** (V44). A company is billed from the list matching its
subscription's currency.

### INR — India

| Employees | Rate / employee / month |
|---|---|
| 1 – 100 | ₹149 |
| 101+ | ₹99 |

- **Monthly minimum:** ₹1,299
- **Annual:** 10 months charged (two months free)

### USD — international

| Employees | Rate / employee / month |
|---|---|
| 1 – 100 | $6 |
| 101+ | $5 |

- **Monthly minimum:** $49
- **Annual:** 10 months charged (two months free)

Tiers are **graduated** — the cheaper rate applies only to employees above the threshold, so the bill
never falls as a company grows. Charging everyone the lowest reached rate would mean the 101st hire
*reduced* the invoice.

---

## Why these numbers

Researched 2026-08-31.

### The market

**United States**, per employee per month:

| | Rate | Base / floor |
|---|---|---|
| BambooHR | ~$10 Core · $17 Pro · $25 Elite | $250/mo minimum |
| Gusto | $6 | + $49/mo base |
| Rippling | $15–50+ (modular) | + $35/mo base |

**India:**

| | Rate |
|---|---|
| Zoho People | ₹60 Essential · ₹120 Professional · ₹180 Premium |
| Keka | ₹6,999/mo up to 100 (~₹70/employee) |
| Market range | ₹48–200 |

### The decision

**Do not convert the rupee price.** ₹149 is about $1.70. Listed in the US that is roughly six times
under the cheapest credible competitor, and in B2B software a price that far below the field reads as
*not serious* rather than as a bargain — it invites the question "what is missing", and attracts the
customers who churn hardest.

So each list is priced against **its own market**:

- A 20-person US company pays **$120/mo** where BambooHR would charge **$200**. Clearly cheaper, still
  credible.
- That is ~3.5× what the same company would pay in India. Normal purchasing-power pricing, and the
  reason the two lists must be able to move independently.

**On the India price:** ₹149 sits between Zoho Professional (₹120) and Premium (₹180) — the premium
end for a brand nobody knows yet. Defensible on breadth (payroll, recruitment, performance and
helpdesk are not in Zoho's ₹60 tier). The decision was to **hold ₹149 as list and discount individual
early customers** using an agreed price, rather than lower the headline. A published price is far
easier to discount from than to raise.

---

## Per-company agreed prices

A company can be quoted its own flat rate, outside the published list entirely.

- Set it: company row → **⋯** → *Agree a custom price*
- Undo it: **⋯** → *Back to standard price list*
- The console shows `₹149/seat · agreed` on any company on one

**Why this is safe, and the property that makes it so:** setting a price marks the subscription
`customPrice`, and rate resolution checks that flag *before* it consults the tiers. So publishing a
new list can never silently re-price someone you negotiated with. Without that, every price change
would quietly restate agreed terms and the first anyone would know is an invoice that doesn't match
what was signed.

The monthly minimum is deliberately **not** applied on top of an agreed rate — that rate *is* the
terms, and adding a floor would charge more than was agreed.

Pinned by `AgreedPriceIntegrationTest`.

---

## Changing a price

1. `/platform/pricing`
2. Pick the currency (INR / USD)
3. **Edit price list** → set tiers, minimum, annual months
4. **Takes effect from** — a date. Months already invoiced keep the list in force then.
5. **Publish**

**A price change is never retroactive.** Each month is priced by the list effective then, so an
invoice already issued still reads what the customer was actually asked to pay. Getting this wrong
turns the billing page into something nobody can check, and makes a dispute unanswerable. Pinned by
`PriceListIntegrationTest.changing_the_price_does_not_rewrite_old_invoices`.

Dating a list in the past *does* re-price already-invoiced months — allowed, but only deliberately.

---

## Currency on a company

Set at creation and nowhere else, because it cannot be inferred later from an address:

- **New company** form → Currency
- **Approving a trial** → the approval terms include it

A USD company is also given `America/New_York` rather than `Asia/Kolkata` — guessing an Indian
timezone for an American customer would put every attendance record five and a half hours out on day
one.

Unset means INR, which is what every company created before there was a choice is.

---

## Known gaps

- **No payment collection.** Subscriptions are activated by hand in the console. Nothing charges a
  card, in either currency.
- **Currency cannot be changed after creation.** Deliberate for now — changing it mid-life would
  re-price historic months against a list that did not apply to them.
- **Only INR and USD.** Adding GBP/EUR is a row in `price_lists` plus an option in two dropdowns — but
  advertising a currency implies a tax jurisdiction (UK VAT, EU OSS), so decide that first.
- **The monthly-revenue figure on the console** is shown per currency, never summed. Adding rupees to
  dollars produces a number that is not revenue in anything.
