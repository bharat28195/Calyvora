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
          <Card>
            <div className="flex items-center justify-between gap-2 print:hidden">
              <CardTitle>Payslip</CardTitle>
              <input type="month" value={month} onChange={(e) => setMonth(e.target.value)}
                className="rounded-md border border-fg/15 bg-fg/5 px-2 py-1 text-xs text-fg" />
            </div>
            {slip ? (
              <>
                {/* Document header: the month on the left, the company's branding on the right. */}
                <div className="mt-4 flex items-start justify-between gap-6 border-b border-fg/10 pb-5">
                  <div className="min-w-0">
                    <p className="text-lg font-semibold">
                      Payslip <span className="font-normal text-fg/60">{formatMonth(slip.month)}</span>
                    </p>
                    <p className="mt-2 text-sm font-medium uppercase tracking-wide">
                      {slip.companyName || me?.company.name}
                    </p>
                    {slip.companyAddress && (
                      <p className="mt-1 max-w-xs text-xs leading-relaxed text-fg/50">{slip.companyAddress}</p>
                    )}
                  </div>
                  {slip.companyLogoUrl && (
                    // A customer's logo can live on any host, and next/image would need every one
                    // of those hosts allow-listed in next.config to optimise it — so a plain <img>
                    // is the only thing that works for an arbitrary tenant-supplied URL.
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={slip.companyLogoUrl} alt={`${slip.companyName} logo`}
                      className="max-h-14 w-auto max-w-[180px] shrink-0 object-contain" />
                  )}
                </div>

                {/* Who this is for. */}
                <p className="mt-5 text-base font-semibold uppercase tracking-wide">{slip.employeeName}</p>
                <div className="mt-3 grid gap-x-6 gap-y-4 border-b border-fg/10 pb-5 sm:grid-cols-4">
                  <Detail label="Employee number" value={slip.employeeNo} />
                  <Detail label="Date joined" value={slip.dateJoined} />
                  <Detail label="Department" value={slip.department} />
                  <Detail label="Designation" value={slip.designation} />
                  <Detail label="Payment mode" value={slip.paymentMode ? humanise(slip.paymentMode) : null} />
                  <Detail label="UAN" value={slip.uan} />
                  <Detail label="PF number" value={slip.pfNumber} />
                  <Detail label="PAN number" value={slip.panMasked} />
                </div>

                {/* Salary details — attendance is what makes the payable days differ from working days. */}
                <p className="mt-5 text-sm font-semibold uppercase tracking-wide text-fg/70">Salary details</p>
                <div className="mt-3 grid gap-x-6 gap-y-4 border-b border-fg/10 pb-5 sm:grid-cols-4">
                  <Detail label="Actual payable days" value={String(slip.payableDays)} />
                  <Detail label="Total working days" value={String(slip.workingDays)} />
                  <Detail label="Loss of pay days" value={String(slip.lopDays)} />
                  <Detail label="Days payable" value={String(slip.payableDays)} />
                </div>

                <div className="mt-5 grid gap-6 sm:grid-cols-2">
                  <div>
                    <p className="mb-1 text-xs uppercase tracking-wide text-fg/40">Earnings</p>
                    {slip.earnings.map((l) => <Row key={l.label} label={l.label} value={money(l.amount, currency)} />)}
                    <Row label="Total earnings (A)" value={money(slip.gross, currency)} strong />
                  </div>
                  <div>
                    <p className="mb-1 text-xs uppercase tracking-wide text-fg/40">Taxes &amp; deductions</p>
                    {slip.deductions.map((l) => <Row key={l.label} label={l.label} value={money(l.amount, currency)} />)}
                    <Row label="Total deductions (B)" value={money(slip.totalDeductions, currency)} strong />
                  </div>
                </div>

                <div className="mt-5 rounded-lg bg-fg/5 px-4 py-3">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-sm font-medium">Net salary payable (A − B)</span>
                    <span className="text-lg font-semibold text-emerald-400 tabular-nums">
                      {money(slip.net, currency)}
                    </span>
                  </div>
                  {slip.netInWords && (
                    <div className="mt-2 flex items-baseline justify-between gap-3 border-t border-fg/10 pt-2">
                      <span className="text-xs uppercase tracking-wide text-fg/40">Net salary in words</span>
                      <span className="text-xs text-fg/70">{slip.netInWords}</span>
                    </div>
                  )}
                </div>

                <p className="mt-4 text-[11px] italic text-fg/40">
                  All amounts are in {currency}. This is a computer-generated statement and does not
                  require a signature.
                </p>
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

function Detail({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="min-w-0">
      <p className="text-[10px] uppercase tracking-wide text-fg/40">{label}</p>
      <p className="mt-0.5 truncate text-xs text-fg/90">
        {value?.trim() ? value : <span className="text-fg/30">—</span>}
      </p>
    </div>
  );
}

/** "2026-07" -> "July 2026", the way a payslip titles itself. */
function formatMonth(month: string): string {
  const [y, m] = month.split("-").map(Number);
  if (!y || !m) return month;
  return new Date(y, m - 1, 1).toLocaleDateString(undefined, { month: "long", year: "numeric" });
}

/** BANK_TRANSFER -> "Bank transfer". */
function humanise(s: string): string {
  const lower = s.toLowerCase().replace(/_/g, " ");
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={"flex items-center justify-between py-1 text-sm " + (strong ? "mt-1 border-t border-fg/5 pt-1.5 font-medium" : "text-fg/70")}>
      <span>{label}</span><span className="tabular-nums">{value}</span>
    </div>
  );
}
