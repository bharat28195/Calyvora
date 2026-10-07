"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { AlertTriangle, CheckCircle2, Download, Loader2, XCircle } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { FilingFile, FilingIssue, PayrollMonthStatus } from "@/lib/types";
import { Card, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";

const select = "h-10 rounded-lg border border-fg/15 bg-fg/5 px-3 text-sm text-fg";

/** Financial-year quarters for the current and previous year, newest first: "2026-27-Q3". */
function quarters(): { value: string; label: string }[] {
  const now = new Date();
  const fyStart = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1;
  const names = ["Apr–Jun", "Jul–Sep", "Oct–Dec", "Jan–Mar"];
  const out: { value: string; label: string }[] = [];
  for (const y of [fyStart, fyStart - 1]) {
    const fy = `${y}-${String((y + 1) % 100).padStart(2, "0")}`;
    for (let q = 4; q >= 1; q--) out.push({ value: `${fy}-Q${q}`, label: `FY ${fy} Q${q} (${names[q - 1]})` });
  }
  return out;
}

function save(file: FilingFile) {
  const url = URL.createObjectURL(new Blob([file.content], { type: file.contentType }));
  const a = document.createElement("a");
  a.href = url;
  a.download = file.filename;
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * Statutory returns: the files a company uploads to the EPFO, ESIC and income-tax portals, built from
 * finalised months — plus what will stop them being accepted, checked before anybody uploads.
 */
export default function FilingsPage() {
  const [issues, setIssues] = useState<FilingIssue[] | null>(null);
  const [months, setMonths] = useState<PayrollMonthStatus[] | null>(null);
  const [month, setMonth] = useState("");
  const [quarter, setQuarter] = useState(() => quarters()[0].value);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [last, setLast] = useState<FilingFile | null>(null);
  const qs = useMemo(quarters, []);

  useEffect(() => {
    api.filingReadiness().then(setIssues).catch((e) => setError(e instanceof ApiError ? e.message : "Failed to load"));
    api.payrollMonths().then((m) => { setMonths(m); if (m.length) setMonth(m[0].month); })
      .catch(() => setMonths([]));
  }, []);

  async function download(key: string, get: () => Promise<FilingFile>) {
    setBusy(key);
    setError(null);
    try {
      const file = await get();
      save(file);
      setLast(file);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not build the file");
    } finally {
      setBusy(null);
    }
  }

  const errors = issues?.filter((i) => i.severity === "ERROR").length ?? 0;

  return (
    <div className="max-w-4xl">
      <h1 className="text-2xl font-semibold tracking-tight">Statutory returns</h1>
      <p className="mt-1 text-fg/50">
        Files for the EPFO, ESIC and income-tax portals, built from finalised months. You upload them
        and pay the challan on the portal — no portal accepts filings from software directly.
      </p>

      {error && <Alert tone="error" className="mt-6">{error}</Alert>}

      <Card className="mt-6">
        <CardTitle>Before you file</CardTitle>
        {issues === null ? (
          <Loader2 className="mt-3 h-5 w-5 animate-spin text-violet" />
        ) : issues.length === 0 ? (
          <p className="mt-3 flex items-center gap-2 text-sm text-emerald-500">
            <CheckCircle2 className="h-4 w-4" /> Nothing missing — every employee on payroll has what the returns need.
          </p>
        ) : (
          <>
            <p className="mt-1 text-sm text-fg/60">
              {errors > 0 ? `${errors} thing${errors === 1 ? "" : "s"} will keep people out of a return.` : "Only warnings."}{" "}
              Fix them on the employee (People → open the person → Statutory) or in{" "}
              <Link href="/payroll/statutory" className="text-violet hover:underline">Statutory settings</Link>.
            </p>
            <ul className="mt-3 divide-y divide-fg/5 text-sm">
              {issues.map((i, n) => (
                <li key={n} className="flex items-start gap-2 py-2">
                  {i.severity === "ERROR"
                    ? <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-400" />
                    : <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-400" />}
                  <span><span className="font-medium">{i.name}</span> — <span className="text-fg/70">{i.message}</span></span>
                </li>
              ))}
            </ul>
          </>
        )}
      </Card>

      <Card className="mt-6">
        <CardTitle>Monthly returns</CardTitle>
        {months === null ? (
          <Loader2 className="mt-3 h-5 w-5 animate-spin text-violet" />
        ) : months.length === 0 ? (
          <p className="mt-2 text-sm text-fg/60">
            No finalised months yet. Finalise a month on the{" "}
            <Link href="/payroll/run" className="text-violet hover:underline">Payroll run</Link> page first —
            returns are only built from locked figures.
          </p>
        ) : (
          <>
            <div className="mt-4 flex flex-wrap items-center gap-3">
              <select className={select} value={month} onChange={(e) => setMonth(e.target.value)} aria-label="Month">
                {months.map((m) => <option key={m.month} value={m.month}>{m.month}</option>)}
              </select>
              <Button variant="secondary" disabled={busy !== null} onClick={() => download("ecr", () => api.filingFile("ecr", month))}>
                {busy === "ecr" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />} PF ECR
              </Button>
              <Button variant="secondary" disabled={busy !== null} onClick={() => download("esi", () => api.filingFile("esi", month))}>
                {busy === "esi" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />} ESI contributions
              </Button>
              <Button variant="secondary" disabled={busy !== null} onClick={() => download("pt", () => api.filingFile("pt", month))}>
                {busy === "pt" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />} Professional tax
              </Button>
            </div>
            <ul className="mt-4 list-disc space-y-1 pl-5 text-xs text-fg/50">
              <li><strong>PF ECR</strong> — upload on the EPFO Unified Portal (ECR/Return Filing). Pay by the 15th of the next month.</li>
              <li><strong>ESI</strong> — open in a spreadsheet and paste into the ESIC monthly contribution template. Pay by the 15th.</li>
              <li><strong>Professional tax</strong> — the state-wise summary for your PT return; due dates differ by state.</li>
            </ul>
          </>
        )}
      </Card>

      <Card className="mt-6">
        <CardTitle>Quarterly TDS — Form 24Q</CardTitle>
        <p className="mt-1 text-sm text-fg/60">
          Salary paid and tax deducted per employee per month, for the return-preparation utility. All
          three months must be finalised.
        </p>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <select className={select} value={quarter} onChange={(e) => setQuarter(e.target.value)} aria-label="Quarter">
            {qs.map((q) => <option key={q.value} value={q.value}>{q.label}</option>)}
          </select>
          <Button variant="secondary" disabled={busy !== null} onClick={() => download("24q", () => api.form24q(quarter))}>
            {busy === "24q" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />} 24Q data
          </Button>
        </div>
        <p className="mt-3 text-xs text-fg/50">
          Deposit TDS by the 7th of the next month (30 April for March). 24Q is due 31 July, 31 October,
          31 January and 31 May.
        </p>
      </Card>

      {last && last.skipped.length > 0 && (
        <Alert tone="info" className="mt-6">
          <p className="font-medium">{last.filename}: {last.skipped.length} left out</p>
          <ul className="mt-1 list-disc pl-5 text-sm">
            {last.skipped.map((s, i) => <li key={i}>{s.name} — {s.reason}</li>)}
          </ul>
        </Alert>
      )}
    </div>
  );
}
