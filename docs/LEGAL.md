# Legal pages — what exists, what you must fill in, and why

Two pages now exist and are linked from every footer on both websites:

- `website/orbit/privacy.html`
- `website/orbit/terms.html`

`calyvora.net` (the USD site) links to the same two pages on `calyvora.in` rather than carrying its
own copies. One set of policies, deliberately — two copies drift, and the day they disagree is the day
it matters.

---

## Why these are blocking, not nice-to-have

1. **You hold other people's personal data.** Salaries, bank accounts, PAN, UAN, attendance. Under
   India's Digital Personal Data Protection Act 2023 the entity holding it must be identifiable and
   must publish how it is processed and how to complain.
2. **No Indian payment gateway will activate you without them.** Razorpay, PayU and Cashfree all
   require a published privacy policy, terms, and an explicit refund/cancellation position before they
   let you accept money. This is the practical blocker, and it arrives at the worst moment — when you
   have a customer ready to pay.
3. **Your first serious customer's compliance team will ask.** A missing policy is not a small
   oversight to them; it reads as a company that has not thought about their data.

---

## Fields you must fill in

Every one is wrapped in `[[DOUBLE BRACKETS]]` in the HTML and rendered on a **yellow highlight**, so
an unfilled page is obvious at a glance rather than shipping quietly.

| Placeholder | Where | Notes |
|---|---|---|
| `[[REGISTERED ENTITY NAME]]` | both | The legal entity, not the brand. "Calyvora" is a trading name. |
| `[[REGISTERED ADDRESS]]` | both | As on the incorporation certificate. |
| `[[CIN / REGISTRATION NUMBER]]` | privacy | CIN for a company, LLPIN for an LLP. |
| `[[DATE]]` | both | The date you publish. Update it whenever the text changes. |
| `[[GRIEVANCE OFFICER NAME]]` / `[[GRIEVANCE OFFICER EMAIL]]` | privacy | Required by the DPDP Act. It can be you. It cannot be nobody. |
| `[[NN]]` retention after subscription ends | privacy §7, terms §7 | Must match in both. 30 or 60 days is normal. |
| `[[NN]]` trial-enquiry retention | privacy §7 | |
| `[[NN]]` response window for rights requests | privacy §9 | 30 days is the usual commitment. |
| `[[NN]]` payment terms | terms §6 | 15 or 30 days. |
| `[[STATE YOUR REFUND POSITION]]` | terms §6 | **The gateway blocker.** Must be a real position, not "no refunds" with nothing else. |
| `[[NN]]` liability cap period | terms §12 | 12 months of fees is the common cap. |
| `[[NN]]` breach-remedy period | terms §13 | 30 days is standard. |
| `[[CITY]]` jurisdiction | terms §15 | Where you are registered. |

**Search for `[[` across `website/orbit/` before publishing.** If it returns nothing, the pages are
ready.

---

## What these pages say that most templates get wrong

They were written against how Orbit actually behaves, not copied from a generic SaaS template. The
parts worth knowing, because you will be asked about them:

**Processor, not controller.** The privacy policy states plainly that your customer is the Data
Fiduciary and Calyvora is the Data Processor. That is what puts responsibility for employee data
where it belongs, and it is why the page tells employees to contact their own HR team rather than us —
we cannot act on their record without their employer's instruction.

**A real sub-processor list.** Neon (database, Singapore), Render (hosting, Singapore), Cloudflare
(DNS and edge), Resend (email). Four names, what each can see, and where. Enterprise buyers ask for
this list; having it already written is a small competitive advantage.

**Honest about data residency.** The data is in Singapore, and the page says so, including that this
means transfer outside India. It explicitly invites a customer who needs Indian residency to raise it
*before* subscribing. Claiming residency you do not have is the kind of thing that ends a contract.

**Security described specifically.** Row-level security enforced by PostgreSQL, TLS everywhere,
one-way password hashing, role-based access to payroll. Specifics rather than the word "bank-grade".

**Terms are honest about what Orbit is not.** It generates payslips from the structure you configure;
it does not compute or file statutory returns and it is not a payroll bureau. Writing that down
protects you from a customer who files a wrong return and looks for someone to blame.

**No uptime guarantee, stated openly.** Section 10 says there is no contractual SLA on standard
subscriptions and invites customers who need one to ask for a quote. Publishing a number you have not
built the redundancy to honour is worse than publishing none — especially while the services still
hibernate.

**What happens when a subscription lapses.** Access is suspended, nothing is deleted immediately, data
is retained for a stated window, and paying restores it exactly as it was. This matches what
`SubscriptionLockFilter` actually does, so the page and the product agree.

---

## Before you publish

1. Fill every `[[ ... ]]`.
2. **Have a lawyer read both pages once.** They are written to be accurate and readable, but they are
   not a substitute for advice on your specific entity, and §6 and §12 of the terms are the two a
   customer's counsel will negotiate.
3. Upload `privacy.html` and `terms.html` to `public_html` alongside the other pages, together with
   the updated `index.html`, `about.html`, `hr-services.html` and `live.html` — the footers changed.
4. The stylesheet cache-buster was bumped to `orbit.css?v=3` on every page, so returning visitors get
   the new CSS. If you edit `orbit.css` again, bump it again or people see half-styled pages.

---

## Still missing

- **A cookie banner.** Orbit sets only a session cookie and a theme preference — no advertising or
  third-party analytics — which is the lightest-touch case. Worth a lawyer's view on whether you need
  a banner at all; you may not.
- **A Data Processing Agreement.** Larger customers will ask for a signed DPA rather than a web page.
  Being ready with a short one turns a two-week delay into a same-day answer.
- **Refund and cancellation as its own page.** Some gateways want it separately linked rather than as a
  clause inside the terms. Check what yours requires when you apply.
