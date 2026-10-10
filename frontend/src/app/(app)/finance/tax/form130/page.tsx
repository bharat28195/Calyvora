"use client";

import { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Loader2, Printer } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { Form130, TaxDeductionRow } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/alert";
import { formatDate } from "@/lib/format";
import { rupeesInWords } from "@/lib/words";
import { cn } from "@/lib/utils";

/**
 * Form 130 (formerly Form 16), both parts, numbered line for line as TRACES issues them.
 *
 * <p>Part A — tax deducted and deposited, quarter by quarter and challan by challan — is officially
 * downloaded from TRACES; Orbit's copy is built from payroll and the challans HR recorded, so the two
 * can be checked against each other before the real one is issued. Part B — the salary and tax
 * working — is the employer's own statement and is complete here.
 */
export default function Form130Page() {
  return <Suspense fallback={null}><Form130Sheet /></Suspense>;
}

function Form130Sheet() {
  const employee = useSearchParams().get("employee");
  const [f, setF] = useState<Form130 | null>(null);
  const [part, setPart] = useState<"A" | "B" | "AB">("AB");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    (employee ? api.employeeForm130(employee) : api.myForm130())
      .then(setF)
      .catch((e) => setError(e instanceof ApiError ? e.message : "Could not load Form 130"));
  }, [employee]);

  if (error) return <Alert tone="error" className="mt-6">{error}</Alert>;
  if (!f) return <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>;

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 print:hidden">
        <Link href={employee ? `/finance/tax/manage/${employee}` : "/finance/tax/computation"} className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg">
          <ArrowLeft className="h-4 w-4" /> Back
        </Link>
        <div className="flex items-center gap-2">
          <div className="flex rounded-lg bg-fg/5 p-0.5 text-sm">
            {([["AB", "Both parts"], ["A", "Part A"], ["B", "Part B"]] as const).map(([k, l]) => (
              <button key={k} onClick={() => setPart(k)} className={cn("rounded-md px-3 py-1", part === k ? "bg-surface font-medium shadow-sm" : "text-fg/60")}>{l}</button>
            ))}
          </div>
          <Button variant="secondary" size="sm" onClick={() => window.print()}><Printer className="h-4 w-4" /> Print or save as PDF</Button>
        </div>
      </div>

      {part !== "B" && <PartA f={f} />}
      {part !== "A" && <PartB f={f} breakBefore={part === "AB"} />}
    </div>
  );
}

// ---- the header both parts share -----------------------------------------------------------------

function Header({ f, part, title }: { f: Form130; part: "A" | "B"; title: string }) {
  return (
    <>
      <div className="border border-black text-center">
        <p className="py-1 text-base font-bold">FORM NO. 130</p>
        {part === "A" && <p className="border-t border-black py-0.5 text-xs">[See rule 215]</p>}
        <p className="border-t border-black py-0.5 font-bold">PART {part}</p>
        <p className="border-t border-black px-2 py-1 text-xs font-bold">{title}</p>
      </div>
      <table className="w-full border-collapse text-[12px]">
        <tbody>
          <tr>
            <td colSpan={2} className="border border-black px-2 py-1">Last updated on <strong>{formatDate(new Date().toISOString())}</strong></td>
            <td colSpan={3} className="border border-black px-2 py-1 text-right">Employee ref. no. <strong>{f.employeeNo ?? "—"}</strong></td>
          </tr>
          <tr>
            <Head colSpan={2}>Name and address of the Employer</Head>
            <Head colSpan={3}>Name and address of the Employee</Head>
          </tr>
          <tr className="align-top">
            <td colSpan={2} className="border border-black px-2 py-2 whitespace-pre-line">{f.employerName}{f.employerAddress ? `\n${f.employerAddress}` : ""}</td>
            <td colSpan={3} className="border border-black px-2 py-2 whitespace-pre-line">{f.employeeName.toUpperCase()}{f.employeeAddress ? `\n${f.employeeAddress}` : ""}</td>
          </tr>
          <tr>
            <Head>PAN of the Deductor</Head><Head>TAN of the Deductor</Head><Head colSpan={3}>PAN of the Employee</Head>
          </tr>
          <tr className="text-center">
            <Cell>{f.employerPan ?? "—"}</Cell><Cell>{f.employerTan ?? "—"}</Cell><Cell colSpan={3}>{f.employeePan ?? "PANNOTAVBL"}</Cell>
          </tr>
          <tr>
            <Head colSpan={2} rowSpan={2}>CIT (TDS)</Head><Head rowSpan={2}>Tax Year</Head><Head colSpan={2}>Period with the Employer</Head>
          </tr>
          <tr><Head>From</Head><Head>To</Head></tr>
          <tr className="text-center">
            <Cell colSpan={2}>{f.citTdsAddress ?? "—"}</Cell><Cell>{f.financialYear}</Cell>
            <Cell>{formatDate(f.periodFrom)}</Cell><Cell>{formatDate(f.periodTo)}</Cell>
          </tr>
        </tbody>
      </table>
    </>
  );
}

