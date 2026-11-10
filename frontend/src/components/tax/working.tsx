"use client";

import { useState } from "react";
import { ChevronDown, Info } from "lucide-react";
import type { TaxComputation, TaxDeductionRow, TaxMonthRow } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { money } from "@/lib/format";
import { cn } from "@/lib/utils";
import { Meter } from "@/components/tax/bits";

const GROUP_LABEL: Record<string, string> = {
  SEC_123: "Section 123 (80C) — investments",
  NPS_EXTRA: "Section 124(3) (80CCD(1B)) — extra NPS",
  EMPLOYER_NPS: "Section 124(2) (80CCD(2)) — employer's NPS",
  HEALTH_SELF: "Section 126 (80D) — your family",
  HEALTH_PARENTS: "Section 126 (80D) — your parents",
  FIRST_HOME_LOAN: "Section 130 (80EE)",
  AFFORDABLE_HOME_LOAN: "Section 131 (80EEA)",
  EV_LOAN: "Section 132 (80EEB)",
};

const SOURCE: Record<TaxMonthRow["source"], { label: string; bar: string; chip: string }> = {
  LOCKED: { label: "Paid", bar: "bg-violet", chip: "bg-violet/10 text-violet" },
  PROJECTED: { label: "Expected", bar: "bg-violet/35", chip: "bg-fg/5 text-fg/55" },
  OPENING: { label: "Before Orbit", bar: "bg-sky-500/60", chip: "bg-sky-500/10 text-sky-700 dark:text-sky-300" },
  NONE: { label: "—", bar: "bg-fg/10", chip: "text-fg/30" },
};

function monthName(m: string) {
  const [y, mm] = m.split("-").map(Number);
  return new Date(y, mm - 1, 1).toLocaleDateString("en-IN", { month: "short", year: "2-digit" });
}

/** "Sec 123 (80C)" and "Sec 124(1) (80CCD(1))" share the ₹1,50,000; everything else has its own. */
function inSection123Pool(d: TaxDeductionRow) {
  return d.key === "EPF" || /^Sec 123\b/.test(d.section) || /^Sec 124\(1\)/.test(d.section);
}

/**
 * The whole computation, in the order the Act applies it — and built to answer "why", not just "how
 * much": every capped line opens to show the ceiling it hit, every month says whether it is paid or
 * expected, and the salary is shown head by head, month by month, so a mid-year raise is visible from
 * the month it took effect. One component for the employee and for HR, so neither sees a different
 * working from the other.
 */
