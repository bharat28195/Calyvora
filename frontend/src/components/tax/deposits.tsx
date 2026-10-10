"use client";

import { useEffect, useState } from "react";
import { Check, Loader2, Pencil, X } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { TdsDepositMonth, TdsDeposits } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Input } from "@/components/ui/input";
import { money, formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";

/**
 * Where each month's TDS went: the challan it was paid on and the 24Q receipt for the quarter. These
 * are what Form 130 Part A lists against every employee; a month without a challan shows on Part A as
 * deducted but not yet deposited.
 */
export function TdsDepositsPanel() {
  const [data, setData] = useState<TdsDeposits | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [draft, setDraft] = useState({ bsrCode: "", depositDate: "", challanSerial: "", amount: "" });
  const [receipts, setReceipts] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function apply(d: TdsDeposits) {
    setData(d);
    setReceipts(Object.fromEntries(d.quarters.map((q) => [q.quarter, q.receiptNo ?? ""])));
  }

  useEffect(() => {
    api.tdsDeposits().then(apply).catch((e) => setError(e instanceof ApiError ? e.message : "Could not load deposits"));
  }, []);

  async function run(job: () => Promise<TdsDeposits>) {
    setBusy(true); setError(null);
    try { apply(await job()); setEditing(null); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Could not save"); }
    finally { setBusy(false); }
  }

  function edit(m: TdsDepositMonth) {
    setEditing(m.month);
    setDraft({ bsrCode: m.bsrCode ?? "", depositDate: m.depositDate ?? "", challanSerial: m.challanSerial ?? "",
      amount: m.amount != null ? String(m.amount) : String(m.tdsDeducted) });
  }

  if (!data) return error ? <Alert tone="error" className="mt-4">{error}</Alert>
    : <div className="flex justify-center py-10"><Loader2 className="h-5 w-5 animate-spin text-violet" /></div>;

  const all = data.quarters.flatMap((q) => q.months).filter((m) => m.finalised && m.tdsDeducted > 0);
  const missing = all.filter((m) => !m.bsrCode).length;

  return (
    <div className="mt-4 space-y-4">
      <p className="text-sm text-fg/60">
        Record the challan each month&apos;s tax was paid on, from the bank counterfoil, and each quarter&apos;s 24Q receipt number.
        Form 130 Part A shows them against every employee. {missing > 0 && <span className="font-medium text-amber-700 dark:text-amber-300">{missing} finalised month{missing === 1 ? "" : "s"} with tax {missing === 1 ? "has" : "have"} no challan yet.</span>}
      </p>
      {error && <Alert tone="error">{error}</Alert>}

      {data.quarters.map((q) => (
        <Card key={q.quarter} className="p-0">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-fg/10 px-5 py-3">
            <p className="font-medium">{q.label} <span className="text-fg/40">· {data.financialYear}</span></p>
            <div className="flex items-center gap-2">
              <span className="text-xs text-fg/50">24Q receipt no.</span>
              <Input className="h-8 w-40 text-sm" value={receipts[q.quarter] ?? ""} placeholder="e.g. QWERTYUI"
                onChange={(e) => setReceipts((r) => ({ ...r, [q.quarter]: e.target.value.toUpperCase() }))} />
              <Button size="sm" variant="secondary" disabled={busy || (receipts[q.quarter] ?? "") === (q.receiptNo ?? "")}
                onClick={() => void run(() => api.saveTdsReceipt(q.quarter, receipts[q.quarter] ?? ""))}>Save</Button>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px] text-sm">
              <thead className="text-left text-xs uppercase tracking-wide text-fg/40">
                <tr className="border-b border-fg/5">
                  <th className="px-5 py-2 font-medium">Salary month</th>
                  <th className="px-3 py-2 text-right font-medium">TDS deducted</th>
                  <th className="px-3 py-2 font-medium">BSR code</th>
                  <th className="px-3 py-2 font-medium">Deposited on</th>
                  <th className="px-3 py-2 font-medium">Challan serial</th>
                  <th className="px-3 py-2 text-right font-medium">Amount paid</th>
                  <th className="w-24" />
                </tr>
              </thead>
              <tbody>
                {q.months.map((m) => editing === m.month ? (
                  <tr key={m.month} className="border-b border-fg/5 bg-violet/[0.04] last:border-0">
                    <td className="px-5 py-2 font-medium">{monthName(m.month)}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{money(m.tdsDeducted)}</td>
                    <td className="px-3 py-2"><Input className="h-8 w-28" inputMode="numeric" maxLength={7} value={draft.bsrCode} placeholder="7 digits"
                      onChange={(e) => setDraft({ ...draft, bsrCode: e.target.value })} /></td>
                    <td className="px-3 py-2"><Input className="h-8 w-36" type="date" value={draft.depositDate}
                      onChange={(e) => setDraft({ ...draft, depositDate: e.target.value })} /></td>
                    <td className="px-3 py-2"><Input className="h-8 w-24" inputMode="numeric" maxLength={5} value={draft.challanSerial} placeholder="5 digits"
                      onChange={(e) => setDraft({ ...draft, challanSerial: e.target.value })} /></td>
                    <td className="px-3 py-2"><Input className="h-8 w-28 text-right" inputMode="decimal" value={draft.amount}
                      onChange={(e) => setDraft({ ...draft, amount: e.target.value })} /></td>
                    <td className="px-3 py-2">
                      <div className="flex justify-end gap-1">
                        <button disabled={busy} aria-label="Save challan" className="rounded p-1 text-emerald-600 hover:bg-emerald-500/10"
                          onClick={() => void run(() => api.saveTdsChallan(m.month, {
                            bsrCode: draft.bsrCode, depositDate: draft.depositDate, challanSerial: draft.challanSerial,
                            amount: draft.amount === "" ? null : Number(draft.amount),
                          }))}>{busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}</button>
                        <button aria-label="Cancel" className="rounded p-1 text-fg/40 hover:bg-fg/5" onClick={() => setEditing(null)}><X className="h-4 w-4" /></button>
                      </div>
                    </td>
                  </tr>
                ) : (
                  <tr key={m.month} className="border-b border-fg/5 last:border-0">
                    <td className="px-5 py-2.5 font-medium">{monthName(m.month)}</td>
                    <td className="px-3 py-2.5 text-right tabular-nums">{m.finalised ? money(m.tdsDeducted) : <span className="text-fg/30">Not finalised</span>}</td>
                    <td className="px-3 py-2.5 tabular-nums">{m.bsrCode ?? <Dash due={m.finalised && m.tdsDeducted > 0} />}</td>
                    <td className="px-3 py-2.5">{m.depositDate ? formatDate(m.depositDate) : <Dash due={false} />}</td>
                    <td className="px-3 py-2.5 tabular-nums">{m.challanSerial ?? <Dash due={false} />}</td>
                    <td className={cn("px-3 py-2.5 text-right tabular-nums", m.amount != null && m.amount < m.tdsDeducted && "text-amber-700 dark:text-amber-300")}>
                      {m.amount != null ? money(m.amount) : "—"}
                    </td>
                    <td className="px-3 py-2.5">
                      {m.finalised && (
                        <div className="flex justify-end gap-1">
                          <button className="inline-flex items-center gap-1 rounded px-2 py-1 text-xs text-violet hover:bg-violet/10" onClick={() => edit(m)}>
                            <Pencil className="h-3 w-3" /> {m.bsrCode ? "Edit" : "Add"}
                          </button>
                          {m.bsrCode && <button disabled={busy} aria-label="Remove challan" className="rounded p-1 text-fg/30 hover:text-red-500"
                            onClick={() => void run(() => api.clearTdsChallan(m.month))}><X className="h-3.5 w-3.5" /></button>}
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      ))}
    </div>
  );
}

function Dash({ due }: { due: boolean }) {
  return due ? <span className="rounded-full bg-amber-500/15 px-2 py-0.5 text-xs font-medium text-amber-700 dark:text-amber-300">Not recorded</span>
    : <span className="text-fg/30">—</span>;
}

function monthName(ym: string) {
  const [y, m] = ym.split("-").map(Number);
  return new Date(y, m - 1, 1).toLocaleDateString("en-IN", { month: "long", year: "numeric" });
}
