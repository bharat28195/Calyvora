"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Check, FileText, Loader2, RotateCcw, X } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type { ProofStatus, TaxComputation, TaxDeclaration, TaxProofFile, TaxReviewInput } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardTitle } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Input } from "@/components/ui/input";
import { money, formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import { MoneyInput, ProofBadge, num, openProof } from "@/components/tax/bits";
import { TaxWorking } from "@/components/tax/working";

/**
 * One person's declaration, as HR reviews it: every claim with its proof beside it, and the three
 * answers HR can give — accept it, accept part of it, or reject it with a reason the employee can act
 * on. The tax working is on the same screen, so the effect of each decision is visible at once.
 */
export default function ReviewTaxPage() {
  const { employeeId } = useParams<{ employeeId: string }>();
  const [d, setD] = useState<TaxDeclaration | null>(null);
  const [c, setC] = useState<TaxComputation | null>(null);
  const [tab, setTab] = useState<"claims" | "working">("claims");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [decl, comp] = await Promise.all([api.employeeTaxDeclaration(employeeId), api.employeeTaxComputation(employeeId)]);
      setD(decl);
      setC(comp);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load this declaration");
    }
  }, [employeeId]);
  useEffect(() => { void load(); }, [load]);

  async function review(input: TaxReviewInput) {
    setError(null);
    try {
      setD(await api.reviewTax(employeeId, input));
      setC(await api.employeeTaxComputation(employeeId));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not save the review");
      throw e;
    }
  }

  async function reopen() {
    try { setD(await api.reopenTaxDeclaration(employeeId)); }
    catch (e) { setError(e instanceof ApiError ? e.message : "Could not reopen"); }
  }

  if (!d || !c) {
    return (
      <div>
        <Back />
        {error ? <Alert tone="error" className="mt-6">{error}</Alert>
          : <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>}
      </div>
    );
  }

  const label = new Map(d.catalog.map((x) => [x.key, x]));
  const claims = d.items.filter((i) => !label.get(i.key)?.income);
  const income = d.items.filter((i) => label.get(i.key)?.income);

  return (
    <div>
      <Back />
      <div className="mt-3 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{d.employeeName}</h1>
          <p className="mt-1 text-fg/50">
            Tax year {d.financialYear} · {d.regime === "NEW" ? "new" : "old"} regime ·{" "}
            {d.status === "SUBMITTED" ? `submitted ${d.submittedAt ? formatDate(d.submittedAt) : ""}` : d.status === "DRAFT" ? "draft" : "not started"}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {d.status === "SUBMITTED" && <Button variant="secondary" onClick={() => void reopen()}><RotateCcw className="h-4 w-4" /> Reopen for changes</Button>}
          <Link href={`/finance/tax/form124?employee=${employeeId}`}><Button variant="ghost"><FileText className="h-4 w-4" /> Form 124</Button></Link>
          <Link href={`/finance/tax/form130?employee=${employeeId}`}><Button variant="ghost"><FileText className="h-4 w-4" /> Form 130</Button></Link>
        </div>
      </div>

      {error && <Alert tone="error" className="mt-4">{error}</Alert>}

      <div className="mt-6 grid gap-4 sm:grid-cols-3">
        <Card><p className="text-xs text-fg/50">Tax for the year</p><p className="mt-1 text-xl font-semibold tabular-nums">{money(c.totalTax)}</p></Card>
        <Card><p className="text-xs text-fg/50">Deductions allowed</p><p className="mt-1 text-xl font-semibold tabular-nums">{money(c.totalDeductions)}</p></Card>
        <Card><p className="text-xs text-fg/50">Next payslip</p><p className="mt-1 text-xl font-semibold tabular-nums">{money(c.projectedNextMonth)}</p></Card>
      </div>

      <div className="mt-6 flex gap-1 border-b border-fg/10">
        {(["claims", "working"] as const).map((t) => (
          <button key={t} onClick={() => setTab(t)}
            className={cn("-mb-px border-b-2 px-3 py-2 text-sm", tab === t ? "border-violet font-medium" : "border-transparent text-fg/50 hover:text-fg")}>
            {t === "claims" ? "Claims and proofs" : "Tax working"}
          </button>
        ))}
      </div>

      {tab === "working" ? <div className="mt-4"><TaxWorking c={c} /></div> : (
        <div className="mt-4 flex flex-col gap-4">
          {claims.length === 0 && d.rent.length === 0 && d.houses.length === 0 && !d.previous && (
            <Card><p className="text-sm text-fg/50">Nothing claimed.</p></Card>
          )}

          {claims.length > 0 && (
            <Card>
              <CardTitle>Deductions and exemptions</CardTitle>
              <div className="mt-2 divide-y divide-fg/5">
                {claims.map((i) => (
                  <ReviewRow key={i.key} title={label.get(i.key)?.label ?? i.key} sub={label.get(i.key)?.sectionLabel}
                    detail={i.detail} amount={i.amount} status={i.proofStatus} accepted={i.acceptedAmount} note={i.reviewNote}
                    proofs={i.proofs} onReview={(s, a, n) => review({ type: "ITEM", id: i.key, status: s, acceptedAmount: a, note: n })} />
                ))}
              </div>
            </Card>
          )}

          {d.rent.length > 0 && (
            <Card>
              <CardTitle>Rent (HRA)</CardTitle>
              <div className="mt-2 divide-y divide-fg/5">
                {d.rent.map((r) => (
                  <ReviewRow key={r.id} title={`${r.city}${r.metro ? " · metro" : ""} · ${r.fromMonth} to ${r.toMonth}`}
                    sub={`Landlord ${r.landlordName ?? "—"}${r.landlordPan ? ` (${r.landlordPan})` : ""}${r.landlordRelationship ? ` · ${r.landlordRelationship}` : ""}`}
                    amount={r.monthlyRent} amountSuffix="a month" status={r.proofStatus} accepted={r.acceptedRent} note={r.reviewNote}
                    proofs={r.proofs} onReview={(s, a, n) => review({ type: "RENT", id: r.id, status: s, acceptedAmount: a, note: n })} />
                ))}
              </div>
            </Card>
          )}

          {d.houses.length > 0 && (
            <Card>
              <CardTitle>Home loans</CardTitle>
              <div className="mt-2 divide-y divide-fg/5">
                {d.houses.map((h) => (
                  <ReviewRow key={h.id} title={`${h.letOut ? "Let out" : "Lives in it"}${h.address ? ` · ${h.address}` : ""}`}
                    sub={`Lender ${h.lenderName ?? "—"}${h.letOut ? ` · rent ${money(h.annualRent)}` : ""}`}
                    amount={h.interest} amountSuffix="interest" status={h.proofStatus} accepted={h.acceptedInterest} note={h.reviewNote}
                    proofs={h.proofs} onReview={(s, a, n) => review({ type: "HOUSE", id: h.id, status: s, acceptedAmount: a, note: n })} />
                ))}
              </div>
            </Card>
          )}

          {d.previous && (
            <Card>
              <CardTitle>Previous employer</CardTitle>
              <div className="mt-2">
                <ReviewRow title={d.previous.employerName ?? "Previous employer"}
                  sub={`TAN ${d.previous.tan ?? "—"} · tax deducted ${money(d.previous.tds)} · PF ${money(d.previous.pf)}`}
                  amount={d.previous.income ?? 0} amountSuffix="salary" wholeOnly
                  status={d.previous.status === "NONE" ? "NONE" : d.previous.status as ProofStatus} note={d.previous.reviewNote}
                  proofs={d.previous.proofs} onReview={(s, _a, n) => review({ type: "PREVIOUS", status: s, note: n })} />
              </div>
            </Card>
          )}

          {income.length > 0 && (
            <Card>
              <CardTitle>Other income declared</CardTitle>
              <dl className="mt-2 space-y-1.5 text-sm">
                {income.map((i) => (
                  <div key={i.key} className="flex justify-between"><dt>{label.get(i.key)?.label}</dt><dd className="tabular-nums">{money(i.amount)}</dd></div>
                ))}
              </dl>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}

function Back() {
  return (
    <Link href="/finance/tax/manage" className="inline-flex items-center gap-1 text-sm text-fg/50 hover:text-fg">
      <ArrowLeft className="h-4 w-4" /> Manage tax
    </Link>
  );
}

/** One claim and HR's three answers to it. */
function ReviewRow({ title, sub, detail, amount, amountSuffix, status, accepted, note, proofs, wholeOnly, onReview }: {
  title: string; sub?: string; detail?: string | null; amount: number; amountSuffix?: string;
  status: ProofStatus; accepted?: number | null; note?: string | null; proofs: TaxProofFile[]; wholeOnly?: boolean;
  onReview: (status: "ACCEPTED" | "PARTIAL" | "REJECTED", amount: number | null, note: string | null) => Promise<void>;
}) {
  const [mode, setMode] = useState<"PARTIAL" | "REJECTED" | null>(null);
  const [part, setPart] = useState("");
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  async function act(s: "ACCEPTED" | "PARTIAL" | "REJECTED") {
    setBusy(true);
    try {
      await onReview(s, s === "PARTIAL" ? num(part) : null, s === "ACCEPTED" ? null : reason || null);
      setMode(null); setPart(""); setReason("");
    } catch { /* shown above */ } finally { setBusy(false); }
  }

  return (
    <div className="py-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-medium">{title}</p>
          {sub && <p className="text-xs text-fg/45">{sub}</p>}
          {detail && <p className="text-xs text-fg/45">{detail}</p>}
        </div>
        <div className="text-right">
          <p className="tabular-nums">{money(amount)} <span className="text-xs text-fg/40">{amountSuffix ?? "declared"}</span></p>
          <ProofBadge status={status} accepted={accepted} />
        </div>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
        {proofs.length === 0 ? <span className="text-fg/40">No proof uploaded</span> : proofs.map((p) => (
          <button key={p.id} onClick={() => void openProof(p.id)}
            className="inline-flex items-center gap-1 rounded-md border border-fg/10 px-1.5 py-0.5 text-fg/70 hover:border-violet hover:text-violet">
            <FileText className="h-3 w-3" />{p.fileName}
          </button>
        ))}
        <span className="flex-1" />
        <Button size="sm" variant="secondary" disabled={busy} onClick={() => void act("ACCEPTED")}><Check className="h-3.5 w-3.5" /> Accept</Button>
        {!wholeOnly && <Button size="sm" variant="ghost" disabled={busy} onClick={() => setMode(mode === "PARTIAL" ? null : "PARTIAL")}>Part</Button>}
        <Button size="sm" variant="ghost" disabled={busy} onClick={() => setMode(mode === "REJECTED" ? null : "REJECTED")}><X className="h-3.5 w-3.5" /> Reject</Button>
      </div>
      {note && <p className="mt-1 text-xs text-fg/50">Last note: {note}</p>}
      {mode && (
        <div className="mt-2 flex flex-wrap items-end gap-2 rounded-lg bg-fg/[0.03] p-2">
          {mode === "PARTIAL" && <div className="w-40"><p className="mb-1 text-xs text-fg/50">Amount accepted</p><MoneyInput value={part} onChange={setPart} /></div>}
          <div className="min-w-[14rem] flex-1">
            <p className="mb-1 text-xs text-fg/50">{mode === "REJECTED" ? "Reason (the employee sees this)" : "Note (optional)"}</p>
            <Input value={reason} onChange={(e) => setReason(e.target.value)} placeholder={mode === "REJECTED" ? "e.g. Receipt is for last year" : ""} />
          </div>
          <Button size="sm" disabled={busy || (mode === "REJECTED" && !reason.trim()) || (mode === "PARTIAL" && !num(part))}
            onClick={() => void act(mode)}>{busy && <Loader2 className="h-3.5 w-3.5 animate-spin" />} Save</Button>
        </div>
      )}
    </div>
  );
}
