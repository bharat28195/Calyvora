"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, ArrowLeft, CheckCircle2 } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { PayrollRun } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";
import { money } from "@/lib/format";

/** HR payroll run — every employee's net for a month, after attendance LOP. "Publish" makes payslips
 *  available to employees (they're computed on demand, so this confirms the run). */
export default function PayrollRunPage() {
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const [run, setRun] = useState<PayrollRun | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [published, setPublished] = useState(false);

  useEffect(() => {
    setRun(null); setPublished(false);
    api.payrollRun(month).then(setRun).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load"));
  }, [month]);

  return (
    <div>
      <Link href="/payroll" className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg"><ArrowLeft className="h-4 w-4" /> Payroll</Link>
      <div className="mt-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Payroll run</h1>
          <p className="mt-1 text-fg/50">Net pay for the month, after attendance loss-of-pay.</p>
        </div>
        <input type="month" value={month} onChange={(e) => setMonth(e.target.value)}
          className="h-9 rounded-lg border border-fg/15 bg-fg/5 px-2 text-sm text-fg" />
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {run === null ? (
        <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <>
          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Kpi label="Employees" value={String(run.employees)} />
            <Kpi label="Gross" value={money(run.totalGross)} />
            <Kpi label="Net payout" value={money(run.totalNet)} accent />
            <Kpi label="LOP days" value={String(run.totalLopDays)} />
          </div>

          <Card className="mt-6 overflow-x-auto p-0">
            <table className="w-full min-w-[640px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-fg/10 text-left text-xs uppercase tracking-wide text-fg/40">
                  <th className="px-5 py-3 font-medium">Employee</th>
                  <th className="px-3 py-3 font-medium text-right">Gross</th>
                  <th className="px-3 py-3 font-medium text-right">LOP</th>
                  <th className="px-5 py-3 font-medium text-right">Net</th>
                </tr>
              </thead>
              <tbody>
                {run.rows.length === 0 ? (
                  <tr><td colSpan={4} className="px-5 py-8 text-center text-fg/50">No salaries on record for this month.</td></tr>
                ) : run.rows.map((r) => (
                  <tr key={r.employeeId} className="border-b border-fg/5 last:border-0">
                    <td className="px-5 py-3">
                      <p className="font-medium">{r.name}</p>
                      {r.jobTitle && <p className="text-xs text-fg/40">{r.jobTitle}</p>}
                    </td>
                    <td className="px-3 py-3 text-right tabular-nums text-fg/70">{money(r.gross)}</td>
                    <td className="px-3 py-3 text-right tabular-nums">{r.lopDays > 0 ? <span className="text-amber-400">{r.lopDays}</span> : <span className="text-fg/30">—</span>}</td>
                    <td className="px-5 py-3 text-right tabular-nums font-semibold text-emerald-400">{money(r.net)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>

          {run.rows.length > 0 && (
            <Card className="mt-4 flex flex-wrap items-center justify-between gap-3">
              <div>
                <CardTitle>Publish payslips</CardTitle>
                <p className="mt-1 text-sm text-fg/60">Make {run.month} payslips available to employees under My pay.</p>
              </div>
              {published ? (
                <span className="inline-flex items-center gap-1.5 text-sm font-medium text-emerald-400"><CheckCircle2 className="h-4 w-4" /> Published</span>
              ) : (
                <Button onClick={() => setPublished(true)}>Publish payslips</Button>
              )}
            </Card>
          )}
        </>
      )}
    </div>
  );
}

function Kpi({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <Card className="py-4">
      <p className="text-xs text-fg/50">{label}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${accent ? "text-emerald-400" : ""}`}>{value}</p>
    </Card>
  );
}
