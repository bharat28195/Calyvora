"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, Printer } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { TaxCatalogEntry, TaxComputation, TaxDeclaration, TaxItem } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";
import { formatDate } from "@/lib/format";

/**
 * Form 124 (formerly 12BB), laid out as the Income-tax Rules, 2026 print it: one four-column table —
 * serial, nature of claim, amount, evidence — with rent for HRA, leave travel, interest on borrowing
 * and the Chapter VIII deductions in that order, then the employee's verification. Printed or saved as
 * PDF from the browser. HR opens anyone's with ?employee=.
 */
export default function Form124Page() {
  return <Suspense fallback={null}><Form124 /></Suspense>;
}

const LENDER: Record<string, string> = { FINANCIAL_INSTITUTION: "(a) Financial institution", EMPLOYER: "(b) Employer", OTHER: "(c) Others" };
const SKIP = new Set(["LTA", "PROFESSIONAL_TAX_OTHER", "HRA_EXEMPTION"]);

function Form124() {
  const params = useSearchParams();
  const employee = params.get("employee");
  const year = params.get("year") ?? undefined;
  const [d, setD] = useState<TaxDeclaration | null>(null);
  const [c, setC] = useState<TaxComputation | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (employee
      ? Promise.all([api.employeeTaxDeclaration(employee, year), api.employeeTaxComputation(employee, year)])
      : Promise.all([api.taxDeclaration(year), api.taxComputation(year)]))
      .then(([decl, comp]) => { setD(decl); setC(comp); })
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load the declaration"));
  }, [employee, year]);

  if (error) return <Alert tone="error" className="mt-6">{error}</Alert>;
  if (!d || !c) return <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;

  const cat = new Map(d.catalog.map((x) => [x.key, x]));
  const lta = d.items.find((i) => i.key === "LTA");
  const epf = c.deductions.find((x) => x.key === "EPF");
  const claims = d.items.filter((i) => { const e = cat.get(i.key); return e && !e.income && !SKIP.has(i.key) && i.amount > 0; });
  const sec123 = claims.filter((i) => cat.get(i.key)!.section === "123");
  const sec124 = claims.filter((i) => cat.get(i.key)!.section === "124(1)");
  const others = claims.filter((i) => !sec123.includes(i) && !sec124.includes(i));

  // One block per landlord: the form asks for the rent paid to each, with their name, address and PAN.
  const landlords = new Map<string, { name: string | null; address: string | null; pan: string | null; rent: number }>();
  for (const r of d.rent) {
    const k = `${r.landlordName ?? ""}|${r.landlordPan ?? ""}`;
    const row = landlords.get(k) ?? { name: r.landlordName, address: r.landlordAddress, pan: r.landlordPan, rent: 0 };
    row.rent += r.monthlyRent * months(r.fromMonth, r.toMonth);
    landlords.set(k, row);
  }
  const fy = d.financialYear.split("-");
  const yearText = `${fy[0]} - ${Number(fy[0]) + 1}`;

  return (
    <div>
      <div className="flex items-center justify-between gap-3 print:hidden">
        <Link href={employee ? `/finance/tax/manage/${employee}` : "/finance/tax"} className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg">
          <ArrowLeft className="h-4 w-4" /> Back
        </Link>
        <Button variant="secondary" size="sm" onClick={() => window.print()}><Printer className="h-4 w-4" /> Print or save as PDF</Button>
      </div>

      <article className="payslip-sheet mx-auto mt-4 max-w-3xl rounded-xl border border-fg/10 bg-white p-8 text-[13px] text-black">
        <header className="text-center">
          <p className="font-bold">INCOME-TAX RULES, 2026</p>
          <p>FORM NO. 124</p>
          <p className="text-xs italic">(See rule 205)</p>
          <p className="mt-1 font-bold">Statement showing particulars of claims by an employee for deduction of tax under section 392(5)(b)</p>
        </header>

        <div className="mt-5 space-y-0.5">
          <p>1. Name and address of the employee: <strong>{d.employeeName}</strong>{d.employeeAddress ? `, ${d.employeeAddress}` : ""}</p>
          <p>2. Permanent Account Number: <strong>{d.employeePan ?? "—"}</strong></p>
          <p>3. Financial year: <strong>{yearText}</strong></p>
        </div>

        <table className="mt-4 w-full border-collapse border border-black">
          <thead>
            <tr><th colSpan={4} className="border border-black py-1 text-sm font-bold">DETAILS OF CLAIMS AND EVIDENCE THEREOF</th></tr>
            <tr>
              <Th className="w-14">Sl. No</Th><Th>Nature of Claim</Th><Th className="w-28">Amount (Rs.)</Th><Th className="w-44">Evidence / particulars</Th>
            </tr>
            <tr className="text-xs italic"><Td center>(1)</Td><Td center>(2)</Td><Td center>(3)</Td><Td center>(4)</Td></tr>
          </thead>
          <tbody>
            {/* 1. HRA */}
            <Block n="1." title="House Rent Allowance">
              {landlords.size === 0 ? <Line text="(i) Rent paid to the landlord" amount={0} /> : [...landlords.values()].map((l, i) => (
                <div key={i} className={i > 0 ? "mt-2 border-t border-dashed border-black/30 pt-2" : ""}>
                  <Line text="(i) Rent paid to the landlord" amount={l.rent} />
                  <Line text="(ii) Name of the landlord" evidence={l.name} />
                  <Line text="(iii) Address of the landlord" evidence={l.address} />
                  <Line text="(iv) Permanent Account Number of the landlord" evidence={l.pan} />
                </div>
              ))}
              <p className="mt-2 text-[11px]">Note: Permanent Account Number shall be furnished if the aggregate rent paid during the previous year exceeds one lakh rupees</p>
            </Block>

            {/* 2. LTA */}
            <Block n="2." title="Leave travel concessions or assistance" amount={lta?.amount ?? 0} evidence={lta?.detail} />

            {/* 3. Interest on borrowing */}
            <Block n="3." title="Deduction of interest on borrowing:">
              {d.houses.length === 0 ? <>
                <Line text="(i) Interest payable/paid to the lender" />
                <Line text="(ii) Name of the lender" /><Line text="(iii) Address of the lender" />
                <Line text="(iv) Permanent Account Number of the lender" />
                <p className="pl-4 text-[12px]">(a) Financial Institutions (if available)<br />(b) Employer (if available)<br />(c) Others</p>
              </> : d.houses.map((h, i) => (
                <div key={h.id} className={i > 0 ? "mt-2 border-t border-dashed border-black/30 pt-2" : ""}>
                  <Line text={`(i) Interest payable/paid to the lender${h.letOut ? " — let-out house" : ""}`} amount={h.interest} />
                  <Line text="(ii) Name of the lender" evidence={h.lenderName} />
                  <Line text="(iii) Address of the lender" evidence={h.lenderAddress} />
                  <Line text="(iv) Permanent Account Number of the lender" evidence={h.lenderPan ? `${h.lenderPan}${h.lenderType ? ` · ${LENDER[h.lenderType]}` : ""}` : h.lenderType ? LENDER[h.lenderType] : null} />
                </div>
              ))}
            </Block>

            {/* 4. Chapter VI-A */}
            <Block n="4." title="Deductions under Chapter VI-A">
              <p className="mt-1 pl-2">(A) Sections 123 and 124</p>
              <p className="mt-1 pl-4">(1) Section 123 (Formerly 80C)</p>
              {epf && epf.declared > 0 || sec123.length > 0 ? (
                <div className="pl-6">
                  {lettered([
                    ...(epf && epf.declared > 0 ? [{ text: "EPF (Deducted from Salary)", amount: epf.declared }] : []),
                    ...sec123.map((i) => itemLine(i, cat.get(i.key)!)),
                  ])}
                </div>
              ) : <p className="pl-6 text-black/50">Nil</p>}
              {sec124.length > 0 && <>
                <p className="mt-1 pl-4">(2) Section 124 (Formerly 80CCD(1))</p>
                <div className="pl-6">{lettered(sec124.map((i) => itemLine(i, cat.get(i.key)!)))}</div>
              </>}
              <p className="mt-2 pl-2">(B) Other sections (eg. 129, 133, 153 ..etc) under VI-A</p>
              {others.length > 0
                ? <div className="pl-6">{lettered(others.map((i) => {
                    const e = cat.get(i.key)!;
                    return { text: `Section ${e.section} (Formerly ${e.oldSection}) — ${e.label}`, amount: i.amount, evidence: evidenceOf(i) };
                  }))}</div>
                : <p className="pl-6 text-black/50">Nil</p>}
            </Block>

            <tr><td colSpan={4} className="border border-black py-1 text-center font-bold">Verification</td></tr>
            <tr>
              <td className="border border-black" />
              <td colSpan={3} className="border border-black px-2 py-2">
                I, <u>{d.employeeName}</u>, son/daughter of <u>{d.parentName ?? "........................"}</u> do hereby certify that the information given above is complete and correct.
              </td>
            </tr>
            <tr>
              <td className="border border-black" />
              <td colSpan={2} className="border border-black px-2 pb-2 pt-8">Place....................................</td>
              <td className="border border-black" />
            </tr>
            <tr>
              <td className="border border-black" />
              <td colSpan={2} className="border border-black px-2 py-2">Date: {d.submittedAt ? formatDate(d.submittedAt) : "...................."}</td>
              <td className="border border-black px-2 py-2 text-[11px]">(signature of employee)</td>
            </tr>
            <tr>
              <td className="border border-black" />
              <td colSpan={2} className="border border-black px-2 py-2">Designation: {d.designation ?? "...................."}</td>
              <td className="border border-black px-2 py-2 text-[11px]">Full Name: {d.employeeName}</td>
            </tr>
          </tbody>
        </table>

        <p className="mt-3 text-[11px] text-black/60 print:hidden">
          On these claims the tax for the year works out to ₹{c.totalTax.toLocaleString("en-IN")} under the {c.regime === "NEW" ? "new" : "old"} regime.
          Claims are allowed up to the limits in the Act; under the new regime most of them do not reduce tax.
        </p>
      </article>
    </div>
  );
}

