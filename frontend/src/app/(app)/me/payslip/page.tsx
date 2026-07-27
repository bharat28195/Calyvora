"use client";

import { useEffect, useState } from "react";
import { Loader2, Printer, TrendingUp } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Compensation, Payslip } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { useSession } from "@/hooks/useSession";
import { money as fmtMoney } from "@/lib/format";

// Company-currency formatting (Settings → Localization); per-record currency is ignored for display.
function money(n: number | null | undefined, _currency?: string) {
  return fmtMoney(n);
}

/**
 * My pay — an employee's own salary, hike history and monthly payslip. Read-only self-service; only
 * an Owner/Admin can change pay (that lives in Payroll).
 */
export default function MyPayslipPage() {
  const { me } = useSession();
  const [comp, setComp] = useState<Compensation | null>(null);
  const [slip, setSlip] = useState<Payslip | null>(null);
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7));
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.myCompensation()
      .then(setComp)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load your pay"))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    api.myPayslip(month).then(setSlip).catch(() => setSlip(null));
  }, [month]);

  const currency = comp?.currency ?? "USD";

  if (loading) return <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;

  return (
    <div>
      <div className="flex items-start justify-between gap-3 print:hidden">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">My pay</h1>
          <p className="mt-1 text-fg/50">Your salary, raises and monthly payslip.</p>
        </div>
        {slip && <Button variant="secondary" size="sm" onClick={() => window.print()}><Printer className="h-4 w-4" /> Print</Button>}
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {comp && comp.currentAnnual != null ? (
        <>
          <Card className="mt-6">
            <p className="text-xs uppercase tracking-wide text-fg/40">Current salary</p>
            <p className="mt-1 text-3xl font-semibold">{money(comp.currentAnnual, currency)}<span className="text-base font-normal text-fg/40">/yr</span></p>
            <p className="text-sm text-fg/50">{money(comp.currentMonthly, currency)}/mo{comp.effectiveDate ? ` · since ${comp.effectiveDate}` : ""}</p>
          </Card>

          {comp.history.length > 1 && (
            <Card className="mt-4">
              <CardTitle>Compensation history</CardTitle>
              <div className="mt-2 flex flex-col divide-y divide-fg/5">
                {comp.history.map((h) => (
                  <div key={h.id} className="flex items-center justify-between gap-3 py-2">
                    <div className="min-w-0">
                      <p className="text-sm font-medium">{money(h.annualAmount, currency)}<span className="text-xs font-normal text-fg/40">/yr</span></p>
                      <p className="truncate text-xs text-fg/40">{h.effectiveDate}{h.reason ? ` · ${h.reason}` : ""}</p>
                    </div>
                    {h.hikePercent != null ? (
                      <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs font-medium text-emerald-400">
                        <TrendingUp className="h-3 w-3" /> +{h.hikePercent}%
                      </span>
                    ) : (
                      <span className="shrink-0 rounded-full bg-fg/10 px-2 py-0.5 text-xs text-fg/50">{h.changeType.toLowerCase()}</span>
                    )}
                  </div>
                ))}
              </div>
            </Card>
          )}

          <div className="payslip-sheet mt-4">
          {/* Printed-payslip header — only appears on paper, gives the sheet a real document identity. */}
          <div className="payslip-print-only mb-4 border-b border-fg/20 pb-3">
            <p className="text-lg font-semibold">{slip?.companyName || me?.company.name}</p>
            {slip?.companyAddress && <p className="text-xs">{slip.companyAddress}</p>}
            <p className="mt-1 text-sm">Payslip{slip ? ` · ${slip.month}` : ""}</p>
            <p className="mt-1 text-sm">{me?.user.firstName} {me?.user.lastName}</p>
          </div>
          <Card>
            <div className="flex items-center justify-between gap-2">
              <CardTitle>Payslip</CardTitle>
              <input type="month" value={month} onChange={(e) => setMonth(e.target.value)}
                className="rounded-md border border-fg/15 bg-fg/5 px-2 py-1 text-xs text-fg print:hidden" />
            </div>
            {slip ? (
              <>
                {slip.workingDays > 0 && (
                  <p className="mt-3 text-xs text-fg/50">
                    Attendance: <span className="font-medium text-fg/70">{slip.payableDays}</span> of {slip.workingDays} working days payable
                    {slip.lopDays > 0 && <span className="text-amber-400"> · {slip.lopDays} day{slip.lopDays === 1 ? "" : "s"} loss of pay</span>}
                  </p>
                )}
                <div className="mt-4 grid gap-4 sm:grid-cols-2">
                  <div>
                    <p className="mb-1 text-xs uppercase tracking-wide text-fg/40">Earnings</p>
                    {slip.earnings.map((l) => <Row key={l.label} label={l.label} value={money(l.amount, currency)} />)}
                    <Row label="Gross" value={money(slip.gross, currency)} strong />
                  </div>
                  <div>
                    <p className="mb-1 text-xs uppercase tracking-wide text-fg/40">Deductions</p>
                    {slip.deductions.map((l) => <Row key={l.label} label={l.label} value={money(l.amount, currency)} />)}
                    <Row label="Total deductions" value={money(slip.totalDeductions, currency)} strong />
                  </div>
                </div>
                <div className="mt-3 flex items-center justify-between border-t border-fg/10 pt-3">
                  <span className="text-sm font-medium">Net pay · {slip.month}</span>
                  <span className="text-lg font-semibold text-emerald-400">{money(slip.net, currency)}</span>
                </div>
              </>
            ) : (
              <p className="mt-3 text-sm text-fg/40">No payslip for this month.</p>
            )}
          </Card>
          </div>
        </>
      ) : !error && (
        <Card className="mt-6"><p className="text-sm text-fg/50">No salary is on record for you yet. Your HR admin sets this in Payroll.</p></Card>
      )}
    </div>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={"flex items-center justify-between py-1 text-sm " + (strong ? "mt-1 border-t border-fg/5 pt-1.5 font-medium" : "text-fg/70")}>
      <span>{label}</span><span className="tabular-nums">{value}</span>
    </div>
  );
}
