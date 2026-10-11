"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, CheckCircle2, FileText, Loader2, Plus, RotateCcw, Trash2, Wallet } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { money } from "@/lib/format";
import type { SettlementExtraLine, SettlementInput, SettlementView } from "@/lib/types";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";

/**
 * Full and final settlement (PD-73): worked out from payroll, leave, service and notice, adjusted by
 * HR, then approved — which freezes it and issues the F&F letter — and marked paid.
 */
const LEAVE_LABEL: Record<string, string> = { VACATION: "Vacation", SICK: "Sick", PERSONAL: "Personal", COMP_OFF: "Comp-off" };
const BASIS: { value: string; label: string }[] = [
  { value: "BASIC_26", label: "Basic ÷ 26 (usual)" },
  { value: "BASIC_30", label: "Basic ÷ 30" },
  { value: "GROSS_30", label: "Gross ÷ 30" },
];

export default function SettlementPage() {
  const { employeeId } = useParams<{ employeeId: string }>();
  const [view, setView] = useState<SettlementView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [lines, setLines] = useState<SettlementExtraLine[]>([]);
  const [paidOn, setPaidOn] = useState(new Date().toISOString().slice(0, 10));
  const [reference, setReference] = useState("");

  const load = useCallback(async () => {
    try {
      const v = await api.settlement(employeeId);
      setView(v);
      setLines(v.extraLines);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load the settlement.");
    }
  }, [employeeId]);
  useEffect(() => { void load(); }, [load]);

  async function run(work: () => Promise<SettlementView>) {
    setBusy(true);
    setError(null);
    try {
      const v = await work();
      setView(v);
      setLines(v.extraLines);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "That did not work.");
    } finally {
      setBusy(false);
    }
  }

  const save = (input: SettlementInput) => run(() => api.updateSettlement(employeeId, input));

  if (!view) {
    return (
      <div>
        <Back />
        {error ? <Alert tone="error" className="mt-4">{error}</Alert>
          : <div className="flex justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>}
      </div>
    );
  }

  const st = view.statement;
  const draft = view.status === "DRAFT";
  const cur = st.currency;
  const encash = new Set(view.encashTypes.split(",").filter(Boolean));

  return (
    <div>
      <Back />
      <div className="mt-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Full &amp; final — {st.employeeName}</h1>
          <p className="mt-1 text-sm text-fg/50">
            {[st.designation, st.department].filter(Boolean).join(", ")}
            {st.startDate && <> · joined {st.startDate}</>} · last day <b>{st.lastWorkingDay}</b> · {st.service} of service
          </p>
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${
          view.status === "PAID" ? "bg-emerald-500/15 text-emerald-500" : view.status === "APPROVED" ? "bg-violet/15 text-violet" : "bg-amber-500/15 text-amber-500"}`}>
          {view.status === "DRAFT" ? "Draft — recalculated live" : view.status === "APPROVED" ? "Approved" : `Paid ${view.paidOn ?? ""}`}
        </span>
      </div>

      {error && <Alert tone="error" className="mt-4">{error}</Alert>}

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_22rem]">
        {/* The statement */}
        <Card>
          <CardTitle>Statement</CardTitle>
          <Section title="Paid to them" lines={st.earnings} total={st.totalEarnings} cur={cur} />
          <Section title="Recovered / deducted" lines={st.deductions} total={st.totalDeductions} cur={cur} negative />
          <div className="mt-4 flex items-center justify-between rounded-lg bg-violet/10 px-4 py-3">
            <span className="font-semibold">{st.netPayable >= 0 ? "Net payable" : "Owed by employee"}</span>
            <span className="text-lg font-semibold tabular-nums">{money(Math.abs(st.netPayable), cur)}</span>
          </div>
          <p className="mt-2 text-xs text-fg/50">Pay by <b>{st.payBy}</b> — two working days after the last day (Code on Wages).</p>
          {st.notes.length > 0 && (
            <ul className="mt-4 list-disc space-y-1 pl-5 text-xs text-fg/55">
              {st.notes.map((n, i) => <li key={i}>{n}</li>)}
            </ul>
          )}
          <p className="mt-4 text-xs text-fg/40">
            Worked from: monthly gross {money(st.monthlyGross, cur)}, basic {money(st.monthlyBasic, cur)} on the last day
            {st.noticeRequired != null && <> · notice {st.noticeServed ?? "—"} of {st.noticeRequired} days served</>}.
          </p>
        </Card>

        {/* Choices */}
        <div className="flex flex-col gap-4">
          <Card>
            <CardTitle>Leave encashment</CardTitle>
            <div className="mt-3 flex flex-col gap-1.5 text-sm">
              {st.leave.map((l) => (
                <label key={l.type} className="flex items-center justify-between gap-2">
                  <span className="flex items-center gap-2">
                    <input type="checkbox" disabled={!draft || busy} checked={encash.has(l.type)} className="h-4 w-4 accent-violet"
                      onChange={(e) => {
                        const next = new Set(encash);
                        if (e.target.checked) next.add(l.type); else next.delete(l.type);
                        void save({ encashTypes: [...next].join(",") });
                      }} />
                    {LEAVE_LABEL[l.type] ?? l.type}
                  </span>
                  <span className="tabular-nums text-fg/60">{l.available} days</span>
                </label>
              ))}
            </div>
            <label className="mt-3 flex items-center gap-2 text-sm">
              Days to encash
              <input type="number" min={0} step={0.5} disabled={!draft || busy}
                defaultValue={view.encashDays ?? ""} placeholder="balance"
                onBlur={(e) => {
                  const v = e.target.value.trim();
                  void save(v === "" ? { clearEncashDays: true } : { encashDays: Number(v) });
                }}
                className="w-24 rounded-md border border-fg/15 bg-fg/[0.03] px-2 py-1 text-sm" />
            </label>
            <label className="mt-2 flex items-center gap-2 text-sm">
              A day&apos;s pay
              <select disabled={!draft || busy} value={view.encashBasis}
                onChange={(e) => void save({ encashBasis: e.target.value })}
                className="rounded-md border border-fg/15 bg-surface px-2 py-1 text-sm">
                {BASIS.map((b) => <option key={b.value} value={b.value}>{b.label}</option>)}
              </select>
            </label>
          </Card>

          <Card>
            <CardTitle>Notice &amp; gratuity</CardTitle>
            <label className="mt-3 flex items-center gap-2 text-sm">
              Notice required
              <input type="number" min={0} disabled={!draft || busy}
                defaultValue={view.noticeDays ?? ""} placeholder={st.noticeRequired != null ? String(st.noticeRequired) : "days"}
                onBlur={(e) => {
                  const v = e.target.value.trim();
                  void save(v === "" ? { clearNoticeDays: true } : { noticeDays: Number(v) });
                }}
                className="w-20 rounded-md border border-fg/15 bg-fg/[0.03] px-2 py-1 text-sm" /> days
            </label>
            <label className="mt-2 flex items-center gap-2 text-sm">
              <input type="checkbox" disabled={!draft || busy} checked={view.noticeWaived} className="h-4 w-4 accent-violet"
                onChange={(e) => void save({ noticeWaived: e.target.checked })} />
              Waive the notice shortfall
            </label>
            <label className="mt-2 flex items-center gap-2 text-sm">
              <input type="checkbox" disabled={!draft || busy} checked={view.includeGratuity} className="h-4 w-4 accent-violet"
                onChange={(e) => void save({ includeGratuity: e.target.checked })} />
              Pay gratuity {st.gratuityDue ? "(due)" : "(not due yet)"}
            </label>
          </Card>

          <Card>
            <CardTitle>Other lines</CardTitle>
            <p className="mt-1 text-xs text-fg/50">A bonus, an advance to recover, a laptop not returned…</p>
            <div className="mt-3 flex flex-col gap-2">
              {lines.map((l, i) => (
                <div key={i} className="flex items-center gap-2">
                  <input value={l.label} disabled={!draft} placeholder="Description"
                    onChange={(e) => setLines(lines.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))}
                    className="min-w-0 flex-1 rounded-md border border-fg/15 bg-fg/[0.03] px-2 py-1 text-sm" />
                  <input type="number" min={0} value={l.amount || ""} disabled={!draft} placeholder="Amount"
                    onChange={(e) => setLines(lines.map((x, j) => (j === i ? { ...x, amount: Number(e.target.value) } : x)))}
                    className="w-24 rounded-md border border-fg/15 bg-fg/[0.03] px-2 py-1 text-sm" />
                  <select value={l.deduction ? "d" : "e"} disabled={!draft}
                    onChange={(e) => setLines(lines.map((x, j) => (j === i ? { ...x, deduction: e.target.value === "d" } : x)))}
                    className="rounded-md border border-fg/15 bg-surface px-1 py-1 text-xs">
                    <option value="e">Pay</option>
                    <option value="d">Recover</option>
                  </select>
                  {draft && (
                    <button type="button" onClick={() => setLines(lines.filter((_, j) => j !== i))} className="text-fg/40 hover:text-red-400">
                      <Trash2 className="h-4 w-4" />
                    </button>
                  )}
                </div>
              ))}
              {draft && (
                <div className="flex gap-2">
                  <Button size="sm" variant="ghost" onClick={() => setLines([...lines, { label: "", amount: 0, deduction: false }])}>
                    <Plus className="h-4 w-4" /> Add a line
                  </Button>
                  <Button size="sm" variant="secondary" disabled={busy || JSON.stringify(lines) === JSON.stringify(view.extraLines)}
                    onClick={() => void save({ extraLines: lines })}>Save lines</Button>
                </div>
              )}
            </div>
          </Card>

          <Card>
            {view.status === "DRAFT" && (
              <>
                <p className="text-sm text-fg/60">Approving freezes these figures and issues the full &amp; final letter.</p>
                <Button className="mt-3 w-full" disabled={busy} onClick={() => void run(() => api.settlementAction(employeeId, "approve"))}>
                  {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <CheckCircle2 className="h-4 w-4" />} Approve settlement
                </Button>
              </>
            )}
            {view.status === "APPROVED" && (
              <>
                {view.documentId && (
                  <Link href={`/documents/${view.documentId}`} className="flex items-center gap-2 text-sm font-medium text-violet hover:underline">
                    <FileText className="h-4 w-4" /> The full &amp; final letter
                  </Link>
                )}
                <div className="mt-3 flex flex-col gap-2">
                  <label className="text-xs text-fg/60">Paid on
                    <Input type="date" value={paidOn} onChange={(e) => setPaidOn(e.target.value)} />
                  </label>
                  <label className="text-xs text-fg/60">Reference (optional)
                    <Input value={reference} placeholder="e.g. NEFT UTR" onChange={(e) => setReference(e.target.value)} />
                  </label>
                  <Button disabled={busy} onClick={() => void run(() => api.settlementAction(employeeId, "paid", { paidOn, reference: reference || undefined }))}>
                    <Wallet className="h-4 w-4" /> Mark as paid
                  </Button>
                  <Button variant="ghost" disabled={busy} onClick={() => void run(() => api.settlementAction(employeeId, "reopen"))}>
                    <RotateCcw className="h-4 w-4" /> Reopen to correct
                  </Button>
                </div>
              </>
            )}
            {view.status === "PAID" && (
              <div className="text-sm">
                <p className="flex items-center gap-2 font-medium text-emerald-500"><CheckCircle2 className="h-4 w-4" /> Paid on {view.paidOn}</p>
                {view.paidReference && <p className="mt-1 text-fg/60">Reference: {view.paidReference}</p>}
                {view.documentId && (
                  <Link href={`/documents/${view.documentId}`} className="mt-2 flex items-center gap-2 font-medium text-violet hover:underline">
                    <FileText className="h-4 w-4" /> The full &amp; final letter
                  </Link>
                )}
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}

function Back() {
  return (
    <Link href="/people/exits" className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg">
      <ArrowLeft className="h-4 w-4" /> Exits
    </Link>
  );
}

function Section({ title, lines, total, cur, negative }: {
  title: string; lines: SettlementView["statement"]["earnings"]; total: number; cur: string; negative?: boolean;
}) {
  return (
    <div className="mt-4">
      <p className="text-xs font-medium uppercase tracking-wide text-fg/40">{title}</p>
      {lines.length === 0 ? (
        <p className="mt-2 text-sm text-fg/40">Nothing.</p>
      ) : (
        <table className="mt-2 w-full text-sm">
          <tbody>
            {lines.map((l, i) => (
              <tr key={i} className="border-b border-fg/5 align-top">
                <td className="py-2 pr-3">
                  <span className="font-medium">{l.label}</span>
                  {l.note && <span className="block text-xs text-fg/45">{l.note}</span>}
                </td>
                <td className="whitespace-nowrap py-2 text-right tabular-nums">{negative ? "− " : ""}{money(l.amount, cur)}</td>
              </tr>
            ))}
            <tr>
              <td className="pt-2 text-right text-xs font-medium text-fg/50">Total</td>
              <td className="pt-2 text-right font-semibold tabular-nums">{negative ? "− " : ""}{money(total, cur)}</td>
            </tr>
          </tbody>
        </table>
      )}
    </div>
  );
}
