"use client";

import { useCallback, useEffect, useState } from "react";
import { Loader2, Lock, Unlock } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { TaxDeclaration, TaxDeclarationRow } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";

/**
 * HR's view of the year: who has declared, under which regime, and what it costs the company to
 * withhold.
 *
 * <p>The window switch is the part with teeth. Declarations are collected at the start of the year
 * and frozen before the last payroll of it, because once a quarterly return has been filed the
 * figures behind it must not move. The switch is enforced on the server — hiding the form would not
 * achieve that — so this page only exposes a decision that is already real.
 */
export default function ManageTaxPage() {

  const [rows, setRows] = useState<TaxDeclarationRow[] | null>(null);
  const [mine, setMine] = useState<TaxDeclaration | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [list, own] = await Promise.all([api.taxDeclarations(), api.taxDeclaration()]);
      setRows(list);
      setMine(own);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load declarations");
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  async function toggleWindow(open: boolean) {
    setBusy(true);
    setError(null);
    try {
      await api.setTaxWindow(open);
      await load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not change the window");
    } finally {
      setBusy(false);
    }
  }

  const open = mine?.windowOpen ?? true;
  const year = mine?.financialYear ?? "";

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Manage tax</h1>
        <p className="mt-1 text-fg/50">
          Declarations for {year}
        </p>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      <Card className="mt-6">
        <CardTitle>Declaration window</CardTitle>
        <p className="mt-1 text-xs text-fg/50">
          Open it in April so people can declare, and close it before the last pay run of the year —
          once a return has been filed, the figures behind it must not move.
        </p>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <span className={`rounded-full px-3 py-1 text-sm ${
            open ? "bg-emerald-500/15 text-emerald-400" : "bg-fg/10 text-fg/60"}`}>
            {open ? "Open — employees can edit" : "Closed — declarations are frozen"}
          </span>
          <Button variant="secondary" size="sm" disabled={busy} onClick={() => void toggleWindow(!open)}>
            {busy ? <Loader2 className="h-4 w-4 animate-spin" />
              : open ? <Lock className="h-4 w-4" /> : <Unlock className="h-4 w-4" />}
            {open ? "Close declarations" : "Open declarations"}
          </Button>
        </div>
      </Card>

      <Card className="mt-4">
        <CardTitle>Who has declared</CardTitle>
        {rows === null ? (
          <div className="mt-6 flex justify-center"><Loader2 className="h-5 w-5 animate-spin text-violet" /></div>
        ) : rows.length === 0 ? (
          <p className="mt-3 text-sm text-fg/50">
            Nobody on payroll yet — a declaration needs a salary on record to be worth anything.
          </p>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full min-w-[560px] text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-fg/40">
                  <th className="pb-2 font-medium">Employee</th>
                  <th className="pb-2 font-medium">Regime</th>
                  <th className="pb-2 font-medium">Status</th>
                  <th className="pb-2 text-right font-medium">Declared</th>
                  <th className="pb-2 text-right font-medium">Tax for the year</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.employeeId} className="border-t border-fg/5">
                    <td className="py-2">{r.employeeName}</td>
                    <td className="py-2 text-fg/70">{r.regime === "NEW" ? "New" : "Old"}</td>
                    <td className="py-2">
                      <span className={`rounded-full px-2 py-0.5 text-xs ${statusChip(r.status)}`}>
                        {statusLabel(r.status)}
                      </span>
                    </td>
                    <td className="py-2 text-right tabular-nums text-fg/70">{inr(r.totalDeclared)}</td>
                    <td className="py-2 text-right tabular-nums font-medium">{inr(r.annualTax)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}

function statusLabel(status: string): string {
  if (status === "SUBMITTED") return "Declared";
  if (status === "DRAFT") return "Draft";
  return "Not started";
}

function statusChip(status: string): string {
  if (status === "SUBMITTED") return "bg-emerald-500/15 text-emerald-400";
  if (status === "DRAFT") return "bg-amber-500/15 text-amber-400";
  return "bg-fg/10 text-fg/50";
}

function inr(value: number, currency = "INR"): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency", currency, maximumFractionDigits: 0,
  }).format(value);
}