type LineSpec = { text: string; amount?: number; evidence?: string | null };

function itemLine(i: TaxItem, e: TaxCatalogEntry): LineSpec {
  return { text: e.label, amount: i.amount, evidence: evidenceOf(i) };
}

function evidenceOf(i: TaxItem) {
  const bits = [i.detail, i.proofs.length ? `${i.proofs.length} proof${i.proofs.length === 1 ? "" : "s"} attached` : null].filter(Boolean);
  return bits.length ? bits.join(" · ") : null;
}

function lettered(lines: LineSpec[]) {
  return lines.map((l, i) => <Line key={i} text={`${String.fromCharCode(97 + i)}. ${l.text}`} amount={l.amount} evidence={l.evidence} />);
}

/** A numbered row of the claims table whose amount and evidence are drawn per line inside it. */
function Block({ n, title, amount, evidence, children }: { n: string; title: string; amount?: number; evidence?: string | null; children?: React.ReactNode }) {
  return (
    <tr className="align-top">
      <Td center>{n}</Td>
      <td colSpan={children ? 3 : 1} className="border border-black px-2 py-1.5">
        <p>{title}</p>
        {children}
      </td>
      {!children && <><Td right>{fmt(amount ?? 0)}</Td><Td>{evidence ?? ""}</Td></>}
    </tr>
  );
}

