# HR module inventory & roadmap

The scope of the standalone HR product on **`product/hr-platform`**, benchmarked against the leading
HR suites — Keka, Zoho People, BambooHR and Darwinbox. Use this to see what's shipped and what's next.

> Sources for the benchmark: Keka (integrated ATS, onboarding, attendance/timesheets, payroll &
> compliance, 360/OKR performance, shifts, helpdesk, expenses, loans), Zoho People (time/shift, LMS
> add-on, onboarding, performance), BambooHR (core HR, payroll, time tracking, reporting).

## ✅ Shipped

| Module | What we have | Backend |
|---|---|---|
| **Core HR / directory** | Employee profiles, org chart, departments, skills, **paged + searchable directory (scales to 1,000+)** | `people` |
| **Onboarding** | Per-employee onboarding checklists with defaults | `people` (onboarding) |
| **Attendance** | Daily attendance, team day sheet, month grid, derived week-offs/holidays | `people` (attendance) |
| **Leave / time off** | Requests → manager approval, balances, leave calendar | `people` (leave) |
| **Holidays** | Company holiday calendar; fills attendance automatically | `people` (holiday) |
| **Payroll** | Compensation history, hikes, **configurable payslip template** (earnings/deductions with payroll validation) | `people` (compensation, payslip template) |
| **Employee self-service** | Me hub: own attendance, leave, goals, **payslips**, expenses | `people` / `me/*` |
| **Performance** | Review cycles (self-assessment → manager rating → approval → **hike into payroll**), goals/OKRs | `performance` |
| **Recruitment / ATS** ⭐ | Job openings + candidate **hiring pipeline** board (applied→hired) | `recruit` |
| **Shift scheduling / rostering** ⭐ | Shift templates + **weekly roster grid**, one shift per employee per day (upsert) | `shift` |
| **Expenses & claims** | Submit → approve → reimburse, categories, pipeline totals | `expense` |
| **Documents** | Letter templates, merge fields, issued letters (frozen) | `document` |
| **Inbox / notifications** | Leave/review/expense routing to the right person | `notification` |
| **People analytics** | Headcount growth, ratings, leave, expenses — real charts | `analytics` |
| **Subscription billing** | Per-employee, per-month (₹100/emp/mo), metered on headcount | `billing` |
| **Admin** | Members/invitations, roles (RBAC), company settings, auth (RS256), tenant isolation (RLS) | `identity`, `invitation`, `company` |

## 🔜 Roadmap (highest value first, from the competitor benchmark)

1. **HR Helpdesk / case management** — employees raise HR queries; HR resolves with SLAs (Keka helpdesk).
2. **Offboarding / exit** — resignation, notice period, clearance checklist, full-and-final.
3. **Payroll compliance (India)** — PF / ESI / PT / TDS computations and statutory reports.
4. **Asset management** — assign laptops/devices to employees, track returns.
5. **Timesheets** — project/task time, billable vs non-billable (Keka project timesheets).
6. **Learning & Development (LMS)** — courses, assignments, completion tracking (Zoho add-on).
7. **Surveys & engagement** — pulse surveys, eNPS.
8. **Rewards & recognition** — kudos, points, badges.
9. **Advanced attendance** — geofencing, biometric/kiosk, mobile punch-in.
10. **Resume parsing & offer workflows** — deepen the ATS toward Keka parity.

## Notes

- Everything is multi-tenant with **row-level security** and role-based access, so a company signs up,
  creates its people, and its data is isolated — the sellable, deploy-to-any-domain shape.
- Non-HR modules from the parent product (Work, Knowledge, Clients, Feed) exist in the codebase but are
  **unlinked from the nav** on this branch, so it deploys HR-only.
