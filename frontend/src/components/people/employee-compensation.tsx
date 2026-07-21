"use client";

import { useEffect, useState } from "react";
import { Loader2, TrendingUp, Plus, FileText } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Compensation, Payslip } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field } from "@/components/ui/field";
import { Alert } from "@/components/ui/alert";

function money(n: number | null | undefined, currency: string) {
  if (n == null) return "—";
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency, maximumFractionDigits: 0 }).format(n);
  } catch {
    return `${currency} ${n.toLocaleString()}`;
  }
}

/** Salary, hike history, and payslip for one employee (feedback C1–C3). Rendered admin-only. */
export function EmployeeCompensation({ employeeId }: { employeeId: string }) {
  const [comp, setComp] = useState<Compensation | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [amount, setAmount] = useState("");
  const [effective, setEffective] = useState(new Date().toISOString().slice(0, 10));
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [slip, setSlip] = useState<Payslip | null>(null);
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7));

  useEffect(() => {
    api.compensation(employeeId).then(setComp).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load"))
      .finally(() => setLoading(false));
  }, [employeeId]);

  const currency = comp?.currency ?? "USD";

  async function addRaise(e: React.FormEvent) {
    e.preventDefault();
    const val = Number(amount);
    if (!val || val <= 0) { setError("Enter a valid amount."); return; }
    setBusy(true); setError(null);
    try {
      setComp(await api.addCompensation(employeeId, { annualAmount: val, effectiveDate: effective, reason }));
      setAdding(false); setAmount(""); setReason("");
      if (slip) setSlip(await api.payslip(employeeId, month)); // refresh open payslip
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save");
    } finally { setBusy(false); }
  }

  async function loadPayslip(m: string) {
    setMonth(m);
    try { setSlip(await api.payslip(employeeId, m)); }
    catch (err) { setError(err instanceof ApiError ? err.message : "No salary on record yet."); }
  }

  if (loading) return <div className="flex justify-center py-4"><Loader2 className="h-5 w-5 animate-spin text-violet" /></div>;

  return (
    <div className="space-y-4">
      {error && <Alert tone="error">{error}</Alert>}

      {/* Current salary */}
      <div className="rounded-xl border border-fg/10 bg-fg/[0.02] p-4">
        <div className="flex items-end justify-between gap-3">
          <div>
            <p className="text-xs uppercase tracking-wide text-fg/40">Current salary</p>
            <p className="mt-1 text-2xl font-semibold">{money(comp?.currentAnnual, currency)}<span className="text-sm font-normal text-fg/40">/yr</span></p>
            <p className="text-sm text-fg/50">{money(comp?.currentMonthly, currency)}/mo{comp?.effectiveDate ? ` · since ${comp.effectiveDate}` : ""}</p>
          </div>
          <div className="flex gap-2">
            <Button type="button" size="sm" variant="secondary" onClick={() => loadPayslip(month)}>
              <FileText className="h-4 w-4" /> Payslip
            </Button>
            <Button type="button" size="sm" onClick={() => setAdding((v) => !v)}>
              <Plus className="h-4 w-4" /> {comp?.currentAnnual == null ? "Set salary" : "Add raise"}
            </Button>
          </div>
        </div>

        {adding && (
          <form onSubmit={addRaise} className="mt-4 grid gap-3 border-t border-fg/10 pt-4 sm:grid-cols-3">
            <Field label={`New annual salary (${currency})`} htmlFor="c-amt"><Input id="c-amt" type="number" min="0" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="e.g. 150000" autoFocus /></Field>
            <Field label="Effective date" htmlFor="c-date"><Input id="c-date" type="date" value={effective} onChange={(e) => setEffective(e.target.value)} /></Field>
            <Field label="Reason (optional)" htmlFor="c-reason"><Input id="c-reason" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Annual review" /></Field>
            <div className="sm:col-span-3 flex justify-end gap-2">
              <Button type="button" variant="ghost" size="sm" onClick={() => setAdding(false)}>Cancel</Button>
              <Button type="submit" size="sm" disabled={busy}>{busy && <Loader2 className="h-4 w-4 animate-spin" />} Save</Button>
            </div>
          </form>
        )}
      </div>

      {/* Hike history */}
      {comp && comp.history.length > 0 && (
        <div>
          <p className="mb-2 text-sm font-medium text-fg/70">Compensation history</p>
          <div className="flex flex-col divide-y divide-fg/5">
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
        </div>
      )}

      {/* Payslip */}
      {slip && (
        <div className="rounded-xl border border-fg/10 p-4">
          <div className="mb-3 flex items-center justify-between gap-2">
            <p className="text-sm font-medium">Payslip</p>
            <input type="month" value={month} onChange={(e) => loadPayslip(e.target.value)}
              className="rounded-md border border-fg/15 bg-fg/5 px-2 py-1 text-xs text-fg" />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
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
            <span className="text-sm font-medium">Net pay</span>
            <span className="text-lg font-semibold text-emerald-400">{money(slip.net, currency)}</span>
          </div>
        </div>
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