// ---- Part A ----------------------------------------------------------------------------------

function PartA({ f }: { f: Form130 }) {
  const paid = f.quarters.reduce((n, q) => n + q.amountPaid, 0);
  const tds = f.quarters.reduce((n, q) => n + q.tds, 0);
  const deposited = f.quarters.reduce((n, q) => n + q.deposited, 0);
  const shown = f.quarters.filter((q) => q.amountPaid > 0 || q.tds > 0);
  const challanTotal = f.challans.filter((c) => c.bsrCode).reduce((n, c) => n + c.tds, 0);
  const missing = f.challans.filter((c) => !c.bsrCode).length;

  return (
    <article className="payslip-sheet mx-auto mt-4 max-w-4xl bg-white p-6 text-[12px] text-black shadow-sm">
      <p className="mb-2 rounded border border-amber-400 bg-amber-50 px-2 py-1 text-[11px] text-amber-900 print:hidden">
        Draft from payroll. The Part A to issue is the one downloaded from TRACES — use this to check it before you do.
        {missing > 0 && ` ${missing} month${missing === 1 ? "" : "s"} of tax ${missing === 1 ? "has" : "have"} no challan recorded yet (Manage tax → TDS deposits).`}
      </p>
      <Header f={f} part="A" title="Certificate under the Income-tax Act, 2025 for tax deducted at source on salary paid to an employee under section 392" />

      <table className="w-full border-collapse">
        <tbody>
          <tr><td colSpan={5} className="border border-black py-1 text-center font-bold">Summary of amount paid/credited and tax deducted at source thereon in respect of the employee</td></tr>
          <tr className="align-middle">
            <Head>Quarter(s)</Head>
            <Head>Receipt Numbers of original quarterly statements of TDS</Head>
            <Head>Amount paid/credited</Head>
            <Head>Amount of tax deducted (Rs.)</Head>
            <Head>Amount of tax deposited / remitted (Rs.)</Head>
          </tr>
          {shown.length === 0 ? <tr><Cell colSpan={5} center>No finalised months yet</Cell></tr> : shown.map((q) => (
            <tr key={q.quarter}>
              <Cell>{q.label.split(" ")[0]}</Cell><Cell center>{q.receiptNo ?? "—"}</Cell>
              <Cell right>{rs(q.amountPaid)}</Cell><Cell right>{rs(q.tds)}</Cell><Cell right>{rs(q.deposited)}</Cell>
            </tr>
          ))}
          <tr className="font-bold"><Cell>Total (Rs.)</Cell><td className="border border-black bg-black/10" /><Cell right>{rs(paid)}</Cell><Cell right>{rs(tds)}</Cell><Cell right>{rs(deposited)}</Cell></tr>
        </tbody>
      </table>

      <p className="mt-3 text-center font-bold">I. DETAILS OF TAX DEDUCTED AND DEPOSITED IN THE CENTRAL GOVERNMENT ACCOUNT THROUGH BOOK ADJUSTMENT</p>
      <p className="text-center text-[11px]">(The deductor to provide payment wise details of tax deducted and deposited with respect to the deductee)</p>
      <table className="mt-1 w-full border-collapse"><tbody>
        <tr><Head>Sl. No.</Head><Head>Tax Deposited in respect of the deductee (Rs.)</Head><Head>Receipt Numbers of Form No. 24G</Head><Head>DDO serial number in Form no. 24G</Head><Head>Date of transfer voucher</Head></tr>
        <tr><Cell colSpan={5} center>Not applicable — for government deductors only</Cell></tr>
      </tbody></table>

      <p className="mt-3 text-center font-bold">II. DETAILS OF TAX DEDUCTED AND DEPOSITED IN THE CENTRAL GOVERNMENT ACCOUNT THROUGH CHALLAN</p>
      <p className="text-center text-[11px]">(The deductor to provide payment wise details of tax deducted and deposited with respect to the deductee)</p>
      <table className="mt-1 w-full border-collapse"><tbody>
        <tr>
          <Head rowSpan={2}>Sl. No.</Head><Head rowSpan={2}>Tax Deposited in respect of the deductee (Rs.)</Head>
          <Head colSpan={3}>Challan Identification Number (CIN)</Head>
        </tr>
        <tr><Head>BSR Code of the Bank Branch</Head><Head>Date on which Tax deposited (dd/mm/yyyy)</Head><Head>Challan Serial Number</Head></tr>
        {f.challans.length === 0 ? <tr><Cell colSpan={5} center>Nil</Cell></tr> : f.challans.map((c, i) => (
          <tr key={c.month} className={c.bsrCode ? "" : "text-black/50"}>
            <Cell center>{i + 1}</Cell><Cell right>{rs(c.tds)}</Cell>
            <Cell center>{c.bsrCode ?? "Not recorded"}</Cell>
            <Cell center>{c.depositDate ? ddmmyyyy(c.depositDate) : "—"}</Cell>
            <Cell center>{c.challanSerial ?? "—"}</Cell>
          </tr>
        ))}
        <tr className="font-bold"><Cell>Total (Rs.)</Cell><Cell right>{rs(challanTotal)}</Cell><td colSpan={3} className="border border-black bg-black/10" /></tr>
      </tbody></table>

      <table className="mt-3 w-full border-collapse"><tbody>
        <tr><td colSpan={2} className="border border-black py-1 text-center font-bold">Verification</td></tr>
        <tr><td colSpan={2} className="border border-black px-2 py-2 leading-relaxed">
          I, <u>{f.signer.name ?? "...................."}</u>, son / daughter of <u>{f.signer.parent ?? "...................."}</u> working in the capacity of{" "}
          <u>{f.signer.designation ?? "...................."}</u> (designation) do hereby certify that a sum of Rs. <u>{rs(tds)}</u> [Rs. <u>{rupeesInWords(tds)}</u> (in words)]
          has been deducted and a sum of Rs. <u>{rs(challanTotal)}</u> [Rs. <u>{rupeesInWords(challanTotal)}</u>] has been deposited to the credit of the Central Government.
          I further certify that the information given above is true, complete and correct and is based on the books of account, documents, TDS statements, TDS deposited and other available records.
        </td></tr>
        <Signature f={f} />
      </tbody></table>
      <p className="mt-2 text-[11px]">Status of matching with OLTAS is shown on the certificate TRACES issues.</p>
    </article>
  );
}

