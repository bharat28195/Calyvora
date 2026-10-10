"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, Printer } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Form130 } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";
import { money, formatDate } from "@/lib/format";

/**
 * Form 130 (formerly Form 16), Part B: the employer's statement of salary paid and tax computed —
 * the part an employer prepares itself. Part A, the certificate of tax deposited, is generated from
 * the TRACES portal and is not something Orbit can issue, which the sheet says plainly.
 */
export default function Form130Page() {
  return <Suspense fallback={null}><Form130Sheet /></Suspense>;
}

function Form130Sheet() {
  const employee = useSearchParams().get("employee");
  const [f, setF] = useState<Form130 | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (employee ? api.employeeForm130(employee) : api.myForm130())
      .then(setF)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load Form 130"));
  }, [employee]);

  if (error) return <Alert tone="error" className="mt-6">{error}</Alert>;
  if (!f) return <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;
  const c = f.computation;
  const quarterTotal = f.quarters.reduce((n, q) => n + q.tds, 0);

  return (
    <div>
      <div className="flex items-center justify-between gap-3 print:hidden">
        <Link href={employee ? `/finance/tax/manage/${employee}` : "/finance/tax/computation"} className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg">
          <ArrowLeft className="h-4 w-4" /> Back
        </Link>
        <Button variant="secondary" size="sm" onClick={() => window.print()}><Printer className="h-4 w-4" /> Print or save as PDF</Button>
      </div>

      <article className="payslip-sheet mx-auto mt-4 max-w-3xl rounded-xl border border-fg/10 bg-surface p-8 text-sm">
        <header className="text-center">
          <p className="text-xs uppercase tracking-widest text-fg/50">Income-tax Rules, 2026 · rule 215</p>
          <h1 className="mt-1 text-xl font-semibold">Form No. 130 — Part B</h1>
          <p className="text-fg/60">Details of salary paid and tax deducted (formerly Form 16, Part B)</p>
        </header>

        <section className="mt-6 grid grid-cols-2 gap-x-6 gap-y-1">
          <Pair k="Employer" v={f.employerName} />
          <Pair k="TAN of the employer" v={f.employerTan ?? "—"} />
          {f.employerAddress && <Pair k="Address" v={f.employerAddress} />}
          <Pair k="Employee" v={f.employeeName} />
          <Pair k="PAN of the employee" v={f.employeePan ?? "—"} />
          {f.designation && <Pair k="Designation" v={f.designation} />}
          <Pair k="Tax year" v={f.financialYear} />
          <Pair k="Period with the employer" v={`${formatDate(f.periodFrom)} to ${formatDate(f.periodTo)}`} />
          <Pair k="Regime" v={c.regime === "NEW" ? "New regime (section 202)" : "Old regime"} />
        </section>

        <Section title="1. Salary">
          <Row n="(a)" k="Gross salary" v={money(c.grossSalary - c.previousEmployerIncome)} />
          {c.previousEmployerIncome > 0 && <Row n="(b)" k="Salary from previous employer(s), as declared" v={money(c.previousEmployerIncome)} />}
          <Row n="" k="Total" v={money(c.grossSalary)} strong />
        </Section>

        <Section title="2. Allowances exempt (Schedule III)">
          {c.exemptions.length === 0 ? <Row n="" k="Nil" v="—" /> : c.exemptions.map((e) => <Row key={e.key} n="" k={`${e.label} ${e.section}`} v={money(e.allowed)} />)}
        </Section>

        <Section title="3. Deductions under section 19">
          <Row n="(a)" k="Standard deduction" v={money(c.standardDeduction)} />
          <Row n="(b)" k="Professional tax" v={money(c.professionalTax)} />
          <Row n="4." k="Income chargeable under the head Salaries" v={money(c.salaryIncome)} strong />
        </Section>

        <Section title="5. Other income reported by the employee">
          <Row n="(a)" k="Income from house property" v={money(c.houseProperty)} />
          <Row n="(b)" k="Income from other sources" v={money(c.otherIncome)} />
          <Row n="6." k="Gross total income" v={money(c.grossTotalIncome)} strong />
        </Section>

        <Section title="7. Deductions under Chapter VIII">
          {c.deductions.length === 0 ? <Row n="" k="Nil" v="—" /> : c.deductions.map((d) => (
            <Row key={d.key} n="" k={`${d.section} — ${d.label}`} v={`${money(d.declared)} / ${money(d.allowed)}`} />
          ))}
          <p className="px-2 text-[11px] text-fg/40">Gross amount / deductible amount</p>
          <Row n="8." k="Aggregate of deductible amounts" v={money(c.totalDeductions)} strong />
        </Section>

        <Section title="Tax">
          <Row n="9." k="Total taxable income (rounded, section 516)" v={money(c.taxableIncome)} strong />
          <Row n="10." k="Tax on total income" v={money(c.taxOnIncome)} />
          <Row n="11." k="Rebate under section 156" v={money(c.rebate)} />
          <Row n="12." k="Surcharge" v={money(c.surcharge)} />
          <Row n="13." k="Health and education cess" v={money(c.cess)} />
          <Row n="14." k="Tax payable" v={money(c.totalTax)} strong />
        </Section>

        <Section title="Tax deducted by this employer, quarter by quarter">
          {f.quarters.map((q) => <Row key={q.quarter} n="" k={q.quarter} v={money(q.tds)} />)}
          <Row n="" k="Total deducted in finalised months" v={money(quarterTotal)} strong />
        </Section>

        <p className="mt-6 text-xs text-fg/50">
          Part A of Form 130, the certificate of tax deducted and deposited, is generated from the TRACES portal and issued with this.
          Figures for months not yet finalised are projected and will change if salary or declarations change.
        </p>

        <div className="mt-10 grid grid-cols-2 gap-6 text-xs text-fg/60">
          <div><div className="border-t border-fg/30 pt-1">Place and date</div></div>
          <div><div className="border-t border-fg/30 pt-1">Signature of the person responsible for deduction of tax</div></div>
        </div>
      </article>
    </div>
  );
}

function Pair({ k, v }: { k: string; v: string }) {
  return <div className="flex gap-2"><span className="text-fg/50">{k}:</span><span className="font-medium">{v}</span></div>;
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mt-5">
      <h2 className="text-sm font-semibold">{title}</h2>
      <div className="mt-1 divide-y divide-fg/10 border-y border-fg/10">{children}</div>
    </section>
  );
}

function Row({ n, k, v, strong }: { n: string; k: string; v: string; strong?: boolean }) {
  return (
    <div className={`flex items-baseline gap-2 px-2 py-1 ${strong ? "font-semibold" : ""}`}>
      <span className="w-8 shrink-0 text-xs text-fg/40">{n}</span>
      <span className="flex-1">{k}</span>
      <span className="tabular-nums">{v}</span>
    </div>
  );
}
