"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, Printer } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Form130 } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { Form130Sheet } from "@/components/tax/form130-sheet";
import { recentTaxYears, taxYearOf, yearQuery } from "@/lib/tax-years";

/**
 * One person's Form 130 (formerly Form 16), for any recent tax year — the employee's own, or anyone's
 * for HR with ?employee=. The form itself is {@link Form130Sheet}.
 *
 * <p>Part A — tax deducted and deposited — is officially downloaded from TRACES; Orbit's copy is built
 * from payroll and the challans HR recorded, so the two can be checked against each other.
 */
export default function Form130Page() {
  return <Suspense fallback={null}><Form130View /></Suspense>;
}

function Form130View() {
  const params = useSearchParams();
  const router = useRouter();
  const employee = params.get("employee");
  const year = params.get("year") ?? taxYearOf();
  const [f, setF] = useState<Form130 | null>(null);
  const [part, setPart] = useState<"A" | "B" | "AB">("AB");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setF(null);
    setError(null);
    (employee ? api.employeeForm130(employee, year) : api.myForm130(year))
      .then(setF)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load Form 130"));
  }, [employee, year]);

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 print:hidden">
        <Link href={employee ? `/finance/tax/manage/${employee}` : "/finance/tax"} className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg">
          <ArrowLeft className="h-4 w-4" /> Back
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          <select value={year} onChange={(e) => router.replace(`/finance/tax/form130${yearQuery(e.target.value, { employee })}`)}
            aria-label="Tax year" className="rounded-lg border border-fg/15 bg-fg/5 px-2 py-1.5 text-sm text-fg">
            {recentTaxYears(4).map((y) => <option key={y} value={y} className="bg-surface">Tax year {y}</option>)}
          </select>
          <div className="flex rounded-lg bg-fg/5 p-0.5 text-sm">
            {([["AB", "Both parts"], ["A", "Part A"], ["B", "Part B"]] as const).map(([k, l]) => (
              <button key={k} onClick={() => setPart(k)} className={cn("rounded-md px-3 py-1", part === k ? "bg-surface font-medium shadow-sm" : "text-fg/60")}>{l}</button>
            ))}
          </div>
          <Button variant="secondary" size="sm" disabled={!f} onClick={() => window.print()}><Printer className="h-4 w-4" /> Print or save as PDF</Button>
        </div>
      </div>

      {error ? <Alert tone="error" className="mt-6">{error}</Alert>
        : !f ? <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
        : <Form130Sheet f={f} part={part} />}
    </div>
  );
}
