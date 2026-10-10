"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import {
  ArrowLeft, ArrowRight, Building2, Check, FileText, HeartPulse, Home, Landmark, Loader2, Lock, Plus,
  PiggyBank, Receipt, Sparkles, Trash2, Wallet, BriefcaseBusiness, Scale,
} from "lucide-react";
import { api, ApiError } from "@/lib/api";
import type {
  TaxComputation, TaxDeclaration, TaxDeclarationInput, TaxRegime, LenderType,
} from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Alert } from "@/components/ui/alert";
import { Input } from "@/components/ui/input";
import { money, formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import { Meter, MoneyInput, Proofs, num } from "@/components/tax/bits";

/*
 * The tax declaration (Form 124), as a short guided walk rather than one long form.
 *
 * Each step is one subject a person recognises — "my investments", "my rent", "my home loan" — with
 * plain names for every line and the section number small beside it. On the right, the tax under both
 * regimes reprices as they type, from the server's own calculator, so the number they see is the
 * number payroll will use. Nothing here does arithmetic of its own: a second copy of tax law in the
 * browser would disagree with the first one sooner or later.
 */

type ItemState = Record<string, { amount: string; detail: string }>;
type RentRow = { id: string | null; fromMonth: string; toMonth: string; monthlyRent: string; city: string;
  landlordName: string; landlordPan: string; landlordAddress: string; landlordRelationship: string };
type HouseRow = { id: string | null; letOut: boolean; address: string; lenderName: string; lenderPan: string;
  lenderAddress: string; lenderType: LenderType | ""; interest: string; annualRent: string; municipalTax: string };
type PrevRow = { employerName: string; tan: string; income: string; tds: string; pf: string; pt: string };
type Form = { regime: TaxRegime; parentsSenior: boolean; items: ItemState; rent: RentRow[]; houses: HouseRow[]; previous: PrevRow;
  employeeAddress: string };

const LENDER_TYPES: { value: LenderType; label: string }[] = [
  { value: "FINANCIAL_INSTITUTION", label: "Bank or housing finance company" },
  { value: "EMPLOYER", label: "My employer" },
  { value: "OTHER", label: "Someone else" },
];

const STEPS = [
  { id: "regime", label: "Regime", icon: Scale },
  { id: "invest", label: "Investments", icon: PiggyBank },
  { id: "health", label: "Health", icon: HeartPulse },
  { id: "rent", label: "Rent", icon: Building2 },
  { id: "home", label: "Home loan", icon: Home },
  { id: "more", label: "More", icon: Receipt },
  { id: "income", label: "Other income", icon: Wallet },
  { id: "previous", label: "Previous job", icon: BriefcaseBusiness },
  { id: "review", label: "Review", icon: Check },
] as const;
type StepId = typeof STEPS[number]["id"];

const INVEST = ["LIFE_INSURANCE", "PPF", "ELSS", "TUITION_FEES", "HOME_LOAN_PRINCIPAL", "NSC", "TAX_SAVER_FD",
  "SUKANYA", "VPF", "POST_OFFICE_TD", "SCSS", "ULIP", "STAMP_DUTY", "PENSION_PLAN", "NPS_EMPLOYEE", "OTHER_123"];
const NPS = ["NPS_ADDITIONAL", "EMPLOYER_NPS"];
const HEALTH_SELF = ["HEALTH_SELF_PREMIUM", "HEALTH_SELF_CHECKUP", "HEALTH_SELF_MEDICAL"];
const HEALTH_PARENTS = ["HEALTH_PARENTS_PREMIUM", "HEALTH_PARENTS_CHECKUP", "HEALTH_PARENTS_MEDICAL"];
const HOME_EXTRA = ["FIRST_HOME_LOAN", "AFFORDABLE_HOME_LOAN"];
const MORE_LOANS = ["EDUCATION_LOAN", "EV_LOAN"];
const MORE_GIVING = ["DONATION_100", "DONATION_50", "DONATION_100_LIMITED", "DONATION_50_LIMITED", "POLITICAL_DONATION"];
const MORE_CARE = ["DISABLED_DEPENDENT", "DISABLED_DEPENDENT_SEVERE", "SPECIFIED_DISEASE", "SPECIFIED_DISEASE_SENIOR",
  "SELF_DISABILITY", "SELF_DISABILITY_SEVERE"];
const MORE_SALARY = ["LTA", "PROFESSIONAL_TAX_OTHER", "AGNIVEER"];
const INCOME = ["SAVINGS_INTEREST", "DEPOSIT_INTEREST", "OTHER_INCOME"];

function fromDeclaration(d: TaxDeclaration): Form {
  const items: ItemState = {};
  for (const i of d.items) items[i.key] = { amount: String(i.amount), detail: i.detail ?? "" };
  return {
    regime: d.regime,
    parentsSenior: d.parentsSenior,
    items,
    rent: d.rent.map((r) => ({ id: r.id, fromMonth: r.fromMonth, toMonth: r.toMonth, monthlyRent: String(r.monthlyRent),
      city: r.city, landlordName: r.landlordName ?? "", landlordPan: r.landlordPan ?? "",
      landlordAddress: r.landlordAddress ?? "", landlordRelationship: r.landlordRelationship ?? "" })),
    houses: d.houses.map((h) => ({ id: h.id, letOut: h.letOut, address: h.address ?? "", lenderName: h.lenderName ?? "",
      lenderPan: h.lenderPan ?? "", lenderAddress: h.lenderAddress ?? "", lenderType: h.lenderType ?? "",
      interest: String(h.interest), annualRent: String(h.annualRent || ""),
      municipalTax: String(h.municipalTax || "") })),
    previous: {
      employerName: d.previous?.employerName ?? "", tan: d.previous?.tan ?? "",
      income: d.previous?.income != null ? String(d.previous.income) : "", tds: d.previous?.tds != null ? String(d.previous.tds) : "",
      pf: d.previous?.pf != null ? String(d.previous.pf) : "", pt: d.previous?.pt != null ? String(d.previous.pt) : "",
    },
    employeeAddress: d.employeeAddress ?? "",
  };
}

function toPayload(f: Form): TaxDeclarationInput {
  const hasPrev = f.previous.employerName.trim() || num(f.previous.income) || num(f.previous.tds);
  return {
    regime: f.regime,
    parentsSenior: f.parentsSenior,
    items: Object.entries(f.items).filter(([, v]) => num(v.amount) > 0)
      .map(([key, v]) => ({ key, amount: num(v.amount), detail: v.detail || null })),
    rent: f.rent.filter((r) => num(r.monthlyRent) > 0).map((r) => ({
      id: r.id, fromMonth: r.fromMonth, toMonth: r.toMonth, monthlyRent: num(r.monthlyRent), city: r.city,
      landlordName: r.landlordName || null, landlordPan: r.landlordPan || null,
      landlordAddress: r.landlordAddress || null, landlordRelationship: r.landlordRelationship || null })),
    houses: f.houses.map((h) => ({ id: h.id, letOut: h.letOut, address: h.address || null, lenderName: h.lenderName || null,
      lenderPan: h.lenderPan || null, lenderAddress: h.lenderAddress || null, lenderType: h.lenderType || null,
      interest: num(h.interest), annualRent: num(h.annualRent), municipalTax: num(h.municipalTax) })),
    employeeAddress: f.employeeAddress,
    previous: hasPrev ? { employerName: f.previous.employerName || null, tan: f.previous.tan || null,
      income: num(f.previous.income), tds: num(f.previous.tds), pf: num(f.previous.pf), pt: num(f.previous.pt) } : {},
  };
}

export default function TaxDeclarationPage() {
  const [data, setData] = useState<TaxDeclaration | null>(null);
  const [form, setForm] = useState<Form | null>(null);
  const [preview, setPreview] = useState<TaxComputation | null>(null);
  const [step, setStep] = useState<StepId>("regime");
  const [dirty, setDirty] = useState(false);
  const [busy, setBusy] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const d = await api.taxDeclaration();
      setData(d);
      setForm(fromDeclaration(d));
      setPreview(await api.taxComputation());
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not load your tax declaration");
    }
  }, []);
  useEffect(() => { void load(); }, [load]);

  // Reprice as they type — half a second after the last keystroke, on the server's own calculator.
  const seq = useRef(0);
  useEffect(() => {
    if (!form || !dirty) return;
    const mine = ++seq.current;
    setPreviewing(true);
    const t = setTimeout(() => {
      api.taxPreview(toPayload(form))
        .then((c) => { if (mine === seq.current) setPreview(c); })
        .catch(() => {})
        .finally(() => { if (mine === seq.current) setPreviewing(false); });
    }, 500);
    return () => clearTimeout(t);
  }, [form, dirty]);

  const catalog = useMemo(() => new Map((data?.catalog ?? []).map((c) => [c.key, c])), [data]);
  const itemView = useMemo(() => new Map((data?.items ?? []).map((i) => [i.key, i])), [data]);

  function update(fn: (f: Form) => Form) {
    setForm((f) => (f ? fn(f) : f));
    setDirty(true);
    setNotice(null);
  }

  async function save(quiet = false): Promise<boolean> {
    if (!form || !data?.windowOpen) return true;
    setBusy(true); setError(null);
    try {
      const d = await api.saveTaxDeclaration(toPayload(form));
      setData(d);
      setForm(fromDeclaration(d));
      setDirty(false);
      setPreview(await api.taxComputation());
      if (!quiet) setNotice("Saved.");
      return true;
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not save your declaration");
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function go(next: StepId) {
    if (dirty && !(await save(true))) return;
    setStep(next);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function submit() {
    if (!(await save(true))) return;
    setBusy(true); setError(null);
    try {
      setData(await api.submitTaxDeclaration());
      setNotice("Declaration submitted. Payroll will use it from the next pay run.");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Could not submit your declaration");
    } finally {
      setBusy(false);
    }
  }

  async function uploadProof(type: "ITEM" | "RENT" | "HOUSE" | "PREVIOUS", id: string, file: File) {
    if (dirty && !(await save(true))) return;
    const d = await api.uploadTaxProof(type, id, file);
    setData(d);
  }
  async function deleteProof(id: string) {
    setData(await api.deleteTaxProof(id));
  }

  if (!data || !form) {
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Tax declaration</h1>
        {error ? <Alert tone="error" className="mt-6">{error}</Alert>
          : <div className="mt-10 flex justify-center"><Loader2 className="h-6 w-6 animate-spin text-violet" /></div>}
      </div>
    );
  }

  const locked = !data.windowOpen;
  const oldOnly = form.regime === "NEW";
  const idx = STEPS.findIndex((s) => s.id === step);
  const proofsOpen = data.proofsOpen;

  /** One declared line: its plain name, the section small beside it, the amount, its proof. */
  const line = (k: string, note?: string) => {
    const c = catalog.get(k);
    if (!c) return null;
    const v = form!.items[k]?.amount ?? "";
    const saved = itemView.get(k);
    const ignored = !c.allowedInNewRegime && !c.income && form!.regime === "NEW";
    return (
      <div key={k} className={cn("py-3", ignored && "opacity-60")}>
        <div className="flex items-start gap-4">
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium">{c.label}</p>
            <p className="text-xs text-fg/45">{c.sectionLabel}{c.hint ? ` · ${c.hint}` : ""}{note ? ` · ${note}` : ""}</p>
          </div>
          <MoneyInput className="w-36 shrink-0" value={v} disabled={locked}
            onChange={(val) => update((f) => ({ ...f, items: { ...f.items, [k]: { amount: val, detail: f.items[k]?.detail ?? "" } } }))} />
        </div>
        {saved && !c.income && (
          <Proofs status={saved.proofStatus} accepted={saved.acceptedAmount} note={saved.reviewNote} proofs={saved.proofs}
            canUpload={proofsOpen} onUpload={(file) => uploadProof("ITEM", k, file)} onDelete={deleteProof} />
        )}
      </div>
    );
  };

  const group = (name: string) => preview?.groups.find((g) => g.group === name);
  const epf = preview?.deductions.find((d) => d.key === "EPF");

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Tax declaration</h1>
          <p className="mt-1 text-fg/50">
            Tax year {data.financialYear} · {statusLabel(data.status)}
            {data.submittedAt && ` on ${formatDate(data.submittedAt)}`}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link href="/finance/tax/computation"><Button variant="secondary">How it&apos;s calculated</Button></Link>
          <Link href="/finance/tax/form124"><Button variant="ghost"><FileText className="h-4 w-4" /> Form 124</Button></Link>
        </div>
      </div>

      {locked && (
        <Alert tone="info" className="mt-4">
          <Lock className="mr-1 inline h-4 w-4" /> Declarations are closed by HR, so nothing here can change right now — including the regime.
        </Alert>
      )}
      {data.proofsDue && (
        <Alert tone="info" className="mt-4">
          The proof deadline has passed. Only what HR has accepted now reduces the tax taken from your pay.
        </Alert>
      )}
      {proofsOpen && !data.proofsDue && (
        <Alert tone="info" className="mt-4">
          Proofs are open{data.proofDeadline ? ` until ${formatDate(data.proofDeadline)}` : ""}. Add a receipt or statement under each line you claimed.
          After the deadline, only what HR accepts reduces your tax.
        </Alert>
      )}
      {error && <Alert tone="error" className="mt-4">{error}</Alert>}
      {notice && <Alert tone="success" className="mt-4">{notice}</Alert>}

      {/* The steps */}
      <nav className="mt-6 flex gap-1 overflow-x-auto pb-1" aria-label="Declaration steps">
        {STEPS.map((s, i) => {
          const Icon = s.icon;
          return (
            <button key={s.id} onClick={() => void go(s.id)}
              className={cn("inline-flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1.5 text-sm transition-colors",
                s.id === step ? "bg-violet text-white" : i < idx ? "bg-violet/10 text-violet" : "text-fg/50 hover:bg-fg/5 hover:text-fg")}>
              <Icon className="h-3.5 w-3.5" />{s.label}
            </button>
          );
        })}
      </nav>

      <div className="mt-4 grid gap-6 lg:grid-cols-[1fr_20rem]">
        <div className="min-w-0">
          {step === "regime" && (
            <Card>
              <h2 className="text-lg font-semibold">Which rules should your tax follow?</h2>
              <p className="mt-1 text-sm text-fg/60">
                India runs two sets of rules side by side. Neither is better for everyone — it depends on how much you invest and
                spend on rent, insurance and loans. Both are priced below on your own salary.
              </p>
              <div className="mt-5 grid gap-3 sm:grid-cols-2">
                {(["NEW", "OLD"] as TaxRegime[]).map((r) => {
                  const tax = r === "NEW" ? preview?.comparison.newRegimeTax : preview?.comparison.oldRegimeTax;
                  const best = preview?.comparison.cheaper === r && (preview?.comparison.saving ?? 0) > 0;
                  return (
                    <button key={r} disabled={locked} onClick={() => update((f) => ({ ...f, regime: r }))}
                      className={cn("relative rounded-xl border p-4 text-left transition-colors disabled:cursor-not-allowed",
                        form.regime === r ? "border-violet bg-violet/5 ring-1 ring-violet" : "border-fg/10 hover:border-fg/25")}>
                      {best && <span className="absolute right-3 top-3 inline-flex items-center gap-1 rounded-full bg-emerald-500/15 px-2 py-0.5 text-[11px] font-medium text-emerald-600 dark:text-emerald-400"><Sparkles className="h-3 w-3" /> Saves {money(preview!.comparison.saving)}</span>}
                      <p className="font-medium">{r === "NEW" ? "New regime" : "Old regime"}</p>
                      <p className="mt-0.5 text-xs text-fg/50">{r === "NEW"
                        ? "Lower rates, ₹75,000 standard deduction, almost no other deductions."
                        : "Higher rates, but rent, investments, insurance and home loans come off."}</p>
                      <p className="mt-3 text-2xl font-semibold tabular-nums">{tax != null ? money(tax) : "—"}</p>
                      <p className="text-xs text-fg/40">tax for the year</p>
                    </button>
                  );
                })}
              </div>
              <p className="mt-4 text-xs text-fg/50">
                The old-regime figure counts what you have declared so far. Fill in the next steps to see it fully, then come back and choose.
                You can switch while declarations are open.
              </p>
            </Card>
          )}

          {step === "invest" && (
            <Card>
              <StepHead title="Investments and savings" subtitle="Section 123 (80C) — up to ₹1,50,000 in total, across all of these." oldOnly={oldOnly} />
              {group("SEC_123") && <div className="mt-4"><Meter used={group("SEC_123")!.allowed} cap={150000} label="Section 123 used" /></div>}
              {epf && (
                <div className="mt-3 flex items-center justify-between rounded-lg bg-fg/[0.03] px-3 py-2 text-sm">
                  <span>Provident fund from your salary <span className="text-xs text-fg/40">· counted automatically</span></span>
                  <span className="tabular-nums">{money(epf.declared)}</span>
                </div>
              )}
              <div className="mt-2 divide-y divide-fg/5">{INVEST.map((k) => line(k))}</div>
              <h3 className="mt-6 text-sm font-semibold">National Pension System</h3>
              <div className="divide-y divide-fg/5">{NPS.map((k) => line(k))}</div>
            </Card>
          )}

          {step === "health" && (
            <Card>
              <StepHead title="Health insurance and check-ups" subtitle="Section 126 (80D). Your family and your parents each have their own limit." oldOnly={oldOnly} />
              <p className="mt-3 text-xs text-fg/50">
                {data.ageBand === "BELOW_60"
                  ? (data.dateOfBirthKnown ? "You are under 60: your family's limit is ₹25,000." : "Your date of birth is not on file, so you are treated as under 60. Ask HR to add it if you are 60 or older.")
                  : "You are 60 or older: your limit is ₹50,000, and medical bills count if nobody is insured."}
              </p>
              <h3 className="mt-4 text-sm font-semibold">You, your spouse and children</h3>
              <div className="divide-y divide-fg/5">{HEALTH_SELF.map((k) => line(k))}</div>
              <div className="mt-6 flex items-center justify-between">
                <h3 className="text-sm font-semibold">Your parents</h3>
                <label className="flex items-center gap-2 text-sm text-fg/70">
                  <input type="checkbox" className="accent-violet" disabled={locked} checked={form.parentsSenior}
                    onChange={(e) => update((f) => ({ ...f, parentsSenior: e.target.checked }))} />
                  A parent is 60 or older
                </label>
              </div>
              <div className="divide-y divide-fg/5">{HEALTH_PARENTS.map((k) => line(k))}</div>
            </Card>
          )}

          {step === "rent" && (
            <Card>
              <StepHead title="Rent you pay" subtitle={data.salaryHasHra
                ? "Your HRA exemption is worked out from this, month by month — you don't need to calculate it."
                : "Your salary has no HRA, so rent counts under Section 134 (80GG) instead, up to ₹60,000 a year."} oldOnly={oldOnly} />
              {preview && preview.exemptions.find((e) => e.key === "HRA") && (
                <div className="mt-4 rounded-lg bg-emerald-500/10 px-3 py-2 text-sm text-emerald-700 dark:text-emerald-300">
                  HRA exempt this year: <strong>{money(preview.exemptions.find((e) => e.key === "HRA")!.allowed)}</strong>
                </div>
              )}
              <div className="mt-4 flex flex-col gap-4">
                {form.rent.map((r, i) => {
                  const saved = data.rent.find((x) => x.id === r.id);
                  const set = (patch: Partial<RentRow>) => update((f) => ({ ...f, rent: f.rent.map((x, j) => (j === i ? { ...x, ...patch } : x)) }));
                  return (
                    <div key={i} className="rounded-xl border border-fg/10 p-4">
                      <div className="grid gap-3 sm:grid-cols-4">
                        <Labelled label="From"><Input type="month" disabled={locked} value={r.fromMonth} onChange={(e) => set({ fromMonth: e.target.value })} /></Labelled>
                        <Labelled label="To"><Input type="month" disabled={locked} value={r.toMonth} onChange={(e) => set({ toMonth: e.target.value })} /></Labelled>
                        <Labelled label="Rent a month"><MoneyInput disabled={locked} value={r.monthlyRent} onChange={(v) => set({ monthlyRent: v })} /></Labelled>
                        <Labelled label="City"><Input disabled={locked} value={r.city} onChange={(e) => set({ city: e.target.value })} placeholder="e.g. Pune" /></Labelled>
                        <Labelled label="Landlord's name"><Input disabled={locked} value={r.landlordName} onChange={(e) => set({ landlordName: e.target.value })} /></Labelled>
                        <Labelled label="Landlord's PAN" hint="Needed above ₹1 lakh a year"><Input disabled={locked} value={r.landlordPan} onChange={(e) => set({ landlordPan: e.target.value.toUpperCase() })} placeholder="ABCDE1234F" /></Labelled>
                        <Labelled label="Landlord's address" className="sm:col-span-2"><Input disabled={locked} value={r.landlordAddress} onChange={(e) => set({ landlordAddress: e.target.value })} /></Labelled>
                        <Labelled label="Related to you?" hint="Form 124 asks"><Input disabled={locked} value={r.landlordRelationship} onChange={(e) => set({ landlordRelationship: e.target.value })} placeholder="No, or e.g. Father" /></Labelled>
                      </div>
                      <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                        {saved ? <Proofs status={saved.proofStatus} accepted={saved.acceptedRent} note={saved.reviewNote} proofs={saved.proofs}
                          canUpload={proofsOpen} onUpload={(file) => uploadProof("RENT", saved.id, file)} onDelete={deleteProof} /> : <span />}
                        {!locked && <button onClick={() => update((f) => ({ ...f, rent: f.rent.filter((_, j) => j !== i) }))}
                          className="inline-flex items-center gap-1 text-xs text-fg/40 hover:text-red-500"><Trash2 className="h-3.5 w-3.5" /> Remove</button>}
                      </div>
                    </div>
                  );
                })}
                {!locked && (
                  <Button variant="secondary" onClick={() => update((f) => ({ ...f, rent: [...f.rent, emptyRent(data.financialYear, f.rent)] }))}>
                    <Plus className="h-4 w-4" /> {form.rent.length ? "Add another home (you moved)" : "Add rent"}
                  </Button>
                )}
              </div>
            </Card>
          )}

          {step === "home" && (
            <Card>
              <StepHead title="Home loan and houses you own" subtitle="The interest on a home loan. Principal repaid goes under Investments." oldOnly={oldOnly} />
              <div className="mt-4 flex flex-col gap-4">
                {form.houses.map((h, i) => {
                  const saved = data.houses.find((x) => x.id === h.id);
                  const set = (patch: Partial<HouseRow>) => update((f) => ({ ...f, houses: f.houses.map((x, j) => (j === i ? { ...x, ...patch } : x)) }));
                  return (
                    <div key={i} className="rounded-xl border border-fg/10 p-4">
                      <div className="flex gap-2">
                        {[false, true].map((lo) => (
                          <button key={String(lo)} disabled={locked} onClick={() => set({ letOut: lo })}
                            className={cn("rounded-full px-3 py-1 text-xs", h.letOut === lo ? "bg-violet text-white" : "bg-fg/5 text-fg/60")}>
                            {lo ? "Let out" : "I live in it"}
                          </button>
                        ))}
                      </div>
                      <div className="mt-3 grid gap-3 sm:grid-cols-3">
                        <Labelled label="Interest paid this year"><MoneyInput disabled={locked} value={h.interest} onChange={(v) => set({ interest: v })} /></Labelled>
                        <Labelled label="Bank or lender"><Input disabled={locked} value={h.lenderName} onChange={(e) => set({ lenderName: e.target.value })} placeholder="e.g. HDFC Bank" /></Labelled>
                        <Labelled label="Lender's PAN" hint="If not a bank"><Input disabled={locked} value={h.lenderPan} onChange={(e) => set({ lenderPan: e.target.value.toUpperCase() })} /></Labelled>
                        {h.letOut && <Labelled label="Rent received this year"><MoneyInput disabled={locked} value={h.annualRent} onChange={(v) => set({ annualRent: v })} /></Labelled>}
                        {h.letOut && <Labelled label="Municipal tax paid"><MoneyInput disabled={locked} value={h.municipalTax} onChange={(v) => set({ municipalTax: v })} /></Labelled>}
                        <Labelled label="Address" className={h.letOut ? "" : "sm:col-span-3"}><Input disabled={locked} value={h.address} onChange={(e) => set({ address: e.target.value })} /></Labelled>
                        <Labelled label="The lender is">
                          <select disabled={locked} value={h.lenderType} onChange={(e) => set({ lenderType: e.target.value as LenderType | "" })}
                            className="w-full rounded-md border border-fg/15 bg-fg/5 px-3 py-2 text-sm text-fg">
                            <option value="">Choose</option>
                            {LENDER_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
                          </select>
                        </Labelled>
                        <Labelled label="Lender's address" className="sm:col-span-2"><Input disabled={locked} value={h.lenderAddress} onChange={(e) => set({ lenderAddress: e.target.value })} placeholder="Branch address on your loan statement" /></Labelled>
                      </div>
                      <p className="mt-2 text-xs text-fg/45">{h.letOut
                        ? "Rent less 30% and the interest is your income from it; a loss comes off other income up to ₹2,00,000 (old regime only)."
                        : "Interest on a home you live in comes off up to ₹2,00,000 (old regime only)."}</p>
                      <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                        {saved ? <Proofs status={saved.proofStatus} accepted={saved.acceptedInterest} note={saved.reviewNote} proofs={saved.proofs}
                          canUpload={proofsOpen} onUpload={(file) => uploadProof("HOUSE", saved.id, file)} onDelete={deleteProof} /> : <span />}
                        {!locked && <button onClick={() => update((f) => ({ ...f, houses: f.houses.filter((_, j) => j !== i) }))}
                          className="inline-flex items-center gap-1 text-xs text-fg/40 hover:text-red-500"><Trash2 className="h-3.5 w-3.5" /> Remove</button>}
                      </div>
                    </div>
                  );
                })}
                {!locked && <Button variant="secondary" onClick={() => update((f) => ({ ...f, houses: [...f.houses, emptyHouse()] }))}><Plus className="h-4 w-4" /> Add a house</Button>}
              </div>
              <h3 className="mt-6 text-sm font-semibold">Extra interest for first-time and affordable homes</h3>
              <div className="divide-y divide-fg/5">{HOME_EXTRA.map((k) => line(k))}</div>
              {preview && preview.interestMovedToHouse > 0 && (
                <p className="mt-3 rounded-lg bg-emerald-500/10 px-3 py-2 text-xs text-emerald-800 dark:text-emerald-300">
                  We count {money(preview.interestMovedToHouse)} of this under Section 22 (formerly 24(b)) instead — it allows up to ₹2,00,000 on a home you live in, more than Section 130/131. Same interest, less tax; nothing to do.
                </p>
              )}
            </Card>
          )}

          {step === "more" && (
            <Card>
              <StepHead title="Other deductions" subtitle="Loans, donations, disability and a few salary items. Skip anything that doesn't apply." oldOnly={oldOnly} />
              <h3 className="mt-4 text-sm font-semibold">Loans</h3>
              <div className="divide-y divide-fg/5">{MORE_LOANS.map((k) => line(k))}</div>
              <h3 className="mt-6 text-sm font-semibold">Donations</h3>
              <p className="text-xs text-fg/45">By cheque or online only — cash gifts above ₹2,000 don&apos;t count.</p>
              <div className="divide-y divide-fg/5">{MORE_GIVING.map((k) => line(k))}</div>
              <h3 className="mt-6 text-sm font-semibold">Disability and illness</h3>
              <div className="divide-y divide-fg/5">{MORE_CARE.map((k) => line(k))}</div>
              <h3 className="mt-6 text-sm font-semibold">From your salary</h3>
              <div className="divide-y divide-fg/5">
                {MORE_SALARY.filter((k) => k !== "LTA" || data.salaryHasLta).map((k) => line(k))}
              </div>
            </Card>
          )}

          {step === "income" && (
            <Card>
              <StepHead title="Other income" subtitle="Income you'd like taxed here, so there's nothing left to pay when you file. Counts in both regimes." oldOnly={false} />
              <div className="mt-2 divide-y divide-fg/5">{INCOME.map((k) => line(k))}</div>
            </Card>
          )}

          {step === "previous" && (
            <Card>
              <StepHead title="An earlier job this year" subtitle="If you joined after April, add what your previous employer paid and deducted — from their Form 130 (Form 16)." oldOnly={false} />
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <Labelled label="Employer's name"><Input disabled={locked} value={form.previous.employerName} onChange={(e) => update((f) => ({ ...f, previous: { ...f.previous, employerName: e.target.value } }))} /></Labelled>
                <Labelled label="Their TAN" hint="On their Form 130"><Input disabled={locked} value={form.previous.tan} onChange={(e) => update((f) => ({ ...f, previous: { ...f.previous, tan: e.target.value.toUpperCase() } }))} placeholder="ABCD12345E" /></Labelled>
                <Labelled label="Salary they paid"><MoneyInput disabled={locked} value={form.previous.income} onChange={(v) => update((f) => ({ ...f, previous: { ...f.previous, income: v } }))} /></Labelled>
                <Labelled label="Tax they deducted"><MoneyInput disabled={locked} value={form.previous.tds} onChange={(v) => update((f) => ({ ...f, previous: { ...f.previous, tds: v } }))} /></Labelled>
                <Labelled label="Your PF there"><MoneyInput disabled={locked} value={form.previous.pf} onChange={(v) => update((f) => ({ ...f, previous: { ...f.previous, pf: v } }))} /></Labelled>
                <Labelled label="Professional tax there"><MoneyInput disabled={locked} value={form.previous.pt} onChange={(v) => update((f) => ({ ...f, previous: { ...f.previous, pt: v } }))} /></Labelled>
              </div>
              {data.previous && (
                <Proofs status={data.previous.status === "NONE" ? "NONE" : data.previous.status === "SUBMITTED" ? "SUBMITTED" : data.previous.status === "ACCEPTED" ? "ACCEPTED" : "REJECTED"}
                  note={data.previous.reviewNote} proofs={data.previous.proofs} canUpload={proofsOpen}
                  onUpload={(file) => uploadProof("PREVIOUS", data.employeeId, file)} onDelete={deleteProof} />
              )}
            </Card>
          )}

          {step === "review" && preview && (
            <Card>
              <h2 className="text-lg font-semibold">Check and submit</h2>
              <p className="mt-1 text-sm text-fg/60">This is what payroll will use. You can still change it while declarations are open.</p>
              <dl className="mt-5 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
                <Row label="Regime" value={preview.regime === "NEW" ? "New" : "Old"} />
                <Row label="Salary for the year" value={money(preview.grossSalary)} />
                {preview.exemptions.map((e) => <Row key={e.key} label={`${e.label}`} value={`− ${money(e.allowed)}`} />)}
                <Row label="Standard deduction" value={`− ${money(preview.standardDeduction)}`} />
                {preview.houseProperty !== 0 && <Row label="House property" value={money(preview.houseProperty)} />}
                {preview.otherIncome > 0 && <Row label="Other income" value={`+ ${money(preview.otherIncome)}`} />}
                <Row label="Deductions" value={`− ${money(preview.totalDeductions)}`} />
                <Row label="Taxable income" value={money(preview.taxableIncome)} strong />
                <Row label="Tax for the year" value={money(preview.totalTax)} strong />
                <Row label="Each month from now" value={money(preview.projectedNextMonth)} />
              </dl>
              <Labelled label="Your address" hint="Printed on Form 124 — where you live now" className="mt-5">
                <Input disabled={locked} value={form.employeeAddress} onChange={(e) => update((f) => ({ ...f, employeeAddress: e.target.value }))}
                  placeholder="House, street, city, PIN" />
              </Labelled>
              {preview.deductions.some((d) => d.allowed < d.declared) && (
                <p className="mt-4 rounded-lg bg-amber-500/10 px-3 py-2 text-xs text-amber-700 dark:text-amber-300">
                  Some claims are above their legal limit or don&apos;t apply to the {preview.regime === "NEW" ? "new" : "old"} regime; only the allowed part counts.
                  <Link href="/finance/tax/computation" className="ml-1 underline">See which</Link>.
                </p>
              )}
              <div className="mt-6 flex flex-wrap gap-2">
                <Button onClick={() => void submit()} disabled={busy || locked}>
                  {busy && <Loader2 className="h-4 w-4 animate-spin" />}
                  {data.status === "SUBMITTED" ? "Submit again with changes" : "Submit declaration"}
                </Button>
                <Link href="/finance/tax/form124"><Button variant="secondary"><FileText className="h-4 w-4" /> View Form 124</Button></Link>
              </div>
            </Card>
          )}

          <div className="mt-4 flex items-center justify-between">
            <Button variant="ghost" disabled={idx === 0 || busy} onClick={() => void go(STEPS[Math.max(0, idx - 1)].id)}>
              <ArrowLeft className="h-4 w-4" /> Back
            </Button>
            <div className="flex items-center gap-2">
              {dirty && !locked && <Button variant="secondary" disabled={busy} onClick={() => void save()}>{busy ? <Loader2 className="h-4 w-4 animate-spin" /> : null} Save</Button>}
              {idx < STEPS.length - 1 && (
                <Button disabled={busy} onClick={() => void go(STEPS[idx + 1].id)}>Next <ArrowRight className="h-4 w-4" /></Button>
              )}
            </div>
          </div>
        </div>

        {/* The live summary */}
        <aside className="lg:sticky lg:top-6 lg:self-start">
          <Card>
            <div className="flex items-center justify-between">
              <p className="text-sm text-fg/50">Your tax this year</p>
              {previewing && <Loader2 className="h-3.5 w-3.5 animate-spin text-fg/40" />}
            </div>
            <p className="mt-1 text-3xl font-semibold tabular-nums">{preview ? money(preview.totalTax) : "—"}</p>
            <p className="text-sm text-fg/50">{form.regime === "NEW" ? "New" : "Old"} regime{preview ? ` · ${money(preview.projectedNextMonth)} a month from now` : ""}</p>
            {preview && (
              <div className="mt-5 flex flex-col gap-2">
                {(["NEW", "OLD"] as TaxRegime[]).map((r) => {
                  const tax = r === "NEW" ? preview.comparison.newRegimeTax : preview.comparison.oldRegimeTax;
                  const max = Math.max(preview.comparison.newRegimeTax, preview.comparison.oldRegimeTax, 1);
                  return (
                    <div key={r}>
                      <div className="flex justify-between text-xs"><span className={cn(form.regime === r && "font-medium")}>{r === "NEW" ? "New regime" : "Old regime"}</span><span className="tabular-nums">{money(tax)}</span></div>
                      <div className="mt-1 h-1.5 rounded-full bg-fg/10"><div className={cn("h-1.5 rounded-full", preview.comparison.cheaper === r ? "bg-emerald-500" : "bg-fg/30")} style={{ width: `${Math.round((tax / max) * 100)}%` }} /></div>
                    </div>
                  );
                })}
                {preview.comparison.saving > 0 && preview.comparison.cheaper !== form.regime && !locked && (
                  <button onClick={() => update((f) => ({ ...f, regime: preview.comparison.cheaper }))}
                    className="mt-2 rounded-lg bg-emerald-500/10 px-3 py-2 text-left text-xs font-medium text-emerald-700 hover:bg-emerald-500/15 dark:text-emerald-300">
                    <Sparkles className="mr-1 inline h-3.5 w-3.5" />
                    Switch to the {preview.comparison.cheaper === "NEW" ? "new" : "old"} regime and save {money(preview.comparison.saving)}
                  </button>
                )}
              </div>
            )}
            {preview && (
              <dl className="mt-5 space-y-1.5 border-t border-fg/10 pt-4 text-xs">
                <Row label="Taxable income" value={money(preview.taxableIncome)} />
                <Row label="Deducted so far" value={money(preview.deductedSoFar)} />
                <Row label="Still to deduct" value={money(preview.remainingTax)} />
              </dl>
            )}
            {preview && !preview.withheldByPayroll && (
              <p className="mt-3 text-[11px] text-fg/40">Your employer doesn&apos;t deduct tax through Orbit yet — this is an estimate to plan with.</p>
            )}
            <Link href="/finance/tax/computation" className="mt-4 inline-flex items-center gap-1 text-xs text-violet hover:underline">
              <Landmark className="h-3.5 w-3.5" /> See the full working
            </Link>
          </Card>
        </aside>
      </div>
    </div>
  );
}

function StepHead({ title, subtitle, oldOnly }: { title: string; subtitle: string; oldOnly: boolean }) {
  return (
    <div>
      <h2 className="text-lg font-semibold">{title}</h2>
      <p className="mt-1 text-sm text-fg/60">{subtitle}</p>
      {oldOnly && (
        <p className="mt-2 rounded-lg bg-fg/[0.04] px-3 py-2 text-xs text-fg/60">
          You&apos;re on the new regime, where most of these don&apos;t reduce tax. Fill them in anyway to compare — the summary shows both.
        </p>
      )}
    </div>
  );
}

function Labelled({ label, hint, className, children }: { label: string; hint?: string; className?: string; children: React.ReactNode }) {
  return (
    <label className={cn("flex flex-col gap-1", className)}>
      <span className="text-xs font-medium text-fg/60">{label}{hint && <span className="font-normal text-fg/40"> · {hint}</span>}</span>
      {children}
    </label>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="text-fg/60">{label}</dt>
      <dd className={cn("tabular-nums", strong && "font-semibold")}>{value}</dd>
    </div>
  );
}

function emptyRent(year: string, existing: RentRow[]): RentRow {
  const start = Number(year.slice(0, 4));
  const last = existing.length ? existing[existing.length - 1].toMonth : null;
  let from = `${start}-04`;
  if (last) {
    const [y, m] = last.split("-").map(Number);
    from = m === 12 ? `${y + 1}-01` : `${y}-${String(m + 1).padStart(2, "0")}`;
  }
  return { id: null, fromMonth: from, toMonth: `${start + 1}-03`, monthlyRent: "", city: "", landlordName: "",
    landlordPan: "", landlordAddress: "", landlordRelationship: "" };
}

function emptyHouse(): HouseRow {
  return { id: null, letOut: false, address: "", lenderName: "", lenderPan: "", lenderAddress: "", lenderType: "",
    interest: "", annualRent: "", municipalTax: "" };
}

function statusLabel(status: TaxDeclaration["status"]): string {
  return status === "SUBMITTED" ? "Submitted" : status === "DRAFT" ? "Draft — not submitted yet" : "Not started";
}

