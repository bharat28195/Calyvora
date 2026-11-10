"use client";

import { Suspense, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, Printer } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Form130 } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { Form130Sheet } from "@/components/tax/form130-sheet";
import { recentTaxYears, taxYearOf, yearQuery } from "@/lib/tax-years";

/** How many certificates are fetched at once — enough to be quick, few enough not to flood the API. */
const PARALLEL = 4;

type Row = { employeeId: string; employeeName: string; form?: Form130; error?: string };

/**
 * Every employee's Form 130 for a year, one after another, ready to print or save as a single PDF —
 * the June job of issuing certificates to the whole company, without opening each person.
 *
 * <p>Certificates load a few at a time with a progress bar, and anybody whose certificate cannot be
 * produced (no salary that year, say) is listed with the reason instead of silently missing.
 */
export default function Form130EveryonePage() {
  return <Suspense fallback={null}><Everyone /></Suspense>;
}

function Everyone() {
  const router = useRouter();
  const year = useSearchParams().get("year") ?? taxYearOf();
  const [rows, setRows] = useState<Row[] | null>(null);
  const [part, setPart] = useState<"A" | "B" | "AB">("AB");
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setRows(null);
    setError(null);
    (async () => {
      try {
        const people = await api.taxDeclarations(year);
        const list: Row[] = people.map((p) => ({ employeeId: p.employeeId, employeeName: p.employeeName }));
        if (cancelled) return;
        setRows(list);
        let next = 0;
        const worker = async () => {
          while (!cancelled && next < list.length) {
            const i = next++;
            try {
              const form = await api.employeeForm130(list[i].employeeId, year);
              list[i] = { ...list[i], form };
            } catch (e) {
              list[i] = { ...list[i], error: e instanceof ApiError ? e.message : "Could not be produced" };
            }
            if (!cancelled) setRows([...list]);
          }
        };
        await Promise.all(Array.from({ length: PARALLEL }, worker));
      } catch (e) {
        if (!cancelled) setError(e instanceof ApiError ? e.message : "Could not load employees");
      }
    })();
    return () => { cancelled = true; };
  }, [year]);

  const done = rows?.filter((r) => r.form || r.error).length ?? 0;
  const total = rows?.length ?? 0;
  const shown = useMemo(() => (rows ?? []).filter((r) => r.form
    && (!query.trim() || r.employeeName.toLowerCase().includes(query.trim().toLowerCase()))), [rows, query]);
  const failed = (rows ?? []).filter((r) => r.error);

  return (
    <div>
      <div className="print:hidden">
        <Link href="/finance/tax/manage" className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg">
          <ArrowLeft className="h-4 w-4" /> Manage tax
        </Link>
        <div className="mt-3 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Form 130 for everyone</h1>
            <p className="mt-1 text-fg/50">Every employee&apos;s certificate for the year, ready to print or save as one PDF.</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select value={year} onChange={(e) => router.replace(`/finance/tax/form130/everyone${yearQuery(e.target.value)}`)}
              aria-label="Tax year" className="rounded-lg border border-fg/15 bg-fg/5 px-2 py-1.5 text-sm text-fg">
              {recentTaxYears(4).map((y) => <option key={y} value={y} className="bg-surface">Tax year {y}</option>)}
            </select>
            <div className="flex rounded-lg bg-fg/5 p-0.5 text-sm">
              {([["AB", "Both parts"], ["A", "Part A"], ["B", "Part B"]] as const).map(([k, l]) => (
                <button key={k} onClick={() => setPart(k)} className={cn("rounded-md px-3 py-1", part === k ? "bg-surface font-medium shadow-sm" : "text-fg/60")}>{l}</button>
              ))}
            </div>
            <Button size="sm" disabled={done < total || shown.length === 0} onClick={() => window.print()}>
              <Printer className="h-4 w-4" /> Print {query.trim() ? `${shown.length} shown` : "all"}
            </Button>
          </div>
        </div>

        {error && <Alert tone="error" className="mt-4">{error}</Alert>}

        {rows && (
          <Card className="mt-4 p-4">
            <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
              <span>
                {done < total
                  ? <><Loader2 className="mr-1 inline h-4 w-4 animate-spin text-violet" /> Preparing {done} of {total}…</>
                  : <>{total - failed.length} certificate{total - failed.length === 1 ? "" : "s"} ready{failed.length > 0 && `, ${failed.length} could not be produced`}.</>}
              </span>
              <Input className="w-56" placeholder="Find someone…" value={query} onChange={(e) => setQuery(e.target.value)} />
            </div>
            <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-fg/10">
              <div className="h-full bg-violet transition-all" style={{ width: `${total ? (done / total) * 100 : 0}%` }} />
            </div>
            {failed.length > 0 && (
              <ul className="mt-3 space-y-0.5 text-xs text-fg/55">
                {failed.map((r) => <li key={r.employeeId}><span className="font-medium text-fg/70">{r.employeeName}:</span> {r.error}</li>)}
              </ul>
            )}
          </Card>
        )}
      </div>

      {!rows && !error && <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>}

      {shown.map((r, i) => (
        <div key={r.employeeId} className="mt-8">
          <p className="mx-auto max-w-4xl text-xs font-medium uppercase tracking-wide text-fg/40 print:hidden">{r.employeeName}</p>
          <Form130Sheet f={r.form!} part={part} breakBefore={i > 0} />
        </div>
      ))}
    </div>
  );
}
