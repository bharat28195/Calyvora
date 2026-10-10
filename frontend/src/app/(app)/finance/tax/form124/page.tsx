"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, Printer } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { TaxComputation, TaxDeclaration } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";
import { money, formatDate } from "@/lib/format";

/**
 * Form 124 (formerly 12BB): the employee's statement of the claims the employer is to allow, laid
 * out in the form's own order — rent for HRA, leave travel, home-loan interest, then the Chapter VIII
 * deductions — with the declaration to sign at the foot. Printed or saved as PDF from the browser.
 * HR opens anyone's with ?employee=.
 */
export default function Form124Page() {
  return <Suspense fallback={null}><Form124 /></Suspense>;
}

function Form124() {
  const employee = useSearchParams().get("employee");
  const [d, setD] = useState<TaxDeclaration | null>(null);
  const [c, setC] = useState<TaxComputation | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (employee
      ? Promise.all([api.employeeTaxDeclaration(employee), api.employeeTaxComputation(employee)])
      : Promise.all([api.taxDeclaration(), api.taxComputation()]))
      .then(([decl, comp]) => { setD(decl); setC(comp); })
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load the declaration"));
  }, [employee]);

  if (error) return <Alert tone="error" className="mt-6">{error}</Alert>;
  if (!d || !c) return <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;

  const label = new Map(d.catalog.map((x) => [x.key, x]));
  const lta = d.items.find((i) => i.key === "LTA");
  const chapter8 = d.items.filter((i) => {
    const e = label.get(i.key);
    return e && !e.income && i.key !== "LTA" && i.key !== "PROFESSIONAL_TAX_OTHER" && i.key !== "HRA_EXEMPTION";
  });
  const totalRent = d.rent.reduce((n, r) => n + r.monthlyRent * months(r.fromMonth, r.toMonth), 0);

  return (
    <div>
      <div className="flex items-center justify-between gap-3 print:hidden">
        <Link href={employee ? `/finance/tax/manage/${employee}` : "/finance/tax"} className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg">
          <ArrowLeft className="h-4 w-4" /> Back
        </Link>
        <Button variant="secondary" size="sm" onClick={() => window.print()}><Printer className="h-4 w-4" /> Print or save as PDF</Button>
      </div>

      <article className="payslip-sheet mx-auto mt-4 max-w-3xl rounded-xl border border-fg/10 bg-surface p-8 text-sm">
        <header className="text-center">
          <p className="text-xs uppercase tracking-widest text-fg/50">Income-tax Rules, 2026</p>
          <h1 className="mt-1 text-xl font-semibold">Form No. 124</h1>
          <p className="text-fg/60">Statement of claims by an employee for deduction of tax (formerly Form 12BB)</p>
          <p className="mt-1 text-fg/60">See section 392 of the Income-tax Act, 2025</p>
        </header>

        <section className="mt-6 grid grid-cols-2 gap-x-6 gap-y-1">
          <Pair k="Name of the employee" v={d.employeeName} />
          <Pair k="Tax year" v={d.financialYear} />
          <Pair k="Regime chosen" v={d.regime === "NEW" ? "New regime (section 202)" : "Old regime"} />
          <Pair k="Status" v={d.status === "SUBMITTED" ? `Submitted${d.submittedAt ? ` on ${formatDate(d.submittedAt)}` : ""}` : "Draft"} />
        </section>

        <Table title="1. House rent allowance" heads={["Period", "Rent", "Landlord", "Landlord's PAN", "Relationship"]}>
          {d.rent.length === 0 ? <Empty cols={5} /> : d.rent.map((r) => (
            <tr key={r.id}>
              <Td>{r.fromMonth} to {r.toMonth} · {r.city}</Td>
              <Td right>{money(r.monthlyRent)} / month</Td>
              <Td>{r.landlordName ?? "—"}{r.landlordAddress ? `, ${r.landlordAddress}` : ""}</Td>
              <Td>{r.landlordPan ?? (totalRent > 100000 ? "Required" : "—")}</Td>
              <Td>{r.landlordRelationship ?? "—"}</Td>
            </tr>
          ))}
        </Table>
        {totalRent > 0 && <p className="mt-1 text-right text-xs text-fg/60">Rent for the year {money(totalRent)}</p>}

        <Table title="2. Leave travel concession" heads={["Amount claimed"]}>
          <tr><Td right>{lta ? money(lta.amount) : "Nil"}</Td></tr>
        </Table>

        <Table title="3. Interest on borrowing for a house" heads={["Use", "Interest", "Lender", "Lender's PAN"]}>
          {d.houses.length === 0 ? <Empty cols={4} /> : d.houses.map((h) => (
            <tr key={h.id}>
              <Td>{h.letOut ? "Let out" : "Self-occupied"}{h.address ? ` · ${h.address}` : ""}</Td>
              <Td right>{money(h.interest)}</Td>
              <Td>{h.lenderName ?? "—"}</Td>
              <Td>{h.lenderPan ?? "—"}</Td>
            </tr>
          ))}
        </Table>

        <Table title="4. Deductions under Chapter VIII" heads={["Section", "Particulars", "Amount", "Evidence"]}>
          {chapter8.length === 0 ? <Empty cols={4} /> : chapter8.map((i) => (
            <tr key={i.key}>
              <Td>{label.get(i.key)?.sectionLabel}</Td>
              <Td>{label.get(i.key)?.label}{i.detail ? ` · ${i.detail}` : ""}</Td>
              <Td right>{money(i.amount)}</Td>
              <Td>{i.proofs.length ? `${i.proofs.length} attached` : "—"}</Td>
            </tr>
          ))}
        </Table>

        {d.previous && (
          <Table title="5. Salary from a previous employer this year" heads={["Employer", "TAN", "Salary", "Tax deducted"]}>
            <tr>
              <Td>{d.previous.employerName ?? "—"}</Td><Td>{d.previous.tan ?? "—"}</Td>
              <Td right>{money(d.previous.income)}</Td><Td right>{money(d.previous.tds)}</Td>
            </tr>
          </Table>
        )}

        <p className="mt-4 text-xs text-fg/50">
          On these claims the tax for the year works out to {money(c.totalTax)} under the {c.regime === "NEW" ? "new" : "old"} regime.
          Claims are allowed up to the limits in the Act; under the new regime most of them do not reduce tax.
        </p>

        <section className="mt-8">
          <h2 className="text-sm font-semibold">Verification</h2>
          <p className="mt-2 leading-relaxed">
            I, <span className="font-medium">{d.employeeName}</span>, do hereby certify that the information given above is complete and correct.
          </p>
          <div className="mt-10 grid grid-cols-3 gap-6 text-xs text-fg/60">
            <div><div className="border-t border-fg/30 pt-1">Place</div></div>
            <div><div className="border-t border-fg/30 pt-1">Date</div></div>
            <div><div className="border-t border-fg/30 pt-1">Signature of the employee</div></div>
          </div>
        </section>
      </article>
    </div>
  );
}

