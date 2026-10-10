"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { ChevronRight, Loader2, Search } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { TaxDeclarationRow, TaxSettings } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Input } from "@/components/ui/input";
import { money } from "@/lib/format";
import { cn } from "@/lib/utils";

type Filter = "ALL" | "WAITING" | "NOT_STARTED" | "SUBMITTED";

/**
 * HR's year: the windows, and everybody's declaration with what is waiting on them.
 *
 * <p>Two windows with teeth, both enforced on the server. Declarations are collected early in the year
 * and frozen before its last payroll, once returns have been filed on them. Proofs are collected
 * towards the end; after the deadline, only what HR accepted reduces anybody's tax.
 */
export default function ManageTaxPage() {
  const [rows, setRows] = useState<TaxDeclarationRow[] | null>(null);
  const [settings, setSettings] = useState<TaxSettings | null>(null);
  const [deadline, setDeadline] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState<Filter>("ALL");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [list, s] = await Promise.all([api.taxDeclarations(), api.taxSettings()]);
      setRows(list);
      setSettings(s);
      setDeadline(s.proofDeadline ?? "");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load declarations");
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  async function saveSettings(next: TaxSettings) {
    setBusy(true); setError(null); setNotice(null);
    try {
      setSettings(await api.saveTaxSettings(next));
      setNotice("Saved.");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not save");
    } finally {
      setBusy(false);
    }
  }

  const shown = useMemo(() => (rows ?? []).filter((r) => {
    if (query && !r.employeeName.toLowerCase().includes(query.toLowerCase())) return false;
    if (filter === "WAITING") return r.awaitingReview > 0;
    if (filter === "NOT_STARTED") return r.status === "NOT_STARTED";
    if (filter === "SUBMITTED") return r.status === "SUBMITTED";
    return true;
  }), [rows, query, filter]);

  const counts = useMemo(() => ({
    total: rows?.length ?? 0,
    submitted: rows?.filter((r) => r.status === "SUBMITTED").length ?? 0,
    waiting: rows?.reduce((n, r) => n + r.awaitingReview, 0) ?? 0,
    tax: rows?.reduce((n, r) => n + r.annualTax, 0) ?? 0,
  }), [rows]);

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">Manage tax</h1>
      <p className="mt-1 text-fg/50">Declarations, proofs and what everyone&apos;s tax comes to this year.</p>

      {error && <Alert tone="error" className="mt-4">{error}</Alert>}
      {notice && <Alert tone="success" className="mt-4">{notice}</Alert>}

      <div className="mt-6 grid gap-4 sm:grid-cols-4">
        <Stat label="On payroll" value={String(counts.total)} />
        <Stat label="Submitted" value={`${counts.submitted} of ${counts.total}`} />
        <Stat label="Proofs waiting" value={String(counts.waiting)} tone={counts.waiting > 0 ? "text-amber-600 dark:text-amber-400" : undefined} />
        <Stat label="Tax for the year" value={money(counts.tax)} />
      </div>

      {settings && (
        <Card className="mt-4">
          <CardTitle>Windows</CardTitle>
          <div className="mt-4 grid gap-4 sm:grid-cols-3">
            <Toggle label="Declarations open" hint="Employees can change what they declare, and their regime."
              on={settings.declarationsOpen} disabled={busy}
              onChange={(v) => void saveSettings({ ...settings, declarationsOpen: v })} />
            <Toggle label="Proofs open" hint="Employees can upload receipts and statements."
              on={settings.proofsOpen} disabled={busy}
              onChange={(v) => void saveSettings({ ...settings, proofsOpen: v })} />
            <div>
              <p className="text-sm font-medium">Proof deadline</p>
              <p className="text-xs text-fg/50">From the month after it, only accepted proofs reduce tax.</p>
              <div className="mt-2 flex gap-2">
                <Input type="date" value={deadline} onChange={(e) => setDeadline(e.target.value)} />
                <Button variant="secondary" disabled={busy || deadline === (settings.proofDeadline ?? "")}
                  onClick={() => void saveSettings({ ...settings, proofDeadline: deadline || null })}>Set</Button>
              </div>
            </div>
          </div>
        </Card>
      )}

      <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
        <div className="flex gap-1">
          {([["ALL", "Everyone"], ["WAITING", "Proofs waiting"], ["NOT_STARTED", "Not started"], ["SUBMITTED", "Submitted"]] as [Filter, string][]).map(([f, l]) => (
            <button key={f} onClick={() => setFilter(f)}
              className={cn("rounded-full px-3 py-1 text-sm", filter === f ? "bg-violet text-white" : "text-fg/60 hover:bg-fg/5")}>{l}</button>
          ))}
        </div>
        <div className="relative">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg/30" />
          <Input className="w-64 pl-9" placeholder="Find someone…" value={query} onChange={(e) => setQuery(e.target.value)} />
        </div>
      </div>

      <Card className="mt-3 overflow-x-auto p-0">
        {rows === null ? (
          <div className="flex justify-center py-10"><Loader2 className="h-5 w-5 animate-spin text-violet" /></div>
        ) : shown.length === 0 ? (
          <p className="px-5 py-8 text-center text-sm text-fg/50">Nobody here.</p>
        ) : (
          <table className="w-full min-w-[720px] text-sm">
            <thead className="text-left text-xs uppercase tracking-wide text-fg/40">
              <tr className="border-b border-fg/10">
                <th className="px-5 py-2 font-medium">Name</th>
                <th className="px-3 py-2 font-medium">Regime</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2 text-right font-medium">Declared</th>
                <th className="px-3 py-2 text-right font-medium">Tax for the year</th>
                <th className="px-3 py-2 font-medium">Proofs</th>
                <th className="w-8" />
              </tr>
            </thead>
            <tbody>
              {shown.map((r) => (
                <tr key={r.employeeId} className="border-b border-fg/5 last:border-0 hover:bg-fg/[0.03]">
                  <td className="px-5 py-2.5 font-medium">
                    <Link href={`/finance/tax/manage/${r.employeeId}`} className="hover:text-violet">{r.employeeName}</Link>
                  </td>
                  <td className="px-3 py-2.5">{r.regime === "NEW" ? "New" : "Old"}</td>
                  <td className="px-3 py-2.5"><StatusChip status={r.status} /></td>
                  <td className="px-3 py-2.5 text-right tabular-nums">{r.totalDeclared > 0 ? money(r.totalDeclared) : "—"}</td>
                  <td className="px-3 py-2.5 text-right tabular-nums">{money(r.annualTax)}</td>
                  <td className="px-3 py-2.5 text-xs">
                    {r.awaitingReview > 0
                      ? <span className="rounded-full bg-amber-500/15 px-2 py-0.5 font-medium text-amber-700 dark:text-amber-300">{r.awaitingReview} to review</span>
                      : r.proofs > 0 ? <span className="text-fg/50">{r.proofs} file{r.proofs === 1 ? "" : "s"}</span> : <span className="text-fg/30">—</span>}
                  </td>
                  <td className="pr-3"><Link href={`/finance/tax/manage/${r.employeeId}`} aria-label={`Open ${r.employeeName}`}><ChevronRight className="h-4 w-4 text-fg/30" /></Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <Card>
      <p className="text-xs text-fg/50">{label}</p>
      <p className={cn("mt-1 text-xl font-semibold tabular-nums", tone)}>{value}</p>
    </Card>
  );
}

function Toggle({ label, hint, on, disabled, onChange }: { label: string; hint: string; on: boolean; disabled?: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <div>
        <p className="text-sm font-medium">{label}</p>
        <p className="text-xs text-fg/50">{hint}</p>
      </div>
      <button role="switch" aria-checked={on} disabled={disabled} onClick={() => onChange(!on)}
        className={cn("relative h-6 w-11 shrink-0 rounded-full transition-colors disabled:opacity-50", on ? "bg-violet" : "bg-fg/20")}>
        <span className={cn("absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-all", on ? "left-[1.375rem]" : "left-0.5")} />
      </button>
    </div>
  );
}

function StatusChip({ status }: { status: string }) {
  const map: Record<string, string> = {
    SUBMITTED: "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400",
    DRAFT: "bg-amber-500/15 text-amber-700 dark:text-amber-300",
    NOT_STARTED: "bg-fg/5 text-fg/50",
  };
  const label: Record<string, string> = { SUBMITTED: "Submitted", DRAFT: "Draft", NOT_STARTED: "Not started" };
  return <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", map[status] ?? "bg-fg/5")}>{label[status] ?? status}</span>;
}
