"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, FileText, Loader2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { TaxComputation } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { money } from "@/lib/format";
import { TaxWorking } from "@/components/tax/working";
import { recentTaxYears, taxYearOf, yearQuery } from "@/lib/tax-years";

/**
 * Why this much tax, and what comes off the next payslip — for this tax year or a recent one.
 *
 * <p>Written for the question people actually ask, which is never "what is my tax" but "why is it
 * <em>that</em>". So it shows the sequence, not the total — in the order the Act applies it.
 */
export default function TaxComputationPage() {
  return <Suspense fallback={null}><Computation /></Suspense>;
}

function Computation() {
  const router = useRouter();
  const year = useSearchParams().get("year") ?? taxYearOf();
  const [data, setData] = useState<TaxComputation | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setData(null);
    setError(null);
    api.taxComputation(year)
      .then(setData)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not work out your tax"));
  }, [year]);

  const q = yearQuery(year);
  return (
    <div>
      <Link href="/finance/tax" className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg">
        <ArrowLeft className="h-4 w-4" /> Tax declaration
      </Link>
      <div className="mt-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">How your tax is calculated</h1>
          <p className="mt-1 text-fg/50">
            {data ? <>Tax year {data.financialYear} · {data.regime === "NEW" ? "new" : "old"} regime</> : <>Tax year {year}</>}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <select value={year} onChange={(e) => router.replace(`/finance/tax/computation${yearQuery(e.target.value)}`)}
            aria-label="Tax year" className="rounded-lg border border-fg/15 bg-fg/5 px-2 py-2 text-sm text-fg">
            {recentTaxYears(4).map((y) => <option key={y} value={y} className="bg-surface">Tax year {y}</option>)}
          </select>
          <Link href={`/finance/tax/form124${q}`}><Button variant="secondary"><FileText className="h-4 w-4" /> Form 124</Button></Link>
          <Link href={`/finance/tax/form130${q}`}><Button variant="secondary"><FileText className="h-4 w-4" /> Form 130</Button></Link>
        </div>
      </div>

      {error ? <Alert tone="error" className="mt-6">{error}</Alert>
        : data === null ? <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
        : <>
          {!data.withheldByPayroll && (
            <Alert tone="info" className="mt-6">
              Your employer doesn&apos;t deduct income tax through Orbit yet, so none has come off your payslips here. Use this to plan.
            </Alert>
          )}
          {data.comparison.saving > 0 && (
            <Card className="mt-4">
              <p className="text-sm">
                Under the <strong>{data.regime === "NEW" ? "old" : "new"}</strong> regime your tax would be{" "}
                <strong>{money(data.regime === "NEW" ? data.comparison.oldRegimeTax : data.comparison.newRegimeTax)}</strong>
                {data.comparison.cheaper !== data.regime
                  ? <> — <span className="text-emerald-600 dark:text-emerald-400">{money(data.comparison.saving)} less</span>. You can switch on the declaration while it&apos;s open.</>
                  : <> — {money(data.comparison.saving)} more. You&apos;re on the cheaper one.</>}
              </p>
            </Card>
          )}
          <div className="mt-4"><TaxWorking c={data} /></div>
        </>}
    </div>
  );
}