function months(from: string, to: string) {
  const [fy, fm] = from.split("-").map(Number);
  const [ty, tm] = to.split("-").map(Number);
  return (ty - fy) * 12 + (tm - fm) + 1;
}

function Pair({ k, v }: { k: string; v: string }) {
  return <div className="flex gap-2"><span className="text-fg/50">{k}:</span><span className="font-medium">{v}</span></div>;
}

function Table({ title, heads, children }: { title: string; heads: string[]; children: React.ReactNode }) {
  return (
    <section className="mt-6">
      <h2 className="text-sm font-semibold">{title}</h2>
      <table className="mt-2 w-full border-collapse text-xs">
        <thead><tr>{heads.map((h) => <th key={h} className="border border-fg/15 px-2 py-1 text-left font-medium text-fg/60">{h}</th>)}</tr></thead>
        <tbody>{children}</tbody>
      </table>
    </section>
  );
}

function Td({ children, right }: { children: React.ReactNode; right?: boolean }) {
  return <td className={`border border-fg/15 px-2 py-1 ${right ? "text-right tabular-nums" : ""}`}>{children}</td>;
}

function Empty({ cols }: { cols: number }) {
  return <tr><td colSpan={cols} className="border border-fg/15 px-2 py-1 text-fg/40">Nil</td></tr>;
}
