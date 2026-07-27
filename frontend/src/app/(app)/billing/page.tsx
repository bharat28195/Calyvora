"use client";

import { useEffect, useState } from "react";
import { Loader2, CheckCircle2, Users, CalendarClock, Sparkles } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { BillingOverview } from "@/lib/types";
import { money as fmtMoney } from "@/lib/format";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Billing — the subscription is priced per active employee, per month. The monthly charge tracks
 * headcount, so a company that grows from 5 to 20 people is billed for 20 the month it has 20.
 */
export default function BillingPage() {
  const [data, setData] = useState<BillingOverview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    api.billingOverview().then(setData).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load billing"));
  }, []);

  const money = (n: number) => fmtMoney(n);

  async function run(label: string, fn: () => Promise<BillingOverview>) {
    setBusy(label); setError(null);
    try { setData(await fn()); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Something went wrong"); }
    finally { setBusy(null); }
  }

  if (error && !data) return <Alert tone="error" className="mt-6">{error}</Alert>;
  if (!data) return <div className="mt-16 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;

  const monthLabel = (m: string) => new Date(`${m}-01T00:00:00`).toLocaleDateString(undefined, { month: "short", year: "numeric" });

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Billing</h1>
          <p className="mt-1 text-fg/50">You&apos;re billed per active employee, per month.</p>
        </div>
        <StatusPill status={data.status} />
      </div>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      {data.trialActive && (
        <Alert tone="warning" className="mt-6">
          You&apos;re on a free trial{data.trialEndsAt ? ` until ${new Date(data.trialEndsAt).toLocaleDateString()}` : ""}.
          Activate to keep your team&apos;s HR running.
        </Alert>
      )}

      {/* The headline number */}
      <Card className="mt-6">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-xs uppercase tracking-wide text-fg/40">This month</p>
            <p className="mt-1 text-4xl font-semibold tabular-nums">{money(data.monthlyCharge)}</p>
            <p className="mt-1 text-sm text-fg/50">
              {data.billableEmployees} employees × {money(data.pricePerEmployee)}/mo
            </p>
          </div>
          <div className="flex gap-2">
            {data.status !== "ACTIVE" && (
              <Button onClick={() => run("activate", () => api.activateSubscription())} disabled={busy !== null}>
                {busy === "activate" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                Activate subscription
              </Button>
            )}
          </div>
        </div>
        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Mini icon={<Users className="h-4 w-4" />} label="Billable employees" value={String(data.billableEmployees)} />
          <Mini label="Per employee / month" value={money(data.pricePerEmployee)} />
          <Mini label="Per employee / year" value={money(data.pricePerEmployeePerYear)} />
          <Mini icon={<CalendarClock className="h-4 w-4" />} label="Annual run-rate" value={money(data.annualCharge)} />
        </div>
      </Card>

      {/* Invoice history */}
      <Card className="mt-6 p-0">
        <div className="border-b border-fg/10 px-5 py-3">
          <CardTitle>Invoices</CardTitle>
          <p className="mt-0.5 text-sm text-fg/50">One per month, priced on that month&apos;s headcount.</p>
        </div>
        <div className="divide-y divide-fg/5">
          {data.invoices.slice().reverse().map((inv) => (
            <div key={inv.month} className="flex items-center gap-3 px-5 py-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium">{monthLabel(inv.month)}</p>
                <p className="text-xs text-fg/40">{inv.headcount} employees × {money(data.pricePerEmployee)}</p>
              </div>
              <p className="shrink-0 text-sm font-medium tabular-nums">{money(inv.amount)}</p>
              <div className="w-24 shrink-0 text-right">
                {inv.status === "PAID" ? (
                  <span className="inline-flex items-center gap-1 text-xs text-emerald-400"><CheckCircle2 className="h-3.5 w-3.5" /> Paid</span>
                ) : (
                  <Button size="sm" variant={inv.status === "OVERDUE" ? "primary" : "secondary"}
                    disabled={busy !== null} onClick={() => run(inv.month, () => api.payInvoice(inv.month))}>
                    {busy === inv.month ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                    {inv.status === "OVERDUE" ? "Pay now" : "Pay"}
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
      </Card>

      <p className="mt-4 text-xs text-fg/40">
        Metered monthly — add or remove people any time and the next invoice reflects it. Demo billing; no card is charged.
      </p>
    </div>
  );
}

function StatusPill({ status }: { status: BillingOverview["status"] }) {
  const map: Record<BillingOverview["status"], string> = {
    ACTIVE: "bg-emerald-500/15 text-emerald-400",
    TRIALING: "bg-aqua/15 text-aqua",
    PAST_DUE: "bg-amber-500/15 text-amber-300",
    CANCELLED: "bg-fg/10 text-fg/50",
  };
  return <span className={cn("rounded-full px-3 py-1 text-xs font-medium", map[status])}>{status.toLowerCase().replace("_", " ")}</span>;
}

function Mini({ icon, label, value }: { icon?: React.ReactNode; label: string; value: string }) {
  return (
    <div className="rounded-lg border border-fg/10 bg-fg/[0.02] p-3">
      <p className="flex items-center gap-1.5 text-xs text-fg/50">{icon}{label}</p>
      <p className="mt-1 text-lg font-semibold tabular-nums">{value}</p>
    </div>
  );
}
