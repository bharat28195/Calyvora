"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { TaxComputation } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/**
 * Why this much tax, and what comes off the next payslip.
 *
 * <p>Written to answer the question an employee actually asks, which is never "what is my tax" but
 * "why is it <em>that</em>". So it shows the sequence rather than the total: gross, what came off it,
 * the slab-by-slab working, then rebate, surcharge and cess in the order the Act applies them.
 *
 * <p>The slab table earns its space. The single most common misunderstanding about Indian income tax
 * is that crossing into a higher band taxes the whole income at that rate; showing the slice charged
 * in each band is the only explanation that survives contact with somebody who does not believe it.
 */
export default function TaxComputationPage() {
  const [data, setData] = useState<TaxComputation | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.taxComputation()
      .then(setData)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not work out your tax"));
  }, []);

  if (error) return <Alert tone="error" className="mt-6">{error}</Alert>;
  if (data === null) {
    return <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;
  }

  const monthsLeft = Math.max(0, 12 - data.monthsElapsed);

  return (
    <div>
      <Link href="/finance/tax" className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg">
        <ArrowLeft className="h-4 w-4" /> Tax declaration
      </Link>

      <div className="mt-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">How your tax is calculated</h1>
          <p className="mt-1 text-fg/50">
            Financial year {data.financialYear} · {data.regime === "NEW" ? "new" : "old"} regime
          </p>
        </div>
      </div>

      <div className="mt-6 grid gap-4 sm:grid-cols-3">
        <Tile label="Tax for the year" value={inr(data.totalTax, data.currency)} tone="text-violet" />
        <Tile label="Deducted so far" value={inr(data.deductedSoFar, data.currency)}
          hint={`${data.monthsElapsed} month${data.monthsElapsed === 1 ? "" : "s"} of the year gone`} />
        <Tile label="Next month" value={inr(data.projectedNextMonth, data.currency)}
          tone="text-sky-400"
          hint={monthsLeft > 0
            ? `${inr(data.remainingTax, data.currency)} left, over ${monthsLeft} pay run${monthsLeft === 1 ? "" : "s"}`
            : "the year is complete"} />
      </div>

      <Card className="mt-6">
        <CardTitle>From salary to taxable income</CardTitle>
        <div className="mt-4 flex flex-col gap-1 text-sm">
          <Row label="Gross salary" value={inr(data.grossSalary, data.currency)} />
          <Row label="Standard deduction" value={`− ${inr(data.standardDeduction, data.currency)}`}
            hint={data.regime === "NEW" ? "₹75,000 under the new regime" : "₹50,000 under the old regime"} />
          {data.deductions.map((d) => (
            <Row
              key={d.key}
              label={`${d.section} — ${d.label}`}
              value={`− ${inr(d.allowed, data.currency)}`}
              // When a claim is trimmed, say so here rather than silently showing the smaller number.
              hint={d.declared > d.allowed
                ? `you claimed ${inr(d.declared, data.currency)}; ${inr(d.allowed, data.currency)} is allowable`
                : undefined}
              muted={d.allowed === 0}
            />
          ))}
          <div className="mt-2 border-t border-fg/10 pt-2">
            <Row label="Taxable income" value={inr(data.taxableIncome, data.currency)} strong />
          </div>
        </div>
      </Card>

      <Card className="mt-4">
        <CardTitle>Tax, slab by slab</CardTitle>
        <p className="mt-1 text-xs text-fg/50">
          Each rate applies only to the part of your income inside that band — not to all of it.
        </p>
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[420px] text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wide text-fg/40">
                <th className="pb-2 font-medium">Band</th>
                <th className="pb-2 text-right font-medium">Rate</th>
                <th className="pb-2 text-right font-medium">Taxed</th>
                <th className="pb-2 text-right font-medium">Tax</th>
              </tr>
            </thead>
            <tbody>
              {data.bands.map((b, i) => (
                <tr key={i} className="border-t border-fg/5">
                  <td className="py-2 text-fg/70">
                    {inr(b.from, data.currency)} – {b.to === null ? "above" : inr(b.to, data.currency)}
                  </td>
                  <td className="py-2 text-right tabular-nums text-fg/70">{b.ratePercent}%</td>
                  <td className="py-2 text-right tabular-nums text-fg/70">{inr(b.taxable, data.currency)}</td>
                  <td className="py-2 text-right tabular-nums font-medium">{inr(b.tax, data.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="mt-5 flex flex-col gap-1 border-t border-fg/10 pt-4 text-sm">
          <Row label="Tax on income" value={inr(data.taxOnIncome, data.currency)} />
          {data.rebate > 0 && (
            <Row label="Rebate under section 87A" value={`− ${inr(data.rebate, data.currency)}`}
              hint={data.regime === "NEW"
                ? "up to ₹60,000, which is what makes ₹12 lakh tax-free"
                : "up to ₹12,500 where taxable income is ₹5 lakh or less"} />
          )}
          {data.surcharge > 0 && (
            <Row label="Surcharge" value={inr(data.surcharge, data.currency)}
              hint="charged on the tax itself, for higher incomes" />
          )}
          <Row label="Health and education cess (4%)" value={inr(data.cess, data.currency)} />
          <div className="mt-2 border-t border-fg/10 pt-2">
            <Row label="Total tax for the year" value={inr(data.totalTax, data.currency)} strong />
            <Row label="Spread over twelve months" value={inr(data.monthlyTds, data.currency)} muted />
          </div>
        </div>
      </Card>

      <Card className="mt-4">
        <CardTitle>The other regime</CardTitle>
        <p className="mt-1 text-xs text-fg/50">
          The same salary and the same declarations, taxed under the other set of rules.
        </p>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <Tile label="Old regime" value={inr(data.comparison.oldRegimeTax, data.currency)} />
          <Tile label="New regime" value={inr(data.comparison.newRegimeTax, data.currency)} />
        </div>
        <p className="mt-4 text-sm text-fg/70">
          {data.comparison.saving === 0
            ? "Both regimes cost you the same this year."
            : <>The <strong>{data.comparison.cheaper === "NEW" ? "new" : "old"} regime</strong> is cheaper
              for you by {inr(data.comparison.saving, data.currency)}
              {data.comparison.cheaper === data.regime ? " — which is the one you are on." : "."}</>}
        </p>
        {data.comparison.cheaper !== data.regime && data.comparison.saving > 0 && (
          <Link href="/finance/tax" className="mt-3 inline-block">
            <Button variant="secondary" size="sm">Change my regime</Button>
          </Link>
        )}
      </Card>

      <p className="mt-6 text-xs text-fg/40">
        Rates are those in force for the financial year shown. This is what your employer will
        withhold; your own return may differ if you have income or deductions your employer does not
        know about.
      </p>
    </div>
  );
}

function Tile({ label, value, tone = "", hint }: {
  label: string; value: string; tone?: string; hint?: string;
}) {
  return (
    <Card>
      <p className="text-sm text-fg/50">{label}</p>
      <p className={`mt-1 text-xl font-semibold tabular-nums ${tone}`}>{value}</p>
      {hint && <p className="mt-1 text-xs text-fg/40">{hint}</p>}
    </Card>
  );
}

function Row({ label, value, hint, strong, muted }: {
  label: string; value: string; hint?: string; strong?: boolean; muted?: boolean;
}) {
  return (
    <div className={`flex flex-wrap items-baseline justify-between gap-2 py-0.5 ${muted ? "opacity-50" : ""}`}>
      <span className={strong ? "font-medium" : "text-fg/70"}>
        {label}
        {hint && <span className="block text-xs text-fg/40">{hint}</span>}
      </span>
      <span className={`tabular-nums ${strong ? "text-base font-semibold" : ""}`}>{value}</span>
    </div>
  );
}

/** Indian digit grouping: ₹12,25,000, not ₹1,225,000. */
function inr(value: number, currency = "INR"): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency", currency, maximumFractionDigits: 0,
  }).format(value);
}