/** One line inside a block: text on the left, amount and evidence in the form's own columns. */
function Line({ text, amount, evidence }: { text: string; amount?: number; evidence?: string | null }) {
  return (
    <div className="grid grid-cols-[1fr_7rem_11rem] gap-0 py-0.5">
      <span className="pl-2">{text}</span>
      <span className="pr-1 text-right tabular-nums">{amount != null ? fmt(amount) : ""}</span>
      <span className="pl-3 break-words">{evidence ?? ""}</span>
    </div>
  );
}

function fmt(n: number) {
  return Number.isInteger(n) ? String(n) : n.toFixed(2);
}

function months(from: string, to: string) {
  const [fy, fm] = from.split("-").map(Number);
  const [ty, tm] = to.split("-").map(Number);
  return (ty - fy) * 12 + (tm - fm) + 1;
}

function Th({ children, className }: { children: React.ReactNode; className?: string }) {
  return <th className={`border border-black px-2 py-1 text-center font-normal ${className ?? ""}`}>{children}</th>;
}

function Td({ children, right, center }: { children?: React.ReactNode; right?: boolean; center?: boolean }) {
  return <td className={`border border-black px-2 py-1 ${right ? "text-right tabular-nums" : ""} ${center ? "text-center" : ""}`}>{children}</td>;
}
