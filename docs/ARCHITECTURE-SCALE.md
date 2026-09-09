# Scale: what the numbers say, and what to do about them

_Measured against the deployed app on 9 September 2026, on Neon, with a purpose-built 1,000-person
tenant. Every figure here is a median of three runs over the network — not a local benchmark, and not
an estimate._

Reproduce with:

```bash
node tools/qa/scale.mjs --seed 200     # or 1000
node tools/qa/load.mjs  --users 100    # concurrency, a different question
```

---

## 1. The measurements

| Screen | 200 people | 1,000 people | Growth |
|---|---|---|---|
| Dashboard | 187 ms | 401 ms | sub-linear |
| `team/mine` — **on every page load** | 132 ms | 896 ms | ~7× |
| My team roster | 195 ms | 1.4 s | ~7× |
| Directory (paged, 25 rows) | 754 ms | 3.1 s | ~4× |
| Leave inbox | 300 ms | 137 ms | flat |
| Analytics | 301 ms | 698 ms | ~2× |
| Team attendance / leave / expenses | 130–210 ms | 0.6–1.4 s | ~5× |
| Attendance day sheet | 2.0 s | **17.4 s** | ~9× |
| **Payroll run** | **7.1 s** | **29.5 s** | ~4× |

**Read it this way:** at 200 seats — the size of the deal in hand — everything is under 800 ms except
payroll and the day sheet. At 1,000 the product is still usable but three screens have become
embarrassing.

Nothing here is a query-plan problem. **The indexes are right**: `employees(company_id)`,
`employees(manager_id)`, `attendance_records(company_id, on_date)` and `(employee_id, on_date)`,
`leave_requests`, `expense_claims` and `users` are all covered. The cost is the *number of round
trips*, not the cost of any one of them.

## 2. The one pattern behind almost all of it

> **The application fetches whole-company data and filters it in Java, or loops per employee and
> queries inside the loop. Both are O(headcount) in round trips.**

Three instances, in order of severity:

**Payroll run — N+1, ~13 queries per employee.** `payrollRun` called `payslip()` for each person.
Each payslip fetched that person's salary, finance row, department, user and a month of attendance —
*and* re-read the company's payslip template, currency, feature flags and PF settings, which are
identical for everyone. About 2,600 queries for 200 people.

**Attendance day sheet — the same shape.** `day()` calls `employeeService.directory()` (which builds
a DTO per employee), then re-loads employees and users it has just fetched, then reads every leave
request in the company unbounded.

**`OrgScope` — loads every employee to walk the tree.** This one is subtle because it is *fast* per
call (132 ms at 200) but the app shell requests it on **every navigation**, so it is a floor under
every screen in the product.

## 3. What has been fixed

**Payroll run: attendance batched.** `AttendanceService.monthForEveryone(month)` loads the whole
company's month in a fixed number of queries and hands each payslip its own slice.

The arithmetic was deliberately **not** reimplemented. Both paths walk the same days through the same
`resolve()` — a second copy would drift, and it would drift silently on payslips, the one document
where being quietly wrong matters most. `PayrollRunBatchingTest` asserts the run's gross, net and
loss-of-pay equal the individual payslip's, with a real absence so loss of pay is non-zero and the
batched attendance is actually exercised.

## 4. What to do next, in order

**a. Hoist the company-wide config out of `payslip()`.** Currency, payslip template, feature flags,
PF settings and company settings are per-company constants read per employee. Pass a small
per-run context. Removes ~5 queries × headcount. *Low risk, no arithmetic touched.*

**b. Batch the remaining per-employee reads in the run** — salary, finance, department, user — into
four company-wide queries. Takes the run to roughly constant query count.

**c. Fix the attendance day sheet.** Drop the redundant `directory()` call, and bound the leave query
by the month being displayed instead of reading the company's entire leave history.

**d. Make `OrgScope` a query, not a scan.** Today it loads every employee and walks the tree in
memory. A recursive CTE over `employees(manager_id)` — already indexed — answers "who is beneath this
person" in the database and returns only the ids. This matters more than its current timing suggests,
because it is on the path of every page load.

**e. Cache `team/mine` per session.** The reporting tree changes when HR edits a profile, not between
one navigation and the next. Even a 60-second cache removes the floor entirely.

## 5. Concurrency is a different problem, with a different fix

Response time under one user says nothing about a hundred. The binding constraint there is not the
query plan:

- **The connection pool is unconfigured**, so Hikari uses its default of **10**. Tomcat will happily
  accept 200 concurrent requests, and they will queue for those ten connections. Under load the
  symptom is not a slow query — it is `connection is not available, request timed out`.
- **A 512 MB free instance** is the memory ceiling for every in-memory filter described above. The
  1,000-person team roster materialises the whole company's attendance for a month; a dozen of those
  at once is the failure mode to worry about.

Set the pool explicitly, and set a `connection-timeout` so a saturated pool fails fast and visibly
rather than hanging. Sizing rule of thumb: `connections ≈ cores × 2`, and more is usually worse —
a bigger pool moves contention from the app into the database.

## 6. The constraint that decides multi-company scale

This is the important one, and it is not obvious from the code.

Tenant isolation binds the company to the **session**:

```java
select set_config('calyvora.company_id', ?, false)   // false = session scope
```

Every Row-Level Security policy reads that setting. It is correct, and it is why one customer cannot
read another's rows. But it means **the app cannot sit behind a transaction-mode connection pooler**.
Neon's pooler (PgBouncer, transaction mode) hands a different backend connection to each transaction,
so a session-scoped setting and the query relying on it can land on different connections — best case
a screen shows nothing, worst case one tenant reads another's rows. `render.yaml` already warns
against the `-pooler` hostname for exactly this reason.

The consequence: **each application instance holds its own direct connections, and Postgres connection
limits become the ceiling on how many instances can run.** That caps horizontal scaling long before
CPU does.

**The unlock is one character.** `set_config(..., true)` is *transaction*-scoped, which is compatible
with transaction pooling — but it must then be set at the start of every transaction rather than once
per borrowed connection. That is a real change to `TenantAwareDataSource` and needs its own tenant-
isolation test under a pooler before anyone trusts it. It is the single highest-leverage architectural
change available when instance count becomes the limit.

Until then the honest ceiling is: **one instance, a few hundred concurrent users, tenants limited by
database size rather than by connections.** That is comfortably enough for the first customers.

## 7. Capacity guidance

| Situation | Verdict |
|---|---|
| One 200-seat customer, ~20 concurrent | Fine today, once payroll is batched |
| One 1,000-seat customer | Fix (a)–(d) first; the day sheet and payroll are not presentable |
| Several hundred concurrent across tenants | Size the pool, leave the free tier |
| Many instances behind a pooler | Needs the transaction-scoped GUC in §6 first |

The free tier is the other half of every number above: a 512 MB instance that sleeps after 15 minutes
idle, and a database in the same region. Moving off it is the cheapest performance work available and
should happen before the first paying customer, independently of any code change here.
