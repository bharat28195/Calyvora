"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, ArrowLeft, CheckCircle2, Lock, LockOpen } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { PayrollJob, PayrollMonthStatus, PayrollRun } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";
import { BankFilePanel } from "@/components/payroll/bank-file";
import { money } from "@/lib/format";

/**
 * HR payroll run — every employee's net for a month, after attendance LOP. Finalising the month locks
 * it: the payslips are stored as issued and never change afterwards, and statutory returns and later
 * months' income tax are built on them.
 */
export default function PayrollRunPage() {
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7));
  const [run, setRun] = useState<PayrollRun | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [lock, setLock] = useState<PayrollMonthStatus | null>(null);
  const [locking, setLocking] = useState(false);
  const [confirming, setConfirming] = useState(false);
  // Bumped after finalising or reopening, so the run reloads from (or stops reading) the locked copy.
  const [reload, setReload] = useState(0);
  // How long the current run has been going, so a big company sees a clock rather than a spinner
  // that might as well be hung.
  const [elapsed, setElapsed] = useState(0);

  // Start the run in the background and poll for it. The old single GET held the request open for
  // the whole computation; at a thousand people that is seconds, and past what a host will wait.
  useEffect(() => {
    let cancelled = false;
    let timer: number | undefined;
    setRun(null); setLock(null); setConfirming(false); setError(null); setElapsed(0);
    api.payrollMonthStatus(month).then((s) => { if (!cancelled) setLock(s); }).catch(() => {});
    const startedAt = Date.now();
    const tick = window.setInterval(() => setElapsed(Math.floor((Date.now() - startedAt) / 1000)), 1000);

    const settle = (job: PayrollJob) => {
      if (cancelled) return;
      if (job.status === "DONE" && job.result) {
        setRun(job.result);
      } else if (job.status === "FAILED") {
        setError(job.error ?? "The payroll run failed.");
      } else {
        timer = window.setTimeout(() => api.payrollJob(job.jobId).then(settle).catch(fail), 1000);
      }
    };
    const fail = (e: unknown) => {
      if (!cancelled) setError(e instanceof ApiError ? e.message : "Failed to load");
    };
    api.startPayrollRun(month).then(settle).catch(fail);

    return () => {
      cancelled = true;
      window.clearInterval(tick);
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [month, reload]);

  async function finalizeMonth() {
    setLocking(true);
    setError(null);
    try {
      setLock(await api.finalizePayrollMonth(month));
      setConfirming(false);
      setReload((n) => n + 1);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not finalise the month");
    } finally {
      setLocking(false);
    }
  }

  async function reopenMonth() {
    setLocking(true);
    setError(null);
    try {
      setLock(await api.reopenPayrollMonth(month));
      setReload((n) => n + 1);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not reopen the month");
    } finally {
      setLocking(false);
    }
  }

  // Whether to show the statutory columns at all. Driven by the run's own numbers rather than by a
  // separate call for the feature flag: if nothing was contributed, an empty PF column is noise, and
  // a company with the feature on but nobody enrolled is in exactly that position.
  const hasPf = (run?.rows ?? []).some((r) => r.employeePf > 0);
  const hasEsi = (run?.rows ?? []).some((r) => (r.employeeEsi ?? 0) > 0);
  const hasPt = (run?.rows ?? []).some((r) => (r.professionalTax ?? 0) > 0);
  const columns = 4 + (hasPf ? 1 : 0) + (hasEsi ? 1 : 0) + (hasPt ? 1 : 0);

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
        !error && (
          <div className="mt-16 flex flex-col items-center gap-2 text-sm text-fg/50">
            <Loader2 className="h-6 w-6 animate-spin text-violet" />
            <p>Computing pay for {month}…{elapsed >= 3 && ` ${elapsed}s`}</p>
          </div>
        )
      ) : (
        <>
          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Kpi label="Employees" value={String(run.employees)} />
            <Kpi label="Gross" value={money(run.totalGross)} />
            <Kpi label="Net payout" value={money(run.totalNet)} accent />
            <Kpi label="LOP days" value={String(run.totalLopDays)} />
          </div>

          {/* Only when statutory payroll is on for this company. A zero here would read as a bug
              rather than as "not applicable", which is what it would actually mean. */}
          {run.totalEmployerContribution > 0 && (
            <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
              <Kpi label="Employer PF + ESI" value={money(run.totalEmployerContribution)} />
              <Kpi label="Total cost" value={money(run.totalGross + run.totalEmployerContribution)} />
            </div>
          )}

          <Card className="mt-6 overflow-x-auto p-0">
            <table className="w-full min-w-[640px] border-collapse text-sm">
              <thead>
                <tr className="border-b border-fg/10 text-left text-xs uppercase tracking-wide text-fg/40">
                  <th className="px-5 py-3 font-medium">Employee</th>
                  <th className="px-3 py-3 font-medium text-right">Gross</th>
                  <th className="px-3 py-3 font-medium text-right">LOP</th>
                  {hasPf && <th className="px-3 py-3 font-medium text-right">PF</th>}
                  {hasEsi && <th className="px-3 py-3 font-medium text-right">ESI</th>}
                  {hasPt && <th className="px-3 py-3 font-medium text-right">Prof. tax</th>}
                  <th className="px-5 py-3 font-medium text-right">Net</th>
                </tr>
              </thead>
              <tbody>
                {run.rows.length === 0 ? (
                  <tr><td colSpan={columns} className="px-5 py-8 text-center text-fg/50">No salaries on record for this month.</td></tr>
                ) : run.rows.map((r) => (
                  <tr key={r.employeeId} className="border-b border-fg/5 last:border-0">
                    <td className="px-5 py-3">
                      <p className="font-medium">{r.name}</p>
                      {r.jobTitle && <p className="text-xs text-fg/40">{r.jobTitle}</p>}
                    </td>
                    <td className="px-3 py-3 text-right tabular-nums text-fg/70">{money(r.gross)}</td>
                    <td className="px-3 py-3 text-right tabular-nums">{r.lopDays > 0 ? <span className="text-amber-400">{r.lopDays}</span> : <span className="text-fg/30">—</span>}</td>
                    {hasPf && (
                      <td className="px-3 py-3 text-right tabular-nums text-fg/70">
                        {r.employeePf > 0 ? money(r.employeePf) : <span className="text-fg/30">—</span>}
                      </td>
                    )}
                    {hasEsi && (
                      <td className="px-3 py-3 text-right tabular-nums text-fg/70">
                        {(r.employeeEsi ?? 0) > 0 ? money(r.employeeEsi) : <span className="text-fg/30">—</span>}
                      </td>
                    )}
                    {hasPt && (
                      <td className="px-3 py-3 text-right tabular-nums text-fg/70">
                        {(r.professionalTax ?? 0) > 0 ? money(r.professionalTax) : <span className="text-fg/30">—</span>}
                      </td>
                    )}
                    <td className="px-5 py-3 text-right tabular-nums font-semibold text-emerald-400">{money(r.net)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>

          {run.rows.length > 0 && <BankFilePanel month={run.month} currency={run.currency} />}

          {run.rows.length > 0 && lock && (
            <Card className="mt-4">
              {lock.finalized ? (
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <CardTitle className="flex items-center gap-2">
                      <Lock className="h-4 w-4 text-emerald-400" /> {run.month} is finalised
                    </CardTitle>
                    <p className="mt-1 text-sm text-fg/60">
                      Payslips are stored exactly as issued
                      {lock.finalizedAt && <> on {new Date(lock.finalizedAt).toLocaleDateString()}</>}. Salary
                      or attendance changes from now on do not alter them.
                    </p>
                  </div>
                  <Button variant="secondary" onClick={reopenMonth} disabled={locking}>
                    {locking ? <Loader2 className="h-4 w-4 animate-spin" /> : <LockOpen className="h-4 w-4" />}
                    Reopen
                  </Button>
                </div>
              ) : (
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <CardTitle>Finalise {run.month}</CardTitle>
                    <p className="mt-1 text-sm text-fg/60">
                      Locks these figures. Payslips are stored as issued, PF/ESI/TDS returns are built on
                      them, and later months&apos; income tax counts what was withheld here.
                    </p>
                  </div>
                  {confirming ? (
                    <div className="flex gap-2">
                      <Button onClick={finalizeMonth} disabled={locking}>
                        {locking ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />}
                        Yes, finalise
                      </Button>
                      <Button variant="secondary" onClick={() => setConfirming(false)}>Cancel</Button>
                    </div>
                  ) : (
                    <Button onClick={() => setConfirming(true)}><Lock className="h-4 w-4" /> Finalise month</Button>
                  )}
                </div>
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