export function TaxWorking({ c }: { c: TaxComputation }) {
  const paidHere = c.months.filter((m) => m.source === "LOCKED").reduce((n, m) => n + (m.tds ?? 0), 0);
  const expected = c.months.filter((m) => m.source === "PROJECTED").reduce((n, m) => n + (m.tds ?? 0), 0);
  const paidSoFar = c.deductedSoFar;
  const old = c.regime === "OLD";

  return (
    <div className="space-y-4">
      {/* ---- the six numbers everything below explains ---- */}
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <Stat label="Taxable income" value={money(c.taxableIncome)} />
        <Stat label="Tax on income" value={money(c.taxOnIncome - c.rebate)} hint={c.rebate > 0 ? `after ${money(c.rebate)} rebate` : undefined} />
        <Stat label="Surcharge and cess" value={money(c.surcharge + c.cess)} />
        <Stat label="Tax for the year" value={money(c.totalTax)} strong />
        <Stat label="Deducted so far" value={money(paidSoFar)} />
        <Stat label="Still to deduct" value={money(c.remainingTax)} hint={c.remainingTax > 0 ? `${money(c.projectedNextMonth)} next payslip` : undefined} />
      </div>
      {c.totalTax > 0 && (
        <div>
          <div className="flex h-2 w-full overflow-hidden rounded-full bg-fg/10">
            {c.priorTds > 0 && <div className="bg-sky-500/60" style={{ width: `${(Math.min(c.priorTds, c.totalTax) / c.totalTax) * 100}%` }} />}
            <div className="bg-violet" style={{ width: `${(Math.min(paidHere, c.totalTax) / c.totalTax) * 100}%` }} />
          </div>
          <p className="mt-1 text-xs text-fg/50">{Math.round((Math.min(paidSoFar, c.totalTax) / c.totalTax) * 100)}% of the year&apos;s tax is already paid.</p>
        </div>
      )}

      {/* ---- what this is counting ---- */}
      <div className="flex flex-wrap gap-2 text-xs">
        <span className="rounded-full bg-violet/10 px-3 py-1 font-medium text-violet">{old ? "Old regime" : "New regime (Section 202)"}</span>
        <span className={cn("rounded-full px-3 py-1", c.proofsDue ? "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300" : "bg-amber-500/10 text-amber-700 dark:text-amber-300")}>
          {c.proofsDue
            ? "Counting the amounts HR approved — the proof deadline has passed"
            : "Counting the amounts declared — HR's approval matters once the proof deadline passes"}
        </span>
      </div>

      {c.months.length > 0 && <TdsByMonth c={c} paidHere={paidHere} expected={expected} />}

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardTitle>From salary to taxable income</CardTitle>
          <dl className="mt-3 space-y-1.5 text-sm">
            <Line label="Salary for the year" value={money(c.grossSalary)} />
            {c.priorIncome > 0 && <Line label="… of which before Orbit or from a previous employer" value={money(c.priorIncome)} muted />}
            {c.exemptions.length > 0 && <Group title="Less: allowances exempt (Schedule III, formerly section 10)" />}
            {c.exemptions.map((e) => (
              <Line key={e.key} label={e.label} sub={e.section} value={`− ${money(e.allowed)}`}
                note={e.allowed < e.declared ? `of ${money(e.declared)}` : undefined} />
            ))}
            <Group title="Less: Section 19 (formerly 16)" />
            <Line label="Standard deduction" sub="19 (16(ia))" value={`− ${money(c.standardDeduction)}`} />
            {c.professionalTax > 0 && <Line label="Professional tax" sub="19 (16(iii))" value={`− ${money(c.professionalTax)}`} />}
            <Line label="Income from salary" value={money(c.salaryIncome)} strong />
            {c.houseProperty !== 0 && <Line label="House property" sub="Sec 22 (24(b))" value={c.houseProperty < 0 ? `− ${money(-c.houseProperty)}` : money(c.houseProperty)} />}
            {c.interestMovedToHouse > 0 && (
              <p className="flex gap-1.5 rounded-lg bg-emerald-500/10 px-2.5 py-1.5 text-xs text-emerald-800 dark:text-emerald-300">
                <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                <span>{money(c.interestMovedToHouse)} of the home-loan interest you put under Section 130/131 is counted here instead — Section 22 allows up to ₹2,00,000 for a home you live in, more than 130/131 do. Same interest, less tax.</span>
              </p>
            )}
            {c.otherIncome > 0 && <Line label="Other income" value={`+ ${money(c.otherIncome)}`} />}
            <Line label="Gross total income" value={money(c.grossTotalIncome)} strong />
            <Line label="Less: deductions (right)" value={`− ${money(c.totalDeductions)}`} />
            <Line label="Taxable income" sub="rounded to ₹10 (Section 516)" value={money(c.taxableIncome)} strong />
          </dl>
        </Card>

        <Card>
          <CardTitle>Deductions</CardTitle>
          {c.deductions.length === 0 ? (
            <p className="mt-3 text-sm text-fg/50">{old ? "Nothing claimed yet." : "The new regime allows almost none — only the employer's NPS and the Agniveer fund."}</p>
          ) : <>
            <DeductionTable title="₹1,50,000 limit — Section 123 (80C, 80CCC, 80CCD(1))" rows={c.deductions.filter(inSection123Pool)} regime={c.regime} />
            <DeductionTable title="Other deductions" rows={c.deductions.filter((d) => !inSection123Pool(d))} regime={c.regime} />
            <div className="mt-3 flex justify-between border-t border-fg/10 pt-2 text-sm font-semibold">
              <span>Total deductions</span><span className="tabular-nums">{money(c.totalDeductions)}</span>
            </div>
          </>}
          {c.groups.length > 0 && (
            <div className="mt-4 flex flex-col gap-3 border-t border-fg/10 pt-4">
              {c.groups.map((g) => <Meter key={g.group} used={g.allowed} cap={g.cap} label={GROUP_LABEL[g.group] ?? g.group} />)}
            </div>
          )}
        </Card>
      </div>

      <Card>
        <CardTitle>Tax on {money(c.taxableIncome)}</CardTitle>
        <p className="mt-1 text-xs text-fg/50">Each rate applies only to the slice of income inside its band — not to all of it.</p>
        <div className="mt-3 space-y-1.5">
          {c.bands.map((b, i) => {
            const width = c.taxableIncome > 0 ? (b.taxable / c.taxableIncome) * 100 : 0;
            return (
              <div key={i} className="grid grid-cols-[9rem_1fr_6rem] items-center gap-3 text-sm sm:grid-cols-[13rem_1fr_7rem]">
                <span className="tabular-nums text-fg/60">{b.ratePercent}% · {money(b.from)}{b.to == null ? "+" : ` – ${money(b.to)}`}</span>
                <div className="h-2 overflow-hidden rounded-full bg-fg/5"><div className="h-full rounded-full bg-violet/60" style={{ width: `${width}%` }} /></div>
                <span className="text-right tabular-nums">{money(b.tax)}</span>
              </div>
            );
          })}
        </div>
        <dl className="mt-4 space-y-1.5 border-t border-fg/10 pt-3 text-sm">
          <Line label="Tax on income" value={money(c.taxOnIncome)} />
          {c.rebate > 0 && <Line label="Rebate" sub="Sec 156 (87A)" value={`− ${money(c.rebate)}`} />}
          {c.surcharge > 0 && <Line label="Surcharge" value={`+ ${money(c.surcharge)}`} />}
          <Line label="Health and education cess" sub="4% of tax and surcharge" value={`+ ${money(c.cess)}`} />
          <Line label="Tax for the year" sub="to the rupee, as on Form 130" value={money(c.totalTax)} strong />
        </dl>
      </Card>

      {c.months.some((m) => m.heads.length > 0) && <SalaryGrid c={c} />}

      {old && c.months.some((m) => m.pt > 0) && (
        <Card>
          <CardTitle>Professional tax, month by month</CardTitle>
          <p className="mt-1 text-xs text-fg/50">Deducted from salary under Section 19 (formerly 16(iii)), up to ₹2,500 a year.</p>
          <MonthStrip months={c.months} value={(m) => m.pt} />
        </Card>
      )}

      {c.hraMonths.length > 0 && c.hraMonths.some((m) => m.exempt > 0) && (
        <Card>
          <CardTitle>HRA, month by month</CardTitle>
          <p className="mt-1 text-xs text-fg/50">Each month the exemption is the least of the three — worked out afresh every month, so a raise or a move changes it from that month on.</p>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead className="text-left text-xs text-fg/40">
                <tr><th className="py-1 font-medium">Month</th><th className="py-1 text-right font-medium">(1) HRA received</th><th className="py-1 text-right font-medium">(2) 50% / 40% of basic</th><th className="py-1 text-right font-medium">(3) Rent − 10% of basic</th><th className="py-1 text-right font-medium">Exempt = least</th></tr>
              </thead>
              <tbody>
                {c.hraMonths.map((m) => {
                  const least = m.exempt;
                  return (
                    <tr key={m.month} className="border-t border-fg/5">
                      <td className="py-1.5">{monthName(m.month)}</td>
                      <Num v={m.hraReceived} hit={m.hraReceived === least} />
                      <Num v={m.percentOfBasic} hit={m.percentOfBasic === least} />
                      <Num v={m.rentLessTenPercent} hit={m.rentLessTenPercent === least} />
                      <td className="py-1.5 text-right font-medium tabular-nums">{money(m.exempt)}</td>
                    </tr>
                  );
                })}
                <tr className="border-t border-fg/10 font-semibold">
                  <td className="py-1.5" colSpan={4}>Exempt for the year</td>
                  <td className="py-1.5 text-right tabular-nums">{money(c.hraMonths.reduce((n, m) => n + m.exempt, 0))}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}

// ---- TDS by month: a chart, then the split ------------------------------------------------------

function TdsByMonth({ c, paidHere, expected }: { c: TaxComputation; paidHere: number; expected: number }) {
  const max = Math.max(1, ...c.months.map((m) => m.tds ?? 0));
  return (
    <Card>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <CardTitle>Tax deducted, month by month</CardTitle>
        <div className="flex gap-3 text-xs text-fg/55">
          <Legend className="bg-violet" label="Paid" />
          <Legend className="bg-violet/35" label="Expected" />
          {c.priorTds > 0 && <Legend className="bg-sky-500/60" label="Before Orbit" />}
        </div>
      </div>
      <div className="mt-4 flex h-36 items-end gap-1.5">
        {c.months.map((m) => {
          const v = m.tds ?? 0;
          return (
            <div key={m.month} className="group relative flex h-full flex-1 flex-col justify-end">
              <div className={cn("w-full rounded-t-md transition-all", SOURCE[m.source].bar)} style={{ height: `${Math.max(v > 0 ? 3 : 0, (v / max) * 100)}%` }} />
              <span className="pointer-events-none absolute -top-6 left-1/2 hidden -translate-x-1/2 whitespace-nowrap rounded bg-fg px-1.5 py-0.5 text-[11px] text-surface group-hover:block">
                {m.tds == null ? SOURCE[m.source].label : money(v)}
              </span>
            </div>
          );
        })}
      </div>
      <div className="mt-1 flex gap-1.5">
        {c.months.map((m) => <span key={m.month} className="flex-1 text-center text-[10px] text-fg/45">{monthName(m.month).split(" ")[0]}</span>)}
      </div>
      <div className="mt-4 grid gap-2 border-t border-fg/10 pt-3 text-sm sm:grid-cols-3">
        <Split label="Previous employer / before Orbit" value={c.priorTds} />
        <Split label="Deducted here, finalised months" value={paidHere} />
        <Split label="Expected over the months left" value={expected} />
      </div>
      <p className="mt-2 text-xs text-fg/45">Expected months spread what is left of the year&apos;s tax evenly, and are worked out again whenever salary or a declaration changes. Bonuses and arrears not yet paid are not in them.</p>
    </Card>
  );
}

function Legend({ className, label }: { className: string; label: string }) {
  return <span className="inline-flex items-center gap-1"><span className={cn("h-2.5 w-2.5 rounded-sm", className)} />{label}</span>;
}

function Split({ label, value }: { label: string; value: number }) {
  return <div><p className="text-xs text-fg/50">{label}</p><p className="font-medium tabular-nums">{money(value)}</p></div>;
}

// ---- salary, head by head, month by month --------------------------------------------------------

function SalaryGrid({ c }: { c: TaxComputation }) {
  const [open, setOpen] = useState(false);
  const names: string[] = [];
  for (const m of c.months) for (const h of m.heads) if (!names.includes(h.name)) names.push(h.name);
  const cell = (m: TaxMonthRow, name: string) => m.heads.find((h) => h.name === name)?.amount ?? 0;
  const rowTotal = (name: string) => c.months.reduce((n, m) => n + cell(m, name), 0);
  const revised = c.months.findIndex((m, i) => i > 0 && m.heads.length > 0 && c.months[i - 1].heads.length > 0
    && Math.round(m.gross) !== Math.round(c.months[i - 1].gross) && m.source === "PROJECTED" && c.months[i - 1].source === "PROJECTED");

  return (
    <Card>
      <button className="flex w-full items-center justify-between text-left" onClick={() => setOpen(!open)}>
        <div>
          <CardTitle>Salary, head by head</CardTitle>
          <p className="mt-1 text-xs text-fg/50">
            Every earning for every month of the year — paid months as paid, the rest at the salary in force that month.
            {revised > 0 && ` Salary changes from ${monthName(c.months[revised].month)}.`}
          </p>
        </div>
        <ChevronDown className={cn("h-5 w-5 shrink-0 text-fg/40 transition-transform", open && "rotate-180")} />
      </button>
      {open && (
        <div className="mt-3 overflow-x-auto">
          <table className="w-full min-w-[980px] text-xs">
            <thead>
              <tr className="text-fg/45">
                <th className="sticky left-0 bg-surface py-1 pr-3 text-left font-medium">Head</th>
                {c.months.map((m) => (
                  <th key={m.month} className="px-1.5 py-1 text-right font-medium">
                    <div>{monthName(m.month)}</div>
                    <span className={cn("mt-0.5 inline-block rounded px-1 text-[10px] font-normal", SOURCE[m.source].chip)}>{SOURCE[m.source].label}</span>
                  </th>
                ))}
                <th className="py-1 pl-2 text-right font-semibold text-fg/70">Total</th>
              </tr>
            </thead>
            <tbody>
              {names.map((name) => (
                <tr key={name} className="border-t border-fg/5">
                  <td className="sticky left-0 bg-surface py-1.5 pr-3">{name}</td>
                  {c.months.map((m) => <td key={m.month} className="px-1.5 py-1.5 text-right tabular-nums">{m.heads.length ? money(cell(m, name)) : "—"}</td>)}
                  <td className="py-1.5 pl-2 text-right font-medium tabular-nums">{money(rowTotal(name))}</td>
                </tr>
              ))}
              {c.priorIncome > 0 && (
                <tr className="border-t border-fg/5 text-fg/60">
                  <td className="sticky left-0 bg-surface py-1.5 pr-3">Before Orbit / previous employer</td>
                  <td colSpan={c.months.length} className="py-1.5 text-center text-fg/40">reported as a total</td>
                  <td className="py-1.5 pl-2 text-right tabular-nums">{money(c.priorIncome)}</td>
                </tr>
              )}
              <tr className="border-t border-fg/15 font-semibold">
                <td className="sticky left-0 bg-surface py-1.5 pr-3">Total</td>
                {c.months.map((m) => <td key={m.month} className="px-1.5 py-1.5 text-right tabular-nums">{m.heads.length ? money(m.gross) : "—"}</td>)}
                <td className="py-1.5 pl-2 text-right tabular-nums">{money(c.grossSalary)}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

function MonthStrip({ months, value }: { months: TaxMonthRow[]; value: (m: TaxMonthRow) => number }) {
  const total = months.reduce((n, m) => n + value(m), 0);
  return (
    <div className="mt-3 overflow-x-auto">
      <div className="grid min-w-[760px] grid-cols-[repeat(12,minmax(0,1fr))_auto] gap-1.5 text-center text-xs">
        {months.map((m) => (
          <div key={m.month} className="rounded-lg bg-fg/[0.03] px-1 py-1.5">
            <div className="text-fg/45">{monthName(m.month).split(" ")[0]}</div>
            <div className="mt-0.5 font-medium tabular-nums">{m.source === "NONE" || m.source === "OPENING" ? "—" : money(value(m))}</div>
            <div className={cn("mt-1 inline-block rounded px-1 text-[10px]", SOURCE[m.source].chip)}>{SOURCE[m.source].label}</div>
          </div>
        ))}
        <div className="flex flex-col justify-center rounded-lg bg-violet/10 px-3 py-1.5">
          <div className="text-fg/50">Year</div>
          <div className="font-semibold tabular-nums">{money(total)}</div>
        </div>
      </div>
    </div>
  );
}

// ---- deductions, with the reason behind every capped figure ---------------------------------------

function DeductionTable({ title, rows, regime }: { title: string; rows: TaxDeductionRow[]; regime: string }) {
  if (rows.length === 0) return null;
  const showApproved = rows.some((r) => r.approved != null);
  return (
    <div className="mt-3">
      <p className="text-xs font-medium uppercase tracking-wide text-fg/45">{title}</p>
      <div className="mt-1 divide-y divide-fg/5">
        <div className="grid grid-cols-[1fr_5.5rem_5.5rem] gap-2 py-1 text-[11px] text-fg/40 sm:grid-cols-[1fr_6rem_6rem_6rem]">
          <span>Item</span><span className="text-right">Claimed</span>
          {showApproved && <span className="hidden text-right sm:block">Approved</span>}
          {!showApproved && <span className="hidden sm:block" />}
          <span className="text-right">Allowed</span>
        </div>
        {rows.map((d) => <DeductionLine key={d.key} d={d} showApproved={showApproved} regime={regime} />)}
      </div>
    </div>
  );
}

function DeductionLine({ d, showApproved, regime }: { d: TaxDeductionRow; showApproved: boolean; regime: string }) {
  const [open, setOpen] = useState(false);
  const claimed = d.claimed ?? d.declared;
  const reduced = d.allowed < d.declared || (d.movedToHouse ?? 0) > 0;
  return (
    <div className="py-1.5 text-sm">
      <div className="grid grid-cols-[1fr_5.5rem_5.5rem] items-baseline gap-2 sm:grid-cols-[1fr_6rem_6rem_6rem]">
        <span className="min-w-0">
          {d.label}<span className="ml-1 text-xs text-fg/40">{d.section.replace(/^Sec /, "")}</span>
          {reduced && (
            <button onClick={() => setOpen(!open)} className="ml-1.5 text-xs text-violet hover:underline">{open ? "hide" : "why?"}</button>
          )}
        </span>
        <span className="text-right tabular-nums text-fg/60">{money(claimed)}</span>
        {showApproved
          ? <span className="hidden text-right tabular-nums text-fg/60 sm:block">{d.approved == null ? "—" : money(d.approved)}</span>
          : <span className="hidden sm:block" />}
        <span className={cn("text-right font-medium tabular-nums", d.allowed === 0 && "text-fg/40")}>{money(d.allowed)}</span>
      </div>
      {open && <Why d={d} regime={regime} />}
    </div>
  );
}

function Why({ d, regime }: { d: TaxDeductionRow; regime: string }) {
  const moved = d.movedToHouse ?? 0;
  if (moved > 0) {
    return (
      <div className="mt-1.5 rounded-lg bg-emerald-500/10 p-2.5 text-xs text-emerald-800 dark:text-emerald-300">
        {money(moved)} of this interest is counted under Section 22 (formerly 24(b)) instead, which allows up to ₹2,00,000 on a home you live in.
        {d.declared - moved > 0 ? ` The other ${money(d.declared - moved)} stays here, and ${money(d.allowed)} of it is allowed.` : " Nothing is left to claim here — and nothing is lost."}
      </div>
    );
  }
  if (d.allowed === 0 && regime === "NEW") {
    return <div className="mt-1.5 rounded-lg bg-fg/[0.04] p-2.5 text-xs text-fg/60">The new regime (Section 202) does not allow this deduction. It would count under the old regime.</div>;
  }
  if (d.limit != null && d.usedBefore != null) {
    const remaining = Math.max(0, d.limit - d.usedBefore);
    return (
      <div className="mt-1.5 grid gap-x-6 gap-y-0.5 rounded-lg bg-fg/[0.04] p-2.5 text-xs sm:grid-cols-2">
        <Kv k="Limit for the section" v={money(d.limit)} />
        <Kv k="Used by the lines above" v={money(d.usedBefore)} />
        <Kv k="Left for this line" v={money(remaining)} />
        <Kv k={d.approved != null ? "Counted (approved)" : "Counted (declared)"} v={money(d.declared)} />
        <p className="col-span-full mt-1 font-medium">Allowed = the smaller of what is left and what is counted = {money(d.allowed)}</p>
      </div>
    );
  }
  return <div className="mt-1.5 rounded-lg bg-fg/[0.04] p-2.5 text-xs text-fg/60">Allowed up to the limit the Act sets for this deduction: {money(d.allowed)} of {money(d.declared)}.</div>;
}

function Kv({ k, v }: { k: string; v: string }) {
  return <div className="flex justify-between gap-3"><span className="text-fg/55">{k}</span><span className="tabular-nums">{v}</span></div>;
}

// ---- bits ---------------------------------------------------------------------------------------

function Stat({ label, value, hint, strong }: { label: string; value: string; hint?: string; strong?: boolean }) {
  return (
    <Card className={cn("p-4", strong && "border-violet/40 bg-violet/[0.06]")}>
      <p className="text-xs text-fg/50">{label}</p>
      <p className={cn("mt-1 text-lg font-semibold tabular-nums", strong && "text-violet")}>{value}</p>
      {hint && <p className="mt-0.5 text-[11px] text-fg/45">{hint}</p>}
    </Card>
  );
}

function Num({ v, hit }: { v: number; hit: boolean }) {
  return <td className={cn("py-1.5 text-right tabular-nums", hit ? "font-medium text-violet" : "text-fg/55")}>{money(v)}</td>;
}

function Group({ title }: { title: string }) {
  return <p className="pt-2 text-xs font-medium uppercase tracking-wide text-fg/40">{title}</p>;
}

function Line({ label, sub, value, note, strong, muted, dim }: {
  label: string; sub?: string; value: string; note?: string; strong?: boolean; muted?: boolean; dim?: boolean;
}) {
  return (
    <div className={cn("flex items-baseline justify-between gap-3", strong && "border-t border-fg/10 pt-1.5 font-semibold", (muted || dim) && "text-fg/45")}>
      <dt className="min-w-0">{label}{sub && <span className="ml-1 text-xs font-normal text-fg/40">{sub}</span>}</dt>
      <dd className="shrink-0 text-right tabular-nums">{value}{note && <span className="ml-1 text-xs font-normal text-fg/40">{note}</span>}</dd>
    </div>
  );
}