// ---- Part B ----------------------------------------------------------------------------------

function PartB({ f, breakBefore }: { f: Form130; breakBefore: boolean }) {
  const c = f.computation;
  const fromOthers = c.previousEmployerIncome;
  const salary = c.grossSalary - fromOthers;
  const ex = (keys: string[]) => c.exemptions.filter((e) => keys.includes(e.key)).reduce((n, e) => n + e.allowed, 0);
  const lta = ex(["LTA"]);
  const hra = ex(["HRA", "HRA_EXEMPTION"]);
  const otherEx = c.exemptions.reduce((n, e) => n + e.allowed, 0) - lta - hra;
  const totalEx = lta + hra + otherEx;
  const sec16 = c.standardDeduction + c.professionalTax;

  // Chapter VIII lines, bucketed by the 1961 section the certificate still numbers them by.
  const by = (test: (old: string) => boolean) => c.deductions.filter((d) => test(oldOf(d)));
  const sum = (rows: TaxDeductionRow[], k: "declared" | "allowed") => rows.reduce((n, r) => n + r[k], 0);
  const s80c = by((o) => o === "80C"), s80ccc = by((o) => o === "80CCC"), s80ccd1 = by((o) => o === "80CCD(1)");
  const s1b = by((o) => o === "80CCD(1B)"), s2 = by((o) => o === "80CCD(2)"), s80d = by((o) => o === "80D");
  const s80e = by((o) => o === "80E"), s80cch = by((o) => o === "80CCH"), s80g = by((o) => o === "80G");
  const s80tta = by((o) => o === "80TTA" || o === "80TTB");
  const named = new Set([...s80c, ...s80ccc, ...s80ccd1, ...s1b, ...s2, ...s80d, ...s80e, ...s80cch, ...s80g, ...s80tta]);
  const rest = c.deductions.filter((d) => !named.has(d));
  const abc = [...s80c, ...s80ccc, ...s80ccd1];
  const net = c.totalTax;

  return (
    <article className={cn("payslip-sheet mx-auto mt-6 max-w-4xl bg-white p-6 text-[12px] text-black shadow-sm", breakBefore && "print:break-before-page")}>
      <Header f={f} part="B" title="Certificate under the Income-tax Act, 2025 for tax deducted at source on salary paid to an employee under section 392" />
      <p className="mt-1 text-right">Annexure - I</p>

      <table className="w-full border-collapse"><tbody>
        <tr><td colSpan={4} className="border border-black px-2 py-1">Details of Salary Paid and any other income and tax deducted</td></tr>
        <R n="A" k="Whether opting out of taxation u/s 202 (formerly 115BAC(1A))?" b={c.regime === "OLD" ? "Yes" : "No"} />
        <tr><Cell>1.</Cell><Cell>Gross Salary</Cell><Cell center>Rs.</Cell><Cell center>Rs.</Cell></tr>
        <R n="(a)" k="Salary as per provisions contained in section 17(1)" a={salary} />
        <R n="(b)" k="Value of perquisites under section 17(2) (as per Form No. 12BA, wherever applicable)" a={0} />
        <R n="(c)" k="Profits in lieu of salary under section 17(3) (as per Form No. 12BA, wherever applicable)" a={0} />
        <R n="(d)" k="Total" b={salary} />
        <R n="(e)" k="Reported total amount of salary received from other employer(s)" b={fromOthers} />
        <tr><Cell>2.</Cell><td colSpan={3} className="border border-black px-2 py-1">Less: Allowances to the extent exempt under section 10</td></tr>
        <R n="(a)" k="Travel concession or assistance under section 10(5)" a={lta} />
        <R n="(b)" k="Death-cum-retirement gratuity under section 10(10)" a={0} />
        <R n="(c)" k="Commuted value of pension under section 10(10A)" a={0} />
        <R n="(d)" k="Cash equivalent of leave salary encashment under section 10(10AA)" a={0} />
        <R n="(e)" k="House rent allowance under section 10(13A)" a={hra} />
        <R n="(f)" k="Other special allowances under section 10(14)" a={0} />
        <R n="(g)" k="Amount of any other exemption under section 10" a={otherEx} />
        <R n="(h)" k="Total amount of any other exemption under section 10" a={otherEx} />
        <R n="(i)" k="Total amount of exemption claimed under section 10 [2(a)+2(b)+2(c)+2(d)+2(e)+2(f)+2(h)]" b={totalEx} />
        <R n="3." k="Total amount of salary received from current employer [1(d)-2(i)]" b={salary - totalEx} />
        <tr><Cell>4.</Cell><td colSpan={3} className="border border-black px-2 py-1">Less: Deductions under section 16 (section 19 of the 2025 Act)</td></tr>
        <R n="(a)" k="Standard deduction under section 16(ia)" a={c.standardDeduction} />
        <R n="(b)" k="Entertainment allowance under section 16(ii)" a={0} />
        <R n="(c)" k="Tax on employment under section 16(iii)" a={c.professionalTax} />
        <R n="5." k="Total amount of deductions under section 16 [4(a)+4(b)+4(c)]" b={sec16} />
        <R n="6." k={`Income chargeable under the head "Salaries" [(3+1(e)-5]`} b={c.salaryIncome} />
        <tr><Cell>7.</Cell><td colSpan={3} className="border border-black px-2 py-1">Add: Any other income reported by the employee</td></tr>
        <R n="(a)" k="Income (or admissible loss) from house property reported by employee offered for TDS" a={c.houseProperty} />
        <R n="(b)" k="Income under the head Other Sources offered for TDS" a={c.otherIncome} />
        <R n="8." k="Total amount of other income reported by the employee [7(a)+7(b)]" b={c.houseProperty + c.otherIncome} />
        <R n="9." k="Gross total income (6+8)" b={c.grossTotalIncome} strong />
      </tbody></table>

      <table className="mt-0 w-full border-collapse"><tbody>
        <tr><Cell>10.</Cell><Cell>Deductions under Chapter VI-A (Chapter VIII of the 2025 Act)</Cell><Cell center>Gross Amount</Cell><Cell center>Deductible Amount</Cell></tr>
        <D n="(a)" k="Deduction in respect of life insurance premia, contributions to provident fund etc. under section 80C" rows={s80c} sum={sum} />
        <D n="(b)" k="Deduction in respect of contribution to certain pension funds under section 80CCC" rows={s80ccc} sum={sum} />
        <D n="(c)" k="Deduction in respect of contribution by taxpayer to pension scheme under section 80CCD (1)" rows={s80ccd1} sum={sum} />
        <D n="(d)" k="Total deduction under section 80C, 80CCC and 80CCD(1)" rows={abc} sum={sum} />
        <D n="(e)" k="Deductions in respect of amount paid/deposited to notified pension scheme under section 80CCD (1B)" rows={s1b} sum={sum} />
        <D n="(f)" k="Deduction in respect of contribution by Employer to pension scheme under section 80CCD (2)" rows={s2} sum={sum} />
        <D n="(g)" k="Deduction in respect of health insurance premia under section 80D" rows={s80d} sum={sum} />
        <D n="(h)" k="Deduction in respect of interest on loan taken for higher education under section 80E" rows={s80e} sum={sum} />
        <D n="(i)" k="Deduction in respect of contribution by the employee to Agnipath Scheme under section 80CCH" rows={s80cch} sum={sum} />
        <D n="(j)" k="Deduction in respect of contribution by the Central Government to Agnipath Scheme under section 80CCH" rows={[]} sum={sum} />
      </tbody></table>
      <table className="w-full border-collapse"><tbody>
        <tr><td className="w-10 border border-black" /><td className="border border-black" /><Head>Gross Amount</Head><Head>Qualifying Amount</Head><Head>Deductible Amount</Head></tr>
        <Q n="(k)" k="Total Deduction in respect of donations to certain funds, charitable institutions, etc. under section 80G" rows={s80g} sum={sum} />
        <Q n="(l)" k="Deduction in respect of interest on deposits in savings account under section 80TTA" rows={s80tta} sum={sum} />
        <tr><Cell>(m)</Cell><td colSpan={4} className="border border-black px-2 py-1">
          Amount Deductible under any other provision(s) of Chapter VI-A
          {rest.length > 0 && <ul className="mt-1 list-disc pl-5">{rest.map((r) => <li key={r.key}>{r.section} — {r.label}: gross {rs(r.declared)}, deductible {rs(r.allowed)}</li>)}</ul>}
        </td></tr>
        <Q n="(n)" k="Total of amount deductible under any other provision(s) of Chapter VI-A" rows={rest} sum={sum} />
      </tbody></table>

      <table className="w-full border-collapse"><tbody>
        <R n="11." k="Aggregate of deductible amount under Chapter VI-A [10(d)+10(e)+10(f)+10(g)+10(h)+10(i)+10(j)+10(k)+10(l)+10(n)]" b={c.totalDeductions} />
        <R n="12." k="Total taxable income (9-11)" b={c.taxableIncome} strong />
        <R n="13." k="Tax on total income" b={c.taxOnIncome} />
        <R n="14." k="Rebate under section 87A (section 156 of the 2025 Act), if applicable" b={c.rebate} />
        <R n="15." k="Surcharge, wherever applicable" b={c.surcharge} />
        <R n="16." k="Health and education cess" b={c.cess} />
        <R n="17." k="Tax payable (13+15+16-14)" b={c.totalTax} />
        <R n="18." k="Less: Relief under section 89 (attach details)" b={0} />
        <R n="19." k="Less: Tax deducted at source as per Form No. 12BAA submitted under provisions of section 192(2B)" b={0} />
        <R n="20." k="Less: Tax collected at source as per Form No. 12BAA submitted under provisions of section 192(2B)" b={0} />
        <R n="21." k="Net tax payable (17-18-19-20)" b={net} strong />
      </tbody></table>

      <table className="mt-0 w-full border-collapse"><tbody>
        <tr><td colSpan={2} className="border border-black py-1 text-center">Verification</td></tr>
        <tr><td colSpan={2} className="border border-black px-2 py-2 leading-relaxed">
          I, <u>{f.signer.name ?? "...................."}</u>, son/daughter of <u>{f.signer.parent ?? "...................."}</u>. Working in the capacity of{" "}
          <u>{f.signer.designation ?? "...................."}</u> (Designation) do hereby certify that the information given above is true, complete and correct and is based on the books of account, documents, TDS statements, and other available records.
        </td></tr>
        <Signature f={f} />
      </tbody></table>
      <p className="mt-2 text-[11px] print:hidden">
        Section numbers are the 1961 Act&apos;s, as TRACES still prints them; the 2025 Act&apos;s equivalents are on the computation page.
        Months not yet finalised are projected and change if salary or declarations change.
      </p>
    </article>
  );
}

