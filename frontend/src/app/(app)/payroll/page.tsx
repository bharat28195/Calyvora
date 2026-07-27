"use client";

import { useEffect, useState } from "react";
import { Loader2, ChevronRight, Wallet } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Employee, Compensation } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { EmployeeCompensation } from "@/components/people/employee-compensation";
import { cn } from "@/lib/utils";
import { money as fmtMoney } from "@/lib/format";

// Company-currency formatting (Settings → Localization); per-record currency ignored for display.
function money(n: number | null | undefined, _currency?: string) {
  return fmtMoney(n);
}

/**
 * Payroll (Owner/Admin) — everyone's current salary at a glance, expand a person to see their hike
 * history, record a raise, or pull a payslip. Reuses the per-employee compensation panel.
 */
export default function PayrollPage() {
  const [employees, setEmployees] = useState<Employee[] | null>(null);
  const [comp, setComp] = useState<Record<string, Compensation | null>>({});
  const [openId, setOpenId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.listEmployees().then(async (list) => {
      setEmployees(list);
      const entries = await Promise.all(list.map(async (e) => {
        try { return [e.id, await api.compensation(e.id)] as const; }
        catch { return [e.id, null] as const; }
      }));
      setComp(Object.fromEntries(entries));
    }).catch((e) => { setEmployees([]); setError(e instanceof ApiError ? e.message : "Failed to load payroll"); });
  }, []);

  const currency = Object.values(comp).find((c) => c?.currency)?.currency ?? "USD";
  const totalAnnual = Object.values(comp).reduce((s, c) => s + (c?.currentAnnual ?? 0), 0);
  const onPayroll = Object.values(comp).filter((c) => c?.currentAnnual != null).length;

  return (
    <div>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Payroll</h1>
        <p className="mt-1 text-fg/50">Salaries, raises and payslips for the whole team.</p>
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {employees === null ? (
        <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>
      ) : (
        <>
          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3">
            <Kpi label="On payroll" value={`${onPayroll} / ${employees.length}`} />
            <Kpi label="Annual payroll" value={money(totalAnnual, currency)} />
            <Kpi label="Monthly" value={money(Math.round(totalAnnual / 12), currency)} />
          </div>

          <Card className="mt-6 p-0">
            <div className="divide-y divide-fg/5">
              {employees.map((e) => {
                const c = comp[e.id];
                const open = openId === e.id;
                return (
                  <div key={e.id}>
                    <button onClick={() => setOpenId(open ? null : e.id)}
                      className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-fg/[0.03]">
                      <ChevronRight className={cn("h-4 w-4 shrink-0 text-fg/40 transition-transform", open && "rotate-90")} />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{e.firstName} {e.lastName}</p>
                        <p className="truncate text-xs text-fg/40">{e.jobTitle ?? "—"}</p>
                      </div>
                      <div className="shrink-0 text-right">
                        <p className="text-sm font-medium tabular-nums">
                          {c?.currentAnnual != null ? money(c.currentAnnual, c.currency) : <span className="text-fg/30">not set</span>}
                        </p>
                        {c?.currentMonthly != null && <p className="text-xs text-fg/40">{money(c.currentMonthly, c.currency)}/mo</p>}
                      </div>
                    </button>
                    {open && (
                      <div className="border-t border-fg/5 bg-fg/[0.02] px-4 py-4">
                        <EmployeeCompensation employeeId={e.id} />
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </Card>
        </>
      )}
    </div>
  );
}

function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <Card className="py-4">
      <p className="flex items-center gap-1.5 text-xs text-fg/50"><Wallet className="h-3.5 w-3.5" /> {label}</p>
      <p className="mt-1 text-xl font-semibold tabular-nums">{value}</p>
    </Card>
  );
}
