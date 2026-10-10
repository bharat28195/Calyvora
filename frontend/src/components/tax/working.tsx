"use client";

import type { TaxComputation } from "@/lib/types";
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

function monthName(m: string) {
  const [y, mm] = m.split("-").map(Number);
  return new Date(y, mm - 1, 1).toLocaleDateString("en-IN", { month: "short", year: "2-digit" });
}

/**
 * The whole computation, in the order the Act applies it: salary, what is exempt, the standard
 * deduction, house property and other income, the deductions against their ceilings, the slabs, and
 * then rebate, surcharge and cess. One component, used by the employee, by HR and on Form 130, so
 * none of them can show a different working from the others.
 */
export function TaxWorking({ c, compact = false }: { c: TaxComputation; compact?: boolean }) {
  return (
    <div className={cn("grid gap-4", !compact && "lg:grid-cols-2")}>
      <Card>
        <CardTitle>Income</CardTitle>
        <dl className="mt-3 space-y-1.5 text-sm">
          <Line label="Salary for the year" value={money(c.grossSalary)} />
          {c.previousEmployerIncome > 0 && <Line label="… of which from your previous employer" value={money(c.previousEmployerIncome)} muted />}
          {c.exemptions.map((e) => (
            <Line key={e.key} label={`${e.label}`} sub={e.section} value={`− ${money(e.allowed)}`}
              note={e.allowed < e.declared ? `of ${money(e.declared)}` : undefined} />
          ))}
          <Line label="Standard deduction" sub="Sec 19" value={`− ${money(c.standardDeduction)}`} />
          {c.professionalTax > 0 && <Line label="Professional tax" sub="Sec 19 (16(iii))" value={`− ${money(c.professionalTax)}`} />}
          <Line label="Income from salary" value={money(c.salaryIncome)} strong />
          {c.houseProperty !== 0 && <Line label="House property" sub="Sec 22 (24(b))" value={c.houseProperty < 0 ? `− ${money(-c.houseProperty)}` : money(c.houseProperty)} />}
          {c.otherIncome > 0 && <Line label="Other income" value={`+ ${money(c.otherIncome)}`} />}
          <Line label="Gross total income" value={money(c.grossTotalIncome)} strong />
        </dl>
      </Card>

      <Card>
        <CardTitle>Deductions</CardTitle>
        {c.deductions.length === 0 ? (
          <p className="mt-3 text-sm text-fg/50">{c.regime === "NEW" ? "The new regime allows almost none — only the employer's NPS." : "Nothing claimed yet."}</p>
        ) : (
          <dl className="mt-3 space-y-1.5 text-sm">
            {c.deductions.map((d) => (
              <Line key={d.key} label={d.label} sub={d.section} value={money(d.allowed)}
                note={d.allowed < d.declared ? `of ${money(d.declared)}` : undefined} dim={d.allowed === 0} />
            ))}
            <Line label="Total deductions" value={`− ${money(c.totalDeductions)}`} strong />
          </dl>
        )}
        {c.groups.length > 0 && (
          <div className="mt-4 flex flex-col gap-3 border-t border-fg/10 pt-4">
            {c.groups.map((g) => <Meter key={g.group} used={g.allowed} cap={g.cap} label={GROUP_LABEL[g.group] ?? g.group} />)}
          </div>
        )}
      </Card>

      <Card className={cn(!compact && "lg:col-span-2")}>
        <CardTitle>Tax on {money(c.taxableIncome)}</CardTitle>
        <p className="mt-1 text-xs text-fg/50">Each rate applies only to the slice of income inside its band — not to all of it.</p>
        <table className="mt-3 w-full text-sm">
          <thead className="text-left text-xs text-fg/40">
            <tr><th className="py-1 font-medium">Band</th><th className="py-1 font-medium">Rate</th><th className="py-1 text-right font-medium">Income in band</th><th className="py-1 text-right font-medium">Tax</th></tr>
          </thead>
          <tbody>
            {c.bands.map((b, i) => (
              <tr key={i} className="border-t border-fg/5">
                <td className="py-1.5 tabular-nums">{money(b.from)} – {b.to == null ? "above" : money(b.to)}</td>
                <td className="py-1.5 tabular-nums">{b.ratePercent}%</td>
                <td className="py-1.5 text-right tabular-nums">{money(b.taxable)}</td>
                <td className="py-1.5 text-right tabular-nums">{money(b.tax)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <dl className="mt-3 space-y-1.5 border-t border-fg/10 pt-3 text-sm">
          <Line label="Tax on income" value={money(c.taxOnIncome)} />
          {c.rebate > 0 && <Line label="Rebate" sub="Sec 156 (87A)" value={`− ${money(c.rebate)}`} />}
          {c.surcharge > 0 && <Line label="Surcharge" value={`+ ${money(c.surcharge)}`} />}
          <Line label="Health and education cess, 4%" value={`+ ${money(c.cess)}`} />
          <Line label="Tax for the year" sub="rounded to ₹10" value={money(c.totalTax)} strong />
        </dl>
      </Card>

      {c.months.length > 0 && (
        <Card className={cn(!compact && "lg:col-span-2")}>
          <CardTitle>Month by month</CardTitle>
          <p className="mt-1 text-xs text-fg/50">Finalised months are what was actually paid; the rest spread what is left of the year&apos;s tax evenly.</p>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead className="text-left text-xs text-fg/40">
                <tr><th className="py-1 font-medium">Month</th><th className="py-1 font-medium">Status</th><th className="py-1 text-right font-medium">Salary</th><th className="py-1 text-right font-medium">Tax (TDS)</th></tr>
              </thead>
              <tbody>
                {c.months.map((m) => (
                  <tr key={m.month} className={cn("border-t border-fg/5", m.source === "NONE" && "text-fg/30")}>
                    <td className="py-1.5">{monthName(m.month)}</td>
                    <td className="py-1.5 text-xs text-fg/50">{{ LOCKED: "Paid", PROJECTED: "Expected", OPENING: "Before Orbit", NONE: "Not employed" }[m.source]}</td>
                    <td className="py-1.5 text-right tabular-nums">{m.source === "OPENING" ? "—" : money(m.gross)}</td>
                    <td className="py-1.5 text-right tabular-nums">{m.tds == null ? "—" : money(m.tds)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {c.hraMonths.length > 0 && c.hraMonths.some((m) => m.exempt > 0) && (
        <Card className={cn(!compact && "lg:col-span-2")}>
          <CardTitle>HRA, month by month</CardTitle>
          <p className="mt-1 text-xs text-fg/50">Each month the exemption is the least of these three.</p>
          <div className="mt-3 overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead className="text-left text-xs text-fg/40">
                <tr><th className="py-1 font-medium">Month</th><th className="py-1 text-right font-medium">HRA received</th><th className="py-1 text-right font-medium">Rent − 10% basic</th><th className="py-1 text-right font-medium">50% / 40% of basic</th><th className="py-1 text-right font-medium">Exempt</th></tr>
              </thead>
              <tbody>
                {c.hraMonths.map((m) => (
                  <tr key={m.month} className="border-t border-fg/5">
                    <td className="py-1.5">{monthName(m.month)}</td>
                    <td className="py-1.5 text-right tabular-nums">{money(m.hraReceived)}</td>
                    <td className="py-1.5 text-right tabular-nums">{money(m.rentLessTenPercent)}</td>
                    <td className="py-1.5 text-right tabular-nums">{money(m.percentOfBasic)}</td>
                    <td className="py-1.5 text-right font-medium tabular-nums">{money(m.exempt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
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