function Signature({ f }: { f: Form130 }) {
  return (
    <>
      <tr><td className="w-1/3 border border-black px-2 py-1.5">Place</td><td className="border border-black px-2 py-1.5">{f.signer.place ?? ""}</td></tr>
      <tr><td className="border border-black px-2 py-1.5">Date</td><td className="border border-black px-2 py-4 text-right text-[11px]">(Signature of person responsible for deduction of tax)</td></tr>
      <tr><td className="border border-black px-2 py-1.5">Designation: {f.signer.designation ?? ""}</td><td className="border border-black px-2 py-1.5">Full Name: {f.signer.name ?? ""}</td></tr>
    </>
  );
}

/** "Sec 124(3) (80CCD(1B))" → "80CCD(1B)": the 1961 number the certificate is keyed on. */
function oldOf(d: TaxDeductionRow) {
  const m = /\s\((.+)\)$/.exec(d.section ?? "");
  return m ? m[1] : "";
}

/** A row with the amount in the inner column (a) or the outer one (b). */
function R({ n, k, a, b, strong }: { n: string; k: string; a?: number | string; b?: number | string; strong?: boolean }) {
  return (
    <tr className={strong ? "font-bold" : ""}>
      <td className="w-10 border border-black px-2 py-1">{n}</td>
      <td className="border border-black px-2 py-1">{k}</td>
      <td className="w-32 border border-black px-2 py-1 text-right tabular-nums">{a === undefined ? "" : typeof a === "number" ? rs(a) : a}</td>
      <td className="w-32 border border-black px-2 py-1 text-right tabular-nums">{b === undefined ? "" : typeof b === "number" ? rs(b) : b}</td>
    </tr>
  );
}

type Sum = (rows: TaxDeductionRow[], k: "declared" | "allowed") => number;

function D({ n, k, rows, sum }: { n: string; k: string; rows: TaxDeductionRow[]; sum: Sum }) {
  return <R n={n} k={k} a={sum(rows, "declared")} b={sum(rows, "allowed")} />;
}

function Q({ n, k, rows, sum }: { n: string; k: string; rows: TaxDeductionRow[]; sum: Sum }) {
  return (
    <tr>
      <td className="w-10 border border-black px-2 py-1">{n}</td>
      <td className="border border-black px-2 py-1">{k}</td>
      <td className="w-24 border border-black px-2 py-1 text-right tabular-nums">{rs(sum(rows, "declared"))}</td>
      <td className="w-24 border border-black px-2 py-1 text-right tabular-nums">{rs(sum(rows, "allowed"))}</td>
      <td className="w-24 border border-black px-2 py-1 text-right tabular-nums">{rs(sum(rows, "allowed"))}</td>
    </tr>
  );
}

function Head({ children, colSpan, rowSpan }: { children: React.ReactNode; colSpan?: number; rowSpan?: number }) {
  return <td colSpan={colSpan} rowSpan={rowSpan} className="border border-black px-2 py-1 text-center font-bold">{children}</td>;
}

function Cell({ children, colSpan, right, center }: { children: React.ReactNode; colSpan?: number; right?: boolean; center?: boolean }) {
  return <td colSpan={colSpan} className={cn("border border-black px-2 py-1", right && "text-right tabular-nums", center && "text-center")}>{children}</td>;
}

/** Rupees as the certificate prints them: 170576.00. */
function rs(n: number) {
  return n.toFixed(2);
}

function ddmmyyyy(iso: string) {
  const [y, m, d] = iso.slice(0, 10).split("-");
  return `${d}-${m}-${y}`;
}
